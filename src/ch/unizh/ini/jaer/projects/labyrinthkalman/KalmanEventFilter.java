/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.labyrinthkalman;

import java.awt.geom.Point2D;
import java.util.ArrayList;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * The specialized Kalman filter for ball tracking in the labyrinth game.
 * @author Eero, Tobias Pietzsch
 */
@Description("specialized Kalman filter for ball tracking in the labyrinth game")
public class KalmanEventFilter extends EventFilter2D implements FrameAnnotater {

	private ArrayList< LabyrinthBallKalmanFilter > filters;
	private ArrayList< boolean[] > lastUpdates;
	private final int nLastUpdates = 10;
	private int lastUpdateIndex;
	private LabyrinthBallKalmanFilter[] lastBestFilters;
	private LabyrinthBallKalmanFilter currentBestFilter;

	// the timestamp of the most recent received event
	private int t = -1;
	private boolean tIsInitialised;

	// parameters
	private double measurementSigma = getDouble("measurementSigma", 3.0);
	private double processSigma = getDouble("processSigma", 100.0);
	private double measurementThreshold = getDouble("measurementThreshold", 3.0);
	private double maxPositionVariance = 10 * 10;

	private boolean annotationEnabled = true;
	LabyrinthBallController controller;

	public KalmanEventFilter(AEChip chip) {
		this(chip,null);
	}

	public KalmanEventFilter(AEChip chip, LabyrinthBallController controller) {
		super(chip);
		this.controller=controller;

		filters = new ArrayList< LabyrinthBallKalmanFilter >();
		lastUpdates = new ArrayList< boolean[] >();
		lastBestFilters = new LabyrinthBallKalmanFilter[ nLastUpdates ];

		setPropertyTooltip("processSigma","standard deviation of acceleration noise [px/s^2]");
		setPropertyTooltip("measurementThreshold","maximum mahalanobis distance to accept a measurement for update");
		setPropertyTooltip("measurementSigma","standard deviation of measurement nois [px]");

		resetFilter();
	}

	public float getMeasurementSigma() {
		return (float)measurementSigma;
	}

	synchronized public void setMeasurementSigma(float measurementSigma) {

		if(measurementSigma < 0) {
			measurementSigma = 0;
		}
		putDouble("measurementSigma", measurementSigma);

		if(measurementSigma != this.measurementSigma) {
			this.measurementSigma = measurementSigma;
			resetFilter();
		}
	}

	public float getProcessSigma() {
		return (float)processSigma;
	}
	synchronized public void setProcessSigma(float processSigma) {

		if(processSigma < 0) {
			processSigma = 0;
		}
		putDouble("processSigma", processSigma);

		if(processSigma != this.processSigma) {
			this.processSigma = processSigma;
			resetFilter();
		}
	}

	public float getMeasurementThreshold() {
		return (float)measurementThreshold;
	}
	synchronized public void setMeasurementThreshold(float measurementThreshold) {

		if(measurementThreshold < 0) {
			measurementThreshold = 0;
		}
		putDouble("measurementThreshold", measurementThreshold);

		if(measurementThreshold != this.measurementThreshold) {
			this.measurementThreshold = measurementThreshold;
			resetFilter();
		}
	}

	@Override
	final public void resetFilter()
	{
		tIsInitialised = false;
		filters.clear();
		lastUpdates.clear();
		LabyrinthBallKalmanFilter filter = new LabyrinthBallKalmanFilter();
		filter.setMeasurementSigma( measurementSigma );
		filter.setProcessSigma( processSigma );
		filter.resetFilter();
		filters.add( filter );
		currentBestFilter = filter;
		lastUpdates.add( new boolean[ nLastUpdates ] );
		lastUpdateIndex = 0;
	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	@Override
	public EventPacket< ? > filterPacket( EventPacket< ? > in )
	{
		if ( !isFilterEnabled() ) {
			return in;
		}

		if ( (in == null) || (in.getSize() == 0) ) {
			return in;
		}

		// TODO: get the performed actions from the controller
		final double[] act = new double[ 2 ];

		// prediction for all filters
		int timestamp = in.getFirstTimestamp();
		if ( tIsInitialised )
		{
			double dt = ( timestamp - t ) / 1000000.0;
			for ( LabyrinthBallKalmanFilter filter : filters )
			{
				filter.predict( act, dt );
			}
		}
		tIsInitialised = true;
		t = timestamp;

		for ( int i = 0; i < filters.size(); ++i )
		{
			lastUpdates.get( i )[ lastUpdateIndex ] = false;
		}

		final double[] meas = new double[ 2 ];
		for ( BasicEvent event : in )
		{
			meas[ 0 ] = event.x;
			meas[ 1 ] = event.y;
			double minDistance = Double.MAX_VALUE;

			// find the filter that best matches this measurement

			LabyrinthBallKalmanFilter bestFilter = null;
			int bestFilterIndex = 0;
			for ( int i = 0; i < filters.size(); ++i )
			{
				LabyrinthBallKalmanFilter filter = filters.get( i );
				double distance = filter.mahalanobisToMeasurement( meas );
				if ( distance < minDistance )
				{
					minDistance = distance;
					bestFilter = filter;
					bestFilterIndex = i;
				}
			}
			if ( ( bestFilter != null ) && ( minDistance <= measurementThreshold ) )
			{
				bestFilter.correct( meas );
				lastUpdates.get( bestFilterIndex )[ lastUpdateIndex ] = true;
			}
			else
			{
				bestFilter = new LabyrinthBallKalmanFilter();
				bestFilter.setMeasurementSigma( measurementSigma );
				bestFilter.setProcessSigma( processSigma );
				bestFilter.resetFilter();
				bestFilter.getMu()[ 0 ] = meas[ 0 ];
				bestFilter.getMu()[ 1 ] = meas[ 1 ];
				filters.add( bestFilter );
				boolean[] lu = new boolean[ nLastUpdates ];
				lu[ lastUpdateIndex ] = true;
				lastUpdates.add( lu );
				System.out.println( filters.size() + " Kalman filters" );
			}
		}

		// clean up old filters
		for ( int i = 0; i < filters.size(); )
		{
			LabyrinthBallKalmanFilter filter = filters.get( i );
			if ( filter.getSigma()[ 0 ][ 0 ] > maxPositionVariance )
			{
				filters.remove( i );
				lastUpdates.remove( i );
			}
			else {
				++i;
			}
		}

		// select current best filter
		int bestFilterN = Integer.MIN_VALUE;
		for ( int i = 0; i < filters.size(); ++i )
		{
			boolean[] lu = lastUpdates.get( i );
			int n = 0;
			for (boolean element : lu) {
				if ( element ) {
					++n;
				}
			}
			if ( n > bestFilterN )
			{
				bestFilterN = n;
				currentBestFilter = filters.get( i );
			}
		}
		lastBestFilters[ lastUpdateIndex ] = currentBestFilter;
		for (LabyrinthBallKalmanFilter lastBestFilter : lastBestFilters) {
			if ( lastBestFilter != currentBestFilter )
			{
				currentBestFilter = null;
				break;
			}
		}

		++lastUpdateIndex;
		if ( lastUpdateIndex >= nLastUpdates ) {
			lastUpdateIndex = 0;
		}

		return in;
	}

	@Override
	public void setAnnotationEnabled(boolean annotationEnabled) {
		this.annotationEnabled = annotationEnabled;
	}

	@Override
	public boolean isAnnotationEnabled() {
		return annotationEnabled;
	}

	protected static void paintCovarianceEllipse( double[] pos, double[][] cov, final double nsigmas, float[] color, GL2 gl )
	{
		final int no_points_ellipse = 12;

		final double a = cov[0][0];
		final double b = cov[1][0];
		final double c = cov[1][1];
		final double d = Math.sqrt( (((a*a) + (4*b*b)) - (2*a*c)) + (c*c) );
		final double scale1 = Math.sqrt( 0.5 * ( a+c+d ) ) * nsigmas;
		final double scale2 = Math.sqrt( 0.5 * ( (a+c)-d ) ) * nsigmas;
		final double theta = (0.5 * Math.atan2( (2*b), (a-c) ) * 180.0) / Math.PI;

		gl.glPushMatrix();

		gl.glTranslated(pos[0], pos[1], 0);
		gl.glRotated(theta, 0, 0, 1);
		gl.glScaled(scale1, scale2, 1);

		gl.glColor3f( color[0], color[1], color[2] );
		gl.glLineWidth(2);

		gl.glBegin(GL.GL_LINE_LOOP);
		final double angle = (2*Math.PI) / no_points_ellipse;
		for (int i = 0;i<no_points_ellipse;i++) {
			gl.glVertex2d( Math.cos( i*angle ), Math.sin( i*angle ) );
		}
		gl.glEnd();

		gl.glPopMatrix();
	}

	protected static void paintKalmanFilterState( final LabyrinthBallKalmanFilter filter, GL2 gl, float[] covColor, float[] speedColor )
	{
		final double[] mu = filter.getMu();
		final double[][] Sigma = filter.getSigma();

		paintCovarianceEllipse( mu, Sigma, 3, covColor, gl );

		double velx = mu[2] * 0.1;
		double vely = mu[3] * 0.1;

		gl.glColor3f( speedColor[0], speedColor[1], speedColor[2] );
		gl.glLineWidth(2);

		gl.glPushMatrix();
		gl.glTranslated(mu[0], mu[1], 0);
		gl.glBegin(GL.GL_LINES);
		gl.glVertex2d(0, 0);
		gl.glVertex2d(velx, vely);
		gl.glEnd();
		gl.glPopMatrix();

		gl.glLineWidth(1);
		gl.glColor3f(1,1,1);
		gl.glBegin(GL.GL_LINES);
		gl.glVertex2d(64, 64);
		gl.glVertex2d(mu[0], mu[1]);
		gl.glEnd();
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {

		if(!isFilterEnabled()) {
			return;
		}

		if (!annotationEnabled) {
			return;
		}

		GL2 gl=drawable.getGL().getGL2();
		float[] covColor = new float[]{1,1,1};
		float[] speedColor = new float[]{1,1,1};
		float[] bestCovColor = new float[]{1,1,0};
		float[] bestSpeedColor = new float[]{1,0,0};
		for ( LabyrinthBallKalmanFilter filter : filters )
		{
			if ( filter == currentBestFilter )
			{
				paintKalmanFilterState( filter, gl, bestCovColor, bestSpeedColor );
			}
			else
			{
				paintKalmanFilterState( filter, gl, covColor, speedColor );
			}
		}
	}

	public Point2D.Float getBallPosition() {

		Point2D.Float position = new Point2D.Float();
		if ( currentBestFilter != null ) {
			final double[] mu = currentBestFilter.getMu();
			position.setLocation(mu[0], mu[1]);
		}
		return position;
	}

	public double getBallPositionX() {
		if ( currentBestFilter != null ) {
			return currentBestFilter.getMu()[0];
		}
		else {
			return 0;
		}
	}

	public double getBallPositionY() {
		if ( currentBestFilter != null ) {
			return currentBestFilter.getMu()[1];
		}
		else {
			return 0;
		}
	}

	public Point2D.Float getBallVelocity() {
		Point2D.Float position = new Point2D.Float();
		if ( currentBestFilter != null ) {
			final double[] mu = currentBestFilter.getMu();
			position.setLocation(mu[2], mu[3]);
		}
		return position;
	}

	public boolean positionEstimateValid()
	{
		return currentBestFilter != null;
	}

	public void accelerationChanged( double dax, double day, int timestamp )
	{
		// prediction for all filters
		final double[] act = new double[] { dax, day };
		if ( tIsInitialised )
		{
			double dt = ( timestamp - t ) / 1000000.0;
			System.out.println( "act = " + act[ 0 ] + " " + act[ 1 ] + " dt = " + dt );
			for ( LabyrinthBallKalmanFilter filter : filters )
			{
				filter.predict( act, dt );
			}
		}
		tIsInitialised = true;
		t = timestamp;
	}
}

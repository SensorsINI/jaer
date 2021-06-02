package ch.unizh.ini.jaer.projects.labyrinthkalman;

public final class LabyrinthBallKalmanFilter extends KalmanFilter
{
	private double measurementSigma = 3.0;

	private double processSigma = 100.0;

	public LabyrinthBallKalmanFilter()
	{
		super( 6, 2, 2 );
	}
	
	public double getMeasurementSigma()
	{
		return measurementSigma;
	}

	public void setMeasurementSigma( double measurementSigma )
	{
		this.measurementSigma = measurementSigma;
		updateQt();
	}

	public double getProcessSigma()
	{
		return processSigma;
	}

	public void setProcessSigma( double processSigma )
	{
		this.processSigma = processSigma;
	}
	
	public void resetFilter()
	{
		initState( new double[] { 64, 64 } ); // initialize position to center of 128x128 retina
		updateCt();
		updateQt();
	}

	@Override
	public void predict( final double[] act, double dt )
	{
		assert act.length == m;

		updateAt( dt );
		updateBt( dt );
		updateRt( dt );
		predictMu( act );
		predictSigma();
	}

	@Override
	public void correct( final double[] meas )
	{
		assert meas.length == k;

		//updateCt();
		//updateQt();
		updateKalmanGain();
		correctMu( meas );
		correctSigma();
	}

	protected void updateAt( double dt )
	{
		double a = 0.5 * dt * dt;
		double b = dt;

		At[ 0 ][ 0 ] = 1; // constant
		At[ 0 ][ 2 ] = b;
		At[ 0 ][ 4 ] = a;
		At[ 1 ][ 1 ] = 1; // constant
		At[ 1 ][ 3 ] = b;
		At[ 1 ][ 5 ] = a;
		At[ 2 ][ 2 ] = 1; // constant
		At[ 2 ][ 4 ] = b;
		At[ 3 ][ 3 ] = 1; // constant
		At[ 3 ][ 5 ] = b;
		At[ 4 ][ 4 ] = 1; // constant
		At[ 5 ][ 5 ] = 1; // constant
	}

	protected void updateBt( double dt )
	{
		final double a = 0.5 * dt * dt;
		final double b = dt;

		Bt[ 0 ][ 0 ] = a; // constant
		Bt[ 1 ][ 1 ] = a;
		Bt[ 2 ][ 0 ] = b;
		Bt[ 3 ][ 1 ] = b; // constant
		Bt[ 4 ][ 0 ] = 1;
		Bt[ 5 ][ 1 ] = 1;
	}

	protected void updateRt( double dt )
	{
		final double a = 0.5 * dt * dt;
		final double b = dt;

		final double cov = processSigma * processSigma;
		final double acov = a * cov;
		final double bcov = b * cov;
		final double aacov = a * acov;
		final double abcov = a * bcov;
		final double bbcov = b * bcov;

		Rt[ 0 ][ 0 ] = aacov;
		Rt[ 0 ][ 2 ] = abcov;
		Rt[ 0 ][ 4 ] = acov;
		Rt[ 1 ][ 1 ] = aacov;
		Rt[ 1 ][ 3 ] = abcov;
		Rt[ 1 ][ 5 ] = acov;
		Rt[ 2 ][ 0 ] = abcov;
		Rt[ 2 ][ 2 ] = bbcov;
		Rt[ 2 ][ 4 ] = bcov;
		Rt[ 3 ][ 1 ] = abcov;
		Rt[ 3 ][ 3 ] = bbcov;
		Rt[ 3 ][ 5 ] = bcov;
		Rt[ 4 ][ 0 ] = acov;
		Rt[ 4 ][ 2 ] = bcov;
		Rt[ 4 ][ 4 ] = cov;
		Rt[ 5 ][ 1 ] = acov;
		Rt[ 5 ][ 3 ] = bcov;
		Rt[ 5 ][ 5 ] = cov;
	}

	protected void updateCt()
	{
		Ct[0][0] = 1;
		Ct[1][1] = 1;
	}
	
	protected void updateQt()
	{
    	final double measCov = measurementSigma * measurementSigma;
        Qt[0][0] = measCov;
        Qt[1][1] = measCov;
	}
	
	protected void initState( final double[] pos )
	{
		assert pos.length == 2;

		for ( int i = 0; i < n; ++i )
		{
			mu[ i ] = 0;
			for ( int j = 0; j < 6; ++j )
				Sigma[ i ][ j ] = 0;
		}

		mu[ 0 ] = pos[ 0 ];
		mu[ 1 ] = pos[ 1 ];
	}
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.labyrinthkalman;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.Scanner;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Jan Funke
 */
public class DepressionMap extends EventFilter2D implements FrameAnnotater {

	private int cameraX = 0;
	private int cameraY = 0;
	private double[][] map;
	private double maxMap;

	private boolean learnMap = false;
	private boolean showMap  = false;

	private String FilePath = getString("DepressionMap.FilePath", "KalmanDepression.map");
	//private File absolutePath = new File("/home/lorenz/capo/Kalman.map");


	public DepressionMap(AEChip chip) {

		super(chip);
		resetFilter();
	}

	synchronized public void doLoadMap() throws FileNotFoundException{
		System.out.println("Loading...");

		getFilePath();
		System.out.println(FilePath);
		Scanner s = null;
		double sum = 0;
		try {
			s = new Scanner(
				new BufferedReader(new FileReader(FilePath)));

			for (int x = 0; x < cameraX; x++) {
				for (int y = 0; y < cameraY; y++) {
					map[x][y] = s.nextDouble();
				}
			}

		}
		catch(Exception e){
			System.err.println ("Error loading file");
		}
		finally {
			s.close();
		}
	}

	@Override
	final public void resetFilter()
	{
		File f = new File(FilePath);
		cameraX = chip.getSizeX();
		cameraY = chip.getSizeY();

		map = new double[cameraX][cameraY];

		System.out.println("f.exists:"+f.exists());
		if(f.exists())
		{
			getFilePath();
			System.out.println(FilePath);
			Scanner s = null;
			double sum = 0;
			try {
				s = new Scanner(
					new BufferedReader(new FileReader(FilePath)));

				for (int x = 0; x < cameraX; x++) {
					for (int y = 0; y < cameraY; y++) {
						map[x][y] = s.nextDouble();
					}
				}

			}
			catch(Exception e){
				System.err.println ("Error loading file");
			}
			finally {
				s.close();
			}

		}

		else
		{

			for (int x = 0; x < cameraX; x++) {
				for (int y = 0; y < cameraY; y++) {
					map[x][y] = 1.0;
				}
			}
		}
		maxMap = 0.0;

		System.out.println("DepressionMap reset");
	}

	synchronized public void doSaveMap(){
		System.out.println("Saving...");

		getFilePath();
		System.out.println(FilePath);

		FileOutputStream out;
		PrintStream p;

		try
		{
			out = new FileOutputStream(FilePath);

			// Connect print stream to the output stream
			p = new PrintStream( out );
			for(int x = 0; x<cameraX; x++) {
				for(int y = 0; y<cameraY; y++)
				{
					p.println (map[x][y]);
				}
			}
			p.close();
		}
		catch (Exception e)
		{
			System.err.println ("Error writing to file");
		}


	}

	@Override
	public void initFilter() {
		resetFilter();
	}

	public void setLearnMap(boolean learnMap) {


		if (this.learnMap && !learnMap) {

			for (int x = 0; x < cameraX; x++) {
				for (int y = 0; y < cameraY; y++) {
					map[x][y] = (maxMap - map[x][y])/maxMap;
				}
			}
		}
		this.learnMap = learnMap;
	}

	public boolean getLearnMap() {
		return learnMap;

	}

	public void setFilePath(String FilePath) {

		this.FilePath = FilePath;
		//resetFilter();
	}

	public String getFilePath() {
		return FilePath;
	}


	public void setShowMap(boolean showMap) {
		this.showMap = showMap;
	}

	public boolean getShowMap() {
		return showMap;
	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {

		if (!isFilterEnabled()) {
			return in;
		}

		if ((in == null) || (in.getSize() == 0)) {
			return in;
		}

		if(map == null) {
			return in;
		}

		checkOutputPacketEventType(WeightedEvent.class);
		OutputEventIterator itr = out.outputIterator();

		for (BasicEvent event : in) {

			if (learnMap) {

				// accumulate spikes
				map[event.x][event.y] += 0.1;
				if (map[event.x][event.y] > maxMap) {
					maxMap = map[event.x][event.y];
				}

			} else {

				WeightedEvent outEvent = (WeightedEvent)itr.nextOutput();
				outEvent.copyFrom(event);
				outEvent.polarity = ((PolarityEvent)event).polarity;
				outEvent.weight = (float)map[event.x][event.y];
			}
		}

		if (learnMap) {
			return in;
		}
		else {
			return out;
		}
	}

	@Override
	public void annotate(GLAutoDrawable drawable) {

		if (!isFilterEnabled()) {
			return;
		}
		if (!showMap) {
			return;
		}
		if (drawable == null) {
			return;
		}
		if (map == null) {
			return;
		}

		GL2 gl=drawable.getGL().getGL2();

		for (int x = 0; x < cameraX; x++) {
			for (int y = 0; y < cameraY; y++) {

				float green = (float)map[x][y]/(float)(learnMap ? maxMap : 1.0f);
				float red   = 1.0f - green;

				gl.glColor4f(red,green,0.0f,.9f);
				gl.glRectf(
					x-0.5f,
					y-0.5f,
					x+0.5f,
					y+0.5f);
			}
		}
	}
}

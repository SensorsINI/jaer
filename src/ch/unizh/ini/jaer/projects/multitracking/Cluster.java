package ch.unizh.ini.jaer.projects.multitracking;

import java.util.Vector;

import net.sf.jaer.event.BasicEvent;

public class Cluster {

	public Vector<BasicEvent> ListOfEvent;
	public float radiusX;
	public float radiusY;
	public float distanceToLastEvent;
	public float xDistanceToLastEvent;
	public float yDistanceToLastEvent;

	public Cluster(BasicEvent e){
		ListOfEvent=new Vector<BasicEvent>();
		ListOfEvent.add(e);
	}

	public void addEvent(BasicEvent e) {
		ListOfEvent.add(e);
	}

	public float distanceToX(BasicEvent event) {
		// TODO Auto-generated method stub
		return 0;
	}

	public float distanceToY(BasicEvent event) {
		// TODO Auto-generated method stub
		return 0;
	}

}

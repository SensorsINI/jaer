package ch.unizh.ini.jaer.projects.multitracking;

import java.util.Vector;

public class Pool {
	public Vector<Integer> xs;
	public Vector<Integer> ys;
	private int xmedian;
	private int ymedian;

public Pool(){
	this.xs=new Vector<Integer>();
	this.ys=new Vector<Integer>();

}

public int getMeanXS(){
	int mean=0;
	for (int i=1; i<xs.size(); i++){
		mean=mean+xs.get(i);
	}
	mean=mean/xs.size();
	return mean;
}

public int getMeanYS(){
	int mean=0;
	for (int i=1; i<ys.size(); i++){
		mean=mean+ys.get(i);
	}
	mean=mean/ys.size();
	return mean;
}
}
package ch.unizh.ini.jaer.projects.hopfield.orientationlearn;

/*
 * Represents an abstraction for a data point in two dimensional space
 *
 * Manas Somaiya
 * Computer and Information Science and Engineering
 * University of Florida
 *
 * Created: October 29, 2003
 * Last updated: October 30, 2003
 *
 */
 
 
 
/**
 * Represents an abstraction for a data point in two dimensional space
 * @author	Manas Somaiya	mhs@cise.ufl.edu
 */ 
public class kMeansPoint {


	/** Value in dimension x */
	private double x;
	

	/** Value in dimension y */
	private double y;
	

	/** Assigned cluster */
	private int clusterNumber;
	

	/**
	 * Creates a new instance of data point
	 *
	 * @param	_x	value in dimension x
	 * @param	_y	value in dimension y
	 */
	public kMeansPoint(double _x, double _y) {
	
		this.x = _x;
		this.y = _y;
		this.clusterNumber=0;
	} // end of kMeansPoint()
	
	
	/**
	 * Assigns the data point to a cluster
	 *
	 * @param	_clusterNumber	the cluster to which this data point is to be assigned
	 */
	public void assignToCluster(int _clusterNumber) {
	
		this.clusterNumber = _clusterNumber;
	
	} // end of assignToCluster()
	
	
	/**
	 * Returns the cluster to which the data point belongs
	 *
	 * @return	the cluster number to which the data point belongs
	 */
	public int getClusterNumber() {
	
		return this.clusterNumber;
	
	} // end of getClusterNumber()
	
	
	/**
	 * Returns the value of data point in x dimension
	 *
	 * @return	the value in x dimension
	 */
	public double getX() {
	
		return this.x;
	
	} // end of getX()
	
	
	/**
	 * Returns the value of data point in y dimension
	 *
	 * @return	the value in y dimension
	 */
	public double getY() {
	
		return this.y;
	
	} // end of getY()
	
	
	/**
	 * Returns the distance between two data points
	 *
	 * @param	dp1 	the first data point
	 * @param	dp2 	the second data point
	 * @return	the distance between the two data points
	 */
	public static double distance(kMeansPoint dp1, kMeansPoint dp2) {
	
		double result = 0;
		double resultX = dp1.getX() - dp2.getX();
		double resultY = dp1.getY() - dp2.getY();
		result = Math.sqrt(resultX*resultX + resultY*resultY);
		return result;
	
	} // end of distance()
	
	
	/**
	 * Returns a string representation of this kMeansPoint
	 *
	 * @return	a string representation of this data point
	 */
	public String toString(){
	
		return "(" + this.x + "," + this.y + ")[" + this.clusterNumber + "]";
	
	} // end of toString()
	
	
	/**
	 * Main method -- to test the kMeansPoint class
	 *
	 * @param	args	command line arguments
	 */
	public static void main(String[] args) {
	
		kMeansPoint dp1 = new kMeansPoint(-3,-4);
		kMeansPoint dp2 = new kMeansPoint(0,4);
		System.out.println(kMeansPoint.distance(dp1, dp2));
		System.out.println(dp1.getX());
		System.out.println(dp2.getY());
		dp1.assignToCluster(7);
		System.out.println(dp1.getClusterNumber());
		dp1.assignToCluster(17);
		System.out.println(dp1.getClusterNumber());
		System.out.println(dp2.getClusterNumber());
		System.out.println(dp1);
	
	} // end of main()


} // end of class
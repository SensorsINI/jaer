/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

/**
 * KMeans Implementation, Refer to http://blog.csdn.net/eaglex/article/details/6376533
 * 
 * @author minliu
 */
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import static java.lang.Float.NaN;
import java.util.ArrayList;
import java.util.Random;

public class KMeans {
	// Clusters Number
	public int ClassCount = 3;
	// Points Number
	public int InstanseNumber = 9;
	// Dimension
	public int FieldCount = 1;
	
	// Abnormal point or noise detection. The threshold = InstanseNumber/ClassCount^t.
	final static double t = 2.0;
        
	// Data matrix
	private float[][] data;
	
	// Center for every cluster
	private float[][] classData;
	
	// Noises data
	private ArrayList<Integer> noises;
	
	private ArrayList<ArrayList<Integer>> result;
	private float[] maxPerRow = null;
        private int[] initialByUser = null;


	public KMeans()
	{
		// The result is also stored in data.
		data = new float[InstanseNumber][FieldCount + 1];
		classData = new float[ClassCount][FieldCount];
		result = new ArrayList<ArrayList<Integer>>(ClassCount);
		noises = new ArrayList<Integer>();
                maxPerRow = new float[FieldCount];
	}		

	/**
	 * The cluster process:
	 * 1. Find the initial values, two options: Maximum distance and random choose.
	 * 2. Adjust until the center doesn't move any more.
	 */
	public void cluster()
	{
		// Normalization.
		normalize();                    
                                
		// The indication flag to update the initial.
		boolean needUpdataInitials = true;
		
		// Iteration times
		int times = 1;
                
                if(getInitialByUser() != null) {
                    
                    for(int i = 0; i < 3; i++) {
                        classData[i] = data[initialByUser[i]]; 
                        ArrayList<Integer> rslt = new ArrayList<Integer>();
                        rslt.add(initialByUser[i]);
                        result.add(rslt);                         
                    }
                   
                } else {
                    // Find the initials.
                    while(needUpdataInitials)
                    {
                            needUpdataInitials = false;
                            result.clear();
                            // System.out.println("Find Initials Iteration " + (times++) + " time(s) ");

                            // findInitials(); // Using the maxium distance to generate the initial center value.

                            randomInitials();  // Generate the random initial center value

                            firstClassify();

                            // Nosies
                            for(int i = 0; i < result.size();i++){
                                    if(result.get(i).size() < InstanseNumber / Math.pow(ClassCount, t)){
                                            needUpdataInitials = true;
                                            noises.addAll(result.get(i));
                                    }
                            }
                    }                    
                }
		
		// Adjust until the center doesn't move.
		Adjust();
                
                // Restore the data value and the classData value, since in normalization it's divided by the maxium.
                for(int i = 0; i < InstanseNumber; i++){
                    for(int j = 0;j < FieldCount; j++){
                        data[i][j] = data[i][j] * maxPerRow[j];                        
                    }
                }              
                int i = 0;
                for(i = 0; i < ClassCount; i++) {
                    for(int j = 0; j < FieldCount; j++)
                    classData[i][j] = classData[i][j] * maxPerRow[j];
                }
	}
	
	/**
	 * Normalization.
	 */
	public void normalize(){
		
		// Find the maximum.
		for(int i = 0;i<InstanseNumber;i++){
			for(int j = 0;j < FieldCount ;j++){
				if(data[i][j] > maxPerRow[j])
					maxPerRow[j] = data[i][j];
			}
		}
		

		// Normalization
		for(int i = 0;i<InstanseNumber;i++){
			for(int j = 0;j < FieldCount ;j++){
                                // If the maximum value is 0, then we don't normalize it.
                                if(maxPerRow[j] == 0) {
                                    continue;
                                }
				data[i][j] = data[i][j]/maxPerRow[j];
			}
		}
	}
        /**
	 * Using random value as the initial center.
	 */
        
	public void randomInitials() {
            Random rand = new Random();
            boolean[]  bool = new boolean[InstanseNumber];
            int randInt[] = new int[ClassCount];
            
            // Generate ClassCount different indexes as the initial center.
            for(int i = 0; i < ClassCount; i++) {
                do {
                    randInt[i]  = rand.nextInt(InstanseNumber);
                }while(bool[randInt[i]]);

                bool[randInt[i]] = true;
                classData[i] = data[randInt[i]];
                ArrayList<Integer> rslt = new ArrayList<Integer>();
                rslt.add(randInt[i]);
                result.add(rslt);
            }           
        }
        
	/**
	 *  Try to find the initial values.
	 */
	public void findInitials(){
		
		int i,j,a,b;
		i = j = a = b = 0;
		
		// The farthest point.
		float maxDis = 0;
		
		int alreadyCls = 2;
		
		ArrayList<Integer> initials = new ArrayList<Integer>();
		
		for(;i < InstanseNumber;i++)
		{
			// Noise.
			if(noises.contains(i))
				continue;
			//long startTime = System.currentTimeMillis();
			j = i + 1;
			for(; j < InstanseNumber;j++)
			{	
				// Noise.
				if(noises.contains(j))
					continue;
				// Find the maximum value and store it.
				float newDis = calDis(data[i], data[j]);
				if( maxDis < newDis)
				{
					a = i;
					b = j;
					maxDis = newDis;
				}
			}
			//long endTime = System.currentTimeMillis();
			//System.out.println(i + " Vector Caculation Time:" + (endTime - startTime) + " ms");
		}
		
		
		initials.add(a);
		initials.add(b);
		classData[0] = data[a];
		classData[1] = data[b];
		
		ArrayList<Integer> resultOne = new ArrayList<Integer>();
		ArrayList<Integer> resultTwo = new ArrayList<Integer>();
		resultOne.add(a);
		resultTwo.add(b);
		result.add(resultOne);
		result.add(resultTwo);
		
		
		while( alreadyCls < ClassCount){
			i = j = 0;
			float maxMin = 0;
			int newClass = -1;
			
			for(;i < InstanseNumber;i++){
				float min = 0;
				float newMin = 0;
				if(initials.contains(i))
					continue;
				// Remove the noises
				if(noises.contains(i))
					continue;
				for(j = 0;j < alreadyCls;j++){
					newMin = calDis(data[i], classData[j]);
					if(min == 0 || newMin < min) {
                                            min = newMin;
                                        }
                                    }
			
				if(min > maxMin)
				{
					maxMin = min;
					newClass = i;
				}
					
			}                  

			//System.out.println("NewClass " + newClass);
			initials.add(newClass);

			classData[alreadyCls++] = data[newClass];
			ArrayList<Integer> rslt = new ArrayList<Integer>();
			rslt.add(newClass);
			result.add(rslt);
		}
		
		
	}
	
	/**
	 *  The first attempt to classfy
	 */
	public void firstClassify()
	{
		for(int i = 0; i < InstanseNumber;i++)
		{
			float min = 0f;
			int clsId = -1;
                        
			for(int j = 0;j < classData.length;j++){
				// Euclidean distance
				float newMin = calDis(classData[j], data[i]);
				if(clsId == -1 || newMin < min){
					clsId = j;
					min = newMin;
					
				}
			}
			if(!result.get(clsId).contains(i))
			{
				result.get(clsId).add(i);
			}
		}
		
	}
	
	/**
	 * Iteration until the center location doesn't change any more.
	 */
	public void Adjust()
	{
		// The change flag
		boolean change = true;
		
		int times = 1;
		while(change){
			// Reset
			change = false;
			// System.out.println("Adjust Iteration " + (times++) + " time(s) ");
			
			// Calculate the mean again
			for(int i = 0;i < ClassCount; i++){
				// Origin data
				ArrayList<Integer> cls = result.get(i);
				
				// New mean value
				float[] newMean = new float[FieldCount];
				
				// Mean value calculation
				for(Integer index:cls){
					for(int j = 0;j < FieldCount ;j++)
						newMean[j] += data[index][j];
				}
				for(int j = 0;j < FieldCount ;j++)
					newMean[j] /= cls.size();
				if(!compareMean(newMean, classData[i])){
					classData[i] = newMean;
					change = true;
				}
			}
			// Clear the previous result
			for(ArrayList<Integer> cls:result)
				cls.clear();
			
			// Using the new class center data.
			for(int i = 0;i < InstanseNumber;i++)
			{
				float min = 0f;
				int clsId = -1;
				for(int j = 0;j < classData.length;j++){
					float newMin = calDis(classData[j], data[i]);
					if(clsId == -1 || newMin < min){
						clsId = j;
						min = newMin;
					}
				}
				data[i][FieldCount] = clsId;
				result.get(clsId).add(i);
			}			
		}
		
		
	}
	
	/**
	 * Using the Euclidean distance.
	 * 
	 * @param a		sample a
	 * @param b		sample b
	 * @return		Euclidean distance between a and b.
	 */
	private float calDis(float[] aVector,float[] bVector)
	{
		double dis = 0;
		int i = 0;
		
		// The last row of the data is the result, we don't need to calculate here.
                // This function will be used to calculate the distance between classData and other points.
		for(i = 0; i < aVector.length;i++)
			dis += Math.pow(bVector[i] - aVector[i],2);
		dis = Math.pow(dis, 0.5);
		return (float)dis;
	}
	
	/**
	 * Check the means of a and b is same or not.
	 * 
	 * @param a	vector a
	 * @param b     vector b
	 * @return
	 */
	private boolean compareMean(float[] a,float[] b)
	{
		if(a.length != b.length)
			return false;
		for(int i =0;i < a.length;i++){
			if(a[i] > 0 &&b[i] > 0&& a[i] != b[i]){
				return false;
			}	
		}
		return true;
	}
	

        public float[][] getData() {
            return data;
        }

        public void setData(float[][] data) {
            this.data = data;
        }

        public ArrayList<ArrayList<Integer>> getResult() {
            return result;
        }

        public void setResult(ArrayList<ArrayList<Integer>> result) {
            this.result = result;
        }

        public float[][] getClassData() {
            return classData;
        }

        public void setClassData(float[][] classData) {
            this.classData = classData;
        }

        public int[] getInitialByUser() {
            return initialByUser;
        }

        public void setInitialByUser(int[] initialByUser) {
            this.initialByUser = initialByUser;
        }
        
        
}


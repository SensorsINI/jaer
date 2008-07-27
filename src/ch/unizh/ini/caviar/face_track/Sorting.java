/* sort.java
 *
 * Created on 14. april 2008, 12:46
 *
 * Author Alexander Tureczek
 *
 *This file implements the selection sort algorithm. The selectionSort method is 
 *copied from Lewis/Loftus "Java, software solutions". the algorithm is sorting 
 *an array of numbers in O(n^2) time. 
 *
 *Also implemented is the Heap Sort algorithm that sorts any vector in n*lg(n) 
 *time.
 *
 */

package ch.unizh.ini.caviar.face_track;
import java.lang.Math;


public class Sorting 
{
    public Sorting(){}
   
    //Contains an implementation of the selection sort algorithm.
    public float[] selectionSort(float[] numbers) 
    {
        float temp;
        short min;
        
        //running through the elements and moving the smaller to the left. 
        for (short index=0; index < numbers.length-1; index++)
        {
            min = index;
            for (short scan =(short) (index + 1); scan < numbers.length; scan++)
            {
                if (numbers[scan]<numbers[min])
                    min = scan;
                
                //Swaping values. 
                temp=(float) numbers[min];
                numbers[min]=(float) numbers[index];
                numbers[index]=(float) temp;
            }
        }
    return numbers;
    }
       
    
    //prepares a nx2 array for heap sort row vise. 
    public float[][] medianHeap(float[][] list)
    {
        float[] list_a = new float[list.length];
        float[] list_b = new float[list.length];
        
        //initializing the median calculation. 
        Descriptive median = new Descriptive();
        
        //If the list is empty then the list is returned with zero elements. 
        if (list.length==0)
        {
            float[][] median_list = new float[list.length+1][2];
            median_list[0][0] = 0;
            median_list[0][1] = 0;
            return (float[][]) median_list;
        }
        //else the list is sorted and the median is calculated through a call to 
        //Descriptive class. 
        else
        {
            float[][] median_list = new float[list.length][2];
            
            for (short index=0; index<list.length; index++)
            {
                list_a[index] =(float) list[index][0];
                list_b[index] =(float) list[index][1];
            }
            list_a = (float[]) heapSort(list_a);
            float med_x =(float) median.Median(list_a);

            list_b = heapSort(list_b);
            float med_y = median.Median(list_b);

            median_list[0][0] = med_x;
            median_list[0][1] = med_y;
       
        return (float[][]) median_list;  
        }
    }
    
    // Contains an implementation of the heapsort algorithm.T.H. Cormen et.al, 
    //"Introduction to algorithms"
    public float[] heapSort(float[] numbers) 
    {
        float[] A=numbers;
        short heap_size=(short) A.length;
        float temp;
        A=build_heap(A);
        
        for (short index=(short)(A.length-1); index>=1; index--)
        {
            temp=(float) A[0];
            A[0]=A[index];
            A[index]=(float) temp;
            
            heap_size=(short) (heap_size - 1);
            heapify(A,(short)0,heap_size);
        }
        return A;
    } 
    
    //Building the heap that is being sorted with heapify.
    private float[] build_heap(float[] A)
    {
        short heap_size=(short)A.length;
        for (short index = (short) Math.floor(A.length/2); index>=0; index--)
        {
            A=heapify(A,index,heap_size);
        }

        return A;          
    }
            
    //This is an implementation of the Max-heapify p.130 T.H. Cormen et.al, 
    //"Introduction to algorithms"
    private float[] heapify(float[] A, short i, short heap_size)
    {
        //creating children 
        //Left, corrected for 0 indices
        short l=(short) (2 * i + 1);
        //Right, corrected for 0 indices
        short r=(short) (2 * i + 2);
        
        short largest=0;
        
        //int heap_size = A.length;
        if (l<=heap_size-1 && A[l]>A[i])
        {
            largest=l;
        }
        else 
            largest=i;
        
        if (r<=heap_size-1 && A[r]>A[largest])
        {
            largest=r;
        }
        if (largest!=i)
        {
            float temp=(float) A[i];
            A[i]=(float) A[largest];
            A[largest]=(float) temp;
            heapify(A,largest,heap_size);
        }
        return A;
    }
}

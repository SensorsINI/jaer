/*
 * EpipolarRectification.java
 *
 * Created on June 13, 2008, 12:33 PM
 *
 * This filter apply the epipolar correction (previously computed using matlab or other tools) 
 * to one retina only here (epipolar correction is appply to two retinas used in stereo 
 * but slightly angled toward each other) so that pixel at the same height appears 
 * on the same y line)
 * One must select which retina to correct, left or non left
 * (should be easily adapted to any configuration)
 * To correct both retinae, one must use two instances of this filter
  */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;

import java.io.*;
import java.util.*;

/**
 * This filter apply the epipolar correction (loading pixel correspondance tables 
 * previously computed using matlab or other tools) 
 * to one retina only here (epipolar correction is appply to two retinas used in stereo 
 * but slightly angled toward each other) so that pixel at the same height appears 
 * on the same y line)
 * One must select which retina to correct, left or non left
 * (should be easily adapted to any configuration)
 * To correct both retinae, one must use two instances of this filter
 * @author rogister
 */
public class EpipolarRectification extends EventFilter2D implements Observer  {

    @Override
    public String getDescription() {
        return "Epipolar rectification for stereoboard mounted retinae";
    }
    protected final int RIGHT = 1;
    protected final int LEFT = 0;
    private boolean left = getPrefs().getBoolean("EpipolarRectification.left", true);
    private boolean right = getPrefs().getBoolean("EpipolarRectification.right", true);

   protected final int ON = 1;
   protected final int OFF = 0;
   private int x_size = getPrefs().getInt("EpipolarRectification.x_size", 128);
   private int y_size = getPrefs().getInt("EpipolarRectification.y_size", 128);

   boolean logLoudly = false;
   boolean runFirst = false;
   
    private boolean i1 = getPrefs().getBoolean("EpipolarRectification.i1", true);
    private boolean i2 = getPrefs().getBoolean("EpipolarRectification.i2", true);
    private boolean i3 = getPrefs().getBoolean("EpipolarRectification.i3", true);
    private boolean i4 = getPrefs().getBoolean("EpipolarRectification.i4", true);

   
   Hashtable indexLookup = new Hashtable();
    
    public EpipolarRectification(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
    }
    
   
    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number put in 
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (!filterEnabled) {
            return in;
        }
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        
        if(!runFirst){
            runFirst = true;
            resetFilter();
            logLoudly = true;
        }
        
        resetOut();
        
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for (Object e : in) {
            if (e instanceof BinocularEvent) {
                BinocularEvent i = (BinocularEvent) e;
                int leftOrRight = i.eye == BinocularEvent.Eye.LEFT ? 0 : 1; //be sure if left is same as here

                BinocularEvent o = (BinocularEvent) outItr.nextOutput();
                 o.copyFrom(i);

                if (left && (leftOrRight == LEFT))  {
                    correctIndex(i,o);
                        
                    
                       
                } else if (right && (leftOrRight == RIGHT)) {
                    correctIndex(i,o);
                  
                    
                } //else {
                   //o.copyFrom(i);
                   
               // }
            } 

        }

        return out;
    }
    
    
    // get the new x,y location for an incoming event
    private boolean correctIndex(BinocularEvent evin, BinocularEvent evout){
        int ind = (evin.x)*y_size+(y_size-evin.y);
        Integer newInd = (Integer)indexLookup.get(new Integer(ind));
        if(newInd==null){
           // System.out.println ("correctIndex: error, newInd: x "+newInd);
            evout = null;
            return false;
        }
        int newx = Math.abs(newInd.intValue()/y_size);
        int newy = newInd.intValue() - newx*y_size;
        
        if(newx>=x_size||newy>=y_size){
             System.out.println ("correctIndex: error, over size: x "+newx+" y "+newy+ "newInd "+newInd.intValue());
             evout = null;
            return false;
        }
       // evout.copyFrom(evin);
        
        evout.x = (short)(newx);
        evout.y = (short)(y_size-newy);
        
        return true;
    }
    
    // reset and reload pixel correspondances tables
    // there are four index tables per retina as for now
    // to allow for pixel interpolation
    private void resetIndexesLookup(){
       
        if(runFirst){
          indexLookup.clear();

        if (left) {
                if (i1) {
                    addToIndexesLookup("indices_gauche1.txt", "indices_new_gauche.txt");
                }
                if (i2) {
                    addToIndexesLookup("indices_gauche2.txt", "indices_new_gauche.txt");
                }
                if (i3) {
                    addToIndexesLookup("indices_gauche3.txt", "indices_new_gauche.txt");
                }
                if (i4) {
                    addToIndexesLookup("indices_gauche4.txt", "indices_new_gauche.txt");
                }
            } else {
                if (i1) {
                    addToIndexesLookup("indices_droite1.txt", "indices_new_droite.txt");
                }
                if (i2) {
                    addToIndexesLookup("indices_droite2.txt", "indices_new_droite.txt");
                }
                if (i3) {
                    addToIndexesLookup("indices_droite3.txt", "indices_new_droite.txt");
                }
                if (i4) {
                    addToIndexesLookup("indices_droite4.txt", "indices_new_droite.txt");
                }
            }
        }

    }
    
    
    private void addToIndexesLookup( String oldIndexFileName, String newIndexFileName ){
        
        
        int[] old_ind = readIndexesFromTextFile(oldIndexFileName);
         int[] new_ind = readIndexesFromTextFile(newIndexFileName);
         if(old_ind==null||new_ind==null){
              System.out.println ("addToIndexesLookup: file "+oldIndexFileName+" error, null array: "+old_ind+" "+new_ind);
             
             return;
         }
         if(old_ind.length!=new_ind.length){
              System.out.println ("addToIndexesLookup: file "+newIndexFileName+" error, different size: "+old_ind.length+" "+new_ind.length);
              return;
         }
        
          for(int i=0;i<old_ind.length;i++){
             // from matlab where indexes start at 1, so we remove 1
              if(old_ind[i]!=0){
             Object o = indexLookup.put(new Integer(old_ind[i]-1), new Integer(new_ind[i]-1));
                // int j = old_ind[i]-1;
                //if (o!=null)  System.out.println ("addToIndexesLookup: file "+newIndexFileName+" already in : "+j);
             
              }
         }
        
    }
   
    // load index file, where x,y pixel location is coded the Matlab way
    private int[] readIndexesFromTextFile (String filename ) {
    
    File file = new File (filename);
   
   
    if (file == null || !file.exists ()) {
      if(logLoudly) System.out.println ("readIndexesFromTextFile: couldn't read: "+filename);
      return null;
    }

    // Count the number of lines with the string of interest.
    int i = 0;
    int[] res = new int[x_size*y_size];
    
    try {
      // Create a FileReader and then wrap it with BufferedReader.
      FileReader file_reader = new FileReader (file);
      BufferedReader buf_reader = new BufferedReader (file_reader);

      // Read each line of the file and look for the string of interest.
     
      do {
         String line = buf_reader.readLine ();
         
         if (line == null) break;
         res[i]=Integer.parseInt(line);
         i++;
      } while (true);
      buf_reader.close ();
    }
    catch (Exception e) {
        System.out.println ("readIndexesFromTextFile exception =" + e );
    }
    
    return res;
    
  }
 
    
   public Object getFilterState() {
        return null;
   }
    
   
    
    synchronized public void resetFilter() {
        resetIndexesLookup();
    }
    
    
    public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
    }
    
    public void initFilter() {
        
    }

   

    public void setLeft(boolean left){
        this.left = left;
        
        getPrefs().putBoolean("EpipolarRectification.left",left);
    }
    public boolean isLeft(){
        return left;
    }
    
     public void setRight(boolean right){
        this.right = right;
        
        getPrefs().putBoolean("EpipolarRectification.right",right);
    }
    public boolean isRight(){
        return right;
    }
    
    public void setX_size(int x_size) {
        this.x_size = x_size;
        
        getPrefs().putInt("EpipolarRectification.x_size",x_size);
    }
    public int getX_size() {
        return x_size;
    }
    
    public void setY_size(int y_size) {
        this.y_size = y_size;
        
        getPrefs().putInt("EpipolarRectification.y_size",y_size);
    }
    public int getY_size() {
        return y_size;
    }
    
    
    public void setI1(boolean i1){
        this.i1 = i1;
        
        getPrefs().putBoolean("EpipolarRectification.i1",i1);
    }
    public boolean isI1(){
        return i1;
    }
        
    public void setI2(boolean i2){
        this.i2 = i2;
        
        getPrefs().putBoolean("EpipolarRectification.i2",i2);
    }
    public boolean isI2(){
        return i2;
    }
        
    public void setI3(boolean i3){
        this.i3 = i3;
        
        getPrefs().putBoolean("EpipolarRectification.i3",i3);
    }
    public boolean isI3(){
        return i3;
    }
        
    public void setI4(boolean i4){
        this.i4 = i4;
        
        getPrefs().putBoolean("EpipolarRectification.i4",i4);
    }
    public boolean isI4(){
        return i4;
    }
    
    
}

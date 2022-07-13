package org.ine.telluride.jaer.tell2022;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Logger;

public class RodSequence extends ArrayList<RodPosition> implements Serializable {

    static Logger log = Logger.getLogger("GoingFishing");
    static String FILENAME = "GoingFishingRodSequence.ser";

    private int index;
    public long durationMs=0;

    public RodSequence(int index) {
        this.index = index;
    }
    
    public void save() {
        if(size()==0){
            log.warning("saving sequence of zero length");
        }
        FileOutputStream fos = null;
        try {
            String fn=FILENAME+index;
            fos = new FileOutputStream(fn);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this);
            oos.close();
            log.info("saved "+this+" to file "+fn);
        } catch (Exception ex) {
            log.warning("Could not save fishing rod sequence: " + ex.toString());
        }
    }

    public void load(int index) throws IOException, ClassNotFoundException{
            FileInputStream fis = new FileInputStream(FILENAME+index);
            ObjectInputStream ois = new ObjectInputStream(fis);
            RodSequence rodSequence= (RodSequence) ois.readObject();
            ois.close();
            clear();
            for(RodPosition p:rodSequence){
                add(p);
            }
            this.index=rodSequence.index;
            this.durationMs=rodSequence.durationMs;
    }
    
    

    @Override
    public void clear(){
        super.clear();
        durationMs=0;
    }
    
    @Override
    public boolean add(RodPosition p){
        durationMs+=p.timeMs;
        return super.add(p);
    }
    
    @Override
    public String toString(){
        return String.format("Fishing rod sequence #%d with %,d positions lasting total %,d ms",index, size(),durationMs);
    }

    /**
     * @return the index
     */
    public int getIndex() {
        return index;
    }
    
}

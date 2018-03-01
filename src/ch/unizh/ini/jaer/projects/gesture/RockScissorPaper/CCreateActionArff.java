/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;

/* &&To
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

 import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
* &&From
/**
 *
 * @author Eun Yeong Ahn
 */
public class CCreateActionArff {
  /* &&From
    static int m_findex = 0;
    
    FastVector atts;
    Instances data;
    double[] vals;

    CCreateActionArff(){
        // 1. set up attributes
        atts = new FastVector();
        // - numeric
        atts.addElement(new Attribute("x")); //x
        atts.addElement(new Attribute("y")); //y
        atts.addElement(new Attribute("class")); //y
       // atts.addElement(new Attribute("value")); //value
        // - string
        //atts.addElement(new Attribute("class", (FastVector) null)); //class
        // 2. create Instances object
        data = new Instances("RockScissorPaper", atts, 0);
    }
    void CreateArff(EventPacket<?> in, int c_value){

        String fname = "./data/"+ Integer.toString(m_findex++) +"_"+GetAction(Integer.toString(c_value))+".arff";
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(fname));
            data.delete();

            //for(BasicEvent ev:in){
            for(int i = 0; i< in.getSize(); i++){
                BasicEvent ev = in.getEvent(i);
               // first instance
                //if(ev.getType() == 0){
                     vals = new double[data.numAttributes()];
                    // - numeric
                    vals[0] = ev.x;
                    vals[1] = ev.y;
                    vals[2] = c_value;
                    //vals[2] = data.attribute(2).addStringValue(c_value);
                    // add
                    data.add(new Instance(1.0, vals));
              // }
               
            }
            // 4. output data
            // System.out.println(data);
            writer.write(data.toString());
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(CCreateActionArff.class.getName()).log(Level.SEVERE, null, ex);
        } 
    };

    private String GetAction(String c_value) {
        if(c_value == null ? "1" == null : c_value.equals("1")){
            return "Rock";
        }else if(c_value == null ? "2" == null : c_value.equals("2")){
            return "Scissor1";
        }else if(c_value == null ? "3" == null : c_value.equals("3")){
            return "Scissor2";
        }else if(c_value == null ? "4" == null : c_value.equals("4")){
            return "Paper";
        }
        return "";
    }
   * &&To
   */
}

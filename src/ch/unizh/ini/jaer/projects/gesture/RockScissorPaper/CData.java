/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;
/* &&From
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
 import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
 * &&To
 */

/*
 * Create data file
 */
public class CData {
    /* &&From
    FastVector m_atts;
    Instances m_data;
    int[] m_count;
    int m_instPerClass;
    int m_noOfClass;

    public CData(int noOfAttr, int noOfInstances) {
        m_atts = new FastVector();
        m_instPerClass = noOfInstances;
        
        for(int i = 0; i < noOfAttr-1; i++){
            m_atts.addElement(new Attribute(Integer.toString(i)));
        }

        FastVector cVal = new FastVector();
        cVal.addElement("1"); cVal.addElement("2"); cVal.addElement("3");//cVal.addElement("4");
        m_atts.addElement(new Attribute("class", cVal));
        m_data = new Instances("RockScissorPaper", m_atts, 0);

        m_noOfClass = 3;
        m_count = new int[m_noOfClass]; //no of instances for each class
        for(int i = 0; i< m_noOfClass; i++)
            m_count[i] = 0;
    }

    void AddInstance(Instance inst, int c){
        if(m_count[c] < m_instPerClass){
            m_data.add(new Instance(inst));
            m_count[c]++;
        }
    }

    boolean IsNoInstances(){
        for(int i = 0; i< m_noOfClass; i++)
            if(m_count[i] != m_instPerClass)
                return false;
        return true;
    }

    void Print(){
        BufferedWriter writer = null;
        try {
            String fname = "./data/Training.arff";
            writer = new BufferedWriter(new FileWriter(fname));
            System.out.println(m_data);
            writer.write(m_data.toString());
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(CData.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                writer.close();
            } catch (IOException ex) {
                Logger.getLogger(CData.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    int numInstances(){
        return m_data.numInstances();
    }
     * &&To
     */

}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;


import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
 
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;




/**
 * For taking statisics on running networks, printing their results to XML
 * @author oconnorp
 */
public class StatCollector {
    
        
    public static void main(String[] args)
    {
        dynamicsTest();
    }
    
    public static void dynamicsTest()
    {
        
        // Construct Network
        NetController<AxonSTP,AxonSTP.Globals,UnitLIF.Globals> nc=new NetController(NetController.AxonTypes.STP);
        Network<AxonSTP> net=nc.net;
        UnitLIF.Globals ug=nc.unitGlobals;
        AxonSTP.Globals lg=nc.axonGlobals;
        
        
        float initRate=100;
        int timeMicros=1000000;
                
        
        lg.delay=10000;
        
        lg.doRandomJitter=true;
        lg.randomJitter=100;
        
        ug.useGlobalThresh=true;
        
        nc.readXML();
        
        
//        nc.setForwardStrengths(new boolean[] {false,true,false,true});
//        nc.setBackwardStrengths(new boolean[] {true,true,false,true});
        
        
        ArrayList<Stat> stats=new ArrayList();
        
        nc.setRecordingState(true);
        nc.startDisplay();
        for (float thresh=1; thresh<5; thresh+=.3)
            for (int tau=10000; tau<300000; tau*=1.3)
                for (int tref=1000; tref<100000; tref*=1.5)
                {
                    
                    Stat s=new Stat();
                    s.thresh=thresh;
                    s.tau=tau;
                    s.tref=tref;
                    
                    
                    ug.thresh=thresh;
                    ug.tau=tau;
                    ug.tref=tref;
                    
                    nc.reset();
                    nc.sim.generateInputSpikes(initRate,timeMicros,0,3);
                                     
                    nc.sim.controlledTime=false;
                    nc.sim.simTimeSeconds=5;
                    nc.sim.run();
                    
                    s.nSpikes=nc.recorder.spikes.size();
                    s.endTime=net.time;
                    
                    System.out.println(s.nSpikes+" Spikes.  Final time: "+s.endTime/1000000f);
                    
                    stats.add(s);
                }
        buildXMLFile(stats);
        
    }
    
    
//    public static ArrayList netRun(NetController nc,Stat st)
//    {
//        
//        
//        
//    }
    
    
    
    public static class Stat
    {
        float thresh;
        float tau;
        float tref;
        
        int nSpikes;
        int endTime;
                
    }
    
    
    public static void buildXMLFile(ArrayList<Stat> stats)
    {
        
        try {
 
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
 
//		// root elements
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("Stats");
		doc.appendChild(rootElement);
// 
                for (Stat s:stats)
                {
                    Element stat = doc.createElement("Stat");
                    
                    
                    Element el;
                    
                    el= doc.createElement("thresh");
                    el.appendChild(doc.createTextNode(""+s.thresh));
                    stat.appendChild(el);
                    
                    el= doc.createElement("tau");
                    el.appendChild(doc.createTextNode(""+s.tau));
                    stat.appendChild(el);
                    
                    el= doc.createElement("tref");
                    el.appendChild(doc.createTextNode(""+s.tref));
                    stat.appendChild(el);
                    
                    el= doc.createElement("nSpikes");
                    el.appendChild(doc.createTextNode(""+s.nSpikes));
                    stat.appendChild(el);
                    
                    el= doc.createElement("endTime");
                    el.appendChild(doc.createTextNode(""+s.endTime));
                    stat.appendChild(el);
                    
                    
                    rootElement.appendChild(stat);
                    
//                    PropertyDescriptor[] props=new PropertyDescriptor[0];
//                    try {
//                        props = Introspector.getBeanInfo(s.getClass()).getPropertyDescriptors();
//                    } catch (IntrospectionException ex) {
//                        Logger.getLogger(StatCollector.class.getName()).log(Level.SEVERE, null, ex);
//                    }
//                                       
//                    
//                    for (Stat s:stats)
//                    {   
//                        
//                        
//                        
//                         Element thresh = doc.createElement("firstname");
//                    }
//                        
                    
                    
                    
                   
                            
                    
                }
                
                
//		// staff elements
//		Element staff = doc.createElement("Staff");
//		rootElement.appendChild(staff);
// 
//		// set attribute to staff element
//		Attr attr = doc.createAttribute("id");
//		attr.setValue("1");
//		staff.setAttributeNode(attr);
// 
//		// shorten way
//		// staff.setAttribute("id", "1");
// 
//		// firstname elements
//		Element firstname = doc.createElement("firstname");
//		firstname.appendChild(doc.createTextNode("yong"));
//		staff.appendChild(firstname);
// 
//		// lastname elements
//		Element lastname = doc.createElement("lastname");
//		lastname.appendChild(doc.createTextNode("mook kim"));
//		staff.appendChild(lastname);
// 
//		// nickname elements
//		Element nickname = doc.createElement("nickname");
//		nickname.appendChild(doc.createTextNode("mkyong"));
//		staff.appendChild(nickname);
// 
//		// salary elements
//		Element salary = doc.createElement("salary");
//		salary.appendChild(doc.createTextNode("100000"));
//		staff.appendChild(salary);
 
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
                
                JFileChooser fc=new JFileChooser();
                int returnVal = fc.showSaveDialog(null);
                File file=null;
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    file = fc.getSelectedFile(); //This grabs the File you typed
                }
                if (file==null)
                {   System.out.println("Null file");
                    return;
                }
                
                StreamResult result = new StreamResult(file);
//		StreamResult result = new StreamResult(new File("C:\\file.xml"));
 
		// Output to console for testing
		// StreamResult result = new StreamResult(System.out);
 
		transformer.transform(source, result);
 
		System.out.println("File saved!");
 
	  } catch (ParserConfigurationException pce) {
		pce.printStackTrace();
	  } catch (TransformerException tfe) {
		tfe.printStackTrace();
	  }
    }
        
    
    
    
    
}

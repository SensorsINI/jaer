package ch.unizh.ini.jaer.projects.hopfield.orientationlearn;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


public class TrainingData  {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5427266466002762971L;
	private Vector<Map<String, String>> trainingMaterial;
	//should hold several bitmap data at local disk to be loaded as training data
	//once loaded should be used to train the hopfield network
	
	
	public TrainingData() {
		trainingMaterial = new Vector<Map<String, String>>();
		
		parseXML("learning.xml");
		//load appropriate jpegs
		//give appropriate names
		
	}
	
	private void parseXML(String xmlPath){
		 try {

	            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
	            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
	            URL xmlURL = getClass().getResource("/ch/unizh/ini/jaer/projects/hopfield/orientationlearn/resources/"+ xmlPath);
	            Document doc = docBuilder.parse ( xmlURL.getFile());

	            // normalize text representation
	            doc.getDocumentElement ().normalize ();
	          

	            NodeList learningPatterns = doc.getElementsByTagName("learningPattern");
	          
	            for(int s=0; s<learningPatterns.getLength() ; s++){


	                Node learningPatternNode = (Node) learningPatterns.item(s);
	                if(learningPatternNode.getNodeType() == Node.ELEMENT_NODE && learningPatternNode.getAttributes().getNamedItem("train") != null){



	                    String name = learningPatternNode.getAttributes().getNamedItem("name").getNodeValue();
	                    String filePath = learningPatternNode.getAttributes().getNamedItem("path").getNodeValue();
	                    
	                    //add them to an array
	                    Map<String,String> finalMaterial = new HashMap<String,String>();
	                    finalMaterial.put("name", name);
	                    finalMaterial.put("path",filePath);
	                    trainingMaterial.add(finalMaterial);
	                    //------


	                }//end of if clause


	            }//end of for loop with s var24


	        }catch (SAXParseException err) {
	        System.out.println ("** Parsing error" + ", line " 
	             + err.getLineNumber () + ", uri " + err.getSystemId ());
	        System.out.println(" " + err.getMessage ());

	        }catch (SAXException e) {
	        Exception x = e.getException ();
	        ((x == null) ? e : x).printStackTrace ();

	        }catch (Throwable t) {
	        t.printStackTrace ();
	        }
	}
	
	public int getNumberOfElements(){
		return trainingMaterial.size();
	}
	
	public String getNameOfTrainingMaterial(int numberOfPattern){
		return trainingMaterial.get(numberOfPattern).get("name");
		
	}
	
	public String getPathOfTrainingMaterial(int numberOfPattern){
		return trainingMaterial.get(numberOfPattern).get("path");
		
	}
	
}

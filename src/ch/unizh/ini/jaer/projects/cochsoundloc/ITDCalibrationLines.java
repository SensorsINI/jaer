/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * Stores all lines for the calibration of the ITDFilter.
 *
 * @author Holger
 */
public class ITDCalibrationLines {

    private ITDCalibrationLine[][] lines;
    int maxNumOfLines = 0;
    public Logger log=Logger.getLogger("EventFilter");

    public void loadCalibrationFile(String calibrationFilePath) {
        log.info("called loadCalibrationFile()");
        //get the calibration lines
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom;
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(calibrationFilePath); //-----------------------------------Change this to allow spaces in filepath
            Element docEle = dom.getDocumentElement();
            NodeList nlProperties = docEle.getElementsByTagName("Properties");
            if (nlProperties != null && nlProperties.getLength() > 0) {
                for (int i = 0; i < nlProperties.getLength(); i++) {
                    Element el = (Element) nlProperties.item(i);
                    maxNumOfLines = Integer.parseInt(getTextValue(el, "maxNumOfLines"));
                    log.info("read xml: maxNumOfCalibrationLines="+maxNumOfLines);
                }
            }
            lines = new ITDCalibrationLine[32][maxNumOfLines];
            NodeList nl = docEle.getElementsByTagName("line");
            if (nl != null && nl.getLength() > 0) {
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    int chan = Integer.parseInt(getTextValue(el, "chan"));
                    int j=0;
                    while (j<maxNumOfLines && lines[chan][j].getSize()==0)
                        j++;
                    lines[chan][j].setStart(Integer.parseInt(getTextValue(el, "start")));
                    lines[chan][j].setEnd(Integer.parseInt(getTextValue(el, "end")));
                    lines[chan][j].setC(Double.parseDouble(getTextValue(el, "c")));
                    lines[chan][j].setM(Double.parseDouble(getTextValue(el, "m")));
                    //log.info("read xml: chan:"+chan+" start:"+start+" end:"+end+" m:"+m+" c:"+c);
                }
            }
        } catch (ParserConfigurationException pce) {
            log.warning("while loading xml calibration file, caught exception " + pce);
            pce.printStackTrace();
        } catch (SAXException se) {
            log.warning("while loading xml calibration file, caught exception " + se);
            se.printStackTrace();
        } catch (IOException ioe) {
            log.warning("while loading xml calibration file, caught exception " + ioe);
            ioe.printStackTrace();
        }
    }

    private String getTextValue(Element ele, String tagName) {
		String textVal = null;
		NodeList nl = ele.getElementsByTagName(tagName);
		if(nl != null && nl.getLength() > 0) {
			Element el = (Element)nl.item(0);
			textVal = el.getFirstChild().getNodeValue();
		}

		return textVal;
	}

}

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
 * Stores all gaussians for the calibration of the ITDFilter.
 * 
 * @author Holger
 */
public class ITDCalibrationGaussians {

    private ITDCalibrationGaussian[][][] gaussians;
    public Logger log = Logger.getLogger("EventFilter");
    private int maxITD;
    private int NumOfBins;
    private int NumOfChannels;
    private int NumOfGaussians;

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

                    this.maxITD = Integer.parseInt(getTextValue(el, "maxITD"));
                    log.info("read xml: maxITD=" + this.maxITD);

                    this.NumOfBins = Integer.parseInt(getTextValue(el, "NumOfBins"));
                    log.info("read xml: NumOfBins=" + this.NumOfBins);

                    this.NumOfChannels = Integer.parseInt(getTextValue(el, "NumOfChannels"));
                    log.info("read xml: NumOfChannels=" + this.NumOfChannels);

                    this.NumOfGaussians = Integer.parseInt(getTextValue(el, "NumOfGaussians"));
                    log.info("read xml: NumOfGaussians=" + this.NumOfGaussians);
                }
            }
            try {
                gaussians = new ITDCalibrationGaussian[this.NumOfChannels][this.NumOfBins][this.NumOfGaussians];
                for (int i1 = 0; i1 < this.NumOfChannels; i1++) {
                    for (int i2 = 0; i2 < this.NumOfBins; i2++) {
                        for (int i3 = 0; i3 < this.NumOfGaussians; i3++) {
                            gaussians[i1][i2][i3] = new ITDCalibrationGaussian();
                        }
                    }
                }
                NodeList nl = docEle.getElementsByTagName("gaussian");

                if (nl != null && nl.getLength() > 0) {
                    for (int i = 0; i < nl.getLength(); i++) {
                        Element el = (Element) nl.item(i);
                        int chan = Integer.parseInt(getTextValue(el, "chan")) - 1;
                        int bin = Integer.parseInt(getTextValue(el, "bin")) - 1;
                        int j = 0;
                        while (j < NumOfGaussians - 1 && gaussians[chan][bin][j].getSigma() == 0) {
                            j++;
                        }
                        try {
                            gaussians[chan][bin][j].setBin(Integer.parseInt(getTextValue(el, "bin")));
                            gaussians[chan][bin][j].setChan(Integer.parseInt(getTextValue(el, "chan")));
                            gaussians[chan][bin][j].setMu(Double.parseDouble(getTextValue(el, "mu")));
                            gaussians[chan][bin][j].setSigma(Double.parseDouble(getTextValue(el, "sigma")));
                        } catch (Exception e1) {
                            log.warning("while adding gauss caught: " + e1);
                            e1.printStackTrace();
                        }
                        //log.info("read gaussian from xml: gaussNr:" + j + " chan:" + chan + " bin:" + bin + " mu:" + gaussians[chan][bin][j].getMu() + " sigma:" + gaussians[chan][bin][j].getSigma());
                    }
                }
            } catch (Exception e1) {
                log.warning("while reading gaussians caught exception: " + e1);
                e1.printStackTrace();
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
        } catch (Exception e2) {
            log.warning("while loading xml calibration file, caught exception " + e2);
        }
    }

    private String getTextValue(Element ele, String tagName) {
        String textVal = null;
        NodeList nl = ele.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            Element el = (Element) nl.item(0);
            textVal = el.getFirstChild().getNodeValue();
        }

        return textVal;
    }

    public double[] convertITD(int channel, int ITD) {
        double[] addThis = new double[NumOfBins];
        double sum = 0;
        for(int k=0; k<NumOfBins;k++)
        {
            double Mu = gaussians[channel][k][0].getMu();
            double Sigma = gaussians[channel][k][0].getSigma();
            if(Sigma==0)
                addThis[k] = 0;
            else
                addThis[k] = java.lang.Math.exp(-(ITD - Mu) * (ITD - Mu) / (2 * Sigma * Sigma)) / (java.lang.Math.sqrt(2 * java.lang.Math.PI) * Sigma);
            sum += addThis[k];
        }
        if (sum != 0) {
            for (int k = 0; k < NumOfBins; k++) {
                addThis[k] /= sum;
            }
        }
        return addThis;
    }

    /**
     * @return the maxITD
     */
    public int getMaxITD() {
        return maxITD;
    }

    /**
     * @return the NumOfBins
     */
    public int getNumOfBins() {
        return NumOfBins;
    }

    /**
     * @return the NumOfChannels
     */
    public int getNumOfChannels() {
        return NumOfChannels;
    }

    /**
     * @return the NumOfGaussians
     */
    public int getNumOfGaussians() {
        return NumOfGaussians;
    }
}

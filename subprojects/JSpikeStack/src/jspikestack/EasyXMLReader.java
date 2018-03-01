/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Get and parse an xml file.  Provides a wrapper around the default xml parsing
 * utility which I think is easier to use.
 *
 * EasyXMLReader reader=new EasyXMLReader();
 *
 * @author oconnorp
 */
public class EasyXMLReader {

    Node doc;
    File file;
    //BASE64Decoder decoder = new BASE64Decoder();

    public static File grabFile(String startPath)
    {
        try {
            if (startPath==null) {
				return getfile(null);
			}
			else {
				return getfile(new File(startPath));
			}
        } catch (FileNotFoundException ex) {
            Logger.getLogger(EasyXMLReader.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public EasyXMLReader() throws Exception
    {   this(grabFile(null));
    }

    public EasyXMLReader(File infile){


        if ((infile!=null) && infile.isDirectory()) {
			file=grabFile(infile.toString());
		}
		else if ((infile!=null) && infile.isFile()) {
			//        else if (infile!=null && !infile.isFile())
            file=infile;
		}
		else{
            System.out.println("The file you gave is neither a file nor a directory");
            file=grabFile(null);
        }

        if (file==null)
        {   return;
        }


        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder;

            dBuilder = dbFactory.newDocumentBuilder();

            Document dc;
            dc = dBuilder.parse(file);

            dc.getDocumentElement().normalize();

            doc = dc;


        } catch (SAXException ex) {
            Logger.getLogger(EasyXMLReader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(EasyXMLReader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(EasyXMLReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Return whether the system has a file */
    public boolean hasFile()
    {   return file!=null;

    }

    public File getFile()
    {   return file;
    }

    public EasyXMLReader(Node db) {
        doc = db;
    }

    public EasyXMLReader getNode(String name) {
        return getNode(name, 0);
    }

    public EasyXMLReader getNode(String name, int index) {
        return new EasyXMLReader(get(name).item(index));
    }

    public int getNodeCount(String name)
    {   return get(name).getLength();
    }

    public NodeList get(String name) {
        if (doc instanceof Element) {
            return ((Element) doc).getElementsByTagName(name);
        } else if (doc instanceof Document) {
            return ((Document) doc).getElementsByTagName(name);
        } else {
            throw new UnsupportedOperationException("Thank poor implementation for this error");
        }

    }

    public String getRaw(String name) {
        return get(name).item(0).getTextContent();
    }

    public Integer getInt(String name) {

        try {
            return Integer.parseInt(getRaw(name));
        } catch (NumberFormatException N) {
            return null;
        }

    }

    public Float getFloat(String name) {
        try {
            return Float.parseFloat(getRaw(name));
        } catch (NumberFormatException N) {
            return null;
        }
    }

    public byte[] getB64(String name) {
        return DatatypeConverter.parseBase64Binary(getRaw(name));
    }

    public float[] getBase64FloatArr(String name) {   /*
         * Thank you Brian Smith, whoever you are
         */

        byte[] binArray = getB64(name);

        // Make little-endian (Doesn't work! Done manually below)
//        ByteBuffer bbuf = ByteBuffer.allocate(binArray.length);
//        bbuf.put(binArray);
//        binArray = bbuf.order(ByteOrder.LITTLE_ENDIAN).array();
////        FloatBuffer ff=bbuf.asFloatBuffer();

        // Cast to float array
        float[] floatValues = new float[binArray.length / 4];
        // Iterate until parse each float int
        int floatIndex = 0;
        for (int nextFloatPosition = 0; nextFloatPosition < binArray.length; nextFloatPosition += 4) { // Read in the bytes
            char c1 = (char) binArray[nextFloatPosition + 3];
            char c2 = (char) binArray[nextFloatPosition + 2];
            char c3 = (char) binArray[nextFloatPosition + 1];
            char c4 = (char) binArray[nextFloatPosition + 0];
            // Bitwise AND to make sure only first 2 bytes are included
            int b1 = c1 & 0xFF;
            int b2 = c2 & 0xFF;
            int b3 = c3 & 0xFF;
            int b4 = c4 & 0xFF;
            // Build the four-byte floating-point "single format" representation
            int intBits = (b4) | (b3 << 8) | (b2 << 16) | (b1 << 24);
            floatValues[floatIndex] = Float.intBitsToFloat(intBits);
            // Increment counter used to populate array
            floatIndex++;
        }

        return floatValues;
    }

    // ===== File IO Functions =====
    static class FileChoice implements Runnable {

        File file;
        File startDir;

        @Override
        public void run() {
            JFileChooser fc;
            fc = new JFileChooser(startDir);
            FileFilter filt=new FileNameExtensionFilter("XML File","xml");
            fc.addChoosableFileFilter(filt);
            //fc.setFileFilter(filt);
            //fc = new JFileChooser("dfsfds");
            fc.setDialogTitle("Choose network weight XML file");

            fc.showOpenDialog(null);
            file = fc.getSelectedFile();
        }
    }

    static public File getfile(File startDir) throws FileNotFoundException {

        FileChoice fc = new FileChoice();
        if ((startDir!=null) && startDir.isDirectory()) {
			fc.startDir=startDir;
		}

        if (SwingUtilities.isEventDispatchThread()) {
            fc.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(fc);
            } catch (Exception ex) {
                System.out.println(ex.toString());
                return null;
            }
        }
        return fc.file;
    }

}

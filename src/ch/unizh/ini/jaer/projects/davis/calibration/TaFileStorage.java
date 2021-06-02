package ch.unizh.ini.jaer.projects.davis.calibration;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.Locale;
import java.util.Scanner;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/** Reads and writes Mat files to XML
 * 
 * From http://stackoverflow.com/questions/26445747/is-there-a-way-of-storing-opencv-javacv-mat-objects-in-a-database
 * 
 * @author tobi
 */
public class TaFileStorage {
    // static
    public static final int READ = 0;
    public static final int WRITE = 1;

    // variables
    private File file;
    private boolean isWrite;
    private Document doc;
    private Element rootElement;

    /** Create a new instance, which can be used to write and/or read
     * 
     */
    public TaFileStorage() {
        file = null;
        isWrite = false;
        doc = null;
        rootElement = null;
    }


    /** read or write
     * 
     * @param filePath
     * @param flags 
     */
    public void open(String filePath, int flags ) {
        try {
            if( flags == READ ) {
                open(filePath);
            }
            else {
                create(filePath);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

    }


    /** read only
     * 
     * @param filePath 
     */
    public void open(String filePath) {
        try {
            file = new File(filePath);
            if( file == null || file.isFile() == false ) {
                System.err.println("Can not open file: " + filePath );
            }
            else {
                isWrite = false;
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
                doc.getDocumentElement().normalize();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    /** write only
     * 
     * @param filePath 
     */
    public void create(String filePath) {
        try {
            file = new File(filePath);
            if( file == null ) {
                System.err.println("Can not wrtie file: " + filePath );
            }
            else {
                isWrite = true;
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

                rootElement = doc.createElement("opencv_storage");
                doc.appendChild(rootElement);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public Mat readMat(String tag) {
        if( isWrite ) {
            System.err.println("Try read from file with write flags");
            return null;
        }

        NodeList nodelist = doc.getElementsByTagName(tag);
        Mat readMat = null;

        for( int i = 0 ; i<nodelist.getLength() ; i++ ) {
            Node node = nodelist.item(i);

            if( node.getNodeType() == Node.ELEMENT_NODE ) {
                Element element = (Element)node;

                String type_id = element.getAttribute("type_id");
                if( "opencv-matrix".equals(type_id) == false) {
                    System.out.println("Fault type_id ");
                }

                String rowsStr = element.getElementsByTagName("rows").item(0).getTextContent();
                String colsStr = element.getElementsByTagName("cols").item(0).getTextContent();
                String dtStr = element.getElementsByTagName("dt").item(0).getTextContent();
                String dataStr = element.getElementsByTagName("data").item(0).getTextContent();

                int rows = Integer.parseInt(rowsStr);
                int cols = Integer.parseInt(colsStr);
                int type = CvType.CV_8U;

                Scanner s = new Scanner(dataStr);
                s.useLocale(Locale.US);

                if( "f".equals(dtStr) ) {
                    type = CvType.CV_32F;
                    readMat = new Mat( rows, cols, type );
                    float fs[] = new float[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextFloat() ) {
                                fs[0] = s.nextFloat();
                            }
                            else {
                                fs[0] = 0;
                                System.err.println("Unmatched number of float value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, fs);
                        }
                    }
                }
                else if( "i".equals(dtStr) ) {
                    type = CvType.CV_32S;
                    readMat = new Mat( rows, cols, type );
                    int is[] = new int[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextInt() ) {
                                is[0] = s.nextInt();
                            }
                            else {
                                is[0] = 0;
                                System.err.println("Unmatched number of int value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, is);
                        }
                    }
                }
                else if( "s".equals(dtStr) ) {
                    type = CvType.CV_16S;
                    readMat = new Mat( rows, cols, type );
                    short ss[] = new short[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextShort() ) {
                                ss[0] = s.nextShort();
                            }
                            else {
                                ss[0] = 0;
                                System.err.println("Unmatched number of int value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, ss);
                        }
                    }
                }
                else if( "b".equals(dtStr) ) {
                    readMat = new Mat( rows, cols, type );
                    byte bs[] = new byte[1];
                    for( int r=0 ; r<rows ; r++ ) {
                        for( int c=0 ; c<cols ; c++ ) {
                            if( s.hasNextByte() ) {
                                bs[0] = s.nextByte();
                            }
                            else {
                                bs[0] = 0;
                                System.err.println("Unmatched number of byte value at rows="+r + " cols="+c);
                            }
                            readMat.put(r, c, bs);
                        }
                    }
                }
            }
        }
        return readMat;
    }


    public void writeMat(String tag, Mat mat) {
        try {
            if( isWrite == false) {
                System.err.println("Try write to file with no write flags");
                return;
            }

            Element matrix = doc.createElement(tag);
            matrix.setAttribute("type_id", "opencv-matrix");
            rootElement.appendChild(matrix);

            Element rows = doc.createElement("rows");
            rows.appendChild( doc.createTextNode( String.valueOf(mat.rows()) ));

            Element cols = doc.createElement("cols");
            cols.appendChild( doc.createTextNode( String.valueOf(mat.cols()) ));

            Element dt = doc.createElement("dt");
            String dtStr;
            int type = mat.type();
            if(type == CvType.CV_32F ) { // type == CvType.CV_32FC1
                dtStr = "f";
            }
            else if( type == CvType.CV_32S ) { // type == CvType.CV_32SC1
                dtStr = "i";
            }
            else if( type == CvType.CV_16S  ) { // type == CvType.CV_16SC1
                dtStr = "s";
            }
            else if( type == CvType.CV_8U ){ // type == CvType.CV_8UC1 
                dtStr = "b";
            }
            else {
                dtStr = "unknown";
            }
            dt.appendChild( doc.createTextNode( dtStr ));

            Element data = doc.createElement("data");
            String dataStr = dataStringBuilder( mat );
            data.appendChild( doc.createTextNode( dataStr ));

            // append all to matrix
            matrix.appendChild( rows );
            matrix.appendChild( cols );
            matrix.appendChild( dt );
            matrix.appendChild( data );

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private String dataStringBuilder(Mat mat) {
        StringBuilder sb = new StringBuilder();
        int rows = mat.rows();
        int cols = mat.cols();
        int type = mat.type();

        if( type == CvType.CV_32F ) {
            float fs[] = new float[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, fs);
                    sb.append( String.valueOf(fs[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }       
        }
        else if( type == CvType.CV_32S ) {
            int is[] = new int[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, is);
                    sb.append( String.valueOf(is[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }
        }
        else if( type == CvType.CV_16S ) {
            short ss[] = new short[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, ss);
                    sb.append( String.valueOf(ss[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }
        }
        else if( type == CvType.CV_8U ) {
            byte bs[] = new byte[1];
            for( int r=0 ; r<rows ; r++ ) {
                for( int c=0 ; c<cols ; c++ ) {
                    mat.get(r, c, bs);
                    sb.append( String.valueOf(bs[0]));
                    sb.append( ' ' );
                }
                sb.append( '\n' );
            }
        }       
        else {
            sb.append("unknown type\n");
        }

        return sb.toString();
    }


    public void release() {
        try {
            if( isWrite == false) {
                System.err.println("Try release of file with no write flags");
                return;
            }

            DOMSource source = new DOMSource(doc);

            StreamResult result = new StreamResult(file);

            // write to xml file
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // do it
            transformer.transform(source, result);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
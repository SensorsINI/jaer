package net.sf.jaer.util;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Adapted from
 * https://stackoverflow.com/questions/34297676/how-to-load-opencv-matrices-saved-with-filestorage-in-java
 * 
 * using
 * https://github.com/EsotericSoftware/yamlbeans
 *
 * @author https://stackoverflow.com/users/3303546/cecilia
 */
public class YamlMatFileStorage {

    private static final Logger log = Logger.getLogger("YamlMatLoader");

    // Mat cannot be used directly because it is a native object and yamlbeans only deals with Java objects
    protected static class MatStorage {

        public int rows;
        public int cols;
        public String dt;
        public List<String> data;

        // The empty constructor is required by YamlReader
        public MatStorage() {
        }

        public double[] getData() {
            double[] dataOut = new double[data.size()];
            for (int i = 0; i < dataOut.length; i++) {
                dataOut[i] = Double.parseDouble(data.get(i));
            }

            return dataOut;
        }

        public void putData(Mat m) {
            rows = m.rows();
            cols = m.cols();
            data = new ArrayList();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    data.add(Double.toString(m.get(r, c)[0]));
                }
            }

        }
    }

    /**
     * Reads a Mat from a YAML file
     *
     * @param path path to YAML file
     * @return the Mat
     */
    public Mat readMatYml(String path) throws FileNotFoundException, YamlException, IOException {
        Mat m=null;
        try (YamlReader reader = new YamlReader(new FileReader(path))) {
            MatStorage matStorage = (MatStorage) reader.read();
            // Create a new Mat to hold the extracted data
            m = new Mat(matStorage.rows, matStorage.cols, CvType.CV_32FC1);
            m.put(0, 0, matStorage.getData());
            log.info(String.format("From file %s loaded Mat %s", path, m));
        }
        return m;
    }

    // Loading function
    /**
     * Reads a Mat from a YAML file
     *
     * @param path path to YAML file
     * @param m the Mat to write
     * @throws java.io.IOException
     * @throws com.esotericsoftware.yamlbeans.YamlException
     */
    public void writeMatYml(String path, Mat m) throws IOException, YamlException {
        try (YamlWriter writer = new YamlWriter(new FileWriter(path))) {
            // write the Mat
            MatStorage ms = new MatStorage();
            ms.putData(m);
            writer.write(ms);
        }
        log.info(String.format("Wrote Mat %s to file %s", m, path));
    }

}

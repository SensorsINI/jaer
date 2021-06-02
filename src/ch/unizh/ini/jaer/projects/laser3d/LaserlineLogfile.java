/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Thomas
 */
public class LaserlineLogfile {

    private String pathname;
    private String filename;
    private boolean writing = false;
    private boolean opened = false;
    private FileWriter laserlineFileStream;
    private BufferedWriter laserlineOutBuffer;
    private Logger log;

    public LaserlineLogfile(Logger log) {
        this.log = log;
    }

    void write(ArrayList laserline, int timestamp) {
        writing = true;
        if (laserlineOutBuffer != null & laserline.size() > 0) {
            try {
                laserlineOutBuffer.write(Integer.toString(timestamp));
                for (Object o : laserline) {
                    float[] fpxl = (float[]) o;
                    laserlineOutBuffer.write(" " + Float.toString(fpxl[0]) + " " + Float.toString(fpxl[1]));
                }
                laserlineOutBuffer.newLine();
            } catch (Exception e) {
                log.log(Level.WARNING, "Error writing laserline to file:" + e.getMessage());
            }
        }
        writing = false;
    }

    void openFile(String pathname, String filename) {
        this.pathname = pathname;
        this.filename = filename;
        if (!opened) {
            try {
                laserlineFileStream = new FileWriter(pathname + filename);
                laserlineOutBuffer = new BufferedWriter(laserlineFileStream);
                laserlineOutBuffer.write("! LaserlineLogfile");
                laserlineOutBuffer.newLine();
                laserlineOutBuffer.write("! v1.0");
                laserlineOutBuffer.newLine();
                log.info("laserlineLogfile " + filename + " opened");
                opened = true;
            } catch (Exception e) {
                log.log(Level.WARNING, "Error opening file:" + e.getMessage());
            }
        } else {
            log.info("LaserlineLogfile already open!");
        }
    }

    private void closeFile() {
        if (laserlineOutBuffer != null) {
            try {
                laserlineOutBuffer.close();
                log.info("laserlineLogfile " + filename + " closed");
            } catch (Exception e) {
                log.warning("Error opening file:" + e.getMessage());
            }
            opened = false;
        }

    }

    void close() {
        if (opened) {
            opened = false;
            while (writing) {
                
            }
            closeFile();
        }
    }
}

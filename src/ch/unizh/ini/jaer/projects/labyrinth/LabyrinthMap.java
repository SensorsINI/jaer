/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import com.kitfox.svg.*;
import com.kitfox.svg.SVGUniverse;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *  Loads SVG file describing a board and displays it in the annotate method.
 * 
 * @author Tobi
 */
public class LabyrinthMap extends EventFilter2D implements FrameAnnotater {

    public static String getDescription() {
        return "Handles SVG maps of Labyrinth game";
    }
    SVGUniverse svg = new SVGUniverse();
    SVGDiagram svgDiagram = null;

    public LabyrinthMap(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadMap", "opens file dialog to select SVG file of map");
        File f = getLastFilePrefs();
        try {
            loadMapFromFile(f);
        } catch (Exception ex) {
            log.warning("couldn't load map information from file " + f + ", caught " + ex + "; you are missing the SVG file describing the Labyrinth maze");
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doLoadMap() {
        final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(getLastFilePrefs());  // defaults to startup runtime folder
        fc.setFileFilter(new FileFilter() {

            public boolean accept(File f) {
                return f.isDirectory()
                        || f.getName().toLowerCase().endsWith(".svg");
            }

            @Override
            public String getDescription() {
                return "SVG files (scalable vector graphics)";
            }
        });

        final int[] state = new int[1];
        state[0] = Integer.MIN_VALUE;

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fc.setSelectedFile(new File(getString("lastFile", System.getProperty("user.dir"))));
                state[0] = fc.showOpenDialog(chip.getAeViewer() != null && chip.getAeViewer().getFilterFrame() != null ? chip.getAeViewer().getFilterFrame() : null);
                if (state[0] == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    putLastFilePrefs(file);
                    try {
                        loadMapFromFile(file);
                    } catch (Exception e) {
                        log.warning(e.toString());
                        JOptionPane.showMessageDialog(fc, "Couldn't load map from file " + file + ", caught exception " + e, "Map file error", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    log.info("Cancelled saving!");
                }
            }
        });
    }

    private void loadMapFromFile(File file) throws MalformedURLException {
        log.info("loading map file "+file);
        URI svgURI = svg.loadSVG(file.toURI().toURL());
        svgDiagram = svg.getDiagram(svgURI);
    }

    private File getLastFilePrefs() {
        return new File(getString("lastFile", System.getProperty("user.dir")));
    }

    private void putLastFilePrefs(File file) {
        putString("lastFile", file.toString());
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }
}

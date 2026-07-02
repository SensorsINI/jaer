package net.sf.jaer.hardwareinterface.usb.nrv;

import java.io.File;

import javax.swing.filechooser.FileFilter;

/**
 * File filter for NRV SDK bias settings files (*.txt).
 */
public class NRVSettingsFileFilter extends FileFilter {

    public static final String EXTENSION = ".txt";

    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        final String name = f.getName().toLowerCase();
        return name.endsWith(EXTENSION);
    }

    @Override
    public String getDescription() {
        return "NRV bias settings (*.txt)";
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.Color;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import net.sf.jaer.graphics.AEViewerConsoleOutputFrame;

/**
 * Handles logging messages for AEViewer status line.
 *
 * @author tobi
 */
public class AEViewerLoggingHandler extends java.util.logging.Handler {

    AEViewer viewer;
    AEViewerConsoleOutputFrame consoleWindow;
    Formatter statusFormatter;
    Formatter consoleFormatter;

    public AEViewerLoggingHandler(AEViewer v){
        viewer=v;
        statusFormatter=new AEViewerStatusFormatter();
        consoleFormatter=new SimpleFormatter();
        setFormatter(new AEViewerStatusFormatter());
        consoleWindow=new AEViewerConsoleOutputFrame();
    }

    public AEViewerConsoleOutputFrame getConsoleWindow(){
        return consoleWindow;
    }

    @Override
    public void publish(LogRecord record) {
        String statusMessage = null, consoleMessage=null;
        if (isLoggable(record)) {
            try {
                statusMessage=getFormatter().format(record);
                consoleMessage=consoleFormatter.format(record);
//                message = record.getMessage(); // getFormatter().format(record);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }
            try {
                viewer.setStatusMessage(statusMessage);
                if(record.getLevel().intValue()>=Level.WARNING.intValue()){
                    viewer.setStatusColor(Color.red);
                    consoleWindow.setWarning();
                }else{
                    viewer.setStatusColor(Color.black);
                    consoleWindow.setInfo();
                }
                consoleWindow.append(consoleMessage);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.WRITE_FAILURE);
            }
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }
}


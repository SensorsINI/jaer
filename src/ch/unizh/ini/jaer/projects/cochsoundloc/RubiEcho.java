/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;

import edu.mplab.rubios.node.RUBIOSNode;

public class RubiEcho extends Thread {

    static Echo echo;
    String address;
    public static volatile int time = 0;

    RubiEcho(String address) {
        this.address = address;
    }

    @Override
    public void run() {
        double myDt = 0.01;
        String args[] = new String[1];
        args[0] = address;
        RubiEcho.echo = new Echo(args, myDt);
        echo.run();
    }

    public static void startLog() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogType(JFileChooser.SAVE_DIALOG);
        int state = fc.showSaveDialog(null);
        if (state == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            try {
                // Create file
                echo.fstreamBins = new FileWriter(path);
                echo.BinFile = new BufferedWriter(echo.fstreamBins);
                try {
                    echo.BinFile.write("time\tMsg\n");
                } catch (IOException ex) {
                    Logger.getLogger(RubiEcho.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(RubiEcho.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void stopLog() {
        try {
            echo.BinFile.close();
        } catch (IOException ex) {
            Logger.getLogger(RubiEcho.class.getName()).log(Level.SEVERE, null, ex);
        }
        echo.BinFile = null;
        echo.fstreamBins = null;
    }

    private static class Echo extends RUBIOSNode {

        public Logger log = Logger.getLogger("RUBIOS:");
        private FileWriter fstreamBins;
        private BufferedWriter BinFile;

        public Echo(String[] args, double mydt) {
            super(args, mydt);

        }

        /** parseMessage ignores all incoming messages
         */
        @Override
        public boolean parseMessage(String msg) {

            return false;
        }

        /**
         */
        @Override
        public void nodeDynamics() {
            for (int i = 0; i < nMessagesReceived; i++) {
                log.info("Echo: " + messagesReceived[i]);
                if (BinFile != null) {
                    try {
                        BinFile.write(time + "\t" + messagesReceived[i] + "\n");
                    } catch (IOException ex) {
                        Logger.getLogger(RubiEcho.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }
}

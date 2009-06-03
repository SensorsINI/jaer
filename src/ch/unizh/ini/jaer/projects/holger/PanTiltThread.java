/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.holger;

import java.util.logging.Logger;

/**
 *
 * @author Holger
 */
public class PanTiltThread extends Thread {

    public PanTiltFrame panTiltFrame = new PanTiltFrame();
    private Logger log = Logger.getLogger("PanTiltThread");
    boolean exitThread = false;

    public PanTiltThread() {
        
        panTiltFrame.pack();
        //panTiltFrame.setLocationRelativeTo(null);
        panTiltFrame.setSize(40, 100);
        panTiltFrame.setResizable(true);
//        panTiltFrame.addWindowListener(new java.awt.event.WindowAdapter() {
//            @Override
//            public void windowClosing(WindowEvent winEvt) {
//                windowClosed = true;
//            }
//        });
        
    }

    @Override
    public void run() {
        panTiltFrame.setVisible(true);
        while (exitThread == false) {
            try {
                FilterOutputObject filterOutput;

                filterOutput = PanTilt.takeBlockingQ();

//                if (panTiltFrame.panTiltControl.isConnected()) {
//                    String pantiltResponse = panTiltFrame.panTiltControl.getResponse();
//                    if (panTiltFrame.isLogResponse() && !pantiltResponse.isEmpty()) {
//                        log.info(pantiltResponse);
//                    }
//                }
                if (panTiltFrame.panTiltControl.isWasMoving() == false) {
                    //log.info("filterOutput read in PanTiltThread: (fromCochlea=" + filterOutput.isFromCochlea() + ")" + filterOutput.getPanOffset());
                    if (filterOutput.isFromCochlea()) {
                        panTiltFrame.setCochleaPanOffset(filterOutput.getPanOffset());
                        panTiltFrame.setCochleaTiltOffset(filterOutput.getTiltOffset());
                        panTiltFrame.setCochleaConfidence(filterOutput.getConfidence());
                        if (panTiltFrame.panTiltControl.isConnected() && panTiltFrame.isUseCochlea() && filterOutput.getConfidence() > panTiltFrame.getCochleaThreshold()) {
                            if (java.lang.Math.abs(filterOutput.getPanOffset()) > 150 ) {
                                panTiltFrame.setPanPos(panTiltFrame.panTiltControl.getPanPos()+(int)filterOutput.getPanOffset()*2);
                            }
                        }
                    }
                    if (filterOutput.isFromRetina()) {
                        panTiltFrame.setRetinaPanOffset(filterOutput.getPanOffset());
                        panTiltFrame.setRetinaTiltOffset(filterOutput.getTiltOffset());
                        panTiltFrame.setRetinaConfidence(filterOutput.getConfidence());
                        if (panTiltFrame.panTiltControl.isConnected() && panTiltFrame.isUseRetina() && filterOutput.getConfidence() > panTiltFrame.getRetinaThreshold()) {
                            if (java.lang.Math.abs(filterOutput.getPanOffset()) > 25 ) {
                                panTiltFrame.setPanPos(panTiltFrame.panTiltControl.getPanPos()-(int)filterOutput.getPanOffset()*5);
                            }
                            if (java.lang.Math.abs(filterOutput.getTiltOffset()) > 25 ) {
                                panTiltFrame.setTiltPos(panTiltFrame.panTiltControl.getTiltPos()+(int)filterOutput.getTiltOffset()*5);
                            }
                        }
                    }
                }
            } catch (InterruptedException ex) {
                log.warning("exception in PanTiltThread: " + ex);
            }
        }
        //panTiltFrame.setVisible(false);
    }

}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.util.logging.Logger;

/**
 * This thread combines the auditory and visual filter outputs and controls the pan tilt unit.
 * 
 * @author Holger
 */
public class PanTiltThread extends Thread {

    public PanTiltFrame panTiltFrame = null;
    private Logger log = Logger.getLogger("PanTiltThread");
    boolean exitThread = false;
    long nextFrameRetinaUpdate=0;
    long nextFrameCochleaUpdate=0;
    PanTilt panTilt = null;
    boolean learnCochleaMoved = false;
    double learnCochleaLastPos;
    double learnCochleaLastOffset;
    boolean learnRetinaMoved = false;
    double learnRetinaLastPos;
    double learnRetinaLastOffset;
    long learnWaitStartTime;

    public PanTiltThread(PanTilt panTilt) {
        this.panTilt = panTilt;
        this.panTiltFrame = new PanTiltFrame(this.panTilt);
//        panTiltFrame.pack();
        //panTiltFrame.setLocationRelativeTo(null);
//        panTiltFrame.setSize(40, 100);
//        panTiltFrame.setResizable(true);
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
                CommObjForPanTilt filterOutput;

                filterOutput = panTilt.takeBlockingQ();

//                if (panTiltFrame.panTiltControl.isConnected()) {
//                    String pantiltResponse = panTiltFrame.panTiltControl.getResponse();
//                    if (panTiltFrame.isLogResponse() && !pantiltResponse.isEmpty()) {
//                        log.info(pantiltResponse);
//                    }
//                }

                //log.info("filterOutput read in PanTiltThread: (fromCochlea=" + filterOutput.isFromCochlea() + ")" + filterOutput.getPanOffset());
                long currentTime = (new java.util.Date()).getTime();
                if (learnWaitStartTime + 3000 < currentTime) {
                    learnRetinaMoved = false;
                    learnCochleaMoved = false;
                }

                if (filterOutput.isFromCochlea()) {
                    
                    if (nextFrameCochleaUpdate < currentTime) {
                        panTiltFrame.setCochleaPanOffset(filterOutput.getPanOffset());
                        panTiltFrame.setCochleaTiltOffset(filterOutput.getTiltOffset());
                        panTiltFrame.setCochleaConfidence(filterOutput.getConfidence());
                        nextFrameCochleaUpdate = currentTime + 400;
                    }
                    if (panTiltFrame.panTiltControl != null) {
                        if (panTiltFrame.panTiltControl.isWasMoving() == false) {
                            if (panTiltFrame.panTiltControl.isConnected() && panTiltFrame.isUseCochlea() && filterOutput.getConfidence() > panTiltFrame.getCochleaThreshold()) {
                                if(panTiltFrame.isLearnRetina() && learnRetinaMoved)
                                {
                                    if (!panTiltFrame.isServoLimitTouched())
                                    {
                                        //moved to: panTiltFrame.getPanPos() = learnRetinaLastPos + learnRetinaLastOffset * panTiltFrame.getRetinaPanScaling()
                                        //better would have been to move to: panTiltFrame.getPanPos() + filterOutput.getPanOffset() * panTiltFrame.getRetinaPanScaling() = learnRetinaLastPos + learnRetinaLastOffset * panTiltFrame.getRetinaPanScaling()
                                        double betterScaling = (panTiltFrame.getPanPos() + filterOutput.getPanOffset() * panTiltFrame.getRetinaPanScaling() - learnRetinaLastPos) / learnRetinaLastOffset;
                                        double newScaling = (0.05*betterScaling + 0.95*panTiltFrame.getRetinaPanScaling());
                                        log.info("Learning: scaling should be "+betterScaling+". set RetinaPanScaling from "+panTiltFrame.getRetinaPanScaling()+" to "+newScaling);
                                        panTiltFrame.setRetinaPanScaling(newScaling);
                                    }
                                    learnRetinaMoved = false;
                                }
                                if (panTiltFrame.isLearnCochlea() || !panTiltFrame.isLearnRetina())
                                {
                                    if (!learnCochleaMoved) {
                                        learnCochleaLastOffset = filterOutput.getPanOffset();
                                        learnCochleaLastPos = panTiltFrame.getPanPos();
                                        panTiltFrame.setPanPos(panTiltFrame.getPanPos() + filterOutput.getPanOffset() * panTiltFrame.getCochleaPanScaling());
                                        if(panTiltFrame.isLearnCochlea())
                                        {
                                            learnCochleaMoved = true;
                                            learnWaitStartTime = currentTime;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (filterOutput.isFromRetina()) {
                    if (nextFrameRetinaUpdate < currentTime) {
                        panTiltFrame.setRetinaPanOffset(filterOutput.getPanOffset());
                        panTiltFrame.setRetinaTiltOffset(filterOutput.getTiltOffset());
                        panTiltFrame.setRetinaConfidence(filterOutput.getConfidence());
                        nextFrameRetinaUpdate = currentTime + 400;
                    }
                    if (panTiltFrame.panTiltControl != null) {
                        if (panTiltFrame.panTiltControl.isWasMoving() == false) {
                            if (panTiltFrame.panTiltControl.isConnected() && panTiltFrame.isUseRetina() && filterOutput.getConfidence() > panTiltFrame.getRetinaThreshold()) {
                                if(panTiltFrame.isLearnCochlea() && learnCochleaMoved)
                                {
                                    if (!panTiltFrame.isServoLimitTouched())
                                    {
                                        //moved to: panTiltFrame.getPanPos() = learnCochleaLastPos + learnCochleaLastOffset * panTiltFrame.getCochleaPanScaling()
                                        //better would have been to move to: panTiltFrame.getPanPos() + filterOutput.getPanOffset() * panTiltFrame.getRetinaPanScaling() = learnCochleaLastPos + learnCochleaLastOffset * panTiltFrame.getCochleaPanScaling()
                                        double betterScaling = (panTiltFrame.getPanPos() + filterOutput.getPanOffset() * panTiltFrame.getRetinaPanScaling() - learnCochleaLastPos) / learnCochleaLastOffset;
                                        double newScaling = (0.05*betterScaling + 0.95*panTiltFrame.getCochleaPanScaling());
                                        log.info("Learning: scaling should be "+betterScaling+". set CochleaPanScaling from "+panTiltFrame.getCochleaPanScaling()+" to "+newScaling);
                                        panTiltFrame.setCochleaPanScaling(newScaling);
                                    }
                                    learnCochleaMoved = false;
                                }
                                if (panTiltFrame.isLearnRetina() || !panTiltFrame.isLearnCochlea())
                                {
                                    if (!learnRetinaMoved) {
                                        if (java.lang.Math.abs(filterOutput.getPanOffset()) > 0.1) {
                                            learnRetinaLastOffset = filterOutput.getPanOffset();
                                            learnRetinaLastPos = panTiltFrame.getPanPos();
                                            panTiltFrame.setPanPos(panTiltFrame.getPanPos() + filterOutput.getPanOffset() * panTiltFrame.getRetinaPanScaling() );
                                            if(panTiltFrame.isLearnRetina())
                                            {
                                                learnRetinaMoved = true;
                                                learnWaitStartTime = currentTime;
                                            }
                                        }
                                        if (java.lang.Math.abs(filterOutput.getTiltOffset()) > 0.1) {
                                            panTiltFrame.setTiltPos(panTiltFrame.getTiltPos() + filterOutput.getTiltOffset() * panTiltFrame.getRetinaTiltScaling());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (panTiltFrame.datagramSocket != null) {

                }
            } catch (InterruptedException ex) {
                log.warning("exception in PanTiltThread: " + ex);
            }
        }
        //panTiltFrame.setVisible(false);
    }

}

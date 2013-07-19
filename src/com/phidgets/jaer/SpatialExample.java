package com.phidgets.jaer;


import com.phidgets.Phidget;
import com.phidgets.PhidgetException;
import com.phidgets.SpatialPhidget;
import com.phidgets.event.AttachEvent;
import com.phidgets.event.AttachListener;
import com.phidgets.event.DetachEvent;
import com.phidgets.event.DetachListener;
import com.phidgets.event.ErrorEvent;
import com.phidgets.event.ErrorListener;
import com.phidgets.event.SpatialDataEvent;
import com.phidgets.event.SpatialDataListener;

/*
 * Copyright 2010 Phidgets Inc.  All rights reserved.
 */


public class SpatialExample {

    public static final void main(String args[]) throws Exception {

        SpatialPhidget spatial;

        System.out.println(Phidget.getLibraryVersion());

        spatial = new SpatialPhidget();

        spatial.addAttachListener(new AttachListener() {

            public void attached(AttachEvent ae) {
                System.out.println("attachment of " + ae);
                try {
                    ((SpatialPhidget) ae.getSource()).setDataRate(496); //set data rate to 496ms
                } catch (PhidgetException pe) {
                    System.out.println("Problem setting data rate!");
                }
            }
        });
        spatial.addDetachListener(new DetachListener() {

            public void detached(DetachEvent ae) {
                System.out.println("detachment of " + ae);
            }
        });
        spatial.addErrorListener(new ErrorListener() {

            public void error(ErrorEvent ee) {
                System.out.println("error event for " + ee);
            }
        });
        spatial.addSpatialDataListener(new SpatialDataListener() {

            public void data(SpatialDataEvent sde) {
                System.out.println(sde);

                int i, j;
                for (j = 0; j < sde.getData().length; j++) {
                    String out = "Data packet (" + j + ") Timestamp: " + sde.getData()[j].getTime();
                    if (sde.getData()[j].getAcceleration().length > 0) {
                        out = out + "\n Acceleration: ";
                        for (i = 0; i < sde.getData()[j].getAcceleration().length; i++) {
                            out = out + sde.getData()[j].getAcceleration()[i] + ((i == sde.getData()[j].getAcceleration().length - 1) ? "" : ",");
                        }
                    }
                    if (sde.getData()[j].getAngularRate().length > 0) {
                        out = out + "\n Angular Rate: ";
                        for (i = 0; i < sde.getData()[j].getAngularRate().length; i++) {
                            out = out + sde.getData()[j].getAngularRate()[i] + ((i == sde.getData()[j].getAngularRate().length - 1) ? "" : ",");
                        }
                    }
                    if (sde.getData()[j].getMagneticField().length > 0) {
                        out = out + "\n Magnetic Field: ";
                        for (i = 0; i < sde.getData()[j].getMagneticField().length; i++) {
                            out = out + sde.getData()[j].getMagneticField()[i] + ((i == sde.getData()[j].getMagneticField().length - 1) ? "" : ",");
                        }
                    }
                    System.out.println(out);
                }
            }
        });
        
 
        spatial.openAny();
       while(true){
            Thread.sleep(1000);
        }
//        System.out.println("waiting for Spatial attachment...");
//        spatial.waitForAttachment();
//
//        System.out.println("Serial: " + spatial.getSerialNumber());
//        System.out.println("Accel Axes: " + spatial.getAccelerationAxisCount());
//        System.out.println("Gyro Axes: " + spatial.getGyroAxisCount());
//        System.out.println("Compass Axes: " + spatial.getCompassAxisCount());
//
//        System.out.println("Outputting events.  Input to stop.");
//        System.in.read();
//        System.out.print("closing...");
//        spatial.close();
//        spatial = null;
//        System.out.println(" ok");
//        if (false) {
//            System.out.println("wait for finalization...");
//            System.gc();
//        }
    }
}

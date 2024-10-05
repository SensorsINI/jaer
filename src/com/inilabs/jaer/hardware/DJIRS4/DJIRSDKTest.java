package com.inilabs.jaer.hardware.DJIRS4;

import java.util.concurrent.TimeUnit;

public class DJIRSDKTest {
    public static boolean printUntilArrives(DJIRonin gimbal, short yaw, short roll, short pitch) {
        int[] currentPosition = new (int int int);
        gimbal.getCurrentPosition(currentPosition);
        System.out.println("yaw = " + currentPosition[0] + " roll = " + currentPosition[1] + " pitch = " + currentPosition[2]);

        short delta = 20;
        return !(currentPosition[0] >= yaw - delta && currentPosition[0] <= yaw + delta &&
                 currentPosition[1] >= roll - delta && currentPosition[1] <= roll + delta &&
                 currentPosition[2] >= pitch - delta && currentPosition[2] <= pitch + delta);
    }

    public static void main(String[] args) {
        System.out.println("###########################################");
        System.out.println("#                                         #");
        System.out.println("#          DJIR-SDK Test v1.0.0           #");
        System.out.println("#                                         #");
        System.out.println("###########################################");

        DJIRonin gimbal = new DJIRonin();

        // Connect to DJI Ronin Gimbal
        gimbal.connect();

        // Select ABSOLUTE_CONTROL mode
        gimbal.setMoveMode(DJIR_SDK.MoveMode.ABSOLUTE_CONTROL);

        // Move to center position (yaw = 0, roll = 0, pitch = 0) for 2000ms
        gimbal.moveTo(0, 0, 0, 2000);
        while (printUntilArrives(gimbal, (short) 0, (short) 0, (short) 0));

        // Move to first test point (yaw = -1200, roll = 0, pitch = 300) for 2000ms
        gimbal.moveTo((short) -1200, (short) 0, (short) 300, 2000);
        while (printUntilArrives(gimbal, (short) -1200, (short) 0, (short) 300));

        // Move to second test point (yaw = 1200, roll = 0, pitch = -300) for 2000ms
        gimbal.moveTo((short) 1200, (short) 0, (short) -300, 2000);
        while (printUntilArrives(gimbal, (short) 1200, (short) 0, (short) -300));

        // Move to center position (yaw = 0, roll = 0, pitch = 0) for 2000ms
        gimbal.moveTo((short) 0, (short) 0, (short) 0, 2000);
        while (printUntilArrives(gimbal, (short) 0, (short) 0, (short) 0));

        System.out.println("Press any key to continue...");
        try {
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

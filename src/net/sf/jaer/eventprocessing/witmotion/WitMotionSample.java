package net.sf.jaer.eventprocessing.witmotion;

import net.sf.jaer.hardwareinterface.serial.witmotion.WitMotionIMU;

/**
 * One HWT906 output cycle (acceleration, gyro, angle, magnetic, quaternion,
 * and device time, whichever frames arrived). Missing numbers are
 * {@link Double#NaN}.
 * <p>
 * {@link #cameraUs} and {@link #aedat4UnixUs} are the camera time base used by
 * {@link net.sf.jaer.eventprocessing.gnss.NmeaGnssFilter}: host receive time is
 * recorded, and playback seeks on {@code aedat4_unix_us}.
 */
public final class WitMotionSample {

    public long receivedUnixMs;
    /** Last camera packet timestamp (microseconds, wrapping int) when this cycle was committed. */
    public int cameraUs;
    /** {@code Aedat4FileOutputStream.cameraTimestampToUnixUs(cameraUs)}; 0 if not recording AEDAT-4. */
    public long aedat4UnixUs;
    /** Device clock from a {@code 0x50} frame, Unix seconds. Not the sidecar sync key. */
    public double deviceUnixS = Double.NaN;
    public double tempC = Double.NaN;
    public double ax = Double.NaN;
    public double ay = Double.NaN;
    public double az = Double.NaN;
    public double wx = Double.NaN;
    public double wy = Double.NaN;
    public double wz = Double.NaN;
    public double roll = Double.NaN;
    public double pitch = Double.NaN;
    public double yaw = Double.NaN;
    public double hx = Double.NaN;
    public double hy = Double.NaN;
    public double hz = Double.NaN;
    public double q0 = Double.NaN;
    public double q1 = Double.NaN;
    public double q2 = Double.NaN;
    public double q3 = Double.NaN;

    public void apply(WitMotionIMU.ReceiveMessage msg) {
        if (msg instanceof WitMotionIMU.TimeMessage time) {
            deviceUnixS = time.timestampSeconds;
        } else if (msg instanceof WitMotionIMU.AccelerationMessage acc) {
            ax = acc.metersPerSecondSquared[0];
            ay = acc.metersPerSecondSquared[1];
            az = acc.metersPerSecondSquared[2];
            tempC = acc.tempCelsius;
        } else if (msg instanceof WitMotionIMU.AngularVelocityMessage gyro) {
            wx = gyro.degreesPerSecond[0];
            wy = gyro.degreesPerSecond[1];
            wz = gyro.degreesPerSecond[2];
        } else if (msg instanceof WitMotionIMU.AngleMessage angle) {
            roll = angle.roll;
            pitch = angle.pitch;
            yaw = angle.yaw;
        } else if (msg instanceof WitMotionIMU.MagneticMessage mag) {
            hx = mag.field[0];
            hy = mag.field[1];
            hz = mag.field[2];
            // This HWT906 sends 0 in the magnetic frame's temperature slot.
            // Keep the acceleration-frame temperature when it is already set.
            if (Double.isNaN(tempC)) {
                tempC = mag.tempCelsius;
            }
        } else if (msg instanceof WitMotionIMU.QuaternionMessage q) {
            q0 = q.q[0];
            q1 = q.q[1];
            q2 = q.q[2];
            q3 = q.q[3];
        }
    }

    public boolean hasMeasurement() {
        return !Double.isNaN(ax) || !Double.isNaN(wx) || !Double.isNaN(roll)
                || !Double.isNaN(hx) || !Double.isNaN(q0) || !Double.isNaN(deviceUnixS);
    }

    public WitMotionSample copy() {
        WitMotionSample c = new WitMotionSample();
        c.receivedUnixMs = receivedUnixMs;
        c.cameraUs = cameraUs;
        c.aedat4UnixUs = aedat4UnixUs;
        c.deviceUnixS = deviceUnixS;
        c.tempC = tempC;
        c.ax = ax;
        c.ay = ay;
        c.az = az;
        c.wx = wx;
        c.wy = wy;
        c.wz = wz;
        c.roll = roll;
        c.pitch = pitch;
        c.yaw = yaw;
        c.hx = hx;
        c.hy = hy;
        c.hz = hz;
        c.q0 = q0;
        c.q1 = q1;
        c.q2 = q2;
        c.q3 = q3;
        return c;
    }

    public String overlayText() {
        if (!hasMeasurement()) {
            return "WitMotion: no sample";
        }
        StringBuilder sb = new StringBuilder(160);
        if (!Double.isNaN(roll)) {
            sb.append(String.format("RPY %7.2f %7.2f %7.2f deg", roll, pitch, yaw));
        }
        if (!Double.isNaN(ax)) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(String.format("Acc %7.2f %7.2f %7.2f m/s2", ax, ay, az));
        }
        if (!Double.isNaN(wx)) {
            sb.append(String.format("\nGyro %7.2f %7.2f %7.2f deg/s", wx, wy, wz));
        }
        if (!Double.isNaN(tempC)) {
            sb.append(String.format("\nT %.1f C", tempC));
        }
        return sb.toString();
    }

    static int wireCode(WitMotionIMU.ReceiveMessage msg) {
        if (msg instanceof WitMotionIMU.TimeMessage) {
            return WitMotionIMU.TimeMessage.CODE;
        }
        if (msg instanceof WitMotionIMU.AccelerationMessage) {
            return WitMotionIMU.AccelerationMessage.CODE;
        }
        if (msg instanceof WitMotionIMU.AngularVelocityMessage) {
            return WitMotionIMU.AngularVelocityMessage.CODE;
        }
        if (msg instanceof WitMotionIMU.AngleMessage) {
            return WitMotionIMU.AngleMessage.CODE;
        }
        if (msg instanceof WitMotionIMU.MagneticMessage) {
            return WitMotionIMU.MagneticMessage.CODE;
        }
        if (msg instanceof WitMotionIMU.QuaternionMessage) {
            return WitMotionIMU.QuaternionMessage.CODE;
        }
        return Integer.MAX_VALUE;
    }
}

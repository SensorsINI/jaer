package es.us.atc.jaer.chips.FpgaConfig;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

import org.usb4java.BufferUtils;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;

public class ATCFpgaConfig extends EventFilter2D {

    private int trackerId = getInt("trackerId", 1);
    private int cmCellInitX = getInt("cmCellInitX", 64);
    private int cmCellInitY = getInt("cmCellInitY", 64);
    private int cmCellRadixStep = getInt("cmCellRadixStep", 7);
    private int cmCellRadixTH = getInt("cmCellRadixTH", 7);
    private int cmCellInitRadix = getInt("cmCellInitRadix", 63);
    private int cmCellRadixMax = getInt("cmCellRadixMax", 127);
    private int cmCellRadixMin = getInt("cmCellRadixMin", 1);
    private int cmCellMaxTime = getInt("cmCellMaxTime", 200000);
    private int cmCellNevTh = getInt("cmCellNevTh", 10);
    private int cmCellAVG = getInt("cmCellAVG", 1);
    private boolean trackerEnable = getBoolean("trackerEnable", true);
    private boolean Reset = getBoolean("Reset", false);
    private int bgaFilterDeltaT = getInt("bgaFilterDeltaT", 100);
    private int bgaNeighbors = getInt("bgaNeighbors", 8);
    // OMC parameters
    private int IFthreshold = getInt("IFthreshold", 5);
    private int DecayTimeMs = getInt("DecayTimeMs", 2);
    private int TauNDecay = getInt("TauNDecay_S", 20);
    private int ExcitationStrength = getInt("ExcitationStrength", 1);
    private int Saturation = getInt("Saturation", 1);
    private int InhibitionStrength = getInt("InhibitionStrength", 1);
    // FPGA clock speed in MegaHertz (MHz) for time conversion.
    private final int CLOCK_SPEED = 50;

    public ATCFpgaConfig(final AEChip chip) {
        super(chip);

        initFilter();
        final String omc = "1) OMC", obt = "2) OBT", bga = "3) BGA";
        setPropertyTooltip(obt, "trackerId", "ID of the tracker to configure.");
        setPropertyTooltip(obt, "cmCellInitX", "Initial focus point (X axis).");
        setPropertyTooltip(obt, "cmCellInitY", "Initial focus point (Y axis).");
        setPropertyTooltip(obt, "cmCellRadixStep", "Step lenght for increasing cluster area dynamically.");
        setPropertyTooltip(obt, "cmCellRadixTH", "Threshold for increasing cluster area dynamically.");
        setPropertyTooltip(obt, "cmCellInitRadix", "Initial cluster radix.");
        setPropertyTooltip(obt, "cmCellRadixMax", "Maximmum cluster radix.");
        setPropertyTooltip(obt, "cmCellRadixMin", "Minimmum cluster radix.");
        setPropertyTooltip(obt, "cmCellMaxTime", "Maximum allowed delay without detecting events for current tracking (in �s). Once elapsed cell will reset itself.");
        setPropertyTooltip(obt, "cmCellNevTh", "Number of events to receive within a cluster before calculating center of mass.");
        setPropertyTooltip(obt, "cmCellAVG", "Amount of CM history involved in calculating the average for the new CM point (2^cmCellAVG).");
        setPropertyTooltip(obt, "trackerEnable", "Enable this tracker.");
        setPropertyTooltip(obt, "Reset", "Global Reset.");
        setPropertyTooltip(bga, "bgaFilterDeltaT", "Delta time for BackgroundActivity filter (in �s).");
        setPropertyTooltip(bga, "bgaNeighbors", "Number of neighbors to correlate the BackgroundActivity filter (top-down and left-right).");
        // OMC parameters
        setPropertyTooltip(omc, "IFthreshold", "Integrate and Fire threshold of OMCs");
        setPropertyTooltip(omc, "DecayTimeMs", "Time intervals at which the subunits of the OMC are decayed");
        setPropertyTooltip(omc, "ExcitationStrength", "Factor that multiplies normalised excitation");
        setPropertyTooltip(omc, "Saturation", "Saturation of subunits");
        setPropertyTooltip(omc, "InhibitionStrength", "Factor that multiplies normalised inhibition");
        setPropertyTooltip(omc, "TauNDecay", "Neuron time constant of decay");
    }

    // OMC parameters
    public void setIFthreshold(final int IFthreshold) {
        this.IFthreshold = IFthreshold;
        putInt("IFthreshold", IFthreshold);
    }

    public int getIFthreshold() {
        return IFthreshold;
    }
    
    public void setTauNDecay(final int TauNDecay) {
        this.TauNDecay = TauNDecay;
        putInt("TauNDecay", TauNDecay);
    }

    public int getTauNDecay() {
        return TauNDecay;
    }
    
    public static int getMinIFthreshold() {
        return 0;
    }

    public static int getMaxIFthreshold() {
        return 33554431;
    }

    public void setDecayTimeMs(final int DecayTimeMs) {
        this.DecayTimeMs = DecayTimeMs;
        putInt("DecayTimeMs", DecayTimeMs);
    }

    public int getDecayTimeMs() {
        return DecayTimeMs;
    }

    public static int getMinDecayTimeMs() {
        return 0;
    }

    public static int getMaxDecayTimeMs() {
        return (2147483647 * 50 * 10 ^ (-6));
    }

    public void setExcitationStrength(final int ExcitationStrength) {
        this.ExcitationStrength = ExcitationStrength;
        putInt("ExcitationStrength", ExcitationStrength);
    }

    public int getExcitationStrength() {
        return ExcitationStrength;
    }

    public static int getMinExcitationStrength() {
        return 0;
    }

    public static int getMaxExcitationStrength() {
        return 128;
    }

    public void setSaturation(final int Saturation) {
        this.Saturation = Saturation;
        putInt("Saturation", Saturation);
    }

    public int getSaturation() {
        return Saturation;
    }

    public static int getMinSaturation() {
        return 1;
    }

    public static int getMaxSaturation() {
        return 128;
    }

    public void setInhibitionStrength(final int InhibitionStrength) {
        this.InhibitionStrength = InhibitionStrength;
        putInt("InhibitionStrength", InhibitionStrength);
    }

    public int getInhibitionStrength() {
        return InhibitionStrength;
    }

    public static int getMinInhibitionStrength() {
        return 0;
    }

    public static int getMaxInhibitionStrength() {
        return 128;
    }

    // End of OMC parameters
    // End of Configuration of OMC
    synchronized public void doConfigureOMC() {
        // Verify that we have a USB device to send to.
        if (devHandle == null) {
            return;
        }
        // Convert ms time into clock cycles.
        final int sendDecayTimeMs = getInt("DecayTimeMs", 0) / (CLOCK_SPEED * 10 ^ (-6));
        final int sendTauNDecay = getInt("TauNDecay", 0) / (CLOCK_SPEED * 10 ^ (-6));

        int sendExcitationStrength = 1;
        if (getInt("ExcitationStrength", 0) == 0) {
            sendExcitationStrength = 0;
        } else if (getInt("ExcitationStrength", 0) == 1) {
            sendExcitationStrength = 1;
        } else if (getInt("ExcitationStrength", 0) >= 2 && getInt("ExcitationStrength", 0) < 4) {
            sendExcitationStrength = 2;
        } else if (getInt("ExcitationStrength", 0) >= 4 && getInt("ExcitationStrength", 0) < 8) {
            sendExcitationStrength = 4;
        } else if (getInt("ExcitationStrength", 0) >= 8 && getInt("ExcitationStrength", 0) < 16) {
            sendExcitationStrength = 8;
        } else if (getInt("ExcitationStrength", 0) >= 16 && getInt("ExcitationStrength", 0) < 32) {
            sendExcitationStrength = 16;
        } else if (getInt("ExcitationStrength", 0) >= 32 && getInt("ExcitationStrength", 0) < 64) {
            sendExcitationStrength = 32;
        } else if (getInt("ExcitationStrength", 0) >= 64 && getInt("ExcitationStrength", 0) < 128) {
            sendExcitationStrength = 64;
        } else if (getInt("ExcitationStrength", 0) == 128) {
            sendExcitationStrength = 128;
        }

        int sendSaturation = 1;
        if (getInt("Saturation", 0) == 1) {
            sendSaturation = 1;
        } else if (getInt("Saturation", 0) >= 2 && getInt("Saturation", 0) < 4) {
            sendSaturation = 2;
        } else if (getInt("Saturation", 0) >= 4 && getInt("Saturation", 0) < 8) {
            sendSaturation = 4;
        } else if (getInt("Saturation", 0) >= 8 && getInt("Saturation", 0) < 16) {
            sendSaturation = 8;
        } else if (getInt("Saturation", 0) >= 16 && getInt("Saturation", 0) < 32) {
            sendSaturation = 16;
        } else if (getInt("Saturation", 0) >= 32 && getInt("Saturation", 0) < 64) {
            sendSaturation = 32;
        } else if (getInt("Saturation", 0) >= 64 && getInt("Saturation", 0) < 128) {
            sendSaturation = 64;
        } else if (getInt("Saturation", 0) == 128) {
            sendSaturation = 128;
        }

        int sendInhibitionStrength = 1;
        if (getInt("InhibitionStrength", 0) == 0) {
            sendInhibitionStrength = 0;
        } else if (getInt("InhibitionStrength", 0) == 1) {
            sendInhibitionStrength = 1;
        } else if (getInt("InhibitionStrength", 0) >= 2 && getInt("InhibitionStrength", 0) < 4) {
            sendInhibitionStrength = 2;
        } else if (getInt("InhibitionStrength", 0) >= 4 && getInt("InhibitionStrength", 0) < 8) {
            sendInhibitionStrength = 4;
        } else if (getInt("InhibitionStrength", 0) >= 8 && getInt("InhibitionStrength", 0) < 16) {
            sendInhibitionStrength = 8;
        } else if (getInt("InhibitionStrength", 0) >= 16 && getInt("InhibitionStrength", 0) < 32) {
            sendInhibitionStrength = 16;
        } else if (getInt("InhibitionStrength", 0) >= 32 && getInt("InhibitionStrength", 0) < 64) {
            sendInhibitionStrength = 32;
        } else if (getInt("InhibitionStrength", 0) >= 64 && getInt("InhibitionStrength", 0) < 128) {
            sendInhibitionStrength = 64;
        } else if (getInt("InhibitionStrength", 0) == 128) {
            sendInhibitionStrength = 128;
        }
        for (int i = 0; i <= 5; i++) {
            // Send all the OMC configuration.
            sendCommand((byte) 240, (byte) (IFthreshold & 0xFF), true); //F0 240
            sendCommand((byte) 241, (byte) ((IFthreshold >>> 8) & 0xFF), true); //F1 241
            sendCommand((byte) 242, (byte) ((IFthreshold >>> 16) & 0xFF), true); //F2 242
            sendCommand((byte) 243, (byte) ((IFthreshold >>> 24) & 0xFF), true); //F3 243
            sendCommand((byte) 244, (byte) (sendDecayTimeMs & 0xFF), true); //F4 244
            sendCommand((byte) 245, (byte) ((sendDecayTimeMs >>> 8) & 0xFF), true); //F5 245
            sendCommand((byte) 246, (byte) ((sendDecayTimeMs >>> 16) & 0xFF), true); //F6 246
            sendCommand((byte) 247, (byte) ((sendDecayTimeMs >>> 24) & 0xFF), true); //F7 247
            sendCommand((byte) 248, (byte) (sendExcitationStrength & 0xFF), true); //F8 248
            sendCommand((byte) 249, (byte) (sendSaturation & 0xFF), true); //F9 249
            sendCommand((byte) 250, (byte) (sendInhibitionStrength & 0xFF), true); //FA 250
            sendCommand((byte) 251, (byte) (sendTauNDecay & 0xFF), true); //FB 251
            sendCommand((byte) 252, (byte) ((sendTauNDecay >>> 8) & 0xFF), true); //FB 252
            sendCommand((byte) 253, (byte) ((sendTauNDecay >>> 16) & 0xFF), true); //FB 253
            sendCommand((byte) 254, (byte) ((sendTauNDecay >>> 24) & 0xFF), true); //FB 25
            sendCommand((byte) 0, (byte) 0, false);
            System.out.print("Sending ");
            System.out.println(i);
        }
    }
    // End of Configuration of OMC

    public int getTrackerId() {
        return trackerId;
    }

    public static int getMinTrackerId() {
        return 1;
    }

    public static int getMaxTrackerId() {
        return 4;
    }

    public void setTrackerId(final int trackerId) {
        this.trackerId = trackerId;
        putInt("trackerId", trackerId);
    }

    public int getCmCellInitX() {
        return cmCellInitX;
    }

    public static int getMinCmCellInitX() {
        return 0;
    }

    public static int getMaxCmCellInitX() {
        return 127;
    }

    public void setCmCellInitX(final int cmCellInitX) {
        this.cmCellInitX = cmCellInitX;
        putInt("cmCellInitX", cmCellInitX);
    }

    public int getCmCellInitY() {
        return cmCellInitY;
    }

    public static int getMinCmCellInitY() {
        return 0;
    }

    public static int getMaxCmCellInitY() {
        return 127;
    }

    public void setCmCellInitY(final int cmCellInitY) {
        this.cmCellInitY = cmCellInitY;
        putInt("cmCellInitY", cmCellInitY);
    }

    public int getCmCellRadixStep() {
        return cmCellRadixStep;
    }

    public static int getMinCmCellRadixStep() {
        return 0;
    }

    public static int getMaxCmCellRadixStep() {
        return 7;
    }

    public void setCmCellRadixStep(final int cmCellRadixStep) {
        this.cmCellRadixStep = cmCellRadixStep;
        putInt("cmCellRadixStep", cmCellRadixStep);
    }

    public int getCmCellRadixTH() {
        return cmCellRadixTH;
    }

    public static int getMinCmCellRadixTH() {
        return 0;
    }

    public static int getMaxCmCellRadixTH() {
        return 7;
    }

    public void setCmCellRadixTH(final int cmCellRadixTH) {
        this.cmCellRadixTH = cmCellRadixTH;
        putInt("cmCellRadixTH", cmCellRadixTH);
    }

    public int getCmCellInitRadix() {
        return cmCellInitRadix;
    }

    public static int getMinCmCellInitRadix() {
        return 0;
    }

    public static int getMaxCmCellInitRadix() {
        return 127;
    }

    public void setCmCellInitRadix(final int cmCellInitRadix) {
        this.cmCellInitRadix = cmCellInitRadix;
        putInt("cmCellInitRadix", cmCellInitRadix);
    }

    public int getCmCellRadixMin() {
        return cmCellRadixMin;
    }

    public static int getMinCmCellRadixMin() {
        return 0;
    }

    public static int getMaxCmCellRadixMin() {
        return 127;
    }

    public void setCmCellRadixMin(final int cmCellRadixMin) {
        this.cmCellRadixMin = cmCellRadixMin;
        putInt("cmCellRadixMin", cmCellRadixMin);
    }

    public int getCmCellRadixMax() {
        return cmCellRadixMax;
    }

    public static int getMinCmCellRadixMax() {
        return 0;
    }

    public static int getMaxCmCellRadixMax() {
        return 127;
    }

    public void setCmCellRadixMax(final int cmCellRadixMax) {
        this.cmCellRadixMax = cmCellRadixMax;
        putInt("cmCellRadixMax", cmCellRadixMax);
    }

    public int getCmCellMaxTime() {
        return cmCellMaxTime;
    }

    public static int getMinCmCellMaxTime() {
        return 1; // 1 micro-second (in �s).
    }

    public static int getMaxCmCellMaxTime() {
        return 1000000; // 1 second (in �s).
    }

    public void setCmCellMaxTime(final int cmCellMaxTime) {
        this.cmCellMaxTime = cmCellMaxTime;
        putInt("cmCellMaxTime", cmCellMaxTime);
    }

    public int getCmCellNevTh() {
        return cmCellNevTh;
    }

    public static int getMinCmCellNevTh() {
        return 1;
    }

    public static int getMaxCmCellNevTh() {
        return 1000;
    }

    public void setCmCellNevTh(final int cmCellNevTh) {
        this.cmCellNevTh = cmCellNevTh;
        putInt("cmCellNevTh", cmCellNevTh);
    }

    public int getCmCellAVG() {
        return cmCellAVG;
    }

    public static int getMinCmCellAVG() {
        return 1;
    }

    public static int getMaxCmCellAVG() {
        return 8;
    }

    public void setCmCellAVG(final int cmCellAVG) {
        this.cmCellAVG = cmCellAVG;
        putInt("cmCellAVG", cmCellAVG);
    }

    public boolean isTrackerEnable() {
        return trackerEnable;
    }

    public void setTrackerEnable(final boolean trackerEnable) {
        this.trackerEnable = trackerEnable;
        putBoolean("trackerEnable", trackerEnable);
    }

    public boolean isReset() {
        return Reset;
    }

    public void setReset(final boolean Reset) {
        this.Reset = Reset;
        putBoolean("Reset", Reset);
    }

    synchronized public void doConfigureCMCell() {
        // Verify that we have a USB device to send to.
        if (devHandle == null) {
            return;
        }

        // Convert time into cycles.
        final int cmCellMaxTimeCycles = getInt("cmCellMaxTime", 0) * CLOCK_SPEED;

        // Select the tracker.
        sendCommand((byte) 127, (byte) (getInt("trackerId", 0) & 0xFF), true);

        // Send all the tracker configuration.
        sendCommand((byte) 78, (byte) ((128 - getInt("cmCellInitY", 0)) & 0xFF), true);
        sendCommand((byte) 79, (byte) ((getInt("cmCellInitX", 0) + 128) & 0xFF), true);
        sendCommand((byte) 80, (byte) (getInt("cmCellRadixTH", 0) & 0xFF), true);
        sendCommand((byte) 81, (byte) (getInt("cmCellInitRadix", 0) & 0xFF), true);
        sendCommand((byte) 82, (byte) (cmCellMaxTimeCycles & 0xFF), true);
        sendCommand((byte) 83, (byte) ((cmCellMaxTimeCycles >>> 8) & 0xFF), true);
        sendCommand((byte) 84, (byte) ((cmCellMaxTimeCycles >>> 16) & 0xFF), true);
        sendCommand((byte) 85, (byte) ((cmCellMaxTimeCycles >>> 24) & 0xFF), true);
        sendCommand((byte) 86, (byte) (getInt("cmCellNevTh", 0) & 0xFF), true);
        sendCommand((byte) 87, (byte) (getInt("cmCellAVG", 0) & 0xFF), true);
        sendCommand((byte) 88, (byte) ((getBoolean("trackerEnable", true)) ? (0xFF) : (0x00)), true);
        sendCommand((byte) 89, (byte) (getInt("cmCellRadixStep", 0) & 0xFF), true);
        sendCommand((byte) 90, (byte) (getInt("cmCellRadixMax", 0) & 0xFF), true);
        sendCommand((byte) 91, (byte) (getInt("cmCellRadixMin", 0) & 0xFF), true);

        // Disable tracker configuration.
        sendCommand((byte) 127, (byte) 0xFF, true);

        sendCommand((byte) 0, (byte) 0, false);

    }

    public int getBgaFilterDeltaT() {
        return bgaFilterDeltaT;
    }

    public static int getMinBgaFilterDeltaT() {
        return 1; // 1 micro-second (in �s).
    }

    public static int getMaxBgaFilterDeltaT() {
        return 1000000; // 1 second (in �s).
    }

    public void setBgaFilterDeltaT(final int bgaFilterDeltaT) {
        this.bgaFilterDeltaT = bgaFilterDeltaT;
        putInt("bgaFilterDeltaT", bgaFilterDeltaT);
    }

    public int getBgaNeighbors() {
        return bgaNeighbors;
    }

    public static int getMinBgaNeighbors() {
        return 0; // No neighbors.
    }

    public static int getMaxBgaNeighbors() {
        return 8; // All closest neighbors.
    }

    public void setBgaNeighbors(final int bgaNeighbors) {
        this.bgaNeighbors = bgaNeighbors;
        putInt("bgaNeighbors", bgaNeighbors);
    }

    synchronized public void doConfigureBGAFilter() {
        // Verify that we have a USB device to send to.
        if (devHandle == null) {
            return;
        }

        // Convert time into cycles.
        final int bgaFilterDeltaTCycles = getInt("bgaFilterDeltaT", 0) * CLOCK_SPEED;

        // Send the four bytes that make up the integer to their respective
        // addresses.
        sendCommand((byte) 128, (byte) (bgaFilterDeltaTCycles & 0xFF), true);
        sendCommand((byte) 129, (byte) ((bgaFilterDeltaTCycles >>> 8) & 0xFF), true);
        sendCommand((byte) 130, (byte) ((bgaFilterDeltaTCycles >>> 16) & 0xFF), true);
        sendCommand((byte) 131, (byte) ((bgaFilterDeltaTCycles >>> 24) & 0xFF), true);
        sendCommand((byte) 135, (byte) (getInt("bgaNeighbors", 0) & 0xFF), true);
        sendCommand((byte) 136, (byte) ((getBoolean("Reset", true)) ? (0xFF) : (0x00)), true);
        setReset(false);
        sendCommand((byte) 0, (byte) 0, false);
    }

    @Override
    public EventPacket<?> filterPacket(final EventPacket<?> in) {
        // Don't modify events and packets going through.
        return (in);
    }

    // The SiLabs C8051F320 used by ATC has VID=0xC410 and PID=0x0000.
    private final short VID = (short) 0x10C4;
    private final short PID = 0x0000;

    private final byte ENDPOINT = 0x02;
    private final int PACKET_LENGTH = 64;

    private DeviceHandle devHandle = null;

    private void openDevice() {
        System.out.println("Searching for device.");

        // Already opened.
        if (devHandle != null) {
            return;
        }

        // Search for a suitable device and connect to it.
        LibUsb.init(null);

        final DeviceList list = new DeviceList();
        if (LibUsb.getDeviceList(null, list) > 0) {
            final Iterator<Device> devices = list.iterator();
            while (devices.hasNext()) {
                final Device dev = devices.next();

                final DeviceDescriptor devDesc = new DeviceDescriptor();
                LibUsb.getDeviceDescriptor(dev, devDesc);

                if ((devDesc.idVendor() == VID) && (devDesc.idProduct() == PID)) {
                    // Found matching device, open it.
                    devHandle = new DeviceHandle();
                    if (LibUsb.open(dev, devHandle) != LibUsb.SUCCESS) {
                        devHandle = null;
                        continue;
                    }

                    final IntBuffer activeConfig = BufferUtils.allocateIntBuffer();
                    LibUsb.getConfiguration(devHandle, activeConfig);

                    if (activeConfig.get() != 1) {
                        LibUsb.setConfiguration(devHandle, 1);
                    }

                    LibUsb.claimInterface(devHandle, 0);

                    System.out.println("Successfully found device.");
                }
            }

            LibUsb.freeDeviceList(list, true);
        }
    }

    private void closeDevice() {
        System.out.println("Shutting down device.");

        // Use reset to close connection.
        if (devHandle != null) {
            LibUsb.releaseInterface(devHandle, 0);
            LibUsb.close(devHandle);
            devHandle = null;

            LibUsb.exit(null);
        }
    }

    private void sendCommand(final byte cmd, final byte data, final boolean spiEnable) {
        System.out.println(String.format("Sending command - cmd: %X, data: %X", cmd, data));

        // Check for presence of ready device.
        if (devHandle == null) {
            return;
        }

        // Prepare message.
        final ByteBuffer dataBuffer = BufferUtils.allocateByteBuffer(PACKET_LENGTH);

        dataBuffer.put(0, (byte) 'A');
        dataBuffer.put(1, (byte) 'T');
        dataBuffer.put(2, (byte) 'C');
        dataBuffer.put(3, (byte) 0x01); // Command always 1 for SPI upload.
        dataBuffer.put(4, (byte) 0x01); // Data length always 1 for 1 byte.
        dataBuffer.put(5, (byte) 0x00);
        dataBuffer.put(6, (byte) 0x00);
        dataBuffer.put(7, (byte) 0x00);
        dataBuffer.put(8, cmd); // Send actual SPI command (address usually).
        dataBuffer.put(9, (byte) ((spiEnable) ? (0x00) : (0x01)));
		// Enable or disable SPI communication.

        // Send bulk transfer request on given endpoint.
        final IntBuffer transferred = BufferUtils.allocateIntBuffer();
        LibUsb.bulkTransfer(devHandle, ENDPOINT, dataBuffer, transferred, 0);
        if (transferred.get(0) != PACKET_LENGTH) {
            System.out.println("Failed to transfer whole packet.");
        }

        // Put content in a second packet.
        dataBuffer.put(0, data);

        // Send second bulk transfer request on given endpoint.
        LibUsb.bulkTransfer(devHandle, ENDPOINT, dataBuffer, transferred, 0);
        if (transferred.get(0) != PACKET_LENGTH) {
            System.out.println("Failed to transfer whole packet.");
        }
    }

    @Override
    public void resetFilter() {
        // Close any open device, and then open a new one.
        closeDevice();
        openDevice();
    }

    @Override
    public void initFilter() {
        // Open the device for the first time.
        openDevice();
    }
}

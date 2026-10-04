package net.sf.jaer.hardwareinterface.serial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import net.sf.jaer.hardwareinterface.serial.SerialPorts.LinuxLink;
import net.sf.jaer.hardwareinterface.serial.SerialPorts.UsbDevice;

public class SerialPortsTest {

    private static final String TEENSY_IOREG = """
            +-o USB Serial@01132300  <class IOUSBHostDevice, id 0x1, registered, matched, active, busy 0, retain 1>
            | {
            |   "idProduct" = 1155
            |   "idVendor" = 5824
            |   "USB Product Name" = "USB Serial"
            |   "USB Vendor Name" = "Teensyduino"
            |   "USB Serial Number" = "21094940"
            | }
            | +-o AppleUSBACMData  <class AppleUSBACMData, id 0x2, registered, matched, active, busy 0, retain 1>
            |   {
            |     "idProduct" = 1155
            |     "idVendor" = 5824
            |   }
            """;

    @Test
    public void macTeensyCalloutMatchesIoregSerial() {
        assertFalse(SerialPorts.includeMacCallout("cu.Bluetooth-Incoming-Port"));
        assertFalse(SerialPorts.includeMacCallout("tty.usbmodem210949401"));
        assertTrue(SerialPorts.includeMacCallout("cu.usbmodem210949401"));

        List<SerialPortInfo> ports = SerialPorts.macPorts(
                List.of("cu.usbmodem210949401", "cu.Bluetooth-Incoming-Port", "cu.debug-console"),
                TEENSY_IOREG);
        assertEquals(1, ports.size());
        SerialPortInfo port = ports.get(0);
        assertEquals("/dev/cu.usbmodem210949401", port.device());
        assertEquals("Teensyduino USB Serial", port.label());
        assertTrue(port.tooltip().contains("21094940"));
        assertTrue(port.tooltip().contains("16C0:0483"));
        assertTrue(port.tooltip().contains("/dev/cu.usbmodem210949401"));
    }

    @Test
    public void linuxByIdIsStableNameAndSkipsDuplicateTty() {
        UsbDevice usb = new UsbDevice();
        usb.vendor = "Teensyduino";
        usb.product = "USB Serial";
        usb.serial = "21094940";
        usb.vidHex = "16c0";
        usb.pidHex = "0483";
        String byId = "/dev/serial/by-id/usb-Teensyduino_USB_Serial_21094940-if00";
        List<SerialPortInfo> ports = SerialPorts.linuxPorts(
                List.of(new LinuxLink(byId, "ttyACM0")),
                List.of(),
                Map.of("ttyACM0", usb));
        assertEquals(1, ports.size());
        assertEquals(byId, ports.get(0).device());
        assertEquals("Teensyduino USB Serial", ports.get(0).label());
        assertTrue(ports.get(0).tooltip().contains("/dev/ttyACM0"));
        assertTrue(ports.get(0).tooltip().contains("16C0:0483"));
    }

    @Test
    public void linuxPlainTtyWhenByIdMissing() {
        List<SerialPortInfo> ports = SerialPorts.linuxPorts(
                List.of(),
                List.of("/dev/ttyACM0"),
                Map.of());
        assertEquals(1, ports.size());
        assertEquals("/dev/ttyACM0", ports.get(0).device());
        assertEquals("ttyACM0", ports.get(0).label());
    }

    @Test
    public void windowsFriendlyNameKeepsComPort() {
        List<SerialPortInfo> ports = SerialPorts.windowsPorts(
                List.of("COM3", "COM10"),
                "COM10|Teensyduino USB Serial (COM10)\nCOM3|USB Serial Device (COM3)\n");
        assertEquals(2, ports.size());
        SerialPortInfo com3 = ports.stream().filter(p -> p.device().equals("COM3")).findFirst().orElseThrow();
        SerialPortInfo com10 = ports.stream().filter(p -> p.device().equals("COM10")).findFirst().orElseThrow();
        assertEquals("USB Serial Device (COM3)", com3.label());
        assertEquals("Teensyduino USB Serial (COM10)", com10.label());
        assertTrue(com10.tooltip().contains("COM10"));
    }
}

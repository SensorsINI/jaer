package net.sf.jaer.hardwareinterface.serial.witmotion;

import gnu.io.NRSerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serial interface to a WitMotion HWT906 (and the same 11-byte {@code 0x55}
 * protocol used by the HWT905). Mirrors the public methods of the Python
 * {@code witmotion.IMU} class at
 * <a href="https://github.com/storborg/witmotion">storborg/witmotion</a>.
 * <p>
 * Default baud rate is {@value #DEFAULT_BAUD_RATE}, which is the HWT906
 * factory rate. The COM port is not fixed: {@link #discoverPort()} scans USB
 * serial ports (the number changes when the adapter is plugged into another
 * socket) and keeps the first one that emits valid WitMotion frames.
 * On Windows the port is opened with {@code kernel32} (nrjavaserial's JNA 4.2
 * native does not load on JDK 25).
 * <p>
 * A few wire values differ from that Python library because they are wrong on
 * the HWT906. See {@link #setUpdateRate}, {@link #setCalibrationMode}, and
 * {@link #setGyroAutomaticCalibration}.
 */
public final class WitMotionIMU implements AutoCloseable {

    /** HWT906 factory baud rate. */
    public static final int DEFAULT_BAUD_RATE = 921600;

    /** Gravity used by the WitMotion acceleration scaling, m/s^2. */
    public static final double G = 9.8;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Preferences prefs = Preferences.userNodeForPackage(WitMotionIMU.class);
    private static final String PREF_LAST_PORT = "lastPort";
    private static final int PAYLOAD_LENGTH = 8;
    private static final int COMMAND_GAP_MS = 100;
    private static final int PROBE_MS = 300;
    private static final int PROBE_FRAMES = 2;
    private static final int UNLOCK_REGISTER = 0x69;
    private static final int UNLOCK_KEY = 0xB588;

    private static final Map<Integer, MessageParser> PARSERS = new ConcurrentHashMap<>();

    static {
        PARSERS.put(TimeMessage.CODE, TimeMessage::parse);
        PARSERS.put(AccelerationMessage.CODE, AccelerationMessage::parse);
        PARSERS.put(AngularVelocityMessage.CODE, AngularVelocityMessage::parse);
        PARSERS.put(AngleMessage.CODE, AngleMessage::parse);
        PARSERS.put(MagneticMessage.CODE, MagneticMessage::parse);
        PARSERS.put(QuaternionMessage.CODE, QuaternionMessage::parse);
    }

    private final String portName;
    private final int baudRate;
    private final SerialLink link;
    private final InputStream input;
    private final OutputStream output;
    private final Object writeLock = new Object();
    private final Map<Class<?>, List<Consumer<ReceiveMessage>>> subscribers = new ConcurrentHashMap<>();
    private final List<Consumer<ReceiveMessage>> anySubscriber = new CopyOnWriteArrayList<>();
    private final Thread rxThread;

    private volatile boolean closed;
    private volatile Double lastTimestamp;
    private volatile Double lastTempCelsius;
    private volatile double[] lastA;
    private volatile double[] lastW;
    private volatile double[] lastAngle;
    private volatile double[] lastMag;
    private volatile double[] lastQ;

    /**
     * Open the HWT906 found by {@link #discoverPort()} at {@link #DEFAULT_BAUD_RATE}.
     */
    public WitMotionIMU() throws IOException {
        this(discoverPort(), DEFAULT_BAUD_RATE);
    }

    /**
     * Open {@code portName} at {@link #DEFAULT_BAUD_RATE}.
     */
    public WitMotionIMU(String portName) throws IOException {
        this(portName, DEFAULT_BAUD_RATE);
    }

    /**
     * Open {@code portName} at {@code baudRate} and start the receive thread.
     */
    public WitMotionIMU(String portName, int baudRate) throws IOException {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("portName is empty");
        }
        this.portName = portName;
        this.baudRate = baudRate;
        SerialLink opened;
        try {
            opened = openLink(portName, baudRate);
        } catch (RuntimeException e) {
            throw new IOException("Could not open " + portName + " at " + baudRate, e);
        }
        this.link = opened;
        this.input = opened.input;
        this.output = opened.output;
        prefs.put(PREF_LAST_PORT, portName);
        rxThread = new Thread(this::rxLoop, "WitMotionIMU-rx");
        rxThread.setDaemon(true);
        rxThread.start();
        log.info("WitMotion IMU open on " + portName + " at " + baudRate);
    }

    public String getPortName() {
        return portName;
    }

    public int getBaudRate() {
        return baudRate;
    }

    /**
     * Find a plugged-in WitMotion serial IMU.
     * <p>
     * USB serial ports are listed from the Windows USB enumeration (so a
     * motherboard COM port is not opened). Each candidate is opened at
     * {@link #DEFAULT_BAUD_RATE} until one emits valid {@code 0x55} frames.
     * The last port that worked is tried first. The COM number itself is not
     * stored as a requirement — unplugging the adapter moves it.
     *
     * @return port name such as {@code COM3}
     * @throws IOException if no port produces WitMotion frames
     */
    public static String discoverPort() throws IOException {
        Set<String> present = presentPortNames();
        if (present.isEmpty()) {
            throw new IOException("No serial ports are present");
        }
        LinkedHashSet<String> order = new LinkedHashSet<>();
        String last = prefs.get(PREF_LAST_PORT, "");
        if (!last.isEmpty() && present.contains(last)) {
            order.add(last);
        }
        List<String> usb = usbSerialPortNames();
        for (String name : usb) {
            if (present.contains(name)) {
                order.add(name);
            }
        }
        if (usb.isEmpty()) {
            order.addAll(present);
        }
        List<String> tried = new ArrayList<>();
        for (String name : order) {
            tried.add(name);
            if (probe(name)) {
                log.info("WitMotion IMU discovered on " + name);
                prefs.put(PREF_LAST_PORT, name);
                return name;
            }
        }
        throw new IOException("No WitMotion IMU responded at " + DEFAULT_BAUD_RATE
                + " on " + tried + " (present " + present + ")");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        rxThread.interrupt();
        try {
            rxThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeQuietly(link);
        log.info("WitMotion IMU closed on " + portName);
    }

    /**
     * Subscribe to every decoded message.
     */
    public void subscribe(Consumer<ReceiveMessage> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is null");
        }
        anySubscriber.add(callback);
    }

    /**
     * Subscribe to one message type, matching Python {@code subscribe(callback, cls)}.
     */
    public <T extends ReceiveMessage> void subscribe(Class<T> messageClass, Consumer<T> callback) {
        if (messageClass == null || callback == null) {
            throw new IllegalArgumentException("messageClass and callback are required");
        }
        subscribers.computeIfAbsent(messageClass, k -> new CopyOnWriteArrayList<>())
                .add(message -> callback.accept(messageClass.cast(message)));
    }

    /** Last device timestamp as Unix seconds, or null if none has arrived. */
    public Double getTimestamp() {
        return lastTimestamp;
    }

    /** Last acceleration in m/s^2, {@code {ax, ay, az}}, or null. */
    public double[] getAcceleration() {
        return copy(lastA);
    }

    /** Last angular velocity in deg/s, {@code {wx, wy, wz}}, or null. */
    public double[] getAngularVelocity() {
        return copy(lastW);
    }

    /** Last Euler angles in degrees, {@code {roll, pitch, yaw}}, or null. */
    public double[] getAngle() {
        return copy(lastAngle);
    }

    /** Last magnetic vector in sensor LSB, {@code {hx, hy, hz}}, or null. */
    public double[] getMagneticVector() {
        return copy(lastMag);
    }

    /** Last quaternion {@code {q0, q1, q2, q3}}, or null. */
    public double[] getQuaternion() {
        return copy(lastQ);
    }

    /** Last temperature in celsius from an acceleration or magnetic frame, or null. */
    public Double getTemperatureCelsius() {
        return lastTempCelsius;
    }

    public void saveConfiguration() throws IOException {
        sendConfigCommand(new ConfigCommand(Register.SAVE, 0));
    }

    /**
     * Write one 5-byte command. Prefer {@link #sendConfigCommand} for register
     * writes; the device ignores them unless the unlock key was sent first.
     */
    public void sendCommand(ConfigCommand cmd) throws IOException {
        if (cmd == null) {
            throw new IllegalArgumentException("cmd is null");
        }
        byte[] buf = cmd.serialize();
        synchronized (writeLock) {
            if (closed) {
                throw new IOException("IMU is closed");
            }
            output.write(buf);
            output.flush();
            log.fine("WitMotion command " + cmd + " -> " + hex(buf));
            try {
                Thread.sleep(COMMAND_GAP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while pacing WitMotion command", e);
            }
        }
    }

    /**
     * Unlock registers, then send {@code cmd}.
     */
    public void sendConfigCommand(ConfigCommand cmd) throws IOException {
        sendCommand(new ConfigCommand(Register.KEY, UNLOCK_KEY));
        sendCommand(cmd);
    }

    /** Restore factory configuration (SAVE register = 1). */
    public void setDefaultConfiguration() throws IOException {
        sendConfigCommand(new ConfigCommand(Register.SAVE, 1));
    }

    /**
     * Set CALSW. {@link CalibrationMode#MAGNETIC} writes {@code 0x07} (spherical
     * fit). The Python enum value {@code 2} is not a CALSW code on this sensor.
     * {@link CalibrationMode#NONE} ends calibration.
     */
    public void setCalibrationMode(CalibrationMode mode) throws IOException {
        if (mode == null) {
            throw new IllegalArgumentException("mode is null");
        }
        sendConfigCommand(new ConfigCommand(Register.CALSW, mode.code));
    }

    public void setInstallationDirection(InstallationDirection direction) throws IOException {
        if (direction == null) {
            throw new IllegalArgumentException("direction is null");
        }
        sendConfigCommand(new ConfigCommand(Register.DIRECTION, direction.code));
    }

    /** Sleep if the device is running. Any later serial command wakes it. */
    public void toggleSleep() throws IOException {
        sendConfigCommand(new ConfigCommand(Register.SLEEP, 0x01));
    }

    /**
     * @param n {@code 9} for the magnetometer heading, {@code 6} for gyro-integrated heading
     */
    public void setAlgorithmDof(int n) throws IOException {
        if (n != 6 && n != 9) {
            throw new IllegalArgumentException("DoF must be 6 or 9, got " + n);
        }
        sendConfigCommand(new ConfigCommand(Register.ALG, n == 9 ? 0x00 : 0x01));
    }

    /**
     * Enable or disable automatic gyro bias reset.
     * <p>
     * HWT906 register {@code 0x63} is {@code GYROCALTIME} in milliseconds (how
     * long the gyro must stay still), not the on/off flag the Python library
     * writes as {@code 0} or {@code 1}. Enabled restores the protocol default
     * of 1000 ms. Disabled writes 0.
     */
    public void setGyroAutomaticCalibration(boolean enabled) throws IOException {
        sendConfigCommand(new ConfigCommand(Register.GYROCALTIME, enabled ? 0x03E8 : 0));
    }

    /**
     * Enable only the given output message types (RSW bitmask).
     */
    public void setMessagesEnabled(Set<Class<? extends ReceiveMessage>> classes) throws IOException {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        int mask = 0;
        for (Class<? extends ReceiveMessage> cls : classes) {
            Integer code = codeOf(cls);
            if (code == null) {
                throw new IllegalArgumentException("Not a WitMotion output message: " + cls);
            }
            mask |= 1 << (code - 0x50);
        }
        sendConfigCommand(new ConfigCommand(Register.RSW, mask));
    }

    /**
     * Set the output rate in Hz.
     * <p>
     * Accepted values are 0.2, 0.5, 1, 2, 5, 10, 20, 50, 100, 125, 200, and the
     * HWT906 rates 500 and 1000. On this sensor {@code 0x0C} is 500 Hz and
     * {@code 0x0D} is 1000 Hz. The Python library uses those two codes for
     * single-shot and "no output"; use {@link #setUpdateRateSingle()} instead.
     */
    public void setUpdateRate(double rateHz) throws IOException {
        Integer code = rateCode(rateHz);
        if (code == null) {
            throw new IllegalArgumentException("Unsupported update rate: " + rateHz
                    + " Hz. HWT906 accepts 0.2, 0.5, 1, 2, 5, 10, 20, 50, 100, 125, 200, 500, 1000");
        }
        sendConfigCommand(new ConfigCommand(Register.RATE, code));
    }

    /** Ask the device for one output burst. HWT906 code is {@code 0x10}. */
    public void setUpdateRateSingle() throws IOException {
        sendConfigCommand(new ConfigCommand(Register.RATE, 0x10));
    }

    /**
     * Change the device baud rate. Reopen the port afterwards; this method does
     * not switch the host UART, because the device changes rate only after the
     * command is accepted.
     */
    public void setBaudrate(int rate) throws IOException {
        int code = switch (rate) {
            case 4800 -> 0x01;
            case 9600 -> 0x02;
            case 19200 -> 0x03;
            case 38400 -> 0x04;
            case 57600 -> 0x05;
            case 115200 -> 0x06;
            case 230400 -> 0x07;
            case 460800 -> 0x08;
            case 921600 -> 0x09;
            default -> throw new IllegalArgumentException("Unsupported baudrate: " + rate);
        };
        sendConfigCommand(new ConfigCommand(Register.BAUD, code));
    }

    /** Three register values for AXOFFSET, AYOFFSET, AZOFFSET. */
    public void setAccelerationBias(int[] values) throws IOException {
        writeBias(values, Register.AXOFFSET, Register.AYOFFSET, Register.AZOFFSET);
    }

    /** Three register values for GXOFFSET, GYOFFSET, GZOFFSET. */
    public void setAngularVelocityBias(int[] values) throws IOException {
        writeBias(values, Register.GXOFFSET, Register.GYOFFSET, Register.GZOFFSET);
    }

    /** Three register values for HXOFFSET, HYOFFSET, HZOFFSET. */
    public void setMagneticBias(int[] values) throws IOException {
        writeBias(values, Register.HXOFFSET, Register.HYOFFSET, Register.HZOFFSET);
    }

    private void writeBias(int[] values, Register x, Register y, Register z) throws IOException {
        if (values == null || values.length != 3) {
            throw new IllegalArgumentException("bias must be three integers");
        }
        Register[] regs = {x, y, z};
        for (int i = 0; i < 3; i++) {
            sendConfigCommand(new ConfigCommand(regs[i], values[i]));
        }
    }

    private void rxLoop() {
        int state = 0;
        int code = 0;
        byte[] payload = new byte[PAYLOAD_LENGTH + 1];
        int filled = 0;
        byte[] buf = new byte[512];
        while (!closed) {
            try {
                int n = input.read(buf);
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                for (int i = 0; i < n && !closed; i++) {
                    int b = buf[i] & 0xFF;
                    if (state == 0) {
                        if (b == 0x55) {
                            state = 1;
                        }
                    } else if (state == 1) {
                        if (PARSERS.containsKey(b)) {
                            code = b;
                            filled = 0;
                            state = 2;
                        } else {
                            state = b == 0x55 ? 1 : 0;
                        }
                    } else {
                        payload[filled++] = (byte) b;
                        if (filled < payload.length) {
                            continue;
                        }
                        int got = payload[PAYLOAD_LENGTH] & 0xFF;
                        int sum = checksum(code, payload);
                        if (sum != got) {
                            log.warning(String.format("WitMotion checksum wanted 0x%02X got 0x%02X code 0x%02X", sum, got, code));
                        } else {
                            ReceiveMessage msg = PARSERS.get(code).parse(payload);
                            if (msg != null) {
                                handle(msg);
                            }
                        }
                        state = 0;
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    log.log(Level.WARNING, "WitMotion read failed on " + portName, e);
                }
                break;
            }
        }
    }

    private void handle(ReceiveMessage msg) {
        if (msg instanceof TimeMessage time) {
            lastTimestamp = time.timestampSeconds;
        } else if (msg instanceof AccelerationMessage acc) {
            lastA = acc.metersPerSecondSquared.clone();
            lastTempCelsius = acc.tempCelsius;
        } else if (msg instanceof AngularVelocityMessage w) {
            lastW = w.degreesPerSecond.clone();
        } else if (msg instanceof AngleMessage angle) {
            lastAngle = new double[] {angle.roll, angle.pitch, angle.yaw};
        } else if (msg instanceof MagneticMessage mag) {
            lastMag = mag.field.clone();
            if (lastTempCelsius == null) {
                lastTempCelsius = mag.tempCelsius;
            }
        } else if (msg instanceof QuaternionMessage q) {
            lastQ = q.q.clone();
        }
        List<Consumer<ReceiveMessage>> typed = subscribers.get(msg.getClass());
        if (typed != null) {
            for (Consumer<ReceiveMessage> cb : typed) {
                cb.accept(msg);
            }
        }
        for (Consumer<ReceiveMessage> cb : anySubscriber) {
            cb.accept(msg);
        }
    }

    private static int checksum(int code, byte[] payload8) {
        int sum = 0x55 + code;
        for (int i = 0; i < PAYLOAD_LENGTH; i++) {
            sum += payload8[i] & 0xFF;
        }
        return sum & 0xFF;
    }

    private static Integer codeOf(Class<? extends ReceiveMessage> cls) {
        if (cls == TimeMessage.class) {
            return TimeMessage.CODE;
        }
        if (cls == AccelerationMessage.class) {
            return AccelerationMessage.CODE;
        }
        if (cls == AngularVelocityMessage.class) {
            return AngularVelocityMessage.CODE;
        }
        if (cls == AngleMessage.class) {
            return AngleMessage.CODE;
        }
        if (cls == MagneticMessage.class) {
            return MagneticMessage.CODE;
        }
        if (cls == QuaternionMessage.class) {
            return QuaternionMessage.CODE;
        }
        return null;
    }

    private static Integer rateCode(double hz) {
        if (near(hz, 0.2)) {
            return 0x01;
        }
        if (near(hz, 0.5)) {
            return 0x02;
        }
        if (near(hz, 1)) {
            return 0x03;
        }
        if (near(hz, 2)) {
            return 0x04;
        }
        if (near(hz, 5)) {
            return 0x05;
        }
        if (near(hz, 10)) {
            return 0x06;
        }
        if (near(hz, 20)) {
            return 0x07;
        }
        if (near(hz, 50)) {
            return 0x08;
        }
        if (near(hz, 100)) {
            return 0x09;
        }
        if (near(hz, 125)) {
            return 0x0A;
        }
        if (near(hz, 200)) {
            return 0x0B;
        }
        if (near(hz, 500)) {
            return 0x0C;
        }
        if (near(hz, 1000)) {
            return 0x0D;
        }
        return null;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static double[] copy(double[] v) {
        return v == null ? null : v.clone();
    }

    private static short i16(byte[] body, int offset) {
        int lo = body[offset] & 0xFF;
        int hi = body[offset + 1] & 0xFF;
        return (short) (lo | (hi << 8));
    }

    private static int u16(byte[] body, int offset) {
        int lo = body[offset] & 0xFF;
        int hi = body[offset + 1] & 0xFF;
        return lo | (hi << 8);
    }

    private static double scaled(short raw, double fullScale) {
        return (raw / 32768.0) * fullScale;
    }

    private static boolean probe(String name) {
        SerialLink candidate = null;
        try {
            candidate = openLink(name, DEFAULT_BAUD_RATE);
            int frames = countValidFrames(candidate.input, PROBE_MS);
            log.fine("WitMotion probe " + name + " valid frames=" + frames);
            return frames >= PROBE_FRAMES;
        } catch (Exception e) {
            log.fine("WitMotion probe skipped " + name + ": " + e);
            return false;
        } finally {
            closeQuietly(candidate);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Set<String> presentPortNames() {
        if (isWindows()) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            names.addAll(usbSerialPortNames());
            names.addAll(serialCommPortNames());
            return names;
        }
        return NRSerialPort.getAvailableSerialPorts();
    }

    private static SerialLink openLink(String name, int baudRate) throws IOException {
        if (isWindows()) {
            WinSerialPort port = new WinSerialPort(name, baudRate);
            return new SerialLink(port.input(), port.output(), port);
        }
        NRSerialPort port = new NRSerialPort(name, baudRate);
        try {
            port.connect();
            if (!port.isConnected()) {
                throw new IOException("Could not open " + name + " at " + baudRate);
            }
            return new SerialLink(port.getInputStream(), port.getOutputStream(), () -> disconnectQuietly(port));
        } catch (IOException e) {
            disconnectQuietly(port);
            throw e;
        } catch (RuntimeException e) {
            disconnectQuietly(port);
            throw new IOException("Could not open " + name + " at " + baudRate, e);
        }
    }

    private static final class SerialLink implements AutoCloseable {
        final InputStream input;
        final OutputStream output;
        private final AutoCloseable closer;

        SerialLink(InputStream input, OutputStream output, AutoCloseable closer) {
            this.input = input;
            this.output = output;
            this.closer = closer;
        }

        @Override
        public void close() throws Exception {
            closer.close();
        }
    }

    private static int countValidFrames(InputStream in, long budgetMs) throws IOException {
        long deadline = System.currentTimeMillis() + budgetMs;
        int state = 0;
        int code = 0;
        byte[] payload = new byte[PAYLOAD_LENGTH + 1];
        int filled = 0;
        int valid = 0;
        byte[] buf = new byte[256];
        while (System.currentTimeMillis() < deadline && valid < PROBE_FRAMES) {
            int n = in.read(buf);
            if (n < 0) {
                break;
            }
            if (n == 0) {
                continue;
            }
            for (int i = 0; i < n; i++) {
                int b = buf[i] & 0xFF;
                if (state == 0) {
                    if (b == 0x55) {
                        state = 1;
                    }
                } else if (state == 1) {
                    if (PARSERS.containsKey(b)) {
                        code = b;
                        filled = 0;
                        state = 2;
                    } else {
                        state = b == 0x55 ? 1 : 0;
                    }
                } else {
                    payload[filled++] = (byte) b;
                    if (filled < payload.length) {
                        continue;
                    }
                    if (checksum(code, payload) == (payload[PAYLOAD_LENGTH] & 0xFF)) {
                        valid++;
                    }
                    state = 0;
                }
            }
        }
        return valid;
    }

    /**
     * USB-enumerated serial port names. Empty when the OS query fails, in which
     * case discovery falls back to every port {@link NRSerialPort} can see.
     */
    private static List<String> usbSerialPortNames() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Pattern com = Pattern.compile("\\bCOM\\d+\\b", Pattern.CASE_INSENSITIVE);
        for (String root : new String[] {
                "HKLM\\SYSTEM\\CurrentControlSet\\Enum\\USB",
                "HKLM\\SYSTEM\\CurrentControlSet\\Enum\\FTDIBUS"
        }) {
            try {
            Process process = new ProcessBuilder("reg", "query", root, "/s", "/f", "PortName")
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(captured);
                } catch (IOException ignored) {
                    // process destroyed, or the pipe closed
                }
            }, "WitMotion-reg");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            reader.join(1000);
            String text = captured.toString();
                Matcher matcher = com.matcher(text);
                while (matcher.find()) {
                    String name = matcher.group().toUpperCase(Locale.ROOT);
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                }
            } catch (Exception e) {
                log.fine("WitMotion USB port query failed for " + root + ": " + e);
            }
        }
        return names;
    }

    private static void closeQuietly(SerialLink link) {
        if (link == null) {
            return;
        }
        try {
            link.close();
        } catch (Exception e) {
            log.fine("WitMotion disconnect: " + e);
        }
    }

    /** Ports named in HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM. */
    private static List<String> serialCommPortNames() {
        List<String> names = new ArrayList<>();
        Pattern com = Pattern.compile("\\bCOM\\d+\\b", Pattern.CASE_INSENSITIVE);
        try {
            Process process = new ProcessBuilder(
                    "reg", "query", "HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM")
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(captured);
                } catch (IOException ignored) {
                    // process destroyed, or the pipe closed
                }
            }, "WitMotion-reg");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            reader.join(1000);
            Matcher matcher = com.matcher(captured.toString());
            while (matcher.find()) {
                String name = matcher.group().toUpperCase(Locale.ROOT);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        } catch (Exception e) {
            log.fine("WitMotion SERIALCOMM query failed: " + e);
        }
        return names;
    }

    private static void disconnectQuietly(NRSerialPort serial) {
        try {
            if (serial != null && serial.isConnected()) {
                serial.disconnect();
            }
        } catch (RuntimeException e) {
            log.fine("WitMotion disconnect: " + e);
        }
    }

    private static String hex(byte[] buf) {
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    @FunctionalInterface
    private interface MessageParser {
        ReceiveMessage parse(byte[] body);
    }

    /** Decoded device-to-host frame. */
    public abstract static class ReceiveMessage {
        @Override
        public abstract String toString();
    }

    /** Output type {@code 0x50}. Year byte is years since 2000. */
    public static final class TimeMessage extends ReceiveMessage {
        public static final int CODE = 0x50;
        /** Unix time in seconds, including milliseconds as a fraction. */
        public final double timestampSeconds;

        TimeMessage(double timestampSeconds) {
            this.timestampSeconds = timestampSeconds;
        }

        static TimeMessage parse(byte[] body) {
            int year = 2000 + (body[0] & 0xFF);
            int month = body[1] & 0xFF;
            int day = body[2] & 0xFF;
            int hour = body[3] & 0xFF;
            int minute = body[4] & 0xFF;
            int second = body[5] & 0xFF;
            int ms = u16(body, 6);
            try {
                LocalDateTime time = LocalDateTime.of(year, month, day, hour, minute, second);
                long extra = ms / 1000L;
                int milli = ms % 1000;
                double stamp = time.plusSeconds(extra).toEpochSecond(ZoneOffset.UTC) + milli / 1000.0;
                return new TimeMessage(stamp);
            } catch (DateTimeException e) {
                log.fine("WitMotion time fields ignored: " + e);
                return null;
            }
        }

        @Override
        public String toString() {
            return "time message - timestamp:" + timestampSeconds;
        }
    }

    /** Output type {@code 0x51}. Acceleration is {@code raw/32768*16*g}. */
    public static final class AccelerationMessage extends ReceiveMessage {
        public static final int CODE = 0x51;
        public final double[] metersPerSecondSquared;
        public final double tempCelsius;

        AccelerationMessage(double[] metersPerSecondSquared, double tempCelsius) {
            this.metersPerSecondSquared = metersPerSecondSquared;
            this.tempCelsius = tempCelsius;
        }

        static AccelerationMessage parse(byte[] body) {
            double[] a = new double[] {
                    scaled(i16(body, 0), 16 * G),
                    scaled(i16(body, 2), 16 * G),
                    scaled(i16(body, 4), 16 * G)
            };
            return new AccelerationMessage(a, i16(body, 6) / 100.0);
        }

        @Override
        public String toString() {
            return "acceleration message - vec:" + Arrays.toString(metersPerSecondSquared)
                    + " temp_celsius:" + tempCelsius;
        }
    }

    /**
     * Output type {@code 0x52}. Angular velocity is {@code raw/32768*2000} deg/s.
     * The trailer is supply voltage in the WitMotion protocol (often unused on
     * the USB HWT906), not temperature.
     */
    public static final class AngularVelocityMessage extends ReceiveMessage {
        public static final int CODE = 0x52;
        public final double[] degreesPerSecond;
        public final double voltage;

        AngularVelocityMessage(double[] degreesPerSecond, double voltage) {
            this.degreesPerSecond = degreesPerSecond;
            this.voltage = voltage;
        }

        static AngularVelocityMessage parse(byte[] body) {
            double[] w = new double[] {
                    scaled(i16(body, 0), 2000),
                    scaled(i16(body, 2), 2000),
                    scaled(i16(body, 4), 2000)
            };
            return new AngularVelocityMessage(w, i16(body, 6) / 100.0);
        }

        @Override
        public String toString() {
            return "angular velocity message - w:" + Arrays.toString(degreesPerSecond)
                    + " voltage:" + voltage;
        }
    }

    /** Output type {@code 0x53}. Angles are {@code raw/32768*180} degrees. */
    public static final class AngleMessage extends ReceiveMessage {
        public static final int CODE = 0x53;
        public final double roll;
        public final double pitch;
        public final double yaw;
        public final int version;

        AngleMessage(double roll, double pitch, double yaw, int version) {
            this.roll = roll;
            this.pitch = pitch;
            this.yaw = yaw;
            this.version = version;
        }

        static AngleMessage parse(byte[] body) {
            return new AngleMessage(
                    scaled(i16(body, 0), 180),
                    scaled(i16(body, 2), 180),
                    scaled(i16(body, 4), 180),
                    u16(body, 6));
        }

        @Override
        public String toString() {
            return String.format("angle message - roll:%1.1f pitch:%1.1f yaw:%1.1f version:%d",
                    roll, pitch, yaw, version);
        }
    }

    /** Output type {@code 0x54}. Magnetic samples are raw signed LSB. */
    public static final class MagneticMessage extends ReceiveMessage {
        public static final int CODE = 0x54;
        public final double[] field;
        public final double tempCelsius;

        MagneticMessage(double[] field, double tempCelsius) {
            this.field = field;
            this.tempCelsius = tempCelsius;
        }

        static MagneticMessage parse(byte[] body) {
            double[] mag = new double[] {i16(body, 0), i16(body, 2), i16(body, 4)};
            return new MagneticMessage(mag, i16(body, 6) / 100.0);
        }

        @Override
        public String toString() {
            return "magnetic message - vec:" + Arrays.toString(field) + " temp_celsius:" + tempCelsius;
        }
    }

    /** Output type {@code 0x59}. Each component is {@code raw/32768}. */
    public static final class QuaternionMessage extends ReceiveMessage {
        public static final int CODE = 0x59;
        public final double[] q;

        QuaternionMessage(double[] q) {
            this.q = q;
        }

        static QuaternionMessage parse(byte[] body) {
            return new QuaternionMessage(new double[] {
                    i16(body, 0) / 32768.0,
                    i16(body, 2) / 32768.0,
                    i16(body, 4) / 32768.0,
                    i16(body, 6) / 32768.0
            });
        }

        @Override
        public String toString() {
            return "quaternion message - q:" + Arrays.toString(q);
        }
    }

    /**
     * CALSW values for the HWT906. Magnetic calibration is spherical fitting
     * ({@code 0x07}), then {@link #NONE} to finish.
     */
    public enum CalibrationMode {
        NONE(0x00),
        GYRO_ACCEL(0x01),
        MAGNETIC(0x07);

        public final int code;

        CalibrationMode(int code) {
            this.code = code;
        }
    }

    /** ORIENT register. Vertical means the module Y arrow points up. */
    public enum InstallationDirection {
        HORIZONTAL(0x00),
        VERTICAL(0x01);

        public final int code;

        InstallationDirection(int code) {
            this.code = code;
        }
    }

    /** Registers this class writes. Addresses match the WitMotion standard protocol. */
    public enum Register {
        SAVE(0x00),
        CALSW(0x01),
        RSW(0x02),
        RATE(0x03),
        BAUD(0x04),
        AXOFFSET(0x05),
        AYOFFSET(0x06),
        AZOFFSET(0x07),
        GXOFFSET(0x08),
        GYOFFSET(0x09),
        GZOFFSET(0x0A),
        HXOFFSET(0x0B),
        HYOFFSET(0x0C),
        HZOFFSET(0x0D),
        SLEEP(0x22),
        DIRECTION(0x23),
        ALG(0x24),
        /** Unlock key. Data must be {@code 0xB588}. */
        KEY(0x69),
        /** Still-time before automatic gyro bias reset, in milliseconds. */
        GYROCALTIME(0x63);

        public final int address;

        Register(int address) {
            this.address = address;
        }
    }

    /** One {@code FF AA} register write. {@code data} is a 16-bit little-endian value. */
    public static final class ConfigCommand {
        public final Register register;
        public final int data;

        public ConfigCommand(Register register, int data) {
            if (register == null) {
                throw new IllegalArgumentException("register is null");
            }
            this.register = register;
            this.data = data;
        }

        public byte[] serialize() {
            return new byte[] {
                    (byte) 0xFF,
                    (byte) 0xAA,
                    (byte) register.address,
                    (byte) (data & 0xFF),
                    (byte) ((data >> 8) & 0xFF)
            };
        }

        @Override
        public String toString() {
            return "config command - register " + register + " -> data " + data;
        }
    }

    /**
     * Print a few samples from the discovered IMU. Optional argument is a port
     * name; otherwise {@link #discoverPort()} is used.
     */
    public static void main(String[] args) throws Exception {
        String port = args.length > 0 ? args[0] : discoverPort();
        try (WitMotionIMU imu = new WitMotionIMU(port)) {
            System.out.println("opened " + imu.getPortName() + " at " + imu.getBaudRate());
            long end = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < end) {
                System.out.println("angle=" + Arrays.toString(imu.getAngle())
                        + " accel=" + Arrays.toString(imu.getAcceleration())
                        + " gyro=" + Arrays.toString(imu.getAngularVelocity())
                        + " mag=" + Arrays.toString(imu.getMagneticVector())
                        + " q=" + Arrays.toString(imu.getQuaternion())
                        + " T=" + imu.getTemperatureCelsius());
                Thread.sleep(200);
            }
        }
    }
}

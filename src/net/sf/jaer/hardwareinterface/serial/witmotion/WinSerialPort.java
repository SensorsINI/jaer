package net.sf.jaer.hardwareinterface.serial.witmotion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows COM port opened through {@code kernel32}. nrjavaserial's JNA 4.2
 * native library does not load on JDK 25, so the HWT906 path cannot use it.
 */
public final class WinSerialPort implements AutoCloseable {

    private static final int GENERIC_READ = 0x80000000;
    private static final int GENERIC_WRITE = 0x40000000;
    private static final int OPEN_EXISTING = 3;
    private static final int DCB_SIZE = 28;
    private static final int SETDTR = 5;
    private static final int SETRTS = 3;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
    private static final MethodHandle CREATE_FILE_W = downcall("CreateFileW", FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS));
    private static final MethodHandle CLOSE_HANDLE = downcall("CloseHandle", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle GET_COMM_STATE = downcall("GetCommState", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SET_COMM_STATE = downcall("SetCommState", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SET_COMM_TIMEOUTS = downcall("SetCommTimeouts", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle ESCAPE_COMM = downcall("EscapeCommFunction", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle READ_FILE = downcall("ReadFile", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS));
    private static final MethodHandle WRITE_FILE = downcall("WriteFile", FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS));
    private static final MethodHandle CLEAR_COMM_ERROR = downcall("ClearCommError", FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle GET_LAST_ERROR = downcall("GetLastError", FunctionDescriptor.of(
            ValueLayout.JAVA_INT));

    private final String name;
    private final MemorySegment handle;
    private final InputStream input;
    private final OutputStream output;
    private volatile boolean closed;

    public WinSerialPort(String portName, int baudRate) throws IOException {
        this.name = portName;
        String path = "\\\\.\\" + portName;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wide = utf16(arena, path);
            MemorySegment opened = (MemorySegment) CREATE_FILE_W.invokeExact(
                    wide,
                    GENERIC_READ | GENERIC_WRITE,
                    0,
                    MemorySegment.NULL,
                    OPEN_EXISTING,
                    0,
                    MemorySegment.NULL);
            if (opened.address() == -1L || opened.address() == 0L) {
                throw new IOException("Could not open " + portName + " (Windows error " + lastError() + ")");
            }
            this.handle = opened;
            try {
                configure(arena, baudRate);
            } catch (IOException e) {
                closeHandle();
                throw e;
            } catch (Throwable e) {
                closeHandle();
                throw new IOException("Could not configure " + portName, e);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Could not open " + portName, e);
        }
        this.input = new ComInput();
        this.output = new ComOutput();
    }

    public String name() {
        return name;
    }

    public InputStream input() {
        return input;
    }

    public OutputStream output() {
        return output;
    }

    /**
     * COM ports named in {@code HKLM\HARDWARE\DEVICEMAP\SERIALCOMM}. This is the
     * map Windows updates when a USB CDC device such as a Teensy enumerates.
     * It does not load nrjavaserial.
     */
    public static List<String> listPortNames() {
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
            }, "WinSerial-reg");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            reader.join(1000);
            Matcher matcher = com.matcher(captured.toString(java.nio.charset.Charset.defaultCharset()));
            while (matcher.find()) {
                String found = matcher.group().toUpperCase(Locale.ROOT);
                if (!names.contains(found)) {
                    names.add(found);
                }
            }
        } catch (Exception ignored) {
            // empty list: the caller reports that no port was found
        }
        return names;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeHandle();
    }

    private void closeHandle() {
        try {
            int closedHandle = (int) CLOSE_HANDLE.invokeExact(handle);
            if (closedHandle == 0) {
                lastError();
            }
        } catch (Throwable ignored) {
            // port is going away
        }
    }

    private void configure(Arena arena, int baudRate) throws Throwable {
        MemorySegment dcb = arena.allocate(32);
        dcb.set(ValueLayout.JAVA_INT, 0, DCB_SIZE);
        if ((int) GET_COMM_STATE.invokeExact(handle, dcb) == 0) {
            throw new IOException("GetCommState failed (Windows error " + lastError() + ")");
        }
        dcb.set(ValueLayout.JAVA_INT, 0, DCB_SIZE);
        dcb.set(ValueLayout.JAVA_INT, 4, baudRate);
        // fBinary, DTR enable, RTS enable. No parity, one stop bit, 8 data bits.
        dcb.set(ValueLayout.JAVA_INT, 8, 1 | (1 << 4) | (1 << 12));
        dcb.set(ValueLayout.JAVA_BYTE, 18, (byte) 8);
        dcb.set(ValueLayout.JAVA_BYTE, 19, (byte) 0);
        dcb.set(ValueLayout.JAVA_BYTE, 20, (byte) 0);
        if ((int) SET_COMM_STATE.invokeExact(handle, dcb) == 0) {
            throw new IOException("SetCommState " + baudRate + " failed (Windows error " + lastError() + ")");
        }
        MemorySegment timeouts = arena.allocate(20);
        timeouts.set(ValueLayout.JAVA_INT, 0, 20);
        timeouts.set(ValueLayout.JAVA_INT, 4, 0);
        timeouts.set(ValueLayout.JAVA_INT, 8, 20);
        timeouts.set(ValueLayout.JAVA_INT, 12, 0);
        timeouts.set(ValueLayout.JAVA_INT, 16, 200);
        if ((int) SET_COMM_TIMEOUTS.invokeExact(handle, timeouts) == 0) {
            throw new IOException("SetCommTimeouts failed (Windows error " + lastError() + ")");
        }
        int dtr = (int) ESCAPE_COMM.invokeExact(handle, SETDTR);
        int rts = (int) ESCAPE_COMM.invokeExact(handle, SETRTS);
        if (dtr == 0 || rts == 0) {
            throw new IOException("EscapeCommFunction failed (Windows error " + lastError() + ")");
        }
    }

    private int queuedInputBytes() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errors = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment stat = arena.allocate(16);
            int ok = (int) CLEAR_COMM_ERROR.invokeExact(handle, errors, stat);
            if (ok == 0) {
                return 0;
            }
            return stat.get(ValueLayout.JAVA_INT, 4);
        } catch (Throwable e) {
            return 0;
        }
    }

    private int read(byte[] dst, int off, int len) throws IOException {
        if (closed) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(len);
            MemorySegment nread = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) READ_FILE.invokeExact(handle, buf, len, nread, MemorySegment.NULL);
            if (ok == 0) {
                throw new IOException("ReadFile failed (Windows error " + lastError() + ")");
            }
            int n = nread.get(ValueLayout.JAVA_INT, 0);
            if (n > 0) {
                MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 0, dst, off, n);
            }
            return n;
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("ReadFile failed", e);
        }
    }

    private void write(byte[] src, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("port is closed");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(len);
            MemorySegment.copy(src, off, buf, ValueLayout.JAVA_BYTE, 0, len);
            MemorySegment nwritten = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) WRITE_FILE.invokeExact(handle, buf, len, nwritten, MemorySegment.NULL);
            if (ok == 0) {
                throw new IOException("WriteFile failed (Windows error " + lastError() + ")");
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("WriteFile failed", e);
        }
    }

    private static int lastError() {
        try {
            return (int) GET_LAST_ERROR.invokeExact();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static MemorySegment utf16(Arena arena, String s) {
        byte[] bytes = (s + "\u0000").getBytes(StandardCharsets.UTF_16LE);
        MemorySegment seg = arena.allocate(bytes.length);
        MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0, bytes.length);
        return seg;
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment address = KERNEL32.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name));
        return LINKER.downcallHandle(address, descriptor);
    }

    private final class ComInput extends InputStream {
        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return WinSerialPort.this.read(b, off, len);
        }

        @Override
        public int available() {
            return queuedInputBytes();
        }
    }

    private final class ComOutput extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            WinSerialPort.this.write(b, off, len);
        }
    }
}

package net.sf.jaer.eventio.ddd;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.swing.ProgressMonitor;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.dsec.BloscHdf5Filter;

/**
 * Converts a DDD17/DDD20 cAER HDF5 file to AEDAT-4 (polarity, APS frames, IMU).
 * OpenXC / CAN groups are not written.
 *
 * @see <a href="https://github.com/SensorsINI/ddd20-utils">ddd20-utils</a>
 */
public final class DddHdf5ToAedat4 {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private static final int CHUNK = 64;
    private static final int HEADER_BYTES = 28;
    private static final int FRAME_INFO_BYTES = 36;
    private static final int ET_POLARITY = 1;
    private static final int ET_FRAME = 2;
    private static final int ET_IMU6 = 3;

    private DddHdf5ToAedat4() {
    }

    public static File convert(File source, File dest, AEChip chip, ProgressMonitor progress)
            throws IOException, InterruptedException {
        if (source == null || !source.isFile()) {
            throw new IOException("not a file: " + source);
        }
        if (dest == null) {
            dest = DddHdf5.aedat4Sibling(source);
        }
        BloscHdf5Filter.ensureRegistered();
        File partial = dest;
        try (HdfFile h = new HdfFile(source.toPath())) {
            Dataset dsData = (Dataset) h.getByPath("/dvs/data");
            Dataset dsTs = (Dataset) h.getByPath("/dvs/timestamp");
            if (dsData == null || dsTs == null) {
                throw new IOException("DDD HDF5 missing /dvs/data or /dvs/timestamp");
            }
            long nRows = dsTs.getDimensions()[0];
            if (nRows <= 0) {
                throw new IOException("DDD HDF5 /dvs has no packets");
            }
            long firstHostUs = firstNonzeroTimestamp(dsTs, nRows);
            if (firstHostUs <= 0) {
                firstHostUs = System.currentTimeMillis() * 1000L;
            }
            EventPacket<PolarityEvent> evPacket = new EventPacket<>(PolarityEvent.class);
            FramePacket framePacket = new FramePacket();
            ImuPacket imuPacket = new ImuPacket(32);
            PacketBundle bundle = new PacketBundle();
            long firstDeviceUs = Long.MIN_VALUE;
            long events = 0;
            long frames = 0;
            long imuSamples = 0;
            long skipped = 0;
            try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(
                    dest, chip, CompressionType.LZ4, firstHostUs)) {
                for (long row0 = 0; row0 < nRows; row0 += CHUNK) {
                    throwIfCanceled(progress);
                    int len = (int) Math.min(CHUNK, nRows - row0);
                    Object chunk = readDataChunk(dsData, row0, len);
                    long[] hostTs = readLongSlice(dsTs, row0, len);
                    for (int i = 0; i < len; i++) {
                        Object row = rowAt(chunk, i, len);
                        byte[] header = headerBytes(row);
                        byte[] body = bodyBytes(row);
                        if (header == null || header.length < HEADER_BYTES || body == null || body.length == 0) {
                            skipped++;
                            continue;
                        }
                        CaerHeader hd = unpackHeader(header);
                        if (hd.etype < 0 || hd.esize <= 0 || hd.ecapacity <= 0) {
                            skipped++;
                            continue;
                        }
                        int nEv = Math.min(hd.evalid > 0 ? hd.evalid : hd.ecapacity, body.length / hd.esize);
                        if (nEv <= 0) {
                            skipped++;
                            continue;
                        }
                        if (hd.etype == ET_POLARITY) {
                            OutputEventIterator<PolarityEvent> oi = evPacket.outputIterator();
                            int wrote = unpackPolarity(body, hd, nEv, oi, firstDeviceUs);
                            if (wrote > 0) {
                                if (firstDeviceUs == Long.MIN_VALUE) {
                                    firstDeviceUs = firstPolarityDeviceUs(body, hd);
                                    relabelPolarityTimestamps(evPacket, firstDeviceUs);
                                }
                                bundle.clear();
                                bundle.add(evPacket);
                                out.writeBundle(bundle);
                                events += wrote;
                            }
                        } else if (hd.etype == ET_FRAME) {
                            if (unpackFrame(body, hd, framePacket, firstDeviceUs, hostTs[i], firstHostUs)) {
                                bundle.clear();
                                bundle.add(framePacket);
                                out.writeBundle(bundle);
                                frames++;
                            } else {
                                skipped++;
                            }
                        } else if (hd.etype == ET_IMU6) {
                            imuPacket.clear();
                            int n = unpackImu6(body, hd, nEv, imuPacket, firstDeviceUs, firstHostUs, hostTs[i]);
                            if (n > 0) {
                                if (imuSamples == 0) {
                                    log.info("DDD HDF5 first IMU " + imuPacket.get(0));
                                }
                                bundle.clear();
                                bundle.add(imuPacket);
                                out.writeBundle(bundle);
                                imuSamples += n;
                            }
                        } else {
                            skipped++;
                        }
                    }
                    if (progress != null) {
                        int pct = (int) Math.min(99, (100L * (row0 + len)) / nRows);
                        progress.setProgress(pct);
                        progress.setNote(String.format("DDD → AEDAT-4: %,d / %,d packets, %,d events, %,d frames",
                                row0 + len, nRows, events, frames));
                    }
                }
            }
            log.info(String.format(
                    "DDD HDF5 convert %s -> %s: %,d polarity events, %,d frames, %,d IMU, skipped %,d packets",
                    source.getName(), dest.getName(), events, frames, imuSamples, skipped));
            if (progress != null) {
                progress.setProgress(100);
            }
            return dest;
        } catch (InterruptedException ie) {
            deleteQuietly(partial);
            throw ie;
        } catch (IOException e) {
            deleteQuietly(partial);
            throw e;
        } catch (RuntimeException e) {
            deleteQuietly(partial);
            throw new IOException("DDD HDF5 convert failed: " + e.getMessage(), e);
        }
    }

    private static void relabelPolarityTimestamps(EventPacket<PolarityEvent> packet, long firstDeviceUs) {
        int n = packet.getSize();
        for (int i = 0; i < n; i++) {
            PolarityEvent e = packet.getEvent(i);
            long dev = e.timestamp & 0xffffffffL;
            e.timestamp = (int) (dev - (firstDeviceUs & 0xffffffffL));
        }
    }

    private static long firstPolarityDeviceUs(byte[] body, CaerHeader hd) {
        ByteBuffer bb = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        if (hd.esize < 8 || body.length < 8) {
            return 0;
        }
        bb.position(4);
        return bb.getInt() & 0xffffffffL;
    }

    private static int unpackPolarity(byte[] body, CaerHeader hd, int nEv,
            OutputEventIterator<PolarityEvent> oi, long firstDeviceUs) {
        ByteBuffer bb = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        int wrote = 0;
        int stride = hd.esize;
        int sx1 = DddHdf5.WIDTH - 1;
        int sy1 = DddHdf5.HEIGHT - 1;
        for (int i = 0; i < nEv; i++) {
            int pos = i * stride;
            if (pos + 8 > body.length) {
                break;
            }
            bb.position(pos);
            int data = bb.getInt();
            int ts32 = bb.getInt();
            if ((data & 1) == 0) {
                continue;
            }
            int pol = (data >>> 1) & 1;
            int y = (data >>> 2) & 0x7fff;
            int x = (data >>> 17) & 0x7fff;
            if (x > sx1 || y > sy1) {
                continue;
            }
            PolarityEvent e = oi.nextOutput();
            e.x = (short) x;
            // cAER/OpenCV y=0 is top; jAER-written AEDAT-4 stores bottom-origin Y.
            e.y = (short) (sy1 - y);
            e.polarity = pol != 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off;
            long rel;
            if (firstDeviceUs == Long.MIN_VALUE) {
                rel = ts32 & 0xffffffffL;
            } else {
                rel = (ts32 & 0xffffffffL) - (firstDeviceUs & 0xffffffffL);
            }
            e.timestamp = (int) rel;
            wrote++;
        }
        return wrote;
    }

    private static boolean unpackFrame(byte[] body, CaerHeader hd, FramePacket dest,
            long firstDeviceUs, long hostUs, long firstHostUs) {
        if (body.length < FRAME_INFO_BYTES) {
            return false;
        }
        ByteBuffer bb = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        int ts = bb.getInt(8);
        int need = DddHdf5.WIDTH * DddHdf5.HEIGHT;
        int pixBytes = body.length - FRAME_INFO_BYTES;
        if (pixBytes < need * 2) {
            return false;
        }
        dest.allocate(DddHdf5.WIDTH, DddHdf5.HEIGHT, FramePacket.ColorMode.GRAYSCALE);
        short[] pixels = dest.getPixels();
        bb.position(FRAME_INFO_BYTES);
        int sy1 = DddHdf5.HEIGHT - 1;
        // Store bottom-origin rows so jAER display matches the APS image.
        for (int y = 0; y < DddHdf5.HEIGHT; y++) {
            int dstY = sy1 - y;
            int dstOff = dstY * DddHdf5.WIDTH;
            for (int x = 0; x < DddHdf5.WIDTH; x++) {
                pixels[dstOff + x] = bb.getShort();
            }
        }
        int rel = relativeUs(ts, firstDeviceUs, hostUs, firstHostUs);
        dest.setTimestampStartUs(rel);
        dest.setTimestampEndUs(rel);
        dest.setExposureUs(0);
        return true;
    }

    /**
     * cAER IMU6 body is {@code info, ts, ax,ay,az, gx,gy,gz, temp} (floats in g
     * and °/s). {@link IMUSample} raw slots are ax,ay,az,<em>temp</em>,gx,gy,gz
     * — do not copy the 7 floats into {@code short[7]} in cAER order.
     */
    static int unpackImu6(byte[] body, CaerHeader hd, int nEv, ImuPacket dest,
            long firstDeviceUs, long firstHostUs, long hostUs) {
        if (hd.esize < 36) {
            return 0;
        }
        ByteBuffer bb = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        int n = 0;
        for (int i = 0; i < nEv; i++) {
            int pos = i * hd.esize;
            if (pos + 36 > body.length) {
                break;
            }
            bb.position(pos);
            bb.getInt();
            int ts = bb.getInt();
            float ax = bb.getFloat();
            float ay = bb.getFloat();
            float az = bb.getFloat();
            float gx = bb.getFloat();
            float gy = bb.getFloat();
            float gz = bb.getFloat();
            float temp = bb.getFloat();
            dest.nextOutput().setFromPhysicalUnits(
                    relativeUs(ts, firstDeviceUs, hostUs, firstHostUs),
                    ax, ay, az, gx, gy, gz, temp);
            n++;
        }
        return n;
    }

    /** One cAER IMU6 event (36-byte body) → {@link IMUSample}. Used by {@link DddHdf5DetectDemo}. */
    static IMUSample decodeOneImu6Event(byte[] body36) {
        CaerHeader hd = new CaerHeader();
        hd.esize = 36;
        ImuPacket packet = new ImuPacket(1);
        if (unpackImu6(body36, hd, 1, packet, 0L, 0L, 0L) != 1) {
            throw new IllegalStateException("IMU6 unpack failed");
        }
        return packet.get(0);
    }

    private static int relativeUs(int deviceTs32, long firstDeviceUs, long hostUs, long firstHostUs) {
        if (firstDeviceUs != Long.MIN_VALUE) {
            return (int) ((deviceTs32 & 0xffffffffL) - (firstDeviceUs & 0xffffffffL));
        }
        return (int) Math.max(0L, hostUs - firstHostUs);
    }

    private static CaerHeader unpackHeader(byte[] header) {
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        CaerHeader h = new CaerHeader();
        h.etype = bb.getShort();
        h.esource = bb.getShort();
        h.esize = bb.getInt();
        h.eoffset = bb.getInt();
        h.eoverflow = bb.getInt();
        h.ecapacity = bb.getInt();
        h.enumber = bb.getInt();
        h.evalid = bb.getInt();
        return h;
    }

    private static final class CaerHeader {
        short etype;
        short esource;
        int esize;
        int eoffset;
        int eoverflow;
        int ecapacity;
        int enumber;
        int evalid;
    }

    private static Object readDataChunk(Dataset ds, long offset, int len) throws IOException {
        try {
            int[] dims = ds.getDimensions();
            if (dims.length >= 2) {
                return ds.getData(new long[]{offset, 0}, new int[]{len, dims[1]});
            }
            return ds.getData(new long[]{offset}, new int[]{len});
        } catch (RuntimeException e) {
            throw new IOException("reading /dvs/data @" + offset + ": " + e.getMessage(), e);
        }
    }

    private static Object rowAt(Object chunk, int i, int len) {
        if (chunk == null) {
            return null;
        }
        if (chunk instanceof Object[][]) {
            Object[][] a = (Object[][]) chunk;
            return i < a.length ? a[i] : null;
        }
        if (chunk.getClass().isArray()) {
            int n = Array.getLength(chunk);
            if (n == len) {
                return Array.get(chunk, i);
            }
            // 2D flattened as [len][3]
            if (n > 0 && Array.get(chunk, 0) != null && Array.get(chunk, 0).getClass().isArray()) {
                return Array.get(chunk, i);
            }
        }
        return chunk;
    }

    private static byte[] headerBytes(Object row) {
        return fieldBytes(row, 1);
    }

    private static byte[] bodyBytes(Object row) {
        return fieldBytes(row, 2);
    }

    private static byte[] fieldBytes(Object row, int index) {
        if (row == null) {
            return null;
        }
        if (row.getClass().isArray() && Array.getLength(row) > index) {
            return asBytes(Array.get(row, index));
        }
        return asBytes(row);
    }

    private static byte[] asBytes(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof byte[]) {
            return (byte[]) o;
        }
        if (o instanceof int[]) {
            int[] a = (int[]) o;
            byte[] b = new byte[a.length];
            for (int i = 0; i < a.length; i++) {
                b[i] = (byte) a[i];
            }
            return b;
        }
        if (o instanceof short[]) {
            short[] a = (short[]) o;
            byte[] b = new byte[a.length];
            for (int i = 0; i < a.length; i++) {
                b[i] = (byte) a[i];
            }
            return b;
        }
        if (o instanceof ByteBuffer) {
            ByteBuffer bb = ((ByteBuffer) o).duplicate();
            byte[] b = new byte[bb.remaining()];
            bb.get(b);
            return b;
        }
        if (o instanceof String) {
            return ((String) o).getBytes(StandardCharsets.US_ASCII);
        }
        if (o.getClass().isArray() && o.getClass().getComponentType() == Byte.TYPE) {
            return (byte[]) o;
        }
        return null;
    }

    private static long[] readLongSlice(Dataset ds, long offset, int len) throws IOException {
        try {
            Object data = ds.getData(new long[]{offset}, new int[]{len});
            return toLongs(data, len);
        } catch (RuntimeException e) {
            throw new IOException("reading /dvs/timestamp @" + offset + ": " + e.getMessage(), e);
        }
    }

    private static long firstNonzeroTimestamp(Dataset ds, long nRows) throws IOException {
        int probe = (int) Math.min(1024, nRows);
        long[] a = readLongSlice(ds, 0, probe);
        for (long v : a) {
            if (v != 0) {
                return v;
            }
        }
        return 0;
    }

    private static long[] toLongs(Object data, int len) {
        long[] out = new long[len];
        if (data instanceof long[]) {
            long[] a = (long[]) data;
            System.arraycopy(a, 0, out, 0, Math.min(len, a.length));
            return out;
        }
        if (data instanceof int[]) {
            int[] a = (int[]) data;
            int n = Math.min(len, a.length);
            for (int i = 0; i < n; i++) {
                out[i] = a[i] & 0xffffffffL;
            }
            return out;
        }
        if (data != null && data.getClass().isArray()) {
            int n = Math.min(len, Array.getLength(data));
            for (int i = 0; i < n; i++) {
                Object el = Array.get(data, i);
                if (el instanceof Number) {
                    out[i] = ((Number) el).longValue();
                }
            }
        }
        return out;
    }

    private static void throwIfCanceled(ProgressMonitor progress) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("DDD HDF5 convert canceled");
        }
        if (progress != null && progress.isCanceled()) {
            throw new InterruptedException("DDD HDF5 convert canceled");
        }
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.isFile() && !f.delete()) {
            log.warning("Left partial DDD convert file: " + f);
        }
    }
}

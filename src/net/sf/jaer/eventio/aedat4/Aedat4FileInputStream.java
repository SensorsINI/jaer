package net.sf.jaer.eventio.aedat4;

import eu.seebetter.ini.chips.DavisChip;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import javax.swing.ProgressMonitor;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.Event;
import net.sf.jaer.eventio.aedat4.dv.EventPacket;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;

/** Pragmatic AEDAT-4 reader: indexes polarity events for raw jAER playback. */
public class Aedat4FileInputStream implements AEFileInputStreamInterface {

    private final AEChip chip;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final TreeSet<Long> markers = new TreeSet<>();
    private File file;
    private int[] addresses = new int[0];
    private int[] timestamps = new int[0];
    private long baseUnixUs;
    private long position;
    private long markIn;
    private long markOut = Long.MAX_VALUE;
    private boolean repeat;
    private boolean nonMonotonicTimeExceptionsChecked = true;
    private int currentStartTimestamp;
    private int timestampResetBitmask;

    public Aedat4FileInputStream(File file, AEChip chip) throws IOException {
        this(file, chip, null);
    }

    public Aedat4FileInputStream(File file, AEChip chip, ProgressMonitor progressMonitor) throws IOException {
        this.file = file;
        this.chip = chip;
        indexFile(progressMonitor);
        clearMarks();
        support.firePropertyChange(AEInputStream.EVENT_INIT, null, this);
    }

    private void indexFile(ProgressMonitor progressMonitor) throws IOException {
        ArrayList<Integer> addressList = new ArrayList<>();
        ArrayList<Long> unixTimestampList = new ArrayList<>();
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(channel, version);
            if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
                throw new IOException(file + " is not an AEDAT-4 file");
            }

            ByteBuffer headerBytes = readSizePrefixed(channel);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            if (header.compression() != CompressionType.NONE) {
                throw new IOException("AEDAT-4 compression is not supported yet: " + header.compression());
            }
            long dataTablePosition = header.dataTablePosition();
            long fileSize = channel.size();
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (channel.position() + 8 <= fileSize) {
                if (dataTablePosition >= 0 && channel.position() >= dataTablePosition) {
                    break;
                }
                long packetOffset = channel.position();
                packetHeader.clear();
                readFully(channel, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int payloadSize = packetHeader.getInt();
                long remaining = fileSize - channel.position();
                if (payloadSize < 0 || payloadSize > remaining) {
                    break;
                }
                ByteBuffer payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN);
                readFully(channel, payload);
                payload.flip();
                if (streamId == Aedat4FileOutputStream.STREAM_EVENTS) {
                    decodeEventPacket(payload, addressList, unixTimestampList);
                }
                if (progressMonitor != null && fileSize > 0) {
                    progressMonitor.setProgress((int) Math.min(100, (packetOffset * 100) / fileSize));
                    if (progressMonitor.isCanceled()) {
                        throw new IOException("AEDAT-4 indexing canceled");
                    }
                }
            }
        }
        if (!unixTimestampList.isEmpty()) {
            baseUnixUs = unixTimestampList.get(0);
        }
        addresses = new int[addressList.size()];
        timestamps = new int[unixTimestampList.size()];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] = addressList.get(i);
            timestamps[i] = (int) (unixTimestampList.get(i) - baseUnixUs);
        }
        markOut = Math.max(0, addresses.length);
    }

    private void decodeEventPacket(ByteBuffer payload, ArrayList<Integer> addressList, ArrayList<Long> unixTimestampList) {
        EventPacket packet = EventPacket.getSizePrefixedRootAsEventPacket(payload);
        int sx1 = chip == null ? 0 : chip.getSizeX() - 1;
        for (int i = 0; i < packet.elementsLength(); i++) {
            Event event = packet.elements(i);
            int address = DavisChip.ADDRESS_TYPE_DVS
                    | ((sx1 - event.x()) << DavisChip.XSHIFT)
                    | (event.y() << DavisChip.YSHIFT)
                    | ((event.polarity() ? 1 : 0) << DavisChip.POLSHIFT);
            addressList.add(address);
            unixTimestampList.add(event.timestamp());
        }
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        if (size < 0) {
            throw new IOException("Negative FlatBuffer size prefix " + size);
        }
        ByteBuffer payload = ByteBuffer.allocate(size + 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(size);
        readFully(channel, payload);
        payload.flip();
        return payload;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException();
            }
        }
    }

    @Override
    public synchronized AEPacketRaw readPacketByNumber(int n) throws IOException {
        if (position >= effectiveMarkOut()) {
            if (repeat) {
                rewind();
            } else {
                throw new EOFException();
            }
        }
        int start = (int) position;
        int end = (int) Math.min(effectiveMarkOut(), position + Math.max(1, n));
        position = end;
        currentStartTimestamp = start < timestamps.length ? timestamps[start] : 0;
        firePosition();
        return new AEPacketRaw(Arrays.copyOfRange(addresses, start, end), Arrays.copyOfRange(timestamps, start, end));
    }

    @Override
    public synchronized AEPacketRaw readPacketByTime(int dt) throws IOException {
        if (position >= effectiveMarkOut()) {
            if (repeat) {
                rewind();
            } else {
                throw new EOFException();
            }
        }
        int start = (int) position;
        int target = timestamps[start] + dt;
        int end = start + 1;
        while (end < effectiveMarkOut() && timestamps[end - 1] <= target) {
            end++;
        }
        position = end;
        currentStartTimestamp = timestamps[start];
        firePosition();
        return new AEPacketRaw(Arrays.copyOfRange(addresses, start, end), Arrays.copyOfRange(timestamps, start, end));
    }

    private long effectiveMarkOut() {
        return Math.min(markOut, addresses.length);
    }

    private void firePosition() {
        support.firePropertyChange(AEInputStream.EVENT_POSITION, null, position);
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() { return nonMonotonicTimeExceptionsChecked; }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) { nonMonotonicTimeExceptionsChecked = yes; }

    @Override
    public long getAbsoluteStartingTimeMs() { return baseUnixUs / 1000L; }

    @Override
    public ZoneId getZoneId() { return ZoneId.systemDefault(); }

    @Override
    public int getDurationUs() { return getLastTimestamp() - getFirstTimestamp(); }

    @Override
    public int getFirstTimestamp() { return timestamps.length == 0 ? 0 : timestamps[0]; }

    @Override
    public PropertyChangeSupport getSupport() { return support; }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) { support.addPropertyChangeListener(listener); }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) { support.removePropertyChangeListener(listener); }

    @Override
    public File getFile() { return file; }

    @Override
    public int getLastTimestamp() { return timestamps.length == 0 ? 0 : timestamps[timestamps.length - 1]; }

    @Override
    public int getMostRecentTimestamp() {
        int index = (int) Math.max(0, Math.min(position - 1, timestamps.length - 1));
        return timestamps.length == 0 ? 0 : timestamps[index];
    }

    @Override
    public void setFile(File file) { this.file = file; }

    @Override
    public int getTimestampResetBitmask() { return timestampResetBitmask; }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) { this.timestampResetBitmask = timestampResetBitmask; }

    @Override
    public void close() throws IOException { }

    @Override
    public int getCurrentStartTimestamp() { return currentStartTimestamp; }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) { this.currentStartTimestamp = currentStartTimestamp; }

    @Override
    public boolean toggleMarker() {
        Long here = position;
        boolean added = markers.add(here);
        if (!added) {
            markers.remove(here);
        }
        support.firePropertyChange(AEInputStream.EVENT_MARK_TOGGLED, added ? null : here, added ? here : null);
        return added;
    }

    @Override
    public boolean jumpToNextMarker() {
        Long next = markers.higher(position);
        if (next == null) {
            return false;
        }
        position(next);
        return true;
    }

    @Override
    public boolean jumpToPrevMarker() {
        Long prev = markers.lower(position);
        if (prev == null) {
            return false;
        }
        position(prev);
        return true;
    }

    @Override
    public float getFractionalPosition() { return addresses.length == 0 ? 0 : (float) position / addresses.length; }

    @Override
    public long position() { return position; }

    @Override
    public void position(long n) {
        long old = position;
        position = Math.max(markIn, Math.min(n, effectiveMarkOut()));
        support.firePropertyChange(AEInputStream.EVENT_REPOSITIONED, old, position);
    }

    @Override
    public void rewind() throws IOException {
        long old = position;
        position = markIn;
        support.firePropertyChange(AEInputStream.EVENT_REWOUND, old, position);
    }

    @Override
    public void setFractionalPosition(float frac) { position((long) (Math.max(0, Math.min(1, frac)) * addresses.length)); }

    @Override
    public long size() { return addresses.length; }

    @Override
    public void clearMarks() {
        long[] oldMarks = new long[]{markIn, markOut};
        markIn = 0;
        markOut = addresses.length;
        markers.clear();
        support.firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, oldMarks, new long[]{markIn, markOut});
    }

    @Override
    public long setMarkIn() {
        if (position <= markOut) {
            long old = markIn;
            markIn = position;
            support.firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, old, markIn);
        }
        return markIn;
    }

    @Override
    public long setMarkOut() {
        if (position > markIn) {
            long old = markOut;
            markOut = position;
            support.firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, old, markOut);
        }
        return markOut;
    }

    @Override
    public long getMarkInPosition() { return markIn; }

    @Override
    public long getMarkOutPosition() { return markOut; }

    @Override
    public boolean isMarkInSet() { return markIn != 0; }

    @Override
    public boolean isMarkOutSet() { return markOut != addresses.length; }

    @Override
    public void setRepeat(boolean repeat) { this.repeat = repeat; }

    @Override
    public boolean isRepeat() { return repeat; }
}

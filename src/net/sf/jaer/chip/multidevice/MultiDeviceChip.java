package net.sf.jaer.chip.multidevice;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.eventio.aedat4.Aedat4CameraTrack;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.MultiDeviceRenderer;

/**
 * One {@link AEViewer} and one filter chain over several device slots.
 * A rig subclass calls {@link #addSlot} and chooses a {@link DeviceRelation}.
 * Geometry numbers live in {@link ViewingPose} prefs, not in Java literals.
 * <p>
 * Tiled vision keeps native {@code x,y} on the event. {@link BasicEvent#source}
 * is the slot. The canvas tiles those cameras for display only.
 */
@Description("Several sensors in one viewer and one filter chain")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public abstract class MultiDeviceChip extends AEChip {

    private final List<DeviceSlot> slots = new ArrayList<>();
    private DeviceRelation relation = new TiledNativeRelation();
    private ViewingPose pose = new ViewingPose();
    private JMenu devicesMenu;
    private JMenuItem orderItem;
    private DeviceRelationDialog relationDialog;
    private final List<EventPacket<PolarityEvent>> recNative = new ArrayList<>();

    protected MultiDeviceChip() {
        setName(getClass().getSimpleName());
        setEventClass(PolarityEvent.class);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setRenderer(new MultiDeviceRenderer(this));
    }

    /**
     * Add a slot and construct its child chip. Source bytes are assigned in
     * add order, starting at 0.
     */
    protected DeviceSlot addSlot(String id, Class<? extends AEChip> chipClass, Modality modality) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("slot id is empty");
        }
        if (chipClass == null) {
            throw new IllegalArgumentException("slot " + id + " has no chip class");
        }
        if (slots.size() > MultiDeviceAddress.MAX_SOURCE) {
            throw new IllegalArgumentException("at most " + (MultiDeviceAddress.MAX_SOURCE + 1) + " slots");
        }
        for (DeviceSlot existing : slots) {
            if (id.equals(existing.getId())) {
                throw new IllegalArgumentException("duplicate slot id " + id);
            }
        }
        AEChip child;
        try {
            child = chipClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot construct " + chipClass.getName() + " for slot " + id, e);
        }
        child.setName(id);
        DeviceSlot slot = new DeviceSlot(id, chipClass, child, modality, (byte) slots.size());
        slots.add(slot);
        pose.load(getPrefs(), slots);
        if (relation != null) {
            relation.applyCanvas(this);
        }
        return slot;
    }

    public List<DeviceSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    public DeviceRelation getRelation() {
        return relation;
    }

    public void setRelation(DeviceRelation relation) {
        this.relation = relation == null ? new TiledNativeRelation() : relation;
        this.relation.applyCanvas(this);
    }

    public ViewingPose getPose() {
        return pose;
    }

    public void setPose(ViewingPose pose) {
        this.pose = pose == null ? new ViewingPose() : pose;
        this.pose.load(getPrefs(), slots);
    }

    /** Slot whose {@link DeviceSlot#getSource()} matches {@code event.source}, or null. */
    public DeviceSlot slot(BasicEvent event) {
        if (event == null) {
            return null;
        }
        return slotBySource(event.source);
    }

    public DeviceSlot slotBySource(byte source) {
        for (DeviceSlot slot : slots) {
            if (slot.getSource() == source) {
                return slot;
            }
        }
        return null;
    }

    public List<DeviceSlot> visionSlots() {
        List<DeviceSlot> vision = new ArrayList<>();
        for (DeviceSlot slot : slots) {
            if (slot.getModality() == Modality.VISION) {
                vision.add(slot);
            }
        }
        return vision;
    }

    public int visionSlotCount() {
        int n = 0;
        for (DeviceSlot slot : slots) {
            if (slot.getModality() == Modality.VISION) {
                n++;
            }
        }
        return n;
    }

    /**
     * Exchange two vision slots in list order. Source bytes stay on the slot,
     * so events follow the camera and the tile moves with it.
     */
    public void swapVisionSlots(int visionIndexA, int visionIndexB) {
        List<Integer> index = visionIndexes();
        if (visionIndexA < 0 || visionIndexB < 0
                || visionIndexA >= index.size() || visionIndexB >= index.size()
                || visionIndexA == visionIndexB) {
            return;
        }
        Collections.swap(slots, index.get(visionIndexA), index.get(visionIndexB));
        if (relation != null) {
            relation.applyCanvas(this);
        }
        refreshDevicesMenu();
    }

    /**
     * Bind EVTS streams onto vision slots. Explicit {@link DeviceSlot#getSourceName()}
     * matches first, then the slot id when it is contained in the source string,
     * then remaining streams in order.
     *
     * @return number of slots that received a stream
     */
    public int bindPlaybackStreams(List<RecordingChipDetector.StreamHint> streams) {
        for (DeviceSlot slot : slots) {
            slot.setBoundStreamId(-1);
        }
        List<RecordingChipDetector.StreamHint> evts = new ArrayList<>();
        if (streams != null) {
            for (RecordingChipDetector.StreamHint hint : streams) {
                if (hint != null && hint.isEvents()) {
                    evts.add(hint);
                }
            }
        }
        boolean[] used = new boolean[evts.size()];
        bindByName(evts, used, true);
        bindByName(evts, used, false);
        for (DeviceSlot slot : slots) {
            if (slot.getBoundStreamId() >= 0 || slot.getModality() != Modality.VISION) {
                continue;
            }
            int idx = firstUnused(used);
            if (idx >= 0) {
                used[idx] = true;
                slot.setBoundStreamId(evts.get(idx).streamId);
            }
        }
        return boundStreamCount();
    }

    public int boundStreamCount() {
        int n = 0;
        for (DeviceSlot slot : slots) {
            if (slot.getBoundStreamId() >= 0) {
                n++;
            }
        }
        return n;
    }

    public boolean isBoundStream(int streamId) {
        return sourceForStream(streamId) >= 0;
    }

    /** Slot source for a bound AEDAT-4 stream, or {@code -1}. */
    public int sourceForStream(int streamId) {
        for (DeviceSlot slot : slots) {
            if (slot.getBoundStreamId() == streamId) {
                return slot.getSource() & 0xff;
            }
        }
        return -1;
    }

    public int firstBoundStreamId() {
        for (DeviceSlot slot : slots) {
            if (slot.getBoundStreamId() >= 0) {
                return slot.getBoundStreamId();
            }
        }
        return -1;
    }

    /**
     * One AEDAT-4 camera track per vision slot, in current slot order.
     * Child chip size is the native sensor, not the tiled canvas.
     */
    public List<Aedat4CameraTrack> aedat4RecordingTracks(RecordingConfigurationSnapshot snapshot) {
        List<DeviceSlot> vision = visionSlots();
        List<Aedat4CameraTrack> tracks = new ArrayList<>(vision.size());
        for (int i = 0; i < vision.size(); i++) {
            DeviceSlot slot = vision.get(i);
            String source = slot.getSourceName();
            if (source == null || source.isEmpty()) {
                source = slot.getId();
            }
            tracks.add(new Aedat4CameraTrack(slot.getChild(), source, snapshot, i));
        }
        return tracks;
    }

    /**
     * Split a filtered polarity packet into native per-slot packets.
     * The returned list is reused on the next call.
     */
    public List<EventPacket<PolarityEvent>> nativePolarityBySlot(EventPacket<?> polarity, boolean skipFilteredOut) {
        List<DeviceSlot> vision = visionSlots();
        while (recNative.size() < vision.size()) {
            recNative.add(new EventPacket<>(PolarityEvent.class));
        }
        for (int i = 0; i < vision.size(); i++) {
            recNative.get(i).clear();
        }
        if (polarity != null) {
            int n = polarity.getSize();
            for (int i = 0; i < n; i++) {
                BasicEvent event = polarity.getEvent(i);
                if (event == null || (skipFilteredOut && event.isFilteredOut())) {
                    continue;
                }
                DeviceSlot slot = slot(event);
                int track = visionIndex(slot);
                if (track < 0) {
                    continue;
                }
                PolarityEvent dest = recNative.get(track).outputIterator().nextOutput();
                dest.copyFrom(event);
                if (relation != null) {
                    relation.unplace(slot, event, dest);
                }
            }
        }
        return recNative.subList(0, vision.size());
    }

    /**
     * Write native {@code x,y} and file timestamps onto one track per vision slot.
     */
    public void recordNativeAedat4(Aedat4FileOutputStream out, PacketBundle bundle, boolean skipFilteredOut)
            throws java.io.IOException {
        if (out == null || bundle == null) {
            return;
        }
        List<EventPacket<PolarityEvent>> natives = nativePolarityBySlot(
                bundle.getFirstPolarityPacket(), skipFilteredOut);
        for (int i = 0; i < natives.size(); i++) {
            EventPacket<PolarityEvent> packet = natives.get(i);
            if (packet == null || packet.isEmpty()) {
                continue;
            }
            PacketBundle one = new PacketBundle();
            one.add(packet);
            out.writeBundle(one, false, i);
        }
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        installDevicesMenu();
    }

    private void installDevicesMenu() {
        AEViewer viewer = getAeViewer();
        if (viewer == null) {
            return;
        }
        JMenuBar bar = viewer.getJMenuBar();
        if (bar == null) {
            return;
        }
        if (devicesMenu != null && devicesMenu.getParent() == bar) {
            refreshDevicesMenu();
            return;
        }
        devicesMenu = new JMenu("Devices");
        devicesMenu.setToolTipText("Slots, viewing direction, and per-slot timestamp offset");
        orderItem = new JMenuItem(orderLabel());
        orderItem.setEnabled(false);
        devicesMenu.add(orderItem);
        if (visionSlotCount() >= 2) {
            JMenuItem swap = new JMenuItem("Swap first two cameras");
            swap.addActionListener(e -> swapVisionSlots(0, 1));
            devicesMenu.add(swap);
        }
        JMenuItem relationItem = new JMenuItem("Viewing direction and time offset…");
        relationItem.addActionListener(e -> showRelationDialog());
        devicesMenu.add(relationItem);
        int at = bar.getMenuCount();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null && "Help".equals(menu.getText())) {
                at = i;
                break;
            }
        }
        bar.add(devicesMenu, at);
        refreshDevicesMenu();
    }

    private void showRelationDialog() {
        if (relationDialog != null && relationDialog.isDisplayable()) {
            relationDialog.toFront();
            relationDialog.requestFocus();
            return;
        }
        AEViewer viewer = getAeViewer();
        Frame owner = viewer;
        relationDialog = new DeviceRelationDialog(owner, this);
        relationDialog.setVisible(true);
    }

    void refreshDevicesMenu() {
        if (orderItem != null) {
            orderItem.setText(orderLabel());
        }
    }

    private String orderLabel() {
        StringBuilder sb = new StringBuilder("Order: ");
        boolean any = false;
        for (DeviceSlot slot : slots) {
            if (slot.getModality() != Modality.VISION) {
                continue;
            }
            if (any) {
                sb.append(" | ");
            }
            any = true;
            sb.append(slot.getId());
            if (slot.getBoundStreamId() >= 0) {
                sb.append(" (stream ").append(slot.getBoundStreamId()).append(')');
            }
        }
        if (!any) {
            sb.append("(no vision slots)");
        }
        return sb.toString();
    }

    private List<Integer> visionIndexes() {
        List<Integer> index = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getModality() == Modality.VISION) {
                index.add(i);
            }
        }
        return index;
    }

    private int visionIndex(DeviceSlot slot) {
        if (slot == null) {
            return -1;
        }
        int index = 0;
        for (DeviceSlot other : slots) {
            if (other.getModality() != Modality.VISION) {
                continue;
            }
            if (other == slot) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private void bindByName(List<RecordingChipDetector.StreamHint> evts, boolean[] used, boolean explicitName) {
        for (DeviceSlot slot : slots) {
            if (slot.getBoundStreamId() >= 0 || slot.getModality() != Modality.VISION) {
                continue;
            }
            String name = explicitName ? slot.getSourceName() : slot.getId();
            if (!explicitName && slot.getSourceName() != null && !slot.getSourceName().isEmpty()) {
                continue;
            }
            int idx = firstMatching(evts, used, name);
            if (idx >= 0) {
                used[idx] = true;
                slot.setBoundStreamId(evts.get(idx).streamId);
            }
        }
    }

    private static int firstMatching(List<RecordingChipDetector.StreamHint> evts, boolean[] used, String name) {
        if (name == null || name.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < evts.size(); i++) {
            if (used[i]) {
                continue;
            }
            if (sourceMatches(evts.get(i).source, name)) {
                return i;
            }
        }
        return -1;
    }

    static boolean sourceMatches(String source, String name) {
        if (source == null || name == null || name.isEmpty()) {
            return false;
        }
        if (source.equalsIgnoreCase(name)) {
            return true;
        }
        if (name.length() < 3) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
    }

    private static int firstUnused(boolean[] used) {
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Decodes {@link MultiDeviceAddress} into native coordinates, applies the
     * relation, then sorts the slice by timestamp.
     */
    public static final class Extractor extends TypedEventExtractor<PolarityEvent> {

        public Extractor(MultiDeviceChip chip) {
            super(chip);
            setXmask(MultiDeviceAddress.X_MASK);
            setXshift((byte) 0);
            setYmask(MultiDeviceAddress.Y_MASK);
            setYshift((byte) MultiDeviceAddress.Y_SHIFT);
            setTypemask(1 << MultiDeviceAddress.TYPE_SHIFT);
            setTypeshift((byte) MultiDeviceAddress.TYPE_SHIFT);
        }

        @Override
        public synchronized void extractPacket(AEPacketRaw in, EventPacket<PolarityEvent> out) {
            out.clear();
            if (in == null || !(getChip() instanceof MultiDeviceChip multi)) {
                return;
            }
            int n = in.getNumEvents();
            if (n <= 0) {
                return;
            }
            int skipBy = 1;
            if (n > EventPacket.MAX_CAPACITY) {
                skipBy = Math.max(1, (n + EventPacket.MAX_CAPACITY - 1) / EventPacket.MAX_CAPACITY);
                MultiDeviceChip.log.warning(String.format(
                        "Raw packet has %d events (> EventPacket.MAX_CAPACITY=%d); extracting every %d-th event",
                        n, EventPacket.MAX_CAPACITY, skipBy));
            }
            int[] addresses = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            out.allocate((n + skipBy - 1) / skipBy);
            OutputEventIterator<PolarityEvent> iterator = out.outputIterator();
            DeviceRelation relation = multi.getRelation();
            for (int i = 0; i < n; i += skipBy) {
                int address = addresses[i];
                int x = MultiDeviceAddress.x(address);
                int y = MultiDeviceAddress.y(address);
                int type = MultiDeviceAddress.type(address);
                DeviceSlot slot = multi.slotBySource((byte) MultiDeviceAddress.source(address));
                if (slot == null || slot.getChild() == null) {
                    continue;
                }
                int sx = slot.getChild().getSizeX();
                int sy = slot.getChild().getSizeY();
                if (x < 0 || y < 0 || x >= sx || y >= sy) {
                    continue;
                }
                PolarityEvent event = iterator.nextOutput();
                event.x = (short) x;
                event.y = (short) y;
                event.timestamp = timestamps[i];
                event.address = address;
                event.setPolarity(type == 1 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
                if (relation != null) {
                    relation.place(slot, event, event);
                } else {
                    event.source = slot.getSource();
                }
            }
            out.sortByTimeStamp();
        }

        @Override
        public int getAddressFromCell(int x, int y, int type) {
            return MultiDeviceAddress.pack(x, y, type, 0);
        }
    }
}

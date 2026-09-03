package net.sf.jaer.eventio.aedat4;

import java.util.ArrayList;
import java.util.List;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.aedat4.Aedat4PlaybackAssignment.Binding;
import net.sf.jaer.eventio.aedat4.Aedat4PlaybackAssignment.ViewerSlot;

/**
 * Headless assignment of muxed AEDAT-4 EVTS streams to existing viewers.
 *
 * {@code java -cp "build/classes;jars/*;lib/*" net.sf.jaer.eventio.aedat4.Aedat4PlaybackAssignmentDemo}
 */
public final class Aedat4PlaybackAssignmentDemo {

    public static void main(String[] args) {
        testDifferentChipsReuseMatchingViewers();
        testIdentityOnlyWhenDuplicateChips();
        testReuseUnmatchedThenCreate();
        testSoftCapUnlessMoreDevices();
        testIdentityTokens();
        testRosSubscriber346ResolvesDavis346();
        System.out.println("AEDAT4_PLAYBACK_ASSIGNMENT PASS");
    }

    private static void testDifferentChipsReuseMatchingViewers() {
        List<RecordingChipDetector.StreamHint> streams = List.of(
                evts(0, "DVXplorer-us4addr1", 640, 480),
                evts(3, "DVS128-0633", 128, 128));
        List<ViewerSlot> viewers = List.of(
                new ViewerSlot(0, "DVS128", "0633"),
                new ViewerSlot(1, "DVXplorer", "bus4-addr1"));
        List<Binding> plan = Aedat4PlaybackAssignment.assign(streams, viewers, loaded());
        assertTrue(plan.size() == 2, "two bindings");
        assertTrue(plan.get(0).viewerIndex == 1 && !plan.get(0).changeChip && !plan.get(0).createNew,
                "DVX stream goes to DVX viewer, not the DVS128 window that opened the file");
        assertTrue(plan.get(1).viewerIndex == 0 && !plan.get(1).changeChip && !plan.get(1).createNew,
                "DVS128 stream goes to DVS128 viewer");
        Binding origin = Aedat4PlaybackAssignment.bindingForViewer(plan, 0);
        assertTrue(origin != null && origin.stream.streamId == 3,
                "opening DVS128 viewer plays the DVS128 stream, not stream 0");
    }

    private static void testIdentityOnlyWhenDuplicateChips() {
        List<RecordingChipDetector.StreamHint> streams = List.of(
                evts(0, "DVXplorer-us4addr1", 640, 480),
                evts(3, "DVXplorer-us5addr2", 640, 480));
        List<ViewerSlot> viewers = List.of(
                new ViewerSlot(0, "DVXplorer", "bus5-addr2"),
                new ViewerSlot(1, "DVXplorer", "bus4-addr1"));
        List<Binding> plan = Aedat4PlaybackAssignment.assign(streams, viewers, loaded());
        assertTrue(plan.get(0).viewerIndex == 1, "us4addr1 matches bus4-addr1");
        assertTrue(plan.get(1).viewerIndex == 0, "us5addr2 matches bus5-addr2");

        List<RecordingChipDetector.StreamHint> mixed = List.of(
                evts(0, "DVXplorer-us4addr1", 640, 480),
                evts(3, "DVS128-0633", 128, 128));
        List<ViewerSlot> mixedViewers = List.of(
                new ViewerSlot(0, "DVS128", "bus9-addr9"),
                new ViewerSlot(1, "DVXplorer", "bus1-addr1"));
        List<Binding> mixedPlan = Aedat4PlaybackAssignment.assign(mixed, mixedViewers, loaded());
        assertTrue(mixedPlan.get(0).viewerIndex == 1 && mixedPlan.get(1).viewerIndex == 0,
                "different chips match by AEChip, not USB bus");
    }

    private static void testReuseUnmatchedThenCreate() {
        List<RecordingChipDetector.StreamHint> streams = List.of(
                evts(0, "DVXplorer-A", 640, 480),
                evts(3, "DVS128-B", 128, 128));
        List<ViewerSlot> one = List.of(new ViewerSlot(0, "Davis346red", ""));
        List<Binding> plan = Aedat4PlaybackAssignment.assign(streams, one, loaded());
        assertTrue(!plan.get(0).createNew && plan.get(0).changeChip && plan.get(0).viewerIndex == 0,
                "first leftover stream reuses the unmatched viewer");
        assertTrue(plan.get(1).createNew, "second stream opens a new viewer");

        List<ViewerSlot> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eight.add(new ViewerSlot(i, "Davis346red", ""));
        }
        List<Binding> reuse = Aedat4PlaybackAssignment.assign(streams, eight, loaded());
        assertTrue(!reuse.get(0).createNew && !reuse.get(1).createNew,
                "with 8 unmatched viewers, both streams reuse windows");
        assertTrue(reuse.get(0).changeChip && reuse.get(1).changeChip,
                "reused Davis windows switch chip");
    }

    private static void testSoftCapUnlessMoreDevices() {
        assertTrue(Aedat4PlaybackAssignment.maxViewers(2) == 8, "2 cameras still cap at 8");
        assertTrue(Aedat4PlaybackAssignment.maxViewers(10) == 10, "10 cameras raise the cap");
        List<RecordingChipDetector.StreamHint> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add(evts(i * 3, "DVS128-" + i, 128, 128));
        }
        List<ViewerSlot> two = List.of(
                new ViewerSlot(0, "DVS128", ""),
                new ViewerSlot(1, "DVS128", ""));
        List<Binding> plan = Aedat4PlaybackAssignment.assign(ten, two, loaded());
        int created = 0;
        for (Binding b : plan) {
            if (b.createNew) {
                created++;
            }
        }
        assertTrue(created == 8, "10 streams + 2 viewers creates 8 more, got " + created);
    }

    private static void testIdentityTokens() {
        assertTrue(Aedat4PlaybackAssignment.identitiesMatch("us4addr1", "bus4-addr1"),
                "us4addr1 matches bus4-addr1");
        assertTrue(Aedat4PlaybackAssignment.identitiesMatch("00000843", "DAVIS346_00000843"),
                "serial suffix matches");
        assertTrue(!Aedat4PlaybackAssignment.identitiesMatch("us4addr1", "bus5-addr2"),
                "different bus/addr do not match");
        assertTrue("us4addr1".equals(Aedat4PlaybackAssignment.identityFromSource("DVXplorer-us4addr1")),
                "dash suffix");
        assertTrue("00000843".equals(Aedat4PlaybackAssignment.identityFromSource("DAVIS346_00000843")),
                "underscore DV serial");
    }

    private static void testRosSubscriber346ResolvesDavis346() {
        RecordingChipDetector.Hint hint = new RecordingChipDetector.Hint(
                "ROS-Subscriber", 346, 260, "aedat4-stream-0");
        Class<? extends AEChip> chip = RecordingChipDetector.resolve(hint, loaded());
        assertTrue(chip == eu.seebetter.ini.chips.davis.Davis346red.class,
                "ROS-Subscriber 346x260 -> Davis346red, got " + chip);
        List<RecordingChipDetector.StreamHint> streams = List.of(
                evts(0, "ROS-Subscriber", 346, 260),
                evts(1, "ROS-Subscriber", 346, 260));
        List<ViewerSlot> viewers = List.of(new ViewerSlot(0, "DVXplorerMicro", ""));
        List<Binding> plan = Aedat4PlaybackAssignment.assign(streams, viewers, loaded());
        assertTrue(plan.size() == 2, "two ROS streams");
        assertTrue(plan.get(0).chip == eu.seebetter.ini.chips.davis.Davis346red.class
                && plan.get(1).chip == eu.seebetter.ini.chips.davis.Davis346red.class,
                "both ROS streams resolve to Davis346red");
        assertTrue(!plan.get(0).createNew && plan.get(0).changeChip,
                "first ROS stream reuses leftover viewer");
        assertTrue(plan.get(1).createNew, "second ROS stream opens a new viewer");
    }

    private static RecordingChipDetector.StreamHint evts(int id, String source, int sx, int sy) {
        return new RecordingChipDetector.StreamHint(id, "EVTS", source, sx, sy, null, "events");
    }

    private static List<Class<? extends AEChip>> loaded() {
        List<Class<? extends AEChip>> loaded = new ArrayList<>();
        loaded.add(ch.unizh.ini.jaer.chip.retina.DVS128.class);
        loaded.add(ch.unizh.ini.jaer.chip.retina.DVXplorer.class);
        loaded.add(eu.seebetter.ini.chips.davis.Davis346red.class);
        return loaded;
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}

package net.sf.jaer.eventio.ros2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

/**
 * Voxel bilinear split, signed histogram, and Foxglove 0–1 / rgb8 mapping.
 */
public class Ros2FrameAssemblerTest {

    @Test
    public void signedHistogramOnOff() {
        Ros2FrameAssembler a = new Ros2FrameAssembler();
        a.setSize(2, 1);
        a.setGrayScale(8);
        a.setEventsPerFrame(4);
        a.setFlipY(false);
        a.addEvent(0, 0, true, 0);
        a.addEvent(0, 0, true, 1);
        a.addEvent(1, 0, false, 2);
        boolean done = a.addEvent(1, 0, false, 3);
        assertTrue(done);
        int[] c = a.copyEventCount();
        assertEquals(2, c[0]);
        assertEquals(-2, c[1]);
    }

    @Test
    public void voxelBilinearSplit() {
        Ros2FrameAssembler a = new Ros2FrameAssembler();
        a.setSize(1, 1);
        a.setVoxelBins(3);
        a.setEventsPerFrame(2);
        a.setFlipY(false);
        a.addEvent(0, 0, true, 0);
        boolean done = a.addEvent(0, 0, true, 100);
        assertTrue(done);
        a.rasterizeVoxel();
        float[] v = a.copyVoxel();
        assertEquals(3, v.length);
        assertEquals(1f, v[0], 1e-5f); // t=0 → bin 0
        assertEquals(0f, v[1], 1e-5f);
        assertEquals(1f, v[2], 1e-5f); // t=last → last bin
    }

    @Test
    public void foxgloveFloat32MapsSignedToUnit() {
        Ros2FrameAssembler a = new Ros2FrameAssembler();
        a.setSize(1, 1);
        a.setGrayScale(4);
        a.setEventsPerFrame(1);
        a.setFlipY(false);
        a.addEvent(0, 0, true, 0);
        EncodedImage img = a.encodeFoxglove(Ros2FrameAssembler.FoxgloveFrameEncoding.Float32).get(0);
        assertEquals("32FC1", img.encoding);
        float v = ByteBuffer.wrap(img.data).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        assertEquals(0.5f + 0.5f * 1f / 4f, v, 1e-5f);
    }

    @Test
    public void foxgloveRgb8OnRedOffGreen() {
        Ros2FrameAssembler a = new Ros2FrameAssembler();
        a.setSize(2, 1);
        a.setGrayScale(1);
        a.setEventsPerFrame(2);
        a.setFlipY(false);
        a.addEvent(0, 0, true, 0);
        a.addEvent(1, 0, false, 1);
        EncodedImage img = a.encodeFoxglove(Ros2FrameAssembler.FoxgloveFrameEncoding.Rgb8).get(0);
        assertEquals("rgb8", img.encoding);
        assertEquals((byte) 255, img.data[0]);
        assertEquals((byte) 0, img.data[1]);
        assertEquals((byte) 0, img.data[3]);
        assertEquals((byte) 255, img.data[4]);
    }
}

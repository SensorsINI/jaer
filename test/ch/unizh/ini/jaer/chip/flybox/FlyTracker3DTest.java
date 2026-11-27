package ch.unizh.ini.jaer.chip.flybox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Pinhole rays from the NRV and EVK4 lenses meet at a known point.
 */
public class FlyTracker3DTest {

    @Test
    public void opticalAxesMeetAtTheOrigin() {
        float[] xyz = FlyTracker3D.intersectRays(0, 0, 6f, 100f, 0, 0, 8f, 80f);
        assertNotNull(xyz);
        assertEquals(0f, xyz[0], 1e-3f);
        assertEquals(0f, xyz[1], 1e-3f);
        assertEquals(0f, xyz[2], 1e-3f);
    }

    @Test
    public void knownPointProjectsAndIntersects() {
        float x = 10f;
        float y = 20f;
        float z = 5f;
        float evk4F = 6f;
        float evk4D = 100f;
        float nrvF = 8f;
        float nrvD = 80f;
        float evk4Depth = evk4D - z;
        float evk4Du = -x * evk4F / (evk4Depth * FlyTracker3D.EVK4_PITCH_MM);
        float evk4Dv = y * evk4F / (evk4Depth * FlyTracker3D.EVK4_PITCH_MM);
        float nrvDepth = x + nrvD;
        float nrvDu = y * nrvF / (nrvDepth * FlyTracker3D.NRV_PITCH_MM);
        float nrvDv = z * nrvF / (nrvDepth * FlyTracker3D.NRV_PITCH_MM);

        float[] xyz = FlyTracker3D.intersectRays(
                evk4Du, evk4Dv, evk4F, evk4D,
                nrvDu, nrvDv, nrvF, nrvD);
        assertNotNull(xyz);
        assertEquals(x, xyz[0], 1e-2f);
        assertEquals(y, xyz[1], 1e-2f);
        assertEquals(z, xyz[2], 1e-2f);
    }
}

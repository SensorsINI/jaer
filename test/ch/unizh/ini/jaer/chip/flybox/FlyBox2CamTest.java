package ch.unizh.ini.jaer.chip.flybox;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sf.jaer.chip.multidevice.DeviceSlot;
import net.sf.jaer.graphics.ChipCanvas;
import nrv.chip.NRVS5KRC1S;
import prophesee.chip.PropheseeIMX636HD;

/**
 * Fly-box slot classes, source names, and the yz / xy viewing directions.
 */
public class FlyBox2CamTest {

    @Before
    public void headlessCanvas() {
        ChipCanvas.beginPreviewHeadless();
    }

    @After
    public void endHeadlessCanvas() {
        ChipCanvas.endPreviewHeadless();
    }

    @Test
    public void nrvImagesYzAndEvk4ImagesXy() {
        FlyBox2Cam probe = new FlyBox2Cam();
        probe.getPrefs().remove("pose.nrv.azimuthDeg");
        probe.getPrefs().remove("pose.evk4.azimuthDeg");
        FlyBox2Cam chip = new FlyBox2Cam();
        DeviceSlot nrv = chip.getSlots().get(0);
        DeviceSlot evk4 = chip.getSlots().get(1);

        assertEquals("nrv", nrv.getId());
        assertEquals(NRVS5KRC1S.class, nrv.getChipClass());
        assertEquals("NRVS5KRC1S", nrv.getSourceName());
        assertEquals(960, nrv.getChild().getSizeX());
        assertEquals(720, nrv.getChild().getSizeY());
        assertEquals(FlyBox2Cam.NRV_AZIMUTH_DEG, chip.getPose().poseFor("nrv").azimuthDeg, 0.001f);
        assertEquals(FlyBox2Cam.NRV_ELEVATION_DEG, chip.getPose().poseFor("nrv").elevationDeg, 0.001f);

        assertEquals("evk4", evk4.getId());
        assertEquals(PropheseeIMX636HD.class, evk4.getChipClass());
        assertEquals("PropheseeIMX636HD", evk4.getSourceName());
        assertEquals(1280, evk4.getChild().getSizeX());
        assertEquals(720, evk4.getChild().getSizeY());
        assertEquals(FlyBox2Cam.EVK4_AZIMUTH_DEG, chip.getPose().poseFor("evk4").azimuthDeg, 0.001f);
        assertEquals(FlyBox2Cam.EVK4_ELEVATION_DEG, chip.getPose().poseFor("evk4").elevationDeg, 0.001f);

        assertEquals(960 + 1280, chip.getSizeX());
        assertEquals(720, chip.getSizeY());
        assertEquals(0, chip.getRelation().tileOriginX(chip, nrv));
        assertEquals(960, chip.getRelation().tileOriginX(chip, evk4));
    }
}

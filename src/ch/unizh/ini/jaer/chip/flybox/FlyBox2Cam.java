/*
 * FlyBox2Cam.java
 *
 * Fly box: NRV images the yz plane, Prophesee EVK4 images the xy plane.
 */
package ch.unizh.ini.jaer.chip.flybox;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.UsbDevices;
import net.sf.jaer.chip.multidevice.DeviceSlot;
import net.sf.jaer.chip.multidevice.Modality;
import net.sf.jaer.chip.multidevice.MultiDeviceChip;
import net.sf.jaer.chip.multidevice.TiledNativeRelation;
import net.sf.jaer.chip.multidevice.ViewingPose;
import nrv.chip.NRVS5KRC1S;
import prophesee.chip.PropheseeIMX636HD;

/**
 * Two cameras on the fly box, one viewer, one filter chain.
 * <p>
 * World frame is right-handed with z up. Azimuth 0 looks along +z and
 * increases toward +x. Elevation 0 is horizontal; -90 looks along -z.
 * Sensor {@code x,y} stay native. The angles are only the viewing direction.
 * <ul>
 * <li><b>nrv</b> — {@link NRVS5KRC1S} images the yz plane (looks along +x).</li>
 * <li><b>evk4</b> — {@link PropheseeIMX636HD} images the xy plane (looks along -z).</li>
 * </ul>
 * Muxed AEDAT-4 source strings are matched on the child chip name
 * ({@code NRVS5KRC1S}, {@code PropheseeIMX636HD}). The canvas tiles NRV on
 * the left and EVK4 on the right. Live USB is still opened from a
 * single-camera chip.
 */
@Description("Fly box: NRV viewing the yz plane and Prophesee EVK4 viewing the xy plane")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@UsbDevices({})
public class FlyBox2Cam extends MultiDeviceChip {

    /** Looks along +x, so the image is the yz plane. */
    public static final float NRV_AZIMUTH_DEG = 90f;
    /** Horizontal. */
    public static final float NRV_ELEVATION_DEG = 0f;
    /** Azimuth is unused when looking straight down. */
    public static final float EVK4_AZIMUTH_DEG = 0f;
    /** Looks along -z, so the image is the xy plane. */
    public static final float EVK4_ELEVATION_DEG = -90f;

    public FlyBox2Cam() {
        DeviceSlot nrv = addSlot("nrv", NRVS5KRC1S.class, Modality.VISION);
        nrv.setSourceName(NRVS5KRC1S.class.getSimpleName());
        DeviceSlot evk4 = addSlot("evk4", PropheseeIMX636HD.class, Modality.VISION);
        evk4.setSourceName(PropheseeIMX636HD.class.getSimpleName());
        setRelation(new TiledNativeRelation());
        applyDefaultPose(nrv, NRV_AZIMUTH_DEG, NRV_ELEVATION_DEG);
        applyDefaultPose(evk4, EVK4_AZIMUTH_DEG, EVK4_ELEVATION_DEG);
    }

    /**
     * Keep a pose the Devices dialog already stored. Otherwise use the
     * fly-box direction.
     */
    private void applyDefaultPose(DeviceSlot slot, float azimuthDeg, float elevationDeg) {
        String azimuthKey = "pose." + slot.getId() + ".azimuthDeg";
        if (getPrefs().get(azimuthKey, null) != null) {
            return;
        }
        ViewingPose.SlotPose pose = getPose().poseFor(slot.getId());
        pose.azimuthDeg = azimuthDeg;
        pose.elevationDeg = elevationDeg;
    }
}

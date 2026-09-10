package net.sf.jaer.chip.opencv;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.opencv.OpenCvCameraHardwareInterface;

/**
 * Standard frame camera (webcam) via OpenCV {@code VideoCapture}. Live data is
 * {@link net.sf.jaer.event.FramePacket} RGB only; no polarity or biasgen.
 */
@Description("OpenCV / UVC webcam: RGB frames in AEViewer and AEDAT-4 FRME")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OpenCvFrameCamera extends AEChip {

    public OpenCvFrameCamera() {
        setName("OpenCvFrameCamera");
        setSizeX(640);
        setSizeY(480);
        setNumCellTypes(1);
        setEventClass(PolarityEvent.class);
        setPixelHeightUm(1);
        setPixelWidthUm(1);
        OpenCvFrameRenderer renderer = new OpenCvFrameRenderer(this);
        setRenderer(renderer);
        DisplayMethod rgba = new ChipRendererDisplayMethodRGBA(getCanvas());
        getCanvas().addDisplayMethod(rgba);
        getCanvas().setDisplayMethod(rgba);
        setEventExtractor(new EmptyExtractor(this));
    }

    public OpenCvFrameCamera(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public void setHardwareInterface(HardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        if (hardwareInterface instanceof OpenCvCameraHardwareInterface) {
            ((OpenCvCameraHardwareInterface) hardwareInterface).setChip(this);
        }
    }

    /** Raw AE extractor unused on the live typed path. */
    public static final class EmptyExtractor extends TypedEventExtractor<PolarityEvent> {
        public EmptyExtractor(AEChip chip) {
            super(chip);
        }

        @Override
        public synchronized EventPacket<PolarityEvent> extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<>(PolarityEvent.class);
            } else {
                out.clear();
            }
            return out;
        }
    }
}

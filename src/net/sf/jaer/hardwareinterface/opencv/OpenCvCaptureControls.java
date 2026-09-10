package net.sf.jaer.hardwareinterface.opencv;

/**
 * {@link net.sf.jaer.chip.opencv.OpenCvFrameCamera} applies stored size / FOURCC /
 * fps after {@code VideoCapture.open}.
 */
public interface OpenCvCaptureControls {

    void onCaptureOpened(OpenCvCameraHardwareInterface hw);
}

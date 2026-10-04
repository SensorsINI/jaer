package net.sf.jaer.hardwareinterface.serial;

/**
 * One serial port that can be opened, with a short label and an HTML tooltip.
 */
public final class SerialPortInfo {

    private final String device;
    private final String label;
    private final String tooltip;

    public SerialPortInfo(String device, String label, String tooltip) {
        this.device = device == null ? "" : device;
        this.label = (label == null || label.isEmpty()) ? this.device : label;
        this.tooltip = tooltip == null ? "" : tooltip;
    }

    /** Path or COM name passed to the port open. This is the preferences value. */
    public String device() {
        return device;
    }

    public String label() {
        return label;
    }

    public String tooltip() {
        return tooltip;
    }
}

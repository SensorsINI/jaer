/*
 * ExternalEvent.java
 *
 * jAER 3.0: external / special marker as its own typed event (not ApsDvsEvent).
 */
package net.sf.jaer.event;

/**
 * External input or other special marker carried in a SPECIAL
 * {@link EventPacket}, produced at USB decode time.
 *
 * @author tobi
 */
public class ExternalEvent extends BasicEvent {

    public enum Edge {
        Falling,
        Rising,
        Pulse,
        Other
    }

    private Edge edge = Edge.Other;
    /** Raw special-event data code from the sensor (e.g. 2/3/4). */
    private int code;

    public ExternalEvent() {
    }

    public Edge getEdge() {
        return edge;
    }

    public void setEdge(Edge edge) {
        this.edge = edge == null ? Edge.Other : edge;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    @Override
    public void copyFrom(BasicEvent src) {
        super.copyFrom(src);
        if (src instanceof ExternalEvent) {
            ExternalEvent e = (ExternalEvent) src;
            this.edge = e.edge;
            this.code = e.code;
        }
    }

    @Override
    public String toString() {
        return String.format("ExternalEvent ts=%d edge=%s code=%d", timestamp, edge, code);
    }
}

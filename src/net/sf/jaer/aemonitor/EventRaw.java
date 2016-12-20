/*
 * EventRaw.java
 *
 * Created on November 6, 2005, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.aemonitor;

/**
 * A raw address-event, having an int (32 bit) timestamp and int (32 bit) raw address
 *
 * @author tobi/minliu/luca
 */
public class EventRaw {
	public int address;
	public int timestamp;
        
        public enum EventType { // see http://inilabs.com/support/software/fileformat/
            SpecialEvent(0), PolarityEvent(1), FrameEvent(2), Imu6Event(3), Imu9Event(4), SampleEvent(5), EarEvent(6), ConfigEvent(7),
            Point1DEvent(8),Point2DEvent(9),Point3DEvent(10), Point4DEvent(11),SpikeEvent(12);// ordered according to id code in http://inilabs.com/support/software/fileformat/#h.veimuraa2lff
            private int value;
            private EventType(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }        
        public EventType eventtype;  //Just for jAER 3.0
        public int pixelData;             //Just for jAER 3.0 Frame Event

	/** Creates a new instance of EventRaw */
	public EventRaw() {
	}

	/**
	 * Creates a new instance of EventRaw for jAER 2.0
	 *
	 * @param a
	 *            the address
	 * @param t
	 *            the timestamp
	 */
	public EventRaw(int a, int t) {
		address = a;
		timestamp = t;

	}

        /**
	 * Creates a new instance of EventRaw for jAER 3.0
	 *
	 * @param a
	 *            the address
	 * @param t
	 *            the timestamp
         * @param etype
         *            the event type
	 */
	public EventRaw(int a, int t, EventType etype) {
		address = a;
		timestamp = t;
                eventtype = etype;
	}
        
	@Override
	public String toString() {
		return "EventRaw with address " + address + " and timestamp " + timestamp;
	}
}

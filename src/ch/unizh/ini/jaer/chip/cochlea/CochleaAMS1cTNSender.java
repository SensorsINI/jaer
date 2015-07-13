package ch.unizh.ini.jaer.chip.cochlea;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

public class CochleaAMS1cTNSender extends EventFilter2D implements Observer {
	private static String TN_IP = "192.168.1.31";
	private static int TN_PORT = 5000;
	private static int TN_MAX_EVENTS = 2048;

	private DatagramSocket socket;
	private InetAddress address;

	private boolean firstRun;

	private int baseTS;
	private int lastTS;
	private int eventCounter;
	private int packetCounter;

	private final ByteBuffer dataBuf = ByteBuffer.allocate(4 * (4 + CochleaAMS1cTNSender.TN_MAX_EVENTS));
	private final ByteBuffer tickBuf = ByteBuffer.allocate(4 * 4);

	public CochleaAMS1cTNSender(final AEChip chip) {
		super(chip);
		chip.addObserver(this);
		initFilter();
	}

	@Override
	public void update(final Observable arg0, final Object arg1) {
		// TODO Auto-generated method stub
	}

	@Override
	public EventPacket<?> filterPacket(final EventPacket<?> in) {
		// Check that we're getting Cochlea events.
		if (in.getEventClass() != CochleaAMSEvent.class) {
			return in;
		}

		// Initialize last timestamp reference.
		if (firstRun) {
			lastTS = in.getFirstTimestamp();
			firstRun = false;
		}

		for (final Object evtGeneric : in) {
			// Don't consider non-Cochlea events.
			if (!(evtGeneric instanceof CochleaAMSEvent)) {
				continue;
			}

			// We know it's a Cochlea event at this point.
			final CochleaAMSEvent evt = (CochleaAMSEvent) evtGeneric;
			final int evtTS = evt.getTimestamp() / 1000; // in ms from µs

			// If the timestamp is the same as the last one, we
			// just add the transformed event to the packet, up
			// to a certain packet size, and then send that packet.
			if (evtTS == lastTS) {
				encodeCochleaEvent(evt);

				// Send packet if full (max events reached).
				if (eventCounter == CochleaAMS1cTNSender.TN_MAX_EVENTS) {
					sendDataPacket();
				}
			}
			// If ts changes, we commit the current packet and then
			// send "tick" packets to have TrueNorth advance its
			// millisecond global clock.
			else {
				encodeCochleaEvent(evt);

				sendDataPacket();

				int tsDifference = evtTS - lastTS;

				while (tsDifference-- > 0) {
					sendTickPacket();
				}

				// Update last timestamp reference.
				lastTS = evtTS;
			}
		}

		return in;
	}

	private void encodeCochleaEvent(final CochleaAMSEvent evt) {
		// Encode Cochlea event into TN format.
		// UDP Packet Spike Word(s):
		// # [31:16] 16b core_id
		// # [15:8] 8b axon
		// # [7:0] 8b deltaT (delay from base_time)
		int tnEvt = 0;

		// Use channels as core_id.
		tnEvt |= (evt.getX() & 0xFFFF) << 16;
		// Use other data as axon.
		tnEvt |= (evt.getY() & 0xFF) << 8;
		// deltaT is always zero in this scheme.

		// Packet format is centered around 32bit integers.
		dataBuf.putInt(tnEvt);
		eventCounter++;
	}

	private void sendDataPacket() {
		// Set header fields.
		// Header0 carries information about the number of spikes, and some packet ID#s.
		// Header1 carries the global time reference ("base_time") for the spikes in the packet.
		dataBuf.putInt(0, (((eventCounter & 0x3FFF) << 18) | // 14 Bits, 31:18
			((packetCounter & 0x3F) << 12) | // 6 Bits, 17:12
			((0 & 0xF) << 8) | // 4 bits, 11:8
			((2 & 0xFF) << 0))); // 8 Bits, 7:0

		dataBuf.putInt(4, baseTS); // 32 bits = ~50 days at 1ms ticks.

		final DatagramPacket packet = new DatagramPacket(dataBuf.array(), dataBuf.limit(), address,
			CochleaAMS1cTNSender.TN_PORT);

		try {
			// Send request
			socket.send(packet);
		}
		catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Update/reset counters.
		eventCounter = 0;
		packetCounter++;

		// Reset buffer.
		dataBuf.clear();
		dataBuf.position(16);
	}

	private void sendTickPacket() {
		// Set header fields.
		// Header0 carries information about the number of spikes, and some packet ID#s.
		// Header1 carries the global time reference ("base_time") for the spikes in the packet.
		tickBuf.putInt(0, (((0 & 0x3FFF) << 18) | // 14 Bits, 31:18
			((packetCounter & 0x3F) << 12) | // 6 Bits, 17:12
			((0 & 0xF) << 8) | // 4 bits, 11:8
			((2 & 0xFF) << 0))); // 8 Bits, 7:0

		tickBuf.putInt(4, baseTS); // 32 bits = ~50 days at 1ms ticks.

		final DatagramPacket packet = new DatagramPacket(tickBuf.array(), tickBuf.limit(), address,
			CochleaAMS1cTNSender.TN_PORT);

		try {
			// Send request
			socket.send(packet);
		}
		catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Update/reset counters.
		packetCounter++;
		baseTS++;
	}

	@Override
	public void resetFilter() {
		if (socket != null) {
			socket.close();
		}

		initFilter();
	}

	@Override
	public void initFilter() {
		// Set default for variables.
		firstRun = true;

		baseTS = 0;
		lastTS = 0;
		eventCounter = 0;
		packetCounter = 0;

		// Little endian buffers.
		dataBuf.order(ByteOrder.LITTLE_ENDIAN);
		tickBuf.order(ByteOrder.LITTLE_ENDIAN);

		// Prepare data buffer: starts after 16 byte header.
		dataBuf.putInt(0, 0); // Packet options, set later.
		dataBuf.putInt(4, 0); // Base time, set later.
		dataBuf.putInt(8, 1); // 1 means NO TICK.
		dataBuf.putInt(12, 0); // Always zero.
		dataBuf.position(16);

		// Prepare tick buffer: only 16 byte header.
		tickBuf.putInt(0, 0); // Packet options, set later.
		tickBuf.putInt(4, 0); // Base time, set later.
		tickBuf.putInt(8, 0); // 0 means TICK.
		tickBuf.putInt(12, 0); // Always zero.

		// Get a datagram socket
		try {
			socket = new DatagramSocket();
			address = InetAddress.getByName(CochleaAMS1cTNSender.TN_IP);

			// Connect UDP socket so that we can only send to
			// that IP:Port combination.
			socket.connect(address, CochleaAMS1cTNSender.TN_PORT);
		}
		catch (SocketException | UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

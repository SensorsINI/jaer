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
import ch.unizh.ini.jaer.chip.cochlea.BinauralCochleaEvent.Ear;

public class CochleaAMS1cTNSender extends EventFilter2D implements Observer {
	private String TN_IP = getString("TN_IP", "192.168.1.1");
	private int TN_PORT = getInt("TN_PORT", 5000);
	private int TN_MAX_EVENTS = getInt("TN_MAX_EVENTS", 2048);

	private int TN_CORE_OFFSET = getInt("TN_CORE_OFFSET", 0);
	private boolean TN_CORE_SINGLE = getBoolean("TN_CORE_SINGLE", false);

	private DatagramSocket socket;
	private InetAddress address;

	private boolean firstRun;

	private int baseTS;
	private int lastTS;
	private int eventCounter;
	private int packetCounter;

	private final ByteBuffer dataBuf = ByteBuffer.allocate(4 * (4 + TN_MAX_EVENTS));
	private final ByteBuffer tickBuf = ByteBuffer.allocate(4 * 4);

	public CochleaAMS1cTNSender(final AEChip chip) {
		super(chip);
		chip.addObserver(this);
		initFilter();
	}

	public String getTNIP() {
		return TN_IP;
	}

	public void setTNIP(final String TN_IP) {
		this.TN_IP = TN_IP;
		putString("TN_IP", TN_IP);

		// Update IP address object for UDP sending.
		try {
			address = InetAddress.getByName(TN_IP);
		}
		catch (final UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public int getTNPort() {
		return TN_PORT;
	}

	public void setTNPort(final int TN_PORT) {
		this.TN_PORT = TN_PORT;
		putInt("TN_PORT", TN_PORT);
	}

	public int getTNMaxEvents() {
		return TN_MAX_EVENTS;
	}

	public void setTNMaxEvents(final int TN_MAX_EVENTS) {
		this.TN_MAX_EVENTS = TN_MAX_EVENTS;
		putInt("TN_MAX_EVENTS", TN_MAX_EVENTS);
	}

	public int getTNCoreOffset() {
		return TN_CORE_OFFSET;
	}

	public void setTNCoreOffset(final int TN_CORE_OFFSET) {
		this.TN_CORE_OFFSET = TN_CORE_OFFSET;
		putInt("TN_CORE_OFFSET", TN_CORE_OFFSET);
	}

	public boolean isTNCoreSingle() {
		return TN_CORE_SINGLE;
	}

	public void setTNCoreSingle(final boolean TN_CORE_SINGLE) {
		this.TN_CORE_SINGLE = TN_CORE_SINGLE;
		putBoolean("TN_CORE_SINGLE", TN_CORE_SINGLE);
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
				if (eventCounter == TN_MAX_EVENTS) {
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

		// Apply offset to hit other cores.
		int tnTargetCore = TN_CORE_OFFSET;
		int tnTargetAxon;

		if (TN_CORE_SINGLE) {
			// In single core mode, we keep the default core,
			// and map all channels into axons.
			tnTargetAxon = evt.getX();

			// Left ear axons 0-63, right ear axons 64-127.
			if (evt.getEar() == Ear.RIGHT) {
				tnTargetAxon += 64;
			}
		}
		else {
			// Use channels as core_id.
			tnTargetCore += evt.getX();

			// Use other data as axon.
			tnTargetAxon = evt.getY();
		}

		int tnEvt = 0; // deltaT is always zero in this scheme.
		tnEvt |= (tnTargetCore & 0xFFFF) << 16;
		tnEvt |= (tnTargetAxon & 0xFF) << 8;

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

		final DatagramPacket packet = new DatagramPacket(dataBuf.array(), dataBuf.limit(), address, TN_PORT);

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

		final DatagramPacket packet = new DatagramPacket(tickBuf.array(), tickBuf.limit(), address, TN_PORT);

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
		}
		catch (final SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			address = InetAddress.getByName(TN_IP);
		}
		catch (final UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

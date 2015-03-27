/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.boards;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DAViSFX3HardwareInterface;
import ch.unizh.ini.jaer.config.AbstractConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDConfigValue;
import ch.unizh.ini.jaer.config.cpld.CPLDShiftRegister;
import ch.unizh.ini.jaer.config.dac.DAC;
import ch.unizh.ini.jaer.config.dac.DACchannel;
import ch.unizh.ini.jaer.config.dac.DACchannelArray;
import ch.unizh.ini.jaer.config.fx2.PortBit;
import ch.unizh.ini.jaer.config.onchip.ChipConfigChain;

/**
 *
 * @author Christian
 */
public class LatticeLogicConfig extends Biasgen implements HasPreference {
	public AEChip chip;
	protected ChipConfigChain chipConfigChain = null;
	protected ShiftedSourceBiasCF[] ssBiases = new ShiftedSourceBiasCF[2];
	/** list of configuration values that implement HasPreference; used for load and store of preferred values. */
	protected ArrayList<HasPreference> hasPreferenceList = new ArrayList<HasPreference>();

	public LatticeLogicConfig(Chip chip) {
		super(chip);
		this.chip = (AEChip) chip;
	}

	/*************************** FX2 *************************/

	/**
	 * Vendor request command understood by the firmware in connection with
	 * VENDOR_REQUEST_SEND_BIAS_BYTES
	 */
	public final Fx2ConfigCmd CMD_IPOT = new Fx2ConfigCmd(1, "IPOT"), CMD_AIPOT = new Fx2ConfigCmd(2, "AIPOT"),
		CMD_SCANNER = new Fx2ConfigCmd(3, "SCANNER"), CMD_CHIP_CONFIG = new Fx2ConfigCmd(4, "CHIP"),
		CMD_SETBIT = new Fx2ConfigCmd(5, "SETBIT"), CMD_VDAC = new Fx2ConfigCmd(6, "VDAC"),
		CMD_INITDAC = new Fx2ConfigCmd(7, "INITDAC"), CMD_CPLD_CONFIG = new Fx2ConfigCmd(8, "CPLD");
	public final String[] CMD_NAMES = { "IPOT", "AIPOT", "SCANNER", "CHIP", "SETBIT", "VDAC", "INITDAC", "CPLD" };
	final byte[] emptyByteArray = new byte[0];

	/**
	 * List of direct port bits
	 *
	 */
	protected ArrayList<PortBit> portBits = new ArrayList();

	// Old logic.
	public static final byte VR_WRITE_CONFIG = (byte) 0xB8;

	// New SeeBetterLogic.
	public static final byte VR_CHIP_BIAS = (byte) 0xC0;
	public static final byte VR_CHIP_DIAG = (byte) 0xC1;

	// Clock cycles per microsecond for ADC logic. It's running at 30MHz.
	private static final int ADC_CLOCK_FREQ_CYCLES = 30;

	/**
	 * @return the chipConfigChain
	 */
	public ChipConfigChain getChipConfigChain() {
		return chipConfigChain;
	}

	/**
	 * Returns list of config values that have preference value
	 *
	 * @return the hasPreferenceList
	 */
	public ArrayList<HasPreference> getHasPreferenceList() {
		return hasPreferenceList;
	}

	/** Command sent to firmware by vendor request */
	public class Fx2ConfigCmd {
		short code;
		String name;

		public Fx2ConfigCmd(int code, String name) {
			this.code = (short) code;
			this.name = name;
		}

		@Override
		public String toString() {
			return "ConfigCmd{" + "code=" + code + ", name=" + name + '}';
		}
	}

	/**
	 * convenience method for sending configuration to hardware. Sends vendor
	 * request VR_WRITE_CONFIG with subcommand cmd, index index and bytes bytes.
	 *
	 * @param cmd
	 *            the subcommand to set particular configuration, e.g.
	 *            CMD_CPLD_CONFIG
	 * @param index
	 *            unused
	 * @param bytes
	 *            the payload
	 * @throws HardwareInterfaceException
	 */
	protected void sendFx2ConfigCommand(Fx2ConfigCmd cmd, int index, byte[] bytes) throws HardwareInterfaceException {
		if (bytes == null) {
			bytes = emptyByteArray;
		}
		else {
			// NullSettle is only understood by SeeBetterLogic new logic devices. So we chop it off when talking
			// to old devices based on the old logic.
			if ((getHardwareInterface() != null) && !(getHardwareInterface() instanceof CypressFX3)) {
				if ((cmd == CMD_CPLD_CONFIG) && (bytes.length == 19)) {
					bytes[16] = bytes[17];
					bytes[17] = bytes[18];

					bytes = Arrays.copyOfRange(bytes, 2, 18);
				}
			}
		}

		if ((getHardwareInterface() != null) && (getHardwareInterface() instanceof CypressFX2)) {
			((CypressFX2) getHardwareInterface()).sendVendorRequest(VR_WRITE_CONFIG, (short) (0xffff & cmd.code),
				(short) (0xffff & index), bytes); // & to prevent sign extension
													// for negative shorts
		}

		if ((getHardwareInterface() != null)
			&& (getHardwareInterface() instanceof net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2)) {
			((net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2) getHardwareInterface()).sendVendorRequest(
				VR_WRITE_CONFIG, (short) (0xffff & cmd.code), (short) (0xffff & index), bytes);
		}

		// Support FX3 firmware with its own special Vendor Request.
		if ((getHardwareInterface() != null) && (getHardwareInterface() instanceof CypressFX3)) {
			// Send biases to chip (addressed ones, AIPOT).
			if (cmd == CMD_AIPOT) {
				if (((CypressFX3) getHardwareInterface()).getPID() == DAViSFX3HardwareInterface.PID) {
					((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_CHIPBIAS,
						(short) (bytes[0] & 0xFFFF),
						ByteBuffer.wrap(Arrays.copyOfRange(bytes, 1, 3)).getShort() & 0xFFFF);
				}
				else {
					((CypressFX3) getHardwareInterface()).sendVendorRequest(VR_CHIP_BIAS, (short) (bytes[0] & 0xFFFF),
						(short) 0, Arrays.copyOfRange(bytes, 1, 3));
				}
			}

			// Send chip shift register (diagnostic).
			if (cmd == CMD_CHIP_CONFIG) {
				if (((CypressFX3) getHardwareInterface()).getPID() == DAViSFX3HardwareInterface.PID) {
					// TODO: chip config is disabled for now.
				}
				else {
					((CypressFX3) getHardwareInterface()).sendVendorRequest(VR_CHIP_DIAG, (short) 0, (short) 0, bytes);
				}
			}

			// Send FPGA shift register for configuration.
			if (cmd == CMD_CPLD_CONFIG) {
				// Break down the big shift register, and send the right configuration commands via SPI.
				ByteBuffer buf = ByteBuffer.wrap(bytes);

				// Exposure (in cycles, from us)
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 7,
					(((buf.getShort(16) & 0xFFFF) << 8) | (buf.get(18) & 0xFF)) * ADC_CLOCK_FREQ_CYCLES);

				// ColSettle
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 10,
					buf.getShort(14) & 0xFFFF);

				// RowSettle
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 11,
					buf.getShort(12) & 0xFFFF);

				// ResSettle
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 9,
					buf.getShort(10) & 0xFFFF);

				// Frame Delay (in cycles, from us)
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 8,
					(buf.getShort(8) & 0xFFFF) * ADC_CLOCK_FREQ_CYCLES);

				// IMU Run
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_IMU, (short) 0, buf.get(7) & 0x01);

				// RS/GS
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 2,
					((buf.get(7) & 0x02) != 0) ? (0) : (1));

				// IMU DLPF
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_IMU, (short) 7, buf.get(5) & 0x07);

				// IMU SampleRateDivider
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_IMU, (short) 6, buf.get(4) & 0xFF);

				// IMU Gyro Scale
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_IMU, (short) 9,
					(buf.get(3) >> 3) & 0x03);

				// IMU Accel Scale
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_IMU, (short) 8,
					(buf.get(2) >> 3) & 0x03);

				// NullSettle
				((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 12,
					buf.getShort(0) & 0xFFFF);
			}

			// Send single port changes (control signals on/off).
			if (cmd == CMD_SETBIT) {
				// runCpld
				if (index == 8) {
					((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_MUX, (short) 0, bytes[0]);
				}

				// runAdc
				if (index == 257) {
					((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_APS, (short) 0, bytes[0]);
				}

				// powerDown
				if (index == 772) {
					((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_MUX, (short) 3,
						(bytes[0] != 0) ? (0) : (1));
				}

				// nChipReset
				if (index == 776) {
					((CypressFX3) getHardwareInterface()).spiConfigSend(CypressFX3.FPGA_DVS, (short) 0, bytes[0]);
				}
			}

			// All other request types are unsupported (SCANNER, VDAC, ...).
		}
	}

	protected void sendFx2Config() {
		for (PortBit b : portBits) {
			update(b, null);
		}
	}

	/*************************** CPLD *************************/

	/**
	 * Active container for CPLD configuration, which know how to format the
	 * data for the CPLD shift register.
	 *
	 */
	protected CPLDShiftRegister cpldConfig = new CPLDShiftRegister();

	protected void sendCPLDConfig() throws HardwareInterfaceException {
		if (cpldConfig.getShiftRegisterLength() > 0) {
			byte[] bytes = cpldConfig.getBytes();
			// log.info("Send CPLD Config: "+cpldConfig.toString());
			sendFx2ConfigCommand(CMD_CPLD_CONFIG, 0, bytes);
		}
	}

	/*************************** DAC *************************/

	protected DACchannelArray dacChannels = new DACchannelArray(this);
	protected DAC dac = null;

	protected void setDAC(DAC dac) {
		this.dac = dac;
	}

	protected void setDACchannelArray(DACchannelArray dacChannels) {
		this.dacChannels = dacChannels;
	}

	protected void addDACchannel(String s) throws ParseException {
		try {
			String d = ",";
			StringTokenizer t = new StringTokenizer(s, d);
			if (t.countTokens() != 2) {
				throw new Error("only " + t.countTokens() + " tokens in pot " + s
					+ "; use , to separate tokens for name,tooltip");
			}
			String name = t.nextToken();
			String tip = t.nextToken();

			int address = dacChannels.getNumChannels();
			dacChannels.addChannel(new DACchannel(chip, name, dac, address++, 0, 0, tip));
		}
		catch (Exception e) {
			throw new Error(e.toString());
		}
	}

	// sends VR to init DAC
	public void initDAC() throws HardwareInterfaceException {
		sendFx2ConfigCommand(CMD_INITDAC, 0, new byte[0]);
	}

	protected boolean sendDACchannel(DACchannel channel) throws HardwareInterfaceException {
		int chan = channel.getChannel();
		int value = channel.getBitValue();
		byte[] b = new byte[6]; // 2*24=48 bits
		byte msb = (byte) (0xff & ((0xf00 & value) >> 8));
		byte lsb = (byte) (0xff & value);
		byte dat1 = 0;
		byte dat2 = (byte) 0xC0;
		byte dat3 = 0;
		dat1 |= (0xff & ((chan % 16) & 0xf));
		dat2 |= ((msb & 0xf) << 2) | ((0xff & ((lsb & 0xc0) >> 6)));
		dat3 |= (0xff & ((lsb << 2)));
		if (chan < 16) { // these are first VPots in list; they need to be
							// loaded first to isSet to the second DAC in the
							// daisy chain
			b[0] = dat1;
			b[1] = dat2;
			b[2] = dat3;
			b[3] = 0;
			b[4] = 0;
			b[5] = 0;
		}
		else { // second DAC VPots, loaded second to end up at start of daisy
				// chain shift register
			b[0] = 0;
			b[1] = 0;
			b[2] = 0;
			b[3] = dat1;
			b[4] = dat2;
			b[5] = dat3;
		}
		sendFx2ConfigCommand(CMD_VDAC, 0, b); // value=CMD_VDAC, index=0, bytes
												// as above
		return true;
	}

	public boolean sendDACconfig() {
		// log.info("Send DAC Config");

		if ((dac == null) || (dacChannels == null)) {
			return false;
		}
		Iterator i = dacChannels.getChannelIterator();
		while (i.hasNext()) {
			DACchannel iPot = (DACchannel) i.next();
			try {
				if (!sendDACchannel(iPot)) {
					return false;
				}
			}
			catch (HardwareInterfaceException ex) {
				Logger.getLogger(LatticeLogicConfig.class.getName()).log(Level.SEVERE, null, ex);
				return false;
			}
		}
		return true;
	}

	/*************************** On Chip config *************************/

	/**
	 * Quick addConfigValue of an addressed pot from a string description, comma
	 * delimited
	 *
	 * @param s
	 *            , e.g. "Amp,n,normal,DVS ON threshold"; separate tokens for
	 *            name,sex,type,tooltip\nsex=n|p, type=normal|cascode
	 * @throws ParseException
	 *             Error
	 */
	protected AddressedIPotCF addAIPot(String s) throws ParseException {
		AddressedIPotCF ret = null;
		try {
			String d = ",";
			StringTokenizer t = new StringTokenizer(s, d);
			if (t.countTokens() != 4) {
				throw new Error("only " + t.countTokens() + " tokens in pot " + s
					+ "; use , to separate tokens for name,sex,type,tooltip\nsex=n|p, type=normal|cascode");
			}
			String name = t.nextToken();
			String a;
			a = t.nextToken();
			Pot.Sex sex = null;
			if (a.equalsIgnoreCase("n")) {
				sex = Pot.Sex.N;
			}
			else if (a.equalsIgnoreCase("p")) {
				sex = Pot.Sex.P;
			}
			else {
				throw new ParseException(s, s.lastIndexOf(a));
			}

			a = t.nextToken();
			Pot.Type type = null;
			if (a.equalsIgnoreCase("normal")) {
				type = Pot.Type.NORMAL;
			}
			else if (a.equalsIgnoreCase("cascode")) {
				type = Pot.Type.CASCODE;
			}
			else {
				throw new ParseException(s, s.lastIndexOf(a));
			}
			String tip = t.nextToken();

			int address = getPotArray().getNumPots();
			getPotArray().addPot(
				ret = new AddressedIPotCF(this, name, address++, type, sex, false, true,
					AddressedIPotCF.maxCoarseBitValue / 2, AddressedIPotCF.maxFineBitValue, address, tip));
		}
		catch (Exception e) {
			throw new Error(e.toString());
		}
		return ret;
	}

	protected boolean sendAIPot(AddressedIPot pot) throws HardwareInterfaceException {
		byte[] bytes = pot.getBinaryRepresentation();

		if ((getHardwareInterface() != null)
			&& (getHardwareInterface() instanceof net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3)
			&& (pot instanceof AddressedIPotCF)) {
			bytes = ((AddressedIPotCF) pot).getCleanBinaryRepresentation();
		}

		if (bytes == null) {
			return false; // not ready yet, called by super
		}

		String hex = String.format("%02X%02X%02X", bytes[2], bytes[1], bytes[0]);
		// log.info("Send AIPot for "+pot.getName()+" with value "+hex);
		sendFx2ConfigCommand(CMD_AIPOT, 0, bytes); // the usual packing of ipots
													// with other such as
													// shifted sources, on-chip
													// voltage dac, and
													// diagnotic mux output and
													// extra configuration
		return true;
	}

	/**
	 * Sends everything on the on-chip shift register
	 *
	 * @throws HardwareInterfaceException
	 * @return false if not sent because bytes are not yet initialized
	 */
	protected boolean sendOnChipConfig() throws HardwareInterfaceException {
		log.info("Send whole OnChip Config");

		// biases
		if (getPotArray() == null) {
			return false;
		}
		AddressedIPotArray ipots = (AddressedIPotArray) potArray;
		Iterator i = ipots.getShiftRegisterIterator();
		while (i.hasNext()) {
			AddressedIPot iPot = (AddressedIPot) i.next();
			if (!sendAIPot(iPot)) {
				return false;
			}
		}

		// shifted sources
		for (ShiftedSourceBiasCF ss : ssBiases) {
			if (!sendAIPot(ss)) {
				return false;
			}
		}

		// diagnose SR
		sendOnChipConfigChain();
		return true;
	}

	public boolean sendOnChipConfigChain() throws HardwareInterfaceException {
		String onChipConfigBits = getChipConfigChain().getBitString();
		byte[] onChipConfigBytes = bitString2Bytes(onChipConfigBits);
		if (onChipConfigBits == null) {
			return false;
		}

		// log.info("Send on chip config: " + onChipConfigBits);
		sendFx2ConfigCommand(CMD_CHIP_CONFIG, 0, onChipConfigBytes);
		return true;
	}

	/*************************** General *************************/

	/**
	 * List of configuration values
	 *
	 */
	protected ArrayList<AbstractConfigValue> configValues = new ArrayList<AbstractConfigValue>();

	/**
	 * Adds a value, adding it to the appropriate internal containers, and
	 * adding this as an observer of the value.
	 *
	 * @param value
	 *            some configuration value
	 */
	public void addConfigValue(AbstractConfigValue value) {
		if (value == null) {
			return;
		}
		configValues.add(value);
		value.addToPreferenceList(getHasPreferenceList());
		if (value instanceof CPLDConfigValue) {
			cpldConfig.add((CPLDConfigValue) value);
		}
		else if (value instanceof PortBit) {
			portBits.add((PortBit) value);
		}
		value.addObserver(this);
		// log.info("Added " + value);
	}

	/**
	 * Clears all lists of configuration values.
	 *
	 * @see AbstractConfigValue
	 *
	 */
	public void clearConfigValues() {
		cpldConfig.clear();
		configValues.clear();
		portBits.clear();
	}

	/**
	 * Sends complete configuration to hardware by calling several updates with
	 * objects
	 *
	 * @param biasgen
	 *            this object
	 * @throws HardwareInterfaceException
	 *             on some error
	 */
	@Override
	public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
		if (isBatchEditOccurring()) {
			// log.info("batch edit occurring, not sending configuration yet");
			return;
		}

		// log.info("sending full configuration");
		if (!sendOnChipConfig()) {
			return;
		}

		sendFx2Config();
		sendDACconfig();
		sendCPLDConfig();
	}

	@Override
	public void loadPreferences() {
		loadPreference();
	}

	/**
     *
     */
	@Override
	public void loadPreference() {
		super.loadPreferences();

		if (getHasPreferenceList() != null) {
			for (HasPreference hp : getHasPreferenceList()) {
				hp.loadPreference();
			}
		}

		if (ssBiases != null) {
			for (ShiftedSourceBiasCF ss : ssBiases) {
				ss.loadPreferences();
			}
		}

		if (dacChannels != null) {
			for (DACchannel channel : dacChannels.getChannels()) {
				channel.loadPreferences();
			}
		}
	}

	@Override
	public void storePreferences() {
		storePreference();
	}

	/**
     *
     */
	@Override
	public void storePreference() {
		super.storePreferences();
		for (HasPreference hp : getHasPreferenceList()) {
			hp.storePreference();
		}
		if (ssBiases != null) {
			for (ShiftedSourceBiasCF ss : ssBiases) {
				ss.storePreferences();
			}
		}
		if (dacChannels != null) {
			for (DACchannel channel : dacChannels.getChannels()) {
				channel.storePreferences();
			}
		}
	}

}

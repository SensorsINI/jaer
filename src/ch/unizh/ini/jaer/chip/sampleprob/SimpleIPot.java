package ch.unizh.ini.jaer.chip.sampleprob;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.PotGUIControl;

public class SimpleIPot extends IPot {
	protected SimpleIPot(final Biasgen biasgen) {
		super(biasgen);
	}

	public SimpleIPot(final Biasgen biasgen, final String name, final int shiftRegisterNumber, final Type type, final Sex sex,
		final int bitValue, final int displayPosition, final String tooltipString) {
		this(biasgen);
		setName(name);
		setType(type);
		setSex(sex);
		this.bitValue = bitValue;
		this.displayPosition = displayPosition;
		this.tooltipString = tooltipString;
		this.shiftRegisterNumber = shiftRegisterNumber;
		loadPreferences(); // do this after name is set
		if (chip.getRemoteControl() != null) {
			chip.getRemoteControl().addCommandListener(this, String.format("seti_%s bitvalue", getName()),
				"Set the bitValue of IPot " + getName());
		}
	}

	@Override
	public JComponent makeGUIPotControl() {
		return new SimpleIPotGUIControl(this);
	}

	private class SimpleIPotGUIControl extends JPanel {

		/**
		 *
		 */
		private static final long serialVersionUID = -1233363354588859515L;
		private SimpleIPotSliderTextControl sliderTextControl = null;
		private PotGUIControl generalControls = null;
		private final IPot pot;

		/**
		 * Creates a new instance of IPotGUIControl
		 *
		 * @param pot
		 *            the IPot to control
		 */
		public SimpleIPotGUIControl(final IPot pot) {
			this.pot = pot;
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			getInsets().set(0, 0, 0, 0);
			generalControls = new PotGUIControl(pot);
			sliderTextControl = new SimpleIPotSliderTextControl(pot);
			generalControls.getSliderAndValuePanel().add(sliderTextControl);
			add(generalControls);
			revalidate();
		}
	}
}

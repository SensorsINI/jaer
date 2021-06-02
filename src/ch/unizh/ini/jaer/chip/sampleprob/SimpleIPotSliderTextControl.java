/*
 * IPotSliderTextControl.java
 *
 * Created on September 21, 2005, 12:23 PM
 */
package ch.unizh.ini.jaer.chip.sampleprob;

import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;

import net.sf.jaer.biasgen.IPot;

/**
 * A GUI control component for controlling an IPot.
 * It provides a slider and text field for entry of the IPot current. It is installed in the GUIPotControl.
 *
 * @author tobi
 */
public class SimpleIPotSliderTextControl extends JPanel implements Observer, StateEditable {
	/**
	 *
	 */
	private static final long serialVersionUID = -5114291912128553860L;
	// the IPot is the master; it is an Observable that notifies Observers when its value changes.
	// thus if the slider changes the pot value, the pot calls us back here to update the appearance of the slider and
	// of the
	// text field. likewise, if code changes the pot, the appearance here will automagically be updated.
	static Preferences prefs = Preferences.userNodeForPackage(SimpleIPotSliderTextControl.class);
	static Logger log = Logger.getLogger("IPotSliderTextControl");
	IPot pot;
	StateEdit edit = null;
	UndoableEditSupport editSupport = new UndoableEditSupport();
	private boolean addedUndoListener = false;
	private long lastMouseWheelMovementTime = 0;
	private final long minDtMsForWheelEditPost = 500;

	// see java tuturial http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
	// and http://java.sun.com/docs/books/tutorial/uiswing/components/formattedtextfield.html
	/**
	 * Creates new form IPotSliderTextControl for a pot and BiasgenFrame
	 *
	 * @param pot
	 *            the pot
	 */
	public SimpleIPotSliderTextControl(final IPot pot) {
		this.pot = pot;
		initComponents(); // this has unfortunate byproduect of resetting pot value to 0... don't know how to prevent
							// stateChanged event
		getInsets().set(0, 0, 0, 0);
		if (pot != null) {
			slider.setVisible(true); // we don't use it now
			slider.setMaximum(16777215); // to anable mouse control by pressing to right or left of slider control
			// slider.setMaximum(pot.getMaxBitValue());
			// slider.setMinorTickSpacing(slider.getMaximum()/100);
			slider.setMinimum(0);
			slider.setToolTipText(pot.getTooltipString());
			// slider.setValue(sliderValueFromBitValue(slider));
			pot.loadPreferences(); // to get around slider value change
			pot.addObserver(this); // when pot changes, so does this gui control view
		}
		updateAppearance(); // set controls up with values from ipot
		// if (frame != null) {
		// editSupport.addUndoableEditListener(frame);
		// } else {
		// log.warning("tried to add null BiasgenFrame for undo support - undos not supported");
		// }
		SimpleIPotSliderTextControl.allInstances.add(this);
	}

	@Override
	public String toString() {
		return "IPotGUIControl for pot " + pot.getName();
	}

	private void rr() {
		revalidate();
		repaint();
	}

	/**
	 * updates the GUI slider and text
	 * fields to match actual pot values. Neither of these trigger events.
	 */
	protected void updateAppearance() {
		if (pot == null) {
			return;
		}
		if (slider.isVisible() != SimpleIPotSliderTextControl.sliderEnabled) {
			slider.setVisible(SimpleIPotSliderTextControl.sliderEnabled);
			rr();
		}
		if (valueTextField.isVisible() != SimpleIPotSliderTextControl.valueEnabled) {
			valueTextField.setVisible(SimpleIPotSliderTextControl.valueEnabled);
			rr();
		}

		slider.setValue(bitValueFromSliderValue(slider));
		valueTextField.setText(Integer.toString(pot.getBitValue()));
		valueBitLabel.setText(String.format("%24s", Integer.toBinaryString(pot.getBitValue())));
	}

	// following two methods compute slider/bit value inverses
	private int sliderValueFromBitValue(final JSlider s) {
		return s.getValue();
	}

	private int bitValueFromSliderValue(final JSlider s) {
		return pot.getBitValue();
	}

	/** called when Observable changes (pot changes) */
	@Override
	public void update(final Observable observable, final Object obj) {
		if (observable instanceof IPot) {
			// log.info("observable="+observable);
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					// don't do the following - it sometimes prevents display updates or results in double updates
					// slider.setValueIsAdjusting(true); // try to prevent a new event from the slider
					updateAppearance();
				}
			});
		}
	}

	/**
	 * This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
	// <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents() {

		slider = new javax.swing.JSlider();
		valueTextField = new javax.swing.JTextField();
		valueBitLabel = new javax.swing.JLabel();
		jPanel2 = new javax.swing.JPanel();

		setFocusable(false);
		setPreferredSize(new java.awt.Dimension(200, 15));
		addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(final java.awt.event.MouseEvent evt) {
				formMouseEntered(evt);
			}

			@Override
			public void mouseExited(final java.awt.event.MouseEvent evt) {
				formMouseExited(evt);
			}
		});
		addAncestorListener(new javax.swing.event.AncestorListener() {
			@Override
			public void ancestorMoved(final javax.swing.event.AncestorEvent evt) {
			}

			@Override
			public void ancestorAdded(final javax.swing.event.AncestorEvent evt) {
				formAncestorAdded(evt);
			}

			@Override
			public void ancestorRemoved(final javax.swing.event.AncestorEvent evt) {
			}
		});
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS));

		slider.setMajorTickSpacing(100);
		slider.setMinorTickSpacing(10);
		slider.setToolTipText("");
		slider.setValue(0);
		slider.setAlignmentX(0.0F);
		slider.setMaximumSize(new java.awt.Dimension(32767, 50));
		slider.setMinimumSize(new java.awt.Dimension(36, 10));
		slider.setPreferredSize(new java.awt.Dimension(250, 15));
		slider.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(final java.awt.event.MouseEvent evt) {
				sliderMousePressed(evt);
			}

			@Override
			public void mouseReleased(final java.awt.event.MouseEvent evt) {
				sliderMouseReleased(evt);
			}
		});
		slider.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(final javax.swing.event.ChangeEvent evt) {
				sliderStateChanged(evt);
			}
		});
		add(slider);

		valueTextField.setColumns(8);
		valueTextField.setFont(new java.awt.Font("Courier New", 0, 11));
		valueTextField.setHorizontalAlignment(SwingConstants.TRAILING);
		valueTextField.setText("value");
		valueTextField.setToolTipText("Enter bias current here. Up and Down arrows change values.");
		valueTextField.setMaximumSize(new java.awt.Dimension(100, 2147483647));
		valueTextField.setMinimumSize(new java.awt.Dimension(65, 15));
		valueTextField.setPreferredSize(new java.awt.Dimension(70, 15));
		valueTextField.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent evt) {
				valueTextFieldActionPerformed(evt);
			}
		});
		valueTextField.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusGained(final java.awt.event.FocusEvent evt) {
				valueTextFieldFocusGained(evt);
			}

			@Override
			public void focusLost(final java.awt.event.FocusEvent evt) {
				valueTextFieldFocusLost(evt);
			}
		});
		valueTextField.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(final java.awt.event.KeyEvent evt) {
				valueTextFieldKeyPressed(evt);
			}
		});
		valueTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
			@Override
			public void mouseWheelMoved(final java.awt.event.MouseWheelEvent evt) {
				valueTextFieldMouseWheelMoved(evt);
			}
		});
		add(valueTextField);

		valueBitLabel.setFont(new java.awt.Font("Courier New", 0, 11));
		valueBitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		valueBitLabel.setText("                        ");
		valueBitLabel.setMaximumSize(new java.awt.Dimension(300, 2147483647));
		valueBitLabel.setMinimumSize(new java.awt.Dimension(100, 15));
		valueBitLabel.setPreferredSize(new java.awt.Dimension(200, 15));
		add(valueBitLabel);

		jPanel2.setFocusable(false);
		jPanel2.setMaximumSize(new java.awt.Dimension(0, 32767));
		jPanel2.setMinimumSize(new java.awt.Dimension(0, 20));
		jPanel2.setPreferredSize(new java.awt.Dimension(0, 20));
		jPanel2.setRequestFocusEnabled(false);
		add(jPanel2);
	}// </editor-fold>//GEN-END:initComponents

	private void valueTextFieldFocusLost(final java.awt.event.FocusEvent evt) {// GEN-FIRST:event_valueTextFieldFocusLost
		valueTextField.setFont(new java.awt.Font("Courier New", 0, 11));
	}// GEN-LAST:event_valueTextFieldFocusLost

	private void valueTextFieldFocusGained(final java.awt.event.FocusEvent evt) {// GEN-FIRST:event_valueTextFieldFocusGained
		valueTextField.setFont(new java.awt.Font("Courier New", 1, 11));
	}// GEN-LAST:event_valueTextFieldFocusGained

	Border selectedBorder = new EtchedBorder(), unselectedBorder = new EmptyBorder(1, 1, 1, 1);

	private void formMouseExited(final java.awt.event.MouseEvent evt) {// GEN-FIRST:event_formMouseExited
	}// GEN-LAST:event_formMouseExited

	private void formMouseEntered(final java.awt.event.MouseEvent evt) {// GEN-FIRST:event_formMouseEntered
		// setBorder(selectedBorder);
	}// GEN-LAST:event_formMouseEntered

	private void sliderMouseReleased(final java.awt.event.MouseEvent evt) {// GEN-FIRST:event_sliderMouseReleased
		endEdit();
	}// GEN-LAST:event_sliderMouseReleased

	private void sliderMousePressed(final java.awt.event.MouseEvent evt) {// GEN-FIRST:event_sliderMousePressed
		startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
	}// GEN-LAST:event_sliderMousePressed

	private void valueTextFieldMouseWheelMoved(final java.awt.event.MouseWheelEvent evt) {// GEN-FIRST:event_valueTextFieldMouseWheelMoved
		final int clicks = evt.getWheelRotation();
		final float ratio = (1 - (clicks * .1f));
		// System.out.println("ratio="+ratio);
		startEdit();
		pot.changeByRatio(ratio);
		final long t = System.currentTimeMillis();
		if ((t - lastMouseWheelMovementTime) > minDtMsForWheelEditPost) {
			endEdit();
		}
		lastMouseWheelMovementTime = t;
	}// GEN-LAST:event_valueTextFieldMouseWheelMoved

	private void valueTextFieldKeyPressed(final java.awt.event.KeyEvent evt) {// GEN-FIRST:event_valueTextFieldKeyPressed
		// key pressed in text field
		// System.out.println("keyPressed evt "+evt);
		// System.out.println("value field key pressed");
		endEdit();
		// String s = evt.getKeyText(evt.getKeyCode());
		final int code = evt.getKeyCode();
		final boolean shift = evt.isShiftDown();
		float byRatio = 1.1f;
		if (shift) {
			byRatio = 10f;
		}
		if (code == KeyEvent.VK_UP) {
			startEdit();
			pot.changeByRatio(byRatio);
			endEdit();
		}
		else if (code == KeyEvent.VK_DOWN) {
			startEdit();
			pot.changeByRatio(1f / byRatio);
			endEdit();
		}
	}// GEN-LAST:event_valueTextFieldKeyPressed

	private void valueTextFieldActionPerformed(final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_valueTextFieldActionPerformed
		// new pots current value entered
		// System.out.println("value field action performed");
		try {
			startEdit();
			pot.setBitValue(Integer.parseInt(valueTextField.getText()));
			endEdit();
		}
		catch (final NumberFormatException e) {
			Toolkit.getDefaultToolkit().beep();
			valueTextField.selectAll();
		}
	}// GEN-LAST:event_valueTextFieldActionPerformed

	/**
	 * when slider is moved, event is sent here. The slider is the 'master' of the value in the text field.
	 * Slider is log scale, from pot min to pot max with caveat that zero position is zero current (no current splitter
	 * outputs switched on) and rest of values are log scale from pot.getCurrentResolution to pot.getMaxCurrent
	 *
	 * @param e
	 *            the ChangeEvent
	 */
	private void sliderStateChanged(final javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_sliderStateChanged
		// we can get a double send here if user presses uparrow key,
		// resulting in new pot value,
		// which updates the slider position, which ends up with
		// a different bitvalue that makes a new
		// pot value.
		// See http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
		// System.out.println("slider state changed");
		// slider is only source of ChangeEvents
		final JSlider s = (JSlider) evt.getSource();
		// System.out.println("slider state changed for "+pot);

		// if(!s.getValueIsAdjusting()){
		// startEdit();
		// }
		int v = s.getValue();
		// System.out.println("v="+v+" "+evt.toString());
		if (v == 0) {
			pot.setBitValue(0); // these pot chanages will come back to us as Observer events
			// a problem because they will updateAappearance, which will change slider state
			// and generate possibly a new slider changeevent
		}
		else {
			v = sliderValueFromBitValue(s);
			pot.setBitValue(v);
		}
		// if(!s.getValueIsAdjusting()){
		//// System.out.println("slider done");
		// endEdit();
		// }
	}// GEN-LAST:event_sliderStateChanged

	// march up till we reach the Container that handles undos
	private void formAncestorAdded(final javax.swing.event.AncestorEvent evt) {// GEN-FIRST:event_formAncestorAdded
		if (addedUndoListener) {
			return;
		}
		addedUndoListener = true;
		if (evt.getComponent() instanceof Container) {
			Container anc = evt.getComponent();
			while ((anc != null) && (anc instanceof Container)) {
				if (anc instanceof UndoableEditListener) {
					editSupport.addUndoableEditListener((UndoableEditListener) anc);
					break;
				}
				anc = anc.getParent();
			}
		}
	}// GEN-LAST:event_formAncestorAdded

	int oldPotValue = 0;

	void startEdit() {
		if (edit != null) {
			return;
		}
		// System.out.println("ipot start edit "+pot);
		edit = new MyStateEdit(this, "pot change");
		oldPotValue = pot.getBitValue();
	}

	void endEdit() {
		if (oldPotValue == pot.getBitValue()) {
			// System.out.println("no edit, because no change in "+pot);
			return;
		}
		// System.out.println("ipot endEdit "+pot);
		if (edit != null) {
			edit.end();
			// System.out.println("ipot "+pot+" postEdit");
			editSupport.postEdit(edit);
			edit = null;
		}
	}

	String STATE_KEY = "pot state";

	@Override
	public void restoreState(final Hashtable<?, ?> hashtable) {
		// System.out.println("restore state");
		if (hashtable == null) {
			throw new RuntimeException("null hashtable");
		}
		if (hashtable.get(STATE_KEY) == null) {
			System.err.println("pot " + pot + " not in hashtable " + hashtable + " with size=" + hashtable.size());
			// Set s=hashtable.entrySet();
			// System.out.println("hashtable entries");
			// for(Iterator i=s.iterator();i.hasNext();){
			// Map.Entry me=(Map.Entry)i.next();
			// System.out.println(me);
			// }
			return;
		}
		final int v = (Integer) hashtable.get(STATE_KEY);
		pot.setBitValue(v);
	}

	@Override
	public void storeState(final Hashtable<Object, Object> hashtable) {
		// System.out.println(" storeState "+pot);
		hashtable.put(STATE_KEY, new Integer(pot.getBitValue()));
	}

	class MyStateEdit extends StateEdit {
		/**
		 *
		 */
		private static final long serialVersionUID = -4321012835355063717L;

		public MyStateEdit(final StateEditable o, final String s) {
			super(o, s);
		}

		@Override
		protected void removeRedundantState() {
		}; // override this to actually get a state stored!!
	}

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JPanel jPanel2;
	private javax.swing.JSlider slider;
	private javax.swing.JTextField valueTextField;
	private javax.swing.JLabel valueBitLabel;
	// End of variables declaration//GEN-END:variables

	public JSlider getSlider() {
		return slider;
	}

	public JTextField getValueTextField() {
		return valueTextField;
	}

	public static boolean isCurrentEnabled() {
		return SimpleIPotSliderTextControl.valueEnabled;
	}

	public static void setCurrentEnabled(final boolean currentEnabled) {
		SimpleIPotSliderTextControl.valueEnabled = currentEnabled;
		SimpleIPotSliderTextControl.prefs.putBoolean("IPotGUIControl.currentEnabled", currentEnabled);
	}

	public static boolean isSliderEnabled() {
		return SimpleIPotSliderTextControl.sliderEnabled;
	}

	public static void setSliderEnabled(final boolean sliderEnabled) {
		SimpleIPotSliderTextControl.sliderEnabled = sliderEnabled;
		SimpleIPotSliderTextControl.prefs.putBoolean("IPotGUIControl.sliderEnabled", sliderEnabled);
	}

	static ArrayList<SimpleIPotSliderTextControl> allInstances = new ArrayList<SimpleIPotSliderTextControl>();

	public static void revalidateAllInstances() {
		for (final SimpleIPotSliderTextControl c : SimpleIPotSliderTextControl.allInstances) {
			// System.out.println(c);
			c.updateAppearance();

			c.revalidate();
		}
	}

	public static boolean sliderEnabled = SimpleIPotSliderTextControl.prefs.getBoolean("IPotGUIControl.sliderEnabled", true);
	public static boolean valueEnabled = SimpleIPotSliderTextControl.prefs.getBoolean("IPotGUIControl.currentEnabled", true);

	static String[] controlNames = { "Type", "Sex", "Slider", "BitValue", "BitView" };

}

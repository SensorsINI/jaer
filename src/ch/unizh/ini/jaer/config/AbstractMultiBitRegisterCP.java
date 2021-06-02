/**
 * @author Ilya Kiselev, ilya.fpga@gmail.com
 * 
 * Created on June 28, 2016, 14:08
 */

package ch.unizh.ini.jaer.config;

import java.awt.Color;
import static java.awt.Component.LEFT_ALIGNMENT;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelListener;
import java.util.Hashtable;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;

/**
 * Abstract class to build a control panel either for a single channel of cochlea,
 * or a set of such values. It enables key and mouse listeners in text fields,
 * and provide undo support
 */
public abstract class AbstractMultiBitRegisterCP extends JPanel implements StateEditable {

	private static final int TF_MAX_HEIGHT = 15;
	private static final int TF_HEIGHT = 6;
	private static final int TF_MIN_W = 15, TF_PREF_W = 20, TF_MAX_W = 40;
	private static final Dimension tfMinimumSize = new Dimension(TF_MIN_W, TF_HEIGHT);
	private static final Dimension tfPreferredSize = new Dimension(TF_PREF_W, TF_HEIGHT);
	private static final Dimension tfMaxSize = new Dimension(TF_MAX_W, TF_MAX_HEIGHT);
	private static final Logger log = Logger.getLogger("MultiBitRegisterCP");

	private final JComponent[] controls;
	protected final MultiBitConfigRegister reg;
	protected final JLabel label = new JLabel();
	private StateEdit edit = null;
	private final UndoableEditSupport editSupport = new UndoableEditSupport();
	private boolean addedUndoListener = false;
	/**
	 * Construct control for one channel
	 *
	 * @param reg,
	 *     the multi-bit config register
	 * @param actionsCollection
	 *     Collection of Arrays of action listeners for MultiBitConfigRegister components
	 */
	
	public AbstractMultiBitRegisterCP(final MultiBitConfigRegister reg, final ActionsCollection actionsCollection) {

		if (reg == null) throw new IllegalArgumentException("Attempted to create a control panel for a non-existent register.");
		if (actionsCollection == null) throw new IllegalArgumentException("Attempted to create a control panel with not defined action listeners.");
		final int nFields = reg.fieldsNumber();
		boolean parametersMatch = (nFields == actionsCollection.ACTIONS.length);
		
		for (int i = 0; parametersMatch && (i < nFields); ++i) {
			parametersMatch = (actionsCollection.ACTIONS[i] instanceof SingleBitAction) && (reg.fieldConfig(i).length == 1);
			parametersMatch |= (actionsCollection.ACTIONS[i] instanceof MultiBitActions) && (reg.fieldConfig(i).length > 1);
		}
		if (!parametersMatch) throw new IllegalArgumentException("Configurations of the register (" + reg + ") and the actions listeners (" 
			+ actionsCollection + ") do not match.");
		
		this.reg = reg;
		controls = new JComponent[nFields];

		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

		add(label);

		for (int i = 0; i < nFields; ++i) {
			if (reg.fieldConfig(i).length == 1) {
				JRadioButton but = new JRadioButton();
				// TODO: Make sure it works for the group control
				but.setToolTipText(reg.fieldConfig(i).getToolTip());
				but.setSelected(reg.getPartialValue(i) != 0);
				but.setAlignmentX(LEFT_ALIGNMENT);
				but.addActionListener(actionsCollection.ACTIONS[i]);
				controls[i] = but;
				add(but);
			} else {
				JTextField tf = new JTextField();
				// TODO: Make sure it works for the group control
				tf.setToolTipText(reg.fieldConfig(i).getToolTip());
				tf.setText(Integer.toString(reg.getPartialValue(i)));
				tf.setMinimumSize(tfMinimumSize);
				tf.setPreferredSize(tfPreferredSize);
				tf.setMaximumSize(tfMaxSize);
				tf.addActionListener(actionsCollection.ACTIONS[i]);
				tf.addFocusListener((FocusListener) actionsCollection.ACTIONS[i]);
				tf.addKeyListener((KeyListener) actionsCollection.ACTIONS[i]);
				tf.addMouseWheelListener((MouseWheelListener) actionsCollection.ACTIONS[i]);
				controls[i] = tf;
				add(tf);
			}
		}

		reg.setControlPanel(this);

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
	}
	
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

	public void updateGUI() {
		for (int i = 0; i < reg.fieldsNumber(); ++i) {
			// if (reg.fieldNeedsUpdate(i))
			if (controls[i] instanceof JRadioButton) {
				((JRadioButton) controls[i]).setSelected(reg.getPartialValue(i) != 0);
			}
			else {
				((JTextField) controls[i]).setText(Integer.toString(reg.getPartialValue(i)));
			}
		}
	}

	protected static class ActionsCollection {
		public final AbstractAction[] ACTIONS;

		public ActionsCollection(final int[] fieldsLengths) {
			final int N_FIELDS = fieldsLengths.length;
			ACTIONS = new AbstractAction[N_FIELDS];

			for (int i = 0; i < N_FIELDS; ++i) {
				ACTIONS[i] = (fieldsLengths[i] == 1) ? new SingleBitAction(i) : new MultiBitActions(i);
			}
		}
	}

	protected static abstract class AbstractAction implements ActionListener {
		protected final int componentID;

		public AbstractAction(final int component) {
			componentID = component;
		}
	}
	
	protected static class SingleBitAction extends AbstractAction {

		public SingleBitAction(final int component) {
			super(component);
		}

		public void actionPerformed(final ActionEvent e) {
			final JRadioButton button = (JRadioButton) e.getSource();
			final AbstractMultiBitRegisterCP embeddingCP = (AbstractMultiBitRegisterCP) button.getParent();
			embeddingCP.reg.setPartialValue(componentID, button.isSelected() ? 1 : 0);
			embeddingCP.reg.setFileModified();
		}
	}

	protected static class MultiBitActions extends AbstractAction implements FocusListener, KeyListener, MouseWheelListener {
	
		private static int clicksSum = 0;
		private static long lastMouseWheelMovementTime = 0;
		private final long minDtMsForWheelEditPost = 500;

		public MultiBitActions(final int component) {
			super(component);
		}

		private void setValueFromGUI(final JTextField tf) {
			final AbstractMultiBitRegisterCP embeddingCP = (AbstractMultiBitRegisterCP) tf.getParent();

			embeddingCP.startEdit();
			try {
				embeddingCP.reg.setPartialValue(componentID, Integer.parseInt(tf.getText()));
				embeddingCP.reg.setFileModified();
				tf.setBackground(Color.white);
			}
			catch (final Exception ex) {
				tf.selectAll();
				tf.setBackground(Color.red);
				log.warning(ex.toString());
			}
			finally {
				embeddingCP.endEdit();
			}
		}

		private void incValueAndUpdateGUI(int inc, final JTextField tf) {
			final AbstractMultiBitRegisterCP embeddingCP = (AbstractMultiBitRegisterCP) tf.getParent();

			embeddingCP.startEdit();
			try {
				embeddingCP.reg.setPartialValue(componentID, embeddingCP.reg.getPartialValue(componentID) + inc);
				embeddingCP.reg.setFileModified();
				tf.setBackground(Color.white);
			}
			catch (final Exception ex) {
				tf.selectAll();
				tf.setBackground(Color.red);
				log.warning(ex.toString());
			}
			finally {
				embeddingCP.endEdit();
			}
		}
		
		// ActionListener interface
		@Override
		public void actionPerformed(final ActionEvent e) {
			setValueFromGUI((JTextField) e.getSource());
		}

		// FocusListener interface
		@Override
		public void focusGained(final FocusEvent e) {}

		@Override
		public void focusLost(final FocusEvent e) {
			setValueFromGUI((JTextField) e.getSource());
		}

		// KeyListener interface
		@Override
		public void keyTyped(KeyEvent e) {}

		@Override
		public void keyPressed(final KeyEvent e) {
			final boolean up = (e.getKeyCode() == KeyEvent.VK_UP);
			final boolean down = (e.getKeyCode() == KeyEvent.VK_DOWN);

			if (up || down) {
				incValueAndUpdateGUI(up ? 1 : -1, (JTextField) e.getSource());
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {}

		// MouseWheelListener interface
		@Override
		public void mouseWheelMoved(final java.awt.event.MouseWheelEvent e) {
			incValueAndUpdateGUI(-e.getWheelRotation(), (JTextField) e.getSource());
		}
	}

	private int oldValue = 0;

	private void startEdit() {
		if (edit == null) {
			edit = new MyStateEdit(this, "pot change");
			oldValue = reg.getFullValue();
		}
	}

	private void endEdit() {
		if ((reg.getFullValue() != oldValue) && (edit != null)) {
			edit.end();
			editSupport.postEdit(edit);
			edit = null;
		}
	}

	String STATE_KEY = "cochlea channel state";

	@Override
	public void storeState(final Hashtable<Object, Object> hashtable) {
		// System.out.println(" storeState "+pot);
		if (reg != null) {
			hashtable.put(STATE_KEY, new Integer(reg.getFullValue()));
		}
	}

	@Override
	public void restoreState(final Hashtable<?, ?> hashtable) {
		// System.out.println("restore state");
		if (hashtable == null) {
			throw new RuntimeException("null hashtable; can't restore state");
		}
		if (hashtable.get(STATE_KEY) == null) {
			log.warning("channel " + reg + " not in hashtable " + hashtable + " with size=" + hashtable.size());
			return;
		}
		final int v = (Integer) hashtable.get(STATE_KEY);
		reg.setFullValue(v);
	}

	private class MyStateEdit extends StateEdit {

		private static final long serialVersionUID = -4321012835355063717L;

		public MyStateEdit(final StateEditable o, final String s) {
			super(o, s);
		}

		@Override
		protected void removeRedundantState() {
		}
	}
}

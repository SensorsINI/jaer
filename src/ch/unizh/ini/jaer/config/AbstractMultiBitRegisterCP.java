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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelListener;
import java.util.Hashtable;
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

	private final JComponent[] controls;
	protected final MultiBitConfigRegister reg;
	protected final JLabel label = new JLabel();
	private StateEdit edit = null;
	private final UndoableEditSupport editSupport = new UndoableEditSupport();
	private boolean addedUndoListener = false;
	private final AbstractChipControlPanel observingPanel;

	/**
	 * Construct control for one channel
	 *
	 * @param reg,
	 *     the multi-bit config register
	 * @param observingPanel
	 *     the panel which has the observer for the MultiBitRegister
	 * @param actionsCollection
	 *     Collection of Arrays of action listeners for MultiBitConfigRegister components
	 */
	
	public AbstractMultiBitRegisterCP(final MultiBitConfigRegister reg, final AbstractChipControlPanel observingPanel, final ActionsCollection actionsCollection) {

		if (reg == null) throw new IllegalArgumentException("Attempted to create a control panel for a non-existent register.");
		if (actionsCollection == null) throw new IllegalArgumentException("Attempted to create a control panel with not defined action listeners.");
		final int nFields = reg.fieldsNumber();
		boolean parametersMatch = (nFields == actionsCollection.SIMPLE_ACTIONS.length);
		
		for (int i = 0; parametersMatch && (i < nFields); ++i) {
			parametersMatch = (actionsCollection.SIMPLE_ACTIONS[i] instanceof SingleBitAction) && (reg.fieldConfig(i).length == 1);
			parametersMatch |= (actionsCollection.SIMPLE_ACTIONS[i] instanceof MultiBitAction) && (reg.fieldConfig(i).length > 1);
		}
		if (!parametersMatch) throw new IllegalArgumentException("Configurations of the register (" + reg + ") and the actions listeners (" 
			+ actionsCollection + ") do not match.");
		
		this.reg = reg;
		this.observingPanel = observingPanel;

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
				but.addActionListener(actionsCollection.SIMPLE_ACTIONS[i]);
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
				tf.addActionListener(actionsCollection.SIMPLE_ACTIONS[i]);
				tf.addFocusListener(actionsCollection.FOCUS_ACTIONS[i]);
				tf.addKeyListener(actionsCollection.KEY_PRESS_ACTIONS[i]);
				tf.addMouseWheelListener(actionsCollection.MOUSE_WHEEL_ACTIONS[i]);
				controls[i] = tf;
				add(tf);
			}
		}

		reg.setControlPanel(this);
		reg.addObserver(observingPanel);

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

	private static void onEnterDataAction(final JTextField tf, final int componentID) {
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
			embeddingCP.observingPanel.getLogger().warning(ex.toString());
		}
		embeddingCP.endEdit();
	}

	protected static class ActionsCollection {
		public final SimpleAction[] SIMPLE_ACTIONS;
		public final FocusAction[] FOCUS_ACTIONS;
		public final KeyPressAction[] KEY_PRESS_ACTIONS;
		public final MouseWheelAction[] MOUSE_WHEEL_ACTIONS;

		public ActionsCollection(final int[] fieldsLengths) {
			final int N_FIELDS = fieldsLengths.length;

			SIMPLE_ACTIONS = new SimpleAction[N_FIELDS];
			FOCUS_ACTIONS = new FocusAction[N_FIELDS];
			KEY_PRESS_ACTIONS = new KeyPressAction[N_FIELDS];
			MOUSE_WHEEL_ACTIONS = new MouseWheelAction[N_FIELDS];

			for (int i = 0; i < N_FIELDS; ++i) {
				if (fieldsLengths[i] == 1) {
					SIMPLE_ACTIONS[i] = new SingleBitAction(i);
				} else {
					SIMPLE_ACTIONS[i] = new MultiBitAction(i);
					FOCUS_ACTIONS[i] = new FocusAction(i);
					KEY_PRESS_ACTIONS[i] = new KeyPressAction(i);
					MOUSE_WHEEL_ACTIONS[i] = new MouseWheelAction(i);
				}
			}
		}
	}

	protected static abstract class SimpleAction implements ActionListener {
		protected final int componentID;

		public SimpleAction(final int component) {
			componentID = component;
		}
	}
	
	protected static class SingleBitAction extends SimpleAction {

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

	protected static class MultiBitAction extends SimpleAction {

		public MultiBitAction(final int component) {
			super(component);
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			onEnterDataAction((JTextField) e.getSource(), componentID);
		}
	}

	protected static class FocusAction implements FocusListener {
		private final int componentID;

		public FocusAction(final int component) {
			componentID = component;
		}

		@Override
		public void focusGained(final FocusEvent e) {}

		@Override
		public void focusLost(final FocusEvent e) {
			onEnterDataAction((JTextField) e.getSource(), componentID);
		}
	}

	protected static class KeyPressAction extends KeyAdapter {
		private final int componentID;

		public KeyPressAction(final int component) {
			componentID = component;
		}

		/**
		 * Invoked when a key has been pressed.
		 */
		public void keyPressed(final KeyEvent e) {

			if ((e.getKeyCode() == KeyEvent.VK_UP) || (e.getKeyCode() == KeyEvent.VK_DOWN)) {
		
				final JTextField tf = (JTextField) e.getSource();
				final AbstractMultiBitRegisterCP embeddingCP = (AbstractMultiBitRegisterCP) tf.getParent();

				final int inc = (e.getKeyCode() == KeyEvent.VK_UP) ? 1 : -1;
				embeddingCP.startEdit();
				try {
					embeddingCP.reg.setPartialValue(componentID, embeddingCP.reg.getPartialValue(componentID) + inc);
					embeddingCP.reg.setFileModified();
					tf.setBackground(Color.white);
				}
				catch (final Exception ex) {
					embeddingCP.observingPanel.getLogger().warning(ex.toString());
				}
				embeddingCP.endEdit();
			}
		}
	}

	protected static class MouseWheelAction implements MouseWheelListener {
		private final int componentID;
		private static long lastMouseWheelMovementTime = 0;
		private final long minDtMsForWheelEditPost = 500;

		public MouseWheelAction(final int component) {
			componentID = component;
		}

		@Override
		public void mouseWheelMoved(final java.awt.event.MouseWheelEvent evt) {
			final int clicks = evt.getWheelRotation();
			final JTextField tf = (JTextField) evt.getSource();
			final AbstractMultiBitRegisterCP embeddingCP = (AbstractMultiBitRegisterCP) tf.getParent();
			// startEdit();
			final long t = System.currentTimeMillis();
			if ((t - lastMouseWheelMovementTime) > minDtMsForWheelEditPost) {
				embeddingCP.endEdit();
			}
			lastMouseWheelMovementTime = t;

			final int inc = -clicks;
			if (clicks == 0) {
				return;
			}
			
			embeddingCP.startEdit();
			try {
				embeddingCP.reg.setPartialValue(componentID, embeddingCP.reg.getPartialValue(componentID) + inc);
				embeddingCP.reg.setFileModified();
				tf.setBackground(Color.white);
			}
			catch (final Exception ex) {
				embeddingCP.observingPanel.getLogger().warning(ex.toString());
			}
			finally {
				embeddingCP.endEdit();
			}
		}
	}

	private int oldValue = 0;

	private void startEdit() {
		if (edit != null) return;
		if (reg == null) return;

		edit = new MyStateEdit(this, "pot change");
		oldValue = reg.getFullValue();
	}

	private void endEdit() {
		if (reg == null) return;
		if (oldValue == reg.getFullValue()) return;

		if (edit != null) {
			edit.end();
			editSupport.postEdit(edit);
			edit = null;
		}
	}

	String STATE_KEY = "cochlea channel state";

	@Override
	public void storeState(final Hashtable<Object, Object> hashtable) {
		// System.out.println(" storeState "+pot);
		if (reg == null) {
			return;
		}
		hashtable.put(STATE_KEY, new Integer(reg.getFullValue()));
	}

	@Override
	public void restoreState(final Hashtable<?, ?> hashtable) {
		// System.out.println("restore state");
		if (hashtable == null) {
			throw new RuntimeException("null hashtable; can't restore state");
		}
		if (hashtable.get(STATE_KEY) == null) {
			observingPanel.getLogger().warning("channel " + reg + " not in hashtable " + hashtable + " with size=" + hashtable.size());
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

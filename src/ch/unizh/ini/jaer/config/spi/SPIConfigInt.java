package ch.unizh.ini.jaer.config.spi;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ch.unizh.ini.jaer.config.ConfigInt;
import static ch.unizh.ini.jaer.config.spi.SPIConfigBit.REMOTE_CONTROL_CMD;
import static ch.unizh.ini.jaer.config.spi.SPIConfigValue.log;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.RemoteControlCommand;

public class SPIConfigInt extends SPIConfigValue implements ConfigInt {

    private final int defaultValue;
    private int value;

    private final Biasgen biasgen;
    private final Preferences sprefs;

    public static final String REMOTE_CONTROL_CMD = "SpiConfigInt";

    public SPIConfigInt(final String configName, final String toolTip, final short moduleAddr, final short paramAddr, final int numBits,
            final int defaultValue, final Biasgen biasgen) {
        super(configName, toolTip, (AEChip) biasgen.getChip(), moduleAddr, paramAddr, numBits);

        this.defaultValue = defaultValue;

        this.biasgen = biasgen;
        sprefs = biasgen.getChip().getPrefs();

        loadPreference();
        sprefs.addPreferenceChangeListener(this);
        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, String.format(REMOTE_CONTROL_CMD + "%s <booleanValue>", getName()), "Set the integer value of " + getName());
        }
    }

    // <editor-fold defaultstate="collapsed" desc="ConfigInt interface implementation">
    @Override
    public int get() {
        return value;
    }

    @Override
    public void set(int value) {
        if ((value < 0) || (value >= (1 << getNumBits()))) {
            // Recover from out-of-range conditions automatically.
            value = defaultValue;
        }

        if (this.value != value) {
            this.value = value;
            setChanged();
            notifyObservers();
        }
    }
    // </editor-fold>

    @Override
    public String toString() {
        return String.format("SPIConfigInt {configName=%s, prefKey=%s, moduleAddr=%d, paramAddr=%d, numBits=%d, default=%d}", getName(),
                getPreferencesKey(), getModuleAddr(), getParamAddr(), getNumBits(), defaultValue);
    }

    @Override
    public void preferenceChange(final PreferenceChangeEvent e) {
        if (e.getKey().equals(getPreferencesKey())) {
            try {
                final int newVal = Integer.parseInt(e.getNewValue());
                set(newVal);
            } catch (NumberFormatException ex) {
                log.warning(String.format("Could not set new for key %s using new value %s: got %s", e.getKey(), e.getNewValue(), ex.toString()));
            }
        }
    }

    @Override
    public void loadPreference() {
        // Ensure preferences loaded from config are put back in valid ranges.
        final int prefValue = sprefs.getInt(getPreferencesKey(), defaultValue);
        set(prefValue);
    }

    @Override
    public void storePreference() {
        sprefs.putInt(getPreferencesKey(), get());
    }

    // <editor-fold defaultstate="collapsed" desc="GUI related functions">
    private static final int TF_PREF_HEIGHT = 8;
    private static final int TF_PREF_WIDTH = 40;
    private static final int TF_MAX_HEIGHT = 16;
    private static final int TF_MAX_WIDTH = 80;

    private static final Dimension prefDimensions = new Dimension(SPIConfigInt.TF_PREF_WIDTH, SPIConfigInt.TF_PREF_HEIGHT);
    private static final Dimension maxDimensions = new Dimension(SPIConfigInt.TF_MAX_WIDTH, SPIConfigInt.TF_MAX_HEIGHT);

    @Override
    public JComponent makeGUIControl() {

        final JPanel pan = new JPanel();
        pan.setAlignmentX(Component.LEFT_ALIGNMENT);
        pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));

        final JLabel label = new JLabel(getName());
        label.setToolTipText(
                "<html>" + toString() + "<br>" + getDescription() + "<br>Enter value or use mouse wheel or arrow keys to change value.");
        pan.add(label);

        final JTextField tf = new JTextField();
        tf.setText(Integer.toString(get()));
        tf.setPreferredSize(SPIConfigInt.prefDimensions);
        tf.setMaximumSize(SPIConfigInt.maxDimensions);
        final SPIConfigIntActions actionListeners = new SPIConfigIntActions(this);
        tf.addActionListener(actionListeners);
        tf.addFocusListener(actionListeners);
        tf.addKeyListener(actionListeners);
        tf.addMouseWheelListener(actionListeners);
        pan.add(tf);
        setControl(tf);
        addObserver(biasgen); // This observer is responsible for sending data to hardware
        addObserver(this); // This observer is responsible for GUI update. It calls the updateControl() method
        return pan;
    }

    @Override
    public void updateControl() {
        if (control != null) {
            ((JTextField) control).setText(Integer.toString(value));
        }
    }

    // <editor-fold defaultstate="collapsed" desc="GUI related functions">
    private static class SPIConfigIntActions extends KeyAdapter implements ActionListener, FocusListener, MouseWheelListener {

        private final SPIConfigInt intConfig;
        private final float FACTOR = (float) Math.pow(2, 0.25), SHIFTED_FACTOR = 2;

        SPIConfigIntActions(final SPIConfigInt intCfg) {
            intConfig = intCfg;
        }

        private void setValueAndUpdateGUI(final int val) {
            final JTextField tf = (JTextField) intConfig.control;
            try {
                intConfig.set(val);
                intConfig.setFileModified();
                tf.setBackground(Color.white);
            } catch (final Exception ex) {
                tf.selectAll();
                tf.setBackground(Color.red);
                SPIConfigValue.log.warning(ex.toString());
            }
        }

        @Override
        public void keyPressed(final KeyEvent e) {
            final boolean up = (e.getKeyCode() == KeyEvent.VK_UP);
            final boolean down = (e.getKeyCode() == KeyEvent.VK_DOWN);
            final boolean shifted = (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK;
            log.info(String.format("up=%s down=%s shifted=%s", up, down, shifted));
            if (up || down) {
                int sign = up ? 1 : -1;
                int oldValue = intConfig.get();
                int newValue = oldValue;
                float factor = shifted ? SHIFTED_FACTOR : FACTOR;
                if (down) {
                    factor = 1 / factor;
                }
                if (Math.abs(oldValue) < 8) {
                    newValue = oldValue + sign;
                } else {
                    newValue = (int) Math.round(newValue * factor);
                }
                setValueAndUpdateGUI(newValue);
            }
        }

        // ActionListener interface
        @Override
        public void actionPerformed(final ActionEvent e) {
            setValueAndUpdateGUI(Integer.parseInt(((JTextField) intConfig.control).getText()));
        }

        // FocusListener interface
        @Override
        public void focusGained(final FocusEvent e) {
        }

        @Override
        public void focusLost(final FocusEvent e) {
            setValueAndUpdateGUI(Integer.parseInt(((JTextField) intConfig.control).getText()));
        }

        // MouseWheelListener interface
        @Override
        public void mouseWheelMoved(final MouseWheelEvent e) {
            final int clicks = e.getWheelRotation();
            final boolean up = clicks<0;
            final boolean down = clicks>0;
            final boolean shifted = (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK;
            log.info(String.format("up=%s down=%s shifted=%s", up, down, shifted));
            if (up || down) {
                int sign = up ? 1 : -1;
                int oldValue = intConfig.get();
                int newValue = oldValue;
                float factor = shifted ? SHIFTED_FACTOR : FACTOR;
                if (down) {
                    factor = 1 / factor;
                }
                if (Math.abs(oldValue) < 8) {
                    newValue = oldValue + sign;
                } else {
                    newValue = (int) Math.round(newValue * factor);
                }
                setValueAndUpdateGUI(newValue);
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Remote control">
    /**
     * Command is e.g. "SpiConfigValue <value>".
     *
     * @param command the first token which dispatches the command here for this
     * value.
     * @param input the command string.
     * @return some informative string for debugging bad commands.
     */
    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] t = input.split("\\s");
        if (t.length < 2) {
            return "? " + this + "\n";
        } else {
            String s = t[0], a = t[1];
            try {
                set(Integer.parseInt(a));
                chip.getLog().info(getName() + ": set value=" + a);
                return this + "\n";
            } catch (NumberFormatException e) {
                chip.getLog().warning("Bad number format: " + input + " caused " + e);
                return e.toString() + "\n";
            } catch (Exception ex) {
                chip.getLog().warning(ex.toString());
                return ex.toString();
            }
        }
    }

    // </editor-fold>
}

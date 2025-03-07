package net.sf.jaer.biasgen.coarsefine;

/*
 * IPotSliderTextControl.java
 *
 * Created on September 21, 2005, 12:23 PM
 */
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;

import net.sf.jaer.biasgen.BiasgenFrame;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF.OperatingMode;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF.VoltageLevel;
import net.sf.jaer.util.EngineeringFormat;

/**
 * A GUI control component for controlling a Pot. It shows the name of the Pot,
 * its attributes and provides fields for direct bit editing of the Pot value.
 * Subclasses provide customized control of voltage or current biases via the
 * sliderAndValuePanel contents.
 *
 * @author tobi
 */
public class ShiftedSourceControlsCF extends javax.swing.JPanel implements Observer, StateEditable {
    // the IPot is the master; it is an Observable that notifies Observers when its value changes.
    // thus if the slider changes the pot value, the pot calls us back here to update the appearance of the slider and of the
    // text field. likewise, if code changes the pot, the appearance here will automagically be updated.

    static Preferences prefs;
    static Logger log = Logger.getLogger("net.sf.jaer");
    static double ln2 = Math.log(2.);
    ShiftedSourceBiasCF pot;
    StateEdit edit = null;
    UndoableEditSupport editSupport = new UndoableEditSupport();
    BiasgenFrame frame;
    public boolean sliderEnabled;
    public boolean valueEnabled;
    public boolean bitValueEnabled;
    public boolean bitViewEnabled;
    public boolean sexEnabled;
    public boolean typeEnabled;
    private boolean addedUndoListener = false;
    private boolean dontProcessRefSlider = false, dontProcessRegBiasSlider = false;

    // see java tuturial http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
    // and http://java.sun.com/docs/books/tutorial/uiswing/components/formattedtextfield.html
    /**
     * Creates new form IPotSliderTextControl
     */
    public ShiftedSourceControlsCF(ShiftedSourceBiasCF pot) {
        this.pot = pot;
        prefs = pot.getChip().getPrefs();
        sliderEnabled = prefs.getBoolean("ConfigurableIPot.sliderEnabled", true);
        valueEnabled = prefs.getBoolean("ConfigurableIPot.valueEnabled", true);
        bitValueEnabled = prefs.getBoolean("ConfigurableIPot.bitValueEnabled", false);
        bitViewEnabled = prefs.getBoolean("ConfigurableIPot.bitViewEnabled", true);
        sexEnabled = prefs.getBoolean("ConfigurableIPot.sexEnabled", true);
        typeEnabled = prefs.getBoolean("ConfigurableIPot.typeEnabled", true);
        initComponents(); // this has unfortunate byproduect of resetting pot value to 0... don't know how to prevent stateChanged event
        dontProcessRegBiasSlider = true;
        regBiasSlider.setMaximum(pot.maxRegBitValue - 1); // TODO replace with getter,  needed to prevent extraneous callbacks
        dontProcessRefSlider = true;
        refBiasSlider.setMaximum(pot.maxRefBitValue - 1);
        operatingModeComboBox.setModel(new DefaultComboBoxModel(OperatingMode.values()));
        voltageLevelComboBox.setModel(new DefaultComboBoxModel(VoltageLevel.values()));
        if (pot != null) {
            nameLabel.setText(pot.getName()); // the name of the bias
            nameLabel.setHorizontalAlignment(SwingConstants.TRAILING);
            nameLabel.setBorder(null);
            if (pot.getTooltipString() != null) {
                nameLabel.setToolTipText(pot.getTooltipString());
            }

            bitPatternTextField.setColumns(pot.getNumBits() + 1);

            pot.loadPreferences(); // to get around slider value change
            pot.addObserver(this); // when pot changes, so does this gui control view
        }
        setAlignmentX(LEFT_ALIGNMENT);
        updateAppearance();  // set controls up with values from ipot
        allInstances.add(this);
        setBitViewEnabled(true);
    }

    public String toString() {
        return "ShiftedSourceControls " + pot.getName();
    }

    void rr() {
        revalidate();
        repaint();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        nameLabel = new javax.swing.JLabel();
        flagsPanel = new javax.swing.JPanel();
        operatingModeComboBox = new javax.swing.JComboBox();
        voltageLevelComboBox = new javax.swing.JComboBox();
        refBiasSlider = new javax.swing.JSlider();
        refBiasTextField = new javax.swing.JTextField();
        bufferBiasPanel = new javax.swing.JPanel();
        regBiasSlider = new javax.swing.JSlider();
        regBiasTextField = new javax.swing.JTextField();
        bitPatternTextField = new javax.swing.JTextField();

        setMaximumSize(new java.awt.Dimension(131243, 25));
        setPreferredSize(new java.awt.Dimension(809, 20));
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                formMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                formMouseExited(evt);
            }
        });
        addAncestorListener(new javax.swing.event.AncestorListener() {
            public void ancestorMoved(javax.swing.event.AncestorEvent evt) {
            }
            public void ancestorAdded(javax.swing.event.AncestorEvent evt) {
                formAncestorAdded(evt);
            }
            public void ancestorRemoved(javax.swing.event.AncestorEvent evt) {
            }
        });
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS));

        nameLabel.setFont(new java.awt.Font("Microsoft Sans Serif", 1, 12)); // NOI18N
        nameLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        nameLabel.setText("name");
        nameLabel.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        nameLabel.setMaximumSize(new java.awt.Dimension(75, 15));
        nameLabel.setMinimumSize(new java.awt.Dimension(50, 15));
        nameLabel.setPreferredSize(new java.awt.Dimension(70, 15));
        add(nameLabel);

        flagsPanel.setMaximumSize(new java.awt.Dimension(32767, 16));
        flagsPanel.setLayout(new javax.swing.BoxLayout(flagsPanel, javax.swing.BoxLayout.X_AXIS));
        add(flagsPanel);

        operatingModeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        operatingModeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                operatingModeComboBoxActionPerformed(evt);
            }
        });
        add(operatingModeComboBox);

        voltageLevelComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        voltageLevelComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                voltageLevelComboBoxActionPerformed(evt);
            }
        });
        add(voltageLevelComboBox);

        refBiasSlider.setMaximum(63);
        refBiasSlider.setToolTipText("Slide to adjust shifted source voltage");
        refBiasSlider.setValue(0);
        refBiasSlider.setAlignmentX(0.0F);
        refBiasSlider.setMaximumSize(new java.awt.Dimension(32767, 16));
        refBiasSlider.setMinimumSize(new java.awt.Dimension(36, 10));
        refBiasSlider.setPreferredSize(new java.awt.Dimension(200, 25));
        refBiasSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                refBiasSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                refBiasSliderMouseReleased(evt);
            }
        });
        refBiasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                refBiasSliderStateChanged(evt);
            }
        });
        add(refBiasSlider);

        refBiasTextField.setColumns(6);
        refBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        refBiasTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        refBiasTextField.setText("value");
        refBiasTextField.setToolTipText("Enter bias current here. Up and Down arrows change values. Shift to increment/decrement bit value.");
        refBiasTextField.setMaximumSize(new java.awt.Dimension(100, 16));
        refBiasTextField.setMinimumSize(new java.awt.Dimension(11, 15));
        refBiasTextField.setPreferredSize(new java.awt.Dimension(53, 15));
        refBiasTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                refBiasTextFieldMouseWheelMoved(evt);
            }
        });
        refBiasTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refBiasTextFieldActionPerformed(evt);
            }
        });
        refBiasTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                refBiasTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                refBiasTextFieldFocusLost(evt);
            }
        });
        refBiasTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                refBiasTextFieldKeyPressed(evt);
                valueTextFieldKeyPressed(evt);
            }
        });
        add(refBiasTextField);

        bufferBiasPanel.setMaximumSize(new java.awt.Dimension(32767, 16));
        bufferBiasPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                bufferBiasPanelformMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                bufferBiasPanelformMouseExited(evt);
            }
        });
        bufferBiasPanel.setLayout(new javax.swing.BoxLayout(bufferBiasPanel, javax.swing.BoxLayout.X_AXIS));

        regBiasSlider.setMaximum(63);
        regBiasSlider.setToolTipText("Slide to adjust internal buffer bias for shifted source");
        regBiasSlider.setValue(0);
        regBiasSlider.setAlignmentX(0.0F);
        regBiasSlider.setMaximumSize(new java.awt.Dimension(32767, 50));
        regBiasSlider.setMinimumSize(new java.awt.Dimension(36, 10));
        regBiasSlider.setPreferredSize(new java.awt.Dimension(100, 10));
        regBiasSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                regBiasSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                regBiasSliderMouseReleased(evt);
            }
        });
        regBiasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                regBiasSliderStateChanged(evt);
            }
        });
        bufferBiasPanel.add(regBiasSlider);

        regBiasTextField.setColumns(6);
        regBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        regBiasTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        regBiasTextField.setText("value");
        regBiasTextField.setToolTipText("Enter buffer bias current here. Up and Down arrows change values. Shift to increment/decrement bit value.");
        regBiasTextField.setMaximumSize(new java.awt.Dimension(100, 2147483647));
        regBiasTextField.setMinimumSize(new java.awt.Dimension(11, 15));
        regBiasTextField.setPreferredSize(new java.awt.Dimension(53, 15));
        regBiasTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                regBiasTextFieldMouseWheelMoved(evt);
            }
        });
        regBiasTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                regBiasTextFieldActionPerformed(evt);
            }
        });
        regBiasTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                regBiasTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                regBiasTextFieldFocusLost(evt);
            }
        });
        regBiasTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                regBiasTextFieldKeyPressed(evt);
            }
        });
        bufferBiasPanel.add(regBiasTextField);

        add(bufferBiasPanel);

        bitPatternTextField.setEditable(false);
        bitPatternTextField.setText("bitPatternTextField");
        bitPatternTextField.setToolTipText("Bit pattern sent to bias gen");
        add(bitPatternTextField);
    }// </editor-fold>//GEN-END:initComponents
//    Border selectedBorder=new EtchedBorder(), unselectedBorder=new EmptyBorder(1,1,1,1);

    private void formMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseExited
//        setBorder(unselectedBorder); // TODO add your handling code here:
    }//GEN-LAST:event_formMouseExited

    private void formMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseEntered
//        setBorder(selectedBorder);
    }//GEN-LAST:event_formMouseEntered

    private void refBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_refBiasSliderStateChanged
        // 1. changing slider, e.g. max value, will generate change events here.
        //
        // 2. we can get a double send here if user presses uparrow key,
        // resulting in new pot value,
        // which updates the slider position, which ends up with
        // a different bitvalue that makes a new
        // pot value.
        //See http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
        //        System.out.println("slider state changed");

        // 3. slider is only source of ChangeEvents, but we can create these event by changing the slider in code.
        // 4. to avoid this, we use dontProcessSlider flag to not update slider from change event handler.
        if (dontProcessRefSlider) {
            dontProcessRefSlider = false;
            return;
        }
        int bv = refBitValueFromSliderValue();
        pot.setRefBitValue(bv);
}//GEN-LAST:event_refBiasSliderStateChanged

    private void refBiasSliderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_refBiasSliderMousePressed
        startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
}//GEN-LAST:event_refBiasSliderMousePressed

    private void refBiasSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_refBiasSliderMouseReleased
        endEdit();
}//GEN-LAST:event_refBiasSliderMouseReleased

    private void refBiasTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refBiasTextFieldActionPerformed
        // new pots current value entered
        //        System.out.println("value field action performed");
        try {
            //            float v=Float.parseFloat(valueTextField.getText());
            float v = engFormat.parseFloat(refBiasTextField.getText());
            //            System.out.println("parsed "+valueTextField.getText()+" as "+v);
            startEdit();
            pot.setRefCurrent(v);
            endEdit();
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
            refBiasTextField.selectAll();
        }
}//GEN-LAST:event_refBiasTextFieldActionPerformed

    private void refBiasTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_refBiasTextFieldFocusGained
        refBiasTextField.setFont(new java.awt.Font("Courier New", 1, 11));  // bold
}//GEN-LAST:event_refBiasTextFieldFocusGained

    private void refBiasTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_refBiasTextFieldFocusLost
        refBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11));
}//GEN-LAST:event_refBiasTextFieldFocusLost

    private void refBiasTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_refBiasTextFieldKeyPressed
        int code = evt.getKeyCode();
        if (code == KeyEvent.VK_UP) {
            pot.setRefBitValue(pot.getRefBitValue() + 1);
            endEdit();
        } else if (code == KeyEvent.VK_DOWN) {
            startEdit();
            pot.setRefBitValue(pot.getRefBitValue() - 1);
            endEdit();
        }
        pot.updateBitValue();
}//GEN-LAST:event_refBiasTextFieldKeyPressed

    private void refBiasTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_refBiasTextFieldMouseWheelMoved
        int clicks = evt.getWheelRotation();
        pot.setRefBitValue(pot.getRefBitValue() - clicks);
}//GEN-LAST:event_refBiasTextFieldMouseWheelMoved

    private void regBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_regBiasSliderStateChanged
        // we can get a double send here if user presses uparrow key,
        // resulting in new pot value,
        // which updates the slider position, which ends up with
        // a different bitvalue that makes a new
        // pot value.
        //See http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
        //        System.out.println("slider state changed");
        // slider is only source of ChangeEvents
        //        System.out.println("slider state changed for "+pot);

        //        if(!s.getValueIsAdjusting()){
        //            startEdit();
        //        }
        if (dontProcessRegBiasSlider) {
            dontProcessRegBiasSlider = false;
            return;
        }
        int bbv = regBitValueFromSliderValue();
        pot.setRegBitValue(bbv);
//        log.info("from slider state change got new buffer bit value = " + pot.getBufferBitValue() + " from slider value =" + s.getValue());
}//GEN-LAST:event_regBiasSliderStateChanged

    private void regBiasSliderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_regBiasSliderMousePressed
        startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
}//GEN-LAST:event_regBiasSliderMousePressed

    private void regBiasSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_regBiasSliderMouseReleased
        endEdit();
}//GEN-LAST:event_regBiasSliderMouseReleased

    private void regBiasTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_regBiasTextFieldActionPerformed
        try {
            float v = engFormat.parseFloat(regBiasTextField.getText());
            startEdit();
            regBiasTextField.setText(engFormat.format(pot.setRegCurrent(v)));
            endEdit();
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
            regBiasTextField.selectAll();
        }
}//GEN-LAST:event_regBiasTextFieldActionPerformed

    private void regBiasTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_regBiasTextFieldFocusGained
        regBiasTextField.setFont(new java.awt.Font("Courier New", 1, 11));
}//GEN-LAST:event_regBiasTextFieldFocusGained

    private void regBiasTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_regBiasTextFieldFocusLost
        regBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11));
}//GEN-LAST:event_regBiasTextFieldFocusLost

    private void regBiasTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_regBiasTextFieldKeyPressed
        // key pressed in text field
        //        System.out.println("keyPressed evt "+evt);
        //        System.out.println("value field key pressed");
        int code = evt.getKeyCode();
        if (code == KeyEvent.VK_UP) {
            pot.setRegBitValue(pot.getRegBitValue() + 1);
            endEdit();
        } else if (code == KeyEvent.VK_DOWN) {
            startEdit();
            pot.setRegBitValue(pot.getRegBitValue() - 1);
            endEdit();
        }
        pot.updateBitValue();
}//GEN-LAST:event_regBiasTextFieldKeyPressed

    private void regBiasTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_regBiasTextFieldMouseWheelMoved
        int clicks = evt.getWheelRotation();
        pot.setRegBitValue(pot.getRegBitValue() - clicks); // rotating wheel away gives negative clicks (scrolling up) but should increase current
}//GEN-LAST:event_regBiasTextFieldMouseWheelMoved

    private void bufferBiasPanelformMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_bufferBiasPanelformMouseEntered
        //        setBorder(selectedBorder);
}//GEN-LAST:event_bufferBiasPanelformMouseEntered

    private void bufferBiasPanelformMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_bufferBiasPanelformMouseExited
        //        setBorder(unselectedBorder); // TODO add your handling code here:
}//GEN-LAST:event_bufferBiasPanelformMouseExited

    private void valueTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_valueTextFieldKeyPressed
        // TODO add your handling code here:
    }//GEN-LAST:event_valueTextFieldKeyPressed

private void formAncestorAdded(javax.swing.event.AncestorEvent evt) {//GEN-FIRST:event_formAncestorAdded
    if (addedUndoListener) {
        return;
    }
    addedUndoListener = true;
    if (evt.getComponent() instanceof Container) {
        Container anc = (Container) evt.getComponent();
        while (anc != null && anc instanceof Container) {
            if (anc instanceof UndoableEditListener) {
                editSupport.addUndoableEditListener((UndoableEditListener) anc);
                break;
            }
            anc = anc.getParent();
        }
    }
}//GEN-LAST:event_formAncestorAdded

private void operatingModeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_operatingModeComboBoxActionPerformed
    startEdit();
    pot.setOperatingMode((OperatingMode) operatingModeComboBox.getSelectedItem());
    endEdit();
}//GEN-LAST:event_operatingModeComboBoxActionPerformed

private void voltageLevelComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_voltageLevelComboBoxActionPerformed
    startEdit();
    pot.setVoltageLevel((VoltageLevel) voltageLevelComboBox.getSelectedItem());
    endEdit();
}//GEN-LAST:event_voltageLevelComboBoxActionPerformed

//     private int oldPotValue=0;
    /**
     * when slider is moved, event is sent here. The slider is the 'master' of
     * the value in the text field. Slider is log scale, from pot min to pot max
     * with caveat that zero position is zero current (no current splitter
     * outputs switched on) and rest of values are log scale from
     * pot.getCurrentResolution to pot.getMaxCurrent
     *
     * @param e the ChangeEvent
     */
    void startEdit() {
//        System.out.println("ipot start edit "+pot);
        edit = new MyStateEdit(this, "ShiftedSourceControlsEdit");
//         oldPotValue=pot.getRefBitValue();
    }

    void endEdit() {
//         if(oldPotValue==pot.getRefBitValue()){
////            System.out.println("no edit, because no change in "+pot);
//             return;
//         }
//        System.out.println("ipot endEdit "+pot);
        if (edit != null) {
            edit.end();
        }
//        System.out.println("ipot "+pot+" postEdit");
        editSupport.postEdit(edit);
    }
    final String KEY_REFBITVALUE = "refBitValue";
    final String KEY_REGBITVALUE = "regBitValue";
    final String KEY_OPERATINGMODE = "operatingMode";
    final String KEY_VOLTAGELEVEL = "voltageLevel";
    final String KEY_ENABLED = "enabled";

    public void restoreState(Hashtable<?, ?> hashtable) {
//        System.out.println("restore state");
        if (hashtable == null) {
            throw new RuntimeException("null hashtable");
        }
        if (hashtable.get(KEY_REFBITVALUE) == null) {
            log.warning("pot " + pot + " not in hashtable " + hashtable + " with size=" + hashtable.size());

            return;
        }
        if (hashtable.get(KEY_REGBITVALUE) == null) {
            log.warning("pot " + pot + " not in hashtable " + hashtable + " with size=" + hashtable.size());
            return;
        }
        pot.setRefBitValue((Integer) hashtable.get(KEY_REFBITVALUE));
        pot.setRegBitValue((Integer) hashtable.get(KEY_REGBITVALUE));
        pot.setOperatingMode((OperatingMode) hashtable.get(KEY_OPERATINGMODE));
        pot.setVoltageLevel((VoltageLevel) hashtable.get(KEY_VOLTAGELEVEL));
    }

    public void storeState(Hashtable<Object, Object> hashtable) {
//        System.out.println(" storeState "+pot);
        hashtable.put(KEY_REFBITVALUE, pot.getRefBitValue());
        hashtable.put(KEY_REGBITVALUE, pot.getRegBitValue());
        hashtable.put(KEY_VOLTAGELEVEL, pot.getVoltageLevel());
        hashtable.put(KEY_OPERATINGMODE, pot.getOperatingMode());

    }

    class MyStateEdit extends StateEdit {

        public MyStateEdit(StateEditable o, String s) {
            super(o, s);
        }

        protected void removeRedundantState() {
        }
    }
    private static EngineeringFormat engFormat = new EngineeringFormat();

    /**
     * updates the GUI slider and text fields to match actual pot values. These
     * updates should not trigger events that cause edits to be stored.
     */
    protected final void updateAppearance() {
        if (pot == null) {
            return;
        }
        if (refBiasSlider.isVisible() != sliderEnabled) {
            refBiasSlider.setVisible(sliderEnabled);
            rr();
        }
        if (refBiasTextField.isVisible() != valueEnabled) {
            refBiasTextField.setVisible(valueEnabled);
            rr();
        }

        if (regBiasSlider.isVisible() != sliderEnabled) {
            regBiasSlider.setVisible(sliderEnabled);
            rr();
        }
        if (regBiasSlider.isVisible() != valueEnabled) {
            regBiasSlider.setVisible(valueEnabled);
            rr();
        }

        refBiasSlider.setValue(refSliderValueFromBitValue());
        refBiasTextField.setText(engFormat.format(pot.getRefCurrent()));

        if (bitPatternTextField.isVisible() != bitViewEnabled) {
            bitPatternTextField.setVisible(bitViewEnabled);
            rr();
        }
        bitPatternTextField.setText(String.format("%16s", Integer.toBinaryString(pot.computeBinaryRepresentation())).replace(' ', '0'));

        regBiasSlider.setValue(regSliderValueFromBitValue());
        regBiasTextField.setText(engFormat.format(pot.getRegCurrent()));

        if (voltageLevelComboBox.getSelectedItem() != pot.getVoltageLevel()) {
            voltageLevelComboBox.setSelectedItem(pot.getVoltageLevel());
        }
        if (operatingModeComboBox.getSelectedItem() != pot.getOperatingMode()) {
            operatingModeComboBox.setSelectedItem(pot.getOperatingMode());
        }
        //log.info("update appearance "+pot.getName());

    }
    // following two methods compute slider/bit value inverses
    private final int knee = 8;  // at this value, mapping goes from linear to log
    // following assumes slider max value is same as max bit value

    private double log2(double x) {
        return Math.log(x) / ln2;
    }

    /**
     * Maps from bit value to linear/log slider value.
     *
     * @param v bit value.
     * @param vmax max bit value.
     * @param slider the slider for the value.
     * @return the correct slider value.
     */
    private int bitVal2SliderVal(int v, int vmax, JSlider slider) {
        int s = 0;
        double sm = slider.getMaximum();
        double vm = vmax;
        s = (int) Math.round(sm * v / vm);;
//        log.info("bitValue=" + v + " -> sliderValue=" + s);
        return s;
    }

    /**
     * Maps from linear slider to linear/exponential bit value.
     *
     * @param vmax max bit value.
     * @param slider the slider.
     * @return the bit value.
     */
    private int sliderVal2BitVal(int vmax, JSlider slider) {
        int v = 0;
        int s = slider.getValue();
        double sm = slider.getMaximum();
        double vm = vmax;
        v = (int) Math.round(vm * s / sm);
//        log.info("sliderValue=" + s + " -> bitValue=" + v);
        return v;
    }

    private int refSliderValueFromBitValue() {
        int s = bitVal2SliderVal(pot.getRefBitValue(), pot.maxRefBitValue, refBiasSlider);
        return s;
    }

    private int refBitValueFromSliderValue() {
        int v = sliderVal2BitVal(pot.maxRefBitValue, refBiasSlider);
        return v;
    }

    /**
     * Returns slider value for this pots buffer bit value.
     */
    private int regSliderValueFromBitValue() {
        int v = bitVal2SliderVal(pot.getRegBitValue(), pot.maxRegBitValue, regBiasSlider);
        return v;
    }

    /**
     * Returns buffer bit value from the slider value.
     */
    private int regBitValueFromSliderValue() {
        int v = sliderVal2BitVal(pot.maxRegBitValue, regBiasSlider);
        return v;
    }

    /**
     * called when Observable changes (pot changes)
     */
    public void update(Observable observable, Object obj) {
        if (observable instanceof ShiftedSourceBiasCF) {
//            log.info("observable="+observable);
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    // don't do the following - it sometimes prevents display updates or results in double updates
//                        slider.setValueIsAdjusting(true); // try to prevent a new event from the slider
                    updateAppearance();
                }
            });
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField bitPatternTextField;
    private javax.swing.JPanel bufferBiasPanel;
    private javax.swing.JPanel flagsPanel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JComboBox operatingModeComboBox;
    private javax.swing.JSlider refBiasSlider;
    private javax.swing.JTextField refBiasTextField;
    private javax.swing.JSlider regBiasSlider;
    private javax.swing.JTextField regBiasTextField;
    private javax.swing.JComboBox voltageLevelComboBox;
    // End of variables declaration//GEN-END:variables

    public JTextField getBitPatternTextField() {
        return this.bitPatternTextField;
    }

    public boolean isBitValueEnabled() {
        return this.bitValueEnabled;
    }

    public void setBitValueEnabled(final boolean bitValueEnabled) {
        this.bitValueEnabled = bitValueEnabled;
        prefs.putBoolean("ConfigurableIPot.bitValueEnabled", bitValueEnabled);
    }

    public boolean isBitViewEnabled() {
        return this.bitViewEnabled;
    }

    public void setBitViewEnabled(final boolean bitViewEnabled) {
        this.bitViewEnabled = bitViewEnabled;
        prefs.putBoolean("ConfigurableIPot.bitViewEnabled", bitViewEnabled);
    }

    public boolean isValueEnabled() {
        return this.valueEnabled;
    }

    public void setValueEnabled(final boolean valueEnabled) {
        this.valueEnabled = valueEnabled;
        prefs.putBoolean("ConfigurableIPot.valueEnabled", valueEnabled);
    }

    public boolean isSexEnabled() {
        return this.sexEnabled;
    }

    public void setSexEnabled(final boolean sexEnabled) {
        this.sexEnabled = sexEnabled;
        prefs.putBoolean("ConfigurableIPot.sliderEnabled", sliderEnabled);
    }

    public boolean isSliderEnabled() {
        return this.sliderEnabled;
    }

    public void setSliderEnabled(final boolean sliderEnabled) {
        this.sliderEnabled = sliderEnabled;
        prefs.putBoolean("ConfigurableIPot.sliderEnabled", sliderEnabled);
    }

    public boolean isTypeEnabled() {
        return this.typeEnabled;
    }

    public void setTypeEnabled(final boolean typeEnabled) {
        this.typeEnabled = typeEnabled;
        prefs.putBoolean("ConfigurableIPot.typeEnabled", typeEnabled);
    }
    static ArrayList<ShiftedSourceControlsCF> allInstances = new ArrayList<ShiftedSourceControlsCF>();

    public static void revalidateAllInstances() {
        for (ShiftedSourceControlsCF c : allInstances) {
            c.updateAppearance();
            c.revalidate();
        }
    }
    static String[] controlNames = {"Type", "Sex", "Slider"}; // TODO ,"BitValue","BitView"
    public static JMenu viewMenu;

//    static {
//        viewMenu = new JMenu("View options");
//        viewMenu.setMnemonic('V');
//        for (int i = 0; i < controlNames.length; i++) {
//            viewMenu.add(new VisibleSetter(controlNames[i])); // add a menu item to enable view of this class of information
//        }
//    }

    /**
     * this inner static class updates the appearance of all instances of the
     * control
     */
    static class VisibleSetter extends JCheckBoxMenuItem {

        public String myName;
        Method setMethod, isSetMethod;

        public VisibleSetter(String myName) {
            super(myName);
            this.myName = myName;
            try {
                setMethod = ShiftedSourceControlsCF.class.getMethod("set" + myName + "Enabled", Boolean.TYPE);
                isSetMethod = ShiftedSourceControlsCF.class.getMethod("is" + myName + "Enabled");
                boolean isSel = (Boolean) isSetMethod.invoke(ShiftedSourceControlsCF.class);
                setSelected(isSel);
            } catch (Exception e) {
                e.printStackTrace();
            }
            addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    try {
                        setMethod.invoke(ShiftedSourceControlsCF.class, Boolean.valueOf(isSelected()));
                        setSelected(isSelected());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                    ShiftedSourceControlsCF.revalidateAllInstances();
                }
            });
        }
    }
}

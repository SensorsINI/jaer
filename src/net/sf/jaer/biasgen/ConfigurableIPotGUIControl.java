package net.sf.jaer.biasgen;

/*
 * IPotSliderTextControl.java
 *
 * Created on September 21, 2005, 12:23 PM
 */
import java.awt.Container;
import java.awt.Dimension;
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

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;

import net.sf.jaer.biasgen.BiasgenFrame;
import net.sf.jaer.biasgen.IPotSliderTextControl;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.Pot.Sex;
import net.sf.jaer.biasgen.Pot.Type;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.biasgen.ConfigurableIPotRev0.CurrentLevel;

/**
 * A GUI control component for controlling a Pot.
 * It shows the name of the Pot, its attributes and 
provides fields for direct bit editing of the Pot value. 
Subclasses provide customized control
of voltage or current biases via the sliderAndValuePanel contents.
 * @author  tobi
 */
public class ConfigurableIPotGUIControl extends javax.swing.JPanel implements Observer, StateEditable {
    // the IPot is the master; it is an Observable that notifies Observers when its value changes.
    // thus if the slider changes the pot value, the pot calls us back here to update the appearance of the slider and of the
    // text field. likewise, if code changes the pot, the appearance here will automagically be updated.

    final static Preferences prefs = Preferences.userNodeForPackage(IPotSliderTextControl.class);
    static final Logger log = Logger.getLogger("ConfigurableIPotGUIControl");
    static double ln2 = Math.log(2.);
    ConfigurableIPotRev0 pot;
    StateEdit edit = null;
    UndoableEditSupport editSupport = new UndoableEditSupport();
    BiasgenFrame frame;
    public static boolean sliderEnabled = prefs.getBoolean("ConfigurableIPot.sliderEnabled", true);
    public static boolean valueEnabled = prefs.getBoolean("ConfigurableIPot.valueEnabled", true);
    public static boolean bitValueEnabled = prefs.getBoolean("ConfigurableIPot.bitValueEnabled", false);
    public static boolean bitViewEnabled = prefs.getBoolean("ConfigurableIPot.bitViewEnabled", false);
    public static boolean sexEnabled = prefs.getBoolean("ConfigurableIPot.sexEnabled", true);
    public static boolean typeEnabled = prefs.getBoolean("ConfigurableIPot.typeEnabled", true);
    private boolean addedUndoListener = false;
    private boolean dontProcessBiasSlider = false, dontProcessBufferBiasSlider = false;

    // see java tuturial http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
    // and http://java.sun.com/docs/books/tutorial/uiswing/components/formattedtextfield.html
    /**
     * Creates new form IPotSliderTextControl
     */
    public ConfigurableIPotGUIControl(ConfigurableIPotRev0 pot) {
        this.pot = pot;
        initComponents(); // this has unfortunate byproduect of resetting pot value to 0... don't know how to prevent stateChanged event
        sexComboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
        typeComboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
        currentLevelComboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
        biasEnabledComboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
        sexComboBox.removeAllItems();
        sexComboBox.setMaximumSize(new Dimension(30, 40));
        for (Pot.Sex i : Pot.Sex.values()) {
            sexComboBox.addItem(i);
        }
        sexComboBox.setPrototypeDisplayValue("XX");
        typeComboBox.removeAllItems();
        for (Pot.Type i : Pot.Type.values()) {
            typeComboBox.addItem(i);
        }
        typeComboBox.setPrototypeDisplayValue("CASCODE");
        currentLevelComboBox.removeAllItems();
        for (ConfigurableIPotRev0.CurrentLevel i : ConfigurableIPotRev0.CurrentLevel.values()) {
            currentLevelComboBox.addItem(i);
        }
        currentLevelComboBox.setPrototypeDisplayValue("NORMAL");
        biasEnabledComboBox.removeAllItems();
        for (ConfigurableIPotRev0.BiasEnabled i : ConfigurableIPotRev0.BiasEnabled.values()) {
            biasEnabledComboBox.addItem(i);
        }
        biasEnabledComboBox.setPrototypeDisplayValue("Disabled");
        if (pot != null) {
            nameLabel.setText(pot.getName()); // the name of the bias
            nameLabel.setHorizontalAlignment(SwingConstants.TRAILING);
            nameLabel.setBorder(null);
            if (pot.getTooltipString() != null) {
                nameLabel.setToolTipText(pot.getName()+": "+pot.getTooltipString()+"(position="+pot.getShiftRegisterNumber()+")");
            }

            typeComboBox.setSelectedItem(pot.getType().toString());
//            typeLabel.setText(pot.getType().toString());
//            sexLabel.setText(pot.getSex().toString());
            sexComboBox.setSelectedItem(pot.getSex().toString());

//            sliderAndValuePanel.setVisible(true);
            pot.loadPreferences(); // to get around slider value change
            pot.addObserver(this); // when pot changes, so does this gui control view
        }
        dontProcessBufferBiasSlider = true;
        bufferBiasSlider.setMaximum(ConfigurableIPotRev0.maxBuffeBitValue - 1); // TODO replace with getter,  needed to prevent extraneous callbacks
//        dontProcessBufferBiasSlider = true;
//        bufferBiasSlider.setMinorTickSpacing(1);
//        dontProcessBufferBiasSlider = true;
//        bufferBiasSlider.setMajorTickSpacing(1);
//        dontProcessBufferBiasSlider = true;
//        bufferBiasSlider.setMinimum(0);
//
//        dontProcessBiasSlider = true;
////        biasSlider.setMaximum(pot.maxBitValue); // TODO this is immense value, perhaps 10^7, is it ok?
//        biasSlider.setMaximum(100); // TODO this is immense value, perhaps 10^7, is it ok?
//        dontProcessBiasSlider = true;
//        biasSlider.setMinorTickSpacing(1);
//        dontProcessBiasSlider = true;
//        biasSlider.setMajorTickSpacing(1);
//        dontProcessBiasSlider = true;
//        biasSlider.setMinimum(0);
        updateAppearance();  // set controls up with values from ipot
        allInstances.add(this);
    }

    @Override
    public String toString() {
        return "ConfigurableIPot for pot " + pot.getName();
    }

    void rr() {
        revalidate();
        repaint();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        sexComboBox = new javax.swing.JComboBox();
        biasEnabledComboBox = new javax.swing.JComboBox();
        typeComboBox = new javax.swing.JComboBox();
        currentLevelComboBox = new javax.swing.JComboBox();
        biasSlider = new javax.swing.JSlider();
        biasTextField = new javax.swing.JTextField();
        bufferBiasSlider = new javax.swing.JSlider();
        bufferBiasTextField = new javax.swing.JTextField();
        nameLabel = new javax.swing.JButton();

        setMaximumSize(new java.awt.Dimension(131243, 22));
        setPreferredSize(new java.awt.Dimension(544, 22));
        addAncestorListener(new javax.swing.event.AncestorListener() {
            public void ancestorMoved(javax.swing.event.AncestorEvent evt) {
            }
            public void ancestorAdded(javax.swing.event.AncestorEvent evt) {
                formAncestorAdded(evt);
            }
            public void ancestorRemoved(javax.swing.event.AncestorEvent evt) {
            }
        });

        sexComboBox.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        sexComboBox.setMaximumRowCount(3);
        sexComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "N", "P" }));
        sexComboBox.setToolTipText("N or P type current");
        sexComboBox.setMaximumSize(new java.awt.Dimension(40, 20));
        sexComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sexComboBoxActionPerformed(evt);
            }
        });

        biasEnabledComboBox.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        biasEnabledComboBox.setMaximumRowCount(3);
        biasEnabledComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Enabled", "Weakly disabled" }));
        biasEnabledComboBox.setToolTipText("Disable to turn off bias");
        biasEnabledComboBox.setMaximumSize(new java.awt.Dimension(80, 20));
        biasEnabledComboBox.setMinimumSize(new java.awt.Dimension(20, 14));
        biasEnabledComboBox.setPreferredSize(new java.awt.Dimension(30, 15));
        biasEnabledComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                biasEnabledComboBoxActionPerformed(evt);
            }
        });

        typeComboBox.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        typeComboBox.setMaximumRowCount(3);
        typeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Normal", "Cascode" }));
        typeComboBox.setToolTipText("Normal or Cascode (extra diode-connected fet)");
        typeComboBox.setMaximumSize(new java.awt.Dimension(80, 20));
        typeComboBox.setMinimumSize(new java.awt.Dimension(20, 14));
        typeComboBox.setPreferredSize(new java.awt.Dimension(30, 15));
        typeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                typeComboBoxActionPerformed(evt);
            }
        });

        currentLevelComboBox.setFont(new java.awt.Font("Tahoma", 0, 8)); // NOI18N
        currentLevelComboBox.setMaximumRowCount(3);
        currentLevelComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Normal current", "Low current" }));
        currentLevelComboBox.setToolTipText("Normal or low current (shifted source)");
        currentLevelComboBox.setMaximumSize(new java.awt.Dimension(80, 20));
        currentLevelComboBox.setMinimumSize(new java.awt.Dimension(20, 14));
        currentLevelComboBox.setPreferredSize(new java.awt.Dimension(30, 15));
        currentLevelComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                currentLevelComboBoxActionPerformed(evt);
            }
        });

        biasSlider.setToolTipText("Slide to adjust bias");
        biasSlider.setValue(0);
        biasSlider.setAlignmentX(0.0F);
        biasSlider.setPreferredSize(new java.awt.Dimension(75, 10));
        biasSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                biasSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                biasSliderMouseReleased(evt);
            }
        });
        biasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                biasSliderStateChanged(evt);
            }
        });

        biasTextField.setColumns(6);
        biasTextField.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        biasTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        biasTextField.setText("value");
        biasTextField.setToolTipText("Enter bias current here. Up and Down arrows change values. Shift to increment/decrement bit value.");
        biasTextField.setMaximumSize(new java.awt.Dimension(100, 16));
        biasTextField.setMinimumSize(new java.awt.Dimension(11, 15));
        biasTextField.setPreferredSize(new java.awt.Dimension(53, 15));
        biasTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                biasTextFieldMouseWheelMoved(evt);
            }
        });
        biasTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                biasTextFieldActionPerformed(evt);
            }
        });
        biasTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                biasTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                biasTextFieldFocusLost(evt);
            }
        });
        biasTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                biasTextFieldKeyPressed(evt);
                valueTextFieldKeyPressed(evt);
            }
        });

        bufferBiasSlider.setToolTipText("Slide to adjust buffer bias");
        bufferBiasSlider.setValue(0);
        bufferBiasSlider.setAlignmentX(0.0F);
        bufferBiasSlider.setPreferredSize(new java.awt.Dimension(40, 10));
        bufferBiasSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                bufferBiasSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                bufferBiasSliderMouseReleased(evt);
            }
        });
        bufferBiasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                bufferBiasSliderStateChanged(evt);
            }
        });

        bufferBiasTextField.setColumns(6);
        bufferBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        bufferBiasTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        bufferBiasTextField.setText("value");
        bufferBiasTextField.setToolTipText("Enter buffer bias current here. Up and Down arrows change values. Shift to increment/decrement bit value.");
        bufferBiasTextField.setMaximumSize(new java.awt.Dimension(100, 2147483647));
        bufferBiasTextField.setMinimumSize(new java.awt.Dimension(11, 15));
        bufferBiasTextField.setPreferredSize(new java.awt.Dimension(53, 15));
        bufferBiasTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                bufferBiasTextFieldMouseWheelMoved(evt);
            }
        });
        bufferBiasTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bufferBiasTextFieldActionPerformed(evt);
            }
        });
        bufferBiasTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                bufferBiasTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                bufferBiasTextFieldFocusLost(evt);
            }
        });
        bufferBiasTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                bufferBiasTextFieldKeyPressed(evt);
            }
        });

        nameLabel.setText("name");
        nameLabel.setBorderPainted(false);
        nameLabel.setContentAreaFilled(false);
        nameLabel.setMargin(new java.awt.Insets(0, 0, 0, 0));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(nameLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 89, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(sexComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 34, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(biasEnabledComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 42, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(typeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(currentLevelComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 42, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(biasSlider, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 215, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(biasTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(bufferBiasSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 90, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(bufferBiasTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(nameLabel)
                    .add(biasSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 21, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(biasTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
                    .add(bufferBiasSlider, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
                    .add(bufferBiasTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
                    .add(biasEnabledComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(typeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(currentLevelComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(sexComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(12, 12, 12))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void currentLevelComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_currentLevelComboBoxActionPerformed
        // must check if action really changes pot state because combobox throws so many events
        if (currentLevelComboBox.getSelectedItem() == null) {
            return;
        }
        if (currentLevelComboBox.getSelectedItem() == pot.getCurrentLevel()) {
            return;
        }
        startEdit();
        pot.setLowCurrentModeEnabled(currentLevelComboBox.getSelectedItem() == ConfigurableIPotRev0.CurrentLevel.Low ? true : false);
        endEdit();
    }//GEN-LAST:event_currentLevelComboBoxActionPerformed

    private void typeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_typeComboBoxActionPerformed
        if (typeComboBox.getSelectedItem() == null) {
            return;
        }
        if (!evt.getActionCommand().equals("comboBoxChanged") || typeComboBox.getSelectedItem() == pot.getType()) {
            return;
        }
        startEdit();
        pot.setType((Pot.Type) typeComboBox.getSelectedItem());
        endEdit();
    }//GEN-LAST:event_typeComboBoxActionPerformed

    private void sexComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sexComboBoxActionPerformed
        if (sexComboBox.getSelectedItem() == null) {
            return;
        }
        if (sexComboBox.getSelectedItem() == pot.getSex()) {
            return;
        }
        startEdit();
        pot.setSex((Pot.Sex) (sexComboBox.getSelectedItem()));
        endEdit();
    }//GEN-LAST:event_sexComboBoxActionPerformed

    private void biasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_biasSliderStateChanged
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

        if (dontProcessBiasSlider) {
            dontProcessBiasSlider = false;
            return;
        }
        int bv = bitValueFromSliderValue();
        pot.setBitValue(bv);
}//GEN-LAST:event_biasSliderStateChanged

    private void biasSliderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_biasSliderMousePressed
        startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
}//GEN-LAST:event_biasSliderMousePressed

    private void biasSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_biasSliderMouseReleased
        endEdit();
}//GEN-LAST:event_biasSliderMouseReleased

    private void biasTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_biasTextFieldActionPerformed
        // new pots current value entered
        //        System.out.println("value field action performed");
        try {
            //            float v=Float.parseFloat(valueTextField.getText());
            float v = engFormat.parseFloat(biasTextField.getText());
            //            System.out.println("parsed "+valueTextField.getText()+" as "+v);
            startEdit();
            pot.setCurrent(v);
            endEdit();
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
            biasTextField.selectAll();
        }
}//GEN-LAST:event_biasTextFieldActionPerformed

    private void biasTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_biasTextFieldFocusGained
        biasTextField.setFont(new java.awt.Font("Courier New", 1, 11));  // bold
}//GEN-LAST:event_biasTextFieldFocusGained

    private void biasTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_biasTextFieldFocusLost
        biasTextField.setFont(new java.awt.Font("Courier New", 0, 11));
}//GEN-LAST:event_biasTextFieldFocusLost

    private void biasTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_biasTextFieldKeyPressed
        int code = evt.getKeyCode();
        boolean shifted = evt.isShiftDown();
        final float byRatio = 1.02f;
        if (code == KeyEvent.VK_UP) {
            startEdit();
            if (shifted) {
                pot.setBitValue(pot.getBitValue() + 1);
            } else {
                pot.changeByRatio(byRatio);
            }
            endEdit();
        } else if (code == KeyEvent.VK_DOWN) {
            startEdit();
            if (shifted) {
                pot.setBitValue(pot.getBitValue() - 1);
            } else {
                pot.changeByRatio(1f / byRatio);
            }
            endEdit();
        }
}//GEN-LAST:event_biasTextFieldKeyPressed

    private void biasTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_biasTextFieldMouseWheelMoved
        int clicks = evt.getWheelRotation();
        float ratio = (1 - clicks * .1f);
        //        System.out.println("ratio="+ratio);
        startEdit();
        pot.changeByRatio(ratio);
        endEdit();
}//GEN-LAST:event_biasTextFieldMouseWheelMoved

    private void bufferBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_bufferBiasSliderStateChanged
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

        if (dontProcessBufferBiasSlider) {
            dontProcessBufferBiasSlider = false;
            return;
        }
        int bbv = bufferBitValueFromSliderValue();
        pot.setBufferBitValue(bbv);
//        log.info("from slider state change got new buffer bit value = " + pot.getBufferBitValue() + " from slider value =" + s.getValue());
}//GEN-LAST:event_bufferBiasSliderStateChanged

    private void bufferBiasSliderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_bufferBiasSliderMousePressed
        startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
}//GEN-LAST:event_bufferBiasSliderMousePressed

    private void bufferBiasSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_bufferBiasSliderMouseReleased
        endEdit();
}//GEN-LAST:event_bufferBiasSliderMouseReleased

    private void bufferBiasTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bufferBiasTextFieldActionPerformed
        try {
            float v = engFormat.parseFloat(bufferBiasTextField.getText());
            startEdit();
            bufferBiasTextField.setText(engFormat.format(pot.setBufferCurrent(v)));
            endEdit();
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
            bufferBiasTextField.selectAll();
        }
}//GEN-LAST:event_bufferBiasTextFieldActionPerformed

    private void bufferBiasTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_bufferBiasTextFieldFocusGained
        bufferBiasTextField.setFont(new java.awt.Font("Courier New", 1, 11));
}//GEN-LAST:event_bufferBiasTextFieldFocusGained

    private void bufferBiasTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_bufferBiasTextFieldFocusLost
        bufferBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11));
}//GEN-LAST:event_bufferBiasTextFieldFocusLost

    private void bufferBiasTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_bufferBiasTextFieldKeyPressed
        // key pressed in text field
        //        System.out.println("keyPressed evt "+evt);
        //        System.out.println("value field key pressed");
        int code = evt.getKeyCode();
        boolean shifted = evt.isShiftDown();
        float byRatio = 1.02f;
        if (code == KeyEvent.VK_UP) {
            startEdit();
            if (shifted) {
                pot.setBufferBitValue(pot.getBufferBitValue() + 1);
            } else {
                pot.changeBufferBiasByRatio(byRatio);
            }
            endEdit();
        } else if (code == KeyEvent.VK_DOWN) {
            startEdit();
            if (shifted) {
                pot.setBufferBitValue(pot.getBufferBitValue() - 1);
            } else {
                pot.changeBufferBiasByRatio(1f / byRatio);
            }
            endEdit();
        }
}//GEN-LAST:event_bufferBiasTextFieldKeyPressed

    private void bufferBiasTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_bufferBiasTextFieldMouseWheelMoved
        int clicks = evt.getWheelRotation();
        pot.setBufferBitValue(pot.getBufferBitValue() - clicks); // rotating wheel away gives negative clicks (scrolling up) but should increase current
}//GEN-LAST:event_bufferBiasTextFieldMouseWheelMoved

    private void valueTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_valueTextFieldKeyPressed
        // TODO add your handling code here:
    }//GEN-LAST:event_valueTextFieldKeyPressed

    private void biasEnabledComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_biasEnabledComboBoxActionPerformed
        startEdit();
        pot.setEnabled(biasEnabledComboBox.getSelectedItem() == ConfigurableIPotRev0.BiasEnabled.Enabled ? true : false);
        endEdit();
}//GEN-LAST:event_biasEnabledComboBoxActionPerformed

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

//     private int oldPotValue=0;
    /** when slider is moved, event is sent here. The slider is the 'master' of the value in the text field.
     * Slider is log scale, from pot min to pot max with caveat that zero position is zero current (no current splitter
     * outputs switched on) and rest of values are log scale from pot.getCurrentResolution to pot.getMaxCurrent
     * @param e the ChangeEvent
     */
    void startEdit() {
//        System.out.println("ipot start edit "+pot);
        edit = new MyStateEdit(this, "ConfigurableIPotGUIControl");
//         oldPotValue=pot.getBitValue();
    }

    void endEdit() {
//         if(oldPotValue==pot.getBitValue()){
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
    final String KEY_BITVALUE = "bitValue";
    final String KEY_BUFFERBITVALUE = "bufferBitValue";
    final String KEY_SEX = "sex";
    final String KEY_CASCODENORMALTYPE = "cascodeNormalType";
    final String KEY_CURRENTLEVEL = "currentLevel";
    final String KEY_ENABLED = "enabled";

    @Override
    public void restoreState(Hashtable<?, ?> hashtable) {
//        System.out.println("restore state");
        if (hashtable == null) {
            throw new RuntimeException("null hashtable");
        }
        if (hashtable.get(KEY_BITVALUE) == null) {
            log.warning("pot " + pot + " not in hashtable " + hashtable + " with size=" + hashtable.size());
//            Set s=hashtable.entrySet();
//            System.out.println("hashtable entries");
//            for(Iterator i=s.iterator();i.hasNext();){
//                Map.Entry me=(Map.Entry)i.next();
//                System.out.println(me);
//            }
            return;
        }
        pot.setBitValue((Integer) hashtable.get(KEY_BITVALUE));
        pot.setBufferBitValue((Integer) hashtable.get(KEY_BUFFERBITVALUE));
        pot.setSex((Sex) hashtable.get(KEY_SEX));
        pot.setType((Type) hashtable.get(KEY_CASCODENORMALTYPE));
        pot.setCurrentLevel((CurrentLevel) hashtable.get(KEY_CURRENTLEVEL));
        pot.setEnabled((Boolean) hashtable.get(KEY_ENABLED));
    }

    @Override
    public void storeState(Hashtable<Object, Object> hashtable) {
//        System.out.println(" storeState "+pot);
        hashtable.put(KEY_BITVALUE, new Integer(pot.getBitValue()));
        hashtable.put(KEY_BUFFERBITVALUE, new Integer(pot.getBufferBitValue()));
        hashtable.put(KEY_SEX, pot.getSex()); // TODO assumes sex nonnull
        hashtable.put(KEY_CASCODENORMALTYPE, pot.getType());
        hashtable.put(KEY_CURRENTLEVEL, pot.getCurrentLevel());
        hashtable.put(KEY_ENABLED, pot.isEnabled());

    }

    class MyStateEdit extends StateEdit {

        public MyStateEdit(StateEditable o, String s) {
            super(o, s);
        }

        @Override
        protected void removeRedundantState() {
        }

        ; // override this to actually get a state stored!!
    }
    private static EngineeringFormat engFormat = new EngineeringFormat();

    /** updates the gui slider and text
    fields to match actual pot values. Neither of these trigger events.
     */
    protected void updateAppearance() {
        if (pot == null) {
            return;
        }
        if (biasSlider.isVisible() != sliderEnabled) {
            biasSlider.setVisible(sliderEnabled);
            rr();
        }
        if (biasTextField.isVisible() != valueEnabled) {
            biasTextField.setVisible(valueEnabled);
            rr();
        }

        biasSlider.setValue(sliderValueFromBitValue());
        biasTextField.setText(engFormat.format(pot.getCurrent()));


        bufferBiasSlider.setValue(bufferSliderValueFromBitValue());
        bufferBiasTextField.setText(engFormat.format(pot.getBufferCurrent()));

        if (sexComboBox.getSelectedItem() != pot.getSex()) {
            sexComboBox.setSelectedItem(pot.getSex());
        }
        if (typeComboBox.getSelectedItem() != pot.getType()) {
            typeComboBox.setSelectedItem(pot.getType());
        }
        if (currentLevelComboBox.getSelectedItem() != pot.getCurrentLevel()) {
            currentLevelComboBox.setSelectedItem(pot.getCurrentLevel());
        }
        if (biasEnabledComboBox.getSelectedItem() != pot.getBiasEnabled()) {
            biasEnabledComboBox.setSelectedItem(pot.getBiasEnabled());
        }
//        System.out.println(pot+" set combobox selected="+biasEnabledComboBox.getSelectedItem());
//        log.info("updateAppearance for "+pot);
    }
    // following two methods compute slider/bit value inverses
    private final int knee = 8;  // at this value, mapping goes from linear to log
    // following assumes slider max value is same as max bit value

    private double log2(double x) {
        return Math.log(x) / ln2;
    }

    /** Maps from bit value to linear/log slider value.
     * 
     * @param v bit value.
     * @param vmax max bit value.
     * @param slider the slider for the value.
     * @return the correct slider value.
     */
    private int bitVal2SliderVal(int v, int vmax, JSlider slider) {
        int s = 0;
        if (v < knee) {
            s = v;
        } else {
            double sm = slider.getMaximum();
            double vm = vmax;
            s = (int) (knee + Math.round((sm - knee) * log2((double) v - (knee - 1)) / log2(vm - (knee - 1))));
        }
//        log.info("bitValue=" + v + " -> sliderValue=" + s);
        return s;
    }

    /** Maps from linear slider to linear/exponential bit value.
     *
     * @param vmax max bit value.
     * @param slider the slider.
     * @return the bit value.
     */
    private int sliderVal2BitVal(int vmax, JSlider slider) {
        int v = 0;
        int s = slider.getValue();
        if (s < knee) {
            v = s;
        } else {
            double sm = slider.getMaximum();
            double vm = vmax;
            v = (int) (knee - 1 + Math.round(Math.pow(2, (s - knee) * (log2(vm - (knee - 1))) / (sm - knee))));
        }
//        log.info("sliderValue=" + s + " -> bitValue=" + v);
        return v;
    }

    private int sliderValueFromBitValue() {
        int s = bitVal2SliderVal(pot.getBitValue(), pot.getMaxBitValue(), biasSlider);
        return s;
    }

    private int bitValueFromSliderValue() {
        int v = sliderVal2BitVal(pot.getMaxBitValue(), biasSlider);
        return v;
    }

    /** Returns slider value for this pots buffer bit value. */
    private int bufferSliderValueFromBitValue() {
        int v = bitVal2SliderVal(pot.getBufferBitValue(), ConfigurableIPotRev0.maxBuffeBitValue, bufferBiasSlider);
        return v;
    }

    /** Returns buffer bit value from the slider value. */
    private int bufferBitValueFromSliderValue() {
        int v = sliderVal2BitVal(ConfigurableIPotRev0.maxBuffeBitValue, bufferBiasSlider);
        return v;
    }

    /** called when Observable changes (pot changes) */
    @Override
    public void update(Observable observable, Object obj) {
        if (observable instanceof ConfigurableIPotRev0) {
//            log.info("observable="+observable);
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    // don't do the following - it sometimes prevents display updates or results in double updates
//                        slider.setValueIsAdjusting(true); // try to prevent a new event from the slider
                    updateAppearance();
                }
            });
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox biasEnabledComboBox;
    private javax.swing.JSlider biasSlider;
    private javax.swing.JTextField biasTextField;
    private javax.swing.JSlider bufferBiasSlider;
    private javax.swing.JTextField bufferBiasTextField;
    private javax.swing.JComboBox currentLevelComboBox;
    private javax.swing.JButton nameLabel;
    private javax.swing.JComboBox sexComboBox;
    private javax.swing.JComboBox typeComboBox;
    // End of variables declaration//GEN-END:variables


    public static boolean isValueEnabled() {
        return ConfigurableIPotGUIControl.valueEnabled;
    }

    public static void setValueEnabled(final boolean valueEnabled) {
        ConfigurableIPotGUIControl.valueEnabled = valueEnabled;
        prefs.putBoolean("ConfigurableIPot.valueEnabled", valueEnabled);
    }

    public static boolean isSexEnabled() {
        return ConfigurableIPotGUIControl.sexEnabled;
    }

    public static void setSexEnabled(final boolean sexEnabled) {
        ConfigurableIPotGUIControl.sexEnabled = sexEnabled;
        prefs.putBoolean("ConfigurableIPot.sliderEnabled", sliderEnabled);
    }

    public static boolean isSliderEnabled() {
        return IPotSliderTextControl.sliderEnabled;
    }

    public static void setSliderEnabled(final boolean sliderEnabled) {
        ConfigurableIPotGUIControl.sliderEnabled = sliderEnabled;
        prefs.putBoolean("ConfigurableIPot.sliderEnabled", sliderEnabled);
    }

    public static boolean isTypeEnabled() {
        return ConfigurableIPotGUIControl.typeEnabled;
    }

    public static void setTypeEnabled(final boolean typeEnabled) {
        ConfigurableIPotGUIControl.typeEnabled = typeEnabled;
        prefs.putBoolean("ConfigurableIPot.typeEnabled", typeEnabled);
    }
    static ArrayList<ConfigurableIPotGUIControl> allInstances = new ArrayList<ConfigurableIPotGUIControl>();

    public static void revalidateAllInstances() {
        for (ConfigurableIPotGUIControl c : allInstances) {
            c.updateAppearance();
            c.revalidate();
        }
    }
    static String[] controlNames = {"Type", "Sex", "Slider"}; // TODO ,"BitValue","BitView"
    public static JMenu viewMenu;

    static {
        viewMenu = new JMenu("View options");
        viewMenu.setMnemonic('V');
        for (int i = 0; i < controlNames.length; i++) {
            viewMenu.add(new VisibleSetter(controlNames[i])); // add a menu item to enable view of this class of information
        }
    }

    /** this inner static class updates the appearance of all instances of the control 
     */
    static class VisibleSetter extends JCheckBoxMenuItem {

        public String myName;
        Method setMethod, isSetMethod;

        public VisibleSetter(String myName) {
            super(myName);
            this.myName = myName;
            try {
                setMethod = ConfigurableIPotGUIControl.class.getMethod("set" + myName + "Enabled", Boolean.TYPE);
                isSetMethod = ConfigurableIPotGUIControl.class.getMethod("is" + myName + "Enabled");
                boolean isSel = (Boolean) isSetMethod.invoke(ConfigurableIPotGUIControl.class);
                setSelected(isSel);
            } catch (Exception e) {
                e.printStackTrace();
            }
            addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        setMethod.invoke(ConfigurableIPotGUIControl.class, new Boolean(isSelected()));
                        setSelected(isSelected());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                    ConfigurableIPotGUIControl.revalidateAllInstances();
                }
            });
        }
    }
}


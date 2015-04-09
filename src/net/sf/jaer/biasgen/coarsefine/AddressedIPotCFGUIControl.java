package net.sf.jaer.biasgen.coarsefine;

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
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF.CurrentLevel;
import net.sf.jaer.util.EngineeringFormat;

/**
 * A GUI control component for controlling a Pot.
 * It shows the name of the Pot, its attributes and 
provides fields for direct bit editing of the Pot value. 
Subclasses provide customized control
of voltage or current biases via the sliderAndValuePanel contents.
 * @author  tobi
 */
public class AddressedIPotCFGUIControl extends javax.swing.JPanel implements Observer, StateEditable {
    // the IPot is the master; it is an Observable that notifies Observers when its value changes.
    // thus if the slider changes the pot value, the pot calls us back here to update the appearance of the slider and of the
    // text field. likewise, if code changes the pot, the appearance here will automagically be updated.

    final static Preferences prefs = Preferences.userNodeForPackage(IPotSliderTextControl.class);
    static final Logger log = Logger.getLogger("ConfigurableIPotGUIControl");
    static double ln2 = Math.log(2.);
    AddressedIPotCF pot;
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
    private boolean dontProcessCoarseBiasSlider = false, dontProcessFineBiasSlider = false;

    // see java tuturial http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
    // and http://java.sun.com/docs/books/tutorial/uiswing/components/formattedtextfield.html
    /**
     * Creates new form IPotSliderTextControl
     */
    public AddressedIPotCFGUIControl(AddressedIPotCF pot) {
        this.pot = pot;
        initComponents(); // this has unfortunate byproduect of resetting pot value to 0... don't know how to prevent stateChanged event
        dontProcessFineBiasSlider = true;
        fineBiasSlider.setMaximum(AddressedIPotCF.maxFineBitValue); // TODO replace with getter,  needed to prevent extraneous callbacks
        dontProcessCoarseBiasSlider = true;
        coarseBiasSlider.setMaximum(AddressedIPotCF.maxCoarseBitValue );
//        dontProcessFineBiasSlider = true;
//        bufferBiasSlider.setMinorTickSpacing(1);
//        dontProcessFineBiasSlider = true;
//        bufferBiasSlider.setMajorTickSpacing(1);
//        dontProcessFineBiasSlider = true;
//        bufferBiasSlider.setMinimum(0);
//
//        dontProcessCoarseBiasSlider = true;
////        biasSlider.setMaximum(pot.maxBitValue); // TODO this is immense value, perhaps 10^7, is it ok?
//        biasSlider.setMaximum(100); // TODO this is immense value, perhaps 10^7, is it ok?
//        dontProcessCoarseBiasSlider = true;
//        biasSlider.setMinorTickSpacing(1);
//        dontProcessCoarseBiasSlider = true;
//        biasSlider.setMajorTickSpacing(1);
//        dontProcessCoarseBiasSlider = true;
//        biasSlider.setMinimum(0);

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
        for (AddressedIPotCF.CurrentLevel i : AddressedIPotCF.CurrentLevel.values()) {
            currentLevelComboBox.addItem(i);
        }
        currentLevelComboBox.setPrototypeDisplayValue("NORMAL");
        biasEnabledComboBox.removeAllItems();
        for (AddressedIPotCF.BiasEnabled i : AddressedIPotCF.BiasEnabled.values()) {
            biasEnabledComboBox.addItem(i);
        }
        biasEnabledComboBox.setPrototypeDisplayValue("Disabled");
        if (pot != null) {
            nameLabel.setText(pot.getName()); // the name of the bias
            nameLabel.setHorizontalAlignment(SwingConstants.TRAILING);
            nameLabel.setBorder(null);
            if (pot.getTooltipString() != null) {
                nameLabel.setToolTipText(pot.getName()+": "+pot.getTooltipString()+"(position="+pot.getAddress()+")");
            }

            typeComboBox.setSelectedItem(pot.getType().toString());
//            typeLabel.setText(pot.getType().toString());
//            sexLabel.setText(pot.getSex().toString());
            sexComboBox.setSelectedItem(pot.getSex().toString());

//            sliderAndValuePanel.setVisible(true);
            pot.loadPreferences(); // to get around slider value change TODO maybe not needed
            pot.addObserver(this); // when pot changes, so does this gui control view
        }
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
        coarseBiasSlider = new javax.swing.JSlider();
        coarseBiasTextField = new javax.swing.JTextField();
        fineBiasSlider = new javax.swing.JSlider();
        fineBiasTextField = new javax.swing.JTextField();
        nameLabel = new javax.swing.JButton();
        totalBiasTextField = new javax.swing.JTextField();

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

        coarseBiasSlider.setToolTipText("Slide to adjust bias");
        coarseBiasSlider.setValue(0);
        coarseBiasSlider.setAlignmentX(0.0F);
        coarseBiasSlider.setPreferredSize(new java.awt.Dimension(100, 10));
        coarseBiasSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                coarseBiasSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                coarseBiasSliderMouseReleased(evt);
            }
        });
        coarseBiasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                coarseBiasSliderStateChanged(evt);
            }
        });

        coarseBiasTextField.setColumns(6);
        coarseBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        coarseBiasTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        coarseBiasTextField.setText("value");
        coarseBiasTextField.setToolTipText("Enter bias current here. Up and Down arrows change values. Shift to increment/decrement bit value.");
        coarseBiasTextField.setMaximumSize(new java.awt.Dimension(100, 16));
        coarseBiasTextField.setMinimumSize(new java.awt.Dimension(11, 15));
        coarseBiasTextField.setPreferredSize(new java.awt.Dimension(53, 15));
        coarseBiasTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                coarseBiasTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                coarseBiasTextFieldFocusLost(evt);
            }
        });
        coarseBiasTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                coarseBiasTextFieldMouseWheelMoved(evt);
            }
        });
        coarseBiasTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                coarseBiasTextFieldActionPerformed(evt);
            }
        });
        coarseBiasTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                coarseBiasTextFieldKeyPressed(evt);
                valueTextFieldKeyPressed(evt);
            }
        });

        fineBiasSlider.setToolTipText("Slide to adjust buffer bias");
        fineBiasSlider.setValue(0);
        fineBiasSlider.setAlignmentX(0.0F);
        fineBiasSlider.setPreferredSize(new java.awt.Dimension(40, 10));
        fineBiasSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                fineBiasSliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                fineBiasSliderMouseReleased(evt);
            }
        });
        fineBiasSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                fineBiasSliderStateChanged(evt);
            }
        });

        fineBiasTextField.setColumns(6);
        fineBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        fineBiasTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        fineBiasTextField.setText("value");
        fineBiasTextField.setToolTipText("Enter buffer bias current here. Up and Down arrows change values. Shift to increment/decrement bit value.");
        fineBiasTextField.setMaximumSize(new java.awt.Dimension(100, 2147483647));
        fineBiasTextField.setMinimumSize(new java.awt.Dimension(11, 15));
        fineBiasTextField.setPreferredSize(new java.awt.Dimension(53, 15));
        fineBiasTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                fineBiasTextFieldMouseWheelMoved(evt);
            }
        });
        fineBiasTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fineBiasTextFieldActionPerformed(evt);
            }
        });
        fineBiasTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                fineBiasTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                fineBiasTextFieldFocusLost(evt);
            }
        });
        fineBiasTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                fineBiasTextFieldKeyPressed(evt);
            }
        });

        nameLabel.setText("name");
        nameLabel.setBorderPainted(false);
        nameLabel.setContentAreaFilled(false);
        nameLabel.setMargin(new java.awt.Insets(0, 0, 0, 0));

        totalBiasTextField.setEditable(false);
        totalBiasTextField.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        totalBiasTextField.setText("BitPattern");

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
                .add(coarseBiasSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(coarseBiasTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(fineBiasSlider, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 36, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(fineBiasTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(totalBiasTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.CENTER, nameLabel)
            .add(org.jdesktop.layout.GroupLayout.CENTER, coarseBiasSlider, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 21, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, coarseBiasTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, fineBiasSlider, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, fineBiasTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, totalBiasTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, biasEnabledComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, typeComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, currentLevelComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(org.jdesktop.layout.GroupLayout.CENTER, sexComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 22, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
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
        pot.setLowCurrentModeEnabled(currentLevelComboBox.getSelectedItem() == AddressedIPotCF.CurrentLevel.Low ? true : false);
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

    private void coarseBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_coarseBiasSliderStateChanged
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

        if (dontProcessCoarseBiasSlider) {
            dontProcessCoarseBiasSlider = false;
            return;
        }
        int bv = coarseBitValueFromSliderValue();
//        log.info(Integer.toString(bv));
        pot.setCoarseBitValue(pot.getMaxCoarseBitValue()-bv);
}//GEN-LAST:event_coarseBiasSliderStateChanged

    private void coarseBiasSliderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_coarseBiasSliderMousePressed
        startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
}//GEN-LAST:event_coarseBiasSliderMousePressed

    private void coarseBiasSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_coarseBiasSliderMouseReleased
        endEdit();
}//GEN-LAST:event_coarseBiasSliderMouseReleased

    private void coarseBiasTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_coarseBiasTextFieldActionPerformed
        // new pots current value entered
        //        System.out.println("value field action performed");
        try {
            //            float v=Float.parseFloat(valueTextField.getText());
            int v = Integer.valueOf(coarseBiasTextField.getText());
            //            System.out.println("parsed "+valueTextField.getText()+" as "+v);
            startEdit();
            dontProcessCoarseBiasSlider=true;
            pot.setCoarseBitValue(v);
            endEdit();
//            log.info(pot.toString());
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
            coarseBiasTextField.selectAll();
        }
}//GEN-LAST:event_coarseBiasTextFieldActionPerformed

    private void coarseBiasTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_coarseBiasTextFieldFocusGained
        coarseBiasTextField.setFont(new java.awt.Font("Courier New", 1, 11));  // bold
}//GEN-LAST:event_coarseBiasTextFieldFocusGained

    private void coarseBiasTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_coarseBiasTextFieldFocusLost
        coarseBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11));
}//GEN-LAST:event_coarseBiasTextFieldFocusLost

    private void coarseBiasTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_coarseBiasTextFieldKeyPressed
        int code = evt.getKeyCode();
        if (code == KeyEvent.VK_UP) {
            startEdit();
            dontProcessCoarseBiasSlider=true;
            pot.setCoarseBitValue(pot.getCoarseBitValue() - 1);
            endEdit();
        } else if (code == KeyEvent.VK_DOWN) {
            dontProcessCoarseBiasSlider=true;
            startEdit();
            pot.setCoarseBitValue(pot.getCoarseBitValue() + 1);
            endEdit();
        }
//        log.info(pot.toString());
}//GEN-LAST:event_coarseBiasTextFieldKeyPressed

    private void coarseBiasTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_coarseBiasTextFieldMouseWheelMoved
        int clicks = evt.getWheelRotation();
        startEdit();
        dontProcessCoarseBiasSlider=true;
        pot.setCoarseBitValue(pot.getCoarseBitValue() + clicks);
        endEdit();
}//GEN-LAST:event_coarseBiasTextFieldMouseWheelMoved

    private void fineBiasSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_fineBiasSliderStateChanged
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

        if (dontProcessFineBiasSlider) {
            dontProcessFineBiasSlider = false;
            return;
        }
        int bbv = fineBitValueFromSliderValue();
        pot.setFineBitValue(bbv);
//        log.info("from slider state change got new buffer bit value = " + pot.getBufferBitValue() + " from slider value =" + s.getValue());
}//GEN-LAST:event_fineBiasSliderStateChanged

    private void fineBiasSliderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fineBiasSliderMousePressed
        startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
}//GEN-LAST:event_fineBiasSliderMousePressed

    private void fineBiasSliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fineBiasSliderMouseReleased
        endEdit();
}//GEN-LAST:event_fineBiasSliderMouseReleased

    private void fineBiasTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fineBiasTextFieldActionPerformed
        try {
            float v = engFormat.parseFloat(fineBiasTextField.getText());
            startEdit();
            fineBiasTextField.setText(engFormat.format(pot.setFineCurrent(v)));
            endEdit();
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
            fineBiasTextField.selectAll();
        }
}//GEN-LAST:event_fineBiasTextFieldActionPerformed

    private void fineBiasTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_fineBiasTextFieldFocusGained
        fineBiasTextField.setFont(new java.awt.Font("Courier New", 1, 11));
}//GEN-LAST:event_fineBiasTextFieldFocusGained

    private void fineBiasTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_fineBiasTextFieldFocusLost
        fineBiasTextField.setFont(new java.awt.Font("Courier New", 0, 11));
}//GEN-LAST:event_fineBiasTextFieldFocusLost

    private void fineBiasTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_fineBiasTextFieldKeyPressed
        // key pressed in text field
        //        System.out.println("keyPressed evt "+evt);
        //        System.out.println("value field key pressed");
        int code = evt.getKeyCode();
        if (code == KeyEvent.VK_UP) {
            startEdit();
            pot.setFineBitValue(pot.getFineBitValue() + 1);
            endEdit();
        } else if (code == KeyEvent.VK_DOWN) {
            startEdit();
            pot.setFineBitValue(pot.getFineBitValue() - 1);
            endEdit();
        }
}//GEN-LAST:event_fineBiasTextFieldKeyPressed

    private void fineBiasTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_fineBiasTextFieldMouseWheelMoved
        int clicks = evt.getWheelRotation();
        startEdit();
        pot.setFineBitValue(pot.getFineBitValue() - clicks);
        endEdit();
}//GEN-LAST:event_fineBiasTextFieldMouseWheelMoved

    private void valueTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_valueTextFieldKeyPressed
        // TODO add your handling code here:
    }//GEN-LAST:event_valueTextFieldKeyPressed

    private void biasEnabledComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_biasEnabledComboBoxActionPerformed
        startEdit();
        pot.setEnabled(biasEnabledComboBox.getSelectedItem() == AddressedIPotCF.BiasEnabled.Enabled ? true : false);
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
    final String KEY_BITVALUE_COARSE = "bitValueCoarse";
    final String KEY_BITVALUE_FINE = "bitValueFine";
    final String KEY_SEX = "sex";
    final String KEY_CASCODENORMALTYPE = "cascodeNormalType";
    final String KEY_CURRENTLEVEL = "currentLevel";
    final String KEY_ENABLED = "enabled";

    @Override
    public void restoreState(Hashtable<?, ?> hashtable) {
        System.out.println("restore state");
        if (hashtable == null) {
            throw new RuntimeException("null hashtable");
        }
        if (hashtable.get(KEY_BITVALUE_COARSE) == null) {
            log.warning("pot " + pot + " not in hashtable " + hashtable + " with size=" + hashtable.size());
            return;
        }
        if (hashtable.get(KEY_BITVALUE_FINE) == null) {
            log.warning("pot " + pot + " not in hashtable " + hashtable + " with size=" + hashtable.size());
            return;
        }
        pot.setCoarseBitValue((Integer) hashtable.get(KEY_BITVALUE_COARSE));
        pot.setFineBitValue((Integer) hashtable.get(KEY_BITVALUE_FINE));
        pot.setSex((Sex) hashtable.get(KEY_SEX));
        pot.setType((Type) hashtable.get(KEY_CASCODENORMALTYPE));
        pot.setCurrentLevel((CurrentLevel) hashtable.get(KEY_CURRENTLEVEL));
        pot.setEnabled((Boolean) hashtable.get(KEY_ENABLED));
    }

    @Override
    public void storeState(Hashtable<Object, Object> hashtable) {
//        System.out.println(" storeState "+pot);
        hashtable.put(KEY_BITVALUE_COARSE, new Integer(pot.getCoarseBitValue()));
        hashtable.put(KEY_BITVALUE_FINE, new Integer(pot.getFineBitValue()));
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
        if (coarseBiasSlider.isVisible() != sliderEnabled) {
            coarseBiasSlider.setVisible(sliderEnabled);
            rr();
        }
        if (coarseBiasTextField.isVisible() != valueEnabled) {
            coarseBiasTextField.setVisible(valueEnabled);
            rr();
        }
        
        if (fineBiasSlider.isVisible() != sliderEnabled) {
            fineBiasSlider.setVisible(sliderEnabled);
            rr();
        }
        if (fineBiasTextField.isVisible() != valueEnabled) {
            fineBiasTextField.setVisible(valueEnabled);
            rr();
        }

        coarseBiasSlider.setValue(coarseSliderValueFromBitValue());
        coarseBiasTextField.setText(engFormat.format(pot.getCoarseCurrent()));

        //totalCurrentTextField.setText(engFormat.format(pot.getTotalCurrent()));

        fineBiasSlider.setValue(fineSliderValueFromBitValue());
        fineBiasTextField.setText(engFormat.format(pot.getFineCurrent()));
        
        totalBiasTextField.setText(String.format("%16s", Integer.toBinaryString(pot.computeBinaryRepresentation())).replace(' ', '0'));

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
        //log.info("updateAppearance for "+pot);
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
        double sm = slider.getMaximum();
        double vm = vmax;
        s = (int) Math.round(sm*v/vm);
        //log.info("bitValue=" + v + " -> sliderValue=" + s);
        return s;
    }

    /** Maps from linear slider to linear/exponential bit value.
     *
     * @param vmax max bit value.
     * @param slider the slider.au
     * @return the bit value.
     */
    private int sliderVal2BitVal(int vmax, JSlider slider) {
        int v = 0;
        float s = slider.getValue();
        double sm = slider.getMaximum();
        double vm = vmax;
        v = (int) Math.round(vm*s/sm);
//        log.info("sliderValue=" + s + " -> bitValue=" + v +" Max bit: "+vmax+" Max slider: "+sm);
        return v;
    }

    private int coarseSliderValueFromBitValue() {
        int s = bitVal2SliderVal(pot.getMaxCoarseBitValue()-pot.getCoarseBitValue(), pot.getMaxCoarseBitValue(), coarseBiasSlider);
        return s;
    }

    private int coarseBitValueFromSliderValue() {
        int v = sliderVal2BitVal(pot.getMaxCoarseBitValue(), coarseBiasSlider);
        return v;
    }

    private int fineSliderValueFromBitValue() {
        int v = bitVal2SliderVal(pot.getFineBitValue(), pot.getMaxFineBitValue(), fineBiasSlider);
        return v;
    }

    private int fineBitValueFromSliderValue() {
        int v = sliderVal2BitVal(pot.getMaxFineBitValue(), fineBiasSlider);
        return v;
    }

    /** called when Observable changes (pot changes) */
    @Override
    public void update(Observable observable, Object obj) {
        if (observable instanceof AddressedIPotCF) {
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
    private javax.swing.JSlider coarseBiasSlider;
    private javax.swing.JTextField coarseBiasTextField;
    private javax.swing.JComboBox currentLevelComboBox;
    private javax.swing.JSlider fineBiasSlider;
    private javax.swing.JTextField fineBiasTextField;
    private javax.swing.JButton nameLabel;
    private javax.swing.JComboBox sexComboBox;
    private javax.swing.JTextField totalBiasTextField;
    private javax.swing.JComboBox typeComboBox;
    // End of variables declaration//GEN-END:variables


    public static boolean isValueEnabled() {
        return AddressedIPotCFGUIControl.valueEnabled;
    }

    public static void setValueEnabled(final boolean valueEnabled) {
        AddressedIPotCFGUIControl.valueEnabled = valueEnabled;
        prefs.putBoolean("ConfigurableIPot.valueEnabled", valueEnabled);
    }

    public static boolean isSexEnabled() {
        return AddressedIPotCFGUIControl.sexEnabled;
    }

    public static void setSexEnabled(final boolean sexEnabled) {
        AddressedIPotCFGUIControl.sexEnabled = sexEnabled;
        prefs.putBoolean("ConfigurableIPot.sliderEnabled", sliderEnabled);
    }

    public static boolean isSliderEnabled() {
        return IPotSliderTextControl.sliderEnabled;
    }

    public static void setSliderEnabled(final boolean sliderEnabled) {
        AddressedIPotCFGUIControl.sliderEnabled = sliderEnabled;
        prefs.putBoolean("ConfigurableIPot.sliderEnabled", sliderEnabled);
    }

    public static boolean isTypeEnabled() {
        return AddressedIPotCFGUIControl.typeEnabled;
    }

    public static void setTypeEnabled(final boolean typeEnabled) {
        AddressedIPotCFGUIControl.typeEnabled = typeEnabled;
        prefs.putBoolean("ConfigurableIPot.typeEnabled", typeEnabled);
    }
    static ArrayList<AddressedIPotCFGUIControl> allInstances = new ArrayList<AddressedIPotCFGUIControl>();

    public static void revalidateAllInstances() {
        for (AddressedIPotCFGUIControl c : allInstances) {
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
                setMethod = AddressedIPotCFGUIControl.class.getMethod("set" + myName + "Enabled", Boolean.TYPE);
                isSetMethod = AddressedIPotCFGUIControl.class.getMethod("is" + myName + "Enabled");
                boolean isSel = (Boolean) isSetMethod.invoke(AddressedIPotCFGUIControl.class);
                setSelected(isSel);
            } catch (Exception e) {
                e.printStackTrace();
            }
            addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        setMethod.invoke(AddressedIPotCFGUIControl.class, new Boolean(isSelected()));
                        setSelected(isSelected());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                    AddressedIPotCFGUIControl.revalidateAllInstances();
                }
            });
        }
    }
}


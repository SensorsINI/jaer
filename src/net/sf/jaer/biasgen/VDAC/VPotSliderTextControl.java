/*
 * VPotSliderTextControl.java
 *
 * Created on September 21, 2005, 12:23 PM
 */
package net.sf.jaer.biasgen.VDAC;

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
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;

import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.util.EngineeringFormat;

/**
 * A GUI control component for controlling an IPot.
 * It shows the name of the IPot and provides a slider and text field for entry of the IPot current.
 * @author  tobi
 */
public class VPotSliderTextControl extends JPanel implements Observer, StateEditable {
    // the IPot is the master; it is an Observable that notifies Observers when its value changes.
    // thus if the slider changes the pot value, the pot calls us back here to update the appearance of the slider and of the
    // text field. likewise, if code changes the pot, the appearance here will automagically be updated.

    static Preferences prefs = Preferences.userNodeForPackage(VPotSliderTextControl.class);
    static Logger log = Logger.getLogger("VPotSliderTextControl");
    VPot pot;
    StateEdit edit = null;
    UndoableEditSupport editSupport = new UndoableEditSupport();
    static final private float TEXT_FIELD_MOUSE_WHEEL_FRACTION = 0.001f; // amount of full scale mouse wheel click changes voltage
    static EngineeringFormat engFormat = new EngineeringFormat();
    private long lastMouseWheelMovementTime=0;
    private final long minDtMsForWheelEditPost=500;

    // when the user starts rolling the mousewheel in the text box, this starts an edit.
    // this edit is ended when the user changes focus or presses a key


    static {
        engFormat.setPrecision(3);
    }
    static final private float TEXT_FIELD_KEY_CLICK_FRACTION = 0.001f; // amount for up down arrow clicks
    private boolean addedUndoListener = false;
    private  boolean sliderDontProcess=false;  // use to prevent slider callbacks with ChangeEvents when slider is being set up
    // see java tuturial http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
    // and http://java.sun.com/docs/books/tutorial/uiswing/components/formattedtextfield.html

    /**
     * Creates new form VPotSliderTextControl.
     * 
     * 
     */
    public VPotSliderTextControl(VPot pot) {
        this.pot = pot;
        initComponents(); // this has unfortunate byproduect of resetting pot value to 0... don't know how to prevent stateChanged event
        if (pot != null) {
            sliderDontProcess=true;
            slider.setVisible(true); // we don't use it now
            slider.setMaximum(pot.getMaxBitValue());
            slider.setMinimum(0);
            slider.setToolTipText(pot.getTooltipString());
            pot.addObserver(this); // when pot changes, so does this gui control view
            pot.loadPreferences(); // to get around slider value change
            sliderDontProcess=false;
        }
        updateAppearance();  // set controls up with values from ipot
        allInstances.add(this);

    }

    public String toString() {
        return "VPotGUIControl for pot " + pot.getName();
    }

    void rr() {
        revalidate();
        repaint();
    }
    // updates the gui slider and text fields to match actual pot values
    // neither of these trigger events

    protected void updateAppearance() {
        if (pot == null) {
            return;
        }
        if (valueTextField.isVisible() != valueEnabled) {
            valueTextField.setVisible(valueEnabled);
            rr();
        }
        slider.setValue(bitValueFromSliderValue(slider));
        valueTextField.setText(engFormat.format(pot.getVoltage()));
    }
    // gets from the slider value the bit value

    private int sliderValueFromBitValue(JSlider s) {
        double f = (double) s.getValue() / s.getMaximum(); // fraction of slider
        int v = (int) Math.round(f * pot.getMaxBitValue());
        return v;
    }
    // gets from the bit value the slider value

    private int bitValueFromSliderValue(JSlider s) {
        int v = (int) Math.round((float) pot.getBitValue() / pot.getMaxBitValue() * s.getMaximum());
        return v;
    }

    /** called when Observable changes (pot changes) */
    public void update(Observable observable, Object obj) {
        if (observable instanceof VPot) {
            slider.setValueIsAdjusting(false); // try to prevent a new event from the slider
//            System.out.println("VPotSliderTextControl observer update");
            updateAppearance();
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        slider = new javax.swing.JSlider();
        valueTextField = new javax.swing.JTextField();

        setFocusable(false);
        setMinimumSize(new java.awt.Dimension(100, 15));
        setPreferredSize(new java.awt.Dimension(200, 20));
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
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.LINE_AXIS));

        slider.setMajorTickSpacing(100);
        slider.setMaximum(1000);
        slider.setMinorTickSpacing(10);
        slider.setToolTipText("");
        slider.setValue(0);
        slider.setAlignmentX(0.0F);
        slider.setPreferredSize(new java.awt.Dimension(100, 23));
        slider.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                sliderMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                sliderMouseReleased(evt);
            }
        });
        slider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sliderStateChanged(evt);
            }
        });
        add(slider);

        valueTextField.setColumns(8);
        valueTextField.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        valueTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        valueTextField.setText("value");
        valueTextField.setToolTipText("Enter voltage here. Up and Down arrows change values.");
        valueTextField.setMaximumSize(new java.awt.Dimension(100, 2147483647));
        valueTextField.setMinimumSize(new java.awt.Dimension(11, 15));
        valueTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                valueTextFieldMouseWheelMoved(evt);
            }
        });
        valueTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                valueTextFieldActionPerformed(evt);
            }
        });
        valueTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                valueTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                valueTextFieldFocusLost(evt);
            }
        });
        valueTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                valueTextFieldKeyPressed(evt);
            }
        });
        add(valueTextField);
    }// </editor-fold>//GEN-END:initComponents

    private void valueTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_valueTextFieldFocusLost
        endEdit();
        valueTextField.setFont(new java.awt.Font("Courier New", 0, 11));
    }//GEN-LAST:event_valueTextFieldFocusLost

    private void valueTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_valueTextFieldFocusGained
        startEdit();
        valueTextField.setFont(new java.awt.Font("Courier New", 1, 11));
    }//GEN-LAST:event_valueTextFieldFocusGained
    Border selectedBorder = new EtchedBorder(), unselectedBorder = new EmptyBorder(1, 1, 1, 1);

    private void formMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseExited
//        setBorder(unselectedBorder); // TODO add your handling code here:
    }//GEN-LAST:event_formMouseExited

    private void formMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseEntered
//        setBorder(selectedBorder);
    }//GEN-LAST:event_formMouseEntered

    private void sliderMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sliderMouseReleased
        endEdit();
    }//GEN-LAST:event_sliderMouseReleased

    private void sliderMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sliderMousePressed
        startEdit(); // start slider edit when mouse is clicked in it! not when dragging it
    }//GEN-LAST:event_sliderMousePressed

    private void valueTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_valueTextFieldMouseWheelMoved
        int clicks = evt.getWheelRotation(); // returns negative if wheel is rotated up (away) from user, as in page up, but we want this to mean increased current
        float ratio = (clicks * TEXT_FIELD_MOUSE_WHEEL_FRACTION);
        if (pot.getSex() == Pot.Sex.N) {
            // therefore if pot is n-type we want to increase voltage
            ratio = -ratio;
        }
        startEdit(); 
        pot.changeByFractionOfFullScale(ratio);
        long t=System.currentTimeMillis();
        if(t-lastMouseWheelMovementTime>minDtMsForWheelEditPost){
            endEdit();
        }
        lastMouseWheelMovementTime=t;
    }//GEN-LAST:event_valueTextFieldMouseWheelMoved

    private void valueTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_valueTextFieldKeyPressed
        // key pressed in text field
        //        System.out.println("keyPressed evt "+evt);
//        System.out.println("value field key pressed");
        endEdit();// in case there was an edit started and we changed the value by wheel motion
//        String s = evt.getKeyText(evt.getKeyCode());
        int code = evt.getKeyCode();
        boolean shift = evt.isShiftDown();
        float byRatio = TEXT_FIELD_KEY_CLICK_FRACTION;
        if (shift) {
            byRatio = TEXT_FIELD_KEY_CLICK_FRACTION * 10;
        }
        if (code == KeyEvent.VK_UP) {
            startEdit();
            pot.changeByFractionOfFullScale(byRatio);
            endEdit();
        } else if (code == KeyEvent.VK_DOWN) {
            startEdit();
            pot.changeByFractionOfFullScale(-byRatio);
            endEdit();
        }
    }//GEN-LAST:event_valueTextFieldKeyPressed

    private void valueTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_valueTextFieldActionPerformed
        // new pots current value entered
//        System.out.println("value field action performed");
        try {
//            float v=Float.parseFloat(valueTextField.getText());
            float v = engFormat.parseFloat(valueTextField.getText());
//            System.out.println("parsed "+valueTextField.getText()+" as "+v);
            startEdit();
            pot.setVoltage(v);
            endEdit();
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
            valueTextField.selectAll();
        }
    }//GEN-LAST:event_valueTextFieldActionPerformed

    /** when slider is moved, event is sent here. The slider is the 'master' of the value in the text field.
     * Slider is linear scale, from pot min to pot max 
     * @param e the ChangeEvent
     */
    private void sliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sliderStateChanged
        if(sliderDontProcess) return;
        // we can get a double send here if user presses uparrow key, resulting in new pot value,
        // which updates the slider position, which ends up with a different bitvalue that makes a new
        // pot value.
        //See http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
//        System.out.println("slider state changed");
        // slider is only source of ChangeEvents
        JSlider s = (JSlider) evt.getSource();
//        System.out.println("slider state changed for "+pot);

        int v = (int) s.getValue(); // slider value
        if (v == 0) {
            pot.setBitValue(0); // these pot chanages will come back to us as Observer events
        // a problem because they will updateAappearance, which will change slider state
        // and generate possibly a new slider changeevent
        } else {
            v = sliderValueFromBitValue(s);
            pot.setBitValue(v);
        }
    }//GEN-LAST:event_sliderStateChanged

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
    int oldPotValue = 0;

    // if the edit is null, makes an edit and stores in oldPotValue the present pot value
    private void startEdit() {
        if(edit!=null) return;
//        System.out.println("ipot start edit "+pot);
        edit = new MyStateEdit(this, "pot change");
        oldPotValue = pot.getBitValue();
    }

    // posts the edit if the value has changed and there's an edit pending, then sets the edit to null
    private void endEdit() {
        if (oldPotValue == pot.getBitValue()) {
//            System.out.println("no edit, because no change in "+pot);
            return;
        }
//        System.out.println("ipot endEdit "+pot);
        if (edit != null) {
            edit.end();
//        System.out.println("ipot "+pot+" postEdit");
            editSupport.postEdit(edit);
            edit=null;
        }
    }
    String STATE_KEY = "pot state";

    public void restoreState(Hashtable<?, ?> hashtable) {
//        System.out.println("restore state");
        if (hashtable == null) {
            throw new RuntimeException("null hashtable");
        }
        if (hashtable.get(STATE_KEY) == null) {
            log.warning("pot " + pot + " not in hashtable " + hashtable + " with size=" + hashtable.size());
//            Set s=hashtable.entrySet();
//            System.out.println("hashtable entries");
//            for(Iterator i=s.iterator();i.hasNext();){
//                Map.Entry me=(Map.Entry)i.next();
//                System.out.println(me);
//            }
            return;
        }
        int v = (Integer) hashtable.get(STATE_KEY);
        pot.setBitValue(v);
    }

    public void storeState(Hashtable<Object, Object> hashtable) {
//        System.out.println(" storeState "+pot);
        hashtable.put(STATE_KEY, new Integer(pot.getBitValue()));
    }

    class MyStateEdit extends StateEdit {

        public MyStateEdit(StateEditable o, String s) {
            super(o, s);
        }

        protected void removeRedundantState() {
        }
        ; // override this to actually get a state stored!!
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSlider slider;
    private javax.swing.JTextField valueTextField;
    // End of variables declaration//GEN-END:variables

    public JSlider getSlider() {
        return this.slider;
    }

    public JTextField getValueTextField() {
        return this.valueTextField;
    }

    public static boolean isVoltageEnabled() {
        return VPotSliderTextControl.valueEnabled;
    }

    public static void setVoltageEnabled(final boolean voltageEnabled) {
        VPotSliderTextControl.valueEnabled = voltageEnabled;
        prefs.putBoolean("VPotGUIControl.voltageEnabled", voltageEnabled);
    }

    public static boolean isSliderEnabled() {
        return VPotSliderTextControl.sliderEnabled;
    }

    public static void setSliderEnabled(final boolean sliderEnabled) {
        VPotSliderTextControl.sliderEnabled = sliderEnabled;
        prefs.putBoolean("VPotGUIControl.sliderEnabled", sliderEnabled);
    }
    static ArrayList<VPotSliderTextControl> allInstances = new ArrayList<VPotSliderTextControl>();

    public static void revalidateAllInstances() {
        for (VPotSliderTextControl c : allInstances) {
//            System.out.println(c);
            c.updateAppearance();

            c.revalidate();
        }
    }
    static boolean sliderEnabled = prefs.getBoolean("VPotGUIControl.sliderEnabled", true);
    static boolean valueEnabled = prefs.getBoolean("VPotGUIControl.voltageEnabled", true);
}


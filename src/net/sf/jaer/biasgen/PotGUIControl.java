/*
 * IPotSliderTextControl.java
 *
 * Created on September 21, 2005, 12:23 PM
 */

package net.sf.jaer.biasgen;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
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
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;

/**
 * A GUI control component for controlling a Pot.
 * It shows the name of the Pot, its attributes and 
 provides fields for direct bit editing of the Pot value. 
 Subclasses provide customized control
 of voltage or current biases via the sliderAndValuePanel contents.
 * @author  tobi
 */
public class PotGUIControl extends javax.swing.JPanel implements  Observer, StateEditable {
    // the IPot is the master; it is an Observable that notifies Observers when its value changes.
    // thus if the slider changes the pot value, the pot calls us back here to update the appearance of the slider and of the
    // text field. likewise, if code changes the pot, the appearance here will automagically be updated.
    
    static Preferences prefs=Preferences.userNodeForPackage(IPotSliderTextControl.class);
    static Logger log=Logger.getLogger("PotGUIControl");
    
    Pot pot;
    StateEdit edit=null;
    UndoableEditSupport editSupport=new UndoableEditSupport();

    public static boolean sliderEnabled=prefs.getBoolean("PotGUIControl.sliderEnabled",true);
    public static boolean valueEnabled=prefs.getBoolean("PotGUIControl.valueEnabled",true);
    public static boolean bitValueEnabled=prefs.getBoolean("PotGUIControl.bitValueEnabled",false);
    public static boolean bitViewEnabled=prefs.getBoolean("PotGUIControl.bitViewEnabled",false);
    public static boolean sexEnabled=prefs.getBoolean("PotGUIControl.sexEnabled",true);
    public static boolean typeEnabled=prefs.getBoolean("PotGUIControl.typeEnabled",true);
    
    private boolean addedUndoListener=false;
    // see java tuturial http://java.sun.com/docs/books/tutorial/uiswing/components/slider.html
    // and http://java.sun.com/docs/books/tutorial/uiswing/components/formattedtextfield.html
    
    static Border selectedBorder=new LineBorder(Color.red), unselectedBorder=new EmptyBorder(1,1,1,1);
    
    /**
     * Creates new form IPotSliderTextControl
     */
    public PotGUIControl(Pot pot) {
        this.pot=pot;
        initComponents(); // this has unfortunate byproduect of resetting pot value to 0... don't know how to prevent stateChanged event
        getInsets().set(0, 0, 0, 0);
//        setMinimumSize(new Dimension(150,10));
        if(pot!=null){
            nameLabel.setText(pot.getName()); // the name of the bias
            nameLabel.setHorizontalAlignment(SwingConstants.LEFT);
            nameLabel.setBorder(null);
            if(pot.getTooltipString()!=null) nameLabel.setToolTipText(pot.getTooltipString());
            
            typeLabel.setText(pot.getType().toString());
            sexLabel.setText(pot.getSex().toString());
            bitPatternTextField.setColumns(pot.getNumBits()+1);
            
            sliderAndValuePanel.setVisible(true);
            pot.loadPreferences(); // to get around slider value change
            pot.addObserver(this); // when pot changes, so does this gui control view
            showFocused(false);
        }
        updateAppearance();  // set controls up with values from ipot
//        if(frame!=null){
//            editSupport.addUndoableEditListener(frame);
//        }else{
//            log.warning("tried to add null listener for undo support - ignored");
//        }
        allInstances.add(this);
    }
    
    void showFocused(boolean yes){
        if(yes){
            setBorder(selectedBorder);
        }else{
            setBorder(unselectedBorder);
        }
    }
    
    public String toString(){
        return "PotGUIControl for pot "+pot.getName();
    }
    
    void rr(){
        revalidate();
        repaint();
    }
    
// updates the gui slider and text fields to match actual pot values
// neither of these trigger events
    protected void updateAppearance(){
        if(pot==null) return;
        if(typeLabel.isVisible()!=typeEnabled){ typeLabel.setVisible(typeEnabled); rr(); }
        if(sexLabel.isVisible()!=sexEnabled){ sexLabel.setVisible(sexEnabled); rr(); }
        if(bitValueTextField.isVisible()!=bitValueEnabled){ bitValueTextField.setVisible(bitValueEnabled); rr(); }
        if(bitPatternTextField.isVisible()!=bitViewEnabled){ bitPatternTextField.setVisible(bitViewEnabled); rr(); }
        
        bitPatternTextField.setText(pot.toBitPatternString());
        bitValueTextField.setText(Integer.toString(pot.getBitValue()));
        if(pot.isModified()) {
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.ITALIC));
            nameLabel.setForeground(Color.red);
        }else{
             nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN));
             nameLabel.setForeground(Color.black);
        }
    }
    
     
    /** called when Observable changes (pot changes) */
    public void update(Observable observable, Object obj) {
        if(observable instanceof Pot){
//            log.info("observable="+observable);
            SwingUtilities.invokeLater(new Runnable(){
                    public void run(){
                        updateAppearance();
                    }
                });
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        nameLabel = new javax.swing.JLabel();
        sexLabel = new javax.swing.JLabel();
        typeLabel = new javax.swing.JLabel();
        sliderAndValuePanel = new javax.swing.JPanel();
        bitValueTextField = new javax.swing.JTextField();
        bitPatternTextField = new javax.swing.JTextField();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));

        setFocusable(false);
        setMaximumSize(new java.awt.Dimension(2147483647, 75));
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
        nameLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        nameLabel.setText("name");
        nameLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        nameLabel.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        nameLabel.setMaximumSize(new java.awt.Dimension(200, 15));
        nameLabel.setMinimumSize(new java.awt.Dimension(17, 10));
        nameLabel.setPreferredSize(new java.awt.Dimension(100, 15));
        add(nameLabel);

        sexLabel.setText("sex");
        sexLabel.setToolTipText("Sex (N- or P-type)");
        sexLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 51, 204)));
        sexLabel.setMaximumSize(new java.awt.Dimension(60, 14));
        sexLabel.setMinimumSize(new java.awt.Dimension(17, 10));
        sexLabel.setPreferredSize(new java.awt.Dimension(50, 14));
        add(sexLabel);

        typeLabel.setText("type");
        typeLabel.setToolTipText("Type (Normal or Cascode)");
        typeLabel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 204, 0)));
        typeLabel.setMaximumSize(new java.awt.Dimension(80, 25));
        typeLabel.setMinimumSize(new java.awt.Dimension(17, 10));
        typeLabel.setPreferredSize(new java.awt.Dimension(80, 18));
        add(typeLabel);

        sliderAndValuePanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 153, 153)));
        sliderAndValuePanel.setFocusable(false);
        sliderAndValuePanel.setLayout(new javax.swing.BoxLayout(sliderAndValuePanel, javax.swing.BoxLayout.X_AXIS));
        add(sliderAndValuePanel);

        bitValueTextField.setColumns(8);
        bitValueTextField.setFont(new java.awt.Font("Courier New", 0, 10)); // NOI18N
        bitValueTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        bitValueTextField.setText("bitValue");
        bitValueTextField.setToolTipText("bit value as an int");
        bitValueTextField.setMaximumSize(new java.awt.Dimension(100, 2147483647));
        bitValueTextField.setMinimumSize(new java.awt.Dimension(17, 10));
        bitValueTextField.setPreferredSize(new java.awt.Dimension(59, 10));
        bitValueTextField.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                bitValueTextFieldMouseWheelMoved(evt);
            }
        });
        bitValueTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bitValueTextFieldActionPerformed(evt);
            }
        });
        bitValueTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                bitValueTextFieldKeyPressed(evt);
            }
        });
        add(bitValueTextField);

        bitPatternTextField.setEditable(false);
        bitPatternTextField.setColumns(10);
        bitPatternTextField.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
        bitPatternTextField.setText("bitPattern");
        bitPatternTextField.setToolTipText("bit value as bits");
        bitPatternTextField.setFocusable(false);
        bitPatternTextField.setMaximumSize(new java.awt.Dimension(100, 2147483647));
        bitPatternTextField.setMinimumSize(new java.awt.Dimension(17, 10));
        bitPatternTextField.setPreferredSize(new java.awt.Dimension(71, 10));
        add(bitPatternTextField);
        add(filler1);
    }// </editor-fold>//GEN-END:initComponents

        
        
    private void bitValueTextFieldMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_bitValueTextFieldMouseWheelMoved
        int clicks=evt.getWheelRotation();
        startEdit();
        pot.setBitValue(pot.getBitValue()-clicks);
        endEdit();
    }//GEN-LAST:event_bitValueTextFieldMouseWheelMoved
    
    private void bitValueTextFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_bitValueTextFieldKeyPressed
        // key pressed in text field
//        System.out.println("bit value text field key pressed event");
        int code=evt.getKeyCode();
        boolean shift=evt.isShiftDown();
        if(!shift){
            if(code==KeyEvent.VK_UP){
                startEdit();
                pot.incrementBitValue(); // appearance updated by observer event
                endEdit();
            }else if(code==KeyEvent.VK_DOWN){
                startEdit();
                pot.decrementBitValue();
                endEdit();
            }
        }else{ // shifted, do bit shift
            int v=pot.getBitValue();
            if(code==KeyEvent.VK_UP){
                v=v<<1;
            }else if(code==KeyEvent.VK_DOWN){
                v=v>>>1;
            }
            startEdit();
            pot.setBitValue(v);
            endEdit();
        }
    }//GEN-LAST:event_bitValueTextFieldKeyPressed
    
    private void bitValueTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bitValueTextFieldActionPerformed
        try{
            int v=Integer.parseInt(bitValueTextField.getText());
            startEdit();
            pot.setBitValue(v);
            endEdit();
        }catch(NumberFormatException e){
            Toolkit.getDefaultToolkit().beep();
            bitValueTextField.selectAll();
        }
        
    }//GEN-LAST:event_bitValueTextFieldActionPerformed

private void formAncestorAdded(javax.swing.event.AncestorEvent evt) {//GEN-FIRST:event_formAncestorAdded
            if(addedUndoListener) return;
            addedUndoListener=true;
            if (evt.getComponent() instanceof Container) {
                Container anc = (Container) evt.getComponent();
                while (anc != null && anc instanceof Container) {
                    if (anc instanceof UndoableEditListener) {
                        editSupport.addUndoableEditListener((UndoableEditListener) anc);
//                        log.info("added undo listener "+anc);
                        break;
                    }
                    anc = anc.getParent();
                }
            }
}//GEN-LAST:event_formAncestorAdded
    
    
     private int oldPotValue=0;
     
    /** when slider is moved, event is sent here. The slider is the 'master' of the value in the text field.
     * Slider is log scale, from pot min to pot max with caveat that zero position is zero current (no current splitter
     * outputs switched on) and rest of values are log scale from pot.getCurrentResolution to pot.getMaxCurrent
     * @param e the ChangeEvent
     */   
     void startEdit(){
//        System.out.println("ipot start edit "+pot);
         edit=new MyStateEdit(this, "pot change");
         oldPotValue=pot.getBitValue();
     }
     
     void endEdit(){
         if(oldPotValue==pot.getBitValue()){
//            System.out.println("no edit, because no change in "+pot);
             return;
         }
//        System.out.println("ipot endEdit "+pot);
         if(edit!=null) edit.end();
//        System.out.println("ipot "+pot+" postEdit");
         editSupport.postEdit(edit);
     }
     
     String STATE_KEY="pot state";
     
     public void restoreState(Hashtable<?,?> hashtable) {
//        System.out.println("restore state");
         if(hashtable==null) throw new RuntimeException("null hashtable");
         if(hashtable.get(STATE_KEY)==null) {
             System.err.println("pot "+pot+" not in hashtable "+hashtable+" with size="+hashtable.size());
//            Set s=hashtable.entrySet();
//            System.out.println("hashtable entries");
//            for(Iterator i=s.iterator();i.hasNext();){
//                Map.Entry me=(Map.Entry)i.next();
//                System.out.println(me);
//            }
             return;
         }
         int v=(Integer)hashtable.get(STATE_KEY);
         pot.setBitValue(v);
     }
     
     public void storeState(Hashtable<Object, Object> hashtable) {
//        System.out.println(" storeState "+pot);
         hashtable.put(STATE_KEY, new Integer(pot.getBitValue()));
     }
     
     class MyStateEdit extends StateEdit{
         public MyStateEdit(StateEditable o, String s){
             super(o,s);
         }
         protected void removeRedundantState(){}; // override this to actually get a state stored!!
     }
     
     
     
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField bitPatternTextField;
    private javax.swing.JTextField bitValueTextField;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JLabel sexLabel;
    private javax.swing.JPanel sliderAndValuePanel;
    private javax.swing.JLabel typeLabel;
    // End of variables declaration//GEN-END:variables
    
    
    public JTextField getBitPatternTextField() {
        return this.bitPatternTextField;
    }
    
    public JTextField getBitValueTextField() {
        return this.bitValueTextField;
    }
    
    public static boolean isBitValueEnabled() {
        return PotGUIControl.bitValueEnabled;
    }
    
    public static void setBitValueEnabled(final boolean bitValueEnabled) {
        PotGUIControl.bitValueEnabled = bitValueEnabled;
        prefs.putBoolean("PotGUIControl.bitValueEnabled", bitValueEnabled);
    }
    
    public static boolean isBitViewEnabled() {
        return PotGUIControl.bitViewEnabled;
    }
    
    public static void setBitViewEnabled(final boolean bitViewEnabled) {
        PotGUIControl.bitViewEnabled = bitViewEnabled;
        prefs.putBoolean("PotGUIControl.bitViewEnabled", bitViewEnabled);
    }
    
    public static boolean isValueEnabled() {
        return PotGUIControl.valueEnabled;
    }
    
    public static void setValueEnabled(final boolean valueEnabled) {
        PotGUIControl.valueEnabled = valueEnabled;
        prefs.putBoolean("PotGUIControl.valueEnabled", valueEnabled);
    }
    
    public static boolean isSexEnabled() {
        return PotGUIControl.sexEnabled;
    }
    
    public static void setSexEnabled(final boolean sexEnabled) {
        PotGUIControl.sexEnabled = sexEnabled;
        prefs.putBoolean("PotGUIControl.sliderEnabled", sliderEnabled);
    }
    
    public static boolean isSliderEnabled() {
        return IPotSliderTextControl.sliderEnabled;
    }
    
    public static void setSliderEnabled(final boolean sliderEnabled) {
        PotGUIControl.sliderEnabled = sliderEnabled;
        prefs.putBoolean("PotGUIControl.sliderEnabled", sliderEnabled);
    }
    
    public static boolean isTypeEnabled() {
        return PotGUIControl.typeEnabled;
    }
    
    public static void setTypeEnabled(final boolean typeEnabled) {
        PotGUIControl.typeEnabled = typeEnabled;
        prefs.putBoolean("PotGUIControl.typeEnabled", typeEnabled);
    }
    
    static ArrayList<PotGUIControl> allInstances=new ArrayList<PotGUIControl>();
    
    public static void revalidateAllInstances(){
        for(PotGUIControl c:allInstances){
            c.updateAppearance();
            c.revalidate();
        }
    }
    
    
    static String[] controlNames={"Type","Sex","Slider","BitValue","BitView"};
    public static JMenu viewMenu;
    static {
        viewMenu=new JMenu("View options");
        viewMenu.setMnemonic('V');
        for(int i=0;i<controlNames.length;i++){
            viewMenu.add(new VisibleSetter(controlNames[i])); // add a menu item to enable view of this class of information
        }
    }
    
    /** this inner static class updates the appearance of all instances of the control 
     */
    static class VisibleSetter extends JCheckBoxMenuItem{
        public String myName;
        Method setMethod,isSetMethod;
        public VisibleSetter(String myName){
            super(myName);
            this.myName=myName;
            try{
                setMethod=PotGUIControl.class.getMethod("set"+myName+"Enabled", Boolean.TYPE);
                isSetMethod=PotGUIControl.class.getMethod("is"+myName+"Enabled");
                boolean isSel=(Boolean)isSetMethod.invoke(PotGUIControl.class);
                setSelected(isSel);
            }catch(Exception e){
                e.printStackTrace();
            }
            addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    try{
                        setMethod.invoke(IPotSliderTextControl.class, new Boolean(isSelected()));
                        setSelected(isSelected());
                        ;
                    }catch(Exception e2){
                        e2.printStackTrace();
                    }
                    PotGUIControl.revalidateAllInstances();
                }
            });
        }
    }
    
    public javax.swing.JPanel getSliderAndValuePanel() {
        return sliderAndValuePanel;
    }
    
    public void setSliderAndValuePanel(javax.swing.JPanel sliderAndValuePanel) {
        this.sliderAndValuePanel = sliderAndValuePanel;
    }
    
}


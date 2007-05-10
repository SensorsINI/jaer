/*
 * FilterPanel.java
 *
 * Created on October 31, 2005, 8:13 PM
 */

package ch.unizh.ini.caviar.eventprocessing;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.ActionListener;
import java.beans.*;
import java.beans.Introspector;
import java.lang.reflect.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.BoxLayout;
import javax.swing.border.*;
/**
 * A panel for a filter that has Integer getter/setter methods. These methods are introspected and a set of controls are built for them.
 *
 * @author  tobi
 */
public class FilterPanel extends javax.swing.JPanel implements PropertyChangeListener {
    
    static final float ALIGNMENT=0;
    
    Logger log=Logger.getLogger("Filters");
    
    EventFilter filter=null;
    final float fontSize=9f;
    Border normalBorder, redLineBorder;
    TitledBorder titledBorder;
    
    /** Creates new form FilterPanel */
    public FilterPanel() {
        initComponents();
    }
    
    public FilterPanel(EventFilter f){
        this.filter=f;
        initComponents();
        String cn=filter.getClass().getName();
        int lastdot=cn.lastIndexOf('.');
        String name=cn.substring(lastdot+1);
        titledBorder=new TitledBorder(name);
        titledBorder.getBorderInsets(this).set(1,1,1,1);
        setBorder(titledBorder);
        normalBorder=titledBorder.getBorder();
        redLineBorder = BorderFactory.createLineBorder(Color.red);
        enabledCheckBox.setSelected(filter.isFilterEnabled());
        addIntrospectedControls();
        f.getPropertyChangeSupport().addPropertyChangeListener(this);
    }
    
    java.util.ArrayList<JPanel> controls=new ArrayList<JPanel>();
        
    // gets getter/setter methods for the filter and makes controls for them. enclosed filters are also added as submenus
    private void addIntrospectedControls(){
        JPanel control;
        try{
            BeanInfo info=Introspector.getBeanInfo(filter.getClass(),EventFilter.class);
            PropertyDescriptor[] props=info.getPropertyDescriptors();
            for(PropertyDescriptor p: props){
                // don't add controls for limiting time unless filter supports it
                if(EventFilter2D.isTimeLimitProperty(p) && !(filter instanceof TimeLimitingFilter) ) continue;
                if(false){
//                    System.out.println("prop "+p);
//                    System.out.println("prop name="+p.getName());
//                    System.out.println("prop write method="+p.getWriteMethod());
//                    System.out.println("prop read method="+p.getReadMethod());
//                    System.out.println("type "+p.getPropertyType());
//                    System.out.println("bound: "+p.isBound());
//                    System.out.println("");
                }
                Class c=p.getPropertyType();
//                if(c instanceof Class) System.out.println("filter="+filter+" propertyType="+c);
                if(c==Integer.TYPE && p.getReadMethod()!=null && p.getWriteMethod()!=null){
                    control=new IntControl(filter,p.getName(),p.getWriteMethod(),p.getReadMethod());
                    add(control);
                    controls.add(control);
                }else if(c==Float.TYPE && p.getReadMethod()!=null && p.getWriteMethod()!=null){
                    control=new FloatControl(filter,p.getName(),p.getWriteMethod(),p.getReadMethod());
                    add(control);
                    controls.add(control);
                }else if(c==Boolean.TYPE && p.getReadMethod()!=null && p.getWriteMethod()!=null){
                    control=new BooleanControl(filter,p.getName(),p.getWriteMethod(),p.getReadMethod());
                    add(control);
                    controls.add(control);
                }else if(EventFilter.class.isAssignableFrom(c)){
                    try{
                        Method r=p.getReadMethod();
                        EventFilter2D enclFilter=(EventFilter2D)(r.invoke(filter));
                        if(enclFilter==null) continue;
//                        log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses EventFilter2D "+enclFilter.getClass().getSimpleName());
                        FilterPanel enclPanel=new FilterPanel(enclFilter);
                        this.add(enclPanel);
                        controls.add(enclPanel);
                        ((TitledBorder)enclPanel.getBorder()).setTitle("enclosed: "+enclFilter.getClass().getSimpleName());
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }else{
//                    log.warning("unknown property type "+p.getPropertyType()+" for property "+p.getName());
                }
                // when filter fires a property change event, we get called here and we update all our controls
                filter.getPropertyChangeSupport().addPropertyChangeListener(this);
            }
        }catch(IntrospectionException e){
            log.warning("FilterPanel.addIntrospectedControls: "+e);
        }
        add(Box.createHorizontalGlue());
        setControlsVisible(false);
//        System.out.println("added glue to "+this);
    }
    
    final float factor=1.51f, wheelFactor=1.05f; // factors to change by with arrow and mouse wheel
    
    class BooleanControl extends JPanel{
        Method write,read;
        EventFilter filter;
        boolean initValue=false, nval;
        public BooleanControl(final EventFilter f, final String name, final Method w, final Method r){
            super();
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            final JCheckBox checkBox=new JCheckBox(name);
            checkBox.setFont(checkBox.getFont().deriveFont(fontSize));
            checkBox.setHorizontalTextPosition(SwingConstants.LEFT);
            add(checkBox);
            try{
                Boolean x=(Boolean)r.invoke(filter);
                if(x==null) {
                    System.err.println("null Boolean returned from read method "+r);
                    return;
                }
                initValue=x.booleanValue();
                checkBox.setSelected(initValue);
            }catch(InvocationTargetException e){
                e.printStackTrace();
            }catch(IllegalAccessException e){
                e.printStackTrace();
            }
            checkBox.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    try{
                        w.invoke(filter, checkBox.isSelected());
                    }catch(InvocationTargetException ite){
                        ite.printStackTrace();
                    }catch(IllegalAccessException iae){
                        iae.printStackTrace();
                    }
                }
            });
        }
        
    }
    
    
    class IntControl extends JPanel {
        Method write,read;
        EventFilter filter;
        int initValue=0, nval;
        public IntControl(final EventFilter f, final String name, final Method w, final Method r){
            super();
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label=new JLabel(name);
            label.setFont(label.getFont().deriveFont(fontSize));
            add(label);
            
            final JTextField tf=new JTextField("", 4);
            tf.setToolTipText("Integer control: use arrow keys or mouse wheel to change value by factor. Shift constrains to simple inc/dec");
            try{
                Integer x=(Integer)r.invoke(filter); // read int value
                if(x==null) {
                    System.err.println("null Integer returned from read method "+r);
                    return;
                }
                initValue=x.intValue();
                String s=Integer.toString(x);
//                System.out.println("init value of "+name+" is "+s);
                tf.setText(s);
            }catch(Exception e){
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
//                    System.out.println(e);
                    try{
                        int y=Integer.parseInt(tf.getText());
                        w.invoke(filter, new Integer(y)); // write int value
                    }catch(NumberFormatException fe){
                        tf.selectAll();
                    }catch(InvocationTargetException ite){
                        ite.printStackTrace();
                    }catch(IllegalAccessException iae){
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    try{
                        Integer x=(Integer)r.invoke(filter);
                        initValue=x.intValue();
//                        System.out.println("x="+x);
                    }catch(InvocationTargetException e){
                        e.printStackTrace();
                    }catch(IllegalAccessException e){
                        e.printStackTrace();
                    }
                    int code=evt.getKeyCode();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    if(!shift){
                        if(code==KeyEvent.VK_UP){
                            try{
                                nval=initValue;
                                if (nval==0) nval=1;
                                else nval=(int)Math.round(initValue*factor);
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf,r);
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }else if(code==KeyEvent.VK_DOWN){
                            try{
                                nval=initValue;
                                if (nval==0) nval=0;
                                else nval=(int)Math.round(initValue/factor);
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf,r);
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }
                    }else{
                        // shifted int control just incs or decs by 1
                        if(code==KeyEvent.VK_UP){
                            try{
                                nval=initValue+1;
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf,r);
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }else if(code==KeyEvent.VK_DOWN){
                            try{
                                nval=initValue-1;
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf,r);
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }
                        
                    }
                    
                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try{
                        Integer x=(Integer)r.invoke(filter);
                        initValue=x.intValue();
//                        System.out.println("x="+x);
                    }catch(InvocationTargetException e){
                        e.printStackTrace();
                    }catch(IllegalAccessException e){
                        e.printStackTrace();
                    }
                    int code=evt.getWheelRotation();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    if(!shift){
                        if(code<0){
                            try{
                                nval=initValue;
                                if (Math.round(initValue*wheelFactor)==initValue) nval++;
                                else nval=(int)Math.round(initValue*wheelFactor);
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }else if(code>0){
                            try{
                                nval=initValue;
                                if (Math.round(initValue/wheelFactor)==initValue) nval--;
                                else nval=(int)Math.round(initValue/wheelFactor);
                                if(nval<0) nval=0;
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }
                    }
                }
                
            });
            tf.addFocusListener(new FocusListener() {
                public void focusGained(FocusEvent e) {
                    tf.setSelectionStart(0);
                    tf.setSelectionEnd(tf.getText().length());
                }
                public void focusLost(FocusEvent e) {
                }
            });
        }
    }
    
    void fixIntValue(JTextField tf, Method r){
        // set text to actual value
        try{
            Integer x=(Integer)r.invoke(filter); // read int value
//            initValue=x.intValue();
            String s=Integer.toString(x);
            tf.setText(s);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    class FloatControl extends JPanel {
        Method write,read;
        EventFilter filter;
        float initValue=0, nval;
        public FloatControl(final EventFilter f, final String name, final Method w, final Method r){
            super();
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label=new JLabel(name);
            label.setFont(label.getFont().deriveFont(fontSize));
            add(label);
            final JTextField tf=new JTextField("", 7);
            tf.setToolTipText("Float control: use arrow keys or mouse wheel to change value by factor. Shift reduces factor.");
            try{
                Float x=(Float)r.invoke(filter);
                if(x==null) {
                    System.err.println("null Float returned from read method "+r);
                    return;
                }
                initValue=x.floatValue();
                String s=String.format("%.4f",initValue);
                tf.setText(s);
            }catch(InvocationTargetException e){
                e.printStackTrace();
            }catch(IllegalAccessException e){
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
//                    System.out.println(e);
                    try{
                        float y=Float.parseFloat(tf.getText());
                        w.invoke(filter, new Float(y));
                        Float x=(Float)r.invoke(filter); // get the value from the getter method to constrain it
                        nval=x.floatValue();
                        tf.setText(String.format("%.4f",nval));
                    }catch(NumberFormatException fe){
                        tf.selectAll();
                    }catch(InvocationTargetException ite){
                        ite.printStackTrace();
                    }catch(IllegalAccessException iae){
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    try{
                        Float x=(Float)r.invoke(filter); // get the value from the getter method
                        initValue=x.floatValue();
//                        System.out.println("x="+x);
                    }catch(InvocationTargetException e){
                        e.printStackTrace();
                    }catch(IllegalAccessException e){
                        e.printStackTrace();
                    }
                    int code=evt.getKeyCode();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    float floatFactor=factor;
                    if(shift) floatFactor=wheelFactor;
                    if(code==KeyEvent.VK_UP){
                        try{
                            nval=initValue;
                            if (nval==0) nval=.1f;
                            else nval=(initValue*floatFactor);
                            w.invoke(filter, new Float(nval)); // setter the value
                            Float x=(Float)r.invoke(filter); // get the value from the getter method to constrain it
                            nval=x.floatValue();
                            tf.setText(String.format("%.4f",nval));
                        }catch(InvocationTargetException ite){
                            ite.printStackTrace();
                        }catch(IllegalAccessException iae){
                            iae.printStackTrace();
                        }
                    }else if(code==KeyEvent.VK_DOWN){
                        try{
                            nval=initValue;
                            if (nval==0) nval=.1f;
                            else nval=(initValue/floatFactor);
                            w.invoke(filter, new Float(initValue/floatFactor));
                            Float x=(Float)r.invoke(filter); // get the value from the getter method to constrain it
                            nval=x.floatValue();
                            tf.setText(String.format("%.4f",nval));
                        }catch(InvocationTargetException ite){
                            ite.printStackTrace();
                        }catch(IllegalAccessException iae){
                            iae.printStackTrace();
                        }
                    }
                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try{
                        Float x=(Float)r.invoke(filter); // get the value from the getter method
                        initValue=x.floatValue();
//                        System.out.println("x="+x);
                    }catch(InvocationTargetException e){
                        e.printStackTrace();
                    }catch(IllegalAccessException e){
                        e.printStackTrace();
                    }
                    int code=evt.getWheelRotation();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    if(!shift){
                        if(code<0){
                            try{
                                nval=initValue;
                                if (nval==0) nval=.1f;
                                else nval=(initValue*wheelFactor);
                                w.invoke(filter, new Float(nval)); // setter the value
                                Float x=(Float)r.invoke(filter); // get the value from the getter method to constrain it
                                nval=x.floatValue();
                                tf.setText(String.format("%.4f",nval));
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }else if(code>0){
                            try{
                                nval=initValue;
                                if (nval==0) nval=.1f;
                                else nval=(initValue/wheelFactor);
                                w.invoke(filter, new Float(initValue/wheelFactor));
                                Float x=(Float)r.invoke(filter); // get the value from the getter method to constrain it
                                nval=x.floatValue();
                                tf.setText(String.format("%.4f",nval));
                            }catch(InvocationTargetException ite){
                                ite.printStackTrace();
                            }catch(IllegalAccessException iae){
                                iae.printStackTrace();
                            }
                        }
                    }
                }
            });
            tf.addFocusListener(new FocusListener() {
                public void focusGained(FocusEvent e) {
                    tf.setSelectionStart(0);
                    tf.setSelectionEnd(tf.getText().length());
                }
                public void focusLost(FocusEvent e) {
                }
            });
        }
    }
    
    /** called when a filter calls firePropertyChange */
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        log.info("propertyChangeEvent name="+propertyChangeEvent.getPropertyName()+" src="+propertyChangeEvent.getSource()+" oldValue="+propertyChangeEvent.getOldValue()+" newValue="+propertyChangeEvent.getNewValue());
        if(propertyChangeEvent.getPropertyName().equals("filterEnabled")){
            enabledCheckBox.setSelected((Boolean)propertyChangeEvent.getNewValue());
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        jPanel1 = new javax.swing.JPanel();
        enabledCheckBox = new javax.swing.JCheckBox();
        resetButton = new javax.swing.JButton();
        showControlsToggleButton = new javax.swing.JToggleButton();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 2));

        jPanel1.setAlignmentX(0.0F);
        jPanel1.setPreferredSize(new java.awt.Dimension(69, 23));
        enabledCheckBox.setFont(new java.awt.Font("Tahoma", 0, 9));
        enabledCheckBox.setToolTipText("Enable or disable the filter");
        enabledCheckBox.setMargin(new java.awt.Insets(1, 1, 1, 1));
        enabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enabledCheckBoxActionPerformed(evt);
            }
        });

        jPanel1.add(enabledCheckBox);

        resetButton.setFont(new java.awt.Font("Tahoma", 0, 9));
        resetButton.setText("R");
        resetButton.setToolTipText("Resets the filter");
        resetButton.setMargin(new java.awt.Insets(1, 5, 1, 5));
        resetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetButtonActionPerformed(evt);
            }
        });

        jPanel1.add(resetButton);

        showControlsToggleButton.setFont(new java.awt.Font("Tahoma", 0, 9));
        showControlsToggleButton.setText("P");
        showControlsToggleButton.setToolTipText("Show filter parameters, hides other filters. Click again to see all filters.");
        showControlsToggleButton.setMargin(new java.awt.Insets(1, 5, 1, 5));
        showControlsToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showControlsToggleButtonActionPerformed(evt);
            }
        });

        jPanel1.add(showControlsToggleButton);

        add(jPanel1);

    }// </editor-fold>//GEN-END:initComponents
    
    boolean controlsVisible=false;
    public boolean isControlsVisible(){
        return controlsVisible;
    }
    
    // true to show filter parameter controls
    void setControlsVisible(boolean yes){
        controlsVisible=yes;
        // see http://forum.java.sun.com/thread.jspa?threadID=755789
        if(yes){
            ((TitledBorder)getBorder()).setTitleColor(SystemColor.textText);
            titledBorder.setBorder(redLineBorder);
        }else{
            ((TitledBorder)getBorder()).setTitleColor(SystemColor.textInactiveText);
            titledBorder.setBorder(normalBorder);
        }
        for(JPanel p:controls){
            p.setVisible(yes);
            p.invalidate();
        }
        invalidate();
        Container c=getTopLevelAncestor();
        if(!filter.isEnclosed() && c!=null && c instanceof Window){
            if(c instanceof FilterFrame){
                // hide all filters except one that is being modified, *unless* we are an enclosed filter
                FilterFrame ff=(FilterFrame)c;
                for(FilterPanel f:ff.filterPanels){
                    if(f==this) continue;  // don't do anything to ourselves
                    f.setVisible(!yes); // hide other filters
                }
            }
            ((Window)c).pack();
        }
        if(c!=null && c instanceof Window){
            ((Window)c).pack();
        }
   }
    
    void toggleControlsVisible(){
        controlsVisible=!controlsVisible;
        setControlsVisible(controlsVisible);
    }
    
    private void showControlsToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showControlsToggleButtonActionPerformed
        toggleControlsVisible();
    }//GEN-LAST:event_showControlsToggleButtonActionPerformed
    
    private void enabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enabledCheckBoxActionPerformed
        boolean yes=enabledCheckBox.isSelected();
        if(filter!=null) filter.setFilterEnabled(yes);
        if(yes){
            ((TitledBorder)getBorder()).setTitleColor(SystemColor.textText);
            titledBorder.setBorder(redLineBorder);
        }else{
            ((TitledBorder)getBorder()).setTitleColor(SystemColor.textInactiveText);
            titledBorder.setBorder(normalBorder);
        }
        repaint();
    }//GEN-LAST:event_enabledCheckBoxActionPerformed
    
    private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetButtonActionPerformed
        if(filter!=null)filter.resetFilter();
    }//GEN-LAST:event_resetButtonActionPerformed
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox enabledCheckBox;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton resetButton;
    private javax.swing.JToggleButton showControlsToggleButton;
    // End of variables declaration//GEN-END:variables
    
}

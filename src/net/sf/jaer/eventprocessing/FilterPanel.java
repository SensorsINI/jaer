/*
 * FilterPanel.java
 *
 * Created on October 31, 2005, 8:13 PM
 */
package net.sf.jaer.eventprocessing;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.ActionListener;
import java.beans.*;
import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.BoxLayout;
import javax.swing.border.*;
/**
 * A panel for a filter that has Integer/Float/Boolean/String/Object[] getter/setter methods (bound properties).
These methods are introspected and a set of controls are built for them. Enclosed filters and
filter chains have panels built for them that are enlosed inside the filter panel, hierarchically.
 * <p>
 * If a filter wants to automatically have the GUI controls reflect what the property state is, then it should 
 * fire PropertyChangeEvent when the property changes. For example, an EventFilter can implement a setter like this:
 * <pre>
public void setMapEventsToLearnedTopologyEnabled(boolean mapEventsToLearnedTopologyEnabled) {
support.firePropertyChange("mapEventsToLearnedTopologyEnabled", this.mapEventsToLearnedTopologyEnabled, mapEventsToLearnedTopologyEnabled); // property, old value, new value
this.mapEventsToLearnedTopologyEnabled = mapEventsToLearnedTopologyEnabled;
getPrefs().putBoolean("TopologyTracker.mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled);
}
</pre>
 * Here, <code>support</code> is a protected field of EventFilter. The change event comes here to FilterPanel and the appropriate automatically 
 * generated control is modified.
 * 
 *
 * @author  tobi
 */
public class FilterPanel extends javax.swing.JPanel implements PropertyChangeListener {
    private interface HasSetter {
        void set(Object o);
    }
    static final float ALIGNMENT=0;
    BeanInfo info;
    PropertyDescriptor[] props;
    Method[] methods;
    Logger log=Logger.getLogger("Filters");
    private EventFilter filter=null;
    final float fontSize=9f;
    private Border normalBorder,  redLineBorder;
    private TitledBorder titledBorder;
    HashMap<String, HasSetter> setterMap=new HashMap<String, HasSetter>(); // map from filter to property, to apply property change events to control

    /** Creates new form FilterPanel */
    public FilterPanel() {
        initComponents();
    }

    public FilterPanel(EventFilter f) {
//        log.info("building FilterPanel for "+f);
        this.setFilter(f);
        initComponents();
        String cn=getFilter().getClass().getName();
        int lastdot=cn.lastIndexOf('.');
        String name=cn.substring(lastdot+1);
        titledBorder=new TitledBorder(name);
        titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
        setBorder(titledBorder);
        normalBorder=titledBorder.getBorder();
        redLineBorder=BorderFactory.createLineBorder(Color.red);
        enabledCheckBox.setSelected(getFilter().isFilterEnabled());
        addIntrospectedControls();
        // when filter fires a property change event, we get called here and we update all our controls
        getFilter().getPropertyChangeSupport().addPropertyChangeListener(this);
        ToolTipManager.sharedInstance().setDismissDelay(10000); // to show tips
        setToolTipText(f.getDescription());
    }
    java.util.ArrayList<JComponent> controls=new ArrayList<JComponent>();
    // gets getter/setter methods for the filter and makes controls for them. enclosed filters are also added as submenus

    private void addIntrospectedControls() {
        JPanel control=null;
        EventFilter filter=getFilter();
        try {
            info=Introspector.getBeanInfo(filter.getClass());
            props=info.getPropertyDescriptors();
            methods=filter.getClass().getMethods();
            control=new JPanel();

            // first add buttons when the method name starts with "do". These methods are by convention associated with actions.
            // these methods, e.g. "void doDisableServo()" do an action.
            for(Method m : methods) {
                if(m.getName().startsWith("do")&&
                        m.getParameterTypes().length==0&&
                        m.getReturnType()==void.class) {
                    JButton button=new JButton(m.getName().substring(2));
                    button.setFont(button.getFont().deriveFont(9f));
                    final EventFilter f=filter;
                    final Method meth=m;
                    button.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            try {
                                meth.invoke(f);
                            } catch(IllegalArgumentException ex) {
                                ex.printStackTrace();
                            } catch(InvocationTargetException ex) {
                                ex.printStackTrace();
                            } catch(IllegalAccessException ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                    addTip(f, button);
                    control.add(button);
                }
            }

            //if at least one button then we show the actions panel
            if(control.getComponentCount()>0) {
                TitledBorder titledBorder=new TitledBorder("Filter Actions");
                titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
                control.setBorder(titledBorder);
                control.setMinimumSize(new Dimension(0, 0));
                add(control);
                controls.add(control);
            }


            // next add enclosed Filter and enclosed FilterChain so they appear at top of list (they are processed first)
            for(PropertyDescriptor p : props) {
                Class c=p.getPropertyType();
                if(p.getName().equals("enclosedFilter")) { //if(c==EventFilter2D.class){
                    // if type of property is an EventFilter, check if it has either an enclosed filter
                    // or an enclosed filter chain. If so, construct FilterPanels for each of them.
                    try {
                        Method r=p.getReadMethod(); // get the getter for the enclosed filter
                        EventFilter2D enclFilter=(EventFilter2D) (r.invoke(getFilter()));
                        if(enclFilter!=null) {
//                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses EventFilter2D "+enclFilter.getClass().getSimpleName());
                            FilterPanel enclPanel=new FilterPanel(enclFilter);
                            this.add(enclPanel);
                            controls.add(enclPanel);
                            ((TitledBorder) enclPanel.getBorder()).setTitle("enclosed: "+enclFilter.getClass().getSimpleName());
                        }
//                        FilterChain chain=getFilter().getEnclosedFilterChain();
//                        if(chain!=null){
//                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses filterChain "+chain);
//                            for(EventFilter f:chain){
//                                FilterPanel enclPanel=new FilterPanel(f);
//                                this.add(enclPanel);
//                                controls.add(enclPanel);
//                                ((TitledBorder)enclPanel.getBorder()).setTitle("enclosed: "+f.getClass().getSimpleName());
//                            }
//                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                } else if(p.getName().equals("enclosedFilterChain")) { //
                    // if type of property is a FilterChain, check if it has either an enclosed filter
                    // or an enclosed filter chain. If so, construct FilterPanels for each of them.
                    try {
                        Method r=p.getReadMethod(); // get the getter for the enclosed filter chain
                        FilterChain chain=(FilterChain) (r.invoke(getFilter()));
                        if(chain!=null) {
//                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses filterChain "+chain);
                            for(EventFilter f : chain) {
                                FilterPanel enclPanel=new FilterPanel(f);
                                this.add(enclPanel);
                                controls.add(enclPanel);
                                ((TitledBorder) enclPanel.getBorder()).setTitle("enclosed: "+f.getClass().getSimpleName());
                            }
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    String name=p.getName();
                    if(control!=null) {
                        control.setToolTipText(getFilter().getPropertyTooltip(name));
                    }
                }
            }

            // next add all other properties that we can handle
            for(PropertyDescriptor p : props) {
//                System.out.println("filter "+getFilter().getClass().getSimpleName()+" has property name="+p.getName()+" type="+p.getPropertyType());
//                if(false){
////                    System.out.println("prop "+p);
////                    System.out.println("prop name="+p.getName());
////                    System.out.println("prop write method="+p.getWriteMethod());
////                    System.out.println("prop read method="+p.getReadMethod());
////                    System.out.println("type "+p.getPropertyType());
////                    System.out.println("bound: "+p.isBound());
////                    System.out.println("");
//                }
                try {
                    Class c=p.getPropertyType();

//                if(c instanceof Class) System.out.println("filter="+filter+" propertyType="+c);
                    if(c==Integer.TYPE&&p.getReadMethod()!=null&&p.getWriteMethod()!=null) {
                        control=new IntControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        add(control);
                        controls.add(control);
                    } else if(c==Float.TYPE&&p.getReadMethod()!=null&&p.getWriteMethod()!=null) {
                        control=new FloatControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        add(control);
                        controls.add(control);
                    } else if(c==Boolean.TYPE&&p.getReadMethod()!=null&&p.getWriteMethod()!=null) {
                        if(p.getName().equals("filterEnabled")) {
                            continue;
                        }
                        if(p.getName().equals("annotationEnabled")) {
                            continue;
                        }
                        control=new BooleanControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        add(control);
                        controls.add(control);
                    } else if(c==String.class&&p.getReadMethod()!=null&&p.getWriteMethod()!=null) {
                        if(p.getName().equals("filterEnabled")) {
                            continue;
                        }
                        if(p.getName().equals("annotationEnabled")) {
                            continue;
                        }
                        control=new StringControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        add(control);
                        controls.add(control);
                    } else if(c.isEnum()&&p.getReadMethod()!=null&&p.getWriteMethod()!=null) {
                        control=new EnumControl(c, getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        add(control);
                        controls.add(control);
                    } else {
//                    log.warning("unknown property type "+p.getPropertyType()+" for property "+p.getName());
                    }
                    String name=p.getName();
                    if(control!=null) {
                        control.setToolTipText(getFilter().getPropertyTooltip(name));
                    }
                } catch(Exception e) {
                    log.warning(e+" caught on property "+p.getName());
                }
            }
        } catch(Exception e) {
            log.warning("caught "+e);
            e.printStackTrace();
        }
        add(Box.createHorizontalGlue());
        setControlsVisible(false);
//        System.out.println("added glue to "+this);
    }

    void addTip(EventFilter f, JLabel label) {
        String s=f.getPropertyTooltip(label.getText());
        if(s==null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);
    }

    void addTip(EventFilter f, JButton b) {
        String s=f.getPropertyTooltip(b.getText());
        if(s==null) {
            return;
        }
        b.setToolTipText(s);
        b.setForeground(Color.BLUE);
    }
    void addTip(EventFilter f, JCheckBox label) {
        String s=f.getPropertyTooltip(label.getText());
        if(s==null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);
    }
    class EnumControl extends JPanel implements HasSetter {
        Method write, read;
        EventFilter filter;
        boolean initValue=false, nval;
        final JComboBox control;

        public void set(Object o) {
            control.setSelectedItem(o);
        }

        public EnumControl(final Class<? extends Enum> c, final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
            final JLabel label=new JLabel(name);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            control=new JComboBox(c.getEnumConstants());
            control.setFont(control.getFont().deriveFont(fontSize));
//            control.setHorizontalAlignment(SwingConstants.LEADING);

            add(label);
            add(control);
            try {
                Object x=(Object) r.invoke(filter);
                if(x==null) {
                    log.warning("null Object returned from read method "+r);
                    return;
                }
                control.setSelectedItem(x);
            } catch(Exception e) {
                e.printStackTrace();
            }
            control.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(filter, control.getSelectedItem());
                    } catch(Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }
    }
    class StringControl extends JPanel implements HasSetter {
        Method write, read;
        EventFilter filter;
        boolean initValue=false, nval;
        final JTextField textField;

        public void set(Object o) {
            if(o instanceof String) {
                String b=(String) o;
                textField.setText(b);
            }
        }

        public StringControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
            final JLabel label=new JLabel(name);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            textField=new JTextField(name);
            textField.setFont(textField.getFont().deriveFont(fontSize));
            textField.setHorizontalAlignment(SwingConstants.LEADING);

            add(label);
            add(textField);
            try {
                String x=(String) r.invoke(filter);
                if(x==null) {
                    log.warning("null String returned from read method "+r);
                    return;
                }
                textField.setText(x);
            } catch(Exception e) {
                e.printStackTrace();
            }
            textField.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(filter, textField.getText());
                    } catch(Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }
    }
    final float factor=1.51f,  wheelFactor=1.05f; // factors to change by with arrow and mouse wheel
    class BooleanControl extends JPanel implements HasSetter {
        Method write, read;
        EventFilter filter;
        boolean initValue=false, nval;
        final JCheckBox checkBox;

        public BooleanControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            checkBox=new JCheckBox(name);
            checkBox.setFont(checkBox.getFont().deriveFont(fontSize));
            checkBox.setHorizontalTextPosition(SwingConstants.LEFT);
            addTip(f, checkBox);
            add(checkBox);
            try {
                Boolean x=(Boolean) r.invoke(filter);
                if(x==null) {
                    System.err.println("null Boolean returned from read method "+r);
                    return;
                }
                initValue=x.booleanValue();
                checkBox.setSelected(initValue);
            } catch(InvocationTargetException e) {
                e.printStackTrace();
            } catch(IllegalAccessException e) {
                e.printStackTrace();
            }
            checkBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(filter, checkBox.isSelected());
                    } catch(InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch(IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
        }

        public void set(Object o) {
            if(o instanceof Boolean) {
                Boolean b=(Boolean) o;
                checkBox.setSelected(b);
            }
        }
    }
    class IntControl extends JPanel implements HasSetter {
        Method write, read;
        EventFilter filter;
        int initValue=0, nval;
        final JTextField tf;

        public void set(Object o) {
            if(o instanceof Integer) {
                Integer b=(Integer) o;
                tf.setText(b.toString());
            }
        }

        public IntControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label=new JLabel(name);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            tf=new JTextField("", 4);
            tf.setToolTipText("Integer control: use arrow keys or mouse wheel to change value by factor. Shift constrains to simple inc/dec");
            try {
                Integer x=(Integer) r.invoke(filter); // read int value
                if(x==null) {
                    System.err.println("null Integer returned from read method "+r);
                    return;
                }
                initValue=x.intValue();
                String s=Integer.toString(x);
//                System.out.println("init value of "+name+" is "+s);
                tf.setText(s);
            } catch(Exception e) {
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
//                    System.out.println(e);
                    try {







                        int y=Integer.parseInt(
                                tf.getText());
                        w.invoke(filter, new Integer(y)); // write int value
                    } catch(NumberFormatException fe) {
                        tf.selectAll();
                    } catch(InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch(IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    try {
                        Integer x=(Integer) r.invoke(filter);
                        initValue=x.intValue();
//                        System.out.println("x="+x);
                    } catch(InvocationTargetException e) {
                        e.printStackTrace();
                    } catch(IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code=evt.getKeyCode();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    if(!shift) {
                        if(code==KeyEvent.VK_UP) {
                            try {
                                nval=initValue;
                                if(nval==0) {
                                    nval=1;
                                } else {
                                    nval=(int) Math.round(initValue*factor);
                                }
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if(code==KeyEvent.VK_DOWN) {
                            try {
                                nval=initValue;
                                if(nval==0) {
                                    nval=0;
                                } else {
                                    nval=(int) Math.round(initValue/factor);
                                }
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        }
                    } else {
                        // shifted int control just incs or decs by 1
                        if(code==KeyEvent.VK_UP) {
                            try {
                                nval=initValue+1;
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if(code==KeyEvent.VK_DOWN) {
                            try {
                                nval=initValue-1;
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        }

                    }

                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try {
                        Integer x=(Integer) r.invoke(filter);
                        initValue=x.intValue();
//                        System.out.println("x="+x);
                    } catch(InvocationTargetException e) {
                        e.printStackTrace();
                    } catch(IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code=evt.getWheelRotation();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    if(!shift) {
                        if(code<0) {
                            try {
                                nval=initValue;
                                if(Math.round(initValue*wheelFactor)==initValue) {
                                    nval++;
                                } else {
                                    nval=(int) Math.round(initValue*wheelFactor);
                                }
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if(code>0) {
                            try {
                                nval=initValue;
                                if(Math.round(initValue/wheelFactor)==initValue) {
                                    nval--;
                                } else {
                                    nval=(int) Math.round(initValue/wheelFactor);
                                }
                                if(nval<0) {
                                    nval=0;
                                }
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
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

    void fixIntValue(JTextField tf, Method r) {
        // set text to actual value
        try {
            Integer x=(Integer) r.invoke(getFilter()); // read int value
//            initValue=x.intValue();
            String s=Integer.toString(x);
            tf.setText(s);
        } catch(Exception e) {
            e.printStackTrace();
        }






    }
    class FloatControl extends JPanel implements HasSetter {
        Method write, read;
        EventFilter filter;
        float initValue=0, nval;
        final JTextField tf;

        public void set(Object o) {
            if(o instanceof Float) {
                Float b=(Float) o;
                tf.setText(b.toString());
            }
        }

        public FloatControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter=f;
            write=w;
            read=r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label=new JLabel(name);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);
            tf=new JTextField("", 7);
            tf.setToolTipText("Float control: use arrow keys or mouse wheel to change value by factor. Shift reduces factor.");
            try {
                Float x=(Float) r.invoke(filter);
                if(x==null) {
                    System.err.println("null Float returned from read method "+r);
                    return;
                }
                initValue=x.floatValue();
                String s=String.format("%.4f", initValue);
                tf.setText(s);
            } catch(InvocationTargetException e) {
                e.printStackTrace();
            } catch(IllegalAccessException e) {
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
//                    System.out.println(e);
                    try {
                        float y=Float.parseFloat(tf.getText());
                        w.invoke(filter, new Float(y));
                        Float x=(Float) r.invoke(filter); // get the value from the getter method to constrain it
                        nval=x.floatValue();
                        tf.setText(String.format("%.4f", nval));
                    } catch(NumberFormatException fe) {
                        tf.selectAll();
                    } catch(InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch(IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {
                {
                }

                public void keyPressed(java.awt.event.KeyEvent evt) {
                    try {
                        Float x=(Float) r.invoke(filter); // get the value from the getter method
                        initValue=x.floatValue();
//                        System.out.println("x="+x);
                    } catch(InvocationTargetException e) {
                        e.printStackTrace();
                    } catch(IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code=evt.getKeyCode();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    float floatFactor=factor;
                    if(shift) {
                        floatFactor=wheelFactor;
                    }
                    if(code==KeyEvent.VK_UP) {
                        try {
                            nval=initValue;
                            if(nval==0) {
                                nval=.1f;
                            } else {
                                nval=(initValue*floatFactor);
                            }
                            w.invoke(filter, new Float(nval)); // setter the value
                            Float x=(Float) r.invoke(filter); // get the value from the getter method to constrain it
                            nval=x.floatValue();
                            tf.setText(String.format("%.4f", nval));
                        } catch(InvocationTargetException ite) {
                            ite.printStackTrace();
                        } catch(IllegalAccessException iae) {
                            iae.printStackTrace();
                        }
                    } else if(code==KeyEvent.VK_DOWN) {
                        try {
                            nval=initValue;
                            if(nval==0) {
                                nval=.1f;
                            } else {
                                nval=(initValue/floatFactor);
                            }
                            w.invoke(filter, new Float(initValue/floatFactor));
                            Float x=(Float) r.invoke(filter); // get the value from the getter method to constrain it
                            nval=x.floatValue();
                            tf.setText(String.format("%.4f", nval));
                        } catch(InvocationTargetException ite) {
                            ite.printStackTrace();
                        } catch(IllegalAccessException iae) {
                            iae.printStackTrace();
                        }
                    }
                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try {
                        Float x=(Float) r.invoke(filter); // get the value from the getter method
                        initValue=x.floatValue();
//                        System.out.println("x="+x);
                    } catch(InvocationTargetException e) {
                        e.printStackTrace();
                    } catch(IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code=evt.getWheelRotation();
                    int mod=evt.getModifiers();
                    boolean shift=evt.isShiftDown();
                    if(!shift) {
                        if(code<0) {
                            try {
                                nval=initValue;
                                if(nval==0) {
                                    nval=.1f;
                                } else {
                                    nval=(initValue*wheelFactor);
                                }
                                w.invoke(filter, new Float(nval)); // setter the value
                                Float x=(Float) r.invoke(filter); // get the value from the getter method to constrain it
                                nval=x.floatValue();
                                tf.setText(String.format("%.4f", nval));
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if(code>0) {
                            try {
                                nval=initValue;
                                if(nval==0) {
                                    nval=.1f;
                                } else {
                                    nval=(initValue/wheelFactor);
                                }
                                w.invoke(filter, new Float(initValue/wheelFactor));
                                Float x=(Float) r.invoke(filter); // get the value from the getter method to constrain it
                                nval=x.floatValue();
                                tf.setText(String.format("%.4f", nval));
                            } catch(InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch(IllegalAccessException iae) {
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

    /** Called when a filter calls firePropertyChange. The PropertyChangeEvent should send the bound property name and the old and new values.
    The GUI control is then updated by this method.
    @param propertyChangeEvent contains the property that has changed, e.g. it would be called from an EventFilter 
     * with 
     * <code>support.firePropertyChange("mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled, this.mapEventsToLearnedTopologyEnabled);</code>
     */
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        if(propertyChangeEvent.getSource()==getFilter()) {
            if(propertyChangeEvent.getPropertyName().equals("filterEnabled")) { // comes from EventFilter when filter is enabled or disabled
//            log.info("propertyChangeEvent name="+propertyChangeEvent.getPropertyName()+" src="+propertyChangeEvent.getSource()+" oldValue="+propertyChangeEvent.getOldValue()+" newValue="+propertyChangeEvent.getNewValue());
                boolean yes=(Boolean) propertyChangeEvent.getNewValue();
                enabledCheckBox.setSelected(yes);
                setBorderActive(yes);
            } else {
                // we need to find the control and set it appropriately. we don't need to set the property itself since this has already been done!
                try {
//                    log.info("PropertyChangeEvent received from " +
//                            propertyChangeEvent.getSource() + " for property=" +
//                            propertyChangeEvent.getPropertyName() +
//                            " newValue=" + propertyChangeEvent.getNewValue());
                    HasSetter setter=setterMap.get(propertyChangeEvent.getPropertyName());
                    setter.set(propertyChangeEvent.getNewValue());

//                    PropertyDescriptor pd=new PropertyDescriptor(propertyChangeEvent.getPropertyName(), getFilter().getClass());
//                    Method wm=pd.getWriteMethod();
//                    wm.invoke(getFilter(), propertyChangeEvent.getNewValue());
                } catch(Exception e) {
                    log.warning(e.toString());
                }
//                  try{
//                    log.info("PropertyChangeEvent received for property="+propertyChangeEvent.getPropertyName()+" newValue="+propertyChangeEvent.getNewValue());
//                    PropertyDescriptor pd=new PropertyDescriptor(propertyChangeEvent.getPropertyName(), getFilter().getClass());
//                    Method wm=pd.getWriteMethod();
//                    wm.invoke(getFilter(), propertyChangeEvent.getNewValue());
//                }catch(Exception e){
//                    log.warning(e.toString());
//                }
            }
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
        jPanel1.setPreferredSize(new java.awt.Dimension(100, 23));
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

    public boolean isControlsVisible() {
        return controlsVisible;
    }
// true to show filter parameter controls

    void setControlsVisible(boolean yes) {
        controlsVisible=yes;
        setBorderActive(yes);
        for(JComponent p : controls) {
            p.setVisible(yes);
            p.invalidate();
        }

        invalidate();
        Container c=getTopLevelAncestor();
        if(!getFilter().isEnclosed()&&c!=null&&c instanceof Window) {
            if(c instanceof FilterFrame) {
                // hide all filters except one that is being modified, *unless* we are an enclosed filter
                FilterFrame ff=(FilterFrame) c;
                for(FilterPanel f : ff.filterPanels) {
                    if(f==this) {
                        continue;  // don't do anything to ourselves
                    }

                    f.setVisible(!yes); // hide other filters
                }

            }
            ((Window) c).pack();
        }

        if(c!=null&&c instanceof Window) {
            ((Window) c).pack();
        }

    }

    private void setBorderActive(final boolean yes) {
        // see http://forum.java.sun.com/thread.jspa?threadID=755789
        if(yes) {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textText);
            titledBorder.setBorder(redLineBorder);
        } else {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textInactiveText);
            titledBorder.setBorder(normalBorder);
        }

    }

    void toggleControlsVisible() {
        controlsVisible=!controlsVisible;
        setControlsVisible(controlsVisible);
    }

    public EventFilter getFilter() {
        return filter;
    }

    public void setFilter(EventFilter filter) {
        this.filter=filter;
    }

    private void showControlsToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showControlsToggleButtonActionPerformed
        toggleControlsVisible();
    }//GEN-LAST:event_showControlsToggleButtonActionPerformed

    private void enabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enabledCheckBoxActionPerformed
        boolean yes=enabledCheckBox.isSelected();
        if(getFilter()!=null) {
            getFilter().setFilterEnabled(yes);
        }

        if(yes) {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textText);
            titledBorder.setBorder(redLineBorder);
        } else {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textInactiveText);
            titledBorder.setBorder(normalBorder);
        }

        repaint();
    }//GEN-LAST:event_enabledCheckBoxActionPerformed

    private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetButtonActionPerformed
        if(getFilter()!=null) {
            getFilter().resetFilter();
        }
    }//GEN-LAST:event_resetButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox enabledCheckBox;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton resetButton;
    private javax.swing.JToggleButton showControlsToggleButton;
    // End of variables declaration//GEN-END:variables
}

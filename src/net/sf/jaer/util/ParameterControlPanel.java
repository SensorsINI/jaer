/*
 * ParameterControlPanel.java
 *
 * Adapted from FilterPanel Nov 2011
 */
package net.sf.jaer.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * A panel for a class that has Integer/Float/Boolean/String/enum getter/setter methods (bound properties).
These methods are introspected and a set of controls are built for them. 
 * <ul>
 * <li>Numerical properties (ints, floats, but not currently doubles) construct a JTextBox control that also allows changes from mouse wheel or arrow keys.
 * <li> boolean properties construct a JCheckBox control.
 * <li> String properties construct a JTextField control.
 * <li> enum properties construct a JComboBox control, which all the possible enum constant values.
 * </ul>
 * <p>
 * If a class wants to automatically have the GUI controls reflect what the property state is, then it should
 * fire PropertyChangeEvent when the property changes. For example, an {@link Class} can implement a setter like this:
 * <pre>
public void setMapEventsToLearnedTopologyEnabled(boolean mapEventsToLearnedTopologyEnabled) {
    support.firePropertyChange("mapEventsToLearnedTopologyEnabled", this.mapEventsToLearnedTopologyEnabled, mapEventsToLearnedTopologyEnabled); // property, old value, new value
    this.mapEventsToLearnedTopologyEnabled = mapEventsToLearnedTopologyEnabled;
    getPrefs().putBoolean("TopologyTracker.mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled);
}
</pre>
 * Here, <code>support</code> is a protected field of Class. The change event comes here and the appropriate automatically
 * generated control is modified.
 * <p>
 * Note that calling firePropertyChange as shown above will inform listeners <em>before</em> the property has actually been
 * changed (this.dt has not been set yet).
 * <p>
 * A tooltip for the property can be installed using the Class setPropertyTooltip method, for example
 * <pre>
 *         setPropertyTooltip("sizeClassificationEnabled", "Enables coloring cluster by size threshold");
 * </pre>
 * will install a tip for the property sizeClassificationEnabled.
 * <p>
 * <strong>Slider controls.</strong>
 * 
 * If you want a slider for an int or float property, then create getMin and getMax methods for the property, e.g., for
 * the property <code>dt</code>:
 * <pre>
public int getDt() {
return this.dt;
}

public void setDt(final int dt) {
getPrefs().putInt("BackgroundActivityFilter.dt",dt);
support.firePropertyChange("dt",this.dt,dt);
this.dt = dt;
}

public int getMinDt(){
return 10;
}

public int getMaxDt(){
return 100000;
}
</pre>
 * <strong>Button control</strong>
 * <p>
 * To add a button control to a panel, implement a method starting with "do", e.g.
 * <pre>
 *     public void doSendParameters() {
sendParameters();
}
 * </pre>
 * This method will construct a button with label "SendParameters" which, when pressed, will call the method "doSendParameters".
 * <p>
 * <strong>
 * Grouping parameters.</strong>
 * <p>
 * Properties are normally sorted alphabetically, with button controls at the top. If you want to group parameters, use
 * the built in Class method {@link net.sf.jaer.eventprocessing.Class#addPropertyToGroup}. All properties of a given group are grouped together. Within
 * a group the parameters are sorted alphabetically, and the groups will also be sorted alphabetically and shown before
 * any ungrouped parameters. E.g., to Create groups "Sizing" and "Tracking" and add properties to each, do
 * <pre>
addPropertyToGroup("Sizing", "clusterSize");
addPropertyToGroup("Sizing", "aspectRatio");
addPropertyToGroup("Sizing", "highwayPerspectiveEnabled");
addPropertyToGroup("Tracking", "mixingFactor");
addPropertyToGroup("Tracking", "velocityMixingFactor");
 * </pre>
 * Or, even simpler, if you have already defined tooltips for your properties, then
 * you can use the overloaded
 * {@link net.sf.jaer.eventprocessing.Class#setPropertyTooltip(java.lang.String, java.lang.String, java.lang.String) setPropertyTooltip} of
 * {@link net.sf.jaer.eventprocessing.Class},
 * as shown next. Here two groups "Size" and "Timing" are defined and properties are added to each (or to neither for "multiOriOutputEnabled").
 * <pre>
final String size="Size", tim="Timing";

setPropertyTooltip(disp,"showGlobalEnabled", "shows line of average orientation");
setPropertyTooltip(tim,"minDtThreshold", "Coincidence time, events that pass this coincidence test are considerd for orientation output");
setPropertyTooltip(tim,"dtRejectMultiplier", "reject delta times more than this factor times minDtThreshold to reduce noise");
setPropertyTooltip(tim,"dtRejectThreshold", "reject delta times more than this time in us to reduce effect of very old events");
setPropertyTooltip("multiOriOutputEnabled", "Enables multiple event output for all events that pass test");
</pre>
 *
 *
 * @author  tobi
 * @see net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String, java.lang.String)
 *@see net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String, java.lang.String, java.lang.String)
 */
public class ParameterControlPanel extends javax.swing.JPanel implements PropertyChangeListener, Observer {

    /** Handles Observable updates from the class we are handling.   If the class is an Observable, we will hear changes here if the class notifies its observers.
     * These changes will set the introspected controls.
     * 
     * If the object has inner classes that generate the events then they will not be seen unless passed on by class.
     @param o the observed object
     @param arg any argument passed
     */
    @Override
    public void update(Observable o, Object arg) {
//        log.info("got update from object "+o+" with argument "+arg);
        // if we refresh any refresh, then refresh all controls displayed because we don't know which one should be updated
        for(JComponent c:controls){
            if(c instanceof HasSetGet){
                HasSetGet gs=(HasSetGet)c;
                gs.refresh();
            }
        }
        
    }

    private interface HasSetGet {

        void set(Object o);
        void refresh();
        
    }
    private Object classObject = null;
    static final float ALIGNMENT = Component.LEFT_ALIGNMENT;
    private BeanInfo info;
    private PropertyDescriptor[] props;
    private Method[] methods;
    private static final Logger log = Logger.getLogger("Parameters");
    final float fontSize = 14f;
    private Border normalBorder, redLineBorder;
    private TitledBorder titledBorder;
    private HashMap<String, HasSetGet> setterMap = new HashMap<>(); // map from class to property, to apply property change events to control
    private java.util.ArrayList<JComponent> controls = new ArrayList<>();
//    private HashMap<String, Container> groupContainerMap = new HashMap();
//    private JPanel inheritedPanel = null;
    PropertyChangeSupport support=null;
    public ParameterControlPanel() {
        initComponents();
    }

    /** Builds a new panel around the Object f. Any properties of the Object (defined by matching <code>setX/getX</code> methods)
     * are added to the panel. If the object implements {@link HasPropertyTooltips} then a tooltip is added for the property if the 
     * {@link HasPropertyTooltips#getPropertyTooltip(java.lang.String) } returns a non-null String property.
     * 
     * @param obj the object
     */
    public ParameterControlPanel(Object obj) {
        setClazz(obj);
        initComponents();
        String cn = getClazz().getClass().getName();
        int lastdot = cn.lastIndexOf('.');
        String name = cn.substring(lastdot + 1);
        setName(name);
        titledBorder = new TitledBorder(name);
        titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
        setBorder(titledBorder);
        normalBorder = titledBorder.getBorder();
        redLineBorder = BorderFactory.createLineBorder(Color.red);
        addIntrospectedControls();
//            add(new JPanel()); // to fill vertical space in GridLayout
            add(Box.createVerticalGlue()); // to fill space at bottom - not needed
        try {
            // when clazz fires a property change event, propertyChangeEvent is called here and we refresh all our controls
            Method m=obj.getClass().getMethod("getPropertyChangeSupport", (Class[])null);
            support=(PropertyChangeSupport)m.invoke(obj, (Object[]) null);
            support.addPropertyChangeListener(this);
        } catch (Exception ex) {
            
        } 
//            if(f instanceof PropertyChangeListener){
//        ((PropertyChangeListener)f).getPropertyChangeSupport().addPropertyChangeListener(this);
//                }
        ToolTipManager.sharedInstance().setDismissDelay(10000); // to show tips
        revalidate();
    }

    // checks for group container and adds to that if needed.
    private void myadd(JComponent comp, String propertyName, boolean inherited) {
//        JPanel pan = new JPanel();
////      pan.setLayout(new GridLayout(0,1));
//        pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));
//        controls.add(pan);
////            add(Box.createHorizontalGlue());
////            comp.setAlignmentX(Component.LEFT_ALIGNMENT);
//        comp.setAlignmentX(Component.RIGHT_ALIGNMENT);
//        pan.add(comp);
//        pan.add(Box.createVerticalStrut(0));
//            pan.add(Box.createVerticalGlue());
        add(comp); // to fix horizontal all left alignment
        controls.add(comp);
    }

    // gets getter/setter methods for the class and makes controls for them.
    private void addIntrospectedControls() {
        JPanel control = null;
        try {
            info = Introspector.getBeanInfo(classObject.getClass());
            
            // add refresh observer if the object is an Observable. Inner classes of classObject that generate updates will not be observed.
            if(classObject instanceof Observable){
                ((Observable)classObject).addObserver(this);
            }

            // refresh properties (refresh/set methods for object)
            props = info.getPropertyDescriptors();
            // refresh all methods
            methods = classObject.getClass().getMethods();
            control = new JPanel();
            int numDoButtons = 0;
            // first add buttons when the method name starts with "do". These methods are by convention associated with actions.
            // these methods, e.g. "void doDisableServo()" do an action.
            Insets butInsets = new Insets(0, 0, 0, 0);
            for (Method m : methods) {
                if (m.getName().startsWith("do")
                        && m.getParameterTypes().length == 0
                        && m.getReturnType() == void.class) {
                    numDoButtons++;
                    JButton button = new JButton(m.getName().substring(2));
                    button.setMargin(butInsets);
                    button.setFont(button.getFont().deriveFont(9f));
                    final Object obj = classObject;
                    final Method meth = m;
                    button.addActionListener(new ActionListener() {

                        public void actionPerformed(ActionEvent e) {
                            try {
                                meth.invoke(obj);
                            } catch (IllegalArgumentException ex) {
                                ex.printStackTrace();
                            } catch (InvocationTargetException ex) {
                                ex.printStackTrace();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                    control.add(button);
                }
            }

            //if at least one button then we show the actions panel
            if (control.getComponentCount() > 0) {
                TitledBorder tb = new TitledBorder("Actions");
                tb.getBorderInsets(this).set(1, 1, 1, 1);
                control.setBorder(tb);
                control.setMinimumSize(new Dimension(0, 0));
                add(control);
                controls.add(control);
            }

            if (numDoButtons > 3) {
                control.setLayout(new GridLayout(0, 3, 3, 3));
            }


            // next add all other properties that we can handle
            // these must be saved and then sorted in case there are property groups defined.


//            ArrayList<Component> sortedControls=new ArrayList();
            for (PropertyDescriptor p : props) {
//                System.out.println("clazz "+getClazz().getClass().getSimpleName()+" has property name="+p.getName()+" type="+p.getPropertyType());
//                if(true){
//                    System.out.println("prop name="+p.getName());
//                    System.out.println("prop description="+p.getShortDescription());
//                    System.out.println("prop write method="+p.getWriteMethod());
//                    System.out.println("prop read method="+p.getReadMethod());
//                    System.out.println("type "+p.getPropertyType());
//                    System.out.println("bound: "+p.isBound());
//                    System.out.println("");
//                }
                try {
                    boolean inherited = false;

                    // TODO handle indexed properties 
                    Class c = p.getPropertyType();
                    String name = p.getName();

                    // check if method comes from a superclass of this Class
                    if (control != null && p.getReadMethod() != null && p.getWriteMethod() != null) {
                        Method m = p.getReadMethod();
                        if (m.getDeclaringClass() != getClazz().getClass()) {
                            inherited = true;
                        }
                    }

                    if (c == Integer.TYPE && p.getReadMethod() != null && p.getWriteMethod() != null) {

                        SliderParams params;
                        if ((params = isSliderType(p, classObject)) != null) {
                            control = new IntSliderControl(getClazz(), p, params);
                        } else {
                            control = new IntControl(getClazz(), p);
                        }
                        myadd(control, name, inherited);
                    } else if (c == Float.TYPE && p.getReadMethod() != null && p.getWriteMethod() != null) {
                        SliderParams params;
                        if ((params = isSliderType(p, classObject)) != null) {
                            control = new FloatSliderControl(getClazz(), p, params);
                        } else {
                            control = new FloatControl(getClazz(), p);
                        }
                        myadd(control, name, inherited);
                    } else if (c == Boolean.TYPE && p.getReadMethod() != null && p.getWriteMethod() != null) {
                        control = new BooleanControl(getClazz(), p);
                        myadd(control, name, inherited);
                    } else if (c == String.class && p.getReadMethod() != null && p.getWriteMethod() != null) {
                        control = new StringControl(getClazz(), p);
                        myadd(control, name, inherited);
                    } else if (c != null && c.isEnum() && p.getReadMethod() != null && p.getWriteMethod() != null) {
                        control = new EnumControl(c, getClazz(), p);
                        myadd(control, name, inherited);
                    } else {
//                    log.warning("unknown property type "+p.getPropertyType()+" for property "+p.getName());
                    }
//                    if (control != null) {
//                        control.setToolTipText(getClazz().getPropertyTooltip(name));
//                    }
                } catch (Exception e) {
                    log.warning(e + " caught on property " + p.getName() + " from class " + classObject);
                }
            }
//            groupContainerMap = null;
//             sortedControls=null;
        } catch (Exception e) {
            log.warning("on adding controls for " + classObject + " caught " + e);
            e.printStackTrace();
        }
//        add(Box.createHorizontalGlue());
//        setControlsVisible(false);
//        System.out.println("added glue to "+this);
    }

    private String getTip(PropertyDescriptor p) {
        String tip = null;
        if (classObject instanceof HasPropertyTooltips) {
            tip = ((HasPropertyTooltips) classObject).getPropertyTooltip(p.getName());
        } else {
            tip = p.getShortDescription();
        }
        return tip;
    }
    
    void addTip(PropertyDescriptor p, JLabel label) {

        
        label.setToolTipText(getTip(p));
        label.setForeground(Color.BLUE);
    }

    void addTip(PropertyDescriptor p, JButton b) {
        b.setToolTipText(getTip(p));
        b.setForeground(Color.BLUE);
    }

    void addTip(PropertyDescriptor p, JCheckBox label) {
        label.setToolTipText(getTip(p));
        label.setForeground(Color.BLUE);
    }

    class EnumControl extends JPanel implements HasSetGet {

        Method write, read;
        Object clazz;
        boolean initValue = false, nval;
        final JComboBox control;

        @Override
        public void set(Object o) {
            control.setSelectedItem(o);
        }

        @Override
        public void refresh() {
              try {
                Object x = (Object) read.invoke(clazz);
                if (x == null) {
                    log.warning("null Object returned from read method " + read);
                    return;
                }
                control.setSelectedItem(x);
            } catch (Exception e) {
                log.warning("cannot access the field; is the class or method not public?");
                e.printStackTrace();
            }          
        }
        
        public EnumControl(final Class<? extends Enum> c, final Object f, PropertyDescriptor p) {
            super();
            final String name = p.getName();
            final Method r = p.getReadMethod(), w = p.getWriteMethod();

            setterMap.put(name, this);
            clazz = f;
            write = w;
            read = r;
            setLayout(new GridLayout(1, 0));
//            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(p, label);
            add(label);

            control = new JComboBox(c.getEnumConstants());
            control.setFont(control.getFont().deriveFont(fontSize));
//            control.setHorizontalAlignment(SwingConstants.LEADING);

            add(label);
            add(control);
            refresh();
            control.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(clazz, control.getSelectedItem());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }


    }

    class StringControl extends JPanel implements HasSetGet {

        Method write, read;
        Object clazz;
        boolean initValue = false, nval;
        final JTextField textField;

        @Override
        public void set(Object o) {
            if (o instanceof String) {
                String b = (String) o;
                textField.setText(b);
            }
        }

        public StringControl(final Object f, PropertyDescriptor p) {
            super();
            final String name = p.getName();
            final Method r = p.getReadMethod(), w = p.getWriteMethod();

            setterMap.put(name, this);
            clazz = f;
            write = w;
            read = r;
            setLayout(new GridLayout(1, 0));
//            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(p, label);
            add(label);

            textField = new JTextField(name);
            textField.setFont(textField.getFont().deriveFont(fontSize));
            textField.setHorizontalAlignment(SwingConstants.LEADING);
            textField.setColumns(10);

            add(label);
            add(textField);
            refresh();
            textField.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(clazz, textField.getText());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void refresh() {
             try {
                String x = (String) read.invoke(clazz);
                if (x == null) {
                    log.warning("null String returned from read method " + read);
                    return;
                }
                textField.setText(x);
                textField.setToolTipText(x);
            } catch (Exception e) {
                log.warning("cannot access the field; is the class or method not public?");
                e.printStackTrace();
            }
        }
    }
    final float factor = 2.04f, wheelFactor = 1.05f; // factors to change by with arrow and mouse wheel.  int factor big enough to change at least 1 count from 1

    class BooleanControl extends JPanel implements HasSetGet {

        Method write, read;
        Object clazz;
        boolean initValue = false, nval;
        final JCheckBox checkBox;

        public BooleanControl(final Object f, PropertyDescriptor p) {
            super();
            final String name = p.getName();
            final Method r = p.getReadMethod(), w = p.getWriteMethod();

            setterMap.put(name, this);
            clazz = f;
            write = w;
            read = r;
            setLayout(new GridLayout(1, 0));
//         setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label=new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setHorizontalTextPosition(SwingConstants.LEFT);
            label.setFont(label.getFont().deriveFont(fontSize));
            add(label);
            addTip(p,label);

            checkBox = new JCheckBox();
            refresh();
            checkBox.setAlignmentX(ALIGNMENT);
            checkBox.setHorizontalAlignment(SwingConstants.LEFT);
            checkBox.setHorizontalTextPosition(SwingConstants.LEFT);
//            checkBox.setBorder(new EmptyBorder(0,0,0,0));
//            Border border=checkBox.getBorder();
//            Insets insets=border.getBorderInsets(this);
//            insets.left=0;
            addTip(p, checkBox);
            add(checkBox);

            checkBox.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(clazz, checkBox.isSelected());
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (Exception iae) {
                        iae.printStackTrace();
                    }
                }
            });
        }

        public void set(Object o) {
            if (o instanceof Boolean) {
                Boolean b = (Boolean) o;
                checkBox.setSelected(b);
            }
        }

        @Override
        public void refresh() {
              try {
                Boolean x = (Boolean) read.invoke(clazz);
                if (x == null) {
                    log.warning("null Boolean returned from read method " + read);
                    return;
                }
                initValue = x.booleanValue();
                checkBox.setSelected(initValue);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (Exception e) {
                log.warning("cannot access the field; is the class or method not public?");
                e.printStackTrace();
            }
        }
    }

    class IntSliderControl extends JPanel implements HasSetGet {

        Method write, read;
        Object clazz;
        int initValue = 0, nval;
        JSlider slider;
        JTextField tf;

        public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                slider.setValue(b);
            }
        }
        
        @Override
        public void refresh() {
             try {
                Integer x = (Integer) read.invoke(clazz); // read int value
                if (x == null) {
                    log.warning("null Integer returned from read method " + read);
                    return;
                }
                initValue = x.intValue();
                slider.setValue(initValue);
            } catch (Exception e) {
                log.warning("cannot access the field; is the class or method not public?");
                e.printStackTrace();
            }       
        }

        public IntSliderControl(final Object f, PropertyDescriptor p, SliderParams params) {
            super();
            final String name = p.getName();
            final Method r = p.getReadMethod(), w = p.getWriteMethod();
            setterMap.put(name, this);
            clazz = f;
            write = w;
            read = r;
            setLayout(new GridLayout(1, 0));
//           setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);

            final IntControl ic = new IntControl(f, p);
            add(ic);
            slider = new JSlider(params.minIntValue, params.maxIntValue);
            slider.setMaximumSize(new Dimension(200, 25));

            refresh();
            add(slider);

            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    try {
                        w.invoke(clazz, new Integer(slider.getValue())); // write int value
                        ic.set(slider.getValue());
//                        tf.setText(Integer.toString(slider.getValue()));
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (Exception iae) {
                        iae.printStackTrace();
                    }
                }
            });

        }
    }

    class FloatSliderControl extends JPanel implements HasSetGet {

        Method write, read;
        Object clazz;
        JSlider slider;
        JTextField tf;
        EngineeringFormat engFmt;
        FloatControl fc;
        boolean dontProcessEvent = false; // to avoid slider callback loops
        float minValue, maxValue, currentValue;

        public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                slider.setValue(b);
                fc.set(b);
            } else if (o instanceof Float) {
                float f = (Float) o;
                int sv = Math.round((f - minValue) / (maxValue - minValue) * (slider.getMaximum() - slider.getMinimum()));
                slider.setValue(sv);
            }
        }
        
         @Override
        public void refresh() {
             try {
                Float x = (Float) read.invoke(clazz); // read int value
                if (x == null) {
                    log.warning("null Float returned from read method " + read);
                    return;
                }
                currentValue = x.floatValue();
                set(new Float(currentValue));
            } catch (Exception e) {
                log.warning("cannot access the field, is the class or method not public?");
                e.printStackTrace();
            }
         }

        public FloatSliderControl(final Object f, PropertyDescriptor p, SliderParams params) {
            super();
            final String name = p.getName();
            final Method r = p.getReadMethod(), w = p.getWriteMethod();
            setterMap.put(name, this);
            clazz = f;
            write = w;
            read = r;
            setLayout(new GridLayout(1, 0));
//           setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);

            fc = new FloatControl(f, p);
            add(fc);
            minValue = params.minFloatValue;
            maxValue = params.maxFloatValue;
            slider = new JSlider();
            slider.setMaximumSize(new Dimension(200, 25));

            engFmt = new EngineeringFormat();

            refresh();
            add(slider);

            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    try {
                        int v = slider.getValue();
                        currentValue = minValue + (maxValue - minValue) * ((float) slider.getValue() / (slider.getMaximum() - slider.getMinimum()));
                        w.invoke(clazz, new Float(currentValue)); // write int value
                        fc.set(new Float(currentValue));

//                        tf.setText(engFmt.format(currentValue));
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (Exception iae) {
                        iae.printStackTrace();
                    }
                }
            });
        }
      }

    class IntControl extends JPanel implements HasSetGet {

        Method write, read;
        Object clazz;
        int initValue = 0, nval;
        final JTextField tf;

        @Override
        public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                tf.setText(b.toString());
            }
        }
      
        @Override
        public void refresh() {
             try {
                Integer x = (Integer) read.invoke(clazz); // read int value
                if (x == null) {
                    log.warning("null Integer returned from read method " + read);
                    return;
                }
                initValue = x.intValue();
                String s = Integer.toString(x);
//                System.out.println("init value of "+name+" is "+s);
                tf.setText(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        

        public IntControl(final Object f, PropertyDescriptor p) {
            super();
            final String name = p.getName();
            final Method r = p.getReadMethod(), w = p.getWriteMethod();

            setterMap.put(name, this);
            clazz = f;
            write = w;
            read = r;
//            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setLayout(new GridLayout(1, 0));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(p, label);
            add(label);

            tf = new JTextField("", 8);
            tf.setMaximumSize(new Dimension(100, 25));
            tf.setToolTipText("Integer control: use arrow keys or mouse wheel to change value by factor. Shift constrains to simple inc/dec");
            refresh();
            add(tf);
            tf.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        int y = Integer.parseInt(
                                tf.getText());
                        w.invoke(clazz, new Integer(y)); // write int value
                    } catch (NumberFormatException fe) {
                        tf.selectAll();
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (Exception iae) {
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {

                @Override
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    try {
                        Integer x = (Integer) r.invoke(clazz);
                        initValue = x.intValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    int code = evt.getKeyCode();
                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code == KeyEvent.VK_UP) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = 1;
                                } else {
                                    nval = (int) Math.round(initValue * factor);
                                }
                                w.invoke(clazz, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
                                iae.printStackTrace();
                            }
                        } else if (code == KeyEvent.VK_DOWN) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = 0;
                                } else {
                                    nval = (int) Math.round(initValue / factor);
                                }
                                w.invoke(clazz, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
                                iae.printStackTrace();
                            }
                        }
                    } else {
                        // shifted int control just incs or decs by 1
                        if (code == KeyEvent.VK_UP) {
                            try {
                                nval = initValue + 1;
                                w.invoke(clazz, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
                                iae.printStackTrace();
                            }
                        } else if (code == KeyEvent.VK_DOWN) {
                            try {
                                nval = initValue - 1;
                                w.invoke(clazz, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
                                iae.printStackTrace();
                            }
                        }

                    }

                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {

                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try {
                        Integer x = (Integer) r.invoke(clazz);
                        initValue = x.intValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    int code = evt.getWheelRotation();
                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code < 0) {
                            try {
                                nval = initValue;
                                if (Math.round(initValue * wheelFactor) == initValue) {
                                    nval++;
                                } else {
                                    nval = (int) Math.round(initValue * wheelFactor);
                                }
                                w.invoke(clazz, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
                                iae.printStackTrace();
                            }
                        } else if (code > 0) {
                            try {
                                nval = initValue;
                                if (Math.round(initValue / wheelFactor) == initValue) {
                                    nval--;
                                } else {
                                    nval = (int) Math.round(initValue / wheelFactor);
                                }
                                if (nval < 0) {
                                    nval = 0;
                                }
                                w.invoke(clazz, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
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
            Integer x = (Integer) r.invoke(getClazz()); // read int value
//            initValue=x.intValue();
            String s = Integer.toString(x);
            tf.setText(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class FloatControl extends JPanel implements HasSetGet {

        Method write, read;
        Object clazz;
        float initValue = 0, nval;
        final JTextField tf;

        public void set(Object o) {
            if (o instanceof Float) {
                Float b = (Float) o;
                tf.setText(b.toString());
            }
        }
        
            
        public void refresh() {
            try {
                Float x = (Float) read.invoke(clazz);
                if (x == null) {
                    log.warning("null Float returned from read method " + read);
                    return;
                }
                initValue = x.floatValue();
                String s = String.format("%.4f", initValue);
                tf.setText(s);
            } catch (Exception e) {
                e.printStackTrace();
            } 
        }
        

        public FloatControl(final Object f, PropertyDescriptor p) {
            super();
            final String name = p.getName();
            final Method r = p.getReadMethod(), w = p.getWriteMethod();

            setterMap.put(name, this);
            clazz = f;
            write = w;
            read = r;
            setLayout(new GridLayout(1, 0));
//            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(p, label);
            add(label);
            tf = new JTextField("", 10);
            tf.setMaximumSize(new Dimension(100, 25));
            tf.setToolTipText("Float control: use arrow keys or mouse wheel to change value by factor. Shift reduces factor.");
            refresh();
            add(tf);
            tf.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
//                    System.out.println(e);
                    try {
                        float y = Float.parseFloat(tf.getText());
                        w.invoke(clazz, new Float(y));
                        Float x = (Float) r.invoke(clazz); // getString the value from the getter method to constrain it
                        nval = x.floatValue();
                        tf.setText(String.format("%.4f", nval));
                    } catch (NumberFormatException fe) {
                        tf.selectAll();
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (Exception iae) {
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {

                {
                }

                public void keyPressed(java.awt.event.KeyEvent evt) {
                    try {
                        Float x = (Float) r.invoke(clazz); // getString the value from the getter method
                        initValue = x.floatValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    int code = evt.getKeyCode();
                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    float floatFactor = factor;
                    if (shift) {
                        floatFactor = wheelFactor;
                    }
                    if (code == KeyEvent.VK_UP) {
                        try {
                            nval = initValue;
                            if (nval == 0) {
                                nval = .1f;
                            } else {
                                nval = (initValue * floatFactor);
                            }
                            w.invoke(clazz, new Float(nval)); // setter the value
                            Float x = (Float) r.invoke(clazz); // getString the value from the getter method to constrain it
                            nval = x.floatValue();
                            tf.setText(String.format("%.4f", nval));
                        } catch (InvocationTargetException ite) {
                            ite.printStackTrace();
                        } catch (Exception iae) {
                            iae.printStackTrace();
                        }
                    } else if (code == KeyEvent.VK_DOWN) {
                        try {
                            nval = initValue;
                            if (nval == 0) {
                                nval = .1f;
                            } else {
                                nval = (initValue / floatFactor);
                            }
                            w.invoke(clazz, new Float(initValue / floatFactor));
                            Float x = (Float) r.invoke(clazz); // getString the value from the getter method to constrain it
                            nval = x.floatValue();
                            tf.setText(String.format("%.4f", nval));
                        } catch (InvocationTargetException ite) {
                            ite.printStackTrace();
                        } catch (Exception iae) {
                            iae.printStackTrace();
                        }
                    }
                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {

                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try {
                        Float x = (Float) r.invoke(clazz); // getString the value from the getter method
                        initValue = x.floatValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    int code = evt.getWheelRotation();
                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code < 0) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = .1f;
                                } else {
                                    nval = (initValue * wheelFactor);
                                }
                                w.invoke(clazz, new Float(nval)); // setter the value
                                Float x = (Float) r.invoke(clazz); // getString the value from the getter method to constrain it
                                nval = x.floatValue();
                                tf.setText(String.format("%.4f", nval));
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
                                iae.printStackTrace();
                            }
                        } else if (code > 0) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = .1f;
                                } else {
                                    nval = (initValue / wheelFactor);
                                }
                                w.invoke(clazz, new Float(initValue / wheelFactor));
                                Float x = (Float) r.invoke(clazz); // getString the value from the getter method to constrain it
                                nval = x.floatValue();
                                tf.setText(String.format("%.4f", nval));
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (Exception iae) {
                                iae.printStackTrace();
                            }
                        }
                    }
                }
            });
            tf.addFocusListener(new FocusListener() {

                @Override
                public void focusGained(FocusEvent e) {
                    tf.setSelectionStart(0);
                    tf.setSelectionEnd(tf.getText().length());
                }

                @Override
                public void focusLost(FocusEvent e) {
                }
            });
        }
    }

    /** Called when a class calls firePropertyChange. The PropertyChangeEvent should send the bound property name and the old and new values.
    The GUI control is then updated by this method.
    @param propertyChangeEvent contains the property that has changed, e.g. it would be called from a setter like this:
     * with 
     * <code>support.firePropertyChange("mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled, this.mapEventsToLearnedTopologyEnabled);</code>
     */
    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        if (propertyChangeEvent.getSource() == getClazz()) {
            {
                // we need to find the control and set it appropriately. we don't need to set the property itself since this has already been done!
                try {
                    log.info("PropertyChangeEvent received from " +
                            propertyChangeEvent.getSource() + " for property=" +
                            propertyChangeEvent.getPropertyName() +
                            " newValue=" + propertyChangeEvent.getNewValue());
                    HasSetGet setter = setterMap.get(propertyChangeEvent.getPropertyName());
                    if (setter == null) {
                        log.warning("null setter for property named " + propertyChangeEvent.getPropertyName());
                    } else {
                        setter.set(propertyChangeEvent.getNewValue());
                    }

//                    PropertyDescriptor pd=new PropertyDescriptor(propertyChangeEvent.getPropertyName(), getClazz().getClass());
//                    Method wm=pd.getWriteMethod();
//                    wm.invoke(getClazz(), propertyChangeEvent.getNewValue());
                } catch (Exception e) {
                    log.warning(e.toString());
                }
//                  try{
//                    log.info("PropertyChangeEvent received for property="+propertyChangeEvent.getPropertyName()+" newValue="+propertyChangeEvent.getNewValue());
//                    PropertyDescriptor pd=new PropertyDescriptor(propertyChangeEvent.getPropertyName(), getClazz().getClass());
//                    Method wm=pd.getWriteMethod();
//                    wm.invoke(getClazz(), propertyChangeEvent.getNewValue());
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
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
    }// </editor-fold>//GEN-END:initComponents
    boolean controlsVisible = true;

    public boolean isControlsVisible() {
        return controlsVisible;
    }

    /** Set visibility of individual clazz controls; hides other filters.
     * @param visible true to show clazz parameter controls, false to hide this clazz's controls and to show all filters in chain.
     */
    public void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        setBorderActive(visible);
        for (JComponent p : controls) {
            p.setVisible(visible);
            p.invalidate();
        }

        invalidate();

    }

    private void setBorderActive(final boolean yes) {
        // see http://forum.java.sun.com/thread.jspa?threadID=755789
        if (yes) {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textText);
            titledBorder.setBorder(redLineBorder);
        } else {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textInactiveText);
            titledBorder.setBorder(normalBorder);
        }

    }

    void toggleControlsVisible() {
        controlsVisible = !controlsVisible;
        setControlsVisible(controlsVisible);
    }

    public Object getClazz() {
        return classObject;
    }

    public void setClazz(Object clazz) {
        this.classObject = clazz;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

    private class SliderParams {

        Object paramClass = null;
        int minIntValue, maxIntValue;
        float minFloatValue, maxFloatValue;

        SliderParams(Object clazz, int minIntValue, int maxIntValue, float minFloatValue, float maxFloatValue) {
            this.minIntValue = minIntValue;
            this.minFloatValue = minFloatValue;
            this.maxIntValue = maxIntValue;
            this.maxFloatValue = maxFloatValue;
        }
    }

    private SliderParams isSliderType(PropertyDescriptor p, Object clazz) throws SecurityException {
        boolean isSliderType = false;
        // check for min/max methods for property, e.g. getMinDt, getMaxDt for property dt
        String propCapped = p.getName().substring(0, 1).toUpperCase() + p.getName().substring(1); // eg. Dt for dt
        String minMethName = "getMin" + propCapped;
        String maxMethName = "getMax" + propCapped;
        SliderParams params = null;
        try {
            Method minMethod = clazz.getClass().getMethod(minMethName, (Class[]) null);
            Method maxMethod = clazz.getClass().getMethod(maxMethName, (Class[]) null);
            isSliderType = true;
            if (p.getPropertyType() == Integer.TYPE) {
                int min = (Integer) minMethod.invoke(clazz);
                int max = (Integer) maxMethod.invoke(clazz);
                params = new SliderParams(Integer.class, min, max, 0, 0);
            } else if (p.getPropertyType() == Float.TYPE) {
                float min = (Float) minMethod.invoke(clazz);
                float max = (Float) maxMethod.invoke(clazz);
                params = new SliderParams(Integer.class, 0, 0, min, max);
            }
        } catch (NoSuchMethodException e) {
        } catch (Exception iae) {
            log.warning(iae.toString() + " for property " + p + " in class " + clazz);
        }
        return params;
    }
}

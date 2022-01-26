/*
 * FilterPanel.java
 *
 * Created on October 31, 2005, 8:13 PM
 */
package net.sf.jaer.eventprocessing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.SystemColor;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.jaer.util.EngineeringFormat;

/**
 * A panel for a filter that has Integer/Float/Boolean/String/enum getter/setter
 * methods (bound properties). These methods are introspected and a set of
 * controls are built for them. Enclosed filters and filter chains have panels
 * built for them that are enlosed inside the filter panel, hierarchically.
 * <ul>
 * <li>Numerical properties (ints, floats, but not currently doubles) construct
 * a JTextBox control that also allows changes from mouse wheel or arrow keys.
 * <li> boolean properties construct a JCheckBox control.
 * <li> String properties construct a JTextField control.
 * <li> enum properties construct a JComboBox control, which all the possible
 * enum constant values.
 * </ul>
 * <p>
 * If a filter wants to automatically have the GUI controls reflect what the
 * property state is, then it should fire PropertyChangeEvent when the property
 * changes. For example, an {@link EventFilter} can implement a setter like
 * this:
 * <pre>
 * public void setMapEventsToLearnedTopologyEnabled(boolean mapEventsToLearnedTopologyEnabled) {
 * support.firePropertyChange("mapEventsToLearnedTopologyEnabled", this.mapEventsToLearnedTopologyEnabled, mapEventsToLearnedTopologyEnabled); // property, old value, new value
 * this.mapEventsToLearnedTopologyEnabled = mapEventsToLearnedTopologyEnabled;
 * getPrefs().putBoolean("TopologyTracker.mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled);
 * }
 * </pre> Here, <code>support</code> is a protected field of EventFilter. The
 * change event comes here to FilterPanel and the appropriate automatically
 * generated control is modified.
 * <p>
 * Note that calling firePropertyChange as shown above will inform listeners
 * <em>before</em> the property has actually been changed (this.dt has not been
 * set yet).
 * <p>
 * A tooltip for the property can be installed using the EventFilter
 * setPropertyTooltip method, for example
 * <pre>
 *         setPropertyTooltip("sizeClassificationEnabled", "Enables coloring cluster by size threshold");
 * </pre> will install a tip for the property sizeClassificationEnabled.
 * <p>
 * <strong>Slider controls.</strong>
 *
 * If you want a slider for an int or float property, then create getMin and
 * getMax methods for the property, e.g., for the property <code>dt</code>:
 * <pre>
 * public int getDt() {
 * return this.dt;
 * }
 *
 * public void setDt(final int dt) {
 * getPrefs().putInt("BackgroundActivityFilter.dt",dt);
 * support.firePropertyChange("dt",this.dt,dt);
 * this.dt = dt;
 * }
 *
 * public int getMinDt(){
 * return 10;
 * }
 *
 * public int getMaxDt(){
 * return 100000;
 * }
 * </pre>
 * <strong>Button control</strong>
 * <p>
 * To add a button control to a panel, implement a method starting with "do",
 * e.g.
 * <pre>
 *     public void doSendParameters() {
 * sendParameters();
 * }
 * </pre> This method will construct a button with label "SendParameters" which,
 * when pressed, will call the method "doSendParameters".
 * <p>
 * <p>
 * To add a momentary press/release button control to a panel, implement a pair
 * of methods starting with "doPress" and "doRelease", e.g.
 * <pre>
 *     public void doPressTurnOnLamp();
 *     public void doReleaseTurnOnLamp();
 ** </pre> This method will construct a button with label "TurnOnLamp" which,
 * while pressed can turn on something momentarily; the doPressXXX() is called
 * on press and doReleaseXXX() on release
 * <p>
 * <p>
 * To add a toggle button control to a panel, implement a pair of methods
 * starting with "doToggleOn" and "doToggleOff", e.g.
 * <pre>
 *     public void doToggleOnLamp();
 *     public void doToggleOffLamp();
 ** </pre> This method will construct a button with label "Lamp" which, while
 * pressed can turn on something momentarily; the doToggleOnLamp() is called on
 * press and doToggleOffLamp() on release
 * <p>
 * <strong>
 * Grouping parameters.</strong>
 * <p>
 * Properties are normally sorted alphabetically, with button controls at the
 * top. If you want to group parameters, use the built in EventFilter method
 * {@link net.sf.jaer.eventprocessing.EventFilter#addPropertyToGroup}. All
 * properties of a given group are grouped together. Within a group the
 * parameters are sorted alphabetically, and the groups will also be sorted
 * alphabetically and shown before any ungrouped parameters. E.g., to Create
 * groups "Sizing" and "Tracking" and add properties to each, do
 * <pre>
 * addPropertyToGroup("Sizing", "clusterSize");
 * addPropertyToGroup("Sizing", "aspectRatio");
 * addPropertyToGroup("Sizing", "highwayPerspectiveEnabled");
 * addPropertyToGroup("Tracking", "mixingFactor");
 * addPropertyToGroup("Tracking", "velocityMixingFactor");
 * </pre> Or, even simpler, if you have already defined tooltips for your
 * properties, then you can use the overloaded
 * {@link net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String, java.lang.String, java.lang.String) setPropertyTooltip}
 * of {@link net.sf.jaer.eventprocessing.EventFilter}, as shown next. Here two
 * groups "Size" and "Timing" are defined and properties are added to each (or
 * to neither for "multiOriOutputEnabled").
 * <pre>
 * final String size="Size", tim="Timing";
 *
 * setPropertyTooltip(tim,"minDtThreshold", "Coincidence time, events that pass this coincidence test are considerd for orientation output");
 * setPropertyTooltip(tim,"dtRejectMultiplier", "reject delta times more than this KEY_FACTOR times minDtThreshold to reduce noise");
 * setPropertyTooltip(tim,"dtRejectThreshold", "reject delta times more than this time in us to reduce effect of very old events");
 * setPropertyTooltip("multiOriOutputEnabled", "Enables multiple event output for all events that pass test");
 * </pre>
 *
 *
 * @author tobi
 * @see
 * net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String,
 * java.lang.String)
 * @see
 * net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String,
 * java.lang.String, java.lang.String)
 * @see net.sf.jaer.eventprocessing.EventFilter
 */
public class FilterPanel extends javax.swing.JPanel implements PropertyChangeListener {

    private interface HasSetter {

        void set(Object o);
    }
    static final float LEFT_ALIGNMENT = Component.LEFT_ALIGNMENT;
    private BeanInfo info;
    private PropertyDescriptor[] props;
    private Method[] methods;
    private static Logger log = Logger.getLogger("Filters");
    private EventFilter filter = null;
    final float fontSize = 10f;
    private Border normalBorder, redLineBorder, enclosedFilterSelectedBorder;
    private TitledBorder titledBorder;
    private HashMap<String, HasSetter> setterMap = new HashMap<String, HasSetter>(); // map from filter to property, to apply property change events to control
    protected java.util.ArrayList<JComponent> controls = new ArrayList<JComponent>();
    private HashMap<EventFilter, FilterPanel> enclosedFilterPanels = new HashMap(); // points from enclosed filter to its panel
    private HashMap<String, Container> groupContainerMap = new HashMap(); // points from group name string to the panel holding the properties
    private HashSet<String> populatedGroupSet = new HashSet(); // Set of all property groups that have at least one item in them
    private HashMap<String, MyControl> propertyControlMap = new HashMap();
    private JComponent ungroupedControls = null;
    private float DEFAULT_REAL_VALUE = 0.01f; // value jumped to from zero on key or wheel up
    ArrayList<AbstractButton> doButList = new ArrayList();

    /**
     * Creates new form FilterPanel
     */
    public FilterPanel() {
        initComponents();
    }

    public FilterPanel(EventFilter f) {
//        log.info("building FilterPanel for "+f);
        this.setFilter(f);
        initComponents();
        Dimension d = enableResetControlsHelpPanel.getPreferredSize();
        enableResetControlsHelpPanel.setMaximumSize(new Dimension(1000, d.height)); // keep from stretching
        String cn = getFilter().getClass().getName();
        int lastdot = cn.lastIndexOf('.');
        String name = cn.substring(lastdot + 1);
        setName(name);
        titledBorder = new TitledBorder(name);
        titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
        setBorder(titledBorder);
        normalBorder = titledBorder.getBorder();
        redLineBorder = BorderFactory.createLineBorder(Color.red, 3);
        enclosedFilterSelectedBorder = BorderFactory.createLineBorder(Color.orange, 3);
        enabledCheckBox.setSelected(getFilter().isFilterEnabled());
        addIntrospectedControls();
        // when filter fires a property change event, we getString called here and we update all our controls
        getFilter().getSupport().addPropertyChangeListener(this);
//        // add ourselves to listen for all enclosed filter property changes as well
//        EventFilter enclosed = getFilter().getEnclosedFilter();
//        while (enclosed != null) {
//            enclosed.getSupport().addPropertyChangeListener(this);
//            enclosed = enclosed.getEnclosedFilter();
//        }
//        FilterChain chain = getFilter().getEnclosedFilterChain();
//        if (chain != null) {
//            for (EventFilter f2 : chain) {
//                EventFilter f3=f2;
//                while (f3 != null) {
//                    f3.getSupport().addPropertyChangeListener(this);
//                    f3 = f3.getEnclosedFilter(); // for some very baroque arrangement
//                }
//            }
//        }

        ToolTipManager.sharedInstance().setDismissDelay(10000); // to show tips
        setToolTipText(f.getDescription());
//        helpBut.setToolTipText("<html>" + f.getDescription() + "<p>Click to show/create wiki page");
    }

    // checks for group container and adds to that if needed.
    private void myadd(MyControl comp, String propertyName, boolean inherited) {
//        if(propertyControlMap.containsKey(propertyName)){
//            log.warning("controls already has "+propertyControlMap.get(propertyName));
//        }
        if (!getFilter().hasPropertyGroups()) {
            ungroupedControls.add(comp);
            controls.add(comp);
            propertyControlMap.put(propertyName, comp);
            return;
        }
        String groupName = getFilter().getPropertyGroup(propertyName);
        if (groupName != null) {
            Container container = groupContainerMap.get(groupName);
//            comp.setAlignmentX(Component.LEFT_ALIGNMENT);
//            if(inherited){
//                JPanel inherPan=new JPanel();
//                inherPan.setBorder(BorderFactory.createLineBorder(Color.yellow) );
//                inherPan.add(comp,BorderLayout.WEST);
//                container.add(inherPan);
//            }else{
            container.add(comp);
            populatedGroupSet.add(groupName); // add to list of populated groups
//            }
        } else {
            ungroupedControls.add(comp);
        }
        controls.add(comp);
        propertyControlMap.put(propertyName, comp);
    }

    // gets getter/setter methods for the filter and makes controls for them. enclosed filters are also added as submenus
    private void addIntrospectedControls() {
        add(Box.createVerticalStrut(0));
        ungroupedControls = new MyControl();
        String u = "(Ungrouped)";
        ungroupedControls.setName(u);
        ungroupedControls.setBorder(new TitledBorder(u));
        ungroupedControls.setLayout(new GridLayout(0, 1));
        controls.add(ungroupedControls);
        MyControl control = null;
        EventFilter filter = getFilter();
        try {
            info = Introspector.getBeanInfo(filter.getClass());
            // TODO check if class is public, otherwise we can't access methods usually
            this.props = info.getPropertyDescriptors();
            this.methods = filter.getClass().getMethods();
            int numDoButtons = 0;
            // first add buttons when the method name starts with "do". These methods are by convention associated with actions.
            // these methods, e.g. "void doDisableServo()" do an action.
            // also, a pair of methods doPressXXX and doReleaseXXX will add a button that calls the first method on press and the 2nd on release
            Insets butInsets = new Insets(0, 0, 0, 0);

            for (Method method : methods) {
                // add a button XXX that calls doPressXXX on press and doReleaseXXX on release of button
                if (method.getName().startsWith("doPress")
                        && (method.getParameterTypes().length == 0)
                        && (method.getReturnType() == void.class)) {

                    for (Method releasedMethod : methods) {
                        String suf = method.getName().substring(7);
                        if (releasedMethod.getName().equals("doRelease" + suf)
                                && (releasedMethod.getParameterTypes().length == 0)
                                && (releasedMethod.getReturnType() == void.class)) {
                            //found corresponding release method, add action listeners for press and release
                            numDoButtons++;
                            JButton button = new JButton(method.getName().substring(7));
                            button.setMargin(butInsets);
                            button.setFont(button.getFont().deriveFont(9f));
                            final EventFilter f = filter;
                            final Method pressedMethodFinal = method;
                            final Method releasedMethodFinal = releasedMethod;
                            button.addMouseListener(new MouseAdapter() {

                                @Override
                                public void mousePressed(MouseEvent e) {
                                    try {
                                        pressedMethodFinal.invoke(f);
                                    } catch (IllegalArgumentException ex) {
                                        ex.printStackTrace();
                                    } catch (InvocationTargetException ex) {
                                        ex.printStackTrace();
                                    } catch (IllegalAccessException ex) {
                                        ex.printStackTrace();
                                    }
                                }

                                @Override
                                public void mouseReleased(MouseEvent e) {
                                    try {
                                        releasedMethodFinal.invoke(f);
                                    } catch (IllegalArgumentException ex) {
                                        ex.printStackTrace();
                                    } catch (InvocationTargetException ex) {
                                        ex.printStackTrace();
                                    } catch (IllegalAccessException ex) {
                                        ex.printStackTrace();
                                    }
                                }
                            });

                            addTip(f, button);
                            doButList.add(button);
                            break; // don't bother with rest of methods
                        }
                    }
                }
                if (method.getName().startsWith("doToggleOn")
                        && (method.getParameterTypes().length == 0)
                        && (method.getReturnType() == void.class)) {

                    for (Method toggleOffMethod : methods) {
                        String suf = method.getName().substring(10);
                        if (toggleOffMethod.getName().equals("doToggleOff" + suf)
                                && (toggleOffMethod.getParameterTypes().length == 0)
                                && (toggleOffMethod.getReturnType() == void.class)) {
                            //found corresponding release method, add action listeners for toggle on and toggle off
                            numDoButtons++;
                            final JToggleButton button = new JToggleButton(method.getName().substring(10));
                            button.setMargin(butInsets);
                            button.setFont(button.getFont().deriveFont(9f));
                            final EventFilter f = filter;
                            final Method toggleOnMethodFinal = method;
                            final Method toggleOffMethodFinal = toggleOffMethod;
                            button.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    try {
                                        if (button.isSelected()) {
                                            toggleOnMethodFinal.invoke(f);
                                        } else {
                                            toggleOffMethodFinal.invoke(f);
                                        }
                                    } catch (IllegalArgumentException ex) {
                                        ex.printStackTrace();
                                    } catch (InvocationTargetException ex) {
                                        ex.printStackTrace();
                                    } catch (IllegalAccessException ex) {
                                        ex.printStackTrace();
                                    }
                                }
                            });

                            addTip(f, button);
                            doButList.add(button);
                            break; // don't bother with rest of methods
                        }
                    }
                }
                // add a button that calls a method XXX for method void doXXX()
                if (method.getName().startsWith("do") && !method.getName().startsWith("doPress") && !method.getName().startsWith("doRelease")
                        && !method.getName().startsWith("doToggleOn") && !method.getName().startsWith("doToggleOff")
                        && (method.getParameterTypes().length == 0)
                        && (method.getReturnType() == void.class)) {
                    numDoButtons++;
                    JButton button = new JButton(method.getName().substring(2));
                    button.setMargin(butInsets);
                    button.setFont(button.getFont().deriveFont(9f));
                    final EventFilter f = filter;
                    final Method meth = method;
                    button.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            try {
                                meth.invoke(f);
                            } catch (IllegalArgumentException ex) {
                                ex.printStackTrace();
                            } catch (InvocationTargetException ex) {
                                ex.printStackTrace();
                            } catch (IllegalAccessException ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                    addTip(f, button);
                    doButList.add(button);
                }
            }

            Comparator<AbstractButton> butComp = new Comparator<AbstractButton>() {

                @Override
                public int compare(AbstractButton o1, AbstractButton o2) {
                    if ((o1 == null) || (o2 == null) || (o1.getText() == null) || (o2.getText() == null)) {
                        return 0;
                    }
                    return o1.getText().compareToIgnoreCase(o2.getText());
                }
            };

            Collections.sort(doButList, butComp);
            if (!doButList.isEmpty()) {

                JPanel buttons = new JPanel() {
                    @Override
                    public Dimension getMaximumSize() {
                        return getPreferredSize();
                    }
                };
                for (AbstractButton b : doButList) {
                    buttons.add(b);
                }

                //if at least one button then we show the actions panel
//                buttons.setMinimumSize(new Dimension(0, 0));
                buttons.setLayout(new GridLayout(0, 3, 3, 3));
                JPanel butPanel = new MyControl();
                butPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
                butPanel.add(buttons);
                TitledBorder tb = new TitledBorder("Filter Actions");
                tb.getBorderInsets(this).set(1, 1, 1, 1);
                butPanel.setBorder(tb);
                add(butPanel);
                controls.add(butPanel);
            }

//            if (numDoButtons > 3) {
//                control.setLayout(new GridLayout(0, 3, 3, 3));
//            }
            // next add enclosed Filter and enclosed FilterChain so they appear at top of list (they are processed first)
            for (PropertyDescriptor p : props) {
                Class c = p.getPropertyType();
                if (p.getName().equals("enclosedFilter")) { //if(c==EventFilter2D.class){
                    // if type of property is an EventFilter, check if it has either an enclosed filter
                    // or an enclosed filter chain. If so, construct FilterPanels for each of them.
                    try {
                        Method r = p.getReadMethod(); // getString the getter for the enclosed filter
                        EventFilter2D enclFilter = (EventFilter2D) (r.invoke(getFilter()));
                        if (enclFilter != null) {
//                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses EventFilter2D "+enclFilter.getClass().getSimpleName());
                            FilterPanel enclPanel = new FilterPanel(enclFilter);
                            this.add(enclPanel);
                            controls.add(enclPanel);
                            enclosedFilterPanels.put(enclFilter, enclPanel);
                            ((TitledBorder) enclPanel.getBorder()).setTitle("enclosed: " + enclFilter.getClass().getSimpleName());
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
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (p.getName().equals("enclosedFilterChain")) { //
                    // if type of property is a FilterChain, check if it has either an enclosed filter
                    // or an enclosed filter chain. If so, construct FilterPanels for each of them.
                    try {
                        Method r = p.getReadMethod(); // getString the getter for the enclosed filter chain
                        FilterChain chain = (FilterChain) (r.invoke(getFilter()));
                        if (chain != null) {
//                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses filterChain "+chain);
                            for (EventFilter f : chain) {
                                FilterPanel enclPanel = new FilterPanel(f);
                                this.add(enclPanel);
                                controls.add(enclPanel);
                                enclosedFilterPanels.put(f, enclPanel);
                                ((TitledBorder) enclPanel.getBorder()).setTitle("enclosed: " + f.getClass().getSimpleName());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    String name = p.getName();
                    if (control != null) {
                        control.setToolTipText(getFilter().getPropertyTooltip(name));
                    }
                }
            }

            // next add all other properties that we can handle
            // these must be saved and then sorted in case there are property groups defined.
            if (getFilter().hasPropertyGroups()) {
                Set<String> groupSet = getFilter().getPropertyGroupSet();

                ArrayList<String> setList = new ArrayList();
                setList.addAll(groupSet);
                Collections.sort(setList);
                for (String s : setList) {
                    JPanel groupPanel = new MyControl();
                    groupPanel.setName(s);
                    groupPanel.setBorder(new TitledBorder(s));
                    groupPanel.setLayout(new GridLayout(0, 1));
                    groupContainerMap.put(s, groupPanel); // point from group name to its panel container
                    add(groupPanel);
                    controls.add(groupPanel); // visibility list
                }
            }

//            ArrayList<Component> sortedControls=new ArrayList();
            // iterate over all methods, adding controls to panel for each type of property
            // but only for those properties that are not marked as "hidden"
            for (PropertyDescriptor p : props) {
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
                    boolean inherited = false;

                    // TODO handle indexed properties
                    Class c = p.getPropertyType();
                    String name = p.getName();

                    // check if method comes from a superclass of this EventFilter
                    if ((control != null) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        Method m = p.getReadMethod();
                        if (m.getDeclaringClass() != getFilter().getClass()) {
                            inherited = true;
                        }
                    }

                    boolean hidden = filter.isPropertyHidden(p.getName());
                    if (hidden) {
                        log.info("not constructing control for " + filter.getClass().getSimpleName() + " for hidden property " + p.getName());
                        continue;
                    }

                    if ((c == Integer.TYPE) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {

                        SliderParams params;
                        if ((params = isSliderType(p, filter)) != null) {
                            control = new IntSliderControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod(), params);
                        } else {
                            control = new IntControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        }
                        myadd(control, name, inherited);
                    } else if ((c == Float.TYPE) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        SliderParams params;
                        if ((params = isSliderType(p, filter)) != null) {
                            control = new FloatSliderControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod(), params);
                        } else {
                            control = new FloatControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());

                        }
                        myadd(control, name, inherited);
                    } else if ((c == Boolean.TYPE) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        if (p.getName().equals("filterEnabled")) { // built in, skip
                            continue;
                        }
                        if (p.getName().equals("annotationEnabled")) {// built in, skip
                            continue;
                        }
                        if (p.getName().equals("selected")) {// built in, skip
                            continue;
                        }

                        control = new BooleanControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        myadd(control, name, inherited);
                    } else if ((c == String.class) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        if (p.getName().equals("filterEnabled")) {
                            continue;
                        }
                        if (p.getName().equals("annotationEnabled")) {
                            continue;
                        }
                        control = new StringControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        myadd(control, name, inherited);
                    } else if ((c != null) && c.isEnum() && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        control = new EnumControl(c, getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        myadd(control, name, inherited);
                    } else if ((c != null) && ((c == Point2D.Float.class) || (c == Point2D.Double.class) || (c == Point2D.class)) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        control = new Point2DControl(getFilter(), p.getName(), p.getWriteMethod(), p.getReadMethod());
                        myadd(control, name, inherited);
                    } else {
//                    log.warning("unknown property type "+p.getPropertyType()+" for property "+p.getName());
                    }
                    if (control != null) {
                        control.setToolTipText(getFilter().getPropertyTooltip(name));
                    }

                } catch (Exception e) {
                    log.warning(e + " caught on property " + p.getName() + " from EventFilter " + filter);
                }
            } // properties

//            groupContainerMap = null;
//             sortedControls=null;
        } catch (Exception e) {
            log.warning("on adding controls for EventFilter " + filter + " caught " + e);
            e.printStackTrace();
        }

//        add(Box.createHorizontalGlue());
        if (ungroupedControls.getComponentCount() > 0) {
            add(Box.createVerticalStrut(0));
            add(ungroupedControls);
        }
        // now remove group containers that are not populated.
        for (String s : groupContainerMap.keySet()) {
            if (!populatedGroupSet.contains(s)) { // remove this group
                log.info("Removing emtpy container " + s + " from " + filter.getClass().getSimpleName());
                controls.remove(groupContainerMap.get(s));
                remove(groupContainerMap.get(s));
            }
        }
        add(Box.createHorizontalStrut(0));  // use up vertical space to get components to top

        setControlsVisible(false);
//        System.out.println("added glue to "+this);
    }

    void addTip(EventFilter f, JLabel label) {
        String s = f.getPropertyTooltip(label.getText());
        if (s == null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);
    }

    void addTip(EventFilter f, AbstractButton b) {
        String s = f.getPropertyTooltip(b.getText());
        if (s == null) {
            return;
        }
        b.setToolTipText(s);
        b.setForeground(Color.BLUE);
    }

    void addTip(EventFilter f, JCheckBox label) {
        String s = f.getPropertyTooltip(label.getText());
        if (s == null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);

    }

    class MyControl extends JPanel {

        @Override
        public Dimension getMaximumSize() {
            Dimension d = getPreferredSize();
            d.setSize(1000, d.getHeight());
            return d;
        }
    }

    class EnumControl extends MyControl implements HasSetter {

        Method write, read;
        EventFilter filter;
        boolean initValue = false, nval;
        final JComboBox control;

        @Override
        public void set(Object o) {
            control.setSelectedItem(o);
        }

        public EnumControl(final Class<? extends Enum> c, final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(f.getClass().getSimpleName() + "." + name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            control = new JComboBox(c.getEnumConstants());
            control.setFont(control.getFont().deriveFont(fontSize));
//            control.setHorizontalAlignment(SwingConstants.LEADING);

            add(label);
            add(control);
            add(Box.createHorizontalGlue());

            try {
                Object x = r.invoke(filter);
                if (x == null) {
                    log.warning("null Object returned from read method " + r);
                    return;
                }
                control.setSelectedItem(x);
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            control.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(filter, control.getSelectedItem());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }
    }

    class StringControl extends MyControl implements HasSetter {

        Method write, read;
        EventFilter filter;
        boolean initValue = false, nval;
        final JTextField textField;

        @Override
        public void set(Object o) {
            if (o instanceof String) {
                String b = (String) o;
                textField.setText(b);
            }
        }

        public StringControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(f.getClass().getSimpleName() + "." + name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            textField = new JTextField(name);
            textField.setFont(textField.getFont().deriveFont(fontSize));
            textField.setHorizontalAlignment(SwingConstants.LEADING);
            textField.setColumns(10);

            add(label);
            add(textField);
            add(Box.createHorizontalGlue());
            try {
                String x = (String) r.invoke(filter);
                if (x == null) {
                    log.warning("null String returned from read method " + r);
                    return;
                }
                textField.setText(x);
                textField.setToolTipText(x);
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            textField.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(filter, textField.getText());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }
    }
    private final float KEY_FACTOR = (float) Math.sqrt(2), WHEEL_FACTOR = (float) Math.pow(2, 1. / 16); // factors to change by with arrow and mouse wheel

    class BooleanControl extends MyControl implements HasSetter {

        Method write, read;
        EventFilter filter;
        boolean initValue = false, nval;
        final JCheckBox checkBox;

        public BooleanControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(f.getClass().getSimpleName() + "." + name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            checkBox = new JCheckBox(name);
            checkBox.setAlignmentX(LEFT_ALIGNMENT);
            checkBox.setFont(checkBox.getFont().deriveFont(fontSize));
            checkBox.setHorizontalTextPosition(SwingConstants.LEFT);
            addTip(f, checkBox);
            add(checkBox);
//            add(Box.createVerticalStrut(0));
            try {
                Boolean x = (Boolean) r.invoke(filter);
                if (x == null) {
                    log.warning("null Boolean returned from read method " + r);
                    return;
                }
                initValue = x.booleanValue();
                checkBox.setSelected(initValue);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            checkBox.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        w.invoke(filter, checkBox.isSelected());
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void set(Object o) {
            if (o instanceof Boolean) {
                Boolean b = (Boolean) o;
                checkBox.setSelected(b);
            }
        }
    }

    class IntSliderControl extends MyControl implements HasSetter {

        Method write, read;
        EventFilter filter;
        int initValue = 0, nval;
        JSlider slider;
        JTextField tf;
        private boolean sliderDontProcess = false;

        @Override
        public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                slider.setValue(b);
            }
        }

        public IntSliderControl(final EventFilter f, final String name, final Method w, final Method r, SliderParams params) {
            super();
            setterMap.put(f.getClass().getSimpleName() + "." + name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);

            final IntControl ic = new IntControl(f, name, w, r);

            tf = ic.tf;
            add(ic);
            slider = new JSlider(params.minIntValue, params.maxIntValue);
            slider.setMaximumSize(new Dimension(300, 50));

            try {
                Integer x = (Integer) r.invoke(filter); // read int value
                if (x == null) {
                    log.warning("null Integer returned from read method " + r);
                    return;
                }
                initValue = x.intValue();
                slider.setValue(initValue);
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            add(slider);

            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    if (sliderDontProcess) {
                        return;
                    }
                    try {
                        w.invoke(filter, new Integer(slider.getValue())); // write int value
                        ic.set(slider.getValue());
//                        tf.setText(Integer.toString(slider.getValue()));
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });

            ic.addPropertyChangeListener(ic.PROPERTY_VALUE, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent pce) {
                    if ((pce.getNewValue() == null) || !(pce.getNewValue() instanceof Integer)) {
                        return;
                    }
                    sliderDontProcess = true;
                    slider.setValue((Integer) (pce.getNewValue()));
                    sliderDontProcess = false;
                }
            });

        }
    }

    class FloatSliderControl extends MyControl implements HasSetter {

        Method write, read;
        EventFilter filter;
        JSlider slider;
        JTextField tf;
        EngineeringFormat engFmt;
        FloatControl fc;
        boolean dontProcessEvent = false; // to avoid slider callback loops
        float minValue, maxValue, currentValue;

        @Override
        public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                slider.setValue(b);
                fc.set(b);
            } else if (o instanceof Float) {
                float f = (Float) o;
                int sv = Math.round(((f - minValue) / (maxValue - minValue)) * (slider.getMaximum() - slider.getMinimum()));
                slider.setValue(sv);
            }
        }

        public FloatSliderControl(final EventFilter f, final String name, final Method w, final Method r, SliderParams params) {
            super();
            setterMap.put(f.getClass().getSimpleName() + "." + name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);

            fc = new FloatControl(f, name, w, r);
            add(fc);
            minValue = params.minFloatValue;
            maxValue = params.maxFloatValue;
            slider = new JSlider();
            slider.setMaximumSize(new Dimension(200, 50));

            engFmt = new EngineeringFormat();

            try {
                Float x = (Float) r.invoke(filter); // read int value
                if (x == null) {
                    log.warning("null Float returned from read method " + r);
                    return;
                }
                currentValue = x.floatValue();
                set(new Float(currentValue));
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            add(slider);
            add(Box.createHorizontalGlue());

            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    try {
                        int v = slider.getValue();
                        currentValue = minValue + ((maxValue - minValue) * ((float) slider.getValue() / (slider.getMaximum() - slider.getMinimum())));
                        w.invoke(filter, new Float(currentValue)); // write int value
                        fc.set(new Float(currentValue));

//                        tf.setText(engFmt.format(currentValue));
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
        }
    }

    class IntControl extends MyControl implements HasSetter {

        Method write, read;
        EventFilter filter;
        int initValue = 0, nval;
        final JTextField tf;
        String PROPERTY_VALUE = "value";

        @Override
        public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                String s = NumberFormat.getIntegerInstance().format(b);
                tf.setText(s);
            }
        }

        public IntControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(f.getClass().getSimpleName() + "." + name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            tf = new JTextField("", 8);
            tf.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.EMPTY_SET);
            // see http://blog.marcnuri.com/blog/defaul...e-cellRenderer/2007/06/06/Detecting-Tab-Key-Pressed-Event-in-JTextField-s-Event-VK-TAB-KeyPressed
            // otherwise TAB will just pass over field without entering a new value
            tf.setMaximumSize(new Dimension(100, 50));
            tf.setToolTipText("Integer control: use arrow keys or mouse wheel to change value by factor. Shift constrains to simple inc/dec");
            try {
                Integer x = (Integer) r.invoke(filter); // read int value
                if (x == null) {
                    log.warning("null Integer returned from read method " + r);
                    return;
                }
                initValue = x.intValue();
                String s = NumberFormat.getIntegerInstance().format(initValue);
//                System.out.println("init value of "+name+" is "+s);
                tf.setText(s);
                fixIntValue(tf, r);
            } catch (Exception e) {
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    Integer newValue = null;
                    try {
                        NumberFormat format = NumberFormat.getNumberInstance();
                        Integer oldValue = null;
                        try {
                            oldValue = (Integer) r.invoke(filter);
                        } catch (Exception re) {
                            log.warning("could not read original value: " + re.toString());
                        }
                        int y = format.parse(tf.getText()).intValue();
                        newValue = new Integer(y);
                        w.invoke(filter, newValue); // write int value
                        firePropertyChange(PROPERTY_VALUE, oldValue, newValue);
                    } catch (ParseException pe) {
                        //Handle exception
                    } catch (NumberFormatException fe) {
                        tf.selectAll();
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {

                @Override
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    Integer newValue = null;
                    Integer oldValue = null;

                    try {
                        oldValue = (Integer) r.invoke(filter);
                        initValue = oldValue.intValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
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
                                    nval = Math.round(initValue * KEY_FACTOR);
                                }
                                w.invoke(filter, newValue = new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if (code == KeyEvent.VK_DOWN) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = 0;
                                } else {
                                    nval = Math.round(initValue / KEY_FACTOR);
                                }
                                w.invoke(filter, newValue = new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        }
                    } else // shifted int control just incs or decs by 1
                    {
                        if (code == KeyEvent.VK_UP) {
                            try {
                                nval = initValue + 1;
                                w.invoke(filter, newValue = new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if (code == KeyEvent.VK_DOWN) {
                            try {
                                nval = initValue - 1;
                                w.invoke(filter, newValue = new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        }
                    }
                    if (evt.getKeyCode() == KeyEvent.VK_TAB) {
                        try {
                            NumberFormat format = NumberFormat.getNumberInstance();
                            int y = format.parse(tf.getText()).intValue();
                            w.invoke(filter, newValue = new Integer(y)); // write int value
                            fixIntValue(tf, r);
                        } catch (ParseException pe) {
                            //Handle exception
                        } catch (NumberFormatException fe) {
                            tf.selectAll();
                        } catch (InvocationTargetException ite) {
                            ite.printStackTrace();
                        } catch (IllegalAccessException iae) {
                            iae.printStackTrace();
                        }
                        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                        manager.focusNextComponent();
                    }
                    firePropertyChange(PROPERTY_VALUE, oldValue, newValue);
                }
            }
            );
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {

                @Override
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt
                ) {
                    Integer oldValue = null, newValue = null;
                    try {
                        oldValue = (Integer) r.invoke(filter);
                        initValue = oldValue.intValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code = evt.getWheelRotation();
                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code < 0) {
                            try {
                                nval = initValue;
                                if (Math.round(initValue * WHEEL_FACTOR) == initValue) {
                                    nval++;
                                } else {
                                    nval = Math.round(initValue * WHEEL_FACTOR);
                                }
                                w.invoke(filter, newValue = new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if (code > 0) {
                            try {
                                nval = initValue;
                                if (Math.round(initValue / WHEEL_FACTOR) == initValue) {
                                    nval--;
                                } else {
                                    nval = Math.round(initValue / WHEEL_FACTOR);
                                }
                                if (nval < 0) {
                                    nval = 0;
                                }
                                w.invoke(filter, newValue = new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        }
                    }
                    firePropertyChange(PROPERTY_VALUE, oldValue, newValue);
                }
            }
            );
            tf.addFocusListener(
                    new FocusListener() {

                @Override
                public void focusGained(FocusEvent e
                ) {
                    tf.setSelectionStart(0);
                    tf.setSelectionEnd(tf.getText().length());
                }

                @Override
                public void focusLost(FocusEvent e
                ) {
                }
            }
            );
            setMaximumSize(getPreferredSize());
        }
    }

    void fixIntValue(JTextField tf, Method r) {
        // set text to actual value
        try {
            Integer x = (Integer) r.invoke(getFilter()); // read int value
//            initValue=x.intValue();
            String s = NumberFormat.getIntegerInstance().format(x);
            tf.setText(s);
        } catch (Exception e) {
            e.printStackTrace();

        }

    }

    class FloatControl extends MyControl implements HasSetter {

        EngineeringFormat engFmt = new EngineeringFormat();
//        final String format="%.6f";

        Method write, read;
        EventFilter filter;
        float initValue = 0, nval;
        final JTextField tf;

        @Override
        public void set(Object o) {
            if (o instanceof Float) {
                Float b = (Float) o;
                tf.setText(engFmt.format(b));
            } else if (o instanceof Integer) {
                int b = (Integer) o;
                tf.setText(engFmt.format((float) b));
            }
        }

        public FloatControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(f.getClass().getSimpleName() + "." + name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);
            tf = new JTextField("", 10);
            tf.setMaximumSize(new Dimension(100, 50));
            tf.setToolTipText("Float control: use arrow keys or mouse wheel to change value by factor. Shift reduces factor.");
            try {
                Float x = (Float) r.invoke(filter);
                if (x == null) {
                    log.warning("null Float returned from read method " + r);
                    return;
                }
                initValue = x.floatValue();
                tf.setText(engFmt.format(initValue));
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
//                    System.out.println(e);
                    try {
                        float y = engFmt.parseFloat(tf.getText());
                        w.invoke(filter, new Float(y));
                        Float x = (Float) r.invoke(filter); // getString the value from the getter method to constrain it
                        nval = x.floatValue();
                        tf.setText(engFmt.format(nval));
                    } catch (NumberFormatException fe) {
                        tf.selectAll();
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {

                {
                }

                @Override
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    try {
                        Float x = (Float) r.invoke(filter); // getString the value from the getter method
                        initValue = x.floatValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code = evt.getKeyCode();
                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    float floatFactor = KEY_FACTOR;
                    if (shift) {
                        floatFactor = 1 + ((WHEEL_FACTOR - 1) / 4);
                    }
                    if (code == KeyEvent.VK_UP) {
                        try {
                            nval = initValue;
                            if (nval == 0) {
                                nval = DEFAULT_REAL_VALUE;
                            } else {
                                nval = (initValue * floatFactor);
                            }
                            w.invoke(filter, new Float(nval)); // setter the value
                            Float x = (Float) r.invoke(filter); // getString the value from the getter method to constrain it
                            nval = x.floatValue();
                            tf.setText(engFmt.format(nval));
                        } catch (InvocationTargetException ite) {
                            ite.printStackTrace();
                        } catch (IllegalAccessException iae) {
                            iae.printStackTrace();
                        }
                    } else if (code == KeyEvent.VK_DOWN) {
                        try {
                            nval = initValue;
                            if (nval == 0) {
                                nval = DEFAULT_REAL_VALUE;
                            } else {
                                nval = (initValue / floatFactor);
                            }
                            w.invoke(filter, new Float(initValue / floatFactor));
                            Float x = (Float) r.invoke(filter); // getString the value from the getter method to constrain it
                            nval = x.floatValue();
                            tf.setText(engFmt.format(nval));
                        } catch (InvocationTargetException ite) {
                            ite.printStackTrace();
                        } catch (IllegalAccessException iae) {
                            iae.printStackTrace();
                        }
                    }
                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {

                @Override
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try {
                        Float x = (Float) r.invoke(filter); // getString the value from the getter method
                        initValue = x.floatValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
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
                                    nval = DEFAULT_REAL_VALUE;
                                } else {
                                    nval = (initValue * WHEEL_FACTOR);
                                }
                                w.invoke(filter, new Float(nval)); // setter the value
                                Float x = (Float) r.invoke(filter); // getString the value from the getter method to constrain it
                                nval = x.floatValue();
                                tf.setText(engFmt.format(nval));
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if (code > 0) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = DEFAULT_REAL_VALUE;
                                } else {
                                    nval = (initValue / WHEEL_FACTOR);
                                }
                                w.invoke(filter, new Float(initValue / WHEEL_FACTOR));
                                Float x = (Float) r.invoke(filter); // getString the value from the getter method to constrain it
                                nval = x.floatValue();
                                tf.setText(engFmt.format(nval));
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
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

    private boolean printedSetterWarning = false;

    /**
     * Called when a filter calls firePropertyChange. The PropertyChangeEvent
     * should send the bound property name and the old and new values. The GUI
     * control is then updated by this method.
     *
     * @param propertyChangeEvent contains the property that has changed, e.g.
     * it would be called from an EventFilter with
     * <code>support.firePropertyChange("mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled, this.mapEventsToLearnedTopologyEnabled);</code>
     */
    @Override
    public void propertyChange(final PropertyChangeEvent propertyChangeEvent) {
        if (propertyChangeEvent.getSource() == getFilter()) {
            if (propertyChangeEvent.getPropertyName().equals("selected")) {
                return; // ignore changes to "selected" for filter because these are masked out from GUI building
            } else if (propertyChangeEvent.getPropertyName().equals("filterEnabled")) { // comes from EventFilter when filter is enabled or disabled
//            log.info("propertyChangeEvent name="+propertyChangeEvent.getPropertyName()+" src="+propertyChangeEvent.getSource()+" oldValue="+propertyChangeEvent.getOldValue()+" newValue="+propertyChangeEvent.getNewValue());
                boolean yes = (Boolean) propertyChangeEvent.getNewValue();
                enabledCheckBox.setSelected(yes);
                setBorderActive(yes);
//                if (yes) {
//                    log.info("selecting checkbox from " + propertyChangeEvent);
//                }
            } else if (propertyChangeEvent.getPropertyName().startsWith("doToggleOff")) {
                // handle toggle off property changes to disable buttons that may have started logging, for example
                for (AbstractButton b : doButList) {
                    if((b instanceof JToggleButton) && b.getText().equals(propertyChangeEvent.getPropertyName().substring(11))){
                        b.setSelected(false);
                    }
                }
            } else {
                // we need to find the control and set it appropriately. we don't need to set the property itself since this has already been done!
                try {
//                    log.info("PropertyChangeEvent received from " +
//                            propertyChangeEvent.getSource() + " for property=" +
//                            propertyChangeEvent.getPropertyName() +
//                            " newValue=" + propertyChangeEvent.getNewValue());
                    final HasSetter setter = setterMap.get(getFilter().getClass().getSimpleName() + "." + propertyChangeEvent.getPropertyName());
                    if (setter == null) {
                        if (!printedSetterWarning) {
                            log.warning("in filter " + getFilter() + " there is no setter for property change from property named " + propertyChangeEvent.getPropertyName());
                            printedSetterWarning = true;
                        }
                    } else {
//                        log.info("setting "+setter.toString()+" to "+propertyChangeEvent.getNewValue());
                        if (SwingUtilities.isEventDispatchThread()) {
                            setter.set(propertyChangeEvent.getNewValue());
                        } else {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        setter.set(propertyChangeEvent.getNewValue());
                                    } catch (Exception e) {
                                        log.warning("caught exception " + propertyChangeEvent.getNewValue() + " in property change:" + e.toString());
                                    }
                                }
                            });
                        }
                    }

//                    PropertyDescriptor pd=new PropertyDescriptor(propertyChangeEvent.getPropertyName(), getFilter().getClass());
//                    Method wm=pd.getWriteMethod();
//                    wm.invoke(getFilter(), propertyChangeEvent.getNewValue());
                } catch (Exception e) {
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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        enableResetControlsHelpPanel = new javax.swing.JPanel();
        enabledCheckBox = new javax.swing.JCheckBox();
        resetButton = new javax.swing.JButton();
        showControlsToggleButton = new javax.swing.JToggleButton();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        enableResetControlsHelpPanel.setToolTipText("Generic controls for this EventFilter");
        enableResetControlsHelpPanel.setAlignmentX(1.0F);
        enableResetControlsHelpPanel.setPreferredSize(new java.awt.Dimension(100, 23));
        enableResetControlsHelpPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 2));

        enabledCheckBox.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        enabledCheckBox.setToolTipText("Enable or disable the filter");
        enabledCheckBox.setMargin(new java.awt.Insets(1, 1, 1, 1));
        enabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                enabledCheckBoxActionPerformed(evt);
            }
        });
        enableResetControlsHelpPanel.add(enabledCheckBox);

        resetButton.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        resetButton.setText("Reset");
        resetButton.setToolTipText("Resets the filter");
        resetButton.setMargin(new java.awt.Insets(1, 5, 1, 5));
        resetButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetButtonActionPerformed(evt);
            }
        });
        enableResetControlsHelpPanel.add(resetButton);

        showControlsToggleButton.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        showControlsToggleButton.setText("Controls");
        showControlsToggleButton.setToolTipText("Show filter parameters, hides other filters. Click again to see all filters.");
        showControlsToggleButton.setMargin(new java.awt.Insets(1, 5, 1, 5));

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, this, org.jdesktop.beansbinding.ELProperty.create("${controlsVisible}"), showControlsToggleButton, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        enableResetControlsHelpPanel.add(showControlsToggleButton);

        add(enableResetControlsHelpPanel);

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents
    boolean controlsVisible = false;

    public boolean isControlsVisible() {
        return controlsVisible;
    }

    /**
     * Set visibility of individual filter controls; hides other filters.
     *
     * @param visible true to show filter parameter controls, false to hide this
     * filter's controls and to show all filters in chain.
     */
    public void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        getFilter().setSelected(visible); // exposing controls 'selects' this filter
        setBorderActive(visible);
        for (JComponent p : controls) {
            p.setVisible(visible);
            p.invalidate();
        }

        invalidate();
        Container c = getTopLevelAncestor();
        if (c == null) {
            return;
        }

        // TODO fix bug here with enclosed filters not showing up if they are enclosed in enclosed filter, unless they are declared as enclosed
        if (!getFilter().isEnclosed() && ((c instanceof Window))) {
            if (c instanceof FilterFrame) {
                // hide all filters except one that is being modified, *unless* we are an enclosed filter
                FilterFrame<FilterPanel> ff = (FilterFrame) c;
                for (FilterPanel f : ff.filterPanels) {
                    if (f == this) {  // for us and if !visible
                        f.setVisible(true); // always set us visible in chain since we are the one being touched
                        continue;
                    }

                    f.setVisible(!visible); // hide / show other filters
                }

            }
//            if (c instanceof Window) // Redundant
//                ((Window) c).pack();

        }

        if (controlPanel != null) {
            controlPanel.setVisible(visible);
        }

//        if (c instanceof Window) {
//            ((Window) c).pack();
//        }
        if (!getFilter().isEnclosed()) { // store last selected top level filter
            if (visible) {
                getFilter().getChip().getPrefs().put(FilterFrame.LAST_FILTER_SELECTED_KEY, getFilter().getClass().toString());
            } else {
                getFilter().getChip().getPrefs().put(FilterFrame.LAST_FILTER_SELECTED_KEY, "");
            }
        }

        if (visible) {
            // Show only controls.
            showControlsToggleButton.setSelected(true);
            showControlsToggleButton.setText("Back to filters list");
        } else {
            showControlsToggleButton.setSelected(false);
            showControlsToggleButton.setText("Controls");
        }
    }

    private void setBorderActive(final boolean yes) {
        // see http://forum.java.sun.com/thread.jspa?threadID=755789
        if (yes) {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.red);
            if (!getFilter().isEnclosed()) {
                titledBorder.setBorder(redLineBorder);
            } else {
                titledBorder.setBorder(enclosedFilterSelectedBorder);
            }
        } else {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textInactiveText);
            titledBorder.setBorder(normalBorder);
        }

    }

    void toggleControlsVisible() {
        controlsVisible = !controlsVisible;
        setControlsVisible(controlsVisible);
    }

    public EventFilter getFilter() {
        return filter;
    }

    public void setFilter(EventFilter filter) {
        this.filter = filter;
    }

    /**
     * Returns the FilterPanel controls for enclosed filter
     *
     * @param filter
     * @return the panel, or null if there is no panel or the filter is not
     * enclosed by this filter
     */
    public FilterPanel getEnclosedFilterPanel(EventFilter filter) {
        return enclosedFilterPanels.get(filter);
    }

    private void enabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enabledCheckBoxActionPerformed
        boolean yes = enabledCheckBox.isSelected();
        if (getFilter() != null) {
            getFilter().setFilterEnabled(yes);
        }

        if (yes) {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textHighlight);
            titledBorder.setBorder(redLineBorder);
        } else {
            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textInactiveText);
            titledBorder.setBorder(normalBorder);
        }

        repaint();
        getFilter().setSelected(yes);
    }//GEN-LAST:event_enabledCheckBoxActionPerformed

    private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetButtonActionPerformed
        if (getFilter() != null) {
            getFilter().resetFilter();
        }
        getFilter().setSelected(true);
    }//GEN-LAST:event_resetButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    protected javax.swing.JPanel enableResetControlsHelpPanel;
    protected javax.swing.JCheckBox enabledCheckBox;
    private javax.swing.JButton resetButton;
    private javax.swing.JToggleButton showControlsToggleButton;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    private class SliderParams {

        Class paramClass = null;
        int minIntValue, maxIntValue;
        float minFloatValue, maxFloatValue;

        SliderParams(Class clazz, int minIntValue, int maxIntValue, float minFloatValue, float maxFloatValue) {
            this.minIntValue = minIntValue;
            this.minFloatValue = minFloatValue;
            this.maxIntValue = maxIntValue;
            this.maxFloatValue = maxFloatValue;
        }
    }

    private SliderParams isSliderType(PropertyDescriptor p, net.sf.jaer.eventprocessing.EventFilter filter) throws SecurityException {
//                if(c instanceof Class) System.out.println("filter="+filter+" propertyType="+c);
        //TODO add slider control type if property has getMin and getMax methods
        boolean isSliderType = false;
        // check for min/max methods for property, e.g. getMinDt, getMaxDt for property dt
        String propCapped = p.getName().substring(0, 1).toUpperCase() + p.getName().substring(1); // eg. Dt for dt
        String minMethName = "getMin" + propCapped;
        String maxMethName = "getMax" + propCapped;
        SliderParams params = null;
        try {
            Method minMethod = filter.getClass().getMethod(minMethName, (Class[]) null);
            Method maxMethod = filter.getClass().getMethod(maxMethName, (Class[]) null);
            isSliderType = true;
//            log.info("property " + p.getName() + " for filter " + filter + " has min/max methods, constructing slider control for it");
            if (p.getPropertyType() == Integer.TYPE) {
                int min = (Integer) minMethod.invoke(filter);
                int max = (Integer) maxMethod.invoke(filter);
                params
                        = new SliderParams(Integer.class, min, max, 0, 0);
            } else if (p.getPropertyType() == Float.TYPE) {
                float min = (Float) minMethod.invoke(filter);
                float max = (Float) maxMethod.invoke(filter);
                params
                        = new SliderParams(Integer.class, 0, 0, min, max);
            }
        } catch (NoSuchMethodException e) {
        } catch (Exception iae) {
            log.warning(iae.toString() + " for property " + p + " in filter " + filter);
        }
        return params;

    }

    class Point2DControl extends MyControl implements HasSetter {

        Method write, read;
        EventFilter filter;
        Point2D.Float point;
        float initValue = 0, nval;
        final JTextField tfx, tfy;
        final String format = "%.1f";
        final JButton nullifyButton;

        @Override
        final public void set(Object o) {
            if (o == null) {
                tfx.setText(null);
                tfy.setText(null);
            } else if (o instanceof Point2D) {
                Point2D b = (Point2D) o;
                tfx.setText(String.format(format, b.getX()));
                tfy.setText(String.format(format, b.getY()));
            }
        }

        final class PointActionListener implements ActionListener {

            Method readMethod, writeMethod;
            Point2D point = new Point2D.Float(0, 0);

            public PointActionListener(Method readMethod, Method writeMethod) {
                this.readMethod = readMethod;
                this.writeMethod = writeMethod;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
//                    System.out.println(e);
                try {
                    float x = Float.parseFloat(tfx.getText());
                    float y = Float.parseFloat(tfy.getText());
                    point.setLocation(x, y);
                    writeMethod.invoke(filter, point);
                    point = (Point2D) readMethod.invoke(filter); // getString the value from the getter method to constrain it
                    set(point);
                } catch (NumberFormatException fe) {
                    tfx.selectAll();
                    tfy.selectAll();
                } catch (InvocationTargetException ite) {
                    ite.printStackTrace();
                } catch (IllegalAccessException iae) {
                    iae.printStackTrace();
                }
            }
        }

        final class PointNullifyActionListener implements ActionListener {

            Method writeMethod;

            public PointNullifyActionListener(Method writeMethod) {
                this.writeMethod = writeMethod;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
//                    System.out.println(e);
                try {
                    Object arg = null;
                    writeMethod.invoke(filter, arg);
                    set(null);
                } catch (InvocationTargetException ite) {
                    ite.printStackTrace();
                } catch (IllegalAccessException iae) {
                    iae.printStackTrace();
                }
            }
        }

        public Point2DControl(final EventFilter f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            tfx = new JTextField("", 10);
            tfx.setMaximumSize(new Dimension(100, 50));
            tfx.setToolTipText("Point2D X: type new value here and press enter. Set blank to set null value for Point2D.");

            tfy = new JTextField("", 10);
            tfy.setMaximumSize(new Dimension(100, 50));
            tfy.setToolTipText("Point2D Y: type new value here and press enter. Set blank to set null value for Point2D.");

            nullifyButton = new JButton("Nullify");
            nullifyButton.setMaximumSize(new Dimension(100, 50));
            nullifyButton.setToolTipText("Sets Point2D to null value");

            try {
                Point2D p = (Point2D) r.invoke(filter);
//                if (p == null) {
//                    log.warning("null object returned from read method " + r);
//                    return;
//                }
                set(p);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                log.warning("cannot access the field named " + name + " check if the class or method is not public?");
                e.printStackTrace();
            }

            add(tfx);
            add(new JLabel(", "));
            add(tfy);
            add(nullifyButton);

            tfx.addActionListener(new PointActionListener(r, w));
            tfy.addActionListener(new PointActionListener(r, w));
            nullifyButton.addActionListener(new PointNullifyActionListener(write));

            tfx.addFocusListener(new FocusListener() {

                @Override
                public void focusGained(FocusEvent e) {
                    tfx.setSelectionStart(0);
                    tfx.setSelectionEnd(tfx.getText().length());
                }

                @Override
                public void focusLost(FocusEvent e) {
                }
            });
            tfy.addFocusListener(new FocusListener() {

                @Override
                public void focusGained(FocusEvent e) {
                    tfy.setSelectionStart(0);
                    tfy.setSelectionEnd(tfy.getText().length());
                }

                @Override
                public void focusLost(FocusEvent e) {
                }
            });

        }
    }

// Addition by Peter: Allow user to add custom filter controls
//    ArrayList<JPanel> customControls=new ArrayList();
    JPanel controlPanel;

    public void addCustomControls(JPanel control) {
        if (controlPanel == null) {
            controlPanel = new JPanel();
            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
            this.add(controlPanel);
        }

        this.controlPanel.add(control);
//        this.customControls.add(controls);

        setControlsVisible(true);
//        this.repaint();
//        this.revalidate();
    }

    public void removeCustomControls() {
//        for (JPanel p:customControls)
//            controls.remove(p);
        if (controlPanel != null) {
            controlPanel.removeAll();
            controlPanel.repaint();
        }
//        customControls.clear();

    }

    private ArrayList<MyControl> highlightedControls = new ArrayList();

    /**
     * Highlights properties that match the string s
     *
     * @param s
     */
    public void highlightProperties(String s) {
        s = s.toLowerCase();
//        System.out.println("\n************** \n searching for " + s + "\n");
        for (MyControl c : highlightedControls) {
            c.setBorder(null);
            c.repaint(300);
//            System.out.println("cleared "+c);
        }
//        System.out.println("");
        highlightedControls.clear();
        if (s.isEmpty()) {
            return;
        }
        for (String propName : propertyControlMap.keySet()) {
            if (propName.toLowerCase().contains(s)) {
                MyControl c = propertyControlMap.get(propName);
//                System.out.println("highlighted " + propName);
                if (c != null) {
                    c.setBorder(new LineBorder(Color.red));
                    c.repaint(300);
                    highlightedControls.add(c);
                }
            }
        }
    }

//    public class ShowControlsAction extends AbstractAction{
//
//        public ShowControlsAction() {
//            super("Show controls");
//            putValue(SELECTED_KEY, "Hide controls");
//            putValue(SHORT_DESCRIPTION,"Toggles visibility of controls of this EventFilter");
//
//        }
//
//        @Override
//        public void actionPerformed(ActionEvent e) {
//            setControlsVisible(enabled);
//        }
//
//    }
}

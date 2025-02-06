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
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.SystemColor;
import java.awt.Toolkit;
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
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.StateEdit;
import javax.swing.undo.StateEditable;
import javax.swing.undo.UndoableEditSupport;
import net.sf.jaer.Preferred;
import net.sf.jaer.eventprocessing.EventFilter.PrefsKeyClassValueDefault;
import static net.sf.jaer.eventprocessing.FilterFrame.prefs;
import net.sf.jaer.eventprocessing.filter.PreferencesMover;

import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.XMLFileFilter;

/**
 * A panel for a filter that has Integer/Float/Boolean/String/enum getter/setter
 * methods (bound properties). These methods are introspected and a set of
 * controls are built for them. Enclosed filters and filter chains have panels
 * built for them that are enclosed inside the filter panel, hierarchically.
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
 * property currentState is, then it should fire PropertyChangeEvent when the
 * property changes. For example, an {@link EventFilter} can implement a setter
 * like this:
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
 * setUndoableState yet).
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

    private FilterFrame filterFrame = null;

    static final float LEFT_ALIGNMENT = Component.LEFT_ALIGNMENT;
    private BeanInfo info;
    private PropertyDescriptor[] props;
    private Method[] methods;
    private final static Logger log = Logger.getLogger("net.sf.jaer");
    private EventFilter filter = null;
    final float fontSize = 10f;
    private Border normalBorder, redLineBorder, blueLineBorder, enclosedFilterSelectedBorder;
    private TitledBorder titledBorder;
    /**
     * map from filter to property, to apply property change events to control
     */
    private final HashMap<String, MyControl> setterMap = new HashMap<>();
    /**
     * A list of all property controls
     */
    protected final java.util.ArrayList<JComponent> controls = new ArrayList<>();
    /**
     * maps from enclosed filter to its panel
     */
    private final HashMap<EventFilter, FilterPanel> enclosedFilterPanels = new HashMap<>();
    /**
     * maps from key group name string to the panel holding the properties
     */
    private final HashMap<String, Container> groupContainerMap = new HashMap<>();
    /**
     * Maps from property name to the group container panel holding the property
     */
    private final HashMap<String, Container> propertyToGroupPanelMap = new HashMap<>();
    /**
     * Set of all property groups that have at least one item in them
     */
    private final HashSet<String> populatedGroupSet = new HashSet();
    /**
     * Map from property name to its control
     */
    private final HashMap<String, MyControl> propertyControlMap = new HashMap();
    private JComponent ungroupedControls = null;
    private float DEFAULT_REAL_VALUE = 0.01f; // value jumped to from zero on key or wheel up
    ArrayList<AbstractButton> doButList = new ArrayList();
    JPanel butPanel = null;

    /**
     * Flag to show simple view of only preferred properties
     */
    private boolean simple = false;

    /**
     * String that user enters into FilterFrame search box, setUndoableState by
     * FilterFrame
     */
    private String searchString = "";

    /**
     * Current highlight(s)
     */
    private HashSet<MyControl> highlightedControls = new HashSet();

    /**
     * Properties modified from default preference value
     */
    private HashSet<MyControl> modifiedControls = new HashSet();

    /**
     * Flag setUndoableState by FilterFrame that says only show the filtered
     * property
     */
    private boolean hideOthers = false;

    /**
     * Creates new form FilterPanel
     */
    public FilterPanel() {
        initComponents();
    }

    public FilterPanel(EventFilter f, FilterFrame filterFrame) {
        setFilter(f);
        setFilterFrame(filterFrame);
        filterFrame.filter2FilterPanelMap.put(f,this);
        initComponents();
        Dimension d = enableResetControlsHelpPanel.getPreferredSize();
        enableResetControlsHelpPanel.setMaximumSize(new Dimension(200, d.height)); // keep from stretching
        exportImportPanel.setMaximumSize(new Dimension(200, d.height)); // keep from stretching
        String cn = getFilter().getClass().getName();
        int lastdot = cn.lastIndexOf('.');
        String name = cn.substring(lastdot + 1);
        setName(name);
        titledBorder = new TitledBorder(name);
        titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
        setBorder(titledBorder);
        normalBorder = titledBorder.getBorder();
        redLineBorder = BorderFactory.createLineBorder(Color.red, 1);
        blueLineBorder = BorderFactory.createLineBorder(Color.blue, 1);
        enclosedFilterSelectedBorder = BorderFactory.createLineBorder(Color.orange, 3);
        enabledCheckBox.setSelected(getFilter().isFilterEnabled());
        add(Box.createVerticalStrut(0));
        buildPanel();
        add(Box.createHorizontalStrut(0));  // use up vertical space to get components to top

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
            propertyToGroupPanelMap.put(propertyName, container);
//            }
        } else {
            ungroupedControls.add(comp);
        }
        controls.add(comp);
        propertyControlMap.put(propertyName, comp);
    }

    private void clearHighlights() {
        for (MyControl c : highlightedControls) {
            c.setBorder(null);
        }
        highlightedControls.clear();
    }

    private void clearModifed() {
        for (MyControl c : modifiedControls) {
            c.setBorder(null);
        }
        modifiedControls.clear();
    }

    /**
     * Returns the group panel holding a property, or null
     *
     * @param propertyName
     * @return the panel or null if none
     */
    private Container getGroupPanel(String propertyName) {
        return propertyToGroupPanelMap.get(propertyName);
    }

    /**
     * Rebuild panel contents
     */
    public void rebuildPanel() {
        boolean wasSelected = getFilter().isSelected();
        clearPanel();
        buildPanel();
        if (wasSelected) {
            getFilter().setSelected(wasSelected);
        }
    }

    private void clearPanel() {
        for (Component c : controls) {
            remove(c);
        }

    }

    private void buildPanel() {
        addIntrospectedControls();
        clearHighlights();
        highlightNonDefaultProperties();
    }

    private void highlightNonDefaultProperties() {
        HashMap<String, PrefsKeyClassValueDefault> prefsMap = getFilter().getNonDefaultProperties();
        for (var e : prefsMap.entrySet()) {
            MyControl c = propertyControlMap.get(e.getValue().key());
            if (c != null) {
                c.highlightModified();
            }
        }
    }

    // gets getter/setter methods for the filter and makes controls for them. enclosed filters are also added as submenus
    private void addIntrospectedControls() {
        boolean wasSelected = getFilter().isSelected(); // restore the currentState in case filter is rebuilt
        doButList.clear();
        String u = "(Ungrouped)";
        ungroupedControls = new MyContainer(u);
        ungroupedControls.setName(u);
        ungroupedControls.setBorder(new TitledBorder(u));
        ungroupedControls.setLayout(new BoxLayout(ungroupedControls, BoxLayout.Y_AXIS));
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
                String s = "Filter Actions";
                butPanel = new MyContainer(s);
                butPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
                butPanel.add(buttons);
                TitledBorder tb = new TitledBorder(s);
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
                // determine if the property (field) is annotated as Preferred
                // 
                boolean preferred = false;
                String propName = p.getName();
                Class clz = getFilter().getClass();
                Field field = null;
                while (field == null && clz != null) // stop when we got field or reached top of class hierarchy
                {
                    try {
                        field = clz.getDeclaredField(propName);
                    } catch (NoSuchFieldException e) {
                        // only get super-class when we couldn't find field
                        clz = clz.getSuperclass();
                    }
                }
                if (field != null) {
                    Annotation annotation = field.getAnnotation(Preferred.class);
                    if (annotation != null) {
                        preferred = true;
                    }
                }
                if (p.getReadMethod() != null) {
                    Annotation annotation = p.getReadMethod().getAnnotation(Preferred.class);
                    if (annotation != null) {
                        preferred = true;
                    }
                }
                if (p.getWriteMethod() != null) {
                    Annotation annotation = p.getWriteMethod().getAnnotation(Preferred.class);
                    if (annotation != null) {
                        preferred = true;
                    }
                }
                if (preferred) {
                    filter.markPropertyAsPreferred(propName);
                    log.finer(String.format("Marked %s as preferred by @Preferred annotation", propName));
                }

//                Class c = p.getPropertyType();
                if (p.getName().equals("enclosedFilter")) { //if(c==EventFilter2D.class){
                    // if type of property is an EventFilter, check if it has either an enclosed filter
                    // or an enclosed filter chain. If so, construct FilterPanels for each of them.
                    try {
                        Method r = p.getReadMethod(); // getString the getter for the enclosed filter
                        EventFilter2D enclFilter = (EventFilter2D) (r.invoke(getFilter()));
                        if (enclFilter != null) {
//                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses EventFilter2D "+enclFilter.getClass().getSimpleName());
                            FilterPanel enclPanel = new FilterPanel(enclFilter, filterFrame);
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
                                
                                FilterPanel enclPanel = new FilterPanel(f, filterFrame);
                                if (f.isControlsVisible()) {
                                    enclPanel.setControlsVisible(true);
                                }
                                Dimension d = enclPanel.getPreferredSize();
                                d.setSize(Integer.MAX_VALUE, d.getHeight()); // setUndoableState height to preferred value, and width to max; see https://stackoverflow.com/questions/26596839/how-to-use-verticalglue-in-box-layout
                                enclPanel.setMaximumSize(d); // extra space to bottom
                                this.add(enclPanel);
                                controls.add(enclPanel);
                                enclosedFilterPanels.put(f, enclPanel);
                                ((TitledBorder) enclPanel.getBorder()).setTitle("enclosed: " + f.getClass().getSimpleName());
                                if (getFilter().isHideNonEnabledEnclosedFilters() && !f.isFilterEnabled()) {  
                                    // if this filter is part of chain but not enabled, then don't show the panel if hideNonEnabledEnclosedFilters is true
                                    enclPanel.setVisible(false);
                                }
                            }
//                            this.add(Box.createVerticalGlue()); // make the properties stick to the enclosed filters
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
                    JPanel groupPanel = new MyContainer(s);
                    groupPanel.setName(s);
                    groupPanel.setBorder(new TitledBorder(s));
                    groupPanel.setLayout(new BoxLayout(groupPanel, BoxLayout.Y_AXIS));
                    groupPanel.setAlignmentX(0);
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
                        log.log(Level.INFO, "not constructing control for {0} for hidden property {1}", new Object[]{filter.getClass().getSimpleName(), p.getName()});
                        continue;
                    }

                    // don't show this getter/setter that is controlled by button
                    if (p.getName().equals("controlsVisible")) {
                        continue;
                    }

                    if ((c == Integer.TYPE) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {

                        SliderParams params;
                        if ((params = isSliderType(p, filter)) != null) {
                            control = new IntSliderControl(p.getName(), p, params);
                        } else {
                            control = new IntControl(p.getName(), p);
                        }
                        myadd(control, name, inherited);
                    } else if ((c == Float.TYPE) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        SliderParams params;
                        if ((params = isSliderType(p, filter)) != null) {
                            control = new FloatSliderControl(p.getName(), p, params);
                        } else {
                            control = new FloatControl(p.getName(), p);

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

                        control = new BooleanControl(p.getName(), p);
                        myadd(control, name, inherited);
                    } else if ((c == String.class) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        if (p.getName().equals("filterEnabled")) {
                            continue;
                        }
                        if (p.getName().equals("annotationEnabled")) {
                            continue;
                        }
                        control = new StringControl(p.getName(), p);
                        myadd(control, name, inherited);
                    } else if ((c != null) && c.isEnum() && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        control = new EnumControl(p.getName(), p, c);
                        myadd(control, name, inherited);
                    } else if ((c != null) && ((c == Point2D.Float.class) || (c == Point2D.Double.class) || (c == Point2D.class)) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
                        control = new Point2DControl(p.getName(), p);
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
//                log.info("Removing emtpy container " + s + " from " + filter.getClass().getSimpleName());
                controls.remove(groupContainerMap.get(s));
                remove(groupContainerMap.get(s));
            }
        }

        setControlsVisible(wasSelected);
    }

    void addTip(EventFilter f, JLabel label) {
        String s = f.getPropertyTooltip(label.getText());
        if (s == null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);
        if (f.isPropertyPreferred(label.getText())) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        // add map from property name to label so we can change the tooltip dynamically
        getFilter().tooltipSupport.property2ComponentMap.put(label.getText(), label);
    }

    void addTip(EventFilter f, AbstractButton b) {
        String s = f.getPropertyTooltip(b.getText());
        if (s == null) {
            return;
        }
        b.setToolTipText(s);
        b.setForeground(Color.BLUE);
        getFilter().tooltipSupport.property2ComponentMap.put(b.getText(), b);
    }

    void addTip(EventFilter f, JCheckBox label) {
        String s = f.getPropertyTooltip(label.getText());
        if (s == null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);
        if (f.isPropertyPreferred(label.getText())) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        getFilter().tooltipSupport.property2ComponentMap.put(label.getText(), label);
    }

    class MyContainer extends JPanel {

        String name;

        MyContainer(String name) {
            this.name = name;
            setAlignmentX(LEFT_ALIGNMENT);
            setAlignmentY(TOP_ALIGNMENT);
        }
    }

    abstract class MyControl extends MyContainer implements StateEditable {

        PropertyDescriptor p;
        /**
         * Read and write methods for the property
         */
        Method read = null;
        Method write = null;
        private boolean addedUndoListener = false;
        StateEdit edit = null;
        Object currentState = null;

        public MyControl(String name, PropertyDescriptor p) {
            super(name);
            setterMap.put(name, this);
            filter = getFilter();
            this.p = p;
            if (p != null) {
                write = p.getWriteMethod();
                read = p.getReadMethod();
            }

            addAncestorListener(new javax.swing.event.AncestorListener() {

                public void ancestorAdded(javax.swing.event.AncestorEvent evt) {
                    if (addedUndoListener) {
                        return;
                    }
                    addedUndoListener = true;
                    if (evt.getComponent() instanceof Container) {
                        Container anc = (Container) evt.getComponent();
                        while (anc != null && anc instanceof Container) {
                            if (anc instanceof UndoableEditListener) {
                                getEditSupport().addUndoableEditListener((UndoableEditListener) anc);
                                break;
                            }
                            anc = anc.getParent();
                        }
                    }
                }

                public void ancestorMoved(javax.swing.event.AncestorEvent evt) {
                }

                public void ancestorRemoved(javax.swing.event.AncestorEvent evt) {
                }

            });
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension d = getPreferredSize();
            d.setSize(1000, d.getHeight());
            return d;
        }

        public void highlightModified() {
            setBorder(blueLineBorder);
            invalidate();
            modifiedControls.add(this);
        }

        public void highlightClearingOthers() {
            highlightModified();
            clearHighlights();
            setBorder(redLineBorder);
            invalidate();
            highlightedControls.add(this);
        }

        public void clearHighlight() {
            setBorder(null);
        }

        abstract void setGuiState(Object o);

        /**
         * Subclasses should call super().setUndoableState()
         */
        public Object setUndoableState(Object o) {
            if (o == null) {
                log.warning("null object, will not set " + name);
                return null;
            }
            try {
                startEdit();
                write.invoke(getFilter(), o); // might call property change listeners
                Object ro = read.invoke(getFilter()); // constrain by writer
                setCurrentState(ro);
                setGuiState(o);
                return ro;
            } catch (IllegalAccessException | InvocationTargetException ex) {
                Logger.getLogger(FilterPanel.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                endEdit();
            }
            return null;
        }

        public void setCurrentState(Object o) {
            this.currentState = o;
        }

        private UndoableEditSupport getEditSupport() {
            return getFilterFrame().editSupport;
        }

        /**
         * Makes a new MyStateEdit and calls storeState()
         */
        void startEdit() {
            edit = new MyStateEdit(this, name);
        }

        /**
         * Ends the currentState edit and posts the edit to the
         * UndoableEditSupport
         */
        void endEdit() {
            if (edit != null) {
                edit.end();
            }
            getEditSupport().postEdit(edit);
        }

        @Override
        public void storeState(Hashtable<Object, Object> state) {
            if (this.currentState == null) {
                log.fine("null state, not puttting state to undoableedit");
                return;
            }
            state.put("state", this.currentState);
            log.fine(String.format("Stored %s state  %s", name, currentState));
        }

        /**
         * Called by undo()
         */
        @Override
        public void restoreState(Hashtable<?, ?> state) {
            Object o = null;
            try {
                o = state.get("state");
            } catch (NullPointerException e) {
                log.warning("stored state is null, cannot restore");
                return;
            }
            setCurrentState(o);
            setGuiState(o);
            log.fine(String.format("Restored %s to %s", name, o));
        }

    }

    private void setFontSizeStyle(final JComponent label) {
        label.setFont(label.getFont().deriveFont(fontSize));
        label.setFont(label.getFont().deriveFont(Font.PLAIN));
    }

    class EnumControl extends MyControl {

        EventFilter filter;
        boolean initValue = false, nval;
        final JComboBox control;

        public EnumControl(final String name, final PropertyDescriptor p, final Class<? extends Enum> c) {
            super(name, p);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            setFontSizeStyle(label);
            addTip(getFilter(), label);
            add(label);

            control = new JComboBox(c.getEnumConstants()) {
                /**
                 * Do not fire if set by program.
                 */
                protected void fireActionEvent() {
                    // if the mouse made the selection -> the comboBox has focus
                    if (this.hasFocus()) {
                        super.fireActionEvent();
                    }
                }
            };
            control.setMaximumSize(new Dimension(100, 30));
            setFontSizeStyle(control);

            add(label);
            add(control);
            add(Box.createHorizontalGlue());

            try {
                Object x = read.invoke(getFilter());
                if (x == null) {
                    log.warning("null Object returned from read method " + read);
                    return;
                }
                setCurrentState(x);
                setGuiState(x);
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            control.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    highlightClearingOthers();
                    try {
                        setUndoableState(control.getSelectedItem());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }

        @Override
        void setGuiState(Object o) {
            control.setSelectedItem(o); // TODO handle string value
        }
    }

    class StringControl extends MyControl {

        EventFilter filter;
        boolean initValue = false, nval;
        final JTextField textField;
        final JLabel label;

        public StringControl(final String name, final PropertyDescriptor p) {
            super(name, p);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            setFontSizeStyle(label);
            addTip(getFilter(), label);
            add(label);

            textField = new JTextField(name);
            textField.setFont(textField.getFont().deriveFont(fontSize));
            textField.setHorizontalAlignment(SwingConstants.LEADING);
            textField.setColumns(10);

            add(label);
            add(textField);
            add(Box.createHorizontalGlue());
            try {
                String x = (String) read.invoke(getFilter());
                if (x == null) {
                    log.warning("null String returned from read method " + read);
                    return;
                }
                setCurrentState(x);
                setGuiState(x);
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            textField.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    highlightClearingOthers();
                    try {
                        setUndoableState(textField.getText());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            });
        }

        @Override
        void setGuiState(Object o) {
            if (o instanceof String) {
                String b = (String) o;
                textField.setText(b);
                if (textField.hasFocus()) {
                    label.setFont(label.getFont().deriveFont(Font.BOLD | Font.ITALIC));
                }
            }
        }

    }
    private final float KEY_FACTOR = (float) Math.sqrt(2), WHEEL_FACTOR = (float) Math.pow(2, 1. / 16); // factors to change by with arrow and mouse wheel

    class BooleanControl extends MyControl {

        EventFilter filter;
        boolean initValue = false, nval;
        final JCheckBox checkBox;

        public BooleanControl(final String name, PropertyDescriptor p) {
            super(name, p);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            checkBox = new JCheckBox(name);
            checkBox.setAlignmentX(LEFT_ALIGNMENT);
            checkBox.setHorizontalTextPosition(SwingConstants.LEFT);
            setFontSizeStyle(checkBox);
            addTip(getFilter(), checkBox);
            add(checkBox);

//            add(Box.createVerticalStrut(0));
            try {
                Boolean x = (Boolean) read.invoke(getFilter());
                if (x == null) {
                    log.warning("null Boolean returned from read method " + read);
                    return;
                }
                initValue = x.booleanValue();
                setCurrentState(initValue);
                setGuiState(currentState);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            checkBox.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    highlightClearingOthers();
                    setUndoableState(checkBox.isSelected());
                }
            });
        }

        @Override
        public Object setUndoableState(Object o) {
            Object ro = super.setUndoableState(o);

            return ro;
        }

        @Override
        void setGuiState(Object o) {
            if (o instanceof Boolean) {
                Boolean b = (Boolean) o;
                checkBox.setSelected(b);
                // check if we need to setUndoableState toggle button for boolean control
                for (AbstractButton but : doButList) {
                    if (but.getText().toLowerCase().equals(checkBox.getText().toLowerCase())) {
                        but.setSelected(b);
                    }

                }
            } else if (o instanceof String) {
                try {
                    Boolean i = Boolean.parseBoolean((String) o);
                    checkBox.setSelected(i);
                } catch (NumberFormatException e) {
                    log.warning(String.format("could not parse value %s", o));
                }
            }
            if (checkBox.hasFocus()) {
                checkBox.setFont(checkBox.getFont().deriveFont(Font.BOLD | Font.ITALIC));
            }

        }
    }

    class IntSliderControl extends MyControl {

        EventFilter filter;
        int initValue = 0, nval;
        JSlider slider;
        JTextField tf;
        private boolean sliderDontProcess = false;

        public IntSliderControl(final String name, final PropertyDescriptor p, final SliderParams params) {
            super(name, p);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);

            final IntControl ic = new IntControl(name, p);

            tf = ic.tf;
            add(ic);
            slider = new JSlider(params.minIntValue, params.maxIntValue);
            slider.setMaximumSize(new Dimension(200, 50));

            try {
                Integer x = (Integer) read.invoke(getFilter()); // read int value
                if (x == null) {
                    log.warning("null Integer returned from read method " + read);
                    return;
                }
                initValue = x.intValue();
                setCurrentState(initValue);
                setGuiState(currentState);
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            add(slider);
            add(Box.createHorizontalGlue());

            slider.addMouseListener(new MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent evt) {
                    highlightClearingOthers();
                    startEdit();
                }

                public void mouseReleased(java.awt.event.MouseEvent evt) {
                    ic.setUndoableState(slider.getValue());
                    endEdit();
                }
            }
            );

            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    if (sliderDontProcess) {
                        return;
                    }
                    ic.setGuiState(slider.getValue());
                }
            });

            ic.addPropertyChangeListener(ic.PROPERTY_VALUE, new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent pce) {
                    if ((pce.getNewValue() == null) || !(pce.getNewValue() instanceof Integer)) {
                        return;
                    }
                    sliderDontProcess = true;
                    setUndoableState((Integer) (pce.getNewValue()));
                    sliderDontProcess = false;
                }
            });

        }

        @Override
        void setGuiState(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                slider.setValue(b);
            }
        }
    }

    class FloatSliderControl extends MyControl {

        EventFilter filter;
        JSlider slider;
        JTextField tf;
        EngineeringFormat engFmt;
        FloatControl fc;
        boolean dontProcessEvent = false; // to avoid slider callback loops
        float minValue, maxValue, currentValue;

        public FloatSliderControl(final String name, final PropertyDescriptor p, final SliderParams params) {
            super(name, p);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);

            fc = new FloatControl(name, p);
            add(fc);
            minValue = params.minFloatValue;
            maxValue = params.maxFloatValue;
            slider = new JSlider();
            slider.setMaximumSize(new Dimension(200, 50));

            engFmt = new EngineeringFormat();

            try {
                Float x = (Float) read.invoke(getFilter()); // read int value
                if (x == null) {
                    log.warning("null Float returned from read method " + read);
                    return;
                }
                currentValue = x.floatValue();
                setCurrentState(x);
                setGuiState(x);
            } catch (Exception e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            add(slider);
            add(Box.createHorizontalGlue());

            slider.addMouseListener(new MouseAdapter() {
                public void mousePressed(java.awt.event.MouseEvent evt) {
                    highlightClearingOthers();
                    startEdit();
                }

                public void mouseReleased(java.awt.event.MouseEvent evt) {
                    currentValue = minValue + ((maxValue - minValue) * ((float) slider.getValue() / (slider.getMaximum() - slider.getMinimum())));
                    fc.setUndoableState(currentValue);
                    endEdit();
                }
            }
            );
            slider.addChangeListener((ChangeEvent e) -> {
                currentValue = minValue + ((maxValue - minValue) * ((float) slider.getValue() / (slider.getMaximum() - slider.getMinimum())));
                fc.setGuiState(currentValue);
            });
        }

        @Override
        void setGuiState(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                slider.setValue(b);
                fc.setUndoableState(b);
            } else if (o instanceof Float) {
                float f = (Float) o;
                int sv = Math.round(((f - minValue) / (maxValue - minValue)) * (slider.getMaximum() - slider.getMinimum()));
                slider.setValue(sv);
            }
        }
    }

    class IntControl extends MyControl {

        EventFilter filter;
        int initValue = 0, nval;
        final JTextField tf;
        final JLabel label;
        String PROPERTY_VALUE = "value";
        boolean signed = false;

        public IntControl(final String name, final PropertyDescriptor p) {
            super(name, p);
            signed = isSigned(write);

            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            setFontSizeStyle(label);
            addTip(getFilter(), label);
            add(label);

            tf = new JTextField("", 8);
            tf.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.EMPTY_SET);
            // see http://blog.marcnuri.com/blog/defaul...e-cellRenderer/2007/06/06/Detecting-Tab-Key-Pressed-Event-in-JTextField-s-Event-VK-TAB-KeyPressed
            // otherwise TAB will just pass over field without entering a new value
            tf.setMaximumSize(new Dimension(100, 50));
            tf.setToolTipText("Integer control: use arrow keys or mouse wheel to change value by factor. Shift constrains to simple inc/dec");
            try {
                Integer x = (Integer) read.invoke(getFilter()); // read int value
                if (x == null) {
                    log.warning("null Integer returned from read method " + read);
                    return;
                }
                initValue = x.intValue();
                setCurrentState(initValue);
                setGuiState(initValue);
            } catch (Exception e) {
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    highlightClearingOthers();
                    Integer newValue = null;
                    try {
                        NumberFormat format = NumberFormat.getNumberInstance();
                        Integer oldValue = null;
                        try {
                            oldValue = (Integer) read.invoke(getFilter());
                        } catch (Exception re) {
                            log.warning("could not read original value: " + re.toString());
                        }
                        int y = format.parse(tf.getText()).intValue();
                        newValue = y;
                        setUndoableState(newValue); // write int value
                        firePropertyChange(PROPERTY_VALUE, oldValue, newValue);
                    } catch (ParseException | NumberFormatException pe) {
                        tf.selectAll();
                        tf.setBackground(Color.red);
                        Toolkit.getDefaultToolkit().beep();
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {

                @Override
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    highlightClearingOthers();
                    Integer newValue = null;
                    Integer oldValue = null;

                    try {
                        oldValue = (Integer) read.invoke(getFilter());
                        initValue = oldValue;
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code = evt.getKeyCode();
                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code == KeyEvent.VK_UP) {
                            nval = initValue;
                            if (nval == 0) {
                                nval = 1;
                            } else {
                                nval = Math.round(initValue * KEY_FACTOR);
                            }
                            if (nval == initValue) {
                                nval++;
                            }

                            setUndoableState(nval);
                        } else if (code == KeyEvent.VK_DOWN) {
                            nval = initValue;
                            if (nval == 0) {
                                nval = 0;
                            } else {
                                nval = Math.round(initValue / KEY_FACTOR);
                            }

                            setUndoableState(nval);
                        }
                    } else // shifted int control just incs or decs by 1
                    {
                        if (code == KeyEvent.VK_UP) {
                            nval = initValue + 1;
                            setUndoableState(nval);
                        } else if (code == KeyEvent.VK_DOWN) {
                            nval = initValue - 1;
                            setUndoableState(nval);
                        }
                    }
                    if (evt.getKeyCode() == KeyEvent.VK_TAB) {// setUndoableState this value, go to next component
                        try {
                            NumberFormat format = NumberFormat.getNumberInstance();
                            int y = format.parse(tf.getText()).intValue();
                            setUndoableState(y);
                        } catch (ParseException | NumberFormatException pe) {
                            tf.selectAll();
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
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    highlightClearingOthers();
                    Integer oldValue = null, newValue = null;
                    try {
                        oldValue = (Integer) read.invoke(getFilter());
                        initValue = oldValue;
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code = evt.getWheelRotation();

                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code < 0) { // wheel up, increase value
                            nval = initValue;
                            if (Math.round(initValue * WHEEL_FACTOR) == initValue) {
                                nval++;
                            } else {
                                nval = Math.round(initValue * WHEEL_FACTOR);
                            }
                            setUndoableState(nval);
                        } else if (code > 0) { // wheel down, decrease value
                            nval = initValue;
                            if (Math.round(initValue / WHEEL_FACTOR) == initValue) {
                                nval--;
                            } else {
                                nval = Math.round(initValue / WHEEL_FACTOR);
                            }
                            setUndoableState(nval);
                        }
                    }
                    firePropertyChange(PROPERTY_VALUE, oldValue, newValue);
                }
            }
            );
            tf.addFocusListener(new FocusListener() {

                @Override
                public void focusGained(FocusEvent e) {
                    tf.setSelectionStart(0);
                    tf.setSelectionEnd(tf.getText().length());
                }

                @Override
                public void focusLost(FocusEvent e) {
                }
            }
            );
            setMaximumSize(getPreferredSize());
        }

        @Override
        void setGuiState(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                String s = NumberFormat.getIntegerInstance().format(b);
                tf.setText(s);
            } else if (o instanceof String) {
                try {
                    Integer i = Integer.parseInt((String) o);
                    tf.setText((String) o);
                } catch (NumberFormatException e) {
                    log.warning(String.format("could not parse value %s", o));
                }
            }
            tf.setBackground(Color.white);
            if (tf.hasFocus()) {
                tf.setFont(tf.getFont().deriveFont(Font.BOLD | Font.ITALIC));
                label.setFont(label.getFont().deriveFont(Font.BOLD | Font.ITALIC));
            }
        }
    }

    void fixIntValue(JTextField tf, Method r) {
        // setUndoableState text to actual value
        try {
            Integer x = (Integer) r.invoke(getFilter()); // read int value
//            initValue=x.intValue();
            String s = NumberFormat.getIntegerInstance().format(x);
            tf.setText(s);
        } catch (Exception e) {
            e.printStackTrace();

        }

    }

    /**
     * Annotation to indicate some number property is signed
     *
     * @param m a method
     * @return true if annotated as signed
     */
    boolean isSigned(Method m) {
        if (m.isAnnotationPresent(SignedNumber.class)) {
            return true;
        } else {
            return false;
        }
    }

    class FloatControl extends MyControl {

        EngineeringFormat engFmt = new EngineeringFormat();
//        final String format="%.6f";

        EventFilter filter;
        float initValue = 0, nval;
        final JTextField tf;
        final JLabel label;
        boolean signed = false;

        public FloatControl(final String name, final PropertyDescriptor p) {
            super(name, p);
            signed = isSigned(write);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            setFontSizeStyle(label);
            addTip(getFilter(), label);
            add(label);
            tf = new JTextField("", 10);
            tf.setMaximumSize(new Dimension(100, 50));
            tf.setToolTipText("Float control: use arrow keys or mouse wheel to change value by factor. Shift reduces factor.");
            try {
                Float x = (Float) read.invoke(getFilter());
                if (x == null) {
                    log.warning("null Float returned from read method " + read);
                    return;
                }
                initValue = x;
                setCurrentState(initValue);
                setGuiState(initValue);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                log.warning("cannot access the field named " + name + " is the class or method not public?");
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener() {  // on enter

                @Override
                public void actionPerformed(ActionEvent e) {
                    highlightClearingOthers();
                    try {
                        float y = engFmt.parseFloat(tf.getText());
                        setUndoableState(y);
                    } catch (NumberFormatException fe) {
                        tf.selectAll();
                        tf.setBackground(Color.red);
                    }
                }
            });
            tf.addKeyListener(new java.awt.event.KeyAdapter() {

                @Override
                public void keyPressed(java.awt.event.KeyEvent evt) {
                    highlightClearingOthers();
                    try {
                        Float x = (Float) read.invoke(getFilter()); // getString the value from the getter method
                        initValue = x.floatValue();
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
                        nval = initValue;
                        if (nval == 0) {
                            nval = DEFAULT_REAL_VALUE;
                        } else {
                            nval = (initValue * floatFactor);
                        }
                        setUndoableState(nval);
                    } else if (code == KeyEvent.VK_DOWN) {
                        try {
                            nval = initValue;
                            if (nval == 0) {
                                nval = DEFAULT_REAL_VALUE;
                            } else {
                                nval = (initValue / floatFactor);
                            }
                            write.invoke(getFilter(), initValue / floatFactor);
                            Float x = (Float) read.invoke(getFilter()); // getString the value from the getter method to constrain it
                            nval = x.floatValue();
                            setUndoableState(nval);
                        } catch (InvocationTargetException | IllegalAccessException ite) {
                            ite.printStackTrace();
                        }
                    } else if (code == KeyEvent.VK_MINUS) { // negate the number
//                        try {
//                            nval = initValue;
//                            w.invoke(getFilter(), new Float(-initValue));
//                            Float x = (Float) r.invoke(getFilter()); // getString the value from the getter method to constrain it
//                            nval = x.floatValue();
//                            tf.setText(engFmt.format(nval));
//                        } catch (InvocationTargetException ite) {
//                            ite.printStackTrace();
//                        } catch (IllegalAccessException iae) {
//                            iae.printStackTrace();
//                        }

                    }
                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {

                @Override
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    highlightClearingOthers();
                    try {
                        Float x = (Float) read.invoke(getFilter()); // getString the value from the getter method
                        initValue = x.floatValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException | IllegalAccessException e) {
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
                                write.invoke(getFilter(), nval); // setter the value
                                Float x = (Float) read.invoke(getFilter()); // getString the value from the getter method to constrain it
                                nval = x.floatValue();
                                setUndoableState(nval); // write int value
                            } catch (InvocationTargetException | IllegalAccessException ite) {
                                ite.printStackTrace();
                            }
                        } else if (code > 0) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = DEFAULT_REAL_VALUE;
                                } else {
                                    nval = (initValue / WHEEL_FACTOR);
                                }
                                write.invoke(getFilter(), initValue / WHEEL_FACTOR);
                                Float x = (Float) read.invoke(getFilter()); // getString the value from the getter method to constrain it
                                nval = x.floatValue();
                                setUndoableState(nval);
                            } catch (InvocationTargetException | IllegalAccessException ite) {
                                ite.printStackTrace();
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

        @Override
        void setGuiState(Object o) {
            if (o instanceof Float) {
                Float b = (Float) o;
                tf.setText(engFmt.format(b));
            } else if (o instanceof Integer) {
                int b = (Integer) o;
                tf.setText(engFmt.format((float) b));
            } else if (o instanceof String) {
                try {
                    Float v = Float.parseFloat((String) o);
                    tf.setText((String) o);
                } catch (NumberFormatException e) {
                    log.warning(String.format("could not parse value %s", o));
                }
            }
            tf.setBackground(Color.white);
            if (tf.hasFocus()) {
                tf.setFont(tf.getFont().deriveFont(Font.BOLD | Font.ITALIC));
                label.setFont(label.getFont().deriveFont(Font.BOLD | Font.ITALIC));
            }
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
                    if ((b instanceof JToggleButton) && b.getText().equals(propertyChangeEvent.getPropertyName().substring(11))) {
                        b.setSelected(false);
                    }
                }
            } else {
                // we need to find the control and setUndoableState it appropriately. we don't need to setUndoableState the property itself since this has already been done!
                try {
//                    log.info("PropertyChangeEvent received from " +
//                            propertyChangeEvent.getSource() + " for property=" +
//                            propertyChangeEvent.getPropertyName() +
//                            " newValue=" + propertyChangeEvent.getNewValue());
                    final MyControl setter = setterMap.get(getFilter().getClass().getSimpleName() + "." + propertyChangeEvent.getPropertyName());
                    if (setter == null) {
                        if (!printedSetterWarning) {
                            log.warning("in filter " + getFilter() + " there is no setter for property change from property named " + propertyChangeEvent.getPropertyName());
                            printedSetterWarning = true;
                        }
                    } else {
//                        log.info("setting "+setter.toString()+" to "+propertyChangeEvent.getNewValue());
                        if (SwingUtilities.isEventDispatchThread()) {
                            setter.setUndoableState(propertyChangeEvent.getNewValue());
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    setter.setUndoableState(propertyChangeEvent.getNewValue());
                                } catch (Exception e) {
                                    log.log(Level.WARNING, "caught exception {0} in property change:{1}", new Object[]{propertyChangeEvent.getNewValue(), e.toString()});
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

        jPanel1 = new javax.swing.JPanel();
        enableResetControlsHelpPanel = new javax.swing.JPanel();
        enabledCheckBox = new javax.swing.JCheckBox();
        resetButton = new javax.swing.JButton();
        showControlsToggleButton = new javax.swing.JToggleButton();
        copyPasteDefaultsPanel = new javax.swing.JPanel();
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(30, 32767));
        copyB = new javax.swing.JButton();
        pasteB = new javax.swing.JButton();
        defaultsB = new javax.swing.JButton();
        exportImportPanel = new javax.swing.JPanel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(30, 32767));
        exportB = new javax.swing.JButton();
        importB = new javax.swing.JButton();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        jPanel1.setAlignmentX(0.0F);
        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.X_AXIS));

        enableResetControlsHelpPanel.setToolTipText("General controls for this EventFilter");
        enableResetControlsHelpPanel.setAlignmentX(0.0F);
        enableResetControlsHelpPanel.setLayout(new javax.swing.BoxLayout(enableResetControlsHelpPanel, javax.swing.BoxLayout.X_AXIS));

        enabledCheckBox.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        enabledCheckBox.setToolTipText("Enable or disable the filter");
        enabledCheckBox.setMargin(new java.awt.Insets(1, 1, 1, 1));
        enabledCheckBox.addActionListener(new java.awt.event.ActionListener() {
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
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetButtonActionPerformed(evt);
            }
        });
        enableResetControlsHelpPanel.add(resetButton);

        showControlsToggleButton.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        showControlsToggleButton.setText("Controls");
        showControlsToggleButton.setToolTipText("Show filter parameters, hides other filters. Click again to see all filters.");
        showControlsToggleButton.setMargin(new java.awt.Insets(1, 5, 1, 5));
        showControlsToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showControlsToggleButtonActionPerformed(evt);
            }
        });
        enableResetControlsHelpPanel.add(showControlsToggleButton);

        jPanel1.add(enableResetControlsHelpPanel);

        copyPasteDefaultsPanel.setMaximumSize(new java.awt.Dimension(129, 17));
        copyPasteDefaultsPanel.setMinimumSize(new java.awt.Dimension(129, 17));
        copyPasteDefaultsPanel.setPreferredSize(new java.awt.Dimension(98, 17));
        copyPasteDefaultsPanel.setLayout(new javax.swing.BoxLayout(copyPasteDefaultsPanel, javax.swing.BoxLayout.X_AXIS));
        copyPasteDefaultsPanel.add(filler2);

        copyB.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        copyB.setText("Copy");
        copyB.setToolTipText("Export the preferences for this filter to an XML preferences file");
        copyB.setAlignmentX(0.5F);
        copyB.setMargin(new java.awt.Insets(1, 5, 1, 5));
        copyB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyBActionPerformed(evt);
            }
        });
        copyPasteDefaultsPanel.add(copyB);

        pasteB.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        pasteB.setText("Paste");
        pasteB.setToolTipText("Import the preferences for this filter from an XML preferences file");
        pasteB.setAlignmentX(0.5F);
        pasteB.setMargin(new java.awt.Insets(1, 5, 1, 5));
        pasteB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteBActionPerformed(evt);
            }
        });
        copyPasteDefaultsPanel.add(pasteB);

        defaultsB.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        defaultsB.setText("Defaults");
        defaultsB.setToolTipText("Set all properties back to default values");
        defaultsB.setAlignmentX(0.5F);
        defaultsB.setMargin(new java.awt.Insets(1, 5, 1, 5));
        defaultsB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultsBActionPerformed(evt);
            }
        });
        copyPasteDefaultsPanel.add(defaultsB);

        jPanel1.add(copyPasteDefaultsPanel);

        exportImportPanel.setMaximumSize(new java.awt.Dimension(98, 17));
        exportImportPanel.setMinimumSize(new java.awt.Dimension(98, 17));
        exportImportPanel.setPreferredSize(new java.awt.Dimension(98, 17));
        exportImportPanel.setLayout(new javax.swing.BoxLayout(exportImportPanel, javax.swing.BoxLayout.X_AXIS));
        exportImportPanel.add(filler1);

        exportB.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        exportB.setText("Export");
        exportB.setToolTipText("Export the preferences for this filter to an XML preferences file");
        exportB.setAlignmentX(0.5F);
        exportB.setMargin(new java.awt.Insets(1, 5, 1, 5));
        exportB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportBActionPerformed(evt);
            }
        });
        exportImportPanel.add(exportB);

        importB.setFont(new java.awt.Font("Tahoma", 0, 9)); // NOI18N
        importB.setText("Import");
        importB.setToolTipText("Import the preferences for this filter from an XML preferences file");
        importB.setAlignmentX(0.5F);
        importB.setMargin(new java.awt.Insets(1, 5, 1, 5));
        importB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importBActionPerformed(evt);
            }
        });
        exportImportPanel.add(importB);

        jPanel1.add(exportImportPanel);

        add(jPanel1);
    }// </editor-fold>//GEN-END:initComponents

    public boolean isControlsVisible() {
        return getFilter().isControlsVisible();
    }

    /**
     * Set visibility of individual filter controls; hides other filters.
     *
     * @param visible true to show filter parameter controls, false to hide this
     * filter's controls and to show all filters in chain.
     */
    public void setControlsVisible(boolean visible) {
        getFilter().controlsVisible=visible;
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
                for (FilterPanel fp : ff.filterPanels ) {
                    if (fp == this) {  // for us and if !visible
                        fp.setVisible(true); // always set us as visible in chain since we are the one being touched
                        continue;
                    }

                    fp.setVisible(!visible); // hide / show other filters
                }

            }
        }
        
        // handle enclosed filters that are disabled and have parent enclosing filter that does not want to show them in GUI
        if(getFilter().isEnclosed() && !getFilter().isFilterEnabled() && getFilter().getEnclosingFilter().isHideNonEnabledEnclosedFilters()){
            setVisible(false);
        }else{
            setVisible(true);
        }

        if (additionalCustomControlsPanel != null) {
            additionalCustomControlsPanel.setVisible(visible);
        }

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
        if (getFilterFrame() != null) {
            getFilterFrame().updateHighlightedAndSimpleVisibilites();
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

    final public EventFilter getFilter() {
        return filter;
    }

    final public void setFilter(EventFilter filter) {
        this.filter = filter;
    }

    /**
     * @return the filterFrame
     */
    final public FilterFrame getFilterFrame() {
        return filterFrame;
    }

    /**
     * @param filterFrame the filterFrame to setUndoableState
     */
    final public void setFilterFrame(FilterFrame filterFrame) {
        this.filterFrame = filterFrame;
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
        // getFilter().setSelected(yes); // filter is only "selected" when the controls are made visible, so that mouse listeners are only active when the panel controls are visible
    }//GEN-LAST:event_enabledCheckBoxActionPerformed

    private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetButtonActionPerformed
        if (getFilter() != null) {
            getFilter().resetFilter();
        }
        getFilter().setSelected(true);
    }//GEN-LAST:event_resetButtonActionPerformed

    private void showControlsToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showControlsToggleButtonActionPerformed
        setControlsVisible(showControlsToggleButton.isSelected());
    }//GEN-LAST:event_showControlsToggleButtonActionPerformed

    private void exportBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportBActionPerformed
        exportPrefsDialog();
    }//GEN-LAST:event_exportBActionPerformed

    private void importBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importBActionPerformed
        importPrefsDialog();
    }//GEN-LAST:event_importBActionPerformed


    private void copyBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyBActionPerformed
        try {
            EventFilter.CopiedProps copiedProps = filter.copyProperties();
            getFilterFrame().setCopiedProps(copiedProps);
            filter.showPlainMessageDialogInSwingThread(String.format("Copied %s", copiedProps), "Copy succeeded");
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException ex) {
            Logger.getLogger(FilterPanel.class.getName()).log(Level.SEVERE, null, ex);
            filter.showWarningDialogInSwingThread(String.format("Error copying: %s", ex.toString()), "Error");
        }
    }//GEN-LAST:event_copyBActionPerformed

    private void pasteBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteBActionPerformed
        EventFilter.CopiedProps copiedProps = getFilterFrame().getCopiedProps();
        if (copiedProps == null || copiedProps.isEmpty()) {
            log.warning("no copied properties to paste");
            filter.showWarningDialogInSwingThread("No properties to paste", "Error");
            return;
        }
        try {
            filter.pasteProperties(copiedProps);
            getFilterFrame().rebuildPanel(this);
            filter.showPlainMessageDialogInSwingThread(String.format("Pasted %d properties", copiedProps.properties.size()), "Paste suceeded");

        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException | ClassCastException ex) {
            Logger.getLogger(FilterPanel.class.getName()).log(Level.SEVERE, null, ex);
            filter.showWarningDialogInSwingThread(String.format("Error pasting: %s", ex.toString()), "Error");
        }
    }//GEN-LAST:event_pasteBActionPerformed

    private void defaultsBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultsBActionPerformed
        HashMap<String, EventFilter.PrefsKeyClassValueDefault> nonDefaultProps = getFilter().getNonDefaultProperties();
        int count = nonDefaultProps.size();
        if (count > 0) {
            StringBuilder sb;
            if (count > MAX_PROPS_TO_SHOW_RESTORE_DEFAULTS) {
                sb = new StringBuilder("");
            } else {
                sb = new StringBuilder("<ul>");
                for (String prop : nonDefaultProps.keySet()) {
                    sb.append("<li>").append(prop.substring(prop.indexOf(".") + 1));
                }
                sb.append("</ul>");
            }
            final String msg = String.format("<html>Really reset %d propertie(s)  %s to default values?<p>You can Export first to save them.", count, sb.toString());
            int ret = JOptionPane.showConfirmDialog(defaultsB, msg, "Restore defaults?", JOptionPane.YES_NO_OPTION);
            switch (ret) {
                case JOptionPane.YES_OPTION:
                    restoreDefaultPropertyValues();
                    break;
            }
        } else {
            JOptionPane.showMessageDialog(defaultsB, "No properties have been modified from default values");
        }
    }//GEN-LAST:event_defaultsBActionPerformed
    private static final int MAX_PROPS_TO_SHOW_RESTORE_DEFAULTS = 20;

    private void restoreDefaultPropertyValues() {
        HashMap<String, EventFilter.PrefsKeyClassValueDefault> clearedProperties = getFilter().restoreDefaultPreferences();
        for (String k : clearedProperties.keySet()) {
            MyControl control = propertyControlMap.get(k);
            if (control != null && (control.write != null)) {
                EventFilter.PrefsKeyClassValueDefault prefsValue = clearedProperties.get(k);
                try {
                    control.setUndoableState(prefsValue.defaultValue());
                    log.fine(String.format("restored %s to %s", k, prefsValue.defaultValue()));
                } catch (Exception e) {
                    log.warning(String.format("could not set %s to %s: %s", k, prefsValue, e.toString()));
                }
            }
        }
        getFilter().restoreDefaultPreferences(); // once more to remove keys from preferences so they are back to hard-coded defaults
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton copyB;
    private javax.swing.JPanel copyPasteDefaultsPanel;
    private javax.swing.JButton defaultsB;
    protected javax.swing.JPanel enableResetControlsHelpPanel;
    protected javax.swing.JCheckBox enabledCheckBox;
    private javax.swing.JButton exportB;
    private javax.swing.JPanel exportImportPanel;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JButton importB;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton pasteB;
    private javax.swing.JButton resetButton;
    private javax.swing.JToggleButton showControlsToggleButton;
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
                int min = (Integer) minMethod.invoke(getFilter());
                int max = (Integer) maxMethod.invoke(getFilter());
                params
                        = new SliderParams(Integer.class, min, max, 0, 0);
            } else if (p.getPropertyType() == Float.TYPE) {
                float min = (Float) minMethod.invoke(getFilter());
                float max = (Float) maxMethod.invoke(getFilter());
                params
                        = new SliderParams(Integer.class, 0, 0, min, max);
            }
        } catch (NoSuchMethodException e) {
        } catch (Exception iae) {
            log.warning(iae.toString() + " for property " + p + " in filter " + filter);
        }
        return params;

    }

    class Point2DControl extends MyControl {

        EventFilter filter;
        Point2D.Float point;
        float initValue = 0, nval;
        final JTextField tfx, tfy;
        final String format = "%.1f";
        final JButton nullifyButton;

        @Override
        void setGuiState(Object o) {
            if (o == null) {
                tfx.setText(null);
                tfy.setText(null);
            } else if (o instanceof Point2D) {
                Point2D b = (Point2D) o;
                tfx.setText(String.format(format, b.getX()));
                tfy.setText(String.format(format, b.getY()));
            } else if (o instanceof String) {
                log.log(Level.WARNING, "cannot set from String value {0}", o);
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
                highlightClearingOthers();
                try {
                    float x = Float.parseFloat(tfx.getText());
                    float y = Float.parseFloat(tfy.getText());
                    point.setLocation(x, y);
                    writeMethod.invoke(getFilter(), point); // maybe not needed with setUndoableState below
                    point = (Point2D) readMethod.invoke(getFilter()); // getString the value from the getter method to constrain it
                    setUndoableState(point);
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
                highlightClearingOthers();
                try {
                    Object arg = null;
                    writeMethod.invoke(getFilter(), arg);
                    setUndoableState(null);
                } catch (InvocationTargetException ite) {
                    ite.printStackTrace();
                } catch (IllegalAccessException iae) {
                    iae.printStackTrace();
                }
            }
        }

        public Point2DControl(final String name, PropertyDescriptor p) {
            super(name, p);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(LEFT_ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(getFilter(), label);
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
                Point2D pt = (Point2D) read.invoke(getFilter());
                setCurrentState(pt);
                setGuiState(pt);
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

            tfx.addActionListener(new PointActionListener(read, write));
            tfy.addActionListener(new PointActionListener(read, write));
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
    JPanel additionalCustomControlsPanel;

    public void addCustomControls(JPanel control) {
        if (additionalCustomControlsPanel == null) {
            additionalCustomControlsPanel = new JPanel();
            additionalCustomControlsPanel.setAlignmentY(TOP_ALIGNMENT);
            BoxLayout boxLayout = new BoxLayout(additionalCustomControlsPanel, BoxLayout.Y_AXIS);
            additionalCustomControlsPanel.setLayout(boxLayout);
            this.add(additionalCustomControlsPanel);
        }

        this.additionalCustomControlsPanel.add(control);
//        this.customControls.add(controls);

        setControlsVisible(true);
//        this.repaint();
//        this.revalidate();
    }

    public void removeCustomControls() {
//        for (JPanel p:customControls)
//            controls.remove(p);
        if (additionalCustomControlsPanel != null) {
            additionalCustomControlsPanel.removeAll();
            additionalCustomControlsPanel.repaint();
        }
//        customControls.clear();

    }

    /**
     * Returns true if the search string is blank and
     * isShowOnlyPreferredProperties is true and property is preferred or if
     * there is a match and !isShowOnlyPreferredProperties.
     *
     * @param propName
     * @return
     */
    private boolean isPropertyVisible(String propName) {
        if (getSearchString() == null || getSearchString().isBlank()) {
            if (!isSimple()) {
                return true;
            }
            return isPropertyPreferred(propName);
        }
        final boolean matches = propName.toLowerCase().contains(getSearchString());
        return matches;
    }

    /**
     * Highlights properties that match the string s, or only shows the ones
     * that match, depending on Hide others checkbox
     *
     * @param searchString the string to match for, lowercase matching
     * @param hideOthers to hide other properties
     * @param simple boolean to show only preferred properties
     */
    public void showPropertyHighlightsOrVisibility(String searchString, boolean hideOthers, boolean simple) {

        setSearchString(searchString);
        setHideOthers(hideOthers);
        setSimple(simple);
        if (searchString.isBlank()) { // just show everything that should be shown
            for (String propName : propertyControlMap.keySet()) {
                MyControl c = propertyControlMap.get(propName);
                if (c == null) {
                    continue;
                }
                c.setBorder(null);
                c.setVisible(isPropertyVisible(propName));
                c.invalidate();
            }
            for (Container c : groupContainerMap.values()) {
                c.setVisible(true);
                c.invalidate();
            }
        } else { // there is a search string, so setUndoableState each property to either highlightClearingOthers or show, hiding others

            highlightedControls.clear();
            // if hideOthers, hide all groups and later only show those that match
            for (Container c : groupContainerMap.values()) {
                c.setVisible(false);
            }

            for (String propName : propertyControlMap.keySet()) { // consider each property
                MyControl c = propertyControlMap.get(propName);
                if (isHideOthers()) {
                    if (isPropertyVisible(propName)) {
                        log.fine(String.format("Showing match: %s is in %s", searchString, propName));
                        highlightedControls.add(c);
                        c.setVisible(true);
                        setGroupContainerWithPropertyVisible(propName, true);
                    } else { // no match, then hide it
                        c.setVisible(false);
                    }
                } else { // highlightClearingOthers
                    setGroupContainerWithPropertyVisible(propName, false);
                    c.setVisible(true);
                    setGroupContainerWithPropertyVisible(propName, true);
                    if (isPropertyVisible(propName)) {
                        log.fine(String.format("Hightlighting Match: %s is in %s", searchString, propName));
                        c.setBorder(redLineBorder); // highlightClearingOthers it
                        highlightedControls.add(c);
                    } else { // no match, then hide it if hideOthers setUndoableState, otherwise show it
                        c.setBorder(null);
                    }
                }
                c.invalidate();
            }
        }
        for (Component c : groupContainerMap.values()) {
            c.invalidate();
        }
        // handle enclosed filters, but only filter them if the controls are visible
        for (EventFilter f : enclosedFilterPanels.keySet()) {
            if (f.isControlsVisible()) {
                FilterPanel p = getEnclosedFilterPanel(f);
                p.showPropertyHighlightsOrVisibility(searchString, hideOthers, simple);
            }
        }
        this.invalidate();
        revalidate();
        repaint();
    }

    /**
     * Returns true if property is @Preferred
     *
     * @param propName
     * @return false if propName==null or not preferred, else true
     */
    private boolean isPropertyPreferred(String propName) {
        if (propName == null) {
            return false;
        }
        return filter.tooltipSupport.isPropertyPreferred(propName);
    }

    private void setGroupContainerWithPropertyVisible(String propName, boolean visible) {
        Container groupContainer = getGroupPanel(propName);
        if (groupContainer != null) {
            groupContainer.setVisible(visible);
        }
    }

    /**
     * @return the simple
     */
    public boolean isSimple() {
        return simple;
    }

    /**
     * @return the hideOthers
     */
    public boolean isHideOthers() {
        return hideOthers;
    }

    /**
     * @param hideOthers the hideOthers to setUndoableState
     */
    public void setHideOthers(boolean hideOthers) {
        this.hideOthers = hideOthers;
    }

    /**
     * @return the searchString
     */
    public String getSearchString() {
        return searchString;
    }

    /**
     * @param searchString the searchString to setUndoableState
     */
    public void setSearchString(String searchString) {
        this.searchString = searchString == null ? "" : searchString.toLowerCase();
    }

    /**
     * @param showOnlyPreferredProperties the simple to setUndoableState
     */
    public void setSimple(boolean showOnlyPreferredProperties) {
        this.simple = showOnlyPreferredProperties;
    }

    /**
     * Show file dialog to save the preferences for this EventFilter
     *
     */
    void exportPrefsDialog() {
        JFileChooser fileChooser = new JFileChooser();
        String defaultFolder = getDefaultPreferencesFolder();

        String lastFilePath = filter.getPrefs().get("FilterFrame.lastFile", defaultFolder); // getString the last folder
        File lastFile = new File(lastFilePath);
        XMLFileFilter fileFilter = new XMLFileFilter();
        fileChooser.addChoosableFileFilter(fileFilter);
        fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        fileChooser.setDialogTitle("Save filter settings to");
        fileChooser.setMultiSelectionEnabled(false);
        //            if(lastImageFile==null){
        //                lastImageFile=new File("snapshot.png");
        //            }
        //            fileChooser.setSelectedFile(lastImageFile);
        int retValue = fileChooser.showSaveDialog(this);
        if (retValue == JFileChooser.APPROVE_OPTION) {
            filter.exportPrefs(fileChooser.getSelectedFile());
        }
    }

    /**
     * Returns the default filter settings folder (filterSettings)
     *
     * @return
     */
    String getDefaultPreferencesFolder() {
        String defaultFolder = System.getProperty("user.dir");
        try {
            File f = new File(defaultFolder + File.separator + "filterSettings");
            defaultFolder = f.getPath();
        } catch (Exception e) {
            log.warning("could not locate default folder for filter settings relative to starting folder, using startup folder");
        }
        return defaultFolder;
    }

    /**
     * Show dialog to import prefs for this filter
     */
    void importPrefsDialog() {
        JFileChooser fileChooser = new JFileChooser();
        String lastFilePath = prefs.get("FilterFrame.lastFile", getDefaultPreferencesFolder());
        File lastFile = new File(lastFilePath);
        if (!lastFile.exists()) {
            log.warning("last file for filter settings " + lastFile + " does not exist, using " + getDefaultPreferencesFolder());
            lastFile = new File(getDefaultPreferencesFolder());
        }

        XMLFileFilter fileFilter = new XMLFileFilter();
        fileChooser.addChoosableFileFilter(fileFilter);
        fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
        int retValue = fileChooser.showOpenDialog(this);
        if (retValue == JFileChooser.APPROVE_OPTION) {
            File f = fileChooser.getSelectedFile();
            prefs.put("FilterFrame.lastFile", f.getAbsolutePath());
            filter.importPrefs(f);
        }
        PreferencesMover.OldPrefsCheckResult result = PreferencesMover.hasOldChipFilterPreferences(filter);
        if (result.hasOldPrefs()) {
            log.warning(result.message());
            PreferencesMover.migrateEventFilterPrefsDialog(filter);
        } else {
            log.fine(result.message());
        }

    }

    class MyStateEdit extends StateEdit {

        public MyStateEdit(StateEditable o, String s) {
            super(o, s);
        }

        protected void removeRedundantState() { // override this to actually get a currentState stored
        }

        public String toString() {
            return String.format("StateEdit: object=%s, property=%s", this.object.getClass().getSimpleName(), this.undoRedoName);
        }
    }

}

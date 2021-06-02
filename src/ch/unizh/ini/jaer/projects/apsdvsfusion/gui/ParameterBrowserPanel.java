/*
 * FilterPanel.java
 *
 * Created on October 31, 2005, 8:13 PM
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.util.EngineeringFormat;
import ch.unizh.ini.jaer.projects.apsdvsfusion.ParameterContainer;


/**
 * A panel for a filter that has Integer/Float/Boolean/String/enum getter/setter methods (bound properties).
These methods are introspected and a set of controls are built for them. Enclosed filters and
filter chains have panels built for them that are enlosed inside the filter panel, hierarchically.
 * <ul>
 * <li>Numerical properties (ints, floats, but not currently doubles) construct a JTextBox control that also allows changes from mouse wheel or arrow keys.
 * <li> boolean properties construct a JCheckBox control.
 * <li> String properties construct a JTextField control.
 * <li> enum properties construct a JComboBox control, which all the possible enum constant values.
 * </ul>
 * <p>
 * If a filter wants to automatically have the GUI controls reflect what the property state is, then it should
 * fire PropertyChangeEvent when the property changes. For example, an {@link EventFilter} can implement a setter like this:
 * <pre>
public void setMapEventsToLearnedTopologyEnabled(boolean mapEventsToLearnedTopologyEnabled) {
support.firePropertyChange("mapEventsToLearnedTopologyEnabled", this.mapEventsToLearnedTopologyEnabled, mapEventsToLearnedTopologyEnabled); // property, old value, new value
this.mapEventsToLearnedTopologyEnabled = mapEventsToLearnedTopologyEnabled;
getPrefs().putBoolean("TopologyTracker.mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled);
}
</pre>
 * Here, <code>support</code> is a protected field of EventFilter. The change event comes here to FilterPanel and the appropriate automatically
 * generated control is modified.
 * <p>
 * Note that calling firePropertyChange as shown above will inform listeners <em>before</em> the property has actually been
 * changed (this.dt has not been set yet).
 * <p>
 * A tooltip for the property can be installed using the EventFilter setPropertyTooltip method, for example
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
 * the built in EventFilter method {@link net.sf.jaer.eventprocessing.EventFilter#addPropertyToGroup}. All properties of a given group are grouped together. Within
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
 * {@link net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String, java.lang.String, java.lang.String) setPropertyTooltip} of
 * {@link net.sf.jaer.eventprocessing.EventFilter},
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
 * @author  Tobi Dellbruck, heavily modified by Peter O`Connor, finetuned by Dennis Goehlsdorf
 * @see net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String, java.lang.String)
 *@see net.sf.jaer.eventprocessing.EventFilter#setPropertyTooltip(java.lang.String, java.lang.String, java.lang.String)
 * @see net.sf.jaer.eventprocessing.EventFilter
 */
public class ParameterBrowserPanel extends CollapsablePanel implements PropertyChangeListener {

    /**
	 *
	 */
	private static final long serialVersionUID = -8774445069061985174L;

	private interface HasSetter {

        void set(Object o);
    }
    public static final float ALIGNMENT = Component.LEFT_ALIGNMENT;
    private BeanInfo info;
    private PropertyDescriptor[] props;
    private Method[] methods;
    private static Logger log = Logger.getLogger("Filters");
    final float fontSize = 10f;
//    private Border normalBorder, redLineBorder;
//    private TitledBorder titledBorder;
    private HashMap<String, HasSetter> setterMap = new HashMap<String, HasSetter>(); // map from filter to property, to apply property change events to control
    protected java.util.ArrayList<JComponent> controls = new ArrayList<JComponent>();
    private HashMap<String, Container> groupContainerMap = new HashMap<String, Container>();
//    private JPanel inheritedPanel = null;
    private float DEFAULT_REAL_VALUE=0.01f; // value jumped to from zero on key or wheel up
    private boolean showNameField = true;
    ParameterContainer parameterContainer;

    /** Creates new form FilterPanel */
    public ParameterBrowserPanel(ParameterContainer topLevel, boolean showNameField) {
    	super(topLevel.getName());
    	this.showNameField = showNameField;
//        titledBorder = new TitledBorder("Network Controls");
//        titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
////        titledBorder.setBorder(BorderFactory.createLineBorder(Color.blue));
////        normalBorder = BorderFactory.createLineBorder(Color.blue);
//        redLineBorder = BorderFactory.createLineBorder(Color.blue);
//        setBorder(titledBorder);
        initComponents();

        addController(topLevel,getContentPanel());
    }

    public ParameterBrowserPanel(ParameterContainer topLevel) {
    	this(topLevel, true);
    }


//    public GeneralController() {
//        log.info("building FilterPanel for "+f);
//        this.setFilter(f);
//        initComponents();
//        String cn = getControllable().getClass().getName();
//        int lastdot = cn.lastIndexOf('.');
//        String name = cn.substring(lastdot + 1);
//        setName(name);
//        titledBorder = new TitledBorder(name);
//        titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
//        setBorder(titledBorder);
//        normalBorder = titledBorder.getBorder();
//        redLineBorder = BorderFactory.createLineBorder(Color.red);
////        enabledCheckBox.setSelected(getControllable().isFilterEnabled());
//        addIntrospectedControls();
//        // when filter fires a property change event, we getString called here and we update all our controls
//        getControllable().getPropertyChangeSupport().addPropertyChangeListener(this);
//        ToolTipManager.sharedInstance().setDismissDelay(10000); // to show tips
//        setToolTipText(f.getDescription());
//    }






    // checks for group container and adds to that if needed.
 // checks for group container and adds to that if needed.
//    private void myadd(JComponent comp, String propertyName, boolean inherited) {
//        JPanel pan = new JPanel();
//        pan.setLayout(new BoxLayout(pan, BoxLayout.X_AXIS));
//        controls.add(pan);
//
//        if (!getControllable().hasPropertyGroups()) {
//            pan.add(comp);
////        if(inherited){
////            pan.setBorder(BorderFactory.createLineBorder(Color.yellow) );
////        }
//            pan.add(Box.createVerticalStrut(0));
//            add(pan);
//            controls.add(comp);
//            return;
//        }
//        String groupName = getControllable().getPropertyGroup(propertyName);
//        if (groupName != null) {
//            Container container = groupContainerMap.get(groupName);
//            comp.setAlignmentX(Component.LEFT_ALIGNMENT);
////            if(inherited){
////                JPanel inherPan=new JPanel();
////                inherPan.setBorder(BorderFactory.createLineBorder(Color.yellow) );
////                inherPan.add(comp,BorderLayout.WEST);
////                container.add(inherPan);
////            }else{
//            container.add(comp);
////            }
//        } else {
////            add(Box.createHorizontalGlue());
//            comp.setAlignmentX(Component.LEFT_ALIGNMENT);
//            pan.add(comp);
//            pan.add(Box.createVerticalStrut(0));
//        }
//        add(pan); // to fix horizontal all left alignment
//        controls.add(comp);
//    }


    /** Adds a top-level control */
@SuppressWarnings("unchecked")
//    void addController(Controllable filter)
//    {
//        addController(filter,this);
//
//    }


    // gets getter/setter methods for the filter and makes controls for them. enclosed filters are also added as submenus
    void addController(final ParameterContainer parameterContainer,final JPanel topPanel) {
//        JPanel control = null;
//        NetController filter = getControllable();
        if (parameterContainer != null) {
			parameterContainer.getSupport().addPropertyChangeListener(this);
		}
		JPanel hostPanel = new JPanel();
		topPanel.setLayout(new BorderLayout());
		topPanel.add(new JLabel("   "),BorderLayout.WEST);
		topPanel.add(hostPanel,BorderLayout.CENTER);
//        hostPanel.setLayout(new javax.swing.BoxLayout(hostPanel, javax.swing.BoxLayout.Y_AXIS));
        hostPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbcHost = new GridBagConstraints();
        gbcHost.weightx = 1.0;
        gbcHost.weighty = 1.0;
        gbcHost.fill = GridBagConstraints.BOTH;
        gbcHost.gridy = 0;

        hostPanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.lightGray));


//        boolean topLevel=hostPanel==this;

//        if (topLevel) // Top-Level control
//        {   titledBorder = new TitledBorder(parameterContainer.getName());
//            titledBorder.getBorderInsets(this).set(1, 1, 1, 1);
//    //        titledBorder.setBorder(BorderFactory.createLineBorder(Color.blue));
//    //        normalBorder = BorderFactory.createLineBorder(Color.blue);
//            redLineBorder = BorderFactory.createLineBorder(Color.blue);
//            setBorder(titledBorder);
//
//        }

        setParameterContainer(parameterContainer);
        try {
            info = Introspector.getBeanInfo(parameterContainer.getClass());
            // TODO check if class is public, otherwise we can't access methods usually
            props = info.getPropertyDescriptors();
            methods = parameterContainer.getClass().getMethods();


            JPanel controla=new JPanel();

//            if (controla==null)
//                controla =

            int numDoButtons = 0;
            // first add buttons when the method name starts with "do". These methods are by convention associated with actions.
            // these methods, e.g. "void doDisableServo()" do an action.
            Insets butInsets = new Insets(0, 0, 0, 0);
            for (Method m : methods) {
                if (m.getName().startsWith("do")
                        && (m.getParameterTypes().length == 0)
                        && (m.getReturnType() == void.class)) {
                    numDoButtons++;
                    JButton button = new JButton(m.getName().substring(2).replace('_', ' '));
                    button.setMargin(butInsets);
                    button.setFont(button.getFont().deriveFont(9f));
                    final ParameterContainer f = parameterContainer;
                    final Method meth = m;
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
                    controla.add(button);
                }
            }

            //if at least one button then we show the actions panel
            if (controla.getComponentCount() > 0) {
//                TitledBorder tb = new TitledBorder("Actions");
//                tb.getBorderInsets(this).set(1, 1, 1, 1);
//                control.setBorder(tb);
//                control.setMinimumSize(new Dimension(0, 0));
                hostPanel.add(controla, gbcHost);
                gbcHost.gridy++;
                controls.add(controla);
            }

            if (numDoButtons > 3) {
                controla.setLayout(new GridLayout(0, 3, 3, 3));
            }


            // next add enclosed Filter and enclosed FilterChain so they appear at top of list (they are processed first)
//            for (PropertyDescriptor p : props) {
//                Class c = p.getPropertyType();
//                if (p.getName().equals("enclosedFilter")) { //if(c==EventFilter2D.class){
//                    // if type of property is an EventFilter, check if it has either an enclosed filter
//                    // or an enclosed filter chain. If so, construct FilterPanels for each of them.
//                    try {
//                        Method r = p.getReadMethod(); // getString the getter for the enclosed filter
//                        NetController enclFilter = (EventFilter2D) (r.invoke(getControllable()));
//                        if (enclFilter != null) {
////                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses EventFilter2D "+enclFilter.getClass().getSimpleName());
//                            GeneralController enclPanel = new GeneralController(enclFilter);
//                            this.add(enclPanel);
//                            controls.add(enclPanel);
//                            ((TitledBorder) enclPanel.getBorder()).setTitle("enclosed: " + enclFilter.getClass().getSimpleName());
//                        }
////                        FilterChain chain=getFilter().getEnclosedFilterChain();
////                        if(chain!=null){
////                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses filterChain "+chain);
////                            for(EventFilter f:chain){
////                                FilterPanel enclPanel=new FilterPanel(f);
////                                this.add(enclPanel);
////                                controls.add(enclPanel);
////                                ((TitledBorder)enclPanel.getBorder()).setTitle("enclosed: "+f.getClass().getSimpleName());
////                            }
////                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                } else if (p.getName().equals("enclosedFilterChain")) { //
//                    // if type of property is a FilterChain, check if it has either an enclosed filter
//                    // or an enclosed filter chain. If so, construct FilterPanels for each of them.
//                    try {
//                        Method r = p.getReadMethod(); // getString the getter for the enclosed filter chain
//                        FilterChain chain = (FilterChain) (r.invoke(getControllable()));
//                        if (chain != null) {
////                            log.info("EventFilter "+filter.getClass().getSimpleName()+" encloses filterChain "+chain);
//                            for (EventFilter f : chain) {
//                                GeneralController enclPanel = new GeneralController(f);
//                                this.add(enclPanel);
//                                controls.add(enclPanel);
//                                ((TitledBorder) enclPanel.getBorder()).setTitle("enclosed: " + f.getClass().getSimpleName());
//                            }
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    String name = p.getName();
//                    if (control != null) {
//                        control.setToolTipText(getControllable().getPropertyTooltip(name));
//                    }
//                }
//            }

            // next add all other properties that we can handle
            // these must be saved and then sorted in case there are property groups defined.

            final JPanel groupPanel;
            //            if (topLevel)
            //            {   groupPanel=this;
            //            }
            //            else

            String pcName = parameterContainer.getName();
            groupPanel = new JPanel();
            //            groupPanel.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            //                groupPanel.setLayout(new BoxLayout(groupPanel,BoxLayout.Y_AXIS));
            groupPanel.setLayout(new GridBagLayout());
            groupPanel.setName(pcName);
            //                groupPanel.setBorder(new TitledBorder(s));
            //            groupPanel.setLayout(new GridLayout(0, 1));
            groupContainerMap.put(pcName, groupPanel);

            GridBagConstraints gbcGroup = new GridBagConstraints();
            gbcGroup.weightx = 1.0;
            gbcGroup.weighty = 1.0;
            gbcGroup.fill = GridBagConstraints.BOTH;
            gbcGroup.gridy = 0;
// TODO: Watch these lines!
//            groupPanel.add(controla, gbcGroup);
//            gbcGroup.gridy++;

            if (getParameterContainer().hasPropertyGroups()) {
                Set<String> groupSet = getParameterContainer().getPropertyGroupSet();
                for (String s : groupSet) {
                    JPanel singleGroupPanel = new JPanel();
                    singleGroupPanel.setName(s);
                    singleGroupPanel.setBorder(new TitledBorder(s));
                    singleGroupPanel.setLayout(new GridLayout(0, 1));
                    groupContainerMap.put(s, singleGroupPanel);
                    add(singleGroupPanel);
                    controls.add(singleGroupPanel); // visibility list
                }
            }

//            ArrayList<Component> sortedControls=new ArrayList();
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
                JPanel control=null;
                try {



//                    boolean inherited = false;

                    // TODO handle indexed properties
                    Class<?> c = p.getPropertyType();
                    String name = p.getName();

//                    // check if method comes from a superclass of this EventFilter
//                    if (control != null && p.getReadMethod() != null && p.getWriteMethod() != null) {
//                        Method m = p.getReadMethod();
//                        if (m.getDeclaringClass() != getControllable().getClass()) {
//                            inherited = true;
//                        }
//                    }
                    if (!getParameterContainer().isPropertyExcluded(name)) {

	                    if ((c == Integer.TYPE) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {

	                        SliderParams params;
	                        if ((params = isSliderType(p, parameterContainer)) != null) {
	                            control = new IntSliderControl(getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod(), params);
	                        } else {
	                            control = new IntControl(getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod());
	                        }
	//                        myadd(control, name, inherited);
	                    } else if ((c == Float.TYPE) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
	                        SliderParams params;
	                        if ((params = isSliderType(p, parameterContainer)) != null) {
	                            control = new FloatSliderControl(getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod(), params);
	                        } else {
	                            control = new FloatControl(getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod());

	                        }
	//                        myadd(control, name, inherited);
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

	                        control = new BooleanControl(getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod());
	//                        myadd(control, name, inherited);
	                    } else if ((c == String.class) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
	//                        if (p.getName().equals("filterEnabled")) {
	//                            continue;
	//                        }
	//                        if (p.getName().equals("annotationEnabled")) {
	//                            continue;
	//                        }
	                    	if (showNameField || !p.getName().equals("name"))
							 {
								control = new StringControl(getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod());
	//                        myadd(control, name, inherited);
							}
	                    } else if ((c != null) && c.isEnum() && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
	                        control = new EnumControl((Class<? extends Enum<?>>)c, getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod());
	//                        myadd(control, name, inherited);
	                    }else if ((c != null) && (c==Point2D.Float.class) && (p.getReadMethod() != null) && (p.getWriteMethod() != null)) {
	                        control = new Point2DControl(getParameterContainer(), p.getName(), p.getWriteMethod(), p.getReadMethod());
	//                        myadd(control, name, inherited);
	                    } else {
	//                    log.warning("unknown property type "+p.getPropertyType()+" for property "+p.getName());
	                    }
	                    if (control != null) {
	                        control.setToolTipText(getParameterContainer().getPropertyTooltip(name));
	                    }
                    }
                } catch (Exception e) {
                    log.warning(e + " caught on property " + p.getName() + " from EventFilter " + parameterContainer);
                }
                if (control!=null) {
                    groupPanel.add(control, gbcGroup);
                    gbcGroup.gridy++;
                }
                if (gbcGroup.gridy > 0)
				 {
					controls.add(groupPanel); // visibility list
				}

            }


//            final ArrayList<JPanel> subcontrols=new ArrayList();
//            ActionListener subrebuilder=new ActionListener(){
//
//                @Override
//                public void actionPerformed(ActionEvent e) {
//
//                    for(JPanel s:subcontrols)
//                        groupPanel.remove(s);
//
//                    for (Controllable c:controllable.getSubControllers())
//                    {
//                        JPanel jp=new JPanel();
//                        groupPanel.add(jp);
//                        addController(c,jp);
//                        subcontrols.add(jp);
//                    }
//
//
//                }
//
//
//            };
//
//
//            controllable.addActionListener(subrebuilder);
//
//            subrebuilder.actionPerformed(null);

//            filter.updateControl();

            if (gbcGroup.gridy > 0) {
	            hostPanel.add(groupPanel, gbcHost);
	            gbcHost.gridy++;
            }

            JComponent custom = getParameterContainer().getCustomControls();
            if (custom != null) {
            	hostPanel.add(custom, gbcHost);
            	gbcHost.gridy++;
            }

//            groupContainerMap = null;
//             sortedControls=null;
        } catch (Exception e) {
            log.warning("on adding controls for EventFilter " + parameterContainer + " caught " + e);
            e.printStackTrace();
        }


//        add(Box.createHorizontalGlue());

        setControlsVisible(true);
//        System.out.println("added glue to "+this);
    }

    void addTip(ParameterContainer f, JLabel label) {
        String s = f.getPropertyTooltip(label.getText());
        if (s == null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);
    }

    void addTip(ParameterContainer f, JButton b) {
        String s = f.getPropertyTooltip(b.getText());
        if (s == null) {
            return;
        }
        b.setToolTipText(s);
        b.setForeground(Color.BLUE);
    }

    void addTip(ParameterContainer f, JCheckBox label) {
        String s = f.getPropertyTooltip(label.getText());
        if (s == null) {
            return;
        }
        label.setToolTipText(s);
        label.setForeground(Color.BLUE);
    }

    class EnumControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = -5669224963030440630L;
		Method write, read;
        ParameterContainer filter;
        boolean initValue = false, nval;
        final JComboBox control;

        @Override
		public void set(Object o) {
            control.setSelectedItem(o);
        }

        public EnumControl(final Class<? extends Enum<?>> c, final ParameterContainer f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            control = new JComboBox(c.getEnumConstants());
            control.setFont(control.getFont().deriveFont(fontSize));
//            control.setHorizontalAlignment(SwingConstants.LEADING);

            add(label);
            add(control);
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

    class StringControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = 4136508027569378649L;
		Method write, read;
        ParameterContainer filter;
        boolean initValue = false, nval;
        final JTextField textField;

        @Override
		public void set(Object o) {
            if (o instanceof String) {
                String b = (String) o;
                textField.setText(b);
            }
        }

        public StringControl(final ParameterContainer f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
            final JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            textField = new JTextField(name);
            textField.setFont(textField.getFont().deriveFont(fontSize));
            textField.setHorizontalAlignment(SwingConstants.LEADING);
            textField.setColumns(10);

            add(label);
            add(textField);
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
            textField.addFocusListener(new FocusListener() {
				@Override
				public void focusLost(FocusEvent arg0) {
                    try {
                        w.invoke(filter, textField.getText());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
				}

				@Override
				public void focusGained(FocusEvent arg0) {
				}
			});
        }
    }
    final float factor = 1.51f, wheelFactor = 1.05f; // factors to change by with arrow and mouse wheel

    class BooleanControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = 3914986042058679017L;
		Method write, read;
        ParameterContainer filter;
        boolean initValue = false, nval;
        final JCheckBox checkBox;

        public BooleanControl(final ParameterContainer f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            checkBox = new JCheckBox(name);
            checkBox.setAlignmentX(ALIGNMENT);
            checkBox.setFont(checkBox.getFont().deriveFont(fontSize));
            checkBox.setHorizontalTextPosition(SwingConstants.LEFT);
            addTip(f, checkBox);
            add(checkBox);
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

    class IntSliderControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = 4455144970014168302L;
		Method write, read;
        Object filter;
        int initValue = 0, nval;
        JSlider slider;
        JTextField tf;

        @Override
		public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                slider.setValue(b);
            }
        }

        public IntSliderControl(final ParameterContainer f, final String name, final Method w, final Method r, SliderParams params) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);

            final IntControl ic = new IntControl(f, name, w, r);
            add(ic);
            slider = new JSlider(params.minIntValue, params.maxIntValue);
            slider.setMaximumSize(new Dimension(200, 50));

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

        }
    }

    class FloatSliderControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = 2875473990519438532L;
		Method write, read;
        ParameterContainer filter;
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

        public FloatSliderControl(final ParameterContainer f, final String name, final Method w, final Method r, SliderParams params) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);

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

            slider.addChangeListener(new ChangeListener() {

                @Override
				public void stateChanged(ChangeEvent e) {
                    try {
//                        int v = slider.getValue();
                        currentValue = minValue + ((maxValue - minValue) * ((float) slider.getValue() / (slider.getMaximum() - slider.getMinimum())));
                        fc.set(new Float(currentValue));
                        w.invoke(filter, new Float(currentValue)); // write int value

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

    class IntControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = 7384247963259592507L;
		Method write, read;
        ParameterContainer filter;
        int initValue = 0, nval;
        final JTextField tf;

        @Override
		public void set(Object o) {
            if (o instanceof Integer) {
                Integer b = (Integer) o;
                tf.setText(b.toString());
            }
        }

        public IntControl(final ParameterContainer f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            tf = new JTextField("", 8);
            tf.setMaximumSize(new Dimension(100, 50));
            tf.setToolTipText("Integer control: use arrow keys or mouse wheel to change value by factor. Shift constrains to simple inc/dec");
            try {
                Integer x = (Integer) r.invoke(filter); // read int value
                if (x == null) {
                    log.warning("null Integer returned from read method " + r);
                    return;
                }
                initValue = x.intValue();
                String s = Integer.toString(x);
//                System.out.println("init value of "+name+" is "+s);
                tf.setText(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
            add(tf);
            tf.addActionListener(new ActionListener() {
                @Override
				public void actionPerformed(ActionEvent e) {
                    try {
                        int y = Integer.parseInt(
                                tf.getText());
                        w.invoke(filter, new Integer(y)); // write int value
                    } catch (NumberFormatException fe) {
                        tf.selectAll();
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
                }
            });
            tf.addFocusListener(new FocusListener() {
				@Override
				public void focusLost(FocusEvent arg0) {
                    try {
                        int y = Integer.parseInt(
                                tf.getText());
                        w.invoke(filter, new Integer(y)); // write int value
                    } catch (NumberFormatException fe) {
                        tf.selectAll();
                    } catch (InvocationTargetException ite) {
                        ite.printStackTrace();
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                    }
				}
				@Override
				public void focusGained(FocusEvent arg0) {
				}
			});
            tf.addKeyListener(new java.awt.event.KeyAdapter() {

                @Override
				public void keyPressed(java.awt.event.KeyEvent evt) {
                    try {
                        Integer x = (Integer) r.invoke(filter);
                        initValue = x.intValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code = evt.getKeyCode();
                //    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code == KeyEvent.VK_UP) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = 1;
                                } else {
                                    nval = Math.round(initValue * factor);
                                }
                                tf.setText(new Integer(nval).toString());
                                w.invoke(filter, new Integer(nval));
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
                                    nval = Math.round(initValue / factor);
                                }
                                tf.setText(new Integer(nval).toString());
                                w.invoke(filter, new Integer(nval));
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        }
                    } else {
                        // shifted int control just incs or decs by 1
                        if (code == KeyEvent.VK_UP) {
                            try {
                                nval = initValue + 1;
                                w.invoke(filter, new Integer(nval));
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
                                w.invoke(filter, new Integer(nval));
                                tf.setText(new Integer(nval).toString());
                                fixIntValue(tf, r);
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        }

                    }

                }
            });
            tf.addMouseWheelListener(new java.awt.event.MouseWheelListener() {

                @Override
				public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                    try {
                        Integer x = (Integer) r.invoke(filter);
                        initValue = x.intValue();
//                        System.out.println("x="+x);
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    int code = evt.getWheelRotation();
                  //  int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code < 0) {
                            try {
                                nval = initValue;
                                if (Math.round(initValue * wheelFactor) == initValue) {
                                    nval++;
                                } else {
                                    nval = Math.round(initValue * wheelFactor);
                                }
                                tf.setText(new Integer(nval).toString());
                                w.invoke(filter, new Integer(nval));
                            } catch (InvocationTargetException ite) {
                                ite.printStackTrace();
                            } catch (IllegalAccessException iae) {
                                iae.printStackTrace();
                            }
                        } else if (code > 0) {
                            try {
                                nval = initValue;
                                if (Math.round(initValue / wheelFactor) == initValue) {
                                    nval--;
                                } else {
                                    nval = Math.round(initValue / wheelFactor);
                                }
                                if (nval < 0) {
                                    nval = 0;
                                }
                                tf.setText(new Integer(nval).toString());
                                w.invoke(filter, new Integer(nval));
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

    void fixIntValue(JTextField tf, Method r) {
        // set text to actual value
        try {
            Integer x = (Integer) r.invoke(getParameterContainer()); // read int value
//            initValue=x.intValue();
            String s = Integer.toString(x);
            tf.setText(s);
        } catch (Exception e) {
            e.printStackTrace();
        }






    }

    class FloatControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = -4380664677658538035L;

		EngineeringFormat engFmt=new EngineeringFormat();
//        final String format="%.6f";

        Method write, read;
        ParameterContainer filter;
        float initValue = 0, nval;
        final JTextField tf;

        @Override
		public void set(Object o) {
            if (o instanceof Float) {
                Float b = (Float) o;
                tf.setText(b.toString());
            }
        }

        public FloatControl(final ParameterContainer f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
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
            tf.addFocusListener(new FocusListener() {

				@Override
				public void focusLost(FocusEvent e) {
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
				@Override
				public void focusGained(FocusEvent e) {
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
//                    int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    float floatFactor = factor;
                    if (shift) {
                        floatFactor = wheelFactor;
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
  //                  int mod = evt.getModifiers();
                    boolean shift = evt.isShiftDown();
                    if (!shift) {
                        if (code < 0) {
                            try {
                                nval = initValue;
                                if (nval == 0) {
                                    nval = DEFAULT_REAL_VALUE;
                                } else {
                                    nval = (initValue * wheelFactor);
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
                                    nval = (initValue / wheelFactor);
                                }
                                w.invoke(filter, new Float(initValue / wheelFactor));
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

    private boolean printedSetterWarning=false;

    /** Called when a filter calls firePropertyChange. The PropertyChangeEvent should send the bound property name and the old and new values.
    The GUI control is then updated by this method.
    @param propertyChangeEvent contains the property that has changed, e.g. it would be called from an EventFilter
     * with
     * <code>support.firePropertyChange("mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled, this.mapEventsToLearnedTopologyEnabled);</code>
     */
    @Override
	public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        if (propertyChangeEvent.getSource() == getParameterContainer()) {
            if (propertyChangeEvent.getPropertyName().equals("selected")) {
                return; // ignore changes to "selected" for filter because these are masked out from GUI building
            } else if (propertyChangeEvent.getPropertyName().equals("filterEnabled")) { // comes from EventFilter when filter is enabled or disabled
//            log.info("propertyChangeEvent name="+propertyChangeEvent.getPropertyName()+" src="+propertyChangeEvent.getSource()+" oldValue="+propertyChangeEvent.getOldValue()+" newValue="+propertyChangeEvent.getNewValue());
                boolean yes = (Boolean) propertyChangeEvent.getNewValue();
                enabledCheckBox.setSelected(yes);
                setBorderActive(yes);
            } else if (propertyChangeEvent.getPropertyName().equals("controlsExpanded")) { // comes from EventFilter when filter is enabled or disabled
//              log.info("propertyChangeEvent name="+propertyChangeEvent.getPropertyName()+" src="+propertyChangeEvent.getSource()+" oldValue="+propertyChangeEvent.getOldValue()+" newValue="+propertyChangeEvent.getNewValue());
            	this.setExpanded((Boolean)propertyChangeEvent.getNewValue());
            } else {
                // we need to find the control and set it appropriately. we don't need to set the property itself since this has already been done!
                try {
//                    log.info("PropertyChangeEvent received from " +
//                            propertyChangeEvent.getSource() + " for property=" +
//                            propertyChangeEvent.getPropertyName() +
//                            " newValue=" + propertyChangeEvent.getNewValue());
                	if (propertyChangeEvent.getPropertyName().equals("name")) {
                		setTitle(propertyChangeEvent.getNewValue().toString());
                	}

                	HasSetter setter = setterMap.get(propertyChangeEvent.getPropertyName());
                    if (setter == null) {
                        if (!getParameterContainer().isPropertyExcluded(propertyChangeEvent.getPropertyName()) && !printedSetterWarning) {
                        	Object o = propertyChangeEvent.getNewValue();
                        	if ((o instanceof Integer) || (o instanceof String) || (o instanceof Float) || (o instanceof Double) || (o instanceof Boolean) || (o instanceof Point2D) || (o instanceof Enum)) {
                        		log.warning("in parameter container " + getParameterContainer() + " (class :"+getParameterContainer().getClass().toString()+") there is no setter for property change from property named " + propertyChangeEvent.getPropertyName());
                        		printedSetterWarning = true;
                        	}
                        }
                    } else {
                        setter.set(propertyChangeEvent.getNewValue());
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

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">
    private void initComponents() {
//        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();



        jPanel1 = new javax.swing.JPanel();
        enabledCheckBox = new javax.swing.JCheckBox();
//        resetButton = new javax.swing.JButton();
        showControlsToggleButton = new javax.swing.JToggleButton();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

    }// </editor-fold>
    boolean controlsVisible = false;

    @Override
    public void setExpanded(boolean expanded) {
    	super.setExpanded(expanded);
    	if ((parameterContainer != null) && (this.isExpanded() != parameterContainer.isControlsExpanded())) {
			parameterContainer.setControlsExpanded(this.isExpanded());
		}
    }

    @Override
    public void toggleSelection() {
    	super.toggleSelection();
    	if ((parameterContainer != null) && (this.isExpanded() != parameterContainer.isControlsExpanded())) {
			parameterContainer.setControlsExpanded(this.isExpanded());
		}
    }


    public boolean isControlsVisible() {
        return controlsVisible;
    }

    /** Set visibility of individual filter controls; hides other filters.
     * @param visible true to show filter parameter controls, false to hide this filter's controls and to show all filters in chain.
     */
    public void setControlsVisible(boolean visible) {
        controlsVisible = visible;
//        getControllable().setSelected(visible); // exposing controls 'selects' this filter
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
//        if (!getControllable().isEnclosed() && c instanceof Window) {
//            if (c instanceof FilterFrame) {
//                // hide all filters except one that is being modified, *unless* we are an enclosed filter
//                FilterFrame<GeneralController> ff = (FilterFrame) c;
//                for (GeneralController f : ff.filterPanels) {
//                    if (f == this) {  // for us and if !visible
//                        f.setVisible(true); // always set us visible in chain since we are the one being touched
//                        continue;
//                    }
//
//                    f.setVisible(!visible); // hide / show other filters
//                }
//
//            }
//            ((Window) c).pack();
//        }

        if (c instanceof Window) {
            ((Window) c).pack();
        }
//        if(!getControllable().isEnclosed()){ // store last selected top level filter
//            if (visible) {
//                getControllable().getChip().getPrefs().put(FilterFrame.LAST_FILTER_SELECTED_KEY, getControllable().getClass().toString());
//            } else {
//                getControllable().getChip().getPrefs().put(FilterFrame.LAST_FILTER_SELECTED_KEY, "");
//            }
//        }
        showControlsToggleButton.setSelected(visible);
    }

    private void setBorderActive(final boolean yes) {
        // see http://forum.java.sun.com/thread.jspa?threadID=755789
        if (yes) {
//            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textText);
//            titledBorder.setBorder(redLineBorder);
        } else {
//            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textInactiveText);
//            titledBorder.setBorder(normalBorder);
        }

    }

    void toggleControlsVisible() {
        controlsVisible = !controlsVisible;
        setControlsVisible(controlsVisible);
    }

    public ParameterContainer getParameterContainer() {
        return parameterContainer;
    }

    public void setParameterContainer(ParameterContainer parameterContainer) {
        this.parameterContainer = parameterContainer;
    }

//    private void enabledCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {
//        boolean yes = enabledCheckBox.isSelected();
//        if (getControllable() != null) {
//            getControllable().setFilterEnabled(yes);
//        }
//
//        if (yes) {
//            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textText);
//            titledBorder.setBorder(redLineBorder);
//        } else {
//            ((TitledBorder) getBorder()).setTitleColor(SystemColor.textInactiveText);
//            titledBorder.setBorder(normalBorder);
//        }
//
//        repaint();
//        getControllable().setSelected(yes);
//    }

//    private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {
//        if (getControllable() != null) {
//            getControllable().resetFilter();
//        }
//        getControllable().setSelected(true);
//    }

//    private void showControlsToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
//        // TODO add your handling code here:
//    }

    // Variables declaration - do not modify
    protected javax.swing.JCheckBox enabledCheckBox;
    protected javax.swing.JPanel jPanel1;
//    private javax.swing.JButton resetButton;
    private javax.swing.JToggleButton showControlsToggleButton;
//    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration

    private class SliderParams {

//        Class<?> paramClass = null;
        int minIntValue, maxIntValue;
        float minFloatValue, maxFloatValue;

        SliderParams(Class<Integer> clazz, int minIntValue, int maxIntValue, float minFloatValue, float maxFloatValue) {
            this.minIntValue = minIntValue;
            this.minFloatValue = minFloatValue;
            this.maxIntValue = maxIntValue;
            this.maxFloatValue = maxFloatValue;
        }
    }

    private SliderParams isSliderType(PropertyDescriptor p, ParameterContainer filter) throws SecurityException {
//                if(c instanceof Class) System.out.println("filter="+filter+" propertyType="+c);
        //TODO add slider control type if property has getMin and getMax methods
//        boolean isSliderType = false;
        // check for min/max methods for property, e.g. getMinDt, getMaxDt for property dt
        String propCapped = p.getName().substring(0, 1).toUpperCase() + p.getName().substring(1); // eg. Dt for dt
        String minMethName = "getMin" + propCapped;
        String maxMethName = "getMax" + propCapped;
        SliderParams params = null;
        try {
            Method minMethod = filter.getClass().getMethod(minMethName, (Class[]) null);
            Method maxMethod = filter.getClass().getMethod(maxMethName, (Class[]) null);
//            isSliderType = true;
//            log.info("property " + p.getName() + " for filter " + filter + " has min/max methods, constructing slider control for it");
            if (p.getPropertyType() == Integer.TYPE) {
                int min = (Integer) minMethod.invoke(filter);
                int max = (Integer) maxMethod.invoke(filter);
                params = new SliderParams(Integer.class, min, max, 0, 0);
            } else if (p.getPropertyType() == Float.TYPE) {
                float min = (Float) minMethod.invoke(filter);
                float max = (Float) maxMethod.invoke(filter);
                params = new SliderParams(Integer.class, 0, 0, min, max);
            }
        } catch (NoSuchMethodException e) {
        } catch (Exception iae) {
            log.warning(iae.toString() + " for property " + p + " in filter " + filter);
        }
        return params;
    }

    class Point2DControl extends JPanel implements HasSetter {

        /**
		 *
		 */
		private static final long serialVersionUID = 4181987148305097419L;
		Method write, read;
        ParameterContainer filter;
        Point2D.Float point;
        float initValue = 0, nval;
        final JTextField tfx, tfy;
        final String format = "%.1f";

        @Override
		final public void set(Object o) {
            if (o instanceof Point2D.Float) {
                Point2D.Float b = (Point2D.Float) o;
                tfx.setText(String.format(format, b.x));
                tfy.setText(String.format(format, b.y));
            }
        }

        final class PointActionListener implements ActionListener {

            Method readMethod, writeMethod;
            Point2D.Float point = new Point2D.Float(0, 0);

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
                    point = (Point2D.Float) readMethod.invoke(filter); // getString the value from the getter method to constrain it
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

        public Point2DControl(final ParameterContainer f, final String name, final Method w, final Method r) {
            super();
            setterMap.put(name, this);
            filter = f;
            write = w;
            read = r;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(ALIGNMENT);
//            setLayout(new FlowLayout(FlowLayout.LEADING));
            JLabel label = new JLabel(name);
            label.setAlignmentX(ALIGNMENT);
            label.setFont(label.getFont().deriveFont(fontSize));
            addTip(f, label);
            add(label);

            tfx = new JTextField("", 10);
            tfx.setMaximumSize(new Dimension(100, 50));
            tfx.setToolTipText("Point2D X: type new value here and press enter.");

            tfy = new JTextField("", 10);
            tfy.setMaximumSize(new Dimension(100, 50));
            tfy.setToolTipText("Point2D Y: type new value here and press enter.");

            try {
                Point2D.Float p = (Point2D.Float) r.invoke(filter);
                if (p == null) {
                    log.warning("null object returned from read method " + r);
                    return;
                }
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

            tfx.addActionListener(new PointActionListener(r, w));
            tfy.addActionListener(new PointActionListener(r, w));

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

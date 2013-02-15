/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JComponent;

//import javax.swing.JOptionPane;


/**
 * 
 * @author Dennis Goehlsdorf, derived from Peter OConnor's Controllable, which was derived from Tobi Delbruck's EventFilter
 */
public abstract class ParameterContainer implements /*Serializable,*/ PropertyChangeListener {

	/**
	 * 
	 */
//	private static final long serialVersionUID = 7279068027303003157L;
    private static Logger log = Logger.getLogger("Filters");

	protected PropertyChangeSupport support = new PropertyChangeSupport(this);

	/** The key,value table of property tooltip strings. */
	protected HashMap<String, String> propertyTooltipMap = null;
	/**
	 * The key,value table of propery group associations. The key the property
	 * name and the value is the group name.
	 */
	protected HashMap<String, String> property2GroupMap = null;
	/**
	 * The keys are the names of property groups, and the values are lists of
	 * properties in the key's group.
	 */
	protected HashMap<String, ArrayList<String>> group2PropertyListMap = null;

	// EventListener listener=new EventListener() {};

	transient ArrayList<ActionListener> listeners = new ArrayList<ActionListener>();

	private Preferences prefs;
	
	private String name;
	// public SpikeStack net;

	// public static enum Options {LIF_STP_RBM,LIF_BASIC_RBM};

    abstract class SingleParameter<T> {
    	Method writeMethod;
    	Method readMethod;
    	String propertyName;
    	public SingleParameter(Method readMethod, Method writeMethod, String propertyName) {
    		this.writeMethod = writeMethod;
    		this.readMethod = readMethod;
    		this.propertyName = propertyName;
    	}
    	void set(Object newValue) {
    		try {
				writeMethod.invoke(ParameterContainer.this, newValue);
			} catch (IllegalArgumentException e) {
				log.warning("Illegal argument while invoking setter method.");
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				log.warning("Illegal access while invoking setter method.");
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				log.warning("Target could not be invoked while invoking setter method.");
				e.printStackTrace();
			}
    	}
    	@SuppressWarnings("unchecked")
		T get() {
    		try {
				return (T)readMethod.invoke(ParameterContainer.this, (Object[])null);
			} catch (IllegalArgumentException e) {
				log.warning("Illegal argument while invoking getter method.");
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				log.warning("Illegal access while invoking getter method.");
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				log.warning("Target could not be invoked while invoking getter method.");
				e.printStackTrace();
			}
			return null;
    	}
    	void restore() {
    		set(readParameter());
    	}
    	abstract void storeParameter(Object newValue);
    	abstract T readParameter();
    }
    
    private HashMap<String, SingleParameter<?>> setterMethods = new HashMap<String, SingleParameter<?>>();
	
	
	public ParameterContainer(String name, Preferences parentPrefs, String nodeName) {
		this(name, parentPrefs.node(nodeName));
	}
	public ParameterContainer(String name, Preferences prefs) {
//	public ParameterContainer(String name, Preferences parentPrefs, String nodeName) {
		this.name = name;
		this.prefs = prefs;
		if (prefs == null) {
			this.prefs = Preferences.userNodeForPackage(this.getClass());
		}
//		this.prefs = parentPrefs.node(nodeName);
		support.addPropertyChangeListener(this);
		discoverParameters();
	}

    public PropertyChangeSupport getSupport() {
        return support;
    }

    public Preferences getPrefs() {
		return prefs;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		getSupport().firePropertyChange("name", this.name, name);
		this.name = name;
	}

	public JComponent createCustomControls() {
		return null;
	}

	public void addActionListener(ActionListener l) {
		getListeners().add(l);
	}

	public ArrayList<ActionListener> getListeners() {
		if (listeners == null)
			listeners = new ArrayList<ActionListener>();

		return listeners;
	}

	public void updateControl() {
		for (ActionListener ac : getListeners())
			ac.actionPerformed(new ActionEvent(this, 0, "garbage"));
	}

	// public ArrayList<Controllable> getSubControllers()
	// { return new ArrayList<Controllable>();
	// }

	/**
	 * Fires PropertyChangeEvents when filter is enabled or disabled with key
	 * "filterEnabled"
	 * 
	 * @return change support
	 */
	public PropertyChangeSupport getPropertyChangeSupport() {
		return support;
	}

//	public void showErrorMsg(String text) {
//		JOptionPane.showMessageDialog(null, text, "Error",
//				JOptionPane.ERROR_MESSAGE);
//	}


	/**
	 * Developers can use setPropertyTooltip to add an optional tooltip for a
	 * filter property so that the tip is shown as the tooltip for the label or
	 * checkbox property in the generated GUI.
	 * <p>
	 * In netbeans, you can add this macro to ease entering tooltips for filter
	 * parameters:
	 * 
	 * <pre>
	 * select-word copy-to-clipboard caret-begin-line caret-down "{setPropertyTooltip(\"" paste-from-clipboard "\",\"\");}" insert-break caret-up caret-end-line caret-backward caret-backward caret-backward caret-backward
	 * </pre>
	 * 
	 * @param propertyName
	 *            the name of the property (e.g. an int, float, or boolean, e.g.
	 *            "dt")
	 * @param tooltip
	 *            the tooltip String to display
	 */
	protected void setPropertyTooltip(String propertyName, String tooltip) {
		if (propertyTooltipMap == null) {
			propertyTooltipMap = new HashMap<String, String>();
		}
		propertyTooltipMap.put(propertyName.toLowerCase(), tooltip);
	}

	/**
	 * Convenience method to add properties to groups along with adding a tip
	 * for the property.
	 * 
	 * @param groupName
	 *            the property group name.
	 * @param propertyName
	 *            the property name.
	 * @param tooltip
	 *            the tip.
	 * @see #PARAM_GROUP_GLOBAL
	 */
	protected void setPropertyTooltip(String groupName, String propertyName,
			String tooltip) {
		setPropertyTooltip(propertyName.toLowerCase(), tooltip);
		addPropertyToGroup(groupName, propertyName.toLowerCase());
	}

	/** @return the tooltip for the property */
	public String getPropertyTooltip(String propertyName) {
		if (propertyTooltipMap == null) {
			return null;
		}
		return propertyTooltipMap.get(propertyName.toLowerCase());
	}

	/**
	 * Adds a property to a group, creating the group if needed.
	 * 
	 * @param groupName
	 *            a named parameter group.
	 * @param propertyName
	 *            the property name.
	 */
	protected void addPropertyToGroup(String groupName, String propertyName) {
		if (property2GroupMap == null) {
			property2GroupMap = new HashMap<String, String>();
		}
		if (group2PropertyListMap == null) {
			group2PropertyListMap = new HashMap<String, ArrayList<String>>();
		}
		// add the mapping from property to group
		property2GroupMap.put(propertyName.toLowerCase(), groupName);

		// create the list of properties in the group
		ArrayList<String> propList;
		if (!group2PropertyListMap.containsKey(groupName)) {
			propList = new ArrayList<String>(2);
			group2PropertyListMap.put(groupName, propList);
		} else {
			propList = group2PropertyListMap.get(groupName);
		}
		propList.add(propertyName.toLowerCase());
	}

	/**
	 * Returns the name of the property group for a property.
	 * 
	 * @param propertyName
	 *            the property name string.
	 * @return the property group name.
	 */
	public String getPropertyGroup(String propertyName) {
		if (property2GroupMap == null) {
			return null;
		}
		return property2GroupMap.get(propertyName.toLowerCase());
	}

	/**
	 * Gets the list of property names in a particular group.
	 * 
	 * @param groupName
	 *            the name of the group.
	 * @return the ArrayList of property names in the group.
	 */
	protected ArrayList<String> getPropertyGroupList(String groupName) {
		if (group2PropertyListMap == null) {
			return null;
		}
		return group2PropertyListMap.get(groupName);
	}

	/**
	 * Returns the set of property groups.
	 * 
	 * @return Set view of property groups.
	 */
	public Set<String> getPropertyGroupSet() {
		if (group2PropertyListMap == null) {
			return null;
		}
		return group2PropertyListMap.keySet();
	}

	/**
	 * Returns the mapping from property name to group name. If null, no groups
	 * have been declared.
	 * 
	 * @return the map, or null if no groups have been declared by adding any
	 *         properties.
	 * @see #property2GroupMap
	 */
	public HashMap<String, String> getPropertyGroupMap() {
		return property2GroupMap;
	}

	/**
	 * Returns true if the filter has property groups.
	 * 
	 */
	public boolean hasPropertyGroups() {
		return property2GroupMap != null;
	}

    public void restoreParameters() {
    	for (SingleParameter<?> sp : setterMethods.values()) {
    		sp.restore();
    	}
    }
    
	private void discoverParameters() {
        BeanInfo info;
		try {
			info = Introspector.getBeanInfo(this.getClass());
	        PropertyDescriptor[] props = info.getPropertyDescriptors();
	        for (PropertyDescriptor p : props) {
//                try {
                  Class<?> c = p.getPropertyType();
                  String name = p.getName();
                  if (c != null && (p.getReadMethod() != null && p.getWriteMethod() != null) && 
                		  (c == Integer.TYPE ||
                				 c  == Float.TYPE ||
                				 c == Boolean.TYPE ||
                				 c == String.class ||
                				 c.isEnum() || 
                				 c==Point2D.Float.class)) {
//                	  setterMethods.put(name, p.getWriteMethod());
                  }
                  if (c == Integer.TYPE && p.getReadMethod() != null && p.getWriteMethod() != null) {
                	  setterMethods.put(name, new SingleParameter<Integer>(p.getReadMethod(), p.getWriteMethod(),name) {
						@Override
						void storeParameter(Object newValue) {
							getPrefs().putInt(propertyName, (Integer)newValue);
						}
						@Override
						Integer readParameter() {
							return getPrefs().getInt(propertyName, get());
						}
                	  });
                  } else if (c == Float.TYPE && p.getReadMethod() != null && p.getWriteMethod() != null) {
                	  setterMethods.put(name, new SingleParameter<Float>(p.getReadMethod(), p.getWriteMethod(),name) {
  						@Override
  						void storeParameter(Object newValue) {
  							getPrefs().putFloat(propertyName, (Float)newValue);
  						}

						@Override
						Float readParameter() {
							return getPrefs().getFloat(propertyName, get());
						}
                  	  });
                  } else if (c == Boolean.TYPE && p.getReadMethod() != null && p.getWriteMethod() != null) {
                	  setterMethods.put(name, new SingleParameter<Boolean>(p.getReadMethod(), p.getWriteMethod(),name) {
  						@Override
  						void storeParameter(Object newValue) {
  							getPrefs().putBoolean(propertyName, (Boolean)newValue);
  						}
						@Override
						Boolean readParameter() {
							return getPrefs().getBoolean(propertyName, get());
						}
                  	  });
                  } else if (c == String.class && p.getReadMethod() != null && p.getWriteMethod() != null) {
                	  setterMethods.put(name, new SingleParameter<String>(p.getReadMethod(), p.getWriteMethod(),name) {
  						@Override
  						void storeParameter(Object newValue) {
  							getPrefs().put(propertyName, (String)newValue);
  						}

						@Override
						String readParameter() {
							return getPrefs().get(propertyName, get());
						}
                  	  });
                  } else if (c != null && c.isEnum() && p.getReadMethod() != null && p.getWriteMethod() != null) {
                	  final Class<?> cf = c; 
                	  setterMethods.put(name, new SingleParameter<Enum<?>>(p.getReadMethod(), p.getWriteMethod(),name) {
                		  final Object[] enumConstants = cf.getClass().getEnumConstants();
  						@Override
  						void storeParameter(Object newValue) {
  							getPrefs().put(propertyName, ((Enum<?>)newValue).toString());
  						}

						@Override
						Enum<?> readParameter() {
							String value = getPrefs().get(propertyName, get().toString());
							for (Object o : enumConstants) {
								if (o.toString().toUpperCase().equals(value.toUpperCase())) {
									return (Enum<?>)o;
								}
							}
							return null;
						}
                  	  });
                  } else if (c != null && c==Point2D.Float.class && p.getReadMethod() != null && p.getWriteMethod() != null) {
                	  setterMethods.put(name, new SingleParameter<Point2D>(p.getReadMethod(), p.getWriteMethod(),name) {
  						@Override
  						void storeParameter(Object newValue) {
  							getPrefs().putFloat(propertyName, (Float)newValue);
  							getPrefs().putInt(propertyName, (Integer)newValue);
  						}

						@Override
						Point2D readParameter() {
							Point2D def = get();
							def.setLocation(getPrefs().getDouble(propertyName+".x", def.getX()), getPrefs().getDouble(propertyName+".y", def.getY()));
							return def;
						}
                  	  });
                  } else {
                  }
	        }
		} catch (IntrospectionException e) {
			log.warning("Problem while introspecting an instance of "+this.getClass().getName()+" named '"+this.name+"'!");
			e.printStackTrace();
		}
		
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
		Object newValue = propertyChangeEvent.getNewValue();
		if (newValue != null && !newValue.equals(propertyChangeEvent.getOldValue())) {
			SingleParameter<?> setter = setterMethods.get(propertyChangeEvent.getPropertyName());
			if (setter != null)
				setter.storeParameter(newValue);
		}
	}

}

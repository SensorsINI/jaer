/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.*;
import javax.swing.JOptionPane;

/**
 *
 * @author Peter, but most code stolen from Tobi's EventFilter
 */
public abstract class Controllable implements Serializable {
    
    
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);

    /** The key,value table of property tooltip strings. */
    protected HashMap<String, String> propertyTooltipMap = null;
//    /** The key,value table of propery group associations.
//     * The key the property name and the value is the group name.*/
//    protected HashMap<String, String> property2GroupMap = null;
//    /** The keys are the names of property groups, and the values are lists of properties in the key's group.*/
//    protected HashMap<String, ArrayList<String>> group2PropertyListMap = null;

//    EventListener listener=new EventListener() {};
    
    transient ArrayList<ActionListener> listeners=new ArrayList();
    
//    public SpikeStack net;
    
//    public static enum Options {LIF_STP_RBM,LIF_BASIC_RBM};
        
    public Controllable()
    {
        
        
        
    }
    
    
    
    
    public void addActionListener(ActionListener l)
    {
        
        getListeners().add(l);
        
    }
    
    public ArrayList<ActionListener> getListeners()
    {
        if (listeners==null)
            listeners=new ArrayList();
        
        return listeners;
    }
    
    public void updateControl()
    {
        
        
        for (ActionListener ac:getListeners())
            ac.actionPerformed(new ActionEvent(this,0,"garbage"));
    }
    
    
    
    public ArrayList<Controllable> getSubControllers()
    {   return new ArrayList();
    }
    
    
    /** Fires PropertyChangeEvents when filter is enabled or disabled with key "filterEnabled"
     * @return change support
     */
    public PropertyChangeSupport getPropertyChangeSupport() {
        return support;
    }
    
    public void showErrorMsg(String text)
    {
        JOptionPane.showMessageDialog(null, text, "Error",JOptionPane.ERROR_MESSAGE);        
    }
    
    public abstract String getName();
    
    
    
     /** Developers can use setPropertyTooltip to add an optional tooltip for a filter property so that the tip is shown
     * as the tooltip for the label or checkbox property in the generated GUI.
     * <p>
     * In netbeans, you can add this macro to ease entering tooltips for filter parameters:
     * <pre>
     * select-word copy-to-clipboard caret-begin-line caret-down "{setPropertyTooltip(\"" paste-from-clipboard "\",\"\");}" insert-break caret-up caret-end-line caret-backward caret-backward caret-backward caret-backward
     * </pre>
     *
     * @param propertyName the name of the property (e.g. an int, float, or boolean, e.g. "dt")
     * @param tooltip the tooltip String to display
     */
    protected void setPropertyTooltip(String propertyName, String tooltip) {
        if (propertyTooltipMap == null) {
            propertyTooltipMap = new HashMap<String, String>();
        }
        propertyTooltipMap.put(propertyName.toLowerCase(), tooltip);
    }
    
    
    /** Convenience method to add properties to groups along with adding a tip for the property.
     *
     * @param groupName the property group name.
     * @param propertyName the property name.
     * @param tooltip the tip.
     * @see #PARAM_GROUP_GLOBAL
     */
//    protected void setPropertyTooltip(String groupName, String propertyName, String tooltip) {
//        setPropertyTooltip(propertyName.toLowerCase(), tooltip);
//        addPropertyToGroup(groupName, propertyName.toLowerCase());
//    }

    /** @return the tooltip for the property */
    protected String getPropertyTooltip(String propertyName) {
        if (propertyTooltipMap == null) {
            return null;
        }
        return propertyTooltipMap.get(propertyName.toLowerCase());
    }

    /** Adds a property to a group, creating the group if needed.
     * 
     * @param groupName a named parameter group.
     * @param propertyName the property name.
     */
//    protected void addPropertyToGroup(String groupName, String propertyName) {
//        if (property2GroupMap == null) {
//            property2GroupMap = new HashMap();
//        }
//        if (group2PropertyListMap == null) {
//            group2PropertyListMap = new HashMap();
//        }
//        // add the mapping from property to group
//        property2GroupMap.put(propertyName.toLowerCase(), groupName);
//
//        // create the list of properties in the group
//        ArrayList<String> propList;
//        if (!group2PropertyListMap.containsKey(groupName)) {
//            propList = new ArrayList<String>(2);
//            group2PropertyListMap.put(groupName, propList);
//        } else {
//            propList = group2PropertyListMap.get(groupName);
//        }
//        propList.add(propertyName.toLowerCase());
//    }

    /** Returns the name of the property group for a property.
     *
     * @param propertyName the property name string.
     * @return the property group name.
     */
//    public String getPropertyGroup(String propertyName) {
//        if (property2GroupMap == null) {
//            return null;
//        }
//        return property2GroupMap.get(propertyName.toLowerCase());
//    }

    /** Gets the list of property names in a particular group.
     *
     * @param groupName the name of the group.
     * @return the ArrayList of property names in the group.
     */
//    protected ArrayList<String> getPropertyGroupList(String groupName) {
//        if (group2PropertyListMap == null) {
//            return null;
//        }
//        return group2PropertyListMap.get(groupName);
//    }

    /** Returns the set of property groups.
     * 
     * @return Set view of property groups.
     */
//    protected Set<String> getPropertyGroupSet() {
//        if (group2PropertyListMap == null) {
//            return null;
//        }
//        return group2PropertyListMap.keySet();
//    }

    /**
     * Returns the mapping from property name to group name.
     * If null, no groups have been declared.
     * 
     * @return the map, or null if no groups have been declared by adding any properties.
     * @see #property2GroupMap
     */
//    public HashMap<String, String> getPropertyGroupMap() {
//        return property2GroupMap;
//    }

    /** Returns true if the filter has property groups.
     *
     */
//    public boolean hasPropertyGroups() {
//        return property2GroupMap != null;
//    }
    
}

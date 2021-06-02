/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * Provides support for adding  tooltips to object properties.
 * 
 * @author Tobi
 */
public class PropertyTooltipSupport implements HasPropertyTooltips{
      /** The key,value table of property tooltip strings. */
    protected HashMap<String, String> propertyTooltipMap = null;
    /** The key,value table of property group associations.
     * The key the property name and the value is the group name.*/
    protected HashMap<String, String> property2GroupMap = null;
    /** The keys are the names of property groups, and the values are lists of properties in the key's group.*/
    protected HashMap<String, ArrayList<String>> group2PropertyListMap = null;
  
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
    public void setPropertyTooltip(String propertyName, String tooltip) {
        if (propertyTooltipMap == null) {
            propertyTooltipMap = new HashMap<String, String>();
        }
        propertyTooltipMap.put(propertyName.toLowerCase(), tooltip);
    }
    
        /** Use this key for global parameters in your filter constructor, as in
     * <pre>
    setPropertyTooltip(TOOLTIP_GROUP_GLOBAL, "propertyName", "property tip string");
    </pre>
     */
    public static final String TOOLTIP_GROUP_GLOBAL = "Global";

    /** Convenience method to add properties to groups along with adding a tip for the property.
     *
     * @param groupName the property group name.
     * @param propertyName the property name.
     * @param tooltip the tip.
     * @see #TOOLTIP_GROUP_GLOBAL
     */
    public void setPropertyTooltip(String groupName, String propertyName, String tooltip) {
        setPropertyTooltip(propertyName.toLowerCase(), tooltip);
        addPropertyToGroup(groupName, propertyName.toLowerCase());
    }

    /** @return the tooltip for the property */
    @Override
    public String getPropertyTooltip(String propertyName) {
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
    public void addPropertyToGroup(String groupName, String propertyName) {
        if (property2GroupMap == null) {
            property2GroupMap = new HashMap();
        }
        if (group2PropertyListMap == null) {
            group2PropertyListMap = new HashMap();
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

    /** Returns the name of the property group for a property.
     *
     * @param propertyName the property name string.
     * @return the property group name.
     */
    public String getPropertyGroup(String propertyName) {
        if (property2GroupMap == null) {
            return null;
        }
        return property2GroupMap.get(propertyName.toLowerCase());
    }

    /** Gets the list of property names in a particular group.
     *
     * @param groupName the name of the group.
     * @return the ArrayList of property names in the group.
     */
    public ArrayList<String> getPropertyGroupList(String groupName) {
        if (group2PropertyListMap == null) {
            return null;
        }
        return group2PropertyListMap.get(groupName);
    }

    /** Returns the set of property groups.
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
     * Returns the mapping from property name to group name.
     * If null, no groups have been declared.
     * 
     * @return the map, or null if no groups have been declared by adding any properties.
     * @see #property2GroupMap
     */
    public HashMap<String, String> getPropertyGroupMap() {
        return property2GroupMap;
    }

    /** Returns true if the filter has property groups.
     *
     */
    public boolean hasPropertyGroups() {
        return property2GroupMap != null;
    }
}

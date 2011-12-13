/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.FileHandler;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author matthias
 * 
 * Stores and maintains all parameters used in the system.
 */
public class Parameters {
    
    /*
     * general
     */
    public static final Parameter DEBUG_MODE = new Parameter(
        "general", 
        "debug",
        "DEBUG_MODE",
        "Visualizes all information available if activated.",
        Type.Boolean,
        false,
        true);
    
    public static final Parameter METHOD = new Parameter(
        "general", 
        "method",
        "METHOD",
        "no longer used...",
        Type.Integer,
        true,
        3);
    
    public static final Parameter GENERAL_CLUSTER_N = new Parameter(
        "general", 
        "clusterN",
        "GENERAL_CLUSTER_N",
        "Defines the maximal number of clusters used by the tracker.",
        Type.Integer,
        false,
        1);
    
    public static final Parameter GENERAL_CANDIDATE_N = new Parameter(
        "general", 
        "candidateN",
        "GENERAL_CANDIDATE_N",
        "Defines the maximal number of candidate clusters used by the tracker.",
        Type.Integer,
        false,
        3);
    
    /*
     * event assignment
     */
    public static final Parameter EVENT_ASSINGMENT_CHANCE = new Parameter(
        "event.assignment", 
        "eventassignmentchance",
        "EVENT_ASSINGMENT_CHANCE",
        "Defines the threshold for the fast rejection of events.",
        Type.Float,
        false,
        100f);
    
    public static final Parameter EVENT_ASSINGMENT_THRESHOLD = new Parameter(
        "event.assignment", 
        "eventassignmentthreshold",
        "EVENT_ASSINGMENT_THRESHOLD",
        "Defines the threshold for the assignment of events.",
        Type.Float,
        false,
        0.25f);
    
    public static final Parameter EVENT_ASSINGMENT_SPATIAL_SHARPNESS = new Parameter(
        "event.assignment", 
        "spatialsharpness",
        "EVENT_ASSINGMENT_SPATIAL_SHARPNESS",
        "Defines the sharpness of the spatial cost function.",
        Type.Float,
        false,
        2f);
    
    public static final Parameter EVENT_ASSINGMENT_TEMPORAL_SHARPNESS = new Parameter(
        "event.assignment", 
        "temporalsharpness",
        "EVENT_ASSINGMENT_TEMPORAL_SHARPNESS",
        "Defines the sharpness of the temporal cost function.",
        Type.Float,
        false,
        500f);
    
    public static final Parameter EVENT_ASSINGMENT_DIFFERENCE = new Parameter(
        "event.assignmnet", 
        "difference",
        "EVENT_ASSINGMENT_DIFFERENCE",
        "Defines the threshold used if two or more clusters are valid ones for the assignment.",
        Type.Float,
        false,
        0.7f);
    
    /*
     * cluster assignment
     */
    public static final Parameter CLUSTER_ASSIGNMENT_THRESHOLD = new Parameter(
        "cluster.assignment", 
        "clusterassignmentthreshold",
        "CLUSTER_ASSIGNMENT_THRESHOLD",
        "Defines the threshold for the assignment of clusters to a temporal pattern.",
        Type.Float,
        false,
        0.2f);
    
    public static final Parameter CLUSTER_MERGE_THRESHOLD = new Parameter(
        "cluster.assignment", 
        "clustermergethreshold",
        "CLUSTER_MERGE_THRESHOLD",
        "Defines the threshold for the merge of clusters that are too similar.",
        Type.Float,
        false,
        100f);
    
    public static final Parameter CLUSTER_DELETION_THRESHOLD = new Parameter(
        "cluster.assignment", 
        "clusterdeletethreshold",
        "CLUSTER_DELETION_THRESHOLD",
        "Defines the threshold for the delete an unused cluster.",
        Type.Float,
        false,
        10f);
    
    /*
     * transition history
     */
    public static final Parameter TRANSITION_HISTORY_KERNEL_METHODE = new Parameter(
        "transitionhistory", 
        "kernelmethod",
        "TRANSITION_HISTORY_KERNEL_METHODE",
        "Defines the method to convolve the signal with a kernel.",
        Type.String,
        false,
        "eventdriven");
    
    public static final Parameter TRANSITION_HISTORY_MAX_WINDOW = new Parameter(
        "transitionhistory", 
        "maxwindow",
        "TRANSITION_HISTORY_MAX_WINDOW",
        "Defines the size of the window to search local maximas.",
        Type.Integer,
        false,
        10000);
    
    public static final Parameter TRANSITION_HISTORY_MAX_RESOLUTION = new Parameter(
        "transitionhistory", 
        "maxresolution",
        "TRANSITION_HISTORY_MAX_RESOLUTION",
        "Defines the temporal resolution with which the local maximas are stored.",
        Type.Integer,
        false,
        1000);
    
    public static final Parameter TRANSITION_HISTORY_DISTRIBUTION = new Parameter(
        "transitionhistory", 
        "distribution",
        "TRANSITION_HISTORY_DISTRIBUTION",
        "Defines the average distribution of events belonging to the same transition.",
        Type.Integer,
        false,
        200);
    
    public static final Parameter TRANSITION_HISTORY_DEVIATION = new Parameter(
        "transitionhistory", 
        "deviation",
        "TRANSITION_HISTORY_DEVIATION",
        "Defines allowed deviation in the number of elements of a possible transition compared to the maxima found.",
        Type.Float,
        false,
        0.2f);
    
    /*
     * signal
     */
    public static final Parameter SIGNAL_TRANSITION_PERCENTAGE = new Parameter(
        "signal", 
        "transitionpercentage",
        "SIGNAL_TRANSITION_PERCENTAGE",
        "Defines the number of events used to create a transition.",
        Type.Float,
        false,
        0.6f);
    
    public static final Parameter SIGNAL_TEMPORAL_RESOLUTION = new Parameter(
        "signal", 
        "temporalresolution",
        "SIGNAL_TEMPORAL_RESOLUTION",
        "Defines the temporal resolution used by the tracker.",
        Type.Integer,
        false,
        10);
    
    public static final Parameter SIGNAL_TEMPORAL_OBSERVATIONS = new Parameter(
        "signal", 
        "temporalobservations",
        "SIGNAL_TEMPORAL_OBSERVATIONS",
        "Defines the number of observations stored by the tracker.",
        Type.Integer,
        false,
        10);
    
    public static final Parameter SIGNAL_QUALITY = new Parameter(
        "signal", 
        "quality",
        "SIGNAL_QUALITY",
        "Defines the required quality of the extracted signal to be accepted.",
        Type.Float,
        false,
        0.9f);
    
    public static final Parameter NOISE_DURATION = new Parameter(
        "signal", 
        "noiseduration",
        "NOISE_DURATION",
        "Defines the level of noise within the tracker.",
        Type.Integer,
        false,
        100);
    
    /*
     * extractor
     */
    public static final Parameter EXTRACTOR_PERIOD = new Parameter(
        "extractor", 
        "extractorperiod",
        "EXTRACTOR_PERIOD",
        "Defines which type of extractor has to be used to find the period of the signal.",
        Type.String,
        true,
        "correlation");
    
    public static final Parameter EXTRACTOR_SIGNAL = new Parameter(
        "extractor", 
        "extractorsignal",
        "EXTRACTOR_SIGNAL",
        "Defines which type of extractor has to be used to find the signal.",
        Type.String,
        true,
        "transition");
    
    /*
     * predictor
     */
    public static final Parameter PREDICTOR_TEMPORAL_TYPE = new Parameter(
        "predictor", 
        "temporaltype",
        "PREDICTOR_TEMPORAL_TYPE",
        "Defines the type of predictor used for the temporal prediction.",
        Type.String,
        false,
        "occurance");
    
    public static final Parameter PREDICTOR_ACCELERATION_TYPE = new Parameter(
        "predictor", 
        "accelerationtype",
        "PREDICTOR_ACCELERATION_TYPE",
        "Defines the type of predictor used for the acceleration.",
        Type.String,
        false,
        "angular");
    
    public static final Parameter PREDICTOR_ACCELERATION_FORWARD = new Parameter(
        "predictor", 
        "accelerationforward",
        "PREDICTOR_ACCELERATION_FORWARD",
        "Defines the maximal forward acceleration of the object.",
        Type.Float,
        false,
        0.00001f);
    
    public static final Parameter PREDICTOR_ACCELERATION_SIDEWAY = new Parameter(
        "predictor", 
        "accelerationsideway",
        "PREDICTOR_ACCELERATION_SIDEWAY",
        "Defines the maximal sideway acceleration of the object.",
        Type.Float,
        false,
        0.1f);
    
    public static final Parameter PREDICTOR_SINGAL_CORRELATION = new Parameter(
        "predictor", 
        "signalcorrelation",
        "PREDICTOR_SINGAL_CORRELATION",
        "Defines the time interval after which the signal correlation has to be recomputed.",
        Type.Integer,
        false,
        10000);
    
    /** Stores the instance of the class. */
    private static Parameters instance = null;
    
    /** Stores the parameters and their values. */
    private Map<Parameter, Object> parameters;
    
    /**
     * The method getInstance() has to be used in order to create a new
     * instance of the class.
     */
    private Parameters() {
        this.parameters = new HashMap<Parameter, Object>();
        
        for (Parameter p : this.getParameters()) {
            this.add(p, p.value);
        }
    }
    
    /**
     * Resets the instance.
     */
    public void reset() {
        this.parameters.clear();
    }
    
    /**
     * Addas a new parameter to the instance.
     * 
     * @param key The key to identify the parameter.
     * @param value The value of the parameter.
     */
    public void add(Parameter key, Object value) {
        this.parameters.put(key, value);
    }
    
    /**
     * Checks whether the parameter does exist or not.
     * 
     * @param key The key to identify the parameter.
     * 
     * @return True, if the parameter exists, false otherwise.
     */
    public boolean hasKey(Parameter key) {
        return this.parameters.containsKey(key);
    }
    
    /**
     * Gets the value of the parameter.
     * 
     * @param key The key to identify the parameter.
     * 
     * @return The value of the parameter.
     */
    public Object get(Parameter key) {
        return this.parameters.get(key);
    }
    
    public Integer getAsInteger(Parameter key) {
        return (Integer)this.get(key);
    }
    
    public Float getAsFloat(Parameter key) {
        return (Float)this.get(key);
    }
    
    public Double getAsDouble(Parameter key) {
        return (Double)this.get(key);
    }
    
    public String getAsString(Parameter key) {
        return (String)this.get(key);
    }
    
    public Boolean getAsBoolean(Parameter key) {
        return (Boolean)this.get(key);
    }
    
    /**
     * Gets the instance of the class Parameters.
     * 
     * @return The instance of the class Parameters.
     */
    public static Parameters getInstance() {
        if (Parameters.instance == null) Parameters.instance = new Parameters();
        
        return Parameters.instance;
    }
    
    /**
     * Gets a list of all defined parameters.
     * 
     * @return All defined parameters.
     */
    public List<Parameter> getParameters() {
        List<Parameter> r = new ArrayList<Parameter>();
        
        Field[] fields = Parameters.class.getDeclaredFields();
        
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getType().getName().equals(Parameter.class.getName())) {
                try {
                    r.add((Parameter)fields[i].get(new Object()));
                } 
                catch (IllegalArgumentException ex) { } 
                catch (IllegalAccessException ex) { }
            }
        }
        return r;
    }
    
    /**
     * Generates a file containing all methods to get and set the defined
     * parameters.
     * 
     * @param path The path of the file.
     */
    public void generateFile(String path) {
        
        Parameter[] a = this.getParameters().toArray(new Parameter[0]);
        Arrays.sort(a, new ParameterComparator());
        
        FileHandler.getInstance(path).delete();
                
        for (int i = 0; i < a.length; i++) {
            FileHandler.getInstance(path).writeLine(a[i].getDefinition());
            FileHandler.getInstance(path).writeLine(a[i].getTooltip());
            FileHandler.getInstance(path).writeLine("");
        }
        FileHandler.getInstance(path).writeLine("");
        FileHandler.getInstance(path).writeLine("");
        FileHandler.getInstance(path).writeLine("");
        
        for (int i = 0; i < a.length; i++) {
            FileHandler.getInstance(path).writeLine(a[i].getGetter().split("\\n"));
            FileHandler.getInstance(path).writeLine("");
            FileHandler.getInstance(path).writeLine(a[i].getSetter().split("\\n"));
            FileHandler.getInstance(path).writeLine("");
        }
    }
    
    /**
     * The possible types used by a parameter.
     */
    public enum Type {
        Integer,
        Float,
        String,
        Boolean
    };
    
    /**
     * Represents a parameter.
     */
    public static class Parameter {
        public String group;
        public String identifier;
        public String description;
        public String storage;
        public Type type;
        public boolean needsReset;
        public Object value;
        
        /**
         * Generates a new instance of the class Parameter.
         * 
         * @param group The group of the parameter.
         * @param identifier The identifier of the parameter.
         * @param storage The identifier of the parameter in the system.
         * @param description The descirption of the parameter.
         * @param type The type of the value of the parameter.
         * @param needsReset Indicates whether the tracker has to be reseted or
         * not when the parameter has changed.
         * @param value The default value of the parameter.
         */
        public Parameter(String group, String identifier, String storage, String description, Type type, boolean needsReset, Object value) {
            this.group = group;
            this.identifier = identifier;
            this.storage = storage;
            this.description = description;
            this.type = type;
            this.needsReset = needsReset;
            this.value = value;
        }
        
        /**
         * Gets the declaration of a parameter based on the given type.
         * 
         * @param t The type of the parameter used.
         * 
         * @return The declaration of the parameter.
         */
        public String getDeclaration(Type t) {
            switch (t) {
                case Integer:
                    return "int";
                case Float:
                    return "float";
                case String:
                    return "String";
                case Boolean:
                    return "boolean";
            }
            return "?";
        }
        
        /**
         * Gets the definition of the internal get-method used by the class
         * Parameters.
         * 
         * @param t The type of the argument used.
         * 
         * @return The definition of the get-Method.
         */
        public String internalGetMethod(Type t) {
            switch (t) {
                case Integer:
                    return "getAsInteger";
                case Float:
                    return "getAsFloat";
                case String:
                    return "getAsString";
                case Boolean:
                    return "getAsBoolean";
            }
            return "?";
        }
        
        /**
         * Gets the definition of the get-method according to the given type.
         * 
         * @param t The type of the argument used.
         * 
         * @return The definition of the get-method.
         */
        public String getMethod(Type t) {
            switch (t) {
                case Integer:
                    return "getInt";
                case Float:
                    return "getFloat";
                case String:
                    return "get";
                case Boolean:
                    return "getBoolean";
            }
            return "?";
        }
        
        /**
         * Gets the definition of the put-method according to the given type.
         * 
         * @param t The type of the argument used.
         * 
         * @return The definition of the put-method.
         */
        public String putMethod(Type t) {
            switch (t) {
                case Integer:
                    return "putInt";
                case Float:
                    return "putFloat";
                case String:
                    return "put";
                case Boolean:
                    return "putBoolean";
            }
            return "?";
        }
        
        //protected int method = getPrefs().getInt("SpatioTemporalPatternTracker.method", Parameters.getInstance().getAsInteger(Parameters.METHOD));
        
        /**
         * Generates the defintion of a parameter.
         * 
         * @return The definition of a parameter.
         */
        public String getDefinition() {
            return "    protected " + this.getDeclaration(type) + " " + this.identifier + " = getPrefs()." + this.getMethod(type) + "(\"" + this.group + "." + this.identifier + "\", Parameters.getInstance()." + this.internalGetMethod(type) + "(Parameters." + this.storage + "));";      
        }
        
        //{setPropertyTooltip("method","Defines the method used to classify a cluster to a predefined pattern.");}
        
        /**
         * Generates the tooltip of a parameter.
         * 
         * @return The tooltip of a parameter.
         */
        public String getTooltip() {
            return "    {setPropertyTooltip(\"" + this.group + "\",\"" + this.identifier + "\",\"" + this.description + "\");}";
        }
        
        /*
        public int getMethod() {
            return this.method;
        }
        */
        
        /**
         * Generates the getter method of a parameter.
         * 
         * @return The getter method of a parameter.
         */
        public String getGetter() {
            String t = this.identifier.substring(0, 1).toUpperCase() + this.identifier.substring(1).toLowerCase();
            
            return "    public " + this.getDeclaration(type) + " get" + t + "() { \n" +
                   "        return this." + this.identifier + "; \n" +
                   "    } \n";
        }
        
        /*
        public void setMethod(final int method) {
            getPrefs().putInt("SpatioTemporalPatternTracker.method", method);
            //getSupport().firePropertyChange("nClusters",this.nClusters, nClusters);
            this.method = method;
        
            Parameters.getInstance().add(Parameters.METHOD, method);
        
            this.resetFilter();
        }
        */
        
        /**
         * Generates the setter method of a parameter.
         * 
         * @return The setter method of a parameter.
         */
        public String getSetter() {
            String t = this.identifier.substring(0, 1).toUpperCase() + this.identifier.substring(1).toLowerCase();
            
            String c = "    public void set" + t + "(final " + this.getDeclaration(type) + " " + this.identifier + ") { \n" +
                       "        getPrefs()." + this.putMethod(type) + "(\"" + this.group + "." + this.identifier + "\", " + this.identifier + "); \n" +
                       "        this." + this.identifier + " = " + this.identifier + "; \n" +
                       "        \n" +
                       "        Parameters.getInstance().add(Parameters." + this.storage + ", " + this.identifier + "); \n" +
                       "        this.tracker.updateListeners(); \n";
            if (this.needsReset) {
                return c +
                       "        \n" +
                       "        this.resetFilter(); \n" +
                       "    } \n";
            }
            return c + 
                   "    } \n";
        }
    }
    
    /**
     * Defines an ordering on the parameter by looking at their group and
     * their identifier.
     */
    public class ParameterComparator implements Comparator<Parameter> {

        @Override
        public int compare(Parameter o1, Parameter o2) {
            int c = o1.group.compareTo(o2.group);
            if (c != 0) return c;
            
            return o1.identifier.compareTo(o2.identifier);
        }
    }
    
    public static void main(String [] args) {
        Parameters.getInstance().generateFile("C:\\Users\\matthias\\Desktop\\out.txt");
    }
}

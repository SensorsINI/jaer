package ch.unizh.ini.jaer.config;


import java.util.Observer;

/** Base configuration interface */
public interface ConfigBase {

    /** Adds Observer to this config item
     * 
     * @param o the observer, which is notified when item changes value.
     */
    public void addObserver(Observer o);

    /** Returns name of config item
     * 
     * @return name
     */
    public String getName();

    /** Returns description
     * 
     * @return description string 
     */
    public String getDescription();
}

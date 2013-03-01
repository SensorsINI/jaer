package ch.unizh.ini.jaer.config;

/** Interface for a configuration boolean bit. */
public interface ConfigBit extends ConfigBase {

    public boolean isSet();

    public void set(boolean yes);
}

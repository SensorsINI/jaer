package ch.unizh.ini.jaer.config;

/** Interface for a configuration boolean bit. */
public interface ConfigBit extends ConfigBase {

	boolean isSet();

	void set(boolean yes);
}

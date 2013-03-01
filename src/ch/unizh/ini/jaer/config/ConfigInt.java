package ch.unizh.ini.jaer.config;

/** Interface for a configuration integer value. */
public interface ConfigInt extends ConfigBase {

    public int get();

    public void set(int v) throws IllegalArgumentException;
}

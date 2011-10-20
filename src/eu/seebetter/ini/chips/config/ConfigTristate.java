package eu.seebetter.ini.chips.config;

/** Interface for a Tristate configuration value */
public interface ConfigTristate extends ConfigBit {

    public boolean isHiZ();

    public void setHiZ(boolean yes);
}
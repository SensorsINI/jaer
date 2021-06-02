package ch.unizh.ini.jaer.config;

public interface ConfigInt extends ConfigBase {

	int get();

	void set(int v) throws IllegalArgumentException;
}

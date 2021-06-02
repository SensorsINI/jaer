package ch.unizh.ini.jaer.config;

import java.util.Observer;

/** Base configuration interface */
public interface ConfigBase {

	void addObserver(Observer o);

	String getName();

	String getDescription();
}

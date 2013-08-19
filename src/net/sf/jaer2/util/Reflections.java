package net.sf.jaer2.util;

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Set;

import net.sf.jaer2.chips.Chip;
import net.sf.jaer2.eventio.processors.EventProcessor;
import net.sf.jaer2.eventio.sinks.Sink;
import net.sf.jaer2.eventio.sources.Source;

import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

public final class Reflections {
	private static final ConfigurationBuilder config = new ConfigurationBuilder();
	static {
		Reflections.config.addUrls(ClasspathHelper.forPackage("net.sf.jaer2"));
		Reflections.config.addUrls(ClasspathHelper.forPackage("ch.unizh.ini.jaer2"));
	}

	private static final org.reflections.Reflections reflections = new org.reflections.Reflections(Reflections.config);

	public static <T> Set<Class<? extends T>> getSubClasses(final Class<T> clazz) {
		final Set<Class<? extends T>> classes = Reflections.reflections.getSubTypesOf(clazz);

		for (final Iterator<Class<? extends T>> iter = classes.iterator(); iter.hasNext();) {
			// Only consider non-abstract sub-classes, that can be instantiated.
			if (Modifier.isAbstract(iter.next().getModifiers())) {
				iter.remove();
			}
		}

		return classes;
	}

	/** List of classes extending EventProcessor. */
	public static final Set<Class<? extends EventProcessor>> eventProcessorTypes = Reflections
		.getSubClasses(EventProcessor.class);

	/** List of classes extending Sink. */
	public static final Set<Class<? extends Source>> sourceTypes = Reflections.getSubClasses(Source.class);

	/** List of classes extending Chip. */
	public static final Set<Class<? extends Chip>> chipTypes = Reflections.getSubClasses(Chip.class);

	/** List of classes extending Sink. */
	public static final Set<Class<? extends Sink>> sinkTypes = Reflections.getSubClasses(Sink.class);
}

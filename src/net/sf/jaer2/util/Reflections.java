package net.sf.jaer2.util;

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Set;

public final class Reflections {
	public final static <T> Set<Class<? extends T>> getSubClasses(final Class<T> clazz) {
		final org.reflections.Reflections reflections = new org.reflections.Reflections("net.sf.jaer2");

		final Set<Class<? extends T>> classes = reflections.getSubTypesOf(clazz);

		for (final Iterator<Class<? extends T>> iter = classes.iterator(); iter.hasNext();) {
			// Only consider non-abstract sub-classes, that can be instantiated.
			if (Modifier.isAbstract(iter.next().getModifiers())) {
				iter.remove();
			}
		}

		return classes;
	}
}

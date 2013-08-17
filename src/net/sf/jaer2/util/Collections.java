package net.sf.jaer2.util;

import java.util.Collection;
import java.util.Iterator;

public final class Collections {
	public static <T> void replaceNonDestructive(final Collection<T> oldContent, final Collection<T> newContent) {
		// Replace with new data in a non-destructive way, by not touching
		// values that were already present.
		for (final Iterator<T> iter = oldContent.iterator(); iter.hasNext();) {
			final T element = iter.next();

			if (newContent.contains(element)) {
				newContent.remove(element);
			}
			else {
				iter.remove();
			}
		}

		// Add remaining values that weren't yet present.
		oldContent.addAll(newContent);
	}
}

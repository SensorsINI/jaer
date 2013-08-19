package net.sf.jaer2.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Collections {
	/**
	 * Replace the data in the first collection with the content of the second
	 * one, by draining the changes into it.
	 * This is done in such a way to preserve elements in the first collection
	 * that are also present in the second one, to minimize the number of
	 * changes to be done. Further, all changes are condensed into one call for
	 * removing deleted content, and one call for adding new one.
	 *
	 * @param oldContent
	 *            collection whose data shall be replaced by the second one.
	 * @param newContent
	 *            collection whose data shall replace the one in the first.
	 *            This collection will be empty (drained) after this call!
	 */
	public static <T> void replaceNonDestructive(final Collection<T> oldContent, final Collection<T> newContent) {
		// Replace with new data in a non-destructive way, by not touching
		// values that were already present.
		final List<T> removals = new ArrayList<>();

		for (final T element : oldContent) {
			if (newContent.contains(element)) {
				newContent.remove(element);
			}
			else {
				removals.add(element);
			}
		}

		// Remove all items that need to be deleted and add all the new ones in
		// only one call each.
		oldContent.removeAll(removals);
		oldContent.addAll(newContent);

		// Consume newContent fully.
		newContent.clear();
	}

	/**
	 * Update the data in the first collection with the content of the second
	 * one, making sure no duplicates are added.
	 * This is done in such a way to preserve elements in the first collection
	 * that are also present in the second one, to minimize the number of
	 * changes to be done. Further, all changes are condensed into one call for
	 * adding new content.
	 *
	 * @param oldContent
	 *            collection whose data shall be updated by the second one.
	 * @param newContent
	 *            collection whose data shall update the one in the first.
	 *            This collection will be empty (drained) after this call!
	 */
	public static <T> void updateNonDestructive(final Collection<T> oldContent, final Collection<T> newContent) {
		// Update with new data in a non-destructive way, by not touching
		// values that were already present.
		for (final T element : oldContent) {
			if (newContent.contains(element)) {
				newContent.remove(element);
			}
		}

		// Add all new items in only one call.
		oldContent.addAll(newContent);

		// Consume newContent fully.
		newContent.clear();
	}
}

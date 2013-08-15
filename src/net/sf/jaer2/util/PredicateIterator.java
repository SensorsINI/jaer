package net.sf.jaer2.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class PredicateIterator<E> implements Iterator<E> {
	private final Iterator<E> iterator;
	private E currentElement = null;

	public PredicateIterator(final Iterator<E> iter) {
		iterator = iter;
	}

	protected abstract boolean verifyPredicate(final E element);

	@Override
	public boolean hasNext() {
		if (currentElement != null) {
			return true;
		}

		while (iterator.hasNext()) {
			currentElement = iterator.next();

			if (verifyPredicate(currentElement)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public E next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}

		final E elem = currentElement;
		currentElement = null;
		return elem;
	}

	@Override
	public void remove() {
		iterator.remove();
	}
}

package net.sf.jaer2.util;

import java.lang.reflect.Array;
import java.util.Comparator;

public final class Arrays {
	@SuppressWarnings("unchecked")
	public static <T> T[] newArrayFromType(final Class<T> type, final int length) {
		return (T[]) Array.newInstance(type, length);
	}

	public static <T> T[] mergeArraysOfType(final Class<T> type, final T[] a, final T[] b, final Comparator<T> cmp) {
		final T[] merged = Arrays.newArrayFromType(type, (a.length + b.length));
		int i = 0, j = 0, k = 0;

		while ((i < a.length) && (j < b.length)) {
			if (cmp.compare(a[i], b[j]) < 0) {
				merged[k++] = a[i++];
			}
			else {
				merged[k++] = b[j++];
			}
		}

		if (i < a.length) {
			System.arraycopy(a, i, merged, k, a.length - i);
		}

		if (j < b.length) {
			System.arraycopy(b, j, merged, k, b.length - j);
		}

		return merged;
	}
}

package ch.unizh.ini.jaer.projects.elised.dynamicBuffer;

/**
 *
 * @author Susi
 * @param <T>: Type of objects to be removed
 */

// an interface for removal functions used with class Ringbuffer
public interface RemovalFunction<T> {
    public void remove(T t);
}

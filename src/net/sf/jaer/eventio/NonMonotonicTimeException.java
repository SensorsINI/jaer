package net.sf.jaer.eventio;

/**
 * class used to signal a backwards read from input stream
 */
public class NonMonotonicTimeException extends Exception {

    protected int timestamp, lastTimestamp;
    protected long position;

    public NonMonotonicTimeException() {
        super();
    }

    public NonMonotonicTimeException(String s) {
        super(s);
    }

    public NonMonotonicTimeException(int ts) {
        this.timestamp = ts;
    }

    /**
     * Constructs a new NonMonotonicTimeException
     *
     * @param readTs the timestamp just read
     * @param lastTs the previous timestamp
     */
    public NonMonotonicTimeException(int readTs, int lastTs) {
        this.timestamp = readTs;
        this.lastTimestamp = lastTs;
    }

    /**
     * Constructs a new NonMonotonicTimeException
     *
     * @param readTs the timestamp just read
     * @param lastTs the previous timestamp
     * @param position the current position in the stream
     */
    public NonMonotonicTimeException(int readTs, int lastTs, long position) {
        this.timestamp = readTs;
        this.lastTimestamp = lastTs;
        this.position = position;
    }

    public int getCurrentTimestamp() {
        return timestamp;
    }

    public int getPreviousTimestamp() {
        return lastTimestamp;
    }

    @Override
    public String toString() {
        if (getMessage() == null|| getMessage().isBlank()) {
            return String.format("NonMonotonicTimeException: position=%,d timestamp=%,d lastTimestamp=%,d jumps backwards by %,d",
                    position, timestamp, lastTimestamp, (timestamp - lastTimestamp));
        } else {
            return getMessage();
        }
    }
}

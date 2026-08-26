package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Validates SciDVS GAER timestamp order before a USB transfer reaches either
 * output sink. State is committed only after every complete word in a transfer
 * has passed validation.
 */
final class SciDVSGaerTimestampOrderGuard {

    private static final int TIMESTAMP_MASK = 0x8000;
    private static final int CODE_MASK = 0x7000;
    private static final int CODE_SHIFT = 12;
    private static final int DATA_MASK = 0x0FFF;
    private static final int TIMESTAMP_DATA_MASK = 0x7FFF;
    private static final int SPECIAL_CODE = 0;
    private static final int TIMESTAMP_RESET_DATA = 1;
    private static final int TIMESTAMP_WRAP_CODE = 7;
    private static final long TIMESTAMP_WRAP_QUANTUM = 0x8000L;

    private long lastTimestamp;
    private long wrapAdd;
    private long epoch;
    private boolean hasTimestamp;
    private ValidationException latchedFailure;

    /**
     * Validates the remaining bytes without changing the caller's buffer state
     * or contents.
     *
     * @param input one complete GAER USB transfer
     * @throws ValidationException if the transfer is malformed, timestamps move
     * backward within an epoch, or an earlier failure is still latched
     */
    synchronized void validate(final ByteBuffer input) {
        if (latchedFailure != null) {
            throw latchedFailure;
        }
        if (input == null) {
            fail("Null SciDVS GAER transfer", -1, null);
        }

        final int byteLength = input.remaining();
        if ((byteLength & 0x01) != 0) {
            fail("Odd-length SciDVS GAER transfer", byteLength - 1,
                    snapshot(input));
        }

        long candidateLastTimestamp = lastTimestamp;
        long candidateWrapAdd = wrapAdd;
        long candidateEpoch = epoch;
        boolean candidateHasTimestamp = hasTimestamp;
        final ShortBuffer words = input.duplicate()
                .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

        for (int wordIndex = 0; wordIndex < words.limit(); wordIndex++) {
            final int word = words.get(wordIndex) & 0xFFFF;
            final int byteOffset = wordIndex * Short.BYTES;
            if ((word & TIMESTAMP_MASK) != 0) {
                final long expandedTimestamp
                        = candidateWrapAdd + (word & TIMESTAMP_DATA_MASK);
                if (candidateHasTimestamp
                        && expandedTimestamp < candidateLastTimestamp) {
                    fail(decreaseMessage(candidateLastTimestamp,
                            expandedTimestamp, candidateEpoch), byteOffset,
                            snapshot(input));
                }
                candidateLastTimestamp = expandedTimestamp;
                candidateHasTimestamp = true;
                continue;
            }

            final int code = (word & CODE_MASK) >>> CODE_SHIFT;
            final int data = word & DATA_MASK;
            if (code == SPECIAL_CODE && data == TIMESTAMP_RESET_DATA) {
                candidateWrapAdd = 0L;
                candidateLastTimestamp = 0L;
                candidateHasTimestamp = true;
                candidateEpoch++;
                continue;
            }
            if (code == TIMESTAMP_WRAP_CODE) {
                try {
                    candidateWrapAdd = Math.addExact(candidateWrapAdd,
                            Math.multiplyExact(TIMESTAMP_WRAP_QUANTUM,
                                    (long) data));
                } catch (final ArithmeticException overflow) {
                    fail("SciDVS GAER timestamp wrap overflow", byteOffset,
                            snapshot(input));
                }
                if (candidateHasTimestamp
                        && candidateWrapAdd < candidateLastTimestamp) {
                    fail(decreaseMessage(candidateLastTimestamp,
                            candidateWrapAdd, candidateEpoch), byteOffset,
                            snapshot(input));
                }
                candidateLastTimestamp = candidateWrapAdd;
                candidateHasTimestamp = true;
            }
        }

        lastTimestamp = candidateLastTimestamp;
        wrapAdd = candidateWrapAdd;
        epoch = candidateEpoch;
        hasTimestamp = candidateHasTimestamp;
    }

    synchronized boolean isFaultLatched() {
        return latchedFailure != null;
    }

    /** Clears all prior state only after the reader-owned restart barrier. */
    synchronized void clearAfterOwnedRestartAndReset() {
        latchedFailure = null;
        wrapAdd = 0L;
        lastTimestamp = 0L;
        hasTimestamp = true;
        epoch++;
    }

    synchronized long getLastTimestamp() {
        return lastTimestamp;
    }

    synchronized long getWrapAdd() {
        return wrapAdd;
    }

    synchronized long getEpoch() {
        return epoch;
    }

    private static String decreaseMessage(final long previous,
            final long current, final long currentEpoch) {
        return "Non-monotonic SciDVS GAER timestamp in epoch "
                + currentEpoch + ": previous=" + previous
                + ", current=" + current;
    }

    private void fail(final String message, final int byteOffset,
            final byte[] transferSnapshot) {
        latchedFailure = new ValidationException(message, byteOffset,
                transferSnapshot);
        throw latchedFailure;
    }

    private static byte[] snapshot(final ByteBuffer input) {
        final ByteBuffer copy = input.duplicate();
        final byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    /** Unchecked, latched diagnostic for a rejected transfer. */
    static final class ValidationException extends RuntimeException {

        private final int byteOffset;
        private final byte[] transferSnapshot;

        ValidationException(final String message, final int byteOffset,
                final byte[] transferSnapshot) {
            super(message + " at byte offset " + byteOffset);
            this.byteOffset = byteOffset;
            this.transferSnapshot = transferSnapshot == null
                    ? null : transferSnapshot.clone();
        }

        int getByteOffset() {
            return byteOffset;
        }

        byte[] getTransferSnapshot() {
            return transferSnapshot == null ? null : transferSnapshot.clone();
        }
    }
}

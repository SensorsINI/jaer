package net.sf.jaer2.viewer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.media.opengl.GL;

import com.jogamp.common.nio.Buffers;

public class BufferWorks {
	public static enum BUFFER_FORMATS {
		BYTE,
		BYTE_NOALPHA,
		FLOAT,
		FLOAT_NOALPHA,
	}

	private final int XLEN;
	private final int YLEN;
	private final BUFFER_FORMATS format;

	public BufferWorks(final int x, final int y, final BUFFER_FORMATS f) {
		XLEN = x;
		YLEN = y;
		format = f;

		if (format == BUFFER_FORMATS.BYTE) {
			colorsBufferByte = Buffers.newDirectByteBuffer(4 * XLEN * YLEN);
			colorsBufferFloat = null;
		}
		else if (format == BUFFER_FORMATS.BYTE_NOALPHA) {
			colorsBufferByte = Buffers.newDirectByteBuffer(3 * XLEN * YLEN);
			colorsBufferFloat = null;
		}
		else if (format == BUFFER_FORMATS.FLOAT) {
			colorsBufferByte = null;
			colorsBufferFloat = Buffers.newDirectFloatBuffer(4 * XLEN * YLEN);
		}
		else if (format == BUFFER_FORMATS.FLOAT_NOALPHA) {
			colorsBufferByte = null;
			colorsBufferFloat = Buffers.newDirectFloatBuffer(3 * XLEN * YLEN);
		}
		else {
			colorsBufferByte = null;
			colorsBufferFloat = null;

			throw new IllegalArgumentException("Invalid buffer type!");
		}
	}

	private static final byte[] RED_B = new byte[] { (byte) 0xFF, 0, 0, (byte) 0xFF };
	private static final byte[] GREEN_B = new byte[] { 0, (byte) 0x80, 0, (byte) 0xFF };
	private static final byte[] BLUE_B = new byte[] { 0, 0, (byte) 0xFF, (byte) 0xFF };
	private static final byte[] YELLOW_B = new byte[] { (byte) 0xFF, (byte) 0xFF, 0, (byte) 0xFF };

	private final ByteBuffer colorsBufferByte;

	private static final float[] RED_F = new float[] { 1, 0, 0, 1 };
	private static final float[] GREEN_F = new float[] { 0, 0.5f, 0, 1 };
	private static final float[] BLUE_F = new float[] { 0, 0, 1, 1 };
	private static final float[] YELLOW_F = new float[] { 1, 1, 0, 1 };

	private final FloatBuffer colorsBufferFloat;

	private void updateBytes() {
		// Reset buffer position
		colorsBufferByte.position(0);

		// Get first pixel color for switch
		final byte[] firstPixel = new byte[] { 0x00, 0x00, 0x00, (byte) 0xFF };
		colorsBufferByte.get(firstPixel, 0, 3);

		// Reset buffer position
		colorsBufferByte.position(0);

		// Populate colors
		if (Arrays.equals(firstPixel, BufferWorks.RED_B)) {
			for (int y = 0; y < YLEN; y++) {
				for (int x = 0; x < XLEN; x += 2) {
					if ((y % 2) == 0) {
						colorsBufferByte.put(BufferWorks.BLUE_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(BufferWorks.YELLOW_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
					else {
						colorsBufferByte.put(BufferWorks.GREEN_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(BufferWorks.RED_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
				}
			}
		}
		else {
			for (int y = 0; y < YLEN; y++) {
				for (int x = 0; x < XLEN; x += 2) {
					if ((y % 2) == 0) {
						colorsBufferByte.put(BufferWorks.RED_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(BufferWorks.GREEN_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
					else {
						colorsBufferByte.put(BufferWorks.YELLOW_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(BufferWorks.BLUE_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
				}
			}
		}

		// Reset buffer position
		colorsBufferByte.position(0);
	}

	private void updateFloats() {
		// Reset buffer position
		colorsBufferFloat.position(0);

		// Get first pixel color for switch
		final float[] firstPixel = new float[] { 0, 0, 0, 1 };
		colorsBufferFloat.get(firstPixel, 0, 3);

		// Reset buffer position
		colorsBufferFloat.position(0);

		// Populate colors
		if (Arrays.equals(firstPixel, BufferWorks.RED_F)) {
			for (int y = 0; y < YLEN; y++) {
				for (int x = 0; x < XLEN; x += 2) {
					if ((y % 2) == 0) {
						colorsBufferFloat.put(BufferWorks.BLUE_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
						colorsBufferFloat.put(BufferWorks.YELLOW_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
					}
					else {
						colorsBufferFloat.put(BufferWorks.GREEN_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
						colorsBufferFloat.put(BufferWorks.RED_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
					}
				}
			}
		}
		else {
			for (int y = 0; y < YLEN; y++) {
				for (int x = 0; x < XLEN; x += 2) {
					if ((y % 2) == 0) {
						colorsBufferFloat.put(BufferWorks.RED_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
						colorsBufferFloat.put(BufferWorks.GREEN_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
					}
					else {
						colorsBufferFloat.put(BufferWorks.YELLOW_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
						colorsBufferFloat.put(BufferWorks.BLUE_F, 0, (format == BUFFER_FORMATS.FLOAT) ? (4) : (3));
					}
				}
			}
		}

		// Reset buffer position
		colorsBufferFloat.position(0);
	}

	public void update() {
		if ((format == BUFFER_FORMATS.BYTE) || (format == BUFFER_FORMATS.BYTE_NOALPHA)) {
			updateBytes();
		}
		else if ((format == BUFFER_FORMATS.FLOAT) || (format == BUFFER_FORMATS.FLOAT_NOALPHA)) {
			updateFloats();
		}
	}

	public Buffer getBuffer() {
		if ((format == BUFFER_FORMATS.BYTE) || (format == BUFFER_FORMATS.BYTE_NOALPHA)) {
			return colorsBufferByte;
		}
		else if ((format == BUFFER_FORMATS.FLOAT) || (format == BUFFER_FORMATS.FLOAT_NOALPHA)) {
			return colorsBufferFloat;
		}

		return null;
	}

	public BUFFER_FORMATS getFormat() {
		return format;
	}

	public int getGLFormat() {
		if ((format == BUFFER_FORMATS.BYTE) || (format == BUFFER_FORMATS.BYTE_NOALPHA)) {
			return GL.GL_UNSIGNED_BYTE;
		}
		else if ((format == BUFFER_FORMATS.FLOAT) || (format == BUFFER_FORMATS.FLOAT_NOALPHA)) {
			return GL.GL_FLOAT;
		}

		return 0;
	}

	public int getGLColorFormat() {
		if ((format == BUFFER_FORMATS.BYTE) || (format == BUFFER_FORMATS.FLOAT)) {
			return GL.GL_RGBA;
		}
		else if ((format == BUFFER_FORMATS.BYTE_NOALPHA) || (format == BUFFER_FORMATS.FLOAT_NOALPHA)) {
			return GL.GL_RGB;
		}

		return 0;
	}
}

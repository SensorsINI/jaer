package net.sf.jaer2.viewer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.media.opengl.GL;

import com.jogamp.common.nio.Buffers;

public class BufferWorks {
	public static enum BUFFER_FORMATS {
		BYTE,
		BYTE_NOALPHA,
	}

	private final int XLEN;
	private final int YLEN;
	private final BUFFER_FORMATS format;
	private final int color;

	public BufferWorks(final int x, final int y, final BUFFER_FORMATS f, final int c) {
		XLEN = x;
		YLEN = y;
		format = f;
		color = c;

		if (format == BUFFER_FORMATS.BYTE) {
			colorsBufferByte = Buffers.newDirectByteBuffer(4 * XLEN * YLEN);
		}
		else if (format == BUFFER_FORMATS.BYTE_NOALPHA) {
			colorsBufferByte = Buffers.newDirectByteBuffer(3 * XLEN * YLEN);
		}
		else {
			colorsBufferByte = null;

			throw new IllegalArgumentException("Invalid buffer type!");
		}
	}

	private static final byte[] RED_B = new byte[] { (byte) 0xFF, 0, 0, (byte) 0xFF };
	private static final byte[] GREEN_B = new byte[] { 0, (byte) 0x80, 0, (byte) 0xFF };
	private static final byte[] BLUE_B = new byte[] { 0, 0, (byte) 0xFF, (byte) 0xFF };
	private static final byte[] YELLOW_B = new byte[] { (byte) 0xFF, (byte) 0xFF, 0, (byte) 0xFF };
	private static final byte[] WHITE_B = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

	private final ByteBuffer colorsBufferByte;

	private void updateBytes() {
		// Reset buffer position
		colorsBufferByte.position(0);

		// Get first pixel color for switch
		final byte[] firstPixel = new byte[] { 0x00, 0x00, 0x00, (byte) 0xFF };
		colorsBufferByte.get(firstPixel, 0, 3);

		// Reset buffer position
		colorsBufferByte.position(0);

		// Choose contrast color
		final byte[] colorPixel;
		switch (color) {
			case 0:
				colorPixel = BufferWorks.RED_B;
				break;

			case 1:
				colorPixel = BufferWorks.GREEN_B;
				break;

			case 2:
				colorPixel = BufferWorks.BLUE_B;
				break;

			case 3:
				colorPixel = BufferWorks.YELLOW_B;
				break;

			default:
				colorPixel = BufferWorks.WHITE_B;
				break;
		}

		// Populate colors
		if (Arrays.equals(firstPixel, BufferWorks.WHITE_B)) {
			for (int y = 0; y < YLEN; y++) {
				for (int x = 0; x < XLEN; x += 2) {
					if ((y % 2) == 0) {
						colorsBufferByte.put(colorPixel, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(BufferWorks.WHITE_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
					else {
						colorsBufferByte.put(BufferWorks.WHITE_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(colorPixel, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
				}
			}
		}
		else {
			for (int y = 0; y < YLEN; y++) {
				for (int x = 0; x < XLEN; x += 2) {
					if ((y % 2) == 0) {
						colorsBufferByte.put(BufferWorks.WHITE_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(colorPixel, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
					else {
						colorsBufferByte.put(colorPixel, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
						colorsBufferByte.put(BufferWorks.WHITE_B, 0, (format == BUFFER_FORMATS.BYTE) ? (4) : (3));
					}
				}
			}
		}

		// Reset buffer position
		colorsBufferByte.position(0);
	}

	public void update() {
		updateBytes();
	}

	public Buffer getBuffer() {
		return colorsBufferByte;

	}

	public BUFFER_FORMATS getFormat() {
		return format;
	}

	@SuppressWarnings("static-method")
	public int getGLFormat() {
		return GL.GL_UNSIGNED_BYTE;

	}

	public int getGLColorFormat() {
		if (format == BUFFER_FORMATS.BYTE_NOALPHA) {
			return GL.GL_RGB;
		}

		return GL.GL_RGBA;
	}
}

/*
 * @(#)AppleRLEEncoder.java  1.1.1  2011-01-17
 *
 * Copyright Â© 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package net.sf.jaer.util.avioutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;


/**
 * Implements the run length encoding of the Microsoft RLE format.
 * <p>
 * Each line of a frame is compressed individually. A line consists of two-byte
 * op-codes optionally followed by data. The end of the line is marked with the
 * EOL op-code.
 * <p>
 * The following op-codes are supported:
 * <ul>
 * <li>{@code 0x00 0x00} <br>
 * Marks the end of a line.</li>
 * 
 * <li>{@code  0x00 0x01} <br>
 * Marks the end of the bitmap.</li>
 * 
 * <li>{@code 0x00 0x02 x y} <br>
 * Marks a delta (skip). {@code x} and {@code y} indicate the horizontal and
 * vertical offset from the current position. {@code x} and {@code y} are
 * unsigned 8-bit values.</li>
 * 
 * <li>{@code 0x00 n data n} 0x00?} <br>
 * Marks a literal run. {@code n} gives the number of data bytes that follow.
 * {@code n} must be between 3 and 255. If n is odd, a pad byte with the value
 * 0x00 must be added.</li>
 * <li>{@code n data} <br>
 * Marks a repetition. {@code n} gives the number of times the data byte is
 * repeated. {@code n} must be between 1 and 255.</li>
 * </ul>
 * Example:
 * 
 * <pre>
 * Compressed data         Expanded data
 * 
 * 03 04                   04 04 04
 * 05 06                   06 06 06 06 06
 * 00 03 45 56 67 00       45 56 67
 * 02 78                   78 78
 * 00 02 05 01             Move 5 right and 1 down
 * 02 78                   78 78
 * 00 00                   End of line
 * 09 1E                   1E 1E 1E 1E 1E 1E 1E 1E 1E
 * 00 01                   End of RLE bitmap
 * </pre>
 * 
 * References:<br/>
 * <a
 * href="http://wiki.multimedia.cx/index.php?title=Microsoft_RLE">http://wiki.
 * multimedia.cx/index.php?title=Microsoft_RLE</a><br>
 * 
 * @author Werner Randelshofer
 * @version 1.1.1 2011-01-17 Removes unused imports. <br>
 *          1.1 2011-01-07 Improves performance. <br>
 *          1.0 2011-01-05 Created.
 */
public class MicrosoftRLEEncoder {

	private final SeekableByteArrayOutputStream tempSeek = new SeekableByteArrayOutputStream();
	private final DataChunkOutputStream temp = new DataChunkOutputStream(
			tempSeek);

	/**
	 * Encodes a 8-bit key frame.
	 * 
	 * @param temp
	 *            The output stream. Must be set to Big-Endian.
	 * @param data
	 *            The image data.
	 * @param offset
	 *            The offset to the first pixel in the data array.
	 * @param length
	 *            The width of the image in data elements.
	 * @param step
	 *            The number to add to offset to get to the next scanline.
	 */
	public void writeKey8(OutputStream out, byte[] data, int offset,
			int length, int step, int height) throws IOException {
		tempSeek.reset();
		int ymax = offset + height * step;
		int upsideDown = ymax - step + offset;

		// Encode each scanline separately
		for (int y = offset; y < ymax; y += step) {
			int xy = upsideDown - y;
			int xymax = xy + length;

			int literalCount = 0;
			int repeatCount = 0;
			for (; xy < xymax; ++xy) {
				// determine repeat count
				byte v = data[xy];
				for (repeatCount = 0; xy < xymax && repeatCount < 255; ++xy, ++repeatCount) {
					if (data[xy] != v) {
						break;
					}
				}
				xy -= repeatCount;
				if (repeatCount < 3) {
					literalCount++;
					if (literalCount == 254) {
						temp.write(0);
						temp.write(literalCount); // Literal OP-code
						temp.write(data, xy - literalCount + 1, literalCount);
						literalCount = 0;
					}
				} else {
					if (literalCount > 0) {
						if (literalCount < 3) {
							for (; literalCount > 0; --literalCount) {
								temp.write(1); // Repeat OP-code
								temp.write(data[xy - literalCount]);
							}
						} else {
							temp.write(0);
							temp.write(literalCount); // Literal OP-code
							temp.write(data, xy - literalCount, literalCount);
							if (literalCount % 2 == 1) {
								temp.write(0); // pad byte
							}
							literalCount = 0;
						}
					}
					temp.write(repeatCount); // Repeat OP-code
					temp.write(v);
					xy += repeatCount - 1;
				}
			}

			// flush literal run
			if (literalCount > 0) {
				if (literalCount < 3) {
					for (; literalCount > 0; --literalCount) {
						temp.write(1); // Repeat OP-code
						temp.write(data[xy - literalCount]);
					}
				} else {
					temp.write(0);
					temp.write(literalCount);
					temp.write(data, xy - literalCount, literalCount);
					if (literalCount % 2 == 1) {
						temp.write(0); // pad byte
					}
				}
				literalCount = 0;
			}

			temp.write(0);
			temp.write(0x0000);// End of line
		}
		temp.write(0);
		temp.write(0x0001);// End of bitmap
		tempSeek.toOutputStream(out);
	}

	/**
	 * Encodes a 8-bit delta frame.
	 * 
	 * @param temp
	 *            The output stream. Must be set to Big-Endian.
	 * @param data
	 *            The image data.
	 * @param prev
	 *            The image data of the previous frame.
	 * @param offset
	 *            The offset to the first pixel in the data array.
	 * @param length
	 *            The width of the image in data elements.
	 * @param step
	 *            The number to add to offset to get to the next scanline.
	 */
	public void writeDelta8(OutputStream out, byte[] data, byte[] prev,
			int offset, int length, int step, int height) throws IOException {

		tempSeek.reset();
		// Determine whether we can skip lines at the beginning
		int ymin;
		int ymax = offset + height * step;
		int upsideDown = ymax - step + offset;
		scanline: for (ymin = offset; ymin < ymax; ymin += step) {
			int xy = upsideDown - ymin;
			int xymax = xy + length;
			for (; xy < xymax; ++xy) {
				if (data[xy] != prev[xy]) {
					break scanline;
				}
			}
		}

		if (ymin == ymax) {
			// => Frame is identical to previous one
			temp.write(0);
			temp.write(0x0001); // end of bitmap
			return;
		}

		if (ymin > offset) {
			int verticalOffset = ymin / step;
			while (verticalOffset > 255) {
				temp.write(0);
				temp.write(0x0002); // Skip OP-code
				temp.write(0); // horizontal offset
				temp.write(255); // vertical offset
				verticalOffset -= 255;
			}
			if (verticalOffset == 1) {
				temp.write(0);
				temp.write(0x0000); // End of line OP-code
			} else {
				temp.write(0);
				temp.write(0x0002); // Skip OP-code
				temp.write(0); // horizontal offset
				temp.write(verticalOffset); // vertical offset
			}
		}

		// Determine whether we can skip lines at the end
		scanline: for (; ymax > ymin; ymax -= step) {
			int xy = upsideDown - ymax + step;
			int xymax = xy + length;
			for (; xy < xymax; ++xy) {
				if (data[xy] != prev[xy]) {
					break scanline;
				}
			}
		}
		// System.out.println("MicrosoftRLEEncoder ymin:" + ymin / step +
		// " ymax" + ymax / step);

		// Encode each scanline
		int verticalOffset = 0;
		for (int y = ymin; y < ymax; y += step) {
			int xy = upsideDown - y;
			int xymax = xy + length;

			// determine skip count
			int skipCount = 0;
			for (; xy < xymax; ++xy, ++skipCount) {
				if (data[xy] != prev[xy]) {
					break;
				}
			}
			if (skipCount == length) {
				// => the entire line can be skipped
				++verticalOffset;
				if (verticalOffset == 255) {
					temp.write(0);
					temp.write(0x0002); // Skip OP-code
					temp.write(0); // horizontal offset
					temp.write(255); // vertical offset
					verticalOffset = 0;
				}
				continue;
			}

			if (verticalOffset > 0 || skipCount > 0) {
				if (verticalOffset == 1 && skipCount == 0) {
					temp.write(0);
					temp.write(0x0000); // End of line OP-code
				} else {
					temp.write(0);
					temp.write(0x0002); // Skip OP-code
					temp.write(Math.min(255, skipCount)); // horizontal offset
					skipCount -= 255;
					temp.write(verticalOffset); // vertical offset
				}
				verticalOffset = 0;
			}
			while (skipCount > 0) {
				temp.write(0);
				temp.write(0x0002); // Skip OP-code
				temp.write(Math.min(255, skipCount)); // horizontal offset
				temp.write(0); // vertical offset
				skipCount -= 255;
			}

			int literalCount = 0;
			int repeatCount = 0;
			for (; xy < xymax; ++xy) {
				// determine skip count
				for (skipCount = 0; xy < xymax; ++xy, ++skipCount) {
					if (data[xy] != prev[xy]) {
						break;
					}
				}
				xy -= skipCount;

				// determine repeat count
				byte v = data[xy];
				for (repeatCount = 0; xy < xymax && repeatCount < 255; ++xy, ++repeatCount) {
					if (data[xy] != v) {
						break;
					}
				}
				xy -= repeatCount;

				if (skipCount < 4 && xy + skipCount < xymax && repeatCount < 3) {
					literalCount++;
					if (literalCount == 254) {
						temp.write(0);
						temp.write(literalCount); // Literal OP-code
						temp.write(data, xy - literalCount + 1, literalCount);
						literalCount = 0;
					}
				} else {
					if (literalCount > 0) {
						if (literalCount < 3) {
							for (; literalCount > 0; --literalCount) {
								temp.write(1); // Repeat OP-code
								temp.write(data[xy - literalCount]);
							}
						} else {
							temp.write(0);
							temp.write(literalCount);
							temp.write(data, xy - literalCount, literalCount);
							if (literalCount % 2 == 1) {
								temp.write(0); // pad byte
							}
						}
						literalCount = 0;
					}
					if (xy + skipCount == xymax) {
						// => we can skip until the end of the line without
						// having to write an op-code
						xy += skipCount - 1;
					} else if (skipCount >= repeatCount) {
						while (skipCount > 255) {
							temp.write(0);
							temp.write(0x0002); // Skip OP-code
							temp.write(255);
							temp.write(0);
							xy += 255;
							skipCount -= 255;
						}
						temp.write(0);
						temp.write(0x0002); // Skip OP-code
						temp.write(skipCount);
						temp.write(0);
						xy += skipCount - 1;
					} else {
						temp.write(repeatCount); // Repeat OP-code
						temp.write(v);
						xy += repeatCount - 1;
					}
				}
			}

			// flush literal run
			if (literalCount > 0) {
				if (literalCount < 3) {
					for (; literalCount > 0; --literalCount) {
						temp.write(1); // Repeat OP-code
						temp.write(data[xy - literalCount]);
					}
				} else {
					temp.write(0);
					temp.write(literalCount);
					temp.write(data, xy - literalCount, literalCount);
					if (literalCount % 2 == 1) {
						temp.write(0); // pad byte
					}
				}
			}

			temp.write(0);
			temp.write(0x0000); // End of line OP-code
		}

		temp.write(0);
		temp.write(0x0001);// End of bitmap
		tempSeek.toOutputStream(out);
	}

	public static void main(String[] args) {
		byte[] data = {//
		8, 2, 3, 4, 4, 3, 7, 7, 7, 8,//
				8, 1, 1, 1, 1, 2, 7, 7, 7, 8,//
				8, 0, 2, 0, 0, 0, 7, 7, 7, 8,//
				8, 2, 2, 3, 4, 4, 7, 7, 7, 8,//
				8, 1, 4, 4, 4, 5, 7, 7, 7, 8 };

		byte[] prev = {//
		8, 3, 3, 3, 3, 3, 7, 7, 7, 8,//
				8, 1, 1, 1, 1, 1, 7, 7, 7, 8, //
				8, 5, 5, 5, 5, 0, 7, 7, 7, 8,//
				8, 2, 2, 0, 0, 0, 7, 7, 7, 8,//
				8, 2, 0, 0, 0, 5, 7, 7, 7, 8 };
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		DataChunkOutputStream out = new DataChunkOutputStream(buf);
		MicrosoftRLEEncoder enc = new MicrosoftRLEEncoder();

		try {
			enc.writeDelta8(out, data, prev, 1, 8, 10, 5);
			// enc.writeKey8(out, data, 1, 8, 10,5);
			out.close();

			byte[] result = buf.toByteArray();
			System.out.println("size:" + result.length);
			System.out.println(Arrays.toString(result));
			System.out.print("0x [");

			for (int i = 0; i < result.length; i++) {
				if (i != 0) {
					System.out.print(',');
				}
				String hex = "00" + Integer.toHexString(result[i]);
				System.out.print(hex.substring(hex.length() - 2));
			}
			System.out.println(']');

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
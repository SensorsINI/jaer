/**
 * @(#)AVIOutputStream.java  1.5.1  2011-01-17
 *
 * Copyright (c) 2008-2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package net.sf.jaer.util.avioutput;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;


/**
 * This class supports writing of images into an AVI 1.0 video file.
 * <p>
 * The images are written as video frames.
 * <p>
 * Video frames can be encoded with one of the following formats:
 * <ul>
 * <li>JPEG</li>
 * <li>PNG</li>
 * <li>RAW</li>
 * <li>RLE</li>
 * </ul>
 * All frames must have the same format. When JPG is used each frame can have an
 * individual encoding quality.
 * <p>
 * All frames in an AVI file must have the same duration. The duration can be
 * set by setting an appropriate pair of values using methods
 * {@link #setFrameRate} and {@link #setTimeScale}.
 * <p>
 * The length of an AVI 1.0 file is limited to 1 GB. This class supports lengths
 * of up to 4 GB, but such files may not work on all players.
 * <p>
 * For detailed information about the AVI RIFF file format see:<br>
 * <a
 * href="http://msdn.microsoft.com/en-us/library/ms779636.aspx">msdn.microsoft
 * .com AVI RIFF</a><br>
 * <a href="http://www.microsoft.com/whdc/archive/fourcc.mspx">www.microsoft.com
 * FOURCC for Video Compression</a><br>
 * <a href="http://www.saettler.com/RIFFMCI/riffmci.html">www.saettler.com
 * RIFF</a><br>
 * 
 * @author Werner Randelshofer
 * @version 1.5.1 2011-01-17 Fixes unintended closing of output stream.. <br>
 *          1.5 2011-01-06 Adds support for RLE 8-bit video format. <br>
 *          1.4 2011-01-04 Adds support for RAW 4-bit and 8-bit video format.
 *          Fixes offsets in "idx1" chunk. <br>
 *          1.3.2 2010-12-27 File size limit is 1 GB. <br>
 *          1.3.1 2010-07-19 Fixes seeking and calculation of offsets. <br>
 *          1.3 2010-07-08 Adds constructor with ImageOutputStream. Added method
 *          getVideoDimension(). <br>
 *          1.2 2009-08-29 Adds support for RAW video format. <br>
 *          1.1 2008-08-27 Fixes computation of dwMicroSecPerFrame in avih
 *          chunk. Changed the API to reflect that AVI works with frame rates
 *          instead of with frame durations. <br>
 *          1.0.1 2008-08-13 Uses FourCC "MJPG" instead of "jpg " for JPG
 *          encoded video. <br>
 *          1.0 2008-08-11 Created.
 */
public class AVIOutputStream implements VideoFrameWriterInterface {

	/**
	 * Underlying output stream.
	 */
	private final ImageOutputStream out;
	/**
	 * The offset of the QuickTime stream in the underlying ImageOutputStream.
	 * Normally this is 0 unless the underlying stream already contained data
	 * when it was passed to the constructor.
	 */
	private final long streamOffset;
	/** Previous frame for delta compression. */
	private Object previousData;

	/**
	 * Supported video encodings.
	 */
	public static enum VideoFormat {

		RAW, RLE, JPG, PNG;
	}

	/**
	 * Current video formats.
	 */
	private final VideoFormat videoFormat;
	/**
	 * Quality of JPEG encoded video frames.
	 */
	private float quality = 0.9f;
	/**
	 * Creation time of the movie output stream.
	 */
	private Date creationTime;
	/**
	 * Width of the video frames. All frames must have the same width. The value
	 * -1 is used to mark unspecified width.
	 */
	private int imgWidth = -1;
	/**
	 * Height of the video frames. All frames must have the same height. The
	 * value -1 is used to mark unspecified height.
	 */
	private int imgHeight = -1;
	/** Number of bits per pixel. */
	private int imgDepth = 24;
	/** Index color model for RAW_RGB4 and RAW_RGB8 formats. */
	private IndexColorModel palette;
	private IndexColorModel previousPalette;
	/** Video encoder. */
	private MicrosoftRLEEncoder encoder;
	/**
	 * The timeScale of the movie.
	 * <p>
	 * Used with frameRate to specify the time scale that this stream will use.
	 * Dividing frameRate by timeScale gives the number of samples per second.
	 * For video streams, this is the frame rate. For audio streams, this rate
	 * corresponds to the time needed to play nBlockAlign bytes of audio, which
	 * for PCM audio is the just the sample rate.
	 */
	private int timeScale = 1;
	/**
	 * The frameRate of the movie in timeScale units.
	 * <p>
	 * 
	 * @see timeScale
	 */
	private int frameRate = 30;
	/** Interval between keyframes. */
	private final int syncInterval = 30;

	/**
	 * The states of the movie output stream.
	 */
	private static enum States {

		STARTED, FINISHED, CLOSED;
	}

	/**
	 * The current state of the movie output stream.
	 */
	private States state = States.FINISHED;

	/**
	 * AVI stores media data in samples. A sample is a single element in a
	 * sequence of time-ordered data.
	 */
	private static class Sample {

		String chunkType;
		/**
		 * Offset of the sample relative to the start of the AVI file.
		 */
		long offset;
		/** Data length of the sample. */
		long length;
		/**
		 * The duration of the sample in time scale units.
		 */
		int duration;
		/** Whether the sample is a sync-sample. */
		boolean isSync;

		/**
		 * Creates a new sample.
		 * 
		 * @param duration
		 * @param offset
		 * @param length
		 */
		public Sample(String chunkId, int duration, long offset, long length,
				boolean isSync) {
			this.chunkType = chunkId;
			this.duration = duration;
			this.offset = offset;
			this.length = length;
			this.isSync = isSync;
		}
	}

	/**
	 * List of video frames.
	 */
	private final LinkedList<Sample> videoFrames;
	/**
	 * This chunk holds the whole AVI content.
	 */
	private CompositeChunk aviChunk;
	/**
	 * This chunk holds the movie frames.
	 */
	private CompositeChunk moviChunk;
	/**
	 * This chunk holds the AVI Main Header.
	 */
	FixedSizeDataChunk avihChunk;
	/**
	 * This chunk holds the AVI Stream Header.
	 */
	FixedSizeDataChunk strhChunk;
	/**
	 * This chunk holds the AVI Stream Format Header.
	 */
	FixedSizeDataChunk strfChunk;

	/**
	 * Chunk base class.
	 */
	private abstract class Chunk {

		/**
		 * The chunkType of the chunk. A String with the length of 4 characters.
		 */
		protected String chunkType;
		/**
		 * The offset of the chunk relative to the start of the
		 * ImageOutputStream.
		 */
		protected long offset;

		/**
		 * Creates a new Chunk at the current position of the ImageOutputStream.
		 * 
		 * @param chunkType
		 *            The chunkType of the chunk. A string with a length of 4
		 *            characters.
		 */
		public Chunk(String chunkType) throws IOException {
			this.chunkType = chunkType;
			offset = getRelativeStreamPosition();
		}

		/**
		 * Writes the chunk to the ImageOutputStream and disposes it.
		 */
		public abstract void finish() throws IOException;

		/**
		 * Returns the size of the chunk including the size of the chunk header.
		 * 
		 * @return The size of the chunk.
		 */
		public abstract long size();
	}

	/**
	 * A CompositeChunk contains an ordered list of Chunks.
	 */
	private class CompositeChunk extends Chunk {

		/**
		 * The type of the composite. A String with the length of 4 characters.
		 */
		protected String compositeType;
		private final LinkedList<Chunk> children;
		private boolean finished;

		/**
		 * Creates a new CompositeChunk at the current position of the
		 * ImageOutputStream.
		 * 
		 * @param compositeType
		 *            The type of the composite.
		 * @param chunkType
		 *            The type of the chunk.
		 */
		public CompositeChunk(String compositeType, String chunkType)
				throws IOException {
			super(chunkType);
			this.compositeType = compositeType;
			// out.write
			out.writeLong(0); // make room for the chunk header
			out.writeInt(0); // make room for the chunk header
			children = new LinkedList<Chunk>();
		}

		public void add(Chunk child) throws IOException {
			if (children.size() > 0) {
				children.getLast().finish();
			}
			children.add(child);
		}

		/**
		 * Writes the chunk and all its children to the ImageOutputStream and
		 * disposes of all resources held by the chunk.
		 * 
		 * @throws java.io.IOException
		 */
		@Override
		public void finish() throws IOException {
			if (!finished) {
				if (size() > 0xffffffffL) {
					throw new IOException("CompositeChunk \"" + chunkType
							+ "\" is too large: " + size());
				}

				long pointer = getRelativeStreamPosition();
				seekRelative(offset);

				DataChunkOutputStream headerData = new DataChunkOutputStream(
						new ImageOutputStreamAdapter(out), false);
				headerData.writeType(compositeType);
				headerData.writeUInt(size() - 8);
				headerData.writeType(chunkType);
				for (Chunk child : children) {
					child.finish();
				}
				seekRelative(pointer);
				if (size() % 2 == 1) {
					out.writeByte(0); // write pad byte
				}
				finished = true;
			}
		}

		@Override
		public long size() {
			long length = 12;
			for (Chunk child : children) {
				length += child.size() + child.size() % 2;
			}
			return length;
		}
	}

	/**
	 * Data Chunk.
	 */
	private class DataChunk extends Chunk {

		private final DataChunkOutputStream data;
		private boolean finished;

		/**
		 * Creates a new DataChunk at the current position of the
		 * ImageOutputStream.
		 * 
		 * @param chunkType
		 *            The chunkType of the chunk.
		 */
		public DataChunk(String name) throws IOException {
			super(name);
			out.writeLong(0); // make room for the chunk header
			data = new DataChunkOutputStream(new ImageOutputStreamAdapter(out),
					false);
		}

		public DataChunkOutputStream getOutputStream() {
			if (finished) {
				throw new IllegalStateException("DataChunk is finished");
			}
			return data;
		}

		/**
		 * Returns the offset of this chunk to the beginning of the random
		 * access file
		 * 
		 * @return
		 */
		public long getOffset() {
			return offset;
		}

		@Override
		public void finish() throws IOException {
			if (!finished) {
				long sizeBefore = size();

				if (size() > 0xffffffffL) {
					throw new IOException("DataChunk \"" + chunkType
							+ "\" is too large: " + size());
				}

				long pointer = getRelativeStreamPosition();
				seekRelative(offset);

				DataChunkOutputStream headerData = new DataChunkOutputStream(
						new ImageOutputStreamAdapter(out), false);
				headerData.writeType(chunkType);
				headerData.writeUInt(size() - 8);
				seekRelative(pointer);
				if (size() % 2 == 1) {
					out.writeByte(0); // write pad byte
				}
				finished = true;
				long sizeAfter = size();
				if (sizeBefore != sizeAfter) {
					System.err.println("size mismatch " + sizeBefore + ".."
							+ sizeAfter);
				}
			}
		}

		@Override
		public long size() {
			return 8 + data.size();
		}
	}

	/**
	 * A DataChunk with a fixed size.
	 */
	private class FixedSizeDataChunk extends Chunk {

		private final DataChunkOutputStream data;
		private boolean finished;
		private final long fixedSize;

		/**
		 * Creates a new DataChunk at the current position of the
		 * ImageOutputStream.
		 * 
		 * @param chunkType
		 *            The chunkType of the chunk.
		 */
		public FixedSizeDataChunk(String chunkType, long fixedSize)
				throws IOException {
			super(chunkType);
			this.fixedSize = fixedSize;
			data = new DataChunkOutputStream(new ImageOutputStreamAdapter(out),
					false);
			data.writeType(chunkType);
			data.writeUInt(fixedSize);
			data.clearCount();

			// Fill fixed size with nulls
			byte[] buf = new byte[(int) Math.min(512, fixedSize)];
			long written = 0;
			while (written < fixedSize) {
				data.write(buf, 0,
						(int) Math.min(buf.length, fixedSize - written));
				written += Math.min(buf.length, fixedSize - written);
			}
			if (fixedSize % 2 == 1) {
				out.writeByte(0); // write pad byte
			}
			seekToStartOfData();
		}

		public DataChunkOutputStream getOutputStream() {
			/*
			 * if (finished) { throw new
			 * IllegalStateException("DataChunk is finished"); }
			 */
			return data;
		}

		/**
		 * Returns the offset of this chunk to the beginning of the random
		 * access file
		 * 
		 * @return
		 */
		public long getOffset() {
			return offset;
		}

		public void seekToStartOfData() throws IOException {
			seekRelative(offset + 8);
			data.clearCount();
		}

		public void seekToEndOfChunk() throws IOException {
			seekRelative(offset + 8 + fixedSize + fixedSize % 2);
		}

		@Override
		public void finish() throws IOException {
			if (!finished) {
				finished = true;
			}
		}

		@Override
		public long size() {
			return 8 + fixedSize;
		}
	}

	/**
	 * Creates a new AVI file with the specified video format and frame rate.
	 * The video has 24 bits per pixel.
	 * 
	 * @param file
	 *            the output file
	 * @param format
	 *            Selects an encoder for the video format.
	 * @param bitsPerPixel
	 *            the number of bits per pixel.
	 * @exception IllegalArgumentException
	 *                if videoFormat is null or if frame rate is <= 0
	 */
	public AVIOutputStream(File file, VideoFormat format) throws IOException {
		this(file, format, 24);
	}

	/**
	 * Creates a new AVI file with the specified video format and frame rate.
	 * 
	 * @param file
	 *            the output file
	 * @param format
	 *            Selects an encoder for the video format.
	 * @param bitsPerPixel
	 *            the number of bits per pixel.
	 * @exception IllegalArgumentException
	 *                if videoFormat is null or if frame rate is <= 0
	 */
	public AVIOutputStream(File file, VideoFormat format, int bitsPerPixel)
			throws IOException {
		if (format == null) {
			throw new IllegalArgumentException("format must not be null");
		}

		if (file.exists()) {
			file.delete();
		}
		this.out = new FileImageOutputStream(file);
		this.streamOffset = 0;
		this.videoFormat = format;
		this.videoFrames = new LinkedList<Sample>();
		this.imgDepth = bitsPerPixel;
		if (imgDepth == 4) {
			byte[] gray = new byte[16];
			for (int i = 0; i < gray.length; i++) {
				gray[i] = (byte) ((i << 4) | i);
			}
			palette = new IndexColorModel(4, 16, gray, gray, gray);
		} else if (imgDepth == 8) {
			byte[] gray = new byte[256];
			for (int i = 0; i < gray.length; i++) {
				gray[i] = (byte) i;
			}
			palette = new IndexColorModel(8, 256, gray, gray, gray);
		}

	}

	/**
	 * Creates a new AVI output stream with the specified video format and
	 * framerate.
	 * 
	 * @param out
	 *            the underlying output stream
	 * @param format
	 *            Selects an encoder for the video format.
	 * @exception IllegalArgumentException
	 *                if videoFormat is null or if framerate is <= 0
	 */
	public AVIOutputStream(ImageOutputStream out, VideoFormat format)
			throws IOException {
		if (format == null) {
			throw new IllegalArgumentException("format must not be null");
		}
		this.out = out;
		this.streamOffset = out.getStreamPosition();
		this.videoFormat = format;
		this.videoFrames = new LinkedList<Sample>();
	}

	/**
	 * Used with frameRate to specify the time scale that this stream will use.
	 * Dividing frameRate by timeScale gives the number of samples per second.
	 * For video streams, this is the frame rate. For audio streams, this rate
	 * corresponds to the time needed to play nBlockAlign bytes of audio, which
	 * for PCM audio is the just the sample rate.
	 * <p>
	 * The default value is 1.
	 * 
	 * @param newValue
	 */
	public void setTimeScale(int newValue) {
		if (newValue <= 0) {
			throw new IllegalArgumentException("timeScale must be greater 0");
		}
		this.timeScale = newValue;
	}

	/**
	 * Returns the time scale of this media.
	 * 
	 * @return time scale
	 */
	public int getTimeScale() {
		return timeScale;
	}

	/**
	 * Sets the rate of video frames in time scale units.
	 * <p>
	 * The default value is 30. Together with the default value 1 of timeScale
	 * this results in 30 frames pers second.
	 * 
	 * @param newValue
	 */
	public void setFrameRate(int newValue) {
		if (newValue <= 0) {
			throw new IllegalArgumentException(
					"frameDuration must be greater 0");
		}
		if (state == States.STARTED) {
			throw new IllegalStateException(
					"frameDuration must be set before the first frame is written");
		}
		this.frameRate = newValue;
	}

	/**
	 * Returns the frame rate of this media.
	 * 
	 * @return frame rate
	 */
	public int getFrameRate() {
		return frameRate;
	}

	/** Sets the global color palette. */
	public void setPalette(IndexColorModel palette) {
		this.palette = palette;
	}

	/**
	 * Sets the compression quality of the video track. A value of 0 stands for
	 * "high compression is important" a value of 1 for
	 * "high image quality is important".
	 * <p>
	 * Changing this value affects frames which are subsequently written to the
	 * AVIOutputStream. Frames which have already been written are not changed.
	 * <p>
	 * This value has only effect on videos encoded with JPG format.
	 * <p>
	 * The default value is 0.9.
	 * 
	 * @param newValue
	 */
	public void setVideoCompressionQuality(float newValue) {
		this.quality = newValue;
	}

	/**
	 * Returns the video compression quality.
	 * 
	 * @return video compression quality
	 */
	public float getVideoCompressionQuality() {
		return quality;
	}

	/**
	 * Sets the dimension of the video track.
	 * <p>
	 * You need to explicitly set the dimension, if you add all frames from
	 * files or input streams.
	 * <p>
	 * If you add frames from buffered images, then AVIOutputStream can
	 * determine the video dimension from the image width and height.
	 * 
	 * @param width
	 *            Must be greater than 0.
	 * @param height
	 *            Must be greater than 0.
	 */
	public void setVideoDimension(int width, int height) {
		if (width < 1 || height < 1) {
			throw new IllegalArgumentException(
					"width and height must be greater zero.");
		}
		this.imgWidth = width;
		this.imgHeight = height;
	}

	/**
	 * Gets the dimension of the video track.
	 * <p>
	 * Returns null if the dimension is not known.
	 */
	public Dimension getVideoDimension() {
		if (imgWidth < 1 || imgHeight < 1) {
			return null;
		}
		return new Dimension(imgWidth, imgHeight);
	}

	/**
	 * Sets the state of the QuickTimeOutpuStream to started.
	 * <p>
	 * If the state is changed by this method, the prolog is written.
	 */
	private void ensureStarted() throws IOException {
		if (state != States.STARTED) {
			creationTime = new Date();
			writeProlog();
			state = States.STARTED;
		}
	}

	/**
	 * Writes a frame to the video track.
	 * <p>
	 * If the dimension of the video track has not been specified yet, it is
	 * derived from the first buffered image added to the AVIOutputStream.
	 * 
	 * @param image
	 *            The frame image.
	 * 
	 * @throws IllegalArgumentException
	 *             if the duration is less than 1, or if the dimension of the
	 *             frame does not match the dimension of the video track.
	 * @throws IOException
	 *             if writing the image failed.
	 */
	public void writeFrame(BufferedImage image) throws IOException {
		ensureOpen();
		ensureStarted();

		// Get the dimensions of the first image
		if (imgWidth == -1) {
			imgWidth = image.getWidth();
			imgHeight = image.getHeight();
		} else {
			// The dimension of the image must match the dimension of the video
			// track
			if (imgWidth != image.getWidth() || imgHeight != image.getHeight()) {
				throw new IllegalArgumentException("Dimensions of image["
						+ videoFrames.size() + "] (width=" + image.getWidth()
						+ ", height=" + image.getHeight()
						+ ") differs from image[0] (width=" + imgWidth
						+ ", height=" + imgHeight);
			}
		}

		DataChunk videoFrameChunk;
		long offset = getRelativeStreamPosition();
		boolean isSync = true;
		switch (videoFormat) {
		case RAW: {
			switch (imgDepth) {
			case 4: {
				IndexColorModel imgPalette = (IndexColorModel) image
						.getColorModel();
				int[] imgRGBs = new int[16];
				imgPalette.getRGBs(imgRGBs);
				int[] previousRGBs = new int[16];
				if (previousPalette == null) {
					previousPalette = palette;
				}
				previousPalette.getRGBs(previousRGBs);
				if (!Arrays.equals(imgRGBs, previousRGBs)) {
					previousPalette = imgPalette;
					DataChunk paletteChangeChunk = new DataChunk("00pc");
					/*
					 * int first = imgPalette.getMapSize(); int last = -1; for
					 * (int i = 0; i < 16; i++) { if (previousRGBs[i] !=
					 * imgRGBs[i] && i < first) { first = i; } if
					 * (previousRGBs[i] != imgRGBs[i] && i > last) { last = i; }
					 * }
					 */
					int first = 0;
					int last = imgPalette.getMapSize() - 1;
					/*
					 * typedef struct { BYTE bFirstEntry; BYTE bNumEntries; WORD
					 * wFlags; PALETTEENTRY peNew[]; } AVIPALCHANGE;
					 * 
					 * typedef struct tagPALETTEENTRY { BYTE peRed; BYTE
					 * peGreen; BYTE peBlue; BYTE peFlags; } PALETTEENTRY;
					 */
					DataChunkOutputStream pOut = paletteChangeChunk
							.getOutputStream();
					pOut.writeByte(first);// bFirstEntry
					pOut.writeByte(last - first + 1);// bNumEntries
					pOut.writeShort(0);// wFlags

					for (int i = first; i <= last; i++) {
						pOut.writeByte((imgRGBs[i] >>> 16) & 0xff); // red
						pOut.writeByte((imgRGBs[i] >>> 8) & 0xff); // green
						pOut.writeByte(imgRGBs[i] & 0xff); // blue
						pOut.writeByte(0); // reserved*/
					}

					moviChunk.add(paletteChangeChunk);
					paletteChangeChunk.finish();
					long length = getRelativeStreamPosition() - offset;
					videoFrames.add(new Sample(paletteChangeChunk.chunkType, 0,
							offset, length - 8, false));
					offset = getRelativeStreamPosition();
				}

				videoFrameChunk = new DataChunk("00db");
				byte[] rgb8 = ((DataBufferByte) image.getRaster()
						.getDataBuffer()).getData();
				byte[] rgb4 = new byte[imgWidth / 2];
				for (int y = (imgHeight - 1) * imgWidth; y >= 0; y -= imgWidth) { // Upside
																					// down
					for (int x = 0, xx = 0, n = imgWidth; x < n; x += 2, ++xx) {
						rgb4[xx] = (byte) (((rgb8[y + x] & 0xf) << 4) | (rgb8[y
								+ x + 1] & 0xf));
					}
					videoFrameChunk.getOutputStream().write(rgb4);
				}
				break;
			}
			case 8: {
				IndexColorModel imgPalette = (IndexColorModel) image
						.getColorModel();
				int[] imgRGBs = new int[256];
				imgPalette.getRGBs(imgRGBs);
				int[] previousRGBs = new int[256];
				if (previousPalette == null) {
					previousPalette = palette;
				}
				previousPalette.getRGBs(previousRGBs);
				if (!Arrays.equals(imgRGBs, previousRGBs)) {
					previousPalette = imgPalette;
					DataChunk paletteChangeChunk = new DataChunk("00pc");
					/*
					 * int first = imgPalette.getMapSize(); int last = -1; for
					 * (int i = 0; i < 16; i++) { if (previousRGBs[i] !=
					 * imgRGBs[i] && i < first) { first = i; } if
					 * (previousRGBs[i] != imgRGBs[i] && i > last) { last = i; }
					 * }
					 */
					int first = 0;
					int last = imgPalette.getMapSize() - 1;
					/*
					 * typedef struct { BYTE bFirstEntry; BYTE bNumEntries; WORD
					 * wFlags; PALETTEENTRY peNew[]; } AVIPALCHANGE;
					 * 
					 * typedef struct tagPALETTEENTRY { BYTE peRed; BYTE
					 * peGreen; BYTE peBlue; BYTE peFlags; } PALETTEENTRY;
					 */
					DataChunkOutputStream pOut = paletteChangeChunk
							.getOutputStream();
					pOut.writeByte(first);// bFirstEntry
					pOut.writeByte(last - first + 1);// bNumEntries
					pOut.writeShort(0);// wFlags

					for (int i = first; i <= last; i++) {
						pOut.writeByte((imgRGBs[i] >>> 16) & 0xff); // red
						pOut.writeByte((imgRGBs[i] >>> 8) & 0xff); // green
						pOut.writeByte(imgRGBs[i] & 0xff); // blue
						pOut.writeByte(0); // reserved*/
					}

					moviChunk.add(paletteChangeChunk);
					paletteChangeChunk.finish();
					long length = getRelativeStreamPosition() - offset;
					videoFrames.add(new Sample(paletteChangeChunk.chunkType, 0,
							offset, length - 8, false));
					offset = getRelativeStreamPosition();
				}

				videoFrameChunk = new DataChunk("00db");
				byte[] rgb8 = ((DataBufferByte) image.getRaster()
						.getDataBuffer()).getData();
				for (int y = (imgHeight - 1) * imgWidth; y >= 0; y -= imgWidth) { // Upside
																					// down
					videoFrameChunk.getOutputStream().write(rgb8, y, imgWidth);
				}
				break;
			}
			default: {
				videoFrameChunk = new DataChunk("00db");
				WritableRaster raster = image.getRaster();
				int[] raw = new int[imgWidth * 3]; // holds a scanline of raw
													// image data with 3
													// channels of 32 bit data
				byte[] bytes = new byte[imgWidth * 3]; // holds a scanline of
														// raw image data with 3
														// channels of 8 bit
														// data
				for (int y = imgHeight - 1; y >= 0; --y) { // Upside down
					raster.getPixels(0, y, imgWidth, 1, raw);
					for (int x = 0, n = imgWidth * 3; x < n; x += 3) {
						bytes[x + 2] = (byte) raw[x]; // Blue
						bytes[x + 1] = (byte) raw[x + 1]; // Green
						bytes[x] = (byte) raw[x + 2]; // Red
					}
					videoFrameChunk.getOutputStream().write(bytes);
				}
				break;
			}
			}
			break;
		}
		case RLE: {
			if (encoder == null) {
				encoder = new MicrosoftRLEEncoder();
			}

			isSync = videoFrames.size() % syncInterval == 0;

			switch (imgDepth) {
			case 4: {
				throw new UnsupportedOperationException(
						"RLE 4-bit not implemented.");
			}
			case 8: {
				IndexColorModel imgPalette = (IndexColorModel) image
						.getColorModel();
				int[] imgRGBs = new int[256];
				imgPalette.getRGBs(imgRGBs);
				int[] previousRGBs = new int[256];
				if (previousPalette == null) {
					previousPalette = palette;
				}
				previousPalette.getRGBs(previousRGBs);
				if (!Arrays.equals(imgRGBs, previousRGBs)) {
					isSync = true; // palette change requires key frame

					previousPalette = imgPalette;
					DataChunk paletteChangeChunk = new DataChunk("00pc");
					/*
					 * int first = imgPalette.getMapSize(); int last = -1; for
					 * (int i = 0; i < 16; i++) { if (previousRGBs[i] !=
					 * imgRGBs[i] && i < first) { first = i; } if
					 * (previousRGBs[i] != imgRGBs[i] && i > last) { last = i; }
					 * }
					 */
					int first = 0;
					int last = imgPalette.getMapSize() - 1;
					/*
					 * typedef struct { BYTE bFirstEntry; BYTE bNumEntries; WORD
					 * wFlags; PALETTEENTRY peNew[]; } AVIPALCHANGE;
					 * 
					 * typedef struct tagPALETTEENTRY { BYTE peRed; BYTE
					 * peGreen; BYTE peBlue; BYTE peFlags; } PALETTEENTRY;
					 */
					DataChunkOutputStream pOut = paletteChangeChunk
							.getOutputStream();
					pOut.writeByte(first);// bFirstEntry
					pOut.writeByte(last - first + 1);// bNumEntries
					pOut.writeShort(0);// wFlags

					for (int i = first; i <= last; i++) {
						pOut.writeByte((imgRGBs[i] >>> 16) & 0xff); // red
						pOut.writeByte((imgRGBs[i] >>> 8) & 0xff); // green
						pOut.writeByte(imgRGBs[i] & 0xff); // blue
						pOut.writeByte(0); // reserved*/
					}

					moviChunk.add(paletteChangeChunk);
					paletteChangeChunk.finish();
					long length = getRelativeStreamPosition() - offset;
					videoFrames.add(new Sample(paletteChangeChunk.chunkType, 0,
							offset, length - 8, false));
					offset = getRelativeStreamPosition();
				}

				videoFrameChunk = new DataChunk("00dc");
				byte[] rgb8 = ((DataBufferByte) image.getRaster()
						.getDataBuffer()).getData();
				if (isSync) {
					encoder.writeKey8(videoFrameChunk.getOutputStream(), rgb8,
							0, imgWidth, imgWidth, imgHeight);
				} else {
					encoder.writeDelta8(videoFrameChunk.getOutputStream(),
							rgb8, (byte[]) previousData, 0, imgWidth, imgWidth,
							imgHeight);
				}
				if (previousData == null) {
					previousData = new byte[rgb8.length];
				}
				System.arraycopy(rgb8, 0, previousData, 0, rgb8.length);
				break;
			}
			default: {
				throw new UnsupportedOperationException(
						"RLE only supports 4-bit and 8-bit video.");
			}
			}
			break;
		}
		case JPG: {
			videoFrameChunk = new DataChunk("00dc");
			ImageWriter iw = ImageIO.getImageWritersByMIMEType("image/jpeg")
					.next();
			ImageWriteParam iwParam = iw.getDefaultWriteParam();
			iwParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			iwParam.setCompressionQuality(quality);
			MemoryCacheImageOutputStream imgOut = new MemoryCacheImageOutputStream(
					videoFrameChunk.getOutputStream());
			iw.setOutput(imgOut);
			IIOImage img = new IIOImage(image, null, null);
			iw.write(null, img, iwParam);
			iw.dispose();
			break;
		}
		case PNG:
		default: {
			videoFrameChunk = new DataChunk("00dc");
			ImageWriter iw = ImageIO.getImageWritersByMIMEType("image/png")
					.next();
			ImageWriteParam iwParam = iw.getDefaultWriteParam();
			MemoryCacheImageOutputStream imgOut = new MemoryCacheImageOutputStream(
					videoFrameChunk.getOutputStream());
			iw.setOutput(imgOut);
			IIOImage img = new IIOImage(image, null, null);
			iw.write(null, img, iwParam);
			iw.dispose();
			break;
		}
		}
		long length = getRelativeStreamPosition() - offset;
		moviChunk.add(videoFrameChunk);
		videoFrameChunk.finish();

		videoFrames.add(new Sample(videoFrameChunk.chunkType, frameRate,
				offset, length - 8, isSync));
		if (getRelativeStreamPosition() > 1L << 32) {
			throw new IOException("AVI file is larger than 4 GB");
		}
	}

	/**
	 * Writes a frame from a file to the video track.
	 * <p>
	 * This method does not inspect the contents of the file. For example, Its
	 * your responsibility to only add JPG files if you have chosen the JPEG
	 * video format.
	 * <p>
	 * If you add all frames from files or from input streams, then you have to
	 * explicitly set the dimension of the video track before you call finish()
	 * or close().
	 * 
	 * @param file
	 *            The file which holds the image data.
	 * 
	 * @throws IllegalStateException
	 *             if the duration is less than 1.
	 * @throws IOException
	 *             if writing the image failed.
	 */
	public void writeFrame(File file) throws IOException {
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
			writeFrame(in);
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	/**
	 * Writes a frame to the video track.
	 * <p>
	 * This method does not inspect the contents of the file. For example, its
	 * your responsibility to only add JPG files if you have chosen the JPEG
	 * video format.
	 * <p>
	 * If you add all frames from files or from input streams, then you have to
	 * explicitly set the dimension of the video track before you call finish()
	 * or close().
	 * 
	 * @param in
	 *            The input stream which holds the image data.
	 * 
	 * @throws IllegalArgumentException
	 *             if the duration is less than 1.
	 * @throws IOException
	 *             if writing the image failed.
	 */
	public void writeFrame(InputStream in) throws IOException {
		ensureOpen();
		ensureStarted();

		DataChunk videoFrameChunk = new DataChunk(
				videoFormat == VideoFormat.RAW ? "00db" : "00dc");
		moviChunk.add(videoFrameChunk);
		OutputStream mdatOut = videoFrameChunk.getOutputStream();
		long offset = getRelativeStreamPosition();
		byte[] buf = new byte[512];
		int len;
		while ((len = in.read(buf)) != -1) {
			mdatOut.write(buf, 0, len);
		}
		long length = getRelativeStreamPosition() - offset;
		videoFrameChunk.finish();
		videoFrames.add(new Sample(videoFrameChunk.chunkType, frameRate,
				offset, length - 8, true));
		if (getRelativeStreamPosition() > 1L << 32) {
			throw new IOException("AVI file is larger than 4 GB");
		}
	}

	/**
	 * Closes the movie file as well as the stream being filtered.
	 * 
	 * @exception IOException
	 *                if an I/O error has occurred
	 */
	public void close() throws IOException {
		if (state == States.STARTED) {
			finish();
		}
		if (state != States.CLOSED) {
			out.close();
			state = States.CLOSED;
		}
	}

	/**
	 * Finishes writing the contents of the AVI output stream without closing
	 * the underlying stream. Use this method when applying multiple filters in
	 * succession to the same output stream.
	 * 
	 * @exception IllegalStateException
	 *                if the dimension of the video track has not been specified
	 *                or determined yet.
	 * @exception IOException
	 *                if an I/O exception has occurred
	 */
	public void finish() throws IOException {
		ensureOpen();
		if (state != States.FINISHED) {
			if (imgWidth == -1 || imgHeight == -1) {
				throw new IllegalStateException(
						"image width and height must be specified");
			}

			moviChunk.finish();
			writeEpilog();
			state = States.FINISHED;
			imgWidth = imgHeight = -1;
		}
	}

	/**
	 * Check to make sure that this stream has not been closed
	 */
	private void ensureOpen() throws IOException {
		if (state == States.CLOSED) {
			throw new IOException("Stream closed");
		}
	}

	/**
	 * Gets the position relative to the beginning of the QuickTime stream.
	 * <p>
	 * Usually this value is equal to the stream position of the underlying
	 * ImageOutputStream, but can be larger if the underlying stream already
	 * contained data.
	 * 
	 * @return The relative stream position.
	 * @throws IOException
	 */
	private long getRelativeStreamPosition() throws IOException {
		return out.getStreamPosition() - streamOffset;
	}

	/**
	 * Seeks relative to the beginning of the QuickTime stream.
	 * <p>
	 * Usually this equal to seeking in the underlying ImageOutputStream, but
	 * can be different if the underlying stream already contained data.
	 * 
	 */
	private void seekRelative(long newPosition) throws IOException {
		out.seek(newPosition + streamOffset);
	}

	private void writeProlog() throws IOException {
		// The file has the following structure:
		//
		// .RIFF AVI
		// ..avih (AVI Header Chunk)
		// ..LIST strl
		// ...strh (Stream Header Chunk)
		// ...strf (Stream Format Chunk)
		// ..LIST movi
		// ...00dc (Compressed video data chunk in Track 00, repeated for each
		// frame)
		// ..idx1 (List of video data chunks and their location in the file)

		// The RIFF AVI Chunk holds the complete movie
		aviChunk = new CompositeChunk("RIFF", "AVI ");
		CompositeChunk hdrlChunk = new CompositeChunk("LIST", "hdrl");

		// Write empty AVI Main Header Chunk - we fill the data in later
		aviChunk.add(hdrlChunk);
		avihChunk = new FixedSizeDataChunk("avih", 56);
		avihChunk.seekToEndOfChunk();
		hdrlChunk.add(avihChunk);

		CompositeChunk strlChunk = new CompositeChunk("LIST", "strl");
		hdrlChunk.add(strlChunk);

		// Write empty AVI Stream Header Chunk - we fill the data in later
		strhChunk = new FixedSizeDataChunk("strh", 56);
		strhChunk.seekToEndOfChunk();
		strlChunk.add(strhChunk);
		strfChunk = new FixedSizeDataChunk("strf", palette == null ? 40
				: 40 + palette.getMapSize() * 4);
		strfChunk.seekToEndOfChunk();
		strlChunk.add(strfChunk);

		moviChunk = new CompositeChunk("LIST", "movi");
		aviChunk.add(moviChunk);

	}

	private void writeEpilog() throws IOException {
		// Compute values
		int duration = 0;
		for (Sample s : videoFrames) {
			duration += s.duration;
		}
		long bufferSize = 0;
		for (Sample s : videoFrames) {
			if (s.length > bufferSize) {
				bufferSize = s.length;
			}
		}

		DataChunkOutputStream d;

		/*
		 * Create Idx1 Chunk and write data ------------- typedef struct
		 * _avioldindex { FOURCC fcc; DWORD cb; struct _avioldindex_entry {
		 * DWORD dwChunkId; DWORD dwFlags; DWORD dwOffset; DWORD dwSize; }
		 * aIndex[]; } AVIOLDINDEX;
		 */
		DataChunk idx1Chunk = new DataChunk("idx1");
		aviChunk.add(idx1Chunk);
		d = idx1Chunk.getOutputStream();
		long moviListOffset = moviChunk.offset + 8;
		// moviListOffset = 0;
		for (Sample f : videoFrames) {

			d.writeType(f.chunkType); // dwChunkId
			// Specifies a FOURCC that identifies a stream in the AVI file. The
			// FOURCC must have the form 'xxyy' where xx is the stream number
			// and yy
			// is a two-character code that identifies the contents of the
			// stream:
			//
			// Two-character code Description
			// db Uncompressed video frame
			// dc Compressed video frame
			// pc Palette change
			// wb Audio data

			d.writeUInt((f.chunkType.endsWith("pc") ? 0x100 : 0x0)//
					| (f.isSync ? 0x10 : 0x0)); // dwFlags
			// Specifies a bitwise combination of zero or more of the following
			// flags:
			//
			// Value Name Description
			// 0x10 AVIIF_KEYFRAME The data chunk is a key frame.
			// 0x1 AVIIF_LIST The data chunk is a 'rec ' list.
			// 0x100 AVIIF_NO_TIME The data chunk does not affect the timing of
			// the
			// stream. For example, this flag should be set for
			// palette changes.

			d.writeUInt(f.offset - moviListOffset); // dwOffset
			// Specifies the location of the data chunk in the file. The value
			// should be specified as an offset, in bytes, from the start of the
			// 'movi' list; however, in some AVI files it is given as an offset
			// from
			// the start of the file.

			d.writeUInt(f.length); // dwSize
			// Specifies the size of the data chunk, in bytes.
		}
		idx1Chunk.finish();

		/*
		 * Write Data into AVI Main Header Chunk ------------- The AVIMAINHEADER
		 * structure defines global information in an AVI file. see
		 * http://msdn.microsoft.com/en-us/library/ms779632(VS.85).aspx typedef
		 * struct _avimainheader { FOURCC fcc; DWORD cb; DWORD
		 * dwMicroSecPerFrame; DWORD dwMaxBytesPerSec; DWORD
		 * dwPaddingGranularity; DWORD dwFlags; DWORD dwTotalFrames; DWORD
		 * dwInitialFrames; DWORD dwStreams; DWORD dwSuggestedBufferSize; DWORD
		 * dwWidth; DWORD dwHeight; DWORD dwReserved[4]; } AVIMAINHEADER;
		 */
		avihChunk.seekToStartOfData();
		d = avihChunk.getOutputStream();

		d.writeUInt((1000000L * timeScale) / frameRate); // dwMicroSecPerFrame
		// Specifies the number of microseconds between frames.
		// This value indicates the overall timing for the file.

		d.writeUInt(0); // dwMaxBytesPerSec
		// Specifies the approximate maximum data rate of the file.
		// This value indicates the number of bytes per second the system
		// must handle to present an AVI sequence as specified by the other
		// parameters contained in the main header and stream header chunks.

		d.writeUInt(0); // dwPaddingGranularity
		// Specifies the alignment for data, in bytes. Pad the data to multiples
		// of this value.

		d.writeUInt(0x10); // dwFlags (0x10 == hasIndex)
		// Contains a bitwise combination of zero or more of the following
		// flags:
		//
		// Value Name Description
		// 0x10 AVIF_HASINDEX Indicates the AVI file has an index.
		// 0x20 AVIF_MUSTUSEINDEX Indicates that application should use the
		// index, rather than the physical ordering of the
		// chunks in the file, to determine the order of
		// presentation of the data. For example, this flag
		// could be used to create a list of frames for
		// editing.
		// 0x100 AVIF_ISINTERLEAVED Indicates the AVI file is interleaved.
		// 0x1000 AVIF_WASCAPTUREFILE Indicates the AVI file is a specially
		// allocated file used for capturing real-time
		// video. Applications should warn the user before
		// writing over a file with this flag set because
		// the user probably defragmented this file.
		// 0x20000 AVIF_COPYRIGHTED Indicates the AVI file contains copyrighted
		// data and software. When this flag is used,
		// software should not permit the data to be
		// duplicated.

		d.writeUInt(videoFrames.size()); // dwTotalFrames
		// Specifies the total number of frames of data in the file.

		d.writeUInt(0); // dwInitialFrames
		// Specifies the initial frame for interleaved files. Noninterleaved
		// files should specify zero. If you are creating interleaved files,
		// specify the number of frames in the file prior to the initial frame
		// of the AVI sequence in this member.
		// To give the audio driver enough audio to work with, the audio data in
		// an interleaved file must be skewed from the video data. Typically,
		// the audio data should be moved forward enough frames to allow
		// approximately 0.75 seconds of audio data to be preloaded. The
		// dwInitialRecords member should be set to the number of frames the
		// audio is skewed. Also set the same value for the dwInitialFrames
		// member of the AVISTREAMHEADER structure in the audio stream header

		d.writeUInt(1); // dwStreams
		// Specifies the number of streams in the file. For example, a file with
		// audio and video has two streams.

		d.writeUInt(bufferSize); // dwSuggestedBufferSize
		// Specifies the suggested buffer size for reading the file. Generally,
		// this size should be large enough to contain the largest chunk in the
		// file. If set to zero, or if it is too small, the playback software
		// will have to reallocate memory during playback, which will reduce
		// performance. For an interleaved file, the buffer size should be large
		// enough to read an entire record, and not just a chunk.

		d.writeUInt(imgWidth); // dwWidth
		// Specifies the width of the AVI file in pixels.

		d.writeUInt(imgHeight); // dwHeight
		// Specifies the height of the AVI file in pixels.

		d.writeUInt(0); // dwReserved[0]
		d.writeUInt(0); // dwReserved[1]
		d.writeUInt(0); // dwReserved[2]
		d.writeUInt(0); // dwReserved[3]
		// Reserved. Set this array to zero.

		/*
		 * Write Data into AVI Stream Header Chunk ------------- The
		 * AVISTREAMHEADER structure contains information about one stream in an
		 * AVI file. see
		 * http://msdn.microsoft.com/en-us/library/ms779638(VS.85).aspx typedef
		 * struct _avistreamheader { FOURCC fcc; DWORD cb; FOURCC fccType;
		 * FOURCC fccHandler; DWORD dwFlags; WORD wPriority; WORD wLanguage;
		 * DWORD dwInitialFrames; DWORD dwScale; DWORD dwRate; DWORD dwStart;
		 * DWORD dwLength; DWORD dwSuggestedBufferSize; DWORD dwQuality; DWORD
		 * dwSampleSize; struct { short int left; short int top; short int
		 * right; short int bottom; } rcFrame; } AVISTREAMHEADER;
		 */
		strhChunk.seekToStartOfData();
		d = strhChunk.getOutputStream();
		d.writeType("vids"); // fccType - vids for video stream
		// Contains a FOURCC that specifies the type of the data contained in
		// the stream. The following standard AVI values for video and audio are
		// defined:
		//
		// FOURCC Description
		// 'auds' Audio stream
		// 'mids' MIDI stream
		// 'txts' Text stream
		// 'vids' Video stream

		switch (videoFormat) {
		case RAW:
			d.writeType("DIB "); // fccHandler - DIB for Raw RGB
			break;
		case RLE:
			d.writeType("RLE "); // fccHandler - Microsoft RLE
			break;
		case JPG:
			d.writeType("MJPG"); // fccHandler - MJPG for Motion JPEG
			break;
		case PNG:
		default:
			d.writeType("png "); // fccHandler - png for PNG
			break;
		}
		// Optionally, contains a FOURCC that identifies a specific data
		// handler. The data handler is the preferred handler for the stream.
		// For audio and video streams, this specifies the codec for decoding
		// the stream.

		if (imgDepth <= 8) {
			d.writeUInt(0x00010000); // dwFlags - AVISF_VIDEO_PALCHANGES
		} else {
			d.writeUInt(0); // dwFlags
		}

		// Contains any flags for the data stream. The bits in the high-order
		// word of these flags are specific to the type of data contained in the
		// stream. The following standard flags are defined:
		//
		// Value Name Description
		// AVISF_DISABLED 0x00000001 Indicates this stream should not
		// be enabled by default.
		// AVISF_VIDEO_PALCHANGES 0x00010000
		// Indicates this video stream contains
		// palette changes. This flag warns the playback
		// software that it will need to animate the
		// palette.

		d.writeUShort(0); // wPriority
		// Specifies priority of a stream type. For example, in a file with
		// multiple audio streams, the one with the highest priority might be
		// the default stream.

		d.writeUShort(0); // wLanguage
		// Language tag.

		d.writeUInt(0); // dwInitialFrames
		// Specifies how far audio data is skewed ahead of the video frames in
		// interleaved files. Typically, this is about 0.75 seconds. If you are
		// creating interleaved files, specify the number of frames in the file
		// prior to the initial frame of the AVI sequence in this member. For
		// more information, see the remarks for the dwInitialFrames member of
		// the AVIMAINHEADER structure.

		d.writeUInt(timeScale); // dwScale
		// Used with dwRate to specify the time scale that this stream will use.
		// Dividing dwRate by dwScale gives the number of samples per second.
		// For video streams, this is the frame rate. For audio streams, this
		// rate corresponds to the time needed to play nBlockAlign bytes of
		// audio, which for PCM audio is the just the sample rate.

		d.writeUInt(frameRate); // dwRate
		// See dwScale.

		d.writeUInt(0); // dwStart
		// Specifies the starting time for this stream. The units are defined by
		// the dwRate and dwScale members in the main file header. Usually, this
		// is zero, but it can specify a delay time for a stream that does not
		// start concurrently with the file.

		d.writeUInt(videoFrames.size()); // dwLength
		// Specifies the length of this stream. The units are defined by the
		// dwRate and dwScale members of the stream's header.

		d.writeUInt(bufferSize); // dwSuggestedBufferSize
		// Specifies how large a buffer should be used to read this stream.
		// Typically, this contains a value corresponding to the largest chunk
		// present in the stream. Using the correct buffer size makes playback
		// more efficient. Use zero if you do not know the correct buffer size.

		d.writeInt(-1); // dwQuality
		// Specifies an indicator of the quality of the data in the stream.
		// Quality is represented as a number between 0 and 10,000.
		// For compressed data, this typically represents the value of the
		// quality parameter passed to the compression software. If set to â€“1,
		// drivers use the default quality value.

		d.writeUInt(0); // dwSampleSize
		// Specifies the size of a single sample of data. This is set to zero
		// if the samples can vary in size. If this number is nonzero, then
		// multiple samples of data can be grouped into a single chunk within
		// the file. If it is zero, each sample of data (such as a video frame)
		// must be in a separate chunk. For video streams, this number is
		// typically zero, although it can be nonzero if all video frames are
		// the same size. For audio streams, this number should be the same as
		// the nBlockAlign member of the WAVEFORMATEX structure describing the
		// audio.

		d.writeUShort(0); // rcFrame.left
		d.writeUShort(0); // rcFrame.top
		d.writeUShort(imgWidth); // rcFrame.right
		d.writeUShort(imgHeight); // rcFrame.bottom
		// Specifies the destination rectangle for a text or video stream within
		// the movie rectangle specified by the dwWidth and dwHeight members of
		// the AVI main header structure. The rcFrame member is typically used
		// in support of multiple video streams. Set this rectangle to the
		// coordinates corresponding to the movie rectangle to update the whole
		// movie rectangle. Units for this member are pixels. The upper-left
		// corner of the destination rectangle is relative to the upper-left
		// corner of the movie rectangle.

		/*
		 * Write BITMAPINFOHEADR Data into AVI Stream Format Chunk /*
		 * ------------- see
		 * http://msdn.microsoft.com/en-us/library/ms779712(VS.85).aspx typedef
		 * struct tagBITMAPINFOHEADER { DWORD biSize; LONG biWidth; LONG
		 * biHeight; WORD biPlanes; WORD biBitCount; DWORD biCompression; DWORD
		 * biSizeImage; LONG biXPelsPerMeter; LONG biYPelsPerMeter; DWORD
		 * biClrUsed; DWORD biClrImportant; } BITMAPINFOHEADER;
		 */
		strfChunk.seekToStartOfData();
		d = strfChunk.getOutputStream();
		d.writeUInt(40); // biSize
		// Specifies the number of bytes required by the structure. This value
		// does not include the size of the color table or the size of the color
		// masks, if they are appended to the end of structure.

		d.writeInt(imgWidth); // biWidth
		// Specifies the width of the bitmap, in pixels.

		d.writeInt(imgHeight); // biHeight
		// Specifies the height of the bitmap, in pixels.
		//
		// For uncompressed RGB bitmaps, if biHeight is positive, the bitmap is
		// a bottom-up DIB with the origin at the lower left corner. If biHeight
		// is negative, the bitmap is a top-down DIB with the origin at the
		// upper left corner.
		// For YUV bitmaps, the bitmap is always top-down, regardless of the
		// sign of biHeight. Decoders should offer YUV formats with postive
		// biHeight, but for backward compatibility they should accept YUV
		// formats with either positive or negative biHeight.
		// For compressed formats, biHeight must be positive, regardless of
		// image orientation.

		d.writeShort(1); // biPlanes
		// Specifies the number of planes for the target device. This value must
		// be set to 1.

		d.writeShort(imgDepth); // biBitCount
		// Specifies the number of bits per pixel (bpp). For uncompressed
		// formats, this value is the average number of bits per pixel. For
		// compressed formats, this value is the implied bit depth of the
		// uncompressed image, after the image has been decoded.

		switch (videoFormat) {
		case RAW:
		default:
			d.writeInt(0); // biCompression - BI_RGB for uncompressed RGB
			break;
		case RLE:
			if (imgDepth == 8) {
				d.writeInt(1); // biCompression - BI_RLE8
			} else if (imgDepth == 4) {
				d.writeInt(2); // biCompression - BI_RLE4
			} else {
				throw new UnsupportedOperationException(
						"RLE only supports 4-bit and 8-bit images");
			}
			break;
		case JPG:
			d.writeType("MJPG"); // biCompression - MJPG for Motion JPEG
			break;
		case PNG:
			d.writeType("png "); // biCompression - png for PNG
			break;
		}
		// For compressed video and YUV formats, this member is a FOURCC code,
		// specified as a DWORD in little-endian order. For example, YUYV video
		// has the FOURCC 'VYUY' or 0x56595559. For more information, see FOURCC
		// Codes.
		//
		// For uncompressed RGB formats, the following values are possible:
		//
		// Value Description
		// BI_RGB 0x00000000 Uncompressed RGB.
		// BI_BITFIELDS 0x00000003 Uncompressed RGB with color masks.
		// Valid for 16-bpp and 32-bpp bitmaps.
		//
		// Note that BI_JPG and BI_PNG are not valid video formats.
		//
		// For 16-bpp bitmaps, if biCompression equals BI_RGB, the format is
		// always RGB 555. If biCompression equals BI_BITFIELDS, the format is
		// either RGB 555 or RGB 565. Use the subtype GUID in the AM_MEDIA_TYPE
		// structure to determine the specific RGB type.

		switch (videoFormat) {
		case RAW:
			d.writeInt(0); // biSizeImage
			break;
		case RLE:
		case JPG:
		case PNG:
		default:
			if (imgDepth == 4) {
				d.writeInt(imgWidth * imgHeight / 2); // biSizeImage
			} else {
				int bytesPerPixel = Math.max(1, imgDepth / 8);
				d.writeInt(imgWidth * imgHeight * bytesPerPixel); // biSizeImage
			}
			break;
		}
		// Specifies the size, in bytes, of the image. This can be set to 0 for
		// uncompressed RGB bitmaps.

		d.writeInt(0); // biXPelsPerMeter
		// Specifies the horizontal resolution, in pixels per meter, of the
		// target device for the bitmap.

		d.writeInt(0); // biYPelsPerMeter
		// Specifies the vertical resolution, in pixels per meter, of the target
		// device for the bitmap.

		d.writeInt(palette == null ? 0 : palette.getMapSize()); // biClrUsed
		// Specifies the number of color indices in the color table that are
		// actually used by the bitmap.

		d.writeInt(0); // biClrImportant
		// Specifies the number of color indices that are considered important
		// for displaying the bitmap. If this value is zero, all colors are
		// important.

		if (palette != null) {
			for (int i = 0, n = palette.getMapSize(); i < n; ++i) {
				/*
				 * typedef struct tagRGBQUAD { BYTE rgbBlue; BYTE rgbGreen; BYTE
				 * rgbRed; BYTE rgbReserved; // This member is reserved and must
				 * be zero. } RGBQUAD;
				 */
				d.write(palette.getBlue(i));
				d.write(palette.getGreen(i));
				d.write(palette.getRed(i));
				d.write(0);
			}
		}

		// -----------------
		aviChunk.finish();
	}
}
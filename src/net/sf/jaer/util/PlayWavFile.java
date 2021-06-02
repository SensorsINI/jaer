package net.sf.jaer.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Plays sound samples which are specified by constructing the player and
 * starting the thread. Note that this class loads the file from disk each time it plays it, so it is slow.
 *
 * From http://www.anyexample.com/programming/java/java_play_wav_sound_file.xml.
 *
 * @author tobi
 *
 * This is part of jAER
 * <a href="http://jaerproject.net/">jaerproject.net</a>, licensed under the
 * LGPL
 * (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 * @see SoundWavFilePlayer for another class that keeps sound in memory
 */
public class PlayWavFile extends Thread {

    private static Logger log = Logger.getLogger("net.sf.jaer.util");
    private String filename;

    private Position curPosition;

    /**
     * The maximum buffer size: 128k samples.
     */
    public final int EXTERNAL_BUFFER_SIZE = 524288; // 128Kb

    /**
     * A stereo position.
     */
    public enum Position {
        LEFT, RIGHT, NORMAL
    };

    /**
     * Sets up to play a wav file at normal center position. To actually play
     * the file, start the thread.
     *
     * @param wavfile filename (including path if not in startup folder), or
     * resource path.
     */
    public PlayWavFile(String wavfile) {
        filename = wavfile;
        curPosition = Position.NORMAL;
    }

    /**
     * Sets up to play a wav file at specified stereo position. To actually play
     * the file, start the thread.
     *
     * @param wavfile filename (including path if not in startup folder), or
     * resource path.
     * @param p the position.
     * @see Position
     */
    public PlayWavFile(String wavfile, Position p) {
        filename = wavfile;
        curPosition = p;
    }

    /**
     * This method plays the file. Start the thread to play.
     */
    @Override
	public void run() {

        BufferedInputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(new File(filename)));
        } catch (FileNotFoundException e) {
            final InputStream s = getClass().getResourceAsStream(filename);
            if (s == null) {
                log.warning("Wave file not found on filesystem or classpath: " + filename);
                return;
            }

            is = new BufferedInputStream(s);
        }

        AudioInputStream audioInputStream = null;
        try {
            audioInputStream = AudioSystem.getAudioInputStream(is);
        } catch (UnsupportedAudioFileException e1) {
            e1.printStackTrace();
            return;
        } catch (IOException e1) {
            e1.printStackTrace();
            return;
        }

        AudioFormat format = audioInputStream.getFormat();
        SourceDataLine auline = null;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try {
            auline = (SourceDataLine) AudioSystem.getLine(info);
            auline.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (auline.isControlSupported(FloatControl.Type.PAN)) {
            FloatControl pan = (FloatControl) auline
                    .getControl(FloatControl.Type.PAN);
            if (curPosition == Position.RIGHT) {
                pan.setValue(1.0f);
            } else if (curPosition == Position.LEFT) {
                pan.setValue(-1.0f);
            }
        }

        auline.start();
        int nBytesRead = 0;
        byte[] abData = new byte[EXTERNAL_BUFFER_SIZE];

        try {
            while (nBytesRead != -1) {
                nBytesRead = audioInputStream.read(abData, 0, abData.length);
                if (nBytesRead >= 0) {
                    auline.write(abData, 0, nBytesRead);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } finally {
            auline.drain();
            auline.close();
        }

    }

    /**
     * Tests the player with "sample.wav" in the startup folder.
     */
    public static void main(String[] args) {
        new PlayWavFile("sounds/oof.wav").start();
    }
}

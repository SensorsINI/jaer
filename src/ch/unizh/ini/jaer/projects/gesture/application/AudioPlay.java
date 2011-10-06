/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.application;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 *
 * @author source from http://www.java2s.com/Code/JavaAPI/javax.sound.sampled/AudioSystemgetAudioInputStreamFilefile.htm
 */

public class AudioPlay{

    private File soundFile;
    private Clip clip;
    private AudioInputStream ais;

    public AudioPlay(String wavfile){
        soundFile = new File(wavfile);
        if (!soundFile.exists()) {
            System.err.println("Wave file not found: " + wavfile);
            return;
        }
    }

    public void start(){
        Line.Info linfo = new Line.Info(Clip.class);
        try{
            Line line = AudioSystem.getLine(linfo);
            clip = (Clip) line;
            ais = AudioSystem.getAudioInputStream(soundFile);
            clip.open(ais);
            clip.start();
        } catch (LineUnavailableException e1) {
            e1.printStackTrace();
            return;
        } catch (UnsupportedAudioFileException e1) {
            e1.printStackTrace();
            return;
        } catch (IOException e1) {
            e1.printStackTrace();
            return;
        }
    }
}

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
 *  original source from http://www.java2s.com/Code/JavaAPI/javax.sound.sampled/AudioSystemgetAudioInputStreamFilefile.htm
 * 
 * @author modified by Jun Haeng Lee  
 */
public class AudioPlay implements LineListener{

    private File soundFile;
    private Clip clip;
    private AudioInputStream ais;
    private Line line;

    public AudioPlay(String wavfile){
        soundFile = new File(wavfile);
        if (!soundFile.exists()) {
            System.err.println("Wave file not found: " + wavfile);
            return;
        }
        Line.Info linfo = new Line.Info(Clip.class);
        try{
            line = AudioSystem.getLine(linfo);
            clip = (Clip) line;
            ais = AudioSystem.getAudioInputStream(soundFile);
            clip.open(ais);
            clip.addLineListener(this);
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
    
    @Override
     public void update(LineEvent le) {
         LineEvent.Type type = le.getType();
         if (type == LineEvent.Type.OPEN) {
         } else if (type == LineEvent.Type.CLOSE) {
         } else if (type == LineEvent.Type.START) {
         } else if (type == LineEvent.Type.STOP) {
             clip.setMicrosecondPosition(0);
         }
     }

    public void start(){
        clip.start();
    }

}



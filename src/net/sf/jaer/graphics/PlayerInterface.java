package net.sf.jaer.graphics;

import java.io.File;
import java.io.IOException;


/** General interface for a file data player 
 @author tobi
 */
public interface PlayerInterface {

    /** Return time in us in player */
    int getTime();

    /** returns true if user is choosing a file */
    boolean isChoosingFile();

    boolean isPaused();

    boolean isPlayingForwards();

    void mark() throws IOException;

    void pause();

    void resume();

    void rewind();

    void setPaused(boolean yes);

    void setTime(int time);

    /** Starts playback
     @param file the file to play
     */
    void startPlayback(File file) throws IOException;

    void stopPlayback();

    void toggleDirection();

    void unmark();

}

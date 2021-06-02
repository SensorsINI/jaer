/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

/**
 * Used to display a selected channel of the cochlea in Rolling and Raster cochlea display methods
 * 
 * @author tobi
 */
public interface HasSelectedCochleaChannel {
    /**
     * @return the selectedChannel
     */
    public int getSelectedChannel();

    /**
     * Set this channel >=0 to show a selected channel. Set to negative integer to display display of selected channel.
     * @param selectedChannel the selectedChannel to set
     */
    public void setSelectedChannel(int selectedChannel);
}

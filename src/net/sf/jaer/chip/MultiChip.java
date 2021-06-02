/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.chip;

/**
 * All chip classes that implement this interface receive input from several chips and therefore need
 * the possibility to adjust the biases of every single one. So implementing this interface leads to a
 * chip selection drop down menu in the BiasgenFrame.
 *
 * @author braendch
 */
public interface MultiChip {

    /**
     * Gets the number of chips in the MultiChip
     *
     * @return number of chips
     */
    public int getNumChips();
    
    public void setNumChips(int numChips);

    /**
     * Gets the index of the selected chip for the bias setting. If the returned index = number of chips
     * all chips are selected
     *
     * @return index of selected chip
     */
    public int getSelectedChip();

    public void setSelectedChip(int selChip);

}

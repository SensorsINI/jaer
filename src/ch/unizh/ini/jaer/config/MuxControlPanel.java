/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.TitledBorder;

import ch.unizh.ini.jaer.config.onchip.OutputMux;

/**
    * Control panel for SBret10 diagnostic output configuration.
    * @author  tobi
    */
public class MuxControlPanel extends javax.swing.JPanel {

    public ArrayList<OutputMux> muxes;
    
    class OutputSelectionAction extends AbstractAction implements Observer {

        OutputMux mux;
        int channel;
        JRadioButton button;

        OutputSelectionAction(OutputMux m, int i) {
            super(m.getChannelName(i));
            mux = m;
            channel = i;
            m.addObserver(this);
        }

        void setButton(JRadioButton b) {
            button = b;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            mux.select(channel);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (channel == mux.selectedChannel) {
                button.setSelected(true);
            }
        }
    }

    /** Creates new control panel for this MUX
        * 
        * @param chip the chip
        */
    public MuxControlPanel(ArrayList<OutputMux> muxes) {
        this.muxes = muxes;
        for (OutputMux m : muxes) {
            JPanel p = new JPanel();
            p.setAlignmentY(0);
            p.setBorder(new TitledBorder(m.getName()));
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            ButtonGroup group = new ButtonGroup();
            final Insets insets = new Insets(0, 0, 0, 0);
            for (int i = 0; i < m.nInputs; i++) {

                OutputSelectionAction action = new OutputSelectionAction(m, i);
                JRadioButton b = new JRadioButton(action);
                action.setButton(b); // needed to update button state
                b.setSelected(i == m.selectedChannel);
                b.setFont(b.getFont().deriveFont(10f));
                b.setToolTipText(b.getText());
                b.setMargin(insets);
//                b.setMinimumSize(new Dimension(30, 14));
                group.add(b);
                p.add(b);
            }
            add(p);
        }
    }
}

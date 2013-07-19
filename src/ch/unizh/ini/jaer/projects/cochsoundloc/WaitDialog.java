/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 *
 * @author Holger
 */
public class WaitDialog extends JFrame {

    private JLabel jLabel1 = new JLabel();

    public WaitDialog() {
        try {
            this.getContentPane().setLayout(new FlowLayout(FlowLayout.CENTER));
            this.setResizable(false);

            this.setTitle("Please wait");
            URL imgURL = ClassLoader.getSystemResource("icons/bulb.gif");
            if (imgURL != null) {
                ImageIcon img = new ImageIcon(imgURL);
                jLabel1.setIcon(img);
            }
            jLabel1.setText("Please wait...");
            jLabel1.setFont(new Font("Tahoma", 1, 13));
            this.getContentPane().add(jLabel1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showDialog() {
        this.setSize(300, 50);
        this.setLocation(Toolkit.getDefaultToolkit().getScreenSize().width / 2 - this.getSize().width / 2, Toolkit.getDefaultToolkit().getScreenSize().height / 2 - this.getSize().height / 2);
        this.setVisible(true);
        this.paint(this.getGraphics());
    }

    public void hideDialog() {
        this.setVisible(false);
    }
}

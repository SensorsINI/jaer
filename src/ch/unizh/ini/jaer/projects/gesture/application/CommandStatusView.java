/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.Timer;

/**
 *
 * @author junhaeng2.lee
 */
public class CommandStatusView extends JFrame{
   private FontMetrics fm70, fm100, fm130;

   private boolean gestureControlOn = false;
   private static String GC_on = "ON";
   private static String GC_off = "OFF";
   private static String gestureString = "Gesture";
   private String statusString = "";

   private static Font font70 = new Font("Arial", Font.BOLD, 70);
   private static Font font100 = new Font("Arial", Font.BOLD, 100);
   private static Font font130 = new Font("Arial", Font.BOLD, 130);

   private Timer cmdViewTimer;
   protected static Random random = new Random();

   public CommandStatusView() {
      super("Gesture controller command status view");
      setFont(font100);
      fm70 = getFontMetrics(font70);
      fm100 = getFontMetrics(font100);
      fm130 = getFontMetrics(font130);
      setSize(1600, 300);

      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      setVisible(true);

      cmdViewTimer = new Timer(1000, timerActionFunction);
   }

   /**
     * action listener for timer events
     */
    ActionListener timerActionFunction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent evt) {
            statusString = "";
            cmdViewTimer.stop();
            repaint();
        }
    };

   public void setStatusString(String statusString){
       this.statusString = statusString;
       if(cmdViewTimer.isRunning())
           cmdViewTimer.restart();
       else
           cmdViewTimer.start();

       repaint();
   }

   public void setGestureContolOn(boolean on){
       gestureControlOn = on;
       repaint();
   }

    @Override
   public void paint(Graphics g) {

      Insets ins = getInsets();
      int w = getSize().width-ins.left-ins.right;
      int h = getSize().height-ins.top-ins.bottom;

      int centerX = w/2 + ins.left;
      int centerY = h/2 + ins.top;

      g.setColor(Color.white);
      g.fillRect(ins.left + w/5, ins.top, w, h);

      String on_off;
      if(gestureControlOn){
          on_off = GC_on;
          g.setColor(Color.red);
          g.fillRect(ins.left, ins.top, w/5, h);
          g.setColor(Color.yellow);
      }else{
          on_off = GC_off;
          g.setColor(Color.gray);
          g.fillRect(ins.left, ins.top, w/5, h);
          g.setColor(Color.black);
      }

      g.setFont(font70);
      g.drawString(
         gestureString,
         w/10 - fm70.stringWidth(gestureString)/2,
         centerY/2 + (fm70.getAscent()-fm70.getDescent())/2
      );

      g.setFont(font100);
      g.drawString(
         on_off,
         w/10 - fm100.stringWidth(on_off)/2,
         centerY*5/4 + (fm100.getAscent()-fm100.getDescent())/2
      );

      g.setFont(font130);
      g.setColor(Color.getHSBColor(random.nextFloat(), 1, 0.5f));
      g.drawString(
         statusString,
         centerX + w/10 -fm130.stringWidth(statusString)/2,
         centerY + (fm130.getAscent()-fm130.getDescent())/2
      );
   }
/*
    public static void main(String[] args)
    {
        CommandStatusView view = new CommandStatusView();
    }
*/
}

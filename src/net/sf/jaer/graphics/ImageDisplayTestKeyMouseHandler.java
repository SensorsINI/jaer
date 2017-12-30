/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import static net.sf.jaer.graphics.ImageDisplay.log;

/**
 *
 * @author tobi
 */
   public class ImageDisplayTestKeyMouseHandler {

        final ImageDisplay disp;
        final Point2D.Float mousePoint;
        Thread thread;

        public ImageDisplayTestKeyMouseHandler(ImageDisplay d, Point2D.Float mp, Thread t) {
            this.disp = d;
            this.mousePoint = mp;
            this.thread = t;
            this.disp.addKeyListener(new KeyAdapter() { // add some key listeners to the ImageDisplay

                @Override
                public void keyReleased(KeyEvent e) {
                    int k = e.getKeyCode();
                    if ((k == KeyEvent.VK_ESCAPE) || (k == KeyEvent.VK_X)) {
                        System.exit(0);
                    } else if (k == KeyEvent.VK_N) {
                        ImageDisplay.makeAndRunNewTestImageDisplay(); // make another window
                    } else if ((k == KeyEvent.VK_W) && ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK)) {
                        thread.interrupt();
                    } else if (k == KeyEvent.VK_UP) {
                        disp.setSizeY(disp.getHeight() * 2); // UP arrow incrases vertical dimension
                    } else if (k == KeyEvent.VK_DOWN) {
                        disp.setSizeY(disp.getHeight() / 2);
                    } else if (k == KeyEvent.VK_RIGHT) {
                        disp.setSizeX(disp.getWidth() * 2);
                    } else if (k == KeyEvent.VK_LEFT) {
                        disp.setSizeX(disp.getWidth() / 2);
                    } else if (k == KeyEvent.VK_G) { // 'g' resets the frame to gray level 0.5f
                        disp.resetFrame(.5f);
                    } else if ((k == KeyEvent.VK_F) && ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK)) {
                        disp.setFontSize((disp.getFontSize() * 14) / 10);
                        log.info("fontSize" + disp.getFontSize());
                    } else if ((k == KeyEvent.VK_F) && ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0)) {
                        int newsize = (disp.getFontSize() * 10) / 14;
                        if (newsize < 1) {
                            newsize = 1;
                        }
                        disp.setFontSize(newsize);
                        log.info("fontSize" + disp.getFontSize());
                    }
                }
            });

            this.disp.addMouseMotionListener(
                    new MouseAdapter() {

                        @Override
                        public void mouseDragged(MouseEvent e
                        ) {
                            super.mouseDragged(e);
                            Point2D.Float p = disp.getMouseImagePosition(e); // save the mouse point in image coordinates
                            mousePoint.x = p.x;
                            mousePoint.y = p.y;
                        }
                    }
            );

        }

    }

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d.plothistogram;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.lang.reflect.Array;

/**
 *
 * @author Thomas
 */
public class HistGCanvas extends Canvas {

    int borderLeft = 50; // px
    int borderTop = 50;  // px
    int borderBottom = 50; // px
    int CFWidth = 800; // width of cordinate frame in px
    int CFHeight = 600; // height of cordinate frame in px
    static Color[] barColors = new Color[10];
    Histogram histg;

    public HistGCanvas(Histogram histg) {
        this.histg = histg;

        barColors[0] = Color.blue;
        barColors[1] = Color.red;
        barColors[2] = Color.green;
        barColors[3] = Color.yellow;
        barColors[4] = Color.gray;
        barColors[5] = Color.cyan;
        barColors[6] = Color.orange;
        barColors[7] = Color.lightGray;
        barColors[8] = Color.pink;
        barColors[9] = Color.darkGray;
    }

    @Override
    public void paint(Graphics g) {
        if (histg != null) {
            Graphics2D g2D = (Graphics2D) g;
            g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            /*
             * Draw Coordinate frame
             */
            g2D.drawLine(borderLeft, borderTop, borderLeft, CFHeight + borderTop);
            g2D.drawLine(borderLeft, CFHeight + borderTop, CFWidth + borderLeft, CFHeight + borderTop);
            if (histg.isInitialized()) {
                int availableWidth = CFWidth / histg.nBins(); //available width per x-datapoint
                int xOffset = availableWidth / 2; // offset of the markers on x-axis
                float[] XData = histg.XData();
                float[][] YData = histg.YData();
                int nDatasets = Array.getLength(YData);
                int barWidth = (availableWidth - 6) / nDatasets;
                if (barWidth == 0) {
                    barWidth = 1;
                }
                int curXPos;
                double curRelY;
                double maxYVal = histg.maxYVal();
                if (maxYVal < 10) {
                    maxYVal = 10;
                } else {
                    double power = Math.floor(Math.log10(maxYVal));
                    double magn = Math.pow(10, power);
                    maxYVal = Math.ceil(maxYVal / magn) * magn;
                }
//                g2D.drawString(String.format("%d", 0), borderLeft-5, borderTop + CFHeight + 25);
                for (int i = 1; i <= histg.nBins(); i++) {
                    // x-axis marker
                    curXPos = borderLeft + i * availableWidth - xOffset;
                    g2D.setColor(Color.black);
                    g2D.drawLine(curXPos, CFHeight + borderTop - 5, curXPos, CFHeight + borderTop + 5);
                    /* Write XData label */
                    if((i-1)%((histg.nBins())/5)==0) {
                        g2D.drawString(String.format("%d", (int) XData[i-1]), curXPos-10, borderTop + CFHeight + 25);
                    }
                    for (int j = 0; j < nDatasets; j++) {
                        curRelY = (double) (YData[j][i - 1] / maxYVal); //relative height of current bar
                        // g2D.drawRect(curXPos-(availableWidth - 2)/2+j*barWidth, (int) 10+(int) Math.ceil((1-curRelY)*600d), barWidth, (int) Math.ceil((curRelY)*600d));
                        g2D.setColor(barColors[j]);
                        g2D.fillRect(curXPos - (barWidth * nDatasets) / 2 + j * barWidth, (int) borderTop + (int) Math.ceil((1 - curRelY) * (double) CFHeight), barWidth, (int) Math.ceil((curRelY) * (double) CFHeight));

                    }
                }

                g2D.setColor(Color.black);
                int curYPos;
                String label;
                for (double i = 0; i < 10; i++) {
                    curYPos = borderTop + (int) (i * CFHeight / 10);
                    g2D.drawLine(borderLeft - 5, curYPos, borderLeft + 5, curYPos);
                    label = String.format("%d", (int) (maxYVal * (10.0 - i) / 10.0));
                    g2D.drawString(label, 40 - 6 * label.length(), curYPos + 5);
                }
                g2D.drawString(String.format("%d", 0), 40, borderTop + CFHeight + 5);
            }
        }
    }
}


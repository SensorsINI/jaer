/*
 * DynamicFontSizeJLabel.java
 *
 * Created on November 3, 2006, 9:11 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright November 3, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.graphics;

import java.awt.Container;
import java.awt.Font;

import javax.swing.JLabel;

/**
 * A JLabel that resizes its own font to match the label size
 * @author tobi
 */
public class DynamicFontSizeJLabel extends JLabel {
    
    public static final int MIN_FONT_SIZE=13, MAX_FONT_SIZE=36;
    private Font currentFont=null;
    private long lastResizeTime=System.currentTimeMillis();

    public DynamicFontSizeJLabel() {
        super();
//        setFont(new java.awt.Font("Bitstream Vera Sans Mono 11 Bold", 0, 11));
        setFont(new java.awt.Font("Monospaced", Font.BOLD, 24));
//        setFont(new java.awt.Font("Arial Narrow", Font.BOLD, 24));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
    }
    
    private void formComponentResized(java.awt.event.ComponentEvent evt) {
        if(System.currentTimeMillis()-lastResizeTime<500) return; // don't resize too often or we can generate a storm of component resizing
        // handle label font sizing here
        double labelWidth=getWidth(); // width of text (approx)
        double parentWidth=getParent().getWidth(); // width of panel holding label
        
        if(labelWidth<200) labelWidth=200;
        double r=labelWidth/parentWidth;  // r is ratio text/container, 2 means text 2 times too large
        if(r>.8 && r<.95) return;
        final double mn=.3, mx=2.3;
        if(r<mn) r=mn; if(r>mx) r=mx;
        final double ratio=0.9; // label should be this fraction of width, we undersize to prevent cycling
        
        Font f=getFont();
        int size=f.getSize(); // old font size
        int newsize=(int)Math.floor(size/r*ratio); // new size is old size divided by width ratio
        if(newsize<MIN_FONT_SIZE) newsize=MIN_FONT_SIZE; if(newsize>MAX_FONT_SIZE) newsize=MAX_FONT_SIZE;
        if(size==newsize) return;
//        System.out.println("labelWidth="+labelWidth+" parentWidth="+parentWidth+" newsize="+newsize+" string="+getText());
        currentFont=f.deriveFont((float)newsize);
        setFont(currentFont);
        lastResizeTime=System.currentTimeMillis();
        
    }
    
    @Override
    public void setText(String str) {
//        System.out.println("setText(\""+str+"\")");
        super.setText(str);
        Container parent=getParent();
        if(parent!=null){
            int w=getWidth();
            int p=parent.getWidth();
            if(w>=p){
                formComponentResized(null);
            }
        }
    }
    
}

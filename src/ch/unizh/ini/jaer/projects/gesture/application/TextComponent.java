/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.application;

import java.awt.*;

/**
 *
 * @author Jun Haeng Lee
 */
public class TextComponent extends GestureGUIComponent {
    private String str = null;
    private Font font = new Font("Arial", Font.PLAIN, 24);
    private Color color = Color.BLACK;

    public TextComponent(String str){
        super();

        this.str = str;
        update();
    }

    public TextComponent(String str, Font font){
        super();

        this.str = str;
        setTextFont(font);
    }

    public TextComponent(String str, Font font, Color color){
        super();

        this.str = str;
        this.color = color;
        setTextFont(font);
    }

    private void update(){
        FontMetrics fm = getFontMetrics(font);

        rect.width = fm.stringWidth(str);
        rect.height = fm.getHeight();
        if( pos != null )
            this.setPosition(pos);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(color);
        g2d.setFont(font);
        g2d.drawString(str, rect.x, (int) rect.getMaxY());
    }

    public void replaceString(String str){
        this.str = str;
        update();
        repaint();
    }

    public void appendString(String str){
        this.str += str;
        update();
        repaint();
    }

    public void setTextFont(Font font){
        this.font = font;
        update();
    }

    public Font getTextFont(){
        return font;
    }

    public Color getTextColor(){
        return color;
    }

    public void setTextColor(Color color){
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public String getStr() {
        return str;
    }
}

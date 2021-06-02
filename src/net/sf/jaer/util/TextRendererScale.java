/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

/**
 * Static methods for scaling TextRenderer text to a desired size relative to
 * jAER AEViewer AEChip display.
 *
 * @author tobi
 */
public class TextRendererScale {

    /**
     * Computes the required scale used in TextRenderer.draw3D
     *
     * @param textRenderer the TextRenderer
     * @param string the rendered String
     * @param screenPixelsPerChipPixel the scaling of screen pixels per chip
     * pixel, e.g. chip.getCanvas().getScale()
     * @param pixelWidth the width (or height), generally in AEChip pixels if rendering onto AEViewer display
     * @param fractionToFill the fraction of pixelWidth to fill with string
     * @return the scale that should be passed to draw3D to render text
     *
     */
    public static float draw3dScale(TextRenderer textRenderer, String string, float screenPixelsPerChipPixel, int pixelWidth, float fractionToFill) {
        FontRenderContext frc = textRenderer.getFontRenderContext();
        Rectangle2D r = textRenderer.getBounds(string);
        Rectangle2D rt = frc.getTransform().createTransformedShape(r).getBounds2D();
        float ps = screenPixelsPerChipPixel;
        float w = (float) rt.getWidth() * ps;
        float scale = fractionToFill * ps * pixelWidth / w;
        return scale;
    }

}

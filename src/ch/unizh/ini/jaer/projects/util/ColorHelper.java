package ch.unizh.ini.jaer.projects.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 *
 * @author: http://www.java2s.com/Code/Java/2D-Graphics-GUI/HSVtoRGB.htm
 */

public class ColorHelper
{
    private final static int H_STEPS = 12;

    private final static int S_STEPS = 3;

    private final static float S_STEP_VALUE = 0.05f;

    private final static float S_MIN_VALUE = 0.15f;

    private final static List<String> staticColors ;

    static
    {
        List<String> colors = new ArrayList<String>();
        float v = 1.0f;
        for (int j = 0; j < H_STEPS; j++)
        {
            float h = j * 6.0f / H_STEPS;
            for (int i = 0; i < S_STEPS; i++)
            {
                float s = S_MIN_VALUE + (i * S_STEP_VALUE);

                float[] rgb = HSVtoRGB(h, s, v);

                int r = (int) (rgb[0] * 255.0f);
                int g = (int) (rgb[1] * 255.0f);
                int b = (int) (rgb[2] * 255.0f);

                String col = Integer.toHexString((r << 16) + (g << 8) + b);
                col = "000000".substring(col.length()) + col;
                colors.add("#"+col);
            }
        }
        
        staticColors = Collections.unmodifiableList(colors);
    }
    
    public int  getColorIndexFor(String ident)
    {
        return Math.abs(ident.hashCode()) % staticColors.size(); 
    }

    /** Give RGB from HSB (hue saturation value)
     * 
     * @param h H is given on [0->6] or -1
     * @param s S and V are given on [0->1].
     * @param v S and V are given on [0->1].
     * @return RGB vector 0-1 range
     */
    public static float[] HSVtoRGB(float h, float s, float v)
    {
        // H is given on [0->6] or -1. S and V are given on [0->1].
        // RGB are each returned on [0->1].
        float m, n, f;
        int i;

        float[] hsv = new float[3];
        float[] rgb = new float[3];

        hsv[0] = h;
        hsv[1] = s;
        hsv[2] = v;

        if (hsv[0] == -1)
        {
            rgb[0] = rgb[1] = rgb[2] = hsv[2];
            return rgb;
        }
        i = (int) (Math.floor(hsv[0]));
        f = hsv[0] - i;
        if (i % 2 == 0)
        {
            f = 1 - f; // if i is even
        }
        m = hsv[2] * (1 - hsv[1]);
        n = hsv[2] * (1 - hsv[1] * f);
        switch (i)
        {
            case 6:
            case 0:
                rgb[0] = hsv[2];
                rgb[1] = n;
                rgb[2] = m;
                break;
            case 1:
                rgb[0] = n;
                rgb[1] = hsv[2];
                rgb[2] = m;
                break;
            case 2:
                rgb[0] = m;
                rgb[1] = hsv[2];
                rgb[2] = n;
                break;
            case 3:
                rgb[0] = m;
                rgb[1] = n;
                rgb[2] = hsv[2];
                break;
            case 4:
                rgb[0] = n;
                rgb[1] = m;
                rgb[2] = hsv[2];
                break;
            case 5:
                rgb[0] = hsv[2];
                rgb[1] = m;
                rgb[2] = n;
                break;
        }

        return rgb;

    }
    
    public static List<String> getColors()
    {
        return staticColors;
    }
}
    
    

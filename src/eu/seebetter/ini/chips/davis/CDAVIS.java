/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * CDAVIS camera with heterogenous mixture of DAVIS and RGB APS global shutter
 * pixels camera
 *
 * @author Chenghan Li, Luca Longinotti, Tobi Delbruck
 */
@Description("CDAVIS APS-DVS camera with RGBW CFA color filter array and 640x480 APS pixels and 320x240 DAVIS pixels")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class CDAVIS extends DavisBaseCamera {

    public static final short WIDTH_PIXELS = 640;
    public static final short HEIGHT_PIXELS = 480;
    public static final ColorFilter[] COLOR_FILTER = {ColorFilter.B, ColorFilter.W, ColorFilter.R, ColorFilter.G};
    public static final float[][] COLOR_CORRECTION = {{1.75f, -0.19f, -0.56f, 0.15f}, {-0.61f, 1.39f, 0.07f, 0.21f},
    {-0.42f, -1.13f, 2.45f, 0.18f}};

    public CDAVIS() {
        setName("CDAVIS");
        setDefaultPreferencesFile("biasgenSettings/CDAVIS/CDAVIS.xml");
        setSizeX(CDAVIS.WIDTH_PIXELS);
        setSizeY(CDAVIS.HEIGHT_PIXELS);
        setPixelHeightUm(10);
        setPixelWidthUm(10); // subpixel size, APS pixel spacing

        setEventExtractor(new DavisColorEventExtractor(this, true, false, CDAVIS.COLOR_FILTER, false));

        davisRenderer = new DavisColorRenderer(this, true, CDAVIS.COLOR_FILTER, true, CDAVIS.COLOR_CORRECTION);
        davisRenderer.setMaxADC(DavisChip.MAX_ADC);
        setRenderer(davisRenderer);
        
        setBiasgen(davisConfig = new CDAVISConfig(this));


        setApsFirstPixelReadOut(new Point(0, 0));
        setApsLastPixelReadOut(new Point(getSizeX() - 1, getSizeY() - 1));
    }

    public CDAVIS(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        davisMenu.add(new JSeparator());
        davisMenu.add(new JCheckBoxMenuItem(new Monochrome()));
        davisMenu.add(new JCheckBoxMenuItem(new SeparateColors()));
        davisMenu.add(new JCheckBoxMenuItem(new ColorCorrection()));
        davisMenu.add(new JCheckBoxMenuItem(new AutoWhiteBalance()));
    }

    final public class SeparateColors extends DavisMenuAction {

        public SeparateColors() {
            super("Separate Colors",
                    "<html>Shows the RGBW color channels as separate subframes",
                    "ToggleSeparateColors");
            putValue(Action.SELECTED_KEY, getDavisConfig().isSeparateAPSByColor());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = getDavisConfig().isSeparateAPSByColor();
            getDavisConfig().setSeparateAPSByColor(!old);
            davisDisplayMethod.showActionText("separate colors = " + getDavisConfig().isSeparateAPSByColor());
            putValue(Action.SELECTED_KEY, !old);
        }
    }

    final public class ColorCorrection extends DavisMenuAction {

        public ColorCorrection() {
            super("Color Correction",
                    "<html>Applies color correction to RGB raw values",
                    "ToggleColorCorrection");
            putValue(Action.SELECTED_KEY, getDavisConfig().getVideoControl().isColorCorrection());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = getDavisConfig().getVideoControl().isColorCorrection();
            getDavisConfig().getVideoControl().setColorCorrection(!old);
            davisDisplayMethod.showActionText("color correction = " + getDavisConfig().getVideoControl().isColorCorrection());
            putValue(Action.SELECTED_KEY, !old);
        }
    }

    final public class AutoWhiteBalance extends DavisMenuAction {

        public AutoWhiteBalance() {
            super("Auto White Balance",
                    "<html>Applies automatic white balancing",
                    "ToggleAutoWhiteBalance");
            putValue(Action.SELECTED_KEY, getDavisConfig().getVideoControl().isAutoWhiteBalance());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = getDavisConfig().getVideoControl().isAutoWhiteBalance();
            getDavisConfig().getVideoControl().setAutoWhiteBalance(!old);
            davisDisplayMethod.showActionText("auto white balance = " + getDavisConfig().getVideoControl().isAutoWhiteBalance());
            putValue(Action.SELECTED_KEY, !old);
        }
    }

    final public class Monochrome extends DavisMenuAction {

        public Monochrome() {
            super("Monochrome",
                    "<html>Displays output as monochrome; use with wafer split chips without color filters",
                    "Monochrome");
            putValue(Action.SELECTED_KEY, getDavisConfig().getVideoControl().isMonochrome());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = getDavisConfig().getVideoControl().isMonochrome();
            getDavisConfig().getVideoControl().setMonochrome(!old);
            davisDisplayMethod.showActionText("monochrome = " + getDavisConfig().getVideoControl().isMonochrome());
            putValue(Action.SELECTED_KEY, getDavisConfig().getVideoControl().isMonochrome());
        }
    }

}

/*
 * Copyright (C) 2018 tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.npp;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.avioutput.DvsSliceAviWriter;

/**
 * Incremental Roshambo learning demo
 *
 * @author Tobi Delbruck/Iulia Lungu
 */
@Description("Incremental learning demo for Roshambo + other finger gestures; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RoShamBoIncremental extends RoShamBoCNN {

    private DvsSliceAviWriter aviWriter = null;
    private Path lastSymbolsPath = Paths.get(getString("lastSymbolsPath", ""));

    public RoShamBoIncremental(AEChip chip) {
        super(chip);
        String learn = "0. Incremental learning";
        setPropertyTooltip(learn, "LearnSymbol0", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol1", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol2", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol3", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol4", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "LearnSymbol5", "Toggle collecting symbol data");
        setPropertyTooltip(learn, "ChooseSymbolsFolder", "Choose a folder to store the symbol AVI data files");
        aviWriter = new DvsSliceAviWriter(chip);
        aviWriter.setEnclosed(true, this);
        aviWriter.setFrameRate(60);
        aviWriter.getDvsFrame().setOutputImageHeight(64);
        aviWriter.getDvsFrame().setOutputImageWidth(64);
        getEnclosedFilterChain().add(aviWriter);
    }

    public void doChooseSymbolsFolder() {
        JFileChooser c = new JFileChooser(lastSymbolsPath.toFile());
        c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        c.setDialogTitle("Choose folder to store symbol AVIs");
        c.setApproveButtonText("Select");
        c.setApproveButtonToolTipText("Selects a folder to store AVIs");
        int ret = c.showOpenDialog(chip.getFilterFrame());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
      
        lastSymbolsPath = Paths.get(c.getSelectedFile().toString());
        putString("lastSymbolsPath",lastSymbolsPath.toString());

    }

    public void doToggleOnLearnSymbol0() {
        log.info("recording symbol");
        String sym="symbol0";
        aviWriter.openAVIOutputStream(lastSymbolsPath.resolve(sym+".avi").toFile(), new String[]{"# "+sym});

    }

    public void doToggleOffLearnSymbol0() {
        log.info("stopping symbol, starting training");
        aviWriter.doCloseFile();
    }

   public void doToggleOnLearnSymbol1() {
        log.info("recording symbol");
        String sym="symbol1";
        aviWriter.openAVIOutputStream(lastSymbolsPath.resolve(sym+".avi").toFile(), new String[]{"# "+sym});

    }

    public void doToggleOffLearnSymbol1() {
        log.info("stopping symbol, starting training");
        aviWriter.doCloseFile();
    }

   public void doToggleOnLearnSymbol2() {
        log.info("recording symbol");
        String sym="symbol2";
        aviWriter.openAVIOutputStream(lastSymbolsPath.resolve(sym+".avi").toFile(), new String[]{"# "+sym});

    }

    public void doToggleOffLearnSymbol2() {
        log.info("stopping symbol, starting training");
        aviWriter.doCloseFile();
    }

}

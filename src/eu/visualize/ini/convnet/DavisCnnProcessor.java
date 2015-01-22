/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Computes CNN from DAVIS APS frames.
 *
 * @author tobi
 */
@Description("Computes CNN from DAVIS APS frames")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisCnnProcessor extends EventFilter2D {

    private String filename = getString("filename", "LCRN_cnn.xml");

    public DavisCnnProcessor(AEChip chip) {
        super(chip);
    }

    /**
     * Loads a convolutional neural network (CNN) trained using DearLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    public void doLoadNetworkFromXML() {
        File f = new File(filename);
        EasyXMLReader networkReader = new EasyXMLReader(f);
        if (!networkReader.hasFile()) {
            log.warning("No file for reader; file=" + networkReader.getFile());
            return;
        }
        filename=networkReader.getFile().toString();
        putString("filename", filename);

        StringBuilder sb = new StringBuilder("network information: \n");
        String netname=networkReader.getRaw("name");
        String notes=networkReader.getRaw("notes");
        String dob=networkReader.getRaw("dob");
        String nettype=networkReader.getRaw("type");
        if(!nettype.equals("cnn")){
            log.warning("network type is not cnn");
        }
        sb.append(String.format("name=%s, dob=%s, type=%s\nnotes=%s\n",netname,dob,nettype,notes));
        int numLayers = networkReader.getNodeCount("Layer");
        sb.append(String.format("numLayers=%d\n", numLayers));

        for (int i = 0; i < numLayers; i++) {
            EasyXMLReader layerReader = networkReader.getNode("Layer", i);
            int index = layerReader.getInt("index");
            String type = layerReader.getRaw("type");
            switch (type) {
                case "i": {
                    int dimx = layerReader.getInt("dimx");
                    int dimy = layerReader.getInt("dimy");
                    int nUnits = layerReader.getInt("nUnits");
                    sb.append(String.format("index=%d Input layer; dimx=%d dimy=%d nUnits=%d\n", index, dimx, dimy, nUnits));
                }
                break;
                case "c": {
                    int inputMaps = layerReader.getInt("inputMaps");
                    int outputMaps = layerReader.getInt("outputMaps");
                    int kernelSize = layerReader.getInt("kernelSize");
                    float[] biases = layerReader.getBase64FloatArr("biases");
                    float[] kernels = layerReader.getBase64FloatArr("kernels");
                    sb.append(String.format("index=%d CNN   layer; inputMaps=%d outputMaps=%d kernelSize=%d\n", index, inputMaps, outputMaps, kernelSize));
                }
                break;
                case "s": {
                    int averageOver = layerReader.getInt("averageOver");
                    float[] biases = layerReader.getBase64FloatArr("biases");
                    sb.append(String.format("index=%d Subsamp layer; averageOver=%d \n", index, averageOver));

                }
                break;
            }
        }
        float[] outputBias = networkReader.getBase64FloatArr("outputBias");
        float[] outputWeights = networkReader.getBase64FloatArr("outputWeights");
        sb.append(String.format("Output: bias=float[%d] outputWeights=float[%d] \n", outputBias.length, outputWeights.length));
        log.info(sb.toString());
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

}

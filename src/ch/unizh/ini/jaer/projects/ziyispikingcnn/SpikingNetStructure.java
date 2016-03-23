package ch.unizh.ini.jaer.projects.ziyispikingcnn;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ziyihua on 15/11/15.
 */
public class SpikingNetStructure {

    public static class network{
        public network(){
            layers = new ArrayList<LAYER>();
        }

        List<LAYER> layers;

        float[] ffb;
        float[][] ffw;
        float[] fv;
        float[] sum_fv;
        float[] o_mem;
        float[] o_refrac_end;
        double[] o_sum_spikes;
        int[] o_spikes;
    }

    public static class LAYER{
        public LAYER(){
            k = new ArrayList<>();
            m = new ArrayList<>();
            r = new ArrayList<>();
            s = new ArrayList<>();
            sp = new ArrayList<>();
        }

        float[] b;
        List<float[][]> k;
        List<float[][]> m;
        List<float[][]> r;
        List<float[][]> s;
        List<float[][]> sp;

        String type;
        int outmaps;
        int inmaps;
        int kernelsize;
        int scale;
        int dimx;
        int dimy;
    }
}

/*
 * HmmTools.java
 *
 * Created on 2. Januar 2008, 01:58
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead;

import java.util.Vector;

/**
 * This class contains some methods that are used by the Filter HMM Filter, 
 * which recognizes sound patterns with a Hidden Markov Model.
 *
 * @author Administrator
 */
public class HmmTools {
    
    int W[][][][][];
    public double[][] TR_Left;
    public double[][] TR_Right;
    public double[][] EMIS_Left;
    public double[][] EMIS_Right;
    public double [][] DATA;
    public double [][] SOUND_Left;
    public double [][] SOUND_Right;
    public double [][] DECODE_Left;
    public double [][] DECODE_Right;
    
    
    
    public int[] LUT;
    
    
    /** Creates a new instance of HmmTools */
    public HmmTools(int maxVal) {
        //genCodeArray(maxVal);
        
    }
    
    public void genCodeArray(int maxVal){
        int W[][][][][]=new int[maxVal+1][maxVal+1][maxVal+1][maxVal+1][maxVal+1];
        int count =0;
        for (int a=0; a<=maxVal; a++){
            for (int b=0; b<=maxVal; b++){
                for (int c=0; c<=maxVal; c++){
                    for (int d=0; d<=maxVal; d++){
                        for (int e=0; e<=maxVal; e++){
                            W[a][b][c][d][e]=(int)DECODE_Left[0][count];
                            count=count+1;
                        }
                    }
                }
            }
        }
        this.W=W;
    }
    public int getObservation(int a,int b, int c, int d, int e){
        return this.W[a][b][c][d][e];
    }
    public void loadHmmData(){
        String path = "c:\\ETH\\RobotHead\\HMM_Data\\hmmRealModels\\BD\\1\\";
        ArrayReader myReader = new ArrayReader();
        
        TR_Left = myReader.readArray(path,"TR_Left.txt");
        TR_Right = myReader.readArray(path,"TR_Right.txt");
        EMIS_Left = myReader.readArray(path,"EMIS_Left.txt");
        EMIS_Right = myReader.readArray(path,"EMIS_Right.txt");
        DATA = myReader.readArray(path,"DATA.txt");
        SOUND_Left = myReader.readArray(path,"SOUNDS_Left.txt");
        SOUND_Right = myReader.readArray(path,"SOUNDS_Right.txt");
        DECODE_Left = myReader.readArray(path,"DECODE_Left.txt");
        DECODE_Right = myReader.readArray(path,"DECODE_Right.txt");
        
        
    }
    
    public void genSoundLUT(){
        
        int numSounds=SOUND_Left[0].length;
        int ns=(int)SOUND_Left[1][numSounds-1]-1;
        
        LUT= new int[ns+1];
        LUT[0]=0;   // 1st state belongs always to no sound (sound 0)
        
        for (int i=0; i<numSounds; i++){
            int minS=(int)SOUND_Left[0][i]-1;
            int maxS=(int)SOUND_Left[1][i]-1;
            for(int j=minS; j<=maxS; j++){
                LUT[j]=i+1;
            }
        }
        System.out.println("LUT");
        for(int i=0;i<LUT.length;i++)
            System.out.print(LUT[i]+" ");
           
                
        
    }
    /**
     * Viterbi algorithm for hidden Markov models. Given an
     * observation sequence o, Viterbi finds the best possible
     * state sequence for o on this HMM, along with the state
     * optimized probability.
     *
     * @param obsBuffer 		the observation sequence
     * @return		a 2d array consisting of the minimum cost, U,
     *				at position (0,0) and the best possible path
     *				beginning at (1,0). The state optimized
     *				probability is Euler's number raised to the
     *				power of -U.
     */
    public double[][] viterbi(Vector obsBuffer, int piState, double a[][], double b[][]) {
        
        int numStates=a.length;     // number of States
        
        int[] o=new int[obsBuffer.size()];
        for(int i=0; i<obsBuffer.size();i++){
            
            o[i] = ((Integer)obsBuffer.elementAt(i)).intValue()-1;
            
        }
        double pi[]= new double[numStates];
        //pi[piState]=0.9;
        
        pi[0]=0.9;
        double rest=0.1/(numStates-1);
        for (int ii=1; ii<numStates; ii++){
            pi[ii]=rest;
        }
        
        int T = o.length;
        int min_state;
        double min_weight, weight;
        double stateOptProb = 0.0;
        int[] Q = new int[T];
        int[][] sTable = new int[numStates][T];
        double[][] aTable = new double[numStates][T];
        double[][] answer = new double[2][T];
        
        // calulate accumulations and best states for time 0
        for(int i = 0; i < numStates; i++) {
            aTable[i][0] = -1*Math.log(pi[i]) - Math.log(b[i][o[0]]);
            sTable[i][0] = 0;
        }
        
        // fill up the rest of the tables
        for(int t = 1; t < T; t++) {
            for(int j = 0; j < numStates; j++) {
                min_weight = aTable[0][t-1] - Math.log(a[0][j]);
                min_state = 0;
                for(int i = 1; i < numStates; i++) {
                    weight = aTable[i][t-1] - Math.log(a[i][j]);
                    if(weight < min_weight) {
                        min_weight = weight;
                        min_state = i;
                    }
                }
                aTable[j][t] = min_weight - Math.log(b[j][o[t]]);
                sTable[j][t] = min_state;
            }
        }
        
        // find minimum value for time T-1
        min_weight = aTable[0][T-1];
        min_state = 1;
        for(int i = 1; i < numStates; i++) {
            if(aTable[i][T-1] < min_weight) {
                min_weight = aTable[i][T-1];
                min_state = i;
            }
        }
        
        // trace back to find the optimized state sequence
        Q[T-1] = min_state;
        for(int t = T-2; t >= 0; t--)
            Q[t] = sTable[Q[t+1]][t+1];
        
        // store answers and return them
        answer[0][0] = min_weight;
        for(int i = 0; i < T; i++)
            answer[1][i] = Q[i];
        return answer;
    }

    
    
}

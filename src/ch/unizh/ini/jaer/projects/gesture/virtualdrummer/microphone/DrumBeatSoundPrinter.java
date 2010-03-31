package ch.unizh.ini.jaer.projects.gesture.virtualdrummer.microphone;



public class DrumBeatSoundPrinter implements DrumBeatSoundEventListener  {

    private int N=5;
    private int n=0;
//    long startTime=System.currentTimeMillis();
 
    /** called by spike source ({@link SpikeReporter}) when a spike is detected
     *      @param e the spike event
     *
     */
    public void drumBeatSoundOccurred(DrumBeatSoundEvent e) {
        System.out.print(e.getTime()+"\t");
        if(++n%N==0){
            n=0; System.out.println("");
        }
    }

}

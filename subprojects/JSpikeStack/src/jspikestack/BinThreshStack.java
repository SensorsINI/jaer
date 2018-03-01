package jspikestack;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author oconnorp
 */
public class BinThreshStack extends Network<Axon> {
    
    
     public BinThreshStack (Axon.AbstractFactory layerFac,Unit.AbstractFactory unitFac)
    {   super(layerFac,unitFac);
    
               
        
    };
    
    
    /** Eat up the events in the input queue until some timeout */
    @Override
    public void eatEvents(int timeout)
    {   
        // If in liveMode, go til inputBuffer is empty, otherwise go til both buffers are empty (or timeout).
        while (!(inputBuffer.isEmpty()&&(internalBuffer.isEmpty() || liveMode )) && enable)
        {
                        
            // Determine whether to read from input or buffer
            boolean readInput=!inputBuffer.isEmpty() && (internalBuffer.isEmpty() || inputBuffer.peek().hitTime<internalBuffer.peek().hitTime);
            PSP ev=readInput?inputBuffer.poll():internalBuffer.poll();
            
            // Update current time to time of this event
            if (ev.hitTime<time)
            {   System.out.println("Input Spike time Decrease detected!  Resetting network...");
                reset();                
            }
            
            time=ev.hitTime;
            
            
            if (time > timeout)
                break;
            
            try{
            
                ev.affect(this);
                
                // Feed Spike to network, add to ouput queue if they're either either forced spikes or internally generated spikes
//                if (inputCurrents && readInput)     // 1: Input event drives current
//                {    lay(ev.layer).fireInputTo(ev);
//                
//                }
//                else if (readInput)                 // 2: Input Spike fires unit
//                {   lay(ev.layer).fireFrom(ev.addr);
//                    outputQueue.add(ev);
//                }
//                else                                // 3: Internally buffered event propagated
//                {  // lay(ev.layer).propagateFrom(ev, ev.addr);
//                    outputQueue.add(ev);
//                }
//                // Post Spike-Feed Actions
//                digest();
            
            }
            catch (java.lang.ArrayIndexOutOfBoundsException ex)
            {   
//                System.out.println("You tried firing an event at address with address "+ev.addr+" to Layer "+ev.layer+", which has just "+lay(ev.layer).nUnits()+" units.");
                throw new java.lang.ArrayIndexOutOfBoundsException("You tried firing an event at address with address "+ev.sp.addr+" to Layer "+ev.sp.layer+", which has just "+lay(ev.sp.layer).nUnits()+" units.");
            }
            
            
        }
        
        enable=true;  // Re-enable network when done.
    }
    
    
}

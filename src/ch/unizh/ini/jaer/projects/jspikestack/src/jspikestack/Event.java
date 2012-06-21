/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author oconnorp
 */
/* Basic "Event" class */
    public class Event
    {   int addr;   
        double time; // Time in millis
        int layer;
        public Event(int addri,double timei,int layeri)
        {   addr=addri;
            time=timei;
            layer=layeri;
        }
    }    
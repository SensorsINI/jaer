/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.labyrinthkalman;

import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;


/**
 * Pixel Exhaustion with exponential exhaustion decay.
 * Pixel with exhaustion k transmits events with a
 * probability proportional to (1/(k+1)) or so.
 * @author lorenz
 */
public final class PixelExhaustion extends EventFilter2D implements Observer{
    // exhaustion of each pixel
	int cameraX;
	int cameraY;
	float[][] exhaustion;
        float maxValue;

    //measure time for decay
        float timeStamp = 0;

    //parameters
        private float   decay          = getPrefs().getFloat("PixelExhaustion.decay", 1.0f);


        public boolean isGeneratingFilter() {
		return false;
	}

	public PixelExhaustion(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		resetFilter();
	}

	public Object getFilterState() {
		return null;
	}

    @Override
	public void resetFilter() {
		initExhaustion();
	}


	final class Coordinate {

		public float x, y;

		Coordinate(){
			this.x = 0;
			this.y = 0;
		}

		Coordinate(float x, float y){
			this.x = x;
			this.y = y;
		}

		public void setCoordinate(float x, float y){
			this.x = x;
			this.y = y;
		}
	}

	synchronized private void initExhaustion() {

		System.out.println("PixelExhaustion initialising...");

                exhaustion = new float[chip.getSizeX()][chip.getSizeY()];

		if(chip.getSizeX()==0 || chip.getSizeY()==0){
			return;
		}

		cameraX = chip.getSizeX();
		cameraY = chip.getSizeY();

		for(int i=0;i<chip.getSizeX();i++){
			for(int j=0; j < chip.getSizeY();j++){
				exhaustion[i][j]=1;
			}
		}

	}

    @Override
	public void initFilter() {
		resetFilter();
	}

    @Override
	public void update(Observable o, Object arg) {
		if(!isFilterEnabled()) return;
		initFilter();
       }

        public float getDecay() {
            return decay;
        }


        synchronized public void setDecay(float decay){
            if(decay < 0) decay = 0;

            if(decay != this.decay)
            {
                resetFilter();
            }

            this.decay = decay;
       }

	

        synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {

		if (!isFilterEnabled())
			return in;
       

                if (in == null || in.getSize() == 0)
                    return in;

                
                float delta_t = in.getLastTimestamp() - timeStamp;
                timeStamp = in.getLastTimestamp();
                
                float decay_factor = 1.0f/(decay * (delta_t*0.001f));

                maxValue = -1;

                for(int i = 0; i<cameraX; i++)
                {
                    for(int j = 0; j<cameraY; j++)
                    {
                        exhaustion[i][j] *= decay_factor;
                        //exhaustion[i][j] -= 0.1;
                        if(exhaustion[i][j] > maxValue) maxValue = exhaustion[i][j];
                    }
                }

                OutputEventIterator itr = out.outputIterator();
                
		for (BasicEvent event : in) {

			if (event.x < 0 || event.x > chip.getSizeX() - 1 || event.y < 0 || event.y > chip.getSizeY() - 1)
				continue;
                        short x = event.x;
                        short y = event.y;

                        //increase exhaustion

                        exhaustion[x][y] += 1.0;
                        if(exhaustion[x][y] > 2.0) exhaustion[x][y] *=5;

                        if(x-1 > 0 && x+1 < chip.getSizeX()-1
                            && y-1 > 0 && y+1 < chip.getSizeY()-1)
                        {
                        exhaustion[x][y] += 1;
                        exhaustion[x][y+1] += 1;
                        exhaustion[x][y-1] += 1;
                        exhaustion[x+1][y] += 1;
                        exhaustion[x-1][y] += 1;
                        exhaustion[x+1][y+1] += 1;
                        exhaustion[x-1][y+1] += 1;
                        exhaustion[x-1][y-1] += 1;
                        exhaustion[x-1][y-1] += 1;
                        }

                        //large exhaustion has low probability to pass the filter
                        double random = Math.random();
                        double stamina = 1.0/(exhaustion[x][y] + 1.0);

                        if(random < stamina)
                        {

                            BasicEvent outEvent = itr.nextOutput();
                            outEvent.x = x;
                            outEvent.y = y;
                            outEvent.timestamp = event.timestamp;

                        }

		}

		return out;
	}



}


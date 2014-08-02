
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

/**
 *
 * @author Bjoern
 */
 public class TrackingSuccessEvaluationPoint {
        public final String name;
        public final float newPosX, newPosY;
        public final long changeTime;
        
        public TrackingSuccessEvaluationPoint(String name,float newPosX,float newPosY,long changeTime) {
            this.name       = name;
            this.newPosX    = newPosX;
            this.newPosY    = newPosY;
            this.changeTime = changeTime;
        }
    }

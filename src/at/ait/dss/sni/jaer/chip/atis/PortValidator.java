/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ait.dss.sni.jaer.chip.atis;
import java.util.logging.Logger;
/**
 * Validates client properties for ATIS connection
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class PortValidator extends org.jdesktop.beansbinding.Validator{
    static Logger log = Logger.getLogger("ATIS304");

    @Override
    public Result validate (Object o){
        if ( o instanceof Integer ){// assume port
            try{
                Integer i = (Integer)o;
                if ( i.intValue() < 0 || i.intValue() > 65535 ){
                    log.warning("Port should be in range (0-65535), " + o + " is not a valid port value");
                    return new Result(null,"Port should be in range (0-65535), " + o + " is not a valid port value");
                }
            } catch ( Exception e ){
                log.warning("Port should be in range (0-65535), " + o + " is not a valid port value");
                return new Result(null,"Port should be in range (0-65535), " + o + " is not a valid port value");
            }
            return null;

        } else{
            log.warning("Port should be in range (0-65535), " + o + " is not a valid port value");
            return new Result(null,"Port should be in range (0-65535), " + o + " is not a valid port value");
//            throw new UnsupportedOperationException("Shouldn't have to validate " + o);
        }
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ait.dss.sni.jaer.chip.atis;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
/**
 * Validates client properties for ATIS connection
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class HostValidator extends org.jdesktop.beansbinding.Validator{
    static Logger log = Logger.getLogger("ATIS304");

    @Override
    public Result validate (Object t){
        if ( t instanceof String ){// assume host
            String s = (String)t;
            try{
                InetSocketAddress address = new InetSocketAddress(s,0);
                if ( address.isUnresolved() ){
                    log.warning("Cannot resolve hostname " + t);
                    return new Result(null,"Cannot resolve hostname " + t);
                }
            } catch ( Exception e ){
                log.warning("Cannot resolve hostname " + t + ", caught " + e.toString());
                return new Result(null,"Cannot resolve hostname " + t + ", caught " + e.toString());
            }
            return null;

        } else{
            log.warning("Cannot resolve hostname " + t);
            return new Result(null,"Cannot resolve hostname " + t);
        }
    }
}

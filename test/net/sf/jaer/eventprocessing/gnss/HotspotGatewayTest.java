package net.sf.jaer.eventprocessing.gnss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class HotspotGatewayTest {

    private static final String ROUTE = """
              0.0.0.0          0.0.0.0   25.255.255.254    10.144.159.50  10034
              0.0.0.0          0.0.0.0   10.213.206.139     10.213.206.5     50
            """;

    private static final String WLAN = """
        State                  : connected
        SSID                   : tobi-s24
        AP BSSID               : 1a:62:36:94:f9:f5
        """;

    @Test
    public void phoneHotspotGateway() {
        assertEquals("10.213.206.139",
                HotspotGateway.phoneGateway(ROUTE, WLAN, Set.of("10.213.206.5")));
    }

    @Test
    public void infrastructureApIsNotAHotspot() {
        String wlan = WLAN.replace("1a:62:36:94:f9:f5", "00:11:22:33:44:55");
        assertNull(HotspotGateway.phoneGateway(ROUTE, wlan, Set.of("10.213.206.5")));
    }

    @Test
    public void ignoresOtherAdapters() {
        assertNull(HotspotGateway.phoneGateway(ROUTE, WLAN, Set.of("10.144.159.50")));
    }

    @Test
    public void localMacBit() {
        assertTrue(HotspotGateway.locallyAdministeredMac("1a:62:36:94:f9:f5"));
        assertFalse(HotspotGateway.locallyAdministeredMac("00:11:22:33:44:55"));
        assertTrue(HotspotGateway.isPrivateLan("10.213.206.139"));
        assertFalse(HotspotGateway.isPrivateLan("25.255.255.254"));
    }
}

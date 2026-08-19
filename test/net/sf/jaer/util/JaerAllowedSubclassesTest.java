package net.sf.jaer.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;

/**
 * Allowlist gate: hostile names must not load; a real filter FQCN must.
 */
public class JaerAllowedSubclassesTest {

    @Test
    public void rejectsRuntimeAsFilter() {
        try {
            JaerAllowedSubclasses.load("java.lang.Runtime", EventFilter2D.class);
            fail("Runtime must not load as EventFilter2D");
        } catch (ClassNotFoundException e) {
            assertTrue(e.getMessage() != null && !e.getMessage().isEmpty());
        }
        assertNull(JaerAllowedSubclasses.loadOrNull("java.lang.Runtime", EventFilter2D.class));
    }

    @Test
    public void loadsKnownFilterWhenAllowlistPresentOrDevFallback() throws Exception {
        Class<?> c = JaerAllowedSubclasses.load(BackgroundActivityFilter.class.getName(), EventFilter2D.class);
        assertNotNull(c);
        assertTrue(EventFilter2D.class.isAssignableFrom(c));
    }

    @Test
    public void tensorflowOnlyAllowsNativeArtifacts() {
        assertTrue(TensorFlowNativeSupport.isAllowedNativeJarName(
                TensorFlowNativeSupport.nativeJarFileName()));
        assertTrue(TensorFlowNativeSupport.isAllowedNativeJarName(
                "tensorflow-core-native-" + TensorFlowNativeSupport.TF_VERSION + "-linux-x86_64.jar"));
        assertFalse(TensorFlowNativeSupport.isAllowedNativeJarName("evil.jar"));
        assertFalse(TensorFlowNativeSupport.isAllowedNativeJarName("not-tensorflow.jar"));
    }
}

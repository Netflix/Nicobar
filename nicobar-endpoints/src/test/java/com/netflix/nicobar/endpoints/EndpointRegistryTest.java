package com.netflix.nicobar.endpoints;


import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleLoader;

/**
 * Tests for {@link BaseEndpointRegistry}.
 *
 * @author Vasanth Asokan
 */
public class EndpointRegistryTest {

    private EndpointRegistry registry;

    @Mock
    private EndpointDatastore datastore;

    @Mock
    private ScriptModuleLoader moduleLoader;

    @Mock
    private EndpointDatastorePoller poller;

    private final String[] endpoints = new String[] { "/test/{param}/0", "/test/1", "/test/{param}/2", "/test/3/{param}", "/test/4", "/test/5" };
    private final String[] activeVersions = new String[] { "v0", null, "v2", null, "v4", "v5" };
    private final String[] latestVersions = new String[] { "v3", "v4", "v2", "v4", "v5", "v6" };
    private final long[] lastModified = new long[] { 1L, 2L, 3L, 4L, 5L, 6L };
    private Map<String, EndpointSummary> initialSummaries;

    @BeforeMethod
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        registry = new EndpointRegistry(datastore, moduleLoader, poller);
        when(datastore.getDatastoreId()).thenReturn("datastore");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInitialPollHandling() throws IOException {
        initialSummaries = createSummaries(0, 1, 2, 3, 4, 5);
        registry.pollResult(datastore.getDatastoreId(),
                initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap());

        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);

        verify(datastore, times(4)).getEndpointArchive((String)arg1.capture(), (String)arg2.capture());
        assertEquals(endpoints[0], arg1.getAllValues().get(0));
        assertEquals(endpoints[2], arg1.getAllValues().get(1));
        assertEquals(endpoints[4], arg1.getAllValues().get(2));
        assertEquals(endpoints[5], arg1.getAllValues().get(3));

        assertEquals(activeVersions[0], arg2.getAllValues().get(0));
        assertEquals(activeVersions[2], arg2.getAllValues().get(1));
        assertEquals(activeVersions[4], arg2.getAllValues().get(2));
        assertEquals(activeVersions[5], arg2.getAllValues().get(3));

        // Verify that the endpoints known to the registry is valid
        Set<EndpointURI> endpointSet = registry.getAllEndpoints();
        for (int i = 0; i < endpoints.length; i++) {
            assertTrue(endpointSet.contains(new EndpointURI(endpoints[i])));
        }

        // Verify that the moduleLoader was updated and queried for 4 active archives
        verify(moduleLoader, times(4)).updateScriptArchives(any(Set.class));
        verify(moduleLoader, times(4)).getScriptModule(any(ModuleId.class));

        // Active set should be zero since we haven't enabled the mockScriptModuleLoader
        Set<EndpointURI> activeSet = registry.getActiveEndpoints();
        assertTrue(activeSet.size() == 0);
    }

    /**
     * We will deactivate and activate some endpoints
     *
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatedActivationsProcessing() throws IOException {
        initialSummaries = createSummaries(0, 1, 2, 3, 4, 5);
        // Setup the module loader to pretend to process archives, and return valid modules
        ScriptModule returnedModule = mock(ScriptModule.class);
        Set<Class<?>> loadedClasses = new HashSet<Class<?>>();
        loadedClasses.add(TestEndpoint.class);
        when(returnedModule.getLoadedClasses()).thenReturn(loadedClasses);
        when(moduleLoader.getScriptModule(any(ModuleId.class))).thenReturn(returnedModule);

        // Send initial poll result
        registry.pollResult(datastore.getDatastoreId(),
                initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap());

        // Reset mocks before testing the update processing.
        Mockito.reset(datastore);
        Mockito.reset(moduleLoader);
        when(moduleLoader.getScriptModule(any(ModuleId.class))).thenReturn(returnedModule);
        when(datastore.getDatastoreId()).thenReturn("datastore");

        // Activate 3
        Map<String, EndpointSummary> modifiedSummaries = createSummaries(3);
        modifiedSummaries.get(endpoints[3]).setActiveVersion("v1");

        // Send second poll result with activations
        registry.pollResult(datastore.getDatastoreId(),
                Collections.<String, EndpointSummary>emptyMap(),
                modifiedSummaries,
                Collections.<String, EndpointSummary>emptyMap());

        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);

        InOrder inOrder1 = Mockito.inOrder(datastore);
        InOrder inOrder2 = Mockito.inOrder(moduleLoader);

        // Verify that the datastore was queried, and the module loader sent an update
        inOrder1.verify(datastore, times(1)).getEndpointArchive(arg1.capture(), arg2.capture());
        assertEquals(endpoints[3], arg1.getValue());
        assertEquals("v1", arg2.getValue());
        inOrder2.verify(moduleLoader, times(1)).updateScriptArchives(any(Set.class));
        inOrder2.verify(moduleLoader, times(1)).getScriptModule(ModuleId.create(endpoints[3], "v1"));

        // Now verify that the active count went up by 1
        assertTrue(registry.getActiveEndpoints().size() == 5);
        assertTrue(registry.getActiveEndpoints().contains(new EndpointURI(endpoints[3])));

        // Deactivate 3
        modifiedSummaries = createSummaries(2);
        modifiedSummaries.get(endpoints[2]).setActiveVersion(null);

        // Send third poll result with deactivations
        registry.pollResult(datastore.getDatastoreId(),
                Collections.<String, EndpointSummary>emptyMap(),
                modifiedSummaries,
                Collections.<String, EndpointSummary>emptyMap());

        verify(moduleLoader, times(1)).removeScriptModule(ModuleId.create(endpoints[2], activeVersions[2]));
        // Now verify that the active count came down by 1
        assertTrue(registry.getActiveEndpoints().size() == 4);
        assertFalse(registry.getActiveEndpoints().contains(new EndpointURI(endpoints[2])));
    }


    /**
     * We will add and  remove some endpoints
     *
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAddRemoveProcessing() throws IOException {
        initialSummaries = createSummaries(0, 1, 2, 4);
        // Setup the module loader to pretend to process archives, and return valid modules
        ScriptModule returnedModule = mock(ScriptModule.class);
        Set<Class<?>> loadedClasses = new HashSet<Class<?>>();
        loadedClasses.add(TestEndpoint.class);
        when(returnedModule.getLoadedClasses()).thenReturn(loadedClasses);
        when(moduleLoader.getScriptModule(any(ModuleId.class))).thenReturn(returnedModule);

        // Send initial poll result
        registry.pollResult(datastore.getDatastoreId(),
                initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap());

        // Reset mocks before testing the update processing.
        Mockito.reset(datastore);
        Mockito.reset(moduleLoader);
        when(moduleLoader.getScriptModule(any(ModuleId.class))).thenReturn(returnedModule);
        when(datastore.getDatastoreId()).thenReturn("datastore");
        // endpoints[5] should not be in the registry of known endpoints
        assertFalse(registry.getAllEndpoints().contains(new EndpointURI(endpoints[5])));

        // Add 3 and 5. 3 is inactive, 5 is active.
        Map<String, EndpointSummary> addedSummaries = createSummaries(3, 5);

        // Send second poll result with adds
        registry.pollResult(datastore.getDatastoreId(),
                addedSummaries,
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap());

        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);

        InOrder inOrder1 = Mockito.inOrder(datastore);
        InOrder inOrder2 = Mockito.inOrder(moduleLoader);

        // Verify that the datastore was queried, and the module loader sent an update
        inOrder1.verify(datastore, times(1)).getEndpointArchive(arg1.capture(), arg2.capture());
        assertEquals(endpoints[5], arg1.getValue());
        assertEquals(activeVersions[5], arg2.getValue());
        inOrder2.verify(moduleLoader, times(1)).updateScriptArchives(any(Set.class));
        inOrder2.verify(moduleLoader, times(1)).getScriptModule(ModuleId.create(endpoints[5], activeVersions[5]));
        // endpoints[5] and endpoints[3] should now be in the registry of known endpoints
        assertTrue(registry.getAllEndpoints().contains(new EndpointURI(endpoints[3])));
        assertTrue(registry.getAllEndpoints().contains(new EndpointURI(endpoints[5])));

        // endpoints[5] should now be in the registry of active endpoints
        assertTrue(registry.getActiveEndpoints().contains(new EndpointURI(endpoints[5])));
        // endpoints[3] should NOT be in the registry of active endpoints
        assertFalse(registry.getActiveEndpoints().contains(new EndpointURI(endpoints[3])));

        // Delete endpoints 0 and 1
        Map<String, EndpointSummary> removedSummaries = createSummaries(0, 1);

        // Send third poll result with removals
        registry.pollResult(datastore.getDatastoreId(),
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap(),
                removedSummaries);

        verify(moduleLoader, times(1)).removeScriptModule(ModuleId.create(endpoints[0], activeVersions[0]));

        // Now verify that the active count came down by 1
        assertTrue(registry.getActiveEndpoints().size() == 3);
        // endpoint 0 should be out of the active endpoint list
        assertFalse(registry.getActiveEndpoints().contains(new EndpointURI(endpoints[0])));
        // Both endpoint 0 and 1 should be out of the known endpoint list
        assertFalse(registry.getAllEndpoints().contains(new EndpointURI(endpoints[0])));
        assertFalse(registry.getAllEndpoints().contains(new EndpointURI(endpoints[1])));
    }

    @Test
    public void testResolve() {
        initialSummaries = createSummaries(0, 1, 2, 3, 4, 5);
        // Setup the module loader to pretend to process archives, and return valid modules
        ScriptModule returnedModule = mock(ScriptModule.class);
        Set<Class<?>> loadedClasses = new HashSet<Class<?>>();
        loadedClasses.add(TestEndpoint.class);
        when(returnedModule.getLoadedClasses()).thenReturn(loadedClasses);
        when(moduleLoader.getScriptModule(any(ModuleId.class))).thenReturn(returnedModule);

        // Send initial poll result
        registry.pollResult(datastore.getDatastoreId(),
                initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap());

        EndpointURI uri = registry.resolveURI("/test/foo/0");
        assertTrue(uri.toString().equals(endpoints[0]));


        uri = registry.resolveURI("/test/3/foo");
        assertTrue(uri.toString().equals(endpoints[3]));

        uri = registry.resolveURI("/test/4");
        assertTrue(uri.toString().equals(endpoints[4]));
    }

    private Map<String, EndpointSummary> createSummaries(int... indices) {
        Map<String, EndpointSummary> summaries = new LinkedHashMap<String, EndpointSummary>();
        for (int i = 0; i < indices.length; i++) {
            summaries.put(endpoints[indices[i]], new EndpointSummary(activeVersions[indices[i]], latestVersions[indices[i]], lastModified[indices[i]]));
        }

        return summaries;
    }

    /**
     * Endpoint Type for testing.
     */
    private interface TestEndpoint {
        public void execute();
    }

    /**
     * Extended non-abstract registry to test.
     */
    private static class EndpointRegistry extends BaseEndpointRegistry<EndpointExecutable<TestEndpoint>> {
        public EndpointRegistry(EndpointDatastore datastore, ScriptModuleLoader moduleLoader,
                EndpointDatastorePoller dataStorePoller) {
            super(datastore, moduleLoader, dataStorePoller);
        }

        @Override
        protected EndpointExecutable<TestEndpoint> buildEndpointExecutable(EndpointURI endpointURI, String version, ScriptModule module) {
            return new EndpointExecutable<TestEndpoint>(endpointURI, version, module, TestEndpoint.class);
        }
    }
}

package com.netflix.nicobar.endpoints;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for {@link EndpointDatatorePollerTest}
 *
 * @author Vasanth Asokan
 */
public class EndpointDatastorePollerTest {

    private EndpointDatastorePoller datastorePoller;

    @Mock
    private EndpointDatastore datastore;

    @Mock
    private BaseEndpointRegistry<?> registry;

    private String[] endpoints;
    private String[] activeVersions;
    private String[] latestVersions;
    private long[] lastModified;
    private Map<String, EndpointSummary> initialSummaries;

    @BeforeMethod
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        datastorePoller = new EndpointDatastorePoller.Builder(registry).build();
        initialSummaries = createSummaries();
        when(datastore.getEndpointSummaries()).thenReturn(initialSummaries);
        when(datastore.getDatastoreId()).thenReturn("datastore");

    }

    @AfterMethod
    public void teardown() {
        datastorePoller.shutdown();
    }

    @Test
    public void testInitialPoll() throws Exception {
        datastorePoller.addDatastore(datastore, 10, TimeUnit.MINUTES, true);
        verify(registry).pollResult(datastore.getDatastoreId(), initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(), Collections.<String, EndpointSummary>emptyMap());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRecurringPoll() throws Exception {
        datastorePoller.addDatastore(datastore, 100, TimeUnit.MILLISECONDS, true);
        Thread.sleep(500);

        InOrder inOrder = inOrder(registry);
        ArgumentCaptor<Map> arg1 = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> arg2 = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> arg3 = ArgumentCaptor.forClass(Map.class);
        inOrder.verify(registry, atLeast(2)).pollResult(eq(datastore.getDatastoreId()),
                (Map<String, EndpointSummary>)arg1.capture(),
                (Map<String, EndpointSummary>)arg2.capture(),
                (Map<String, EndpointSummary>)arg3.capture());

        // Check first poll
        assertEquals(initialSummaries, arg1.getAllValues().get(0));
        assertEquals(Collections.<String, EndpointSummary>emptyMap(), arg2.getAllValues().get(0));
        assertEquals(Collections.<String, EndpointSummary>emptyMap(), arg3.getAllValues().get(0));

        // Check second poll
        assertEquals(Collections.<String, EndpointSummary>emptyMap(), arg1.getAllValues().get(1));
        assertEquals(Collections.<String, EndpointSummary>emptyMap(), arg2.getAllValues().get(1));
        assertEquals(Collections.<String, EndpointSummary>emptyMap(), arg3.getAllValues().get(1));
    }

    @Test
    public void testActivation() throws Exception {
        datastorePoller.addDatastore(datastore, 10, TimeUnit.MINUTES, true);
        verify(registry).pollResult(datastore.getDatastoreId(), initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(), Collections.<String, EndpointSummary>emptyMap());

        // Recreate the summaries and mutate slightly
        Map<String, EndpointSummary> repoSummaries = createSummaries();
        repoSummaries.get(endpoints[1]).setActiveVersion("v1");
        repoSummaries.get(endpoints[1]).setLastModified(System.currentTimeMillis());
        Map<String, EndpointSummary> modifiedSummaries = Collections.singletonMap(endpoints[1], repoSummaries.get(endpoints[1]));

        when(datastore.getEndpointSummaries()).thenReturn(repoSummaries);
        datastorePoller.pollDatastore(datastore);
        verify(registry).pollResult(datastore.getDatastoreId(),
                Collections.<String, EndpointSummary>emptyMap(),
                modifiedSummaries,
                Collections.<String, EndpointSummary>emptyMap());
    }

    @Test
    public void testDeactivation() throws Exception {
        datastorePoller.addDatastore(datastore, 10, TimeUnit.MINUTES, true);
        verify(registry).pollResult(datastore.getDatastoreId(), initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(), Collections.<String, EndpointSummary>emptyMap());

        // Recreate the summaries and mutate slightly
        Map<String, EndpointSummary> repoSummaries = createSummaries();
        repoSummaries.get(endpoints[0]).setActiveVersion(null);
        repoSummaries.get(endpoints[0]).setLastModified(System.currentTimeMillis());
        Map<String, EndpointSummary> modifiedSummaries = Collections.singletonMap(endpoints[0], repoSummaries.get(endpoints[0]));

        when(datastore.getEndpointSummaries()).thenReturn(repoSummaries);
        datastorePoller.pollDatastore(datastore);
        verify(registry).pollResult(datastore.getDatastoreId(),
                Collections.<String, EndpointSummary>emptyMap(),
                modifiedSummaries,
                Collections.<String, EndpointSummary>emptyMap());
    }

    @Test
    public void testCreateSummary() throws Exception {
        datastorePoller.addDatastore(datastore, 10, TimeUnit.MINUTES, true);
        verify(registry).pollResult(datastore.getDatastoreId(), initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(), Collections.<String, EndpointSummary>emptyMap());

        // Recreate the summaries and mutate
        Map<String, EndpointSummary> repoSummaries = createSummaries();
        String newEndpoint = "/test/10";
        repoSummaries.put(newEndpoint, new EndpointSummary(null, "v1", System.currentTimeMillis()));
        Map<String, EndpointSummary> newSummaries = Collections.singletonMap(newEndpoint, repoSummaries.get(newEndpoint));

        when(datastore.getEndpointSummaries()).thenReturn(repoSummaries);
        datastorePoller.pollDatastore(datastore);
        verify(registry).pollResult(datastore.getDatastoreId(),
                newSummaries,
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap());
    }

    @Test
    public void testDeleteSummary() throws Exception {
        datastorePoller.addDatastore(datastore, 10, TimeUnit.MINUTES, true);
        verify(registry).pollResult(datastore.getDatastoreId(), initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(), Collections.<String, EndpointSummary>emptyMap());

        // Recreate the summaries and mutate
        Map<String, EndpointSummary> repoSummaries = createSummaries();
        String deletedEndpoint = "/test/1";
        EndpointSummary deletedSummary = repoSummaries.remove(deletedEndpoint);
        Map<String, EndpointSummary> removedSummaries = Collections.singletonMap(deletedEndpoint, deletedSummary);

        when(datastore.getEndpointSummaries()).thenReturn(repoSummaries);
        datastorePoller.pollDatastore(datastore);
        verify(registry).pollResult(datastore.getDatastoreId(),
                Collections.<String, EndpointSummary>emptyMap(),
                Collections.<String, EndpointSummary>emptyMap(),
                removedSummaries);
    }

    @Test
    public void testAllMutations() throws Exception {
        datastorePoller.addDatastore(datastore, 10, TimeUnit.MINUTES, true);
        verify(registry).pollResult(datastore.getDatastoreId(), initialSummaries,
                Collections.<String, EndpointSummary>emptyMap(), Collections.<String, EndpointSummary>emptyMap());

        // Recreate the summaries and mutate
        Map<String, EndpointSummary> repoSummaries = createSummaries();
        String deletedEndpoint = "/test/1";
        EndpointSummary deletedSummary = repoSummaries.remove(deletedEndpoint);
        Map<String, EndpointSummary> removedSummaries = Collections.singletonMap(deletedEndpoint, deletedSummary);

        String activatedEndpoint = "/test/2";
        EndpointSummary activatedSummary = repoSummaries.get(activatedEndpoint);
        activatedSummary.setActiveVersion("v1");
        Map<String, EndpointSummary> modifiedSummaries = Collections.singletonMap(activatedEndpoint, activatedSummary);

        String newEndpoint = "/test/10";
        repoSummaries.put(newEndpoint, new EndpointSummary(null, "v1", System.currentTimeMillis()));
        Map<String, EndpointSummary> newSummaries = Collections.singletonMap(newEndpoint, repoSummaries.get(newEndpoint));

        when(datastore.getEndpointSummaries()).thenReturn(repoSummaries);
        datastorePoller.pollDatastore(datastore);
        verify(registry).pollResult(datastore.getDatastoreId(),
                newSummaries,
                modifiedSummaries,
                removedSummaries);
    }

    private Map<String, EndpointSummary> createSummaries() {
        endpoints = new String[] { "/test/1", "/test/2", "/test/3", "/test/4", "/test/5", "/test/6" };
        activeVersions = new String[] { "v1", null, "v2", null, "v3", "v4" };
        latestVersions = new String[] { "v3", "v4", "v2", "v4", "v5", "v6" };
        lastModified = new long[] { 1L, 2L, 3L, 4L, 5L, 6L };

        Map<String, EndpointSummary> summaries = new HashMap<String, EndpointSummary>();
        for (int i = 0; i < endpoints.length; i++) {
            summaries.put(endpoints[i], new EndpointSummary(activeVersions[i], latestVersions[i], lastModified[i]));
        }

        return summaries;
    }
}
package com.netflix.nicobar.endpoints;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.persistence.ArchiveRepository;


/**
 * Tests for {@link EndpointDatastore}.
 *
 * @author Vasanth Asokan
 */
public class EndpointDatastoreTest {
    private EndpointDatastore datastore;

    @Mock
    private EndpointIndex endpointIndex;

    @Mock
    private ArchiveRepository archiveRepository;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.initMocks(this);
        datastore = new EndpointDatastore("datastoreId", archiveRepository, endpointIndex);
    }

    @Test
    public void testCreateEndpointVersion() throws IOException {
        String endpointUri = "/test/endpoint";
        String version = "v1";
        JarScriptArchive scriptArchive = mock(JarScriptArchive.class);
        ScriptModuleSpec moduleSpec = new ScriptModuleSpec.Builder(ModuleId.create(endpointUri, version)).build();

        when(scriptArchive.getModuleSpec()).thenReturn(moduleSpec);
        EndpointSummary summary = new EndpointSummary(null, "v0", 1001L);
        when(endpointIndex.get(endpointUri)).thenReturn(summary);

        datastore.insertEndpointArchive(scriptArchive);

        EndpointSummary modifiedSummary = new EndpointSummary(summary.getActiveVersion(), version, 1001L);
        verify(archiveRepository).insertArchive(scriptArchive);
        verify(endpointIndex).set(endpointUri, modifiedSummary);
    }

    @Test
    public void testCreateNewEndpoint() throws IOException {
        String endpointUri = "/test/endpoint";
        String version = "v1";
        JarScriptArchive scriptArchive = mock(JarScriptArchive.class);
        ScriptModuleSpec moduleSpec = new ScriptModuleSpec.Builder(ModuleId.create(endpointUri, version)).build();
        when(scriptArchive.getModuleSpec()).thenReturn(moduleSpec);
        when(endpointIndex.get(endpointUri)).thenReturn(null);

        datastore.insertEndpointArchive(scriptArchive);

        ArgumentCaptor<EndpointSummary> argument = ArgumentCaptor.forClass(EndpointSummary.class);
        verify(archiveRepository).insertArchive(scriptArchive);
        verify(endpointIndex).set(eq(endpointUri), argument.capture());
        assertNull(argument.getValue().getActiveVersion());
        assertEquals(version, argument.getValue().getLatestVersion());
    }

    @Test
    public void testCreateActiveEndpointVersion() throws IOException {
        String endpointUri = "/test/endpoint";
        String version = "v1";
        JarScriptArchive scriptArchive = mock(JarScriptArchive.class);
        ScriptModuleSpec moduleSpec = new ScriptModuleSpec.Builder(ModuleId.create(endpointUri, version)).build();
        when(scriptArchive.getModuleSpec()).thenReturn(moduleSpec);
        EndpointSummary summary = new EndpointSummary("v0", "v0", 1001L);
        when(endpointIndex.get(endpointUri)).thenReturn(summary);

        datastore.insertEndpointArchive(scriptArchive);
        EndpointSummary modifiedSummary = new EndpointSummary(summary.getActiveVersion(), version, 1001L);

        verify(archiveRepository).insertArchive(scriptArchive);
        verify(endpointIndex).set(endpointUri, modifiedSummary);
    }

    @Test
    public void testActivateEndpointVersion() throws IOException {
        String endpointUri = "/test/endpoint";
        String version = "v1";
        EndpointSummary summary = new EndpointSummary(null, "v2", 1001L);
        when(endpointIndex.get(endpointUri)).thenReturn(summary);

        long tStart = System.currentTimeMillis();
        datastore.activateEndpointVersion(endpointUri, version);
        long tEnd = System.currentTimeMillis();

        ArgumentCaptor<EndpointSummary> argument = ArgumentCaptor.forClass(EndpointSummary.class);
        verify(endpointIndex).set(eq(endpointUri), argument.capture());
        assertEquals(version, argument.getValue().getActiveVersion());
        assertEquals(summary.getLatestVersion(), argument.getValue().getLatestVersion());
        long lastModified = argument.getValue().getLastModified();
        assertTrue(lastModified >= tStart && lastModified <= tEnd);
    }

    @Test
    public void testDeactivateEndpointVersion() throws IOException {
        String endpointUri = "/test/endpoint";
        String version = "v1";
        EndpointSummary summary = new EndpointSummary("v1", "v2", 1001L);
        when(endpointIndex.get(endpointUri)).thenReturn(summary);

        long tStart = System.currentTimeMillis();
        datastore.deactivateEndpointVersion(endpointUri, version);
        long tEnd = System.currentTimeMillis();

        ArgumentCaptor<EndpointSummary> argument = ArgumentCaptor.forClass(EndpointSummary.class);
        verify(endpointIndex).set(eq(endpointUri), argument.capture());
        assertNull(argument.getValue().getActiveVersion());
        assertEquals(summary.getLatestVersion(), argument.getValue().getLatestVersion());
        long lastModified = argument.getValue().getLastModified();
        assertTrue(lastModified >= tStart && lastModified <= tEnd);
    }

    @Test
    public void testGetSummaryMap() throws IOException {
        Map<String, EndpointSummary> expectedMap = Collections.singletonMap("/a/b", new EndpointSummary(null, "v1", 1000L));
        when(endpointIndex.getSummaryMap()).thenReturn(expectedMap);
        Map<String, EndpointSummary> summaryMap = datastore.getEndpointSummaries();
        assertEquals(expectedMap, summaryMap);
    }

    @Test
    public void testGetEndpointArchive() throws IOException {
        String endpointUri = "/test/endpoint";
        String version = "v1";
        ScriptArchive scriptArchive = mock(ScriptArchive.class);
        when(archiveRepository.getScriptArchives(
                Collections.singleton(ModuleId.create(endpointUri, version)))).thenReturn(Collections.singleton(scriptArchive));
        ScriptArchive returnedArchive = datastore.getEndpointArchive(endpointUri, version);

        assertEquals(scriptArchive, returnedArchive);
    }

    @Test
    public void testGetEndpointArchiveReturnsNull() throws IOException {
        String endpointUri = "/test/endpoint";
        String version = "v1";
        when(archiveRepository.getScriptArchives(
                Collections.singleton(ModuleId.create(endpointUri, version)))).thenReturn(Collections.singleton((ScriptArchive)null));
        ScriptArchive returnedArchive = datastore.getEndpointArchive(endpointUri, version);

        assertNull(returnedArchive);
    }

    @Test
    public void testDeleteEndpoint() throws IOException {
        String endpointUri = "/test/endpoint";
        datastore.deleteEndpoint(endpointUri);
        verify(endpointIndex).remove(endpointUri);
    }
}
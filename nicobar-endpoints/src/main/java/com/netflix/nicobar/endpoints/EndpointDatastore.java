package com.netflix.nicobar.endpoints;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.persistence.ArchiveRepository;

/**
 * A datastore for creating, querying and managing an endpoint and its versions.
 * Each endpoint version is backed by a {@link ScriptArchive} which can be converted
 * to a ScriptModule.
 *
 * This datastore, essentially wraps a {@link ArchiveRepository} and an
 * {@link EndpointIndex}, providing a consistent view of persisted endpoints.
 *
 * <p>
 * The main management operations on an endpoint are,
 *
 *
 * @author Vasanth Asokan
 */
public class EndpointDatastore {

    private String datastoreId;
    private EndpointIndex endpointIndex;
    private ArchiveRepository archiveRepository;

    /**
     * Construct an endpoint datastore.
     *
     * @param datastoreId the string id of the datastore.
     * @param archiveRepository the underlying archive repository.
     * @param endpointIndex the underlying endpoint index.
     */
    public EndpointDatastore(String datastoreId, ArchiveRepository archiveRepository, EndpointIndex endpointIndex) {
        this.datastoreId = datastoreId;
        this.endpointIndex = endpointIndex;
        this.archiveRepository = archiveRepository;
    }

    /**
     * Get the string datastoreId.
     *
     * @return the string ID.
     */
    public String getDatastoreId() {
        return datastoreId;
    }

    /**
     * Insert the script archive for an endpoint version.
     *
     * @param endpointArchive the script archive defining the endpoint code.
     * @throws IOException
     */
    public void insertEndpointArchive(JarScriptArchive endpointArchive) throws IOException {
        Objects.requireNonNull(endpointArchive, "endpointArchive");
        String endpointUri = endpointArchive.getModuleSpec().getModuleId().getName();
        String endpointVersion = endpointArchive.getModuleSpec().getModuleId().getVersion();
        archiveRepository.insertArchive(endpointArchive);
        // Read-modify-write the endpoint index entry to reflect the new archive
        EndpointSummary summary = endpointIndex.get(endpointUri);
        if (summary == null) {
            summary = new EndpointSummary(null, endpointVersion, System.currentTimeMillis());
        }
        summary.setLatestVersion(endpointVersion);
        endpointIndex.set(endpointUri, summary);
    }

    /**
     * Set an endpoint archive version as the active version backing the endpoint.
     *
     * @param endpointUri the URI of the endpoint to which the activation applies.
     * @param version the version of the archive to make active.
     * @throws IOException
     */
    public void activateEndpointVersion(String endpointUri, String version) throws IOException {
        Objects.requireNonNull(endpointUri, "endpointUri");
        Objects.requireNonNull(version, "version");
        // Read-modify-write the endpoint index entry to reflect the activation
        EndpointSummary summary = endpointIndex.get(endpointUri);
        summary.setActiveVersion(version);
        summary.setLastModified(System.currentTimeMillis());
        endpointIndex.set(endpointUri, summary);
    }

    /**
     * Set an endpoint as inactive.
     *
     * @param endpointUri the URI of the endpoint to which the deactivation applies.
     * @param version the version of the archive being made inactive.
     * @throws IOException
     */
    public void deactivateEndpointVersion(String endpointUri, String version) throws IOException {
        Objects.requireNonNull(endpointUri, "endpointUri");
        Objects.requireNonNull(version, "version");
        // Read-modify-write the endpoint index entry to reflect the deactivation
        EndpointSummary summary = endpointIndex.get(endpointUri);
        summary.setActiveVersion(null);
        summary.setLastModified(System.currentTimeMillis());
        endpointIndex.set(endpointUri, summary);
    }

    /**
     * Get a summary of all endpoints known to this datastore.
     *
     * @return a map of endpoint URIs to their endpoint summaries.
     * @throws IOException
     */
    public Map<String, EndpointSummary> getEndpointSummaries() throws IOException {
        return endpointIndex.getSummaryMap();
    }

    /**
     * Get the endpoint archive for a given URI and backing archive version.
     *
     * @param endpointUri the URI of the endpoint.
     * @param version the version of the archive.
     * @return a script archive
     * @throws IOException
     */
    public ScriptArchive getEndpointArchive(String endpointUri, String version) throws IOException {
        Objects.requireNonNull(endpointUri, "endpointUri");
        Objects.requireNonNull(version, "version");
        Set<ScriptArchive> archives = archiveRepository.getScriptArchives(Collections.singleton(ModuleId.create(endpointUri, version)));
        if (archives == null || archives.size() == 0)
            return null;
        return archives.iterator().next();
    }

    /**
     * Delete an endpoint from the datastore.
     *
     * This involves deleting all endpoint versions in the archive repository,
     * and then finally deleting the index entry for the endpoint URI.
     *
     * @param endpointUri the URI of the endpoint to delete.
     * @throws IOException
     */
    public void deleteEndpoint(String endpointUri) throws IOException {
        Objects.requireNonNull(endpointUri, "endpointUri");
        // TODO: Get all versions script archives for this endpointUri,
        // and delete them one by one.
        // Delete from index
        endpointIndex.remove(endpointUri);
    }
}

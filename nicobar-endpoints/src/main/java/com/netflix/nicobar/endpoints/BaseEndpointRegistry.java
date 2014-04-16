package com.netflix.nicobar.endpoints;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleLoader;

/**
 * A registry holding a mapping between Endpoint URIs, and their backing
 * {@link ScriptModule} instances, as well as {@link EndpointExecutable} instances.
 * <p>
 * The registry also supports finding a URI mapping for an incoming request
 * endpoint path, that maybe templated.
 * <p>
 * Lookups on the registry will be low latency, and thus adds negligible overhead
 * during request processing flows.
 *
 * @param <T> The final executable endpoint type, restricted to being a subtype
 *            of EndpointExecutable<?>.
 * @author Vasanth Asokan
 */
public abstract class BaseEndpointRegistry<T extends EndpointExecutable<?>> {

    private final static Logger logger = LoggerFactory.getLogger(BaseEndpointRegistry.class);

    protected final EndpointDatastore datastore;
    protected final ScriptModuleLoader moduleLoader;
    /** A map of active endpoints to their backing endpoint executables. This makes request handling low latency for these endpoints. */
    protected ConcurrentHashMap<EndpointURI, T> activeEndpoints = new ConcurrentHashMap<EndpointURI, T>();
    /** Set of templated EndpointURI representations */
    private final Set<EndpointURI> templatedEndpoints = new LinkedHashSet<EndpointURI>();
    /** Set of non-templated URI representations */
    private final Set<EndpointURI> nonTemplatedEndpoints = new HashSet<EndpointURI>();

    /**
     * Construct an EndpointRegistry.
     */
    public BaseEndpointRegistry(EndpointDatastore datastore, ScriptModuleLoader moduleLoader, EndpointDatastorePoller dataStorePoller) {
        Objects.requireNonNull(datastore, "datastore");
        Objects.requireNonNull(moduleLoader, "moduleLoader");
        this.datastore = datastore;
        this.moduleLoader = moduleLoader;
    }

    /**
     * Get the set of all endpoints (active and inactive) known to this registry.
     *
     * @return a set of {@link EndpointURI} representing endpoints.
     */
    public Set<EndpointURI> getAllEndpoints() {
        Set<EndpointURI> endpointSet = new LinkedHashSet<EndpointURI>(templatedEndpoints);
        endpointSet.addAll(nonTemplatedEndpoints);
        return endpointSet;
    }

    /**
     * Get the active set of endpoints known to this registry.
     *
     * @return a set of EndpointURI's representing active endpoints.
     */
    public Set<EndpointURI> getActiveEndpoints() {
        return activeEndpoints.keySet();
    }

    /**
     * Return the backing {@link EndpointExecutable} for a request URI path.
     *
     * @param incomingUri the URI path of an incoming request.
     * @return an executable endpoint object, or null if there isn't one.
     */
    @Nullable
    public T getEndpoint(String incomingUri) {
        EndpointURI resolvedURI = resolveURI(incomingUri);
        if (resolvedURI == null)
            return null;

        return activeEndpoints.get(resolvedURI);
    }

    public boolean isValidEndpoint(String incomingUri) {
        if (resolveURI(incomingUri) != null) {
            return true;
        }

        return false;
    }

    /**
     * Receive an update from a poll event.
     * <p>
     * A {@link EndpointDatastorePoller} will periodically poll an {@link EndpointDatastore}
     * and invoke this method, with the computed delta information.
     * <p>
     * The registry then recomputes its internal in-memory map of active, inactive
     * endpoints, as well trigger loading / unloading of the script modules, and
     * creating an execution ready {@link EndpointExecutable} instance.
     *
     * @param datastoreId the underlying datastore which was polled.
     * @param newEndpoints a set of endpoints which were added.
     * @param modifiedEndpoints a set of endpoints which were modified (activated/deactivated).
     * @param removedEndpoints a set of endpoints which were removed.
     */
    public synchronized void pollResult(String datastoreId, Map<String, EndpointSummary> newEndpoints,
            Map<String, EndpointSummary> modifiedEndpoints, Map<String, EndpointSummary> removedEndpoints) {
        logger.info(String.format("Processing poll result from datastore %s: %d new, %d modified, %d removed.",
                datastoreId, newEndpoints.size(), modifiedEndpoints.size(), removedEndpoints.size()));

        processNewEndpoints(newEndpoints);
        processModifiedEndpoints(modifiedEndpoints);
        processRemovedEndpoints(removedEndpoints);
    }

    /**
     * Process newly added endpoints
     *
     * @param newEndpoints a set of endpoints whose state has to be added to the registry.
     */
    protected void processNewEndpoints(Map<String, EndpointSummary> newEndpoints) {
        for (Map.Entry<String, EndpointSummary> entry: newEndpoints.entrySet()) {
            String URI = entry.getKey();
            EndpointSummary summary = entry.getValue();
            EndpointURI endpointURI;
            try {
                endpointURI = new EndpointURI(URI);
                if (endpointURI.isTemplated()) {
                    templatedEndpoints.add(endpointURI);
                } else {
                    nonTemplatedEndpoints.add(endpointURI);
                }

                if (summary.getActiveVersion() != null) {
                    T endpoint = loadEndpoint(endpointURI, summary.getActiveVersion());
                    activeEndpoints.put(endpointURI, endpoint);
                }
            } catch (Exception e) {
                logger.error("Unable to load module for endpoint URI: " + URI + ". Skipping!", e);
                continue;
            }
        }
    }

    /**
     * Process modified endpoints
     *
     * @param modifiedEndpoints a set of endpoints whose state has to be modified in the registry.
     */
    protected void processModifiedEndpoints(Map<String, EndpointSummary> modifiedEndpoints) {
        for (Map.Entry<String, EndpointSummary> entry: modifiedEndpoints.entrySet()) {
            String URI = entry.getKey();
            EndpointSummary summary = entry.getValue();
            EndpointURI endpointURI;
            try {
                endpointURI = new EndpointURI(URI);

                // This is an active endpoint version going inactive, so unload
                T oldEndpoint = activeEndpoints.get(endpointURI);
                if (oldEndpoint != null) {
                    unloadEndpoint(oldEndpoint.getUri(), oldEndpoint.getVersion());
                    activeEndpoints.remove(endpointURI);
                }

                // Load up the newly activated version, if any.
                if (summary.getActiveVersion() != null) {
                    T newEndpoint = loadEndpoint(endpointURI, summary.getActiveVersion());
                    activeEndpoints.put(endpointURI, newEndpoint);
                }
            } catch (Exception e) {
                logger.error("Unable to load module for endpoint URI: " + URI + ". Skipping!", e);
                continue;
            }
        }
    }

    /**
     * Process removed endpoints
     *
     * @param removedEndpoints a set of endpoints whose state has to be removed from the registry.
     */
    protected void processRemovedEndpoints(Map<String, EndpointSummary> removedEndpoints) {
        for (Map.Entry<String, EndpointSummary> entry: removedEndpoints.entrySet()) {
            String URI = entry.getKey();
            EndpointSummary summary = entry.getValue();
            EndpointURI endpointURI;
            try {
                endpointURI = new EndpointURI(URI);
                if (endpointURI.isTemplated()) {
                    templatedEndpoints.remove(endpointURI);
                } else {
                    nonTemplatedEndpoints.remove(endpointURI);
                }

                // This is an active endpoint going inactive, so unload
                T endpoint = activeEndpoints.get(endpointURI);
                if (endpoint != null) {
                    unloadEndpoint(endpointURI, summary.getActiveVersion());
                    activeEndpoints.remove(endpointURI);
                }
            } catch (Exception e) {
                logger.error("Unable to load module for endpoint URI: " + URI + ". Skipping!", e);
                continue;
            }
        }
    }

    /**
     * Hook to process an archive, and transform it before it gets loaded
     * into a {@link ScriptModuleLoader}.
     *
     * This implementation does nothing, and returns back the original archive.
     *
     * @param originalArchive a script archive
     * @return a transformed archive.
     */
    protected ScriptArchive archivePreloadHook(ScriptArchive originalArchive) {
        return originalArchive;
    }

    /**
     * Fetch, load and prepare an EndpointExecutable for a given endpoint URI and version.
     *
     * A specific endpoint archive is fetched from the datastore, loaded into the
     * script module loader, and an EndpointExecutable is constructed, thus making
     * this endpoint ready to execute.
     *
     * @param endpointURI the URI of the endpoint to load.
     * @param version the version of the endpoint archive to load.
     * @return an executable endpoint.
     * @throws Exception when any of the intermediate steps fail.
     */
    protected T loadEndpoint(EndpointURI endpointURI, String version) throws Exception {
        String URI = endpointURI.getURI();
        String loggedEndpointId = URI + "_" + version;

        logger.info("Fetching endpoint archive for: " + loggedEndpointId);
        ScriptArchive archive = datastore.getEndpointArchive(URI, version);
        ScriptArchive transformedArchive = archivePreloadHook(archive);

        logger.info("Loading endpoint module for: " + loggedEndpointId);
        moduleLoader.updateScriptArchives(Collections.singleton(transformedArchive));
        ModuleId moduleId = ModuleId.create(URI, version);
        ScriptModule module = moduleLoader.getScriptModule(moduleId);
        if (module == null) {
            throw new IllegalStateException("Could not instantiate script module for endpoint: " + URI + "_" + version);
        }

        logger.info("Loaded endpoint module for: " + loggedEndpointId);
        return buildEndpointExecutable(endpointURI, version, module);
    }

    /**
     * Unload the backing script module for a given endpoint URI and version.
     *
     * @param endpointURI
     */
    protected void unloadEndpoint(EndpointURI endpointURI, String version) {
        ModuleId removedModuleId = ModuleId.create(endpointURI.getURI(), version);
        moduleLoader.removeScriptModule(removedModuleId);
    }

    /**
     * Lookup a URI path against {@link EndpointURI} objects known to this registry.
     *
     * The actual lookup involves first looking for a direct match against,
     * non-templated URI paths. If there isn't a match, then a sequential pass is made
     * through a list templated URIs, checking for a match against each one.
     *
     * TODO: This sequential scan through templated URIs can use a faster string matching
     * algorithm.
     *
     * @param incomingUri a URI path.
     * @return an EndpointURI, possibly null.
     */
    @Nullable
    protected EndpointURI resolveURI(String incomingUri) {
        EndpointURI uri = new EndpointURI(incomingUri);
        if (nonTemplatedEndpoints.contains(uri)) {
            return uri;
        }

        for (EndpointURI candidateUri : templatedEndpoints) {
            if (candidateUri.matches(incomingUri))
                return candidateUri;
        }

        return null;
    }

    /**
     * Build a <T> representing an EndpointExecutable, given a URI path, archive version and a {@link ScriptModule}
     *
     * @param endpointURI the endpoint URI.
     * @param version the version of the underlying endpoint archive.
     * @param module the executable ScriptModule
     * @return an EndpointExecutable.
     */
    protected abstract T buildEndpointExecutable(EndpointURI endpointURI, String version, ScriptModule module);
}

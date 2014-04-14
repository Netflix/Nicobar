package com.netflix.nicobar.endpoints;

import java.util.Objects;

import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleUtils;

/**
 * A ready to execute Endpoint class.
 *
 * Wraps the underlying executable {@link ScriptModule}, and also has
 * cached the underlying endpoint entry class, ready to create instances
 * to handle incoming requests, with little overhead
 *
 * @param <T> The type of the Endpoint entry class.
 * @author Vasanth Asokan
 */
public class EndpointExecutable<T> {
    private final EndpointURI uri;
    private final String version;
    private final ScriptModule scriptModule;
    private final Class<? extends T> entryClass;

    /**
     * Construct an endpoint executable.
     *
     * @param uri the endpoint URI
     * @param version the specific endpoint version
     * @param scriptModule the underlying script module
     * @param entryClass the entryClass to discover an assignable class from the script module.
     */
    @SuppressWarnings("unchecked")
    public EndpointExecutable(EndpointURI uri, String version, ScriptModule scriptModule, Class<T> entryClass) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(scriptModule, "scriptModule");

        this.uri = uri;
        this.version = version;
        this.scriptModule = scriptModule;
        Class<?> foundClass = ScriptModuleUtils.findAssignableClass(scriptModule, entryClass);
        if (foundClass == null)
            throw new IllegalArgumentException("Cannot find class assignable to: " + entryClass.getName());

        this.entryClass = (Class<? extends T>)foundClass;
    }

    /**
     * The URI of the endpoint this executable serves.
     *
     * @return the endpoint URI.
     */
    public EndpointURI getUri() {
        return uri;
    }

    /**
     * The version of the endpoint that this executable
     * was built out of.
     *
     * @return the endpoint version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Get the underlying script module.
     *
     * @return a script module.
     */
    public ScriptModule getScriptModule() {
        return scriptModule;
    }

    /**
     * Get the entrypoint class that was found during construction.
     *
     * @return an entry point class.
     */
    public Class<? extends T> getEntryClass() {
        return entryClass;
    }

    /**
     * Build an instance of the entrypoint class.
     *
     * @return an instance of the entrypoint class.
     * @throws Exception if there were issues with instantiation.
     */
    public T newInstance() throws Exception {
        return entryClass.newInstance();
    }
}

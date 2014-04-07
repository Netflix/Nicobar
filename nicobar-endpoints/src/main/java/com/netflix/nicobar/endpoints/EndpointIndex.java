package com.netflix.nicobar.endpoints;

import java.io.IOException;
import java.util.Map;

/**
 * An simple key-value index interface for mapping endpoints
 * to an {@link EndpointSummary} summarizing state related to
 * the underlying script archives.
 *
 * @author Vasanth Asokan
 */
public interface EndpointIndex {

    /**
     * Set the endpoint summary of an endpoint URI.
     *
     * @param endpointUri the endpoint URI.
     * @param summary the endpoint summary.
     */
    public void set(String endpointUri, EndpointSummary summary) throws IOException;

    /**
     * Get the endpoint summary of an endpoint URI.
     *
     * @param endpointUri the endpoint URI.
     * @return the endpoint summary.
     */
    public EndpointSummary get(String endpointUri) throws IOException;

    /**
     * Remove the endpoint summary of an endpoint URI.
     * This removes the entire index entry.
     *
     * @param endpointUri the endpoint URI.
     */
    public void remove(String endpointUri) throws IOException;

    /**
     * Get the endpoint summaries for all the endpoint URIs.
     *
     * @return a map of endpoint URIs to their summaries.
     */
    public Map<String, EndpointSummary> getSummaryMap() throws IOException;
}

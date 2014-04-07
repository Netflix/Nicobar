package com.netflix.nicobar.endpoints;


/**
 * Serializer for the {@link EndpointSummary}
 *
 * @author Vasanth Asokan
 */
public interface EndpointSummarySerializer {
    /**
     * Convert the {@link EndpointSummary} to a JSON String
     */
    public String serialize(EndpointSummary summary);

    /**
     * Convert the input JSON String to a {@link EndpointSummary}
     * @throws RuntimeException if the given string cannot be deserialized into an EndpointSummary.
     */
    public EndpointSummary deserialize(String json);
}
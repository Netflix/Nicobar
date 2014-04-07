package com.netflix.nicobar.endpoints;

import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.Test;

import com.netflix.nicobar.endpoints.EndpointSummary;

/**
 * Tests for {@link GsonEndpointSummarySerializer}.
 *
 * @author Vasanth Asokan
 */
public class EndpointSummarySerializerTest {
    @Test
    public void testRoundTrip() {
        EndpointSummary summary = new EndpointSummary("v1", "v2", System.currentTimeMillis());
        EndpointSummarySerializer serializer = new GsonEndpointSummarySerializer();
        String json = serializer.serialize(summary);
        assertEquals(summary, serializer.deserialize(json));
    }

    @Test
    public void testRoundTripWithNulls() {
        EndpointSummary summary = new EndpointSummary(null, "v2", System.currentTimeMillis());
        EndpointSummarySerializer serializer = new GsonEndpointSummarySerializer();
        String json = serializer.serialize(summary);
        EndpointSummary returnedSummary = serializer.deserialize(json);
        assertEquals(summary, returnedSummary);
    }
}
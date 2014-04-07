package com.netflix.nicobar.endpoints;

import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonEndpointSummarySerializer implements EndpointSummarySerializer {

    private static final Gson SERIALIZER = new GsonBuilder().create();

    @Override
    public String serialize(EndpointSummary summary) {
        Objects.requireNonNull(summary, "summary");
        return SERIALIZER.toJson(summary);
    }

    @Override
    public EndpointSummary deserialize(String json) {
        Objects.requireNonNull(json, "json");
        return SERIALIZER.fromJson(json, EndpointSummary.class);
    }
}

package com.netflix.nicobar.endpoints.cassandra;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.nicobar.cassandra.internal.CassandraGateway;
import com.netflix.nicobar.endpoints.EndpointIndex;
import com.netflix.nicobar.endpoints.EndpointSummary;
import com.netflix.nicobar.endpoints.EndpointSummarySerializer;

/**
 * A {@link EndpointIndex} backed by a Cassandra column family,
 * with the index implemented as a wide row.
 *
 * @author Vasanth Asokan
 */
public class CassandraEndpointIndex implements EndpointIndex {

    private final CassandraGateway gateway;
    private final String indexRowKey;
    private final EndpointSummarySerializer serializer;

    /**
     * Construct a cassandra based index.
     * @param indexRowKey the row key string to use for the wide row.
     * @param gateway a gateway to Cassandra.
     * @param serializer a serializer that converts an {@link EndpointSummary}
     *        to a string suitable for storage in a column.
     */
    public CassandraEndpointIndex(String indexRowKey, CassandraGateway gateway, EndpointSummarySerializer serializer) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.indexRowKey = Objects.requireNonNull(indexRowKey, "indexRowKey");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    @Override
    public void set(String endpointUri, EndpointSummary value) throws IOException {
        Map<String, Object> columns = new HashMap<String, Object>();
        columns.put(endpointUri, serializer.serialize(value));
        try {
            gateway.upsert(indexRowKey, columns);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public EndpointSummary get(String endpointUri) throws IOException {
        String json;

        try {
            ColumnList<String> columns = gateway.getColumns(indexRowKey, endpointUri);
            if (columns == null || columns.size() == 0)
                return null;

            Column<String> column = columns.getColumnByName(endpointUri);
            if (column == null)
                return null;

            json = column.getStringValue();
        } catch (Exception e) {
            throw new IOException(e);
        }

        return serializer.deserialize(json);
    }

    @Override
    public void remove(String endpointUri) throws IOException {
        try {
            gateway.deleteColumn(indexRowKey, endpointUri);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public Map<String, EndpointSummary> getSummaryMap() throws IOException {
        Map<String, EndpointSummary> summarySet = new HashMap<String, EndpointSummary>();
        try {
            ColumnList<String> columns = gateway.getRow(indexRowKey);
            for (Column<String> column : columns) {
                String json = column.getStringValue();
                summarySet.put(column.getName(), serializer.deserialize(json));
            }
        } catch (Exception e) {
            throw new IOException(e);
        }

        return summarySet;
    }
}

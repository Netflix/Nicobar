package com.netflix.nicobar.cassandra.internal;

import java.util.Map;
import java.util.concurrent.Future;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Rows;

/**
 * Concrete implementation of CassandraGateway, using Cassandra Hystrix commands.
 * @author Vasanth Asokan
 */
public class CassandraGatewayImpl implements CassandraGateway {

    private final Keyspace keyspace;
    private final String columnFamily;

    public CassandraGatewayImpl(Keyspace keyspace, String cf) {
        this.keyspace = keyspace;
        this.columnFamily = cf;
    }

    @Override
    public Keyspace getKeyspace() {
        return this.keyspace;
    }

    @Override
    public String getColumnFamily() {
        return this.columnFamily;
    }

    @Override
    public void upsert(String rowKey, Map<String, Object> attributes) {
        new HystrixCassandraPut<String>(keyspace, columnFamily, rowKey, attributes).execute();
    }

    @Override
    public void upsert(String rowKey, Map<String, Object> attributes, int ttlSeconds) {
        new HystrixCassandraPut<String>(keyspace, columnFamily, rowKey, attributes, ttlSeconds).execute();
    }

    @Override
    public ColumnList<String> getRow(String rowKey) {
        return new HystrixCassandraGetRow<String>(keyspace, columnFamily, rowKey).execute();
    }

    @Override
    public Rows<String, String> getRows(String... rowKeys) {
        return new HystrixCassandraGetRowsByKeys<String>(keyspace, columnFamily, rowKeys).execute();
    }

    @Override
    public Rows<String, String> select(String cql) {
        return new HystrixCassandraGetRowsByQuery<String>(keyspace, columnFamily, String.class, cql).execute();
    }

    @Override
    public Future<Rows<String, String>> selectAsync(String cql) {
        return new HystrixCassandraGetRowsByQuery<String>(keyspace, columnFamily, String.class, cql).queue();
    }

    @Override
    public ColumnList<String> getColumns(String rowKey, String... columns) {
        return new HystrixCassandraGetRow<String>(keyspace, columnFamily, rowKey).withColumns(columns).execute();
    }

    @Override
    public void deleteRow(String rowKey) {
        new HystrixCassandraDeleteRow<String>(keyspace, columnFamily, rowKey).execute();
    }
}
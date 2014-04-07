package com.netflix.nicobar.cassandra.internal;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.CqlResult;
import com.netflix.astyanax.model.Rows;

/**
 * Hystrix command to get rows from Cassandra that match a particular CQL query.
 * See http://crlog.info/2011/06/13/cassandra-query-language-cql-v1-0-0-updated/
 * @param <RowKeyType> the type of the row key, String, Integer etc.
 * @author Vasanth Asokan, modified from hystrix command implementations in Zuul
 *         Zuul (https://github.com/Netflix/zuul)
 */
public class HystrixCassandraGetRowsByQuery<RowKeyType> extends AbstractCassandraHystrixCommand<Rows<RowKeyType, String>> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final String cql;

    /**
     * Get rows specified by their row keys.
     *
     * @param keyspace
     * @param columnFamily
     * @param cql
     */
    public HystrixCassandraGetRowsByQuery(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, String cql) {
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.cql = cql;
    }

    /**
     * Get rows specified by their row keys.
     *
     * @param keyspace
     * @param columnFamilyName
     * @param cql
     */
    @SuppressWarnings("unchecked")
    public HystrixCassandraGetRowsByQuery(Keyspace keyspace, String columnFamilyName, Class<?> columnFamilyKeyType, String cql) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, columnFamilyKeyType);
        this.cql = cql;
    }

    @Override
    protected Rows<RowKeyType, String> run() throws Exception {
        CqlResult<RowKeyType, String> cqlresult = keyspace.prepareQuery(columnFamily).withCql(cql).execute()
                .getResult();
        Rows<RowKeyType, String> result = cqlresult.getRows();
        return result;
    }
}

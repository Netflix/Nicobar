package com.netflix.nicobar.cassandra.internal;

import java.util.Arrays;
import java.util.List;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.model.ColumnFamily;

/**
 * Hystrix command to delete columns from a specific row in from Cassandra.
 * @param <RowKeyType> the row key type, String, Integer etc.
 * @author Vasanth Asokan, modified from hystrix command implementations in
 *         Zuul (https://github.com/Netflix/zuul)
 */
public class HystrixCassandraDeleteColumn<RowKeyType> extends AbstractCassandraHystrixCommand<Void> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final RowKeyType rowKey;
    private final List<String> columnNames;

    @SuppressWarnings("unchecked")
    public HystrixCassandraDeleteColumn(Keyspace keyspace, String columnFamilyName, RowKeyType rowKey, String... columnNames) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, rowKey);
        this.rowKey = rowKey;
        this.columnNames = Arrays.asList(columnNames);
    }

    @Override
    protected Void run() throws Exception {
        MutationBatch m = keyspace.prepareMutationBatch();
        ColumnListMutation<String> mutation = m.withRow(columnFamily, rowKey);
        for (String column: columnNames) {
            mutation = mutation.deleteColumn(column);
        }
        m.execute();
        return null;
    }
}

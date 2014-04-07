package com.netflix.nicobar.cassandra.internal;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.model.ColumnFamily;

/**
 * Hystrix command to delete a row from Cassandra.
 * @author Vasanth Asokan, modified from hystrix command implementations in
 *         Zuul (https://github.com/Netflix/zuul)
 */
public class HystrixCassandraDeleteRow<RowKeyType> extends AbstractCassandraHystrixCommand<Void> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final RowKeyType rowKey;

    public HystrixCassandraDeleteRow(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, RowKeyType rowKey) {
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.rowKey = rowKey;
    }

    @SuppressWarnings("unchecked")
    public HystrixCassandraDeleteRow(Keyspace keyspace, String columnFamilyName, RowKeyType rowKey) {
        this.keyspace = keyspace;
        this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, rowKey);
        this.rowKey = rowKey;
    }

    @Override
    protected Void run() throws Exception {
        MutationBatch m = keyspace.prepareMutationBatch();
        m.withRow(columnFamily, rowKey).delete();
        m.execute();
        return null;
    }
}

package com.netflix.nicobar.cassandra.internal;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.query.RowQuery;

/**
 * Hystrix command to get a row from Cassandra using a specific row key.
 *
 * @param <RowKeyType> the row key type - String, Integer etc.
 * @author Vasanth Asokan, modified from hystrix command implementations in Zuul (https://github.com/Netflix/zuul)
 */
public class HystrixCassandraGetRow<RowKeyType> extends AbstractCassandraHystrixCommand<ColumnList<String>> {

   private final Keyspace keyspace;
   private final ColumnFamily<RowKeyType, String> columnFamily;
   private final RowKeyType rowKey;
   private String[] columns;

   public HystrixCassandraGetRow(Keyspace keyspace, ColumnFamily<RowKeyType, String> columnFamily, RowKeyType rowKey) {
       this.keyspace = keyspace;
       this.columnFamily = columnFamily;
       this.rowKey = rowKey;
   }

   /**
    * Restrict the response to only these columns.
    *
    * Example usage: new HystrixCassandraGetRow(args).withColumns("column1", "column2").execute()
    *
    * @param columns the list of column names.
    * @return self.
    */
   public HystrixCassandraGetRow<RowKeyType> withColumns(String... columns) {
       this.columns = columns;
       return this;
   }

   @SuppressWarnings("unchecked")
   public HystrixCassandraGetRow(Keyspace keyspace, String columnFamilyName, RowKeyType rowKey) {
       this.keyspace = keyspace;
       this.columnFamily = getColumnFamilyViaColumnName(columnFamilyName, rowKey);
       this.rowKey = rowKey;
   }

   @Override
   protected ColumnList<String> run() throws Exception {
        RowQuery<RowKeyType, String> rowQuery = keyspace.prepareQuery(columnFamily).getKey(rowKey);
        /* apply column slice if we have one */
        if (columns != null) {
            rowQuery = rowQuery.withColumnSlice(columns);
        }
        ColumnList<String> result = rowQuery.execute().getResult();
        return result;
   }
}

/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
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

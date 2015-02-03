/*
 *
 *  Copyright 2013 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
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
public class HystrixCassandraDeleteColumns<RowKeyType> extends AbstractCassandraHystrixCommand<Void> {

    private final Keyspace keyspace;
    private final ColumnFamily<RowKeyType, String> columnFamily;
    private final RowKeyType rowKey;
    private final List<String> columnNames;

    @SuppressWarnings("unchecked")
    public HystrixCassandraDeleteColumns(Keyspace keyspace, String columnFamilyName, RowKeyType rowKey, String... columnNames) {
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

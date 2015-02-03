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

package com.netflix.nicobar.cassandra;

import java.util.Map;
import java.util.concurrent.Future;

import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Rows;

/**
 * Common cassandra CRUD operations.
 *
 * @author Vasanth Asokan
 */
public interface CassandraGateway {
    /**
     * Return the keyspace that this gateway provides access to.
     * @return the Cassandra keyspace.
     */
    public Keyspace getKeyspace();

    /**
     * Return the column family that this gateway provides access to.
     * @return a cassandra column family name.
     */
    public String getColumnFamily();

    /**
     * Performs an insert/update for a row in Cassandra.
     *
     * @param rowKey the row key to use for insertions.
     * @param attributes map of column names to column values.
     */
    public void upsert(String rowKey, Map<String, Object> attributes);

    /**
     * Performs an insert/update for a row in Cassandra.
     *
     * @param rowKey the row key to use for insertions
     * @param attributes map of column names to column values.
     * @param ttlSeconds how long should columns in this upsert live.
     */
    public void upsert(String rowKey, Map<String, Object> attributes, int ttlSeconds);

    /**
     * Deletes a row in Cassandra.
     * @param rowKey the key of the row to delete.
     */
    public void deleteRow(String rowKey);

    /**
     * Deletes a column from a row in Cassandra.
     * @param rowKey the key of the row containing the column.
     * @param column the name of the column to delete.
     */
    public void deleteColumn(String rowKey, String column);

    /**
     * Gets specific columns from a specific row
     *
     * @param rowKey the specific row's key.
     * @param columns the specific columns
     * @return retrieved column list, possibly null.
     */
    public ColumnList<String> getColumns(String rowKey, String... columns);

    /**
     * Gets all columns for the specified row.
     * @param rowKey a single row key.
     * @return list of columns for the row, possibly null.
     */
    public ColumnList<String> getRow(String rowKey);

    /**
     * Gets all columns for all the listed row keys.
     * @param rowKeys a list of row keys.
     * @return list of rows, possibly null.
     */
    public Rows<String, String> getRows(String... rowKeys);

    /**
     * Performs a CQL query and returns result.
     *
     * @param cql the CQL query string.
     * @return resulting row set, could be null.
     */
    public Rows<String, String> select(String cql);

    /**
     * Performs a CQL query asynchronously
     *
     * @param cql the CQL query string.
     * @return Future containing result row set.
     */
    public Future<Rows<String, String>> selectAsync(String cql);
}

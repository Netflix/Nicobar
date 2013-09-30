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
package com.netflix.scriptlib.cassandra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.CqlResult;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.scriptlib.core.archive.JarScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptModuleSpec;

/**
 * Data access object of {@link ScriptArchive}s stored in Cassandra.
 * This implementation is based on the Astyanax and requires CQL 3 support to be enabled.
 * <p>
 * The query algorithm attempts to divide up read operations such that they won't overwhelm Cassandra
 * if many instances are using this implementation to poll for updates.
 * Upon insertion, all archives are assigned a shard number calculated as (moduleId.hashCode() % <number of shard>).
 * The shard number is subsequently inserted into a column for which a secondary index has been defined.
 * <p>
 * The {@link #getArchiveUpdateTimes(long)} method will first search each shard for any rows with an update timestamp greater than
 * the last poll time, and if any are found, the contents of those archives are loaded in small batches.
 *
 *
 *<pre>
 * Default Schema:
 *
 * CREATE TABLE script_repo (
 *    module_id varchar,
 *    shard_num int,
 *    last_update timestamp,
 *    module_spec varchar,
 *    archive_content_hash blob,
 *    archive_content blob,
 * PRIMARY KEY (module_id)
 * );
 *
 * CREATE INDEX script_repo_shard_num_index on script_repo (shard_num);
 * </pre>
 *
 * See {@link ScriptCassandraDaoConfig} to override the default table name.
 * @author James Kojo
 */
public class ScriptArchiveCassandraDao {
    private final static Logger logger = LoggerFactory.getLogger(ScriptArchiveCassandraDao.class);

    /** column names */
    public static enum Columns {
        module_id,
        shard_num,
        last_update,
        module_spec,
        archive_content_hash,
        archive_content;
    }

    private final ScriptCassandraDaoConfig config;

    /**
     * Construct a instance of the dao with the given configuration
     * @param config
     */
    public ScriptArchiveCassandraDao(ScriptCassandraDaoConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * insert a Jar into the script archive
     * @param moduleId module identifier for this archive. used as row key.
     * @param jarFilePath absolute path to jar file to insert
     * @param createTime create time to use for insertion. should be a timestamp in ms
     * @param moduleSpec optional {@link ScriptModuleSpec} for the archive
     */
    public void insertJarArchive(String moduleId, Path jarFilePath, long createTime, @Nullable ScriptModuleSpec moduleSpec) throws IOException, ConnectionException {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(jarFilePath, "jarFilePath");
        int shardNum = Math.abs(moduleId.hashCode() % config.getShardCount());
        byte[] jarBytes = Files.readAllBytes(jarFilePath);
        byte[] hash = calculateHash(jarBytes);
        MutationBatch mutationBatch = config.getKeyspace().prepareMutationBatch();
        ColumnListMutation<String> columnMutation = mutationBatch.withRow(getColumnFamily(), moduleId);
        columnMutation
            .putColumn(Columns.shard_num.name(), shardNum)
            .putColumn(Columns.last_update.name(), createTime)
            .putColumn(Columns.archive_content_hash.name(), hash)
            .putColumn(Columns.archive_content.name(), jarBytes);
        if (moduleSpec != null) {
            String serialized = config.getModuleSpecSerializer().serialize(moduleSpec);
            columnMutation.putColumn(Columns.module_spec.name(), serialized);
        }
        mutationBatch.execute();
    }

    /**
     * Get the last update times of all of the script archives managed by this Dao.
     * @return map of moduleId to last update time
     */
    public Map<String, Long> getArchiveUpdateTimes() throws ConnectionException, IOException {
        int shardCount = config.getShardCount();

        // shuffle the order the shards are queried in order to avoid hotspots
        List<Integer> shards = new ArrayList<Integer>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            shards.add(i);
        }
        Collections.shuffle(shards);
        Map<String, Long> updateTimes = new LinkedHashMap<String, Long>();
        for (int shard : shards) {
            Map<String, Long> shardUpdateTimes = getArchiveUpdateTimes(shard);
            updateTimes.putAll(shardUpdateTimes);
        }
        return updateTimes;
    }

    /**
     * Get the last update times of all of the script archives within a given shard
     * @param shardNum shard number to query
     * @return map of moduleId to last update time
     */
    public Map<String, Long> getArchiveUpdateTimes(int shardNum) throws ConnectionException {
        Rows<String, String> rows = getRows(EnumSet.of(Columns.module_id, Columns.last_update), shardNum);
        Map<String, Long> updateTimes = new LinkedHashMap<String, Long>();
        for (Row<String, String> row : rows) {
            String moduleId = row.getKey();
            Column<String> lastUpdateColumn = row.getColumns().getColumnByName(Columns.last_update.name());
            Long updateTime = lastUpdateColumn != null ? lastUpdateColumn.getLongValue() : null;
            if (StringUtils.isNotBlank(moduleId) && updateTime != null) {
                updateTimes.put(moduleId, updateTime);
            }
        }
        return updateTimes;
    }

    /**
     * Get all of the rows in a shard
     * @param columns which columns to select
     * @param shardNum shard number to select from
     * @return result rows
     */
    public Rows<String, String> getRows(EnumSet<Columns> columns, int shardNum) throws ConnectionException {
        CqlResult<String, String> result = config.getKeyspace()
            .prepareQuery(getColumnFamily())
            .withCql(generateSelectByShardCql(columns))
            .asPreparedStatement()
            .withIntegerValue(shardNum)
            .execute()
            .getResult();
        Rows<String, String> rows = result.getRows();
        return rows;
    }

    /**
     * Get all of the {@link ScriptArchive}s for the given set of moduleIds. Will perform the operation in batches
     * as specified by {@link ScriptCassandraDaoConfig#getArchiveFetchBatchSize()} and outputs the jar files in
     * the path specified by {@link ScriptCassandraDaoConfig#getArchiveOutputDirectory()}.
     *
     * @param moduleIds keys to search for
     * @return set of ScriptArchives retrieved from the database
     */
    public Set<ScriptArchive> getScriptArchives(Set<String> moduleIds) throws ConnectionException, IOException {
        Set<ScriptArchive> archives = new LinkedHashSet<ScriptArchive>(moduleIds.size()*2);
        Path archiveOuputDir = config.getArchiveOutputDirectory();
        List<String> moduleIdList = new LinkedList<String>(moduleIds);
        int batchSize = config.getArchiveFetchBatchSize();
        int start = 0;
        while (start < moduleIdList.size()) {
            int end = Math.min(moduleIdList.size(), start + batchSize);
            List<String> batchModuleIds = moduleIdList.subList(start, end);

            Rows<String,String> rows = config.getKeyspace().prepareQuery(getColumnFamily())
                .getKeySlice(batchModuleIds)
                .execute()
                .getResult();
            for (Row<String, String> row : rows) {
                String moduleId = row.getKey();
                ColumnList<String> columns = row.getColumns();
                Column<String> lastUpdateColumn = columns.getColumnByName(Columns.last_update.name());
                Column<String> hashColumn = columns.getColumnByName(Columns.archive_content_hash.name());
                Column<String> contentColumn = columns.getColumnByName(Columns.archive_content.name());
                if (lastUpdateColumn == null || hashColumn == null || contentColumn == null) {
                    continue;
                }
                Column<String> moduleSpecColumn = columns.getColumnByName(Columns.module_spec.name());
                ScriptModuleSpec moduleSpec = null;
                if (moduleSpecColumn != null && moduleSpecColumn.hasValue()) {
                    String moduleSpecString = moduleSpecColumn.getStringValue();
                    moduleSpec = config.getModuleSpecSerializer().deserialize(moduleSpecString);
                }
                long lastUpdateTime = lastUpdateColumn.getLongValue();
                byte[] hash = hashColumn.getByteArrayValue();
                byte[] content = contentColumn.getByteArrayValue();

                // verify the hash
                if (hash != null && hash.length > 0 && !verifyHash(hash, content)) {
                    logger.warn("Content hash validation failed for moduleId {}. size: {}", moduleId, content.length);
                    continue;
                }
                String fileName = new StringBuilder().append(moduleId).append("-").append(lastUpdateTime).append(".jar").toString();
                Path jarFile = archiveOuputDir.resolve(fileName);
                Files.write(jarFile, content);
                JarScriptArchive scriptArchive = new JarScriptArchive.Builder(jarFile)
                    .setModuleSpec(moduleSpec)
                    .setCreateTime(lastUpdateTime)
                    .build();
                archives.add(scriptArchive);
            }
            start = end;
        }
        return archives;
    }

    /**
     * Delete an archive by ID
     * @param moduleId module id to delete
     * @throws ConnectionException
     */
    public void deleteArchive(String moduleId) throws ConnectionException {
        Objects.requireNonNull(moduleId, "moduleId");
        MutationBatch mutationBatch = config.getKeyspace().prepareMutationBatch();
        mutationBatch.withRow(getColumnFamily(), moduleId).delete();
        mutationBatch.execute();

    }

    /**
     * Generate the CQL to select specific columns by shard number.
     *  SELECT <columns>... FROM script_repo WHERE shard_num = ?
     */
    protected String generateSelectByShardCql(EnumSet<Columns> columns) {
        StringBuilder sb = new StringBuilder()
            .append("SELECT ");
        boolean first = true;
        for (Columns column : columns) {
            if (first) {
                first = false;
            } else {
                sb.append(",");
            }
            sb.append(column.name());
        }
        sb.append("\n")
            .append("FROM ").append(config.getColumnFamilyName()).append("\n")
            .append("WHERE ").append(Columns.shard_num.name()).append(" = ?\n");
        return sb.toString();
    }

    /**
     * Create a column family object using the dao configuration.
     */
    protected ColumnFamily<String, String> getColumnFamily() {
        return ColumnFamily.newColumnFamily(config.getColumnFamilyName(), StringSerializer.get(), StringSerializer.get());
    }

    protected boolean verifyHash(byte[] expectedHashCode, byte[] content) {
        byte[] hashCode = calculateHash(content);
        return Arrays.equals(expectedHashCode, hashCode);
    }

    protected byte[] calculateHash(byte[] content) {
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // should never happen
            return null;
        }
        byte[] hashCode = digester.digest(content);
        return hashCode;
    }
}

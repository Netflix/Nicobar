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
package com.netflix.nicobar.cassandra;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.nicobar.cassandra.internal.CassandraGateway;
import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.persistence.ArchiveRepository;
import com.netflix.nicobar.core.persistence.ArchiveSummary;
import com.netflix.nicobar.core.persistence.RepositorySummary;
import com.netflix.nicobar.core.persistence.RepositoryView;

/**
 * Data access object of {@link ScriptArchive}s stored in Cassandra.
 * This implementation is based on the Astyanax and requires CQL 3 support to be enabled.
 * <p>
 * The query algorithm attempts to divide up read operations such that they won't overwhelm Cassandra
 * if many instances are using this implementation to poll for updates.
 * Upon insertion, all archives are assigned a shard number calculated as (moduleId.hashCode() % <number of shard>).
 * The shard number is subsequently inserted into a column for which a secondary index has been defined.
 * RepositoryView poller methods will first search each shard for any rows with an update timestamp greater than
 * the last poll time, and if any are found, the contents of those archives are loaded in small batches.
 *
 *
 *<pre>
 * Default Schema:
 *
 * CREATE TABLE script_repo (
 *    module_id varchar,
 *    module_name varchar,
 *    module_version varchar,
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
 * See {@link CassandraArchiveRepositoryConfig} to override the default table name.
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class CassandraArchiveRepository implements ArchiveRepository {
    private final static Logger logger = LoggerFactory.getLogger(CassandraArchiveRepository.class);

    /** column names */
    public static enum Columns {
        module_id,
        module_name,
        module_version,
        shard_num,
        last_update,
        module_spec,
        archive_content_hash,
        archive_content;
    }

    protected final RepositoryView defaultView;
    private final CassandraArchiveRepositoryConfig config;
    private final CassandraGateway cassandra;

    /**
     * Construct a instance of the repository with the given configuration
     * @param config
     */
    public CassandraArchiveRepository(CassandraArchiveRepositoryConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.cassandra = this.config.getCassandraGateway();
        defaultView = new DefaultView();
    }

    /**
     * Construct a instance of the repository with the given configuration
     * @param config
     */
    public CassandraArchiveRepository(CassandraArchiveRepositoryConfig config, RepositoryView defaultView) {
        this.config = Objects.requireNonNull(config, "config");
        this.cassandra = this.config.getCassandraGateway();
        this.defaultView = defaultView;
    }

    @Override
    public String getRepositoryId() {
        return getConfig().getRepositoryId();
    }

    /**
     * The default view reports all archives inserted into this repository.
     * @return the default view into all archives.
     */
    @Override
    public RepositoryView getDefaultView() {
        return defaultView;
    }

    /**
     * No named views supported by this repository!
     * Throws UnsupportedOperationException.
     */
    @Override
    public RepositoryView getView(String view) {
        throw new UnsupportedOperationException();
    }

    /**
     * insert a Jar into the script archive
     */
    @Override
    public void insertArchive(JarScriptArchive jarScriptArchive) throws IOException {
        Objects.requireNonNull(jarScriptArchive, "jarScriptArchive");
        ScriptModuleSpec moduleSpec = jarScriptArchive.getModuleSpec();
        ModuleId moduleId = moduleSpec.getModuleId();
        Path jarFilePath;
        try {
            jarFilePath = Paths.get(jarScriptArchive.getRootUrl().toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        int shardNum = calculateShardNum(moduleId);
        byte[] jarBytes = Files.readAllBytes(jarFilePath);
        byte[] hash = calculateHash(jarBytes);
        Map<String, Object> columns = new HashMap<String, Object>();
        columns.put(Columns.module_id.name(), moduleId.toString());
        columns.put(Columns.module_name.name(), moduleId.getName());
        columns.put(Columns.module_version.name(), moduleId.getVersion());
        columns.put(Columns.shard_num.name(), shardNum);
        columns.put(Columns.last_update.name(), jarScriptArchive.getCreateTime());
        columns.put(Columns.archive_content_hash.name(), hash);
        columns.put(Columns.archive_content.name(), jarBytes);

        String serialized = getConfig().getModuleSpecSerializer().serialize(moduleSpec);
        columns.put(Columns.module_spec.name(), serialized);
        try {
            cassandra.upsert(moduleId.toString(), columns);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Get all of the rows in in the table. Attempts to reduce the load on cassandra by splitting up the query into smaller sub-queries
     * @param columns which columns to select
     * @return result rows
     */
    public Iterable<Row<String, String>> getRows(EnumSet<?> columns) throws Exception {
        int shardCount = config.getShardCount();

        List<Future<Rows<String, String>>> futures = new ArrayList<Future<Rows<String, String>>>();
        for (int i = 0; i < shardCount; i++) {
            futures.add(cassandra.selectAsync(generateSelectByShardCql(columns, i)));
        }

        List<Row<String, String>> rows = new LinkedList<Row<String, String>>();
        for (Future<Rows<String, String>> f: futures) {
            Rows<String, String> shardRows = f.get();
            Iterables.addAll(rows, shardRows);
        }

        return rows;
    }

    /**
     * Get all of the {@link ScriptArchive}s for the given set of moduleIds. Will perform the operation in batches
     * as specified by {@link CassandraArchiveRepositoryConfig#getArchiveFetchBatchSize()} and outputs the jar files in
     * the path specified by {@link CassandraArchiveRepositoryConfig#getArchiveOutputDirectory()}.
     *
     * @param moduleIds keys to search for
     * @return set of ScriptArchives retrieved from the database
     */
    @Override
    public Set<ScriptArchive> getScriptArchives(Set<ModuleId> moduleIds) throws IOException {
        Set<ScriptArchive> archives = new LinkedHashSet<ScriptArchive>(moduleIds.size()*2);
        Path archiveOuputDir = getConfig().getArchiveOutputDirectory();
        List<ModuleId> moduleIdList = new LinkedList<ModuleId>(moduleIds);
        int batchSize = getConfig().getArchiveFetchBatchSize();
        int start = 0;
        try {
            while (start < moduleIdList.size()) {
                int end = Math.min(moduleIdList.size(), start + batchSize);
                List<ModuleId> batchModuleIds = moduleIdList.subList(start, end);
                List<String> rowKeys = new ArrayList<String>(batchModuleIds.size());
                for (ModuleId batchModuleId:batchModuleIds) {
                    rowKeys.add(batchModuleId.toString());
                }

                Rows<String, String> rows = cassandra.getRows(rowKeys.toArray(new String[0]));
                for (Row<String, String> row : rows) {
                    String moduleId = row.getKey();
                    ColumnList<String> columns = row.getColumns();
                    Column<String> lastUpdateColumn = columns.getColumnByName(Columns.last_update.name());
                    Column<String> hashColumn = columns.getColumnByName(Columns.archive_content_hash.name());
                    Column<String> contentColumn = columns.getColumnByName(Columns.archive_content.name());
                    if (lastUpdateColumn == null || hashColumn == null || contentColumn == null) {
                        continue;
                    }
                    ScriptModuleSpec moduleSpec = getModuleSpec(columns);
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
        } catch (Exception e) {
            throw new IOException(e);
        }
        return archives;
    }

    /**
     * Delete an archive by ID
     * @param moduleId module id to delete
     * @throws ConnectionException
     */
    @Override
    public void deleteArchive(ModuleId moduleId) throws IOException {
        Objects.requireNonNull(moduleId, "moduleId");
        cassandra.deleteRow(moduleId.toString());
    }

    @Override
    public void addDeploySpecs(ModuleId moduleId, Map<String, Object> deploySpecs) {
        throw new UnsupportedOperationException();
    }

    /**
     * Generate the CQL to select specific columns by shard number.
     *  SELECT <columns>... FROM script_repo WHERE shard_num = ?
     */
    protected String generateSelectByShardCql(EnumSet<?> columns, Integer shardNum) {
        StringBuilder sb = new StringBuilder()
            .append("SELECT ");
        boolean first = true;
        for (Enum<?> column : columns) {
            if (first) {
                first = false;
            } else {
                sb.append(",");
            }
            sb.append(column.name());
        }
        sb.append("\n")
            .append("FROM ").append(cassandra.getColumnFamily())
            .append("\n").append("WHERE ").append(Columns.shard_num.name())
            .append(" = ").append(shardNum).append("\n");
        return sb.toString();
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

    protected int calculateShardNum(ModuleId moduleId) {
        return Math.abs(moduleId.hashCode() % getConfig().getShardCount());
    }

    private ScriptModuleSpec getModuleSpec(ColumnList<String> columns) {
        ScriptModuleSpec moduleSpec = null;
        if (columns != null) {
            Column<String> moduleSpecColumn = columns.getColumnByName(Columns.module_spec.name());
            if (moduleSpecColumn != null && moduleSpecColumn.hasValue()) {
                String moduleSpecString = moduleSpecColumn.getStringValue();
                moduleSpec = getConfig().getModuleSpecSerializer().deserialize(moduleSpecString);
            }
        }
        return moduleSpec;
    }

    /**
     * @return configuration settings for this repository
     */
    public CassandraArchiveRepositoryConfig getConfig() {
        return config;
    }

    protected class DefaultView implements RepositoryView {
        @Override
        public String getName() {
            return "Default View";
        }

        /**
         * Get the last update times of all of the script archives managed by this Repository.
         * @return map of moduleId to last update time
         */
        @Override
        public Map<ModuleId, Long> getArchiveUpdateTimes() throws IOException {
            Iterable<Row<String, String>> rows;
            try {
                rows = getRows(EnumSet.of(Columns.module_id, Columns.last_update));
            } catch (Exception e) {
                throw new IOException(e);
            }
            Map<ModuleId, Long> updateTimes = new LinkedHashMap<ModuleId, Long>();
            for (Row<String, String> row : rows) {
                String moduleId = row.getKey();
                Column<String> lastUpdateColumn = row.getColumns().getColumnByName(Columns.last_update.name());
                Long updateTime = lastUpdateColumn != null ? lastUpdateColumn.getLongValue() : null;
                if (StringUtils.isNotBlank(moduleId) && updateTime != null) {
                    updateTimes.put(ModuleId.fromString(moduleId), updateTime);
                }
            }
            return updateTimes;
        }

        @Override
        public RepositorySummary getRepositorySummary() throws IOException {
            Map<ModuleId, Long> updateTimes = getArchiveUpdateTimes();
            int archiveCount = updateTimes.size();
            long maxUpdateTime = 0;
            for (Long updateTime : updateTimes.values()) {
                if (updateTime > maxUpdateTime) {
                    maxUpdateTime = updateTime;
                }
            }
            String description = String.format("Cassandra Keyspace: %s Column Family: %s",
                cassandra.getKeyspace().getKeyspaceName(), cassandra.getColumnFamily());
            RepositorySummary repositorySummary = new RepositorySummary(getRepositoryId(),
                description, archiveCount, maxUpdateTime);
            return repositorySummary;
        }

        /**
         * Get a summary of all archives in this Repository
         * @return List of summaries
         */
        @Override
        public List<ArchiveSummary> getArchiveSummaries() throws IOException {
            List<ArchiveSummary> summaries = new LinkedList<ArchiveSummary>();
            Iterable<Row<String, String>> rows;
            try {
                    rows = getRows(EnumSet.of(Columns.module_id, Columns.last_update, Columns.module_spec));
            } catch (Exception e) {
                throw new IOException(e);
            }

            for (Row<String, String> row : rows) {
                String moduleId = row.getKey();
                ColumnList<String> columns = row.getColumns();
                Column<String> lastUpdateColumn = columns.getColumnByName(Columns.last_update.name());
                long updateTime = lastUpdateColumn != null ? lastUpdateColumn.getLongValue() : 0;
                ScriptModuleSpec moduleSpec = getModuleSpec(columns);
                ArchiveSummary summary = new ArchiveSummary(ModuleId.fromString(moduleId), moduleSpec, updateTime);
                summaries.add(summary);
            }
            return summaries;
        }
    }
}

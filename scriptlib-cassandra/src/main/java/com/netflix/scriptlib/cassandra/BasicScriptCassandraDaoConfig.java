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
import java.util.Objects;

import com.netflix.astyanax.Keyspace;

/**
 * Configuration provider for the {@link ScriptArchiveCassandraDao}
 *
 * @author James Kojo
 */
public class BasicScriptCassandraDaoConfig implements ScriptCassandraDaoConfig {

    /** default column family name if not overriden in the builder */
    public static final String DEFAULT_COLUMN_FAMILY_NAME = "script_repo";

    /** default number of shards to separate the archives into */
    public static final int DEFAULT_SHARD_COUNT = 10;

    /** Default number of archives to fetch per round-trip */
    public static final int DEFAULT_FETCH_BATCH_SIZE = 10;

    public static class Builder {
        private final Keyspace keyspace;
        private String columnFamilyName;
        private int shardCount = -1;
        private int fetchBatchSize = -1;
        private Path archiveOutputDirectory;

        public Builder(Keyspace keyspace) {
            this.keyspace = keyspace;
        }

        /** Override the default column family name that will be used */
        public Builder setColumnFamilyName(String columnFamilyName) {
            this.columnFamilyName = columnFamilyName;
            return this;
        }
        /** Number of shards or buckets the archives should be put into */
        public Builder setShardCount(int shardCount) {
            this.shardCount = shardCount;
            return this;
        }
        /** Number of archives to fetch per round-trip to the database */
        public Builder setFetchBatchSizeCount(int fetchBatchSize) {
            this.fetchBatchSize = fetchBatchSize;
            return this;
        }
        /** Output Directory for the script archives */
        public Builder setArchiveOutputDirectory(Path archiveOutputDirectory) {
            this.archiveOutputDirectory = archiveOutputDirectory;
            return this;
        }
        /** Construct the config with defaults if necessary */
        ScriptCassandraDaoConfig build() throws IOException {
            String buildColumnFamilyName = columnFamilyName;
            if (buildColumnFamilyName == null) {
                buildColumnFamilyName = DEFAULT_COLUMN_FAMILY_NAME;
            }
            int buildShardCount = shardCount;
            if (buildShardCount <= 0) {
                buildShardCount = DEFAULT_SHARD_COUNT;
            }
            int buildFetchBatchSize = fetchBatchSize;
            if (buildFetchBatchSize <= 0) {
                buildFetchBatchSize = DEFAULT_FETCH_BATCH_SIZE;
            }
            Path buildArchiveDir = archiveOutputDirectory;
            if (buildArchiveDir == null) {
                buildArchiveDir = Files.createTempDirectory("ScriptArchiveOutputDir");
            }
            return new BasicScriptCassandraDaoConfig(keyspace, buildColumnFamilyName, buildShardCount, buildFetchBatchSize, archiveOutputDirectory);
        }
    }

    private final Keyspace keyspace;
    private final String columnFamilyName;
    private final int shardCount;
    private final int fetchBatchSize;
    private final Path archiveOutputDirectory;

    protected BasicScriptCassandraDaoConfig(Keyspace keyspace, String columnFamilyName, int shardCount, int fetchBatchSize, Path archiveOutputDirectory) {
        this.keyspace = Objects.requireNonNull(keyspace, "keyspace");
        this.columnFamilyName = Objects.requireNonNull(columnFamilyName, "columnFamilyName");
        this.shardCount = shardCount;
        this.fetchBatchSize = fetchBatchSize;
        this.archiveOutputDirectory = Objects.requireNonNull(archiveOutputDirectory, "archiveOutputDirectory");;
    }

    @Override
    public Keyspace getKeyspace() {
        return keyspace;
    }

    @Override
    public String getColumnFamilyName() {
        return columnFamilyName;
    }

    @Override
    public int getShardCount() {
        return shardCount;
    }

    @Override
    public int getArchiveFetchBatchSize() {
        return fetchBatchSize;
    }

    @Override
    public Path getArchiveOutputDirectory() {
        return archiveOutputDirectory;
    }
}

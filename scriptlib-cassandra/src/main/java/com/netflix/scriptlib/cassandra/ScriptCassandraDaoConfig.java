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

import java.nio.file.Path;

import com.netflix.astyanax.Keyspace;
import com.netflix.scriptlib.core.archive.ScriptModuleSpec;
import com.netflix.scriptlib.core.archive.ScriptModuleSpecSerializer;

/**
 * Configuration provider interface for the {@link ScriptArchiveCassandraDao}
 *
 * @author James Kojo
 */
public interface ScriptCassandraDaoConfig {
    /**
     * The keyspace must have CQL 3.0.0 enabled. see Astynax docs for instructions on setting the cql version number.
     * @return the {@link Keyspace} in which the operations should be performed.
     */
    public Keyspace getKeyspace();

    /**
     * @return the column family name of that archives are stored in.
     */
    public String getColumnFamilyName();

    /**
     * @return number of shards to put the archives in
     */
    public int getShardCount();

    /**
     * @return how many archives to fetch at a time
     */
    public int getArchiveFetchBatchSize();

    /**
     * @return the output directory for archives
     */
    public Path getArchiveOutputDirectory();

    /**
     * @return serializer for the {@link ScriptModuleSpec} for use when inserting or fetching data.
     */
    public ScriptModuleSpecSerializer getModuleSpecSerializer();
}
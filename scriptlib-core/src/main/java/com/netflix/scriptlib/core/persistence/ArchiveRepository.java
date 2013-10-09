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
package com.netflix.scriptlib.core.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptModuleSpec;

/**
 * Interface to represent a persistence store for archives
 *
 * @author James Kojo
 */
public interface ArchiveRepository {

    public String getRepositoryId();

    /**
     * insert a Jar into the script archive
     * @param moduleId module identifier for this archive. used as row key.
     * @param jarFilePath absolute path to jar file to insert
     * @param moduleSpec optional {@link ScriptModuleSpec} for the archive
     */
    public void insertArchive(String moduleId, Path jarFilePath, @Nullable ScriptModuleSpec moduleSpec)
        throws IOException;

    /**
     * Get the last update times of all of the script archives managed by this Repository.
     * @return map of moduleId to last update time
     */
    public Map<String, Long> getArchiveUpdateTimes() throws IOException;

    /**
     * Get a summary of the of this repository.
     * @return displayable summary of the contents
     */
    public RepositorySummary getRepositorySummary() throws IOException;

    /**
     * Get a summary of all archives in this Repository
     * @return List of summaries
     */
    public List<ArchiveSummary> getArchiveSummaries() throws IOException;

    /**
     * Get all of the {@link ScriptArchive}s for the given set of moduleIds.
     *
     * @param moduleIds keys to search for
     * @return set of ScriptArchives retrieved from the database
     */
    public Set<ScriptArchive> getScriptArchives(Set<String> moduleIds) throws IOException;

    /**
     * Delete an archive by ID
     * @param moduleId module id to delete
     * @throws ConnectionException
     */
    public void deleteArchive(String moduleId) throws IOException;
}
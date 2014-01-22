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
package com.netflix.nicobar.core.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;

/**
 * Interface to represent a persistence store for archives
 *
 * @author James Kojo
 */
public interface ArchiveRepository {

    public String getRepositoryId();

    /**
     * insert a Jar into the script archive
     * @param jarScriptArchive script archive which describes the jar and the ModuleSpec which should be inserted
     */
    public void insertArchive(JarScriptArchive jarScriptArchive)
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
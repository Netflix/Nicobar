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

/**
 * Data object which holds summary information for a given {@link ArchiveRepository}.
 * Used for display and reporting purposes.
 *
 * @author James Kojo
 */
public class RepositorySummary {
    private final String repositoryId;
    private final String description;
    private final int archiveCount;
    private final long lastUpdated;

    public RepositorySummary(String repositoryId, String description, int archiveCount, long lastUpdated) {
        this.repositoryId = repositoryId;
        this.description = description;
        this.archiveCount = archiveCount;
        this.lastUpdated = lastUpdated;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public String getDescription() {
        return description;
    }

    public int getArchiveCount() {
        return archiveCount;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
}

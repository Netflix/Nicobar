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
package com.netflix.nicobar.core.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.netflix.nicobar.core.archive.ModuleId;

/**
 * A repository view provides a window into querying the archives held by
 * an {@link ArchiveRepository}. Windowed views allow querying repositories
 * only for subsets of archives matching the windowing parameters. Archive
 * repositories must provide atleast one default view, and optionally provide
 * other named views.
 * @author Vasanth Asokan
 */
public interface RepositoryView {
    /**
     * Get the name of this view.
     * @return the name.
     */
    public String getName();

    /**
     * Get the last update times of all of the script archives under this
     * repository view.
     *
     * @return map of moduleId to last update time
     */
    public Map<ModuleId, Long> getArchiveUpdateTimes() throws IOException;

    /**
     * Get a summary of the archives in this repository view.
     *
     * @return displayable summary of the contents
     */
    public RepositorySummary getRepositorySummary() throws IOException;

    /**
     * Get a summary of all archives in this repository view
     *
     * @return List of summaries
     */
    public List<ArchiveSummary> getArchiveSummaries() throws IOException;
}

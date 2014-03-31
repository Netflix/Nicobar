package com.netflix.nicobar.core.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.netflix.nicobar.core.archive.ModuleId;

public interface RepositoryView {
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

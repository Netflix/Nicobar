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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * Templated base class for file system scanning daos.
 *
 * @author James Kojo
 */
public abstract class FileSystemScriptArchiveDao implements ScriptArchiveDao {

    protected final Path rootDir;
    /** Map of archive root path to the last known time it was deleted  */
    private final Map<Path, Long> deletedTimeIndex = new HashMap<Path, Long>();
    /** Map of archive root path to the archive name. Used for remembering deleted archives  */
    private final Map<Path, String> moduleIdIndex = new HashMap<Path, String>();

    protected FileSystemScriptArchiveDao(final Path rootDir) throws IOException {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
        if (!Files.isDirectory(rootDir) || !Files.isReadable(rootDir)) {
            throw new IllegalArgumentException("rootDir must be a readable directory: " + rootDir);
        }
    }

    @Override
    public synchronized UpdateResult getUpdatesSince(final long lastPollTime) throws IOException {
        Set<Path> visitedArchivePaths = new HashSet<Path>(moduleIdIndex.size()*2);
        final Set<Path> updatedArchivePaths = new HashSet<Path>(moduleIdIndex.size());
        findUpdatedArchives(lastPollTime, visitedArchivePaths, updatedArchivePaths);
        final Set<ScriptArchive> updatedArchives = new HashSet<ScriptArchive>(moduleIdIndex.size());

        // archive has been updated. convert it to a ScriptArchive and note the name
        for (Path archivePath : updatedArchivePaths) {
            ScriptArchive scriptArchive = createScriptArchive(archivePath);
            updatedArchives.add(scriptArchive);
            moduleIdIndex.put(archivePath, scriptArchive.getModuleSpec().getModuleId());
        }

        // archives may have come back into existence after they were deleted
        deletedTimeIndex.keySet().removeAll(visitedArchivePaths);
        updateDeleteTimes(visitedArchivePaths);
        Set<String> deleted = new HashSet<String>(moduleIdIndex.size());
        findDeleteArchives(lastPollTime, deleted);
        return new UpdateResult(updatedArchives, deleted);
    }

    /**
     * Search the underlying filesystem for updates to the {@link ScriptArchive}s
     * @param lastPollTime lower bound for the search
     * @param visitedArchivePaths result parameter which will be populated with the archive roots that were visited
     * @param updatedArchivePaths result parameter which will be populated with the subset of Paths that were recently modified
     * @throws IOException
     */
    protected abstract void findUpdatedArchives(long lastPollTime, Set<Path> visitedArchivePaths, Set<Path> updatedArchivePaths) throws IOException;

    /**
     * Create a {@link ScriptArchive} from the given archivePath.
     * @param archivePath
     * @return
     * @throws IOException
     */
    protected abstract ScriptArchive createScriptArchive(Path archivePath) throws IOException;

    /**
     * Updates the deleted time index.
     * Find deleted files by calculating the set difference between the last known set and the
     * most recently visited set.
     * @param visitedArchivePaths set of root archives dirs that have been most recently verified to exist
     */
    private void updateDeleteTimes(Set<Path> visitedArchivePaths) {
        Set<Path> deletedArchives = new HashSet<Path>(moduleIdIndex.keySet());
        deletedArchives.removeAll(visitedArchivePaths);
        long now = System.currentTimeMillis();
        for (Path path : deletedArchives) {
            if (!deletedTimeIndex.containsKey(path)) {
                // the delete time is not factually accurate, but it records
                // the first time we noticed the delete
                deletedTimeIndex.put(path, now);
            }
        }
    }

    /**
     * Find deleted archives
     * @param lastPollTime lower bound for the search
     * @param deleted result parameters which will be populated with the archive names of deleted archives
     */
    private void findDeleteArchives(final long lastPollTime, Set<String> deleted) {
        for (Entry<Path, Long> entry : deletedTimeIndex.entrySet()) {
            Path deletedPath = entry.getKey();
            Long deleteTime = entry.getValue();
            if (deleteTime > lastPollTime) {
                String deletedAchiveName = moduleIdIndex.get(deletedPath);
                if (deletedAchiveName != null) {
                    deleted.add(deletedAchiveName);
                }
            }
        }
    }
}
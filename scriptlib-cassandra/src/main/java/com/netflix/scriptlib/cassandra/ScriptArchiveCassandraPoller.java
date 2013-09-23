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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.persistence.ScriptArchivePoller;

/**
 * Implementation of the {@link ScriptArchivePoller} interface which is backed
 * by a {@link ScriptArchiveCassandraDao}.
 *
 * @author James Kojo
 */
public class ScriptArchiveCassandraPoller implements ScriptArchivePoller {
    private final ScriptArchiveCassandraDao archiveDao;

    /** Map of moduleId to last known update time of the archive */
    private final Map<String, Long> lastUpdateTimes = new HashMap<String, Long>();

    public ScriptArchiveCassandraPoller(ScriptArchiveCassandraDao archiveDao) {
        this.archiveDao = Objects.requireNonNull(archiveDao, "archiveDao");
    }

    @Override
    public synchronized PollResult poll(long lastPollTime) throws IOException {
        Map<String, Long> queryUpdateTimes;
        try {
            queryUpdateTimes = archiveDao.getArchiveUpdateTimes();
        } catch (ConnectionException e) {
            throw new IOException("Exception when attempting to query for last update times.",e);
        }
        Set<String> updatedModuleIds = new HashSet<String>(queryUpdateTimes.size());
        for (Entry<String, Long> entry : queryUpdateTimes.entrySet()) {
            String moduleId = entry.getKey();
            Long queryUpdateTime = entry.getValue();
            Long lastUpdateTime = lastUpdateTimes.get(moduleId);
            if (lastUpdateTime == null || lastUpdateTime < queryUpdateTime) {
                lastUpdateTime = queryUpdateTime;
                lastUpdateTimes.put(moduleId, lastUpdateTime);
            }
            if (lastUpdateTime >= lastPollTime) {
                updatedModuleIds.add(moduleId);
            }
        }

        // find deleted modules
        Map<String, Long> deletedModuleIds = new HashMap<String, Long>(lastUpdateTimes);
        deletedModuleIds.keySet().removeAll(queryUpdateTimes.keySet());
        Iterator<Entry<String, Long>> deletedEntries = deletedModuleIds.entrySet().iterator();
        while (deletedEntries.hasNext()) {
            Entry<String, Long> deletedEntry = deletedEntries.next();
            long deleteTime = deletedEntry.getValue();
            if (deleteTime <= lastPollTime) {
                deletedEntries.remove();
            }
        }

        // lookup updated archives and update archive times
        Set<ScriptArchive> scriptArchives;
        try {
            scriptArchives = archiveDao.getScriptArchives(updatedModuleIds);
        } catch (ConnectionException e) {
            throw new IOException("Exception when attempting to Fetch archives for moduleIds: " +
                updatedModuleIds, e);
        }
        return new PollResult(scriptArchives, deletedModuleIds.keySet());
    }
}

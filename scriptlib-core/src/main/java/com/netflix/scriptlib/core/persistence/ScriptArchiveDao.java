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
import java.util.Objects;
import java.util.Set;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * DAO interface for {@link ScriptArchive} persistence stores.
 *
 * @author James Kojo
 */
public interface ScriptArchiveDao {

    /**
     * Result of a DAO poll operation
     */
    public static class UpdateResult {
        private final Set<ScriptArchive> updatedArchives;
        private final Set<String> deletedModuleIds;
        public UpdateResult(Set<ScriptArchive> updatedArchives,
            Set<String> deletedModuleIds) {
            this.updatedArchives = Objects.requireNonNull(updatedArchives, "updatedArchives");
            this.deletedModuleIds = Objects.requireNonNull(deletedModuleIds, "deleteModuleIds");
        }
        /**
         * @return newly created or recently changed archives
         */
        public Set<ScriptArchive> getUpdatedArchives() {
            return updatedArchives;
        }
        /**
         * @return recently deleted archives
         */
        public Set<String> getDeletedModuleIds() {
            return deletedModuleIds;
        }
    }

    /**
     * Get a set of updates since the last poll interval.
     * @param lastPollTime last poll interval timestamp or 0 to get all archives.
     * @return
     * @throws IOException
     */
    public UpdateResult getUpdatesSince(long lastPollTime) throws IOException;
}

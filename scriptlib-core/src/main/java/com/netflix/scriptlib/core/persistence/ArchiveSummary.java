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

import java.util.Objects;

import com.netflix.scriptlib.core.archive.ScriptModuleSpec;

/**
 * Data object for a summary of an individual script archive. useful for displaying a list view
 * of archives.
 *
 * @author James Kojo
 */
public class ArchiveSummary {
    private final String moduleId;
    private final ScriptModuleSpec moduleSpec;
    private final long lastUpdateTime;

    public ArchiveSummary(String moduleId, ScriptModuleSpec moduleSpec, long lastUpdateTime) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.moduleSpec = moduleSpec;
        this.lastUpdateTime = lastUpdateTime;
    }
    public String getModuleId() {
        return moduleId;
    }

    public ScriptModuleSpec getModuleSpec() {
        return moduleSpec;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
}

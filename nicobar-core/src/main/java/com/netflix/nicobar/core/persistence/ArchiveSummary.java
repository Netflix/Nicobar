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

import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;

/**
 * Data object for a summary of an individual script archive. useful for displaying a list view
 * of archives.
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class ArchiveSummary {
    private final ModuleId moduleId;
    private final ScriptModuleSpec moduleSpec;
    private final long lastUpdateTime;
    private final Map<String, Object> deploySpecs;

    public ArchiveSummary(ModuleId moduleId, ScriptModuleSpec moduleSpec, long lastUpdateTime, @Nullable Map<String, Object> deploySpecs) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.moduleSpec = moduleSpec;
        this.lastUpdateTime = lastUpdateTime;
        this.deploySpecs = deploySpecs;
    }
    public ModuleId getModuleId() {
        return moduleId;
    }

    public ScriptModuleSpec getModuleSpec() {
        return moduleSpec;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Deployment specs for this archive. This depends on the underlying
     * archive repository's support for deploy specs, and thus could be null.
     * @return concrete set of deployment specs, or null
     */
    public Map<String, Object> getDeploySpecs() {
        return deploySpecs;
    }
}

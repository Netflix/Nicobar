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
package com.netflix.scriptlib.core.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Common configuration elements for converting a {@link ScriptArchive} to a module.
 * @author James Kojo
 */
public class ScriptModuleSpec {
    /**
     * Used to Construct a {@link ScriptModuleSpec}.
     */
    public static class Builder {
        private final String moduleId;
        private final Map<String, String> archiveMetadata = new LinkedHashMap<String, String>();
        private final List<String> dependencies = new LinkedList<String>();

        public Builder(String moduleId) {
            this.moduleId = moduleId;
        }
        /** Append all of the given metadata. */
        public Builder addMetadata(Map<String, String> metadata) {
            if (metadata != null) {
                archiveMetadata.putAll(metadata);
            }
            return this;
        }
        /** Append the given metadata. */
        public Builder addMetadata(String property, String value) {
            if (property != null && value != null) {
                archiveMetadata.put(property, value);
            }
            return this;
        }
        /** Add Module dependency. */
        public Builder addDependency(String dependencyName) {
            if (dependencyName != null) {
                dependencies.add(dependencyName);
            }
            return this;
        }
        /** Add Module dependencies. */
        public Builder addDependencies(List<String> dependencies) {
            if (dependencies != null) {
                dependencies.addAll(dependencies);
            }
            return this;
        }
        /** Build the {@link PathScriptArchive}. */
        public ScriptModuleSpec build() {
            return new ScriptModuleSpec(moduleId,
               Collections.unmodifiableMap(new HashMap<String, String>(archiveMetadata)),
               Collections.unmodifiableList(new ArrayList<String>(dependencies)));
        }
    }

    private final String moduleId;
    private final Map<String, String> archiveMetadata;
    private final List<String> dependencies;

    protected ScriptModuleSpec(String moduleId, Map<String, String> archiveMetadata, List<String> dependencies) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.archiveMetadata = Objects.requireNonNull(archiveMetadata, "archiveMetadata");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * @return id of the archive and the subsequently created module
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * @return Application specific metadata about this archive. This metadata will
     * be transferred to the Module after it's been created
     */
    public Map<String, String> getMetadata() {
        return archiveMetadata;
    }

    /**
     * @return the names of the modules that this archive depends on
     */
    public List<String> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        ScriptModuleSpec other = (ScriptModuleSpec) o;
        return Objects.equals(this.moduleId, other.moduleId) &&
            Objects.equals(this.archiveMetadata, other.archiveMetadata) &&
            Objects.equals(this.dependencies, other.dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleId, moduleId, dependencies);
    }
}


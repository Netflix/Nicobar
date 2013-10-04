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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

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
        private final Set<String> compilerDependencies = new LinkedHashSet<String>();
        private final Map<String, String> archiveMetadata = new LinkedHashMap<String, String>();
        private final Set<String> moduleDependencies = new LinkedHashSet<String>();

        public Builder(String moduleId) {
            this.moduleId = moduleId;
        }
        /** Add a dependency on the named compiler */
        public Builder addCompilerDependency(String compilerName) {
            if (compilerName != null) {
                compilerDependencies.add(compilerName);
            }
            return this;
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
        public Builder addModuleDependency(String dependencyName) {
            if (dependencyName != null) {
                moduleDependencies.add(dependencyName);
            }
            return this;
        }
        /** Add Module dependencies. */
        public Builder addModuleDependencies(Set<String> dependencies) {
            if (dependencies != null) {
                dependencies.addAll(dependencies);
            }
            return this;
        }
        /** Build the {@link PathScriptArchive}. */
        public ScriptModuleSpec build() {
            return new ScriptModuleSpec(moduleId,
               Collections.unmodifiableMap(new HashMap<String, String>(archiveMetadata)),
               Collections.unmodifiableSet(new LinkedHashSet<String>(moduleDependencies)),
               Collections.unmodifiableSet(new LinkedHashSet<String>(compilerDependencies)));
        }
    }

    private final String moduleId;
    private final Map<String, String> archiveMetadata;
    private final Set<String> moduleDependencies;
    private final Set<String> compilerDependencies;

    protected ScriptModuleSpec(String moduleId, Map<String, String> archiveMetadata, Set<String> moduleDependencies, Set<String> compilerDependencies) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.compilerDependencies = Objects.requireNonNull(compilerDependencies, "compilerDependencies");
        this.archiveMetadata = Objects.requireNonNull(archiveMetadata, "archiveMetadata");
        this.moduleDependencies = Objects.requireNonNull(moduleDependencies, "dependencies");
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
    public Set<String> getModuleDependencies() {
        return moduleDependencies;
    }

    /**
     * @return the names of the compilers that should process this archive
     */
    public Set<String> getCompilerDependencies() {
        return compilerDependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        ScriptModuleSpec other = (ScriptModuleSpec) o;
        return Objects.equals(this.moduleId, other.moduleId) &&
            Objects.equals(this.archiveMetadata, other.archiveMetadata) &&
            Objects.equals(this.moduleDependencies, other.moduleDependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleId, moduleId, moduleDependencies);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("moduleId", moduleId)
            .append("archiveMetadata", archiveMetadata)
            .append("dependencies", moduleDependencies)
            .toString();
    }
}


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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Common configuration for {@link ScriptArchive} implementations.
 *
 * @author James Kojo
 */
public class ScriptArchiveDescriptor {

    /**
     * Used to Construct a {@link PathScriptArchive}.
     * By default, this will generate a archiveName using the last element of the {@link Path}
     */
    public static class Builder {
        private String archiveId;
        private final Map<String, String> archiveMetadata = new LinkedHashMap<String, String>();
        private final List<String> dependencies = new LinkedList<String>();

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
        /** Build the {@link PathScriptArchive}. */
        public ScriptArchiveDescriptor build() throws IOException {
            return new ScriptArchiveDescriptor(archiveId,
               Collections.unmodifiableMap(new HashMap<String, String>(archiveMetadata)),
               Collections.unmodifiableList(new ArrayList<String>(dependencies)));
        }
    }


    private final String archiveId;
    private final Map<String, String> archiveMetadata;
    private final List<String> dependencies;

    protected ScriptArchiveDescriptor(String archiveId, Map<String, String> archiveMetadata, List<String> dependencies) {
        this.archiveId = Objects.requireNonNull(archiveId, "archiveName");
        this.archiveMetadata = Objects.requireNonNull(archiveMetadata, "archiveMetadata");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * @return id of this archive. Used to create the moduleId
     */
    public String getArchiveId() {
        return archiveId;
    }

    /**
     * @return Application specific metadata about this archive
     */
    public Map<String, String> getArchiveMetadata() {
        return archiveMetadata;
    }

    /**
     * @return the names of the modules that this archive depends on
     */
    public List<String> getDependencies() {
        return dependencies;
    }
}


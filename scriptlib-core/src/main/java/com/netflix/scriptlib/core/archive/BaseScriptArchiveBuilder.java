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

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Base class for script archive builders. contains common construction logic
 *
 * @author James Kojo
 */
@SuppressWarnings("unchecked")
public abstract class BaseScriptArchiveBuilder<T extends BaseScriptArchiveBuilder<?>>  {

    protected String archiveId;
    protected final Map<String, String> archiveMetadata = new LinkedHashMap<String, String>();
    protected final List<String> dependencies = new LinkedList<String>();

    protected BaseScriptArchiveBuilder() {
    }

    /** set the archiveId and add the metdata and dependencies */
    public T setDescriptor(ScriptArchiveDescriptor descriptor) {
        setArchiveId(descriptor.getArchiveId());
        addMetadata(descriptor.getArchiveMetadata());
        addDependencies(descriptor.getDependencies());
        return (T) this;
    }

    /** Override the default name */
    public T setArchiveId(String archiveId) {
        this.archiveId = archiveId;
        return (T) this;
    }

    /** Append all of the given metadata. */
    public T addMetadata(Map<String, String> metadata) {
        if (metadata != null) {
            archiveMetadata.putAll(metadata);
        }
        return (T) this;
    }

    /** Append the given metadata. */
    public T addMetadata(String property, String value) {
        if (property != null && value != null) {
            archiveMetadata.put(property, value);
        }
        return (T) this;
    }

    /** Add Module dependency. */
    public T addDependency(String dependencyName) {
        if (dependencyName != null) {
            dependencies.add(dependencyName);
        }
        return (T) this;
    }

    /** Add Module dependency. */
    public T addDependencies(List<String> dependencies) {
        if (dependencies != null) {
            dependencies.addAll(dependencies);
        }
        return (T) this;
    }
}
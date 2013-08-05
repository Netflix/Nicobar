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
package com.netflix.scriptlib.core.compile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data object which represents a bundle of scripts and associated resources.
 *
 * An archive is uniquely identified by it's name and version number.
 *
 * @author James Kojo
 */
public class ScriptArchive {
    private final String name;
    private final int version;
    private final Path rootPath;
    private final List<Path> files;
    private final Map<String, String> archiveMetadata;
    private final List<String> dependencies;

    /**
     * @param name non-empty name of the archive
     * @param version positive version number of the archive
     * @param absolute base path that all files are relative to
     * @param files relative paths to script files and resources that makeup the archive.
     * @param archiveMetadata application specific metadata about this archive
     * @param dependencies module names that this archive depends on for compilation and execution
     */
    public ScriptArchive(String name, int version, Path rootPath, List<Path> files, Map<String, String> archiveMetadata, List<String> dependencies) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = version;
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        this.files = Objects.requireNonNull(files, "files");
        this.archiveMetadata = archiveMetadata != null ? archiveMetadata : Collections.<String, String>emptyMap();
        this.dependencies = dependencies != null ? dependencies : Collections.<String>emptyList();
    }

    /**
     * @return Name of this archive. Usually the same as module name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return version of this archive. Usually the same as module version.
     */
    public int getVersion() {
        return version;
    }

    /**
     * @return absolute base path that all files are relative to
     */
    public Path getRootPath() {
        return rootPath;
    }
    /**
     * @return list of Paths to scripts and resources that constitute this archive
     */
    public List<Path> getFiles() {
        return files;
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

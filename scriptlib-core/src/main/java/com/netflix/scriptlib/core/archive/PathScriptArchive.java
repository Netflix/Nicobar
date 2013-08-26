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
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Script archive backed by a files in a {@link Path}. (Optionally) Includes all files under the given rootPath.
 *
 * @author James Kojo
 */
public class PathScriptArchive implements ScriptArchive {

    /**
     * Used to Construct a {@link PathScriptArchive}.
     * By default, this will generate a archiveName using the last element of the {@link Path}
     */
    public static class Builder {
        private String archiveName;
        private final Path rootDirPath;
        private final Map<String, String> archiveMetadata = new LinkedHashMap<String, String>();
        private final List<String> dependencies = new LinkedList<String>();
        private final HashSet<Path> addedFiles = new LinkedHashSet<Path>();
        boolean recurseRoot = true;

        /**
         * Start a builder with required parameters.
         * @param rootDirPath absolute path to the root directory to recursively add
         */
        public Builder(Path rootDirPath) {
            this.rootDirPath = rootDirPath;
        }
        /** Override the default name */
        public Builder setArchiveName(String archiveName) {
            this.archiveName = archiveName;
            return this;
        }
        /** If true, then add all of the files underneath the root path. default is true */
        public Builder setResurseRoot(boolean recurseRoot) {
            this.recurseRoot = recurseRoot;
            return this;
        }
        /**
         * Append a single file to the archive
         * @param file relative path from the root
         */
        public Builder addFile(Path file) {
            if (file != null) {
                addedFiles.add(file);
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
        public Builder addDependency(String dependencyName) {
            if (dependencyName != null) {
                dependencies.add(dependencyName);
            }
            return this;
        }
        /** Build the {@link PathScriptArchive}. */
        public PathScriptArchive build() throws IOException {
            String buildArchiveName = archiveName != null ? archiveName : this.rootDirPath.getFileName().toString();
            final LinkedHashSet<String> buildEntries = new LinkedHashSet<String>();
            if (recurseRoot) {
                Files.walkFileTree(this.rootDirPath, new SimpleFileVisitor<Path>() {
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relativePath = rootDirPath.relativize(file);
                        buildEntries.add(relativePath.toString());
                        return FileVisitResult.CONTINUE;
                    };
                });
            }
            for (Path file : addedFiles) {
                if (file.isAbsolute()) {
                    file = rootDirPath.relativize(file);
                }
                buildEntries.add(file.toString());
            }
            return new PathScriptArchive(buildArchiveName, rootDirPath, buildEntries,
               new HashMap<String, String>(archiveMetadata),
               new ArrayList<String>(dependencies));
        }
    }

    private final String archiveName;
    private final Set<String> entryNames;
    private final Path rootDirPath;
    private final URL rootUrl;
    private final Map<String, String> archiveMetadata;
    private final List<String> dependencies;

    protected PathScriptArchive(String archiveName, Path rootDirPath, Set<String> entries,
            Map<String, String> applicationMetaData, List<String> dependencies) throws IOException {
        this.archiveName = Objects.requireNonNull(archiveName, "archiveName");
        this.rootDirPath = Objects.requireNonNull(rootDirPath, "rootPath");
        if (!this.rootDirPath.isAbsolute()) throw new IllegalArgumentException("rootPath must be absolute.");
        this.entryNames = Collections.unmodifiableSet(Objects.requireNonNull(entries, "rootPath"));
        this.archiveMetadata = Objects.requireNonNull(applicationMetaData, "applicationMetaData");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.rootUrl = this.rootDirPath.toUri().toURL();
    }

    @Override
    public String getArchiveName() {
        return archiveName;
    }

    @Override
    public URL getRootUrl() {
        return rootUrl;
    }

    @Override
    public Set<String> getArchiveEntryNames() {
        return entryNames;
    }

    @Override
    @Nullable
    public URL getEntry(String entryName) throws IOException {
        if (!entryNames.contains(entryName)) {
            return null;
        }
        return rootDirPath.resolve(entryName).toUri().toURL();
    }

    @Override
    public Map<String, String> getArchiveMetadata() {
        return archiveMetadata;
    }

    @Override
    public List<String> getDependencies() {
        return dependencies;
    }
}

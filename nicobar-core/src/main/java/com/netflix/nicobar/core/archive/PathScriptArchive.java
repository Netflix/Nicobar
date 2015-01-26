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
package com.netflix.nicobar.core.archive;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.io.Charsets;

/**
 * Script archive backed by a files in a {@link Path}.
 *
 * @author James Kojo
 */
public class PathScriptArchive implements ScriptArchive {
    private final static ScriptModuleSpecSerializer DEFAULT_SPEC_SERIALIZER = new GsonScriptModuleSpecSerializer();
    /**
     * Used to Construct a {@link PathScriptArchive}.
     * <pre>
     * Default settings:
     * * generate a moduleId using the last element of the {@link Path}
     * * all files under the root path will be included in the archive.
     * * searches for a module spec file under the root directory called "moduleSpec.json" and
     *   uses the {@link GsonScriptModuleSpecSerializer} to deserialize it.
     * </pre>
     */
    public static class Builder {
        private final Path rootDirPath;
        private final Set<Path> addedFiles = new LinkedHashSet<Path>();
        private ScriptModuleSpec moduleSpec;
        boolean recurseRoot = true;
        private ScriptModuleSpecSerializer specSerializer = DEFAULT_SPEC_SERIALIZER;
        private long createTime;

        /**
         * Start a builder with required parameters.
         * @param rootDirPath absolute path to the root directory to recursively add
         */
        public Builder(Path rootDirPath) {
            this.rootDirPath = rootDirPath;
        }
        /** If true, then add all of the files underneath the root path. default is true */
        public Builder setRecurseRoot(boolean recurseRoot) {
            this.recurseRoot = recurseRoot;
            return this;
        }
        /** Set the module spec for this archive */
        public Builder setModuleSpec(ScriptModuleSpec moduleSpec) {
            this.moduleSpec = moduleSpec;
            return this;
        }

        /** override the default module spec file name */
        public Builder setModuleSpecSerializer(ScriptModuleSpecSerializer specSerializer) {
            this.specSerializer = specSerializer;
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
        /** Set the creation time */
        public Builder setCreateTime(long createTime) {
            this.createTime = createTime;
            return this;
        }
        /** Build the {@link PathScriptArchive}. */
        public PathScriptArchive build() throws IOException {
            ScriptModuleSpec buildModuleSpec = moduleSpec;
            if (buildModuleSpec == null) {
                // attempt to find a module spec in the root directory
                Path moduleSpecLocation = rootDirPath.resolve(specSerializer.getModuleSpecFileName());
                if (Files.exists(moduleSpecLocation)) {
                    byte[] bytes = Files.readAllBytes(moduleSpecLocation);
                    if (bytes != null && bytes.length > 0) {
                        String json = new String(bytes, Charsets.UTF_8);
                        buildModuleSpec = specSerializer.deserialize(json);
                    }
                }
                // create a default spec
                if (buildModuleSpec == null) {
                    ModuleId moduleId = ModuleId.create(this.rootDirPath.getFileName().toString());
                    buildModuleSpec = new ScriptModuleSpec.Builder(moduleId).build();
                }
            }
            final LinkedHashSet<String> buildEntries = new LinkedHashSet<String>();
            if (recurseRoot) {
                Files.walkFileTree(this.rootDirPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Override
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
            long buildCreateTime = createTime;
            if (buildCreateTime <= 0) {
                buildCreateTime = Files.getLastModifiedTime(rootDirPath).toMillis();
            }
            return new PathScriptArchive(buildModuleSpec, rootDirPath, buildEntries, buildCreateTime);
        }
    }

    private final Set<String> entryNames;
    private final Path rootDirPath;
    private final URL rootUrl;
    private final long createTime;
    private ScriptModuleSpec moduleSpec;

    protected PathScriptArchive(ScriptModuleSpec moduleSpec, Path rootDirPath, Set<String> entries, long createTime) throws IOException {
        this.moduleSpec = Objects.requireNonNull(moduleSpec, "moduleSpec");
        this.rootDirPath = Objects.requireNonNull(rootDirPath, "rootDirPath");
        if (!this.rootDirPath.isAbsolute()) throw new IllegalArgumentException("rootPath must be absolute.");
        this.entryNames = Collections.unmodifiableSet(Objects.requireNonNull(entries, "entries"));
        this.rootUrl = this.rootDirPath.toUri().toURL();
        this.createTime = createTime;
    }

    @Override
    public ScriptModuleSpec getModuleSpec() {
        return moduleSpec;
    }

    @Override
    public void setModuleSpec(ScriptModuleSpec spec) {
        this.moduleSpec = spec;
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
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        PathScriptArchive other = (PathScriptArchive) o;
        return Objects.equals(this.moduleSpec, other.moduleSpec) &&
            Objects.equals(this.entryNames, other.entryNames) &&
            Objects.equals(this.rootDirPath, other.rootDirPath) &&
            Objects.equals(this.rootUrl, other.rootUrl) &&
            Objects.equals(this.createTime, other.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleSpec, entryNames,rootDirPath, rootUrl, createTime);
    }
}

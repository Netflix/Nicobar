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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Script archive backed by a single file in a given {@link Path}.
 *
 */
public class SingleFileScriptArchive implements ScriptArchive {

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
        private final Path filePath;
        private ScriptModuleSpec moduleSpec;
        private long createTime;

        /**
         * Start a builder with required parameters.
         * @param rootDirPath absolute path to the root directory to recursively add
         */
        public Builder(Path filePath) {
            this.filePath = filePath;
        }
        
        /** Set the module spec for this archive */
        public Builder setModuleSpec(ScriptModuleSpec moduleSpec) {
            this.moduleSpec = moduleSpec;
            return this;
        }

        /** Set the creation time */
        public Builder setCreateTime(long createTime) {
            this.createTime = createTime;
            return this;
        }
        
        /** Build the {@link PathScriptArchive}. */
        public SingleFileScriptArchive build() throws IOException {
            long buildCreateTime = createTime;
            if (buildCreateTime <= 0) {
                buildCreateTime = Files.getLastModifiedTime(filePath).toMillis();
            }
            ScriptModuleSpec buildModuleSpec = moduleSpec;
            // Define moduleId canonically as the file name with '.'s replaced by '_'.
            if (buildModuleSpec == null) {
                String moduleId = this.filePath.getFileName().toString().replaceAll("\\.", "_");
                buildModuleSpec = new ScriptModuleSpec.Builder(moduleId).build();
            }
            
            Path rootDir = filePath.normalize().getParent();
            String fileName = rootDir.relativize(filePath).toString();
            return new SingleFileScriptArchive(buildModuleSpec, rootDir, fileName, buildCreateTime);
        }
    }

    private final ScriptModuleSpec moduleSpec;
    private final Set<String> entryNames;
    private final Path rootDirPath;
    private final URL rootUrl;
    private final long createTime;

    protected SingleFileScriptArchive(ScriptModuleSpec moduleSpec, Path rootDirPath, String fileName, long createTime) throws IOException {
        this.moduleSpec = Objects.requireNonNull(moduleSpec, "moduleSpec");
        this.rootDirPath = Objects.requireNonNull(rootDirPath, "rootDirPath");
        if (!this.rootDirPath.isAbsolute()) throw new IllegalArgumentException("rootPath must be absolute.");
        this.entryNames = Collections.singleton(Objects.requireNonNull(fileName, "fileName"));
        this.rootUrl = this.rootDirPath.toUri().toURL();
        this.createTime = createTime;
    }

    @Override
    public ScriptModuleSpec getModuleSpec() {
        return moduleSpec;
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
        SingleFileScriptArchive other = (SingleFileScriptArchive) o;
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

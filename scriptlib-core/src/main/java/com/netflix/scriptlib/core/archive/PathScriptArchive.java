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

import static com.netflix.scriptlib.core.archive.ScriptModuleSpec.MODULE_SPEC_FILE_NAME;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.io.Charsets;

/**
 * Script archive backed by a files in a {@link Path}. (Optionally) Includes all files under the given rootPath.
 *
 * @author James Kojo
 */
public class PathScriptArchive implements ScriptArchive {

    /**
     * Used to Construct a {@link PathScriptArchive}.
     * By default, this will generate a moduleId using the last element of the {@link Path}
     */
    public static class Builder {
        private final Path rootDirPath;
        private final Set<Path> addedFiles = new LinkedHashSet<Path>();
        private ScriptModuleSpec moduleSpec;
        boolean recurseRoot = true;

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
        /** Build the {@link PathScriptArchive}. */
        public PathScriptArchive build() throws IOException {
            ScriptModuleSpec buildModuleSpec = moduleSpec;
            if (buildModuleSpec == null) {
                // attempt to find a module spec in the root directory
                Path moduleSpecLocation = rootDirPath.resolve(MODULE_SPEC_FILE_NAME);
                if (Files.exists(moduleSpecLocation)) {
                    byte[] bytes = Files.readAllBytes(moduleSpecLocation);
                    if (bytes != null && bytes.length > 0) {
                        String json = new String(bytes, Charsets.UTF_8);
                        buildModuleSpec = new ScriptModuleSpecSerializer().deserialize(json);
                    }
                }
                // create a default spec
                if (buildModuleSpec == null) {
                    String moduleId = this.rootDirPath.getFileName().toString();
                    buildModuleSpec = new ScriptModuleSpec.Builder(moduleId).build();
                }
            }
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
            return new PathScriptArchive(buildModuleSpec, rootDirPath, buildEntries);
        }
    }

    private final ScriptModuleSpec moduleSpec;
    private final Set<String> entryNames;
    private final Path rootDirPath;
    private final URL rootUrl;

    protected PathScriptArchive(ScriptModuleSpec moduleSpec, Path rootDirPath, Set<String> entries) throws IOException {
        this.moduleSpec = Objects.requireNonNull(moduleSpec, "moduleSpec");
        this.rootDirPath = Objects.requireNonNull(rootDirPath, "rootDirPath");
        if (!this.rootDirPath.isAbsolute()) throw new IllegalArgumentException("rootPath must be absolute.");
        this.entryNames = Collections.unmodifiableSet(Objects.requireNonNull(entries, "entries"));
        this.rootUrl = this.rootDirPath.toUri().toURL();
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
}

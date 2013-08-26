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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.Nullable;

/**
 * Script archive backed by a {@link JarFile}.
 *
 * @author James Kojo
 */
public class JarScriptArchive implements ScriptArchive {

    /**
     * Used to Construct a {@link JarScriptArchive}.
     */
    public static class Builder {
        private final String name;
        private final Path jarPath;

        private final Map<String, String> archiveMetadata = new LinkedHashMap<String, String>();
        private final List<String> dependencies = new LinkedList<String>();
        /**
         * Start a builder with required parameters.
         * @param name archive name, will be used as module name
         * @param jarPath absolute path to the jarfile that this will represent
         */
        public Builder(String name, Path jarPath) {
            this.name = name;
            this.jarPath = jarPath;
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
        /** Build the {@link JarScriptArchive}. */
        public JarScriptArchive build() throws IOException {
           return new JarScriptArchive(name, jarPath, new HashMap<String, String>(archiveMetadata),
               new ArrayList<String>(dependencies));
        }
    }

    private final String archiveName;
    private final Set<String> entryNames;
    private final URL rootUrl;
    private final Map<String, String> archiveMetadata;
    private final List<String> dependencies;

    protected JarScriptArchive(String archiveName, Path jarPath, Map<String, String> applicationMetaData, List<String> dependencies) throws IOException {
        this.archiveName = Objects.requireNonNull(archiveName, "archiveName");
        Objects.requireNonNull(jarPath, "jarFile");
        if (!jarPath.isAbsolute()) throw new IllegalArgumentException("jarPath must be absolute.");

        this.archiveMetadata = Objects.requireNonNull(applicationMetaData, "applicationMetaData");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");

        // initialize the index
        JarFile jarFile = new JarFile(jarPath.toFile());
        Set<String> indexBuilder;
        try {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            indexBuilder = new HashSet<String>();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (!jarEntry.isDirectory()) {
                    indexBuilder.add(jarEntry.getName());
                }
            }
        } finally {
           jarFile.close();
        }
        entryNames = Collections.unmodifiableSet(indexBuilder);
        rootUrl = jarPath.toUri().toURL();
    }
    @Override
    public String getArchiveName() {
        return archiveName;
    }

    /**
     * Gets the root path in the form of 'file://path/to/jarfile/jarfile.jar".
     */
    @Override
    public URL getRootUrl() {
        return rootUrl;
    }

    @Override
    public Set<String> getArchiveEntryNames() {
        return entryNames;
    }

    @Nullable
    public URL getEntry(String entryName) throws IOException {
        if (!entryNames.contains(entryName)) {
            return null;
        }
        String spec = new StringBuilder()
            .append("jar:").append(rootUrl.toString()).append("!/").append(entryName).toString();
        return new URL(spec);
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

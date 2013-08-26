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
    private final static String JAR_FILE_SUFFIX = ".jar";

    /**
     * Used to Construct a {@link JarScriptArchive}.
     * By default, this will generate a archiveName using the name of the jarfile, minus the ".jar" suffix.
     */
    public static class Builder extends BaseScriptArchiveBuilder<Builder> {
        private final Path jarPath;

        /**
         * Start a builder with required parameters.
         * @param jarPath absolute path to the jarfile that this will represent
         */
        public Builder(Path jarPath) {
            this.jarPath = jarPath;
        }
        /** Build the {@link JarScriptArchive}. */
        public JarScriptArchive build() throws IOException {
            String buildArchiveName;
            if (archiveId != null){
                buildArchiveName = archiveId;
            } else {
                buildArchiveName = this.jarPath.getFileName().toString();
                if (buildArchiveName.endsWith(JAR_FILE_SUFFIX)) {
                    buildArchiveName = buildArchiveName.substring(0, buildArchiveName.lastIndexOf(JAR_FILE_SUFFIX));
                }
            }

            return new JarScriptArchive(
                new ScriptArchiveDescriptor(buildArchiveName,
                    Collections.unmodifiableMap(new HashMap<String, String>(archiveMetadata)),
                    Collections.unmodifiableList(new ArrayList<String>(dependencies))),
                jarPath);
        }
    }

    private final ScriptArchiveDescriptor descriptor;
    private final Set<String> entryNames;
    private final URL rootUrl;

    protected JarScriptArchive(ScriptArchiveDescriptor descriptor, Path jarPath) throws IOException {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(jarPath, "jarFile");
        if (!jarPath.isAbsolute()) throw new IllegalArgumentException("jarPath must be absolute.");

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
    public ScriptArchiveDescriptor getDescriptor() {
        return descriptor;
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
}

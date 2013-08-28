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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.annotation.Nullable;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

/**
 * Script archive backed by a {@link JarFile}.
 *
 * @author James Kojo
 */
public class JarScriptArchive implements ScriptArchive {
    private final static String JAR_FILE_SUFFIX = ".jar";

    /**
     * Used to Construct a {@link JarScriptArchive}.
     * By default, this will generate a moduleId using the name of the jarfile, minus the ".jar" suffix.
     */
    public static class Builder {
        private final Path jarPath;
        private ScriptModuleSpec moduleSpec;
        /**
         * Start a builder with required parameters.
         * @param jarPath absolute path to the jarfile that this will represent
         */
        public Builder(Path jarPath) {
            this.jarPath = jarPath;
        }
        /** Set the module spec for this archive */
        public Builder setModuleSpec(ScriptModuleSpec moduleSpec) {
            this.moduleSpec = moduleSpec;
            return this;
        }
        /** Build the {@link JarScriptArchive}. */
        public JarScriptArchive build() throws IOException {
            ScriptModuleSpec buildModuleSpec = moduleSpec;
            if (buildModuleSpec == null){
                // attempt to find a module spec in the jar file
                JarFile jarFile = new JarFile(jarPath.toFile());
                try {
                    ZipEntry zipEntry = jarFile.getEntry(MODULE_SPEC_FILE_NAME);
                    if (zipEntry != null) {
                        InputStream inputStream = jarFile.getInputStream(zipEntry);
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        IOUtils.copy(inputStream, outputStream);
                        byte[] bytes = outputStream.toByteArray();
                        if (bytes != null && bytes.length > 0) {
                            String json = new String(bytes, Charsets.UTF_8);
                            buildModuleSpec = new ScriptModuleSpecSerializer().deserialize(json);
                        }
                    }
                } finally {
                    IOUtils.closeQuietly(jarFile);
                }
                // create a default module spec
                if (buildModuleSpec == null) {
                    String moduleId = this.jarPath.getFileName().toString();
                    if (moduleId.endsWith(JAR_FILE_SUFFIX)) {
                        moduleId = moduleId.substring(0, moduleId.lastIndexOf(JAR_FILE_SUFFIX));
                    }
                    buildModuleSpec = new ScriptModuleSpec.Builder(moduleId).build();
                }
            }
            return new JarScriptArchive(buildModuleSpec,jarPath);
        }
    }

    private final ScriptModuleSpec moduleSpec;
    private final Set<String> entryNames;
    private final URL rootUrl;

    protected JarScriptArchive(ScriptModuleSpec moduleSpec, Path jarPath) throws IOException {
        this.moduleSpec = Objects.requireNonNull(moduleSpec, "moduleSpec");
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
    public ScriptModuleSpec getModuleSpec() {
        return moduleSpec;
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

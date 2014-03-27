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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
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
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Script archive backed by a {@link JarFile}.
 *
 * The jar file may optionally contain a module specification. If it does, then the module specification
 * is deserialized and used to construct the archive. Otherwise, a module specification with default values
 * is created.
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class JarScriptArchive implements ScriptArchive {
    /** Default file name of the optional {@link ScriptModuleSpec} in the archive */
    public final static String DEFAULT_MODULE_SPEC_FILE_NAME = "moduleSpec.json";
    private final static String JAR_FILE_SUFFIX = ".jar";
    private final static ScriptModuleSpecSerializer DEFAULT_SPEC_SERIALIZER = new GsonScriptModuleSpecSerializer();

    /**
     * Used to Construct a {@link JarScriptArchive}.
     * By default, this will generate a moduleId using the name of the jarfile, minus the ".jar" suffix.
     */
    public static class Builder {
        private final Path jarPath;
        private ScriptModuleSpec moduleSpec;
        private String specFileName;
        private ScriptModuleSpecSerializer specSerializer;
        private long createTime;
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
        /** override the default module spec file name */
        public Builder setModuleSpecFileName(String specFileName) {
            this.specFileName = specFileName;
            return this;
        }
        /** override the default module spec file name */
        public Builder setModuleSpecSerializer(ScriptModuleSpecSerializer specSerializer) {
            this.specSerializer = specSerializer;
            return this;
        }
        /** Set the creation time */
        public Builder setCreateTime(long createTime) {
            this.createTime = createTime;
            return this;
        }
        /** Build the {@link JarScriptArchive}. */
        public JarScriptArchive build() throws IOException {
            ScriptModuleSpec buildModuleSpec = moduleSpec;
            String moduleSpecEntry = null;

            if (buildModuleSpec == null){
                String buildSpecFileName = specFileName != null ? specFileName : DEFAULT_MODULE_SPEC_FILE_NAME;
                // attempt to find a module spec in the jar file
                JarFile jarFile = new JarFile(jarPath.toFile());
                try {
                    ZipEntry zipEntry = jarFile.getEntry(buildSpecFileName);
                    if (zipEntry != null) {
                        moduleSpecEntry = buildSpecFileName;
                        InputStream inputStream = jarFile.getInputStream(zipEntry);
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        IOUtils.copy(inputStream, outputStream);
                        byte[] bytes = outputStream.toByteArray();
                        if (bytes != null && bytes.length > 0) {
                            String json = new String(bytes, Charsets.UTF_8);
                            ScriptModuleSpecSerializer buildSpecSerializer = specSerializer != null  ? specSerializer :
                                DEFAULT_SPEC_SERIALIZER;
                            buildModuleSpec = buildSpecSerializer.deserialize(json);
                        }
                    }
                } finally {
                    IOUtils.closeQuietly(jarFile);
                }
                // create a default module spec
                if (buildModuleSpec == null) {
                    String jarFileName = this.jarPath.getFileName().toString();
                    if (jarFileName.endsWith(JAR_FILE_SUFFIX)) {
                      jarFileName = jarFileName.substring(0, jarFileName.lastIndexOf(JAR_FILE_SUFFIX));
                    }

                    ModuleId moduleId = ModuleId.create(jarFileName);
                    buildModuleSpec = new ScriptModuleSpec.Builder(moduleId).build();
                }
            }
            long buildCreateTime = createTime;
            if (buildCreateTime <= 0) {
                buildCreateTime = Files.getLastModifiedTime(jarPath).toMillis();
            }
            return new JarScriptArchive(buildModuleSpec, jarPath, moduleSpecEntry, buildCreateTime);
        }
    }

    private final ScriptModuleSpec moduleSpec;
    private final Set<String> entryNames;
    private final URL rootUrl;
    private final long createTime;

    protected JarScriptArchive(ScriptModuleSpec moduleSpec, Path jarPath, long createTime) throws IOException {
        this(moduleSpec, jarPath, null, createTime);
    }

    protected JarScriptArchive(ScriptModuleSpec moduleSpec, Path jarPath, String moduleSpecEntry, long createTime) throws IOException {
        this.createTime = createTime;
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
                // Skip adding moduleSpec to archive entries
                if (jarEntry.getName().equals(moduleSpecEntry)) {
                    continue;
                }

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

    @Override
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
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        JarScriptArchive other = (JarScriptArchive) o;
        return Objects.equals(this.moduleSpec, other.moduleSpec) &&
            Objects.equals(this.entryNames, other.entryNames) &&
            Objects.equals(this.rootUrl, other.rootUrl) &&
            Objects.equals(this.createTime, other.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleSpec, entryNames, rootUrl, createTime);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("moduleSpec", moduleSpec)
            .append("entryNames", entryNames)
            .append("rootUrl", rootUrl)
            .append("createTime", createTime)
            .toString();
    }
}

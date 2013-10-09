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
package com.netflix.scriptlib.core.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.Nullable;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.netflix.scriptlib.core.archive.GsonScriptModuleSpecSerializer;
import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptModuleSpec;
import com.netflix.scriptlib.core.archive.ScriptModuleSpecSerializer;

/**
 * {@link ArchiveRepository} implementation which stores {@link ScriptArchive}s in
 * sub-directories a root directory.
 *
 * @author James Kojo
 */
public class PathArchiveRepository implements ArchiveRepository {

    private static final ScriptModuleSpecSerializer DEFAULT_SERIALIZER = new GsonScriptModuleSpecSerializer();
    public static class Builder {
        private final Path rootDir;
        private String repositoryId ;
        private ScriptModuleSpecSerializer moduleSpecSerializer = DEFAULT_SERIALIZER;
        private String respositoryDescription;

        public Builder(Path rootDir){
            this.rootDir = rootDir;
        }

        public Builder setRepostoryId(String repositoryId) {
           this.repositoryId = repositoryId;
           return this;
        }

        public Builder setRepositoryDescription(String respositoryDescription) {
            this.respositoryDescription = respositoryDescription;
            return this;
        }
        public Builder setModuleSpecSerializer(ScriptModuleSpecSerializer moduleSpecSerializer) {
            this.moduleSpecSerializer = moduleSpecSerializer;
            return this;
        }

        public PathArchiveRepository build() {
            String buildRepositoryId = repositoryId;
            if (buildRepositoryId == null) {
                buildRepositoryId = rootDir.toString();
            }
            String buildDescription = respositoryDescription;
            if (buildDescription == null) {
                buildDescription =  PathArchiveRepository.class.getSimpleName() + ": " + rootDir.toString();
            }
            return new PathArchiveRepository(rootDir, buildRepositoryId, buildDescription, moduleSpecSerializer);
        }
    }

    /**
     * Directory filter which finds readable directories
     */
    protected final static DirectoryStream.Filter<Path> DIRECTORY_FILTER = new DirectoryStream.Filter<Path>() {
        public boolean accept(Path path) throws IOException {
            return (Files.isDirectory(path) && Files.isReadable(path));
        }
    };

    private final Path rootDir;
    private final String repositoryId ;
    private final ScriptModuleSpecSerializer moduleSpecSerializer;
    private final String repositoryDescription;

    protected PathArchiveRepository(Path rootDir, String repositoryId, String repositoryDescription, ScriptModuleSpecSerializer moduleSpecSerializer) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
        this.repositoryId = Objects.requireNonNull(repositoryId, "repositoryId");
        this.moduleSpecSerializer = Objects.requireNonNull(moduleSpecSerializer, "moduleSpecSerializer");
        this.repositoryDescription = Objects.requireNonNull(repositoryDescription, "repositoryDescription");
    }

    @Override
    public String getRepositoryId() {
        return repositoryId;
    }

    @Override
    public void insertArchive(String moduleId, Path jarFilePath, @Nullable ScriptModuleSpec moduleSpec)
            throws IOException {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(jarFilePath, "jarFilePath");
        Path moduleDir = rootDir.resolve(moduleId);
        if (Files.exists(moduleDir)) {
            FileUtils.deleteDirectory(moduleDir.toFile());
        }
        JarFile jarFile = new JarFile(jarFilePath.toFile());
        try {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                Path entryName = moduleDir.resolve(jarEntry.getName());
                if (jarEntry.isDirectory()) {
                    Files.createDirectories(entryName);
                } else {
                    InputStream inputStream = jarFile.getInputStream(jarEntry);
                    try {
                        Files.copy(inputStream, entryName);
                    } finally {
                        IOUtils.closeQuietly(inputStream);
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(jarFile);
        }
        if (moduleSpec != null) {
            String serialized = moduleSpecSerializer.serialize(moduleSpec);
            Files.write(moduleDir.resolve(moduleSpecSerializer.getModuleSpecFileName()), serialized.getBytes(Charsets.UTF_8));
        }
        // update the timestamp on the module directory to indicate that the module has been updated
        Files.setLastModifiedTime(moduleDir, Files.getLastModifiedTime(jarFilePath));
    }

    @Override
    public Map<String, Long> getArchiveUpdateTimes() throws IOException {
        Map<String, Long> updateTimes = new LinkedHashMap<String, Long>();
        DirectoryStream<Path> archiveDirs = Files.newDirectoryStream(rootDir, DIRECTORY_FILTER);
        for (Path archiveDir: archiveDirs) {
            Path absoluteArchiveDir = rootDir.resolve(archiveDir);
            long lastUpdateTime = Files.getLastModifiedTime(absoluteArchiveDir).toMillis();
            String moduleId = archiveDir.getFileName().toString();
            updateTimes.put(moduleId, lastUpdateTime);
        }
        return updateTimes;
    }

    @Override
    public RepositorySummary getRepositorySummary() throws IOException {
        Map<String, Long> archiveUpdateTimes = getArchiveUpdateTimes();
        long maxUpdateTime = 0;
        for (Long updateTime : archiveUpdateTimes.values()) {
            if (updateTime > maxUpdateTime) {
                maxUpdateTime = updateTime;
            }
        }
        return new RepositorySummary(getRepositoryId(), repositoryDescription, archiveUpdateTimes.size(), maxUpdateTime);
    }

    @Override
    public List<ArchiveSummary> getArchiveSummaries() throws IOException {
        List<ArchiveSummary> summaries = new LinkedList<ArchiveSummary>();
        Set<String> moduleIds = getArchiveUpdateTimes().keySet();
        Set<ScriptArchive> scriptArchives = getScriptArchives(moduleIds);
        for (ScriptArchive scriptArchive : scriptArchives) {
            ScriptModuleSpec moduleSpec = scriptArchive.getModuleSpec();
            long lastUpdateTime = scriptArchive.getCreateTime();
            summaries.add(new ArchiveSummary(moduleSpec.getModuleId(), moduleSpec, lastUpdateTime));
        }
        return summaries;
    }

    @Override
    public Set<ScriptArchive> getScriptArchives(Set<String> moduleIds) throws IOException {
        Set<ScriptArchive> scriptArchives = new LinkedHashSet<ScriptArchive>();
        for (String moduleId : moduleIds) {
            Path moduleDir = rootDir.resolve(moduleId);
            if (Files.exists(moduleDir)) {
                PathScriptArchive scriptArchive = new PathScriptArchive.Builder(moduleDir).build();
                scriptArchives.add(scriptArchive);
            }
        }
        return scriptArchives;
    }

    @Override
    public void deleteArchive(String moduleId) throws IOException {
        Path moduleDir = rootDir.resolve(moduleId);
        if (Files.exists(moduleDir)) {
            FileUtils.deleteDirectory(moduleDir.toFile());
        }
    }
}

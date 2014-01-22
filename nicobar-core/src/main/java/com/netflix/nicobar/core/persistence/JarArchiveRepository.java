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
package com.netflix.nicobar.core.persistence;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
import java.util.jar.JarOutputStream;

import org.apache.commons.io.IOUtils;

import com.netflix.nicobar.core.archive.GsonScriptModuleSpecSerializer;
import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.archive.ScriptModuleSpecSerializer;

/**
 * {@link ArchiveRepository} implementation which stores {@link ScriptArchive}s in individual
 * jar files in a single root directory
 *
 * @author James Kojo
 */
public class JarArchiveRepository implements ArchiveRepository {

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

        public JarArchiveRepository build() {
            String buildRepositoryId = repositoryId;
            if (buildRepositoryId == null) {
                buildRepositoryId = rootDir.toString();
            }
            String buildDescription = respositoryDescription;
            if (buildDescription == null) {
                buildDescription =  JarArchiveRepository.class.getSimpleName() + ": " + rootDir.toString();
            }
            return new JarArchiveRepository(rootDir, buildRepositoryId, buildDescription, moduleSpecSerializer);
        }
    }

    /**
     * Directory filter which finds readable jar files
     */
    protected final static DirectoryStream.Filter<Path> JAR_FILE_FILTER = new DirectoryStream.Filter<Path>() {
        public boolean accept(Path path) throws IOException {
            return (Files.isRegularFile(path) &&
                Files.isReadable(path)) &&
                path.toString().endsWith(".jar");
        }
    };

    private final Path rootDir;
    private final String repositoryId ;
    private final ScriptModuleSpecSerializer moduleSpecSerializer;
    private final String repositoryDescription;

    protected JarArchiveRepository(Path rootDir, String repositoryId, String repositoryDescription, ScriptModuleSpecSerializer moduleSpecSerializer) {
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
    public void insertArchive(JarScriptArchive jarScriptArchive)
            throws IOException {
        Objects.requireNonNull(jarScriptArchive, "jarScriptArchive");
        ScriptModuleSpec moduleSpec = jarScriptArchive.getModuleSpec();
        String moduleId = moduleSpec.getModuleId();
        Path moduleJarPath = getModuleJarPath(moduleId);
        Files.deleteIfExists(moduleJarPath);
        JarFile sourceJarFile;
        try {
            sourceJarFile = new JarFile(jarScriptArchive.getRootUrl().toURI().getPath());
        } catch (URISyntaxException e) {
           throw new IOException(e);
        }
        JarOutputStream destJarFile = new JarOutputStream(new FileOutputStream(moduleJarPath.toFile()));
        try {
            String moduleSpecFileName = moduleSpecSerializer.getModuleSpecFileName();
            Enumeration<JarEntry> sourceEntries = sourceJarFile.entries();
            while (sourceEntries.hasMoreElements()) {
                JarEntry sourceEntry = sourceEntries.nextElement();
                if (sourceEntry.getName().equals(moduleSpecFileName)) {
                    // avoid double entry for module spec
                    continue;
                }
                destJarFile.putNextEntry(sourceEntry);
                if (!sourceEntry.isDirectory()) {
                    InputStream inputStream = sourceJarFile.getInputStream(sourceEntry);
                    IOUtils.copy(inputStream, destJarFile);
                    IOUtils.closeQuietly(inputStream);
                }
                destJarFile.closeEntry();
            }
            // write the module spec
            String serialized = moduleSpecSerializer.serialize(moduleSpec);
            JarEntry moduleSpecEntry = new JarEntry(moduleSpecSerializer.getModuleSpecFileName());
            destJarFile.putNextEntry(moduleSpecEntry);
            IOUtils.write(serialized, destJarFile);
            destJarFile.closeEntry();
        } finally {
            IOUtils.closeQuietly(sourceJarFile);
            IOUtils.closeQuietly(destJarFile);
        }
        // update the timestamp on the jar file to indicate that the module has been updated
        Files.setLastModifiedTime(moduleJarPath, FileTime.fromMillis(jarScriptArchive.getCreateTime()));
    }

    @Override
    public Map<String, Long> getArchiveUpdateTimes() throws IOException {
        Map<String, Long> updateTimes = new LinkedHashMap<String, Long>();
        DirectoryStream<Path> archiveJars = Files.newDirectoryStream(rootDir, JAR_FILE_FILTER);
        for (Path archiveJar: archiveJars) {
            Path absoluteArchiveFile = rootDir.resolve(archiveJar);
            long lastUpdateTime = Files.getLastModifiedTime(absoluteArchiveFile).toMillis();
            String moduleId = archiveJar.getFileName().toString();
            if (moduleId.endsWith(".jar")) {
                moduleId = moduleId.substring(0, moduleId.lastIndexOf(".jar"));
            }
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
            Path moduleJar = getModuleJarPath(moduleId);
            if (Files.exists(moduleJar)) {
                JarScriptArchive scriptArchive = new JarScriptArchive.Builder(moduleJar).build();
                scriptArchives.add(scriptArchive);
            }
        }
        return scriptArchives;
    }

    @Override
    public void deleteArchive(String moduleId) throws IOException {
        Path moduleJar = getModuleJarPath(moduleId);
        if (Files.exists(moduleJar)) {
            Files.delete(moduleJar);
        }
    }

    /**
     * Translated a module id to an absolute path of the module jar
     */
    protected Path getModuleJarPath(String moduleId) {
        Path moduleJarPath = rootDir.resolve(moduleId + ".jar");
        return moduleJarPath;
    }
}

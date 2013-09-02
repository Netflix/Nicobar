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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.netflix.scriptlib.core.archive.JarScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * {@link ScriptArchivePoller} which scans the local filesystem for jar files
 * and converting them to {@link ScriptArchive}s
 *
 * @author James Kojo
 */
public class JarScriptArchivePoller extends FileSystemScriptArchivePoller {
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

    public JarScriptArchivePoller(final Path rootDir) throws IOException {
        super(rootDir);
    }

    /**
     * Search the root directory for jar archives that have changed
     * @param lastPollTime lower bound for the search
     * @param visitedArchivePaths result parameter which will be populated with the archive roots that were visited
     * @param updatedArchivePaths result parameter which will be populated with the updated archives
     * @throws IOException
     */
    @Override
    protected void findUpdatedArchives(final long lastPollTime, Set<Path> visitedArchivePaths,
        final Set<Path> updatedArchivePaths) throws IOException {
        DirectoryStream<Path> jarPaths = Files.newDirectoryStream(rootDir, JAR_FILE_FILTER);
        for (final Path jarPath: jarPaths) {
            Path absoluteJarPath = rootDir.resolve(jarPath);
            long lastModifiedTime = Files.getLastModifiedTime(jarPath).toMillis();
            if (lastModifiedTime > lastPollTime) {
                updatedArchivePaths.add(absoluteJarPath);
            }
            visitedArchivePaths.add(absoluteJarPath);
        }
    }

    /**
     * Create the script archive from the given archive root for updated archives
     */
    @Override
    protected ScriptArchive createScriptArchive(Path jarFilePath) throws IOException {
        ScriptArchive archive = new JarScriptArchive.Builder(jarFilePath).build();
        return archive;
    }
}

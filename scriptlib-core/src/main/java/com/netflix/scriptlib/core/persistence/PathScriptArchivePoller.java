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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * {@link ScriptArchivePoller} which scans the local filesystem for directories which
 * are turned into {@link ScriptArchive}s.
 *
 * @author James Kojo
 */
public class PathScriptArchivePoller extends FileSystemScriptArchivePoller {

    /**
     * Directory filter which finds readable directories
     */
    protected final static DirectoryStream.Filter<Path> DIRECTORY_FILTER = new DirectoryStream.Filter<Path>() {
        public boolean accept(Path path) throws IOException {
            return (Files.isDirectory(path) && Files.isReadable(path));
        }
    };

    public PathScriptArchivePoller(final Path rootDir) throws IOException {
        super(rootDir);
    }

    /**
     * Recursively search the root directory for changes to the archives.
     * @param lastPollTime lower bound for the search
     * @param visitedArchivePaths result parameter which will be populated with the archive roots that were visited
     * @param updated result parameter which will be populated with the subset of Paths that were recently modified
     * @throws IOException
     */
    @Override
    protected void findUpdatedArchives(final long lastPollTime, Set<Path> visitedArchivePaths,
        final Map<Path, Long> updated) throws IOException {
        DirectoryStream<Path> archiveDirs = Files.newDirectoryStream(rootDir, DIRECTORY_FILTER);
        for (final Path archiveDir: archiveDirs) {
            final Path absoluteArchiveDir = rootDir.resolve(archiveDir);
            Files.walkFileTree(archiveDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return visit(dir);
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    return visit(file);
                }
                public FileVisitResult visit(Path path) throws IOException {
                    long visitedLastModified = Files.getLastModifiedTime(path).toMillis();
                    if (visitedLastModified > lastPollTime) {
                        updated.put(absoluteArchiveDir, visitedLastModified);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            visitedArchivePaths.add(absoluteArchiveDir);
        }
    }

    /**
     * Create the script archive from the given archive root for updated archives
     */
    @Override
    protected ScriptArchive createScriptArchive(Path archiveRootPath, long lastUpdateTime) throws IOException {
        ScriptArchive archive = new PathScriptArchive.Builder(archiveRootPath)
            .setCreateTime(lastUpdateTime)
            .build();
        return archive;
    }
}

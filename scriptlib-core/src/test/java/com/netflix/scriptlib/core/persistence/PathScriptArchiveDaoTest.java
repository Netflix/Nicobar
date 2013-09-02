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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.persistence.ScriptArchivePoller.PollResult;

/**
 * Unit tests for {@link PathScriptArchivePoller}
 *
 * @author James Kojo
 */
public class PathScriptArchiveDaoTest {
    private final static String TEXT_PATH_RESOURCE_NAME = "paths/test-text";
    private final static String MODULE_SPEC_PATH_RESOURCE_NAME = "paths/test-modulespec";
    private Path rootArchiveDirectory;


    @BeforeClass
    public void setup() throws IOException {
        rootArchiveDirectory = Files.createTempDirectory(PathScriptArchiveDaoTest.class.getSimpleName()+"_");
        FileUtils.forceDeleteOnExit(rootArchiveDirectory.toFile());
        copyArchive(TEXT_PATH_RESOURCE_NAME);
        copyArchive(MODULE_SPEC_PATH_RESOURCE_NAME);
    }

    /**
     * Simulate what a client might do at startup
     */
    @Test
    public void testInitialLoad() throws Exception {
        PathScriptArchivePoller archivePoller = new PathScriptArchivePoller(rootArchiveDirectory);
        PollResult pollResult = archivePoller.poll(0);
        Set<String> deletedModuleIds = pollResult.getDeletedModuleIds();
        Set<ScriptArchive> updatedArchives = pollResult.getUpdatedArchives();
        assertEquals(updatedArchives.size(), 2);
        assertTrue(deletedModuleIds.isEmpty(), deletedModuleIds.toString());
        Set<String> updatedModuleIds = new HashSet<String>();
        for (ScriptArchive scriptArchive : updatedArchives) {
            updatedModuleIds.add(scriptArchive.getModuleSpec().getModuleId());
        }
        assertEquals(updatedModuleIds, new HashSet<String>(Arrays.asList("test-modulespec-moduleId", "test-text")));
    }

    /**
     * Simulate what a client might do on an on-going bases
     */
    @Test
    public void testPolling() throws Exception {
        PollResult pollResult;
        Set<ScriptArchive> updatedArchives;
        Set<String> deletedModuleIds;
        PathScriptArchivePoller archivePoller = new PathScriptArchivePoller(rootArchiveDirectory);

        // initial startup phase
        archivePoller.poll(0);
        long lastPollTime = System.currentTimeMillis();

        // poll for changes
        pollResult = archivePoller.poll(lastPollTime);
        updatedArchives = pollResult.getUpdatedArchives();
        deletedModuleIds = pollResult.getDeletedModuleIds();
        assertTrue(updatedArchives.isEmpty(), updatedArchives.toString());
        assertTrue(deletedModuleIds.isEmpty(), deletedModuleIds.toString());
        lastPollTime += 1000;

        // touch a file to force a reload then poll. some filesystems only have 1 second granularity, so advance by at least that much
        Path moduleSpecPath = Paths.get(rootArchiveDirectory.toString(), "test-modulespec", PathScriptArchive.DEFAULT_MODULE_SPEC_FILE_NAME);
        Files.setLastModifiedTime(moduleSpecPath, FileTime.fromMillis(lastPollTime+1000));
        pollResult = archivePoller.poll(lastPollTime);
        updatedArchives = pollResult.getUpdatedArchives();
        deletedModuleIds = pollResult.getDeletedModuleIds();
        assertEquals(updatedArchives.size(), 1);
        assertEquals(updatedArchives.iterator().next().getModuleSpec().getModuleId(), "test-modulespec-moduleId");
        assertTrue(deletedModuleIds.isEmpty(), deletedModuleIds.toString());
        lastPollTime += 1000;

        // poll one more time to make sure the state has been reset properly
        pollResult = archivePoller.poll(lastPollTime);
        updatedArchives = pollResult.getUpdatedArchives();
        deletedModuleIds = pollResult.getDeletedModuleIds();
        assertTrue(updatedArchives.isEmpty(), updatedArchives.toString());
        assertTrue(deletedModuleIds.isEmpty(), deletedModuleIds.toString());
    }
    /**
     * Simulate a deletion
     */
    @Test(priority=Integer.MAX_VALUE) // run this last
    public void testDelete() throws Exception {
        PollResult pollResult;
        Set<ScriptArchive> updatedArchives;
        Set<String> deletedModuleIds;
        PathScriptArchivePoller archivePoller = new PathScriptArchivePoller(rootArchiveDirectory);

        // initial startup phase
        archivePoller.poll(0);
        long lastPollTime = System.currentTimeMillis() - 1000;

        // delete a module
        Path moduleSpecPath = Paths.get(rootArchiveDirectory.toString(), "test-modulespec");
        FileUtils.deleteDirectory(moduleSpecPath.toFile());
        pollResult = archivePoller.poll(lastPollTime);
        updatedArchives = pollResult.getUpdatedArchives();
        deletedModuleIds = pollResult.getDeletedModuleIds();
        assertTrue(updatedArchives.isEmpty(), updatedArchives.toString());
        assertEquals(deletedModuleIds.size(), 1);
        assertEquals(deletedModuleIds.iterator().next(), "test-modulespec-moduleId");
        lastPollTime += 2000;

        // poll one more time to make sure the state has been reset properly
        pollResult = archivePoller.poll(lastPollTime);
        updatedArchives = pollResult.getUpdatedArchives();
        deletedModuleIds = pollResult.getDeletedModuleIds();
        assertTrue(updatedArchives.isEmpty(), updatedArchives.toString());
        assertTrue(deletedModuleIds.isEmpty(), deletedModuleIds.toString());
        lastPollTime += 2000;

        // restore the module and reload
        copyArchive(MODULE_SPEC_PATH_RESOURCE_NAME);
        Files.setLastModifiedTime(moduleSpecPath, FileTime.fromMillis(lastPollTime+1000));
        pollResult = archivePoller.poll(lastPollTime);
        updatedArchives = pollResult.getUpdatedArchives();
        deletedModuleIds = pollResult.getDeletedModuleIds();
        assertEquals(updatedArchives.size(), 1);
        assertTrue(deletedModuleIds.isEmpty(), deletedModuleIds.toString());
    }


    /**
     * Copy the given archive resource to the isolated workspace
     */
    private void copyArchive(String archiveResourceName) throws IOException {
        URL moduleSpecArchiveUrl = getClass().getClassLoader().getResource(archiveResourceName);
        Path moduleSpecArchivePath = Paths.get(moduleSpecArchiveUrl.getFile());
        FileUtils.copyDirectory(moduleSpecArchivePath.toFile(), rootArchiveDirectory.resolve(moduleSpecArchivePath.getFileName()).toFile());
    }
}

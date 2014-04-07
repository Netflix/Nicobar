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

import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_MODULE_SPEC_JAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.persistence.ArchiveRepository;
import com.netflix.nicobar.core.persistence.ArchiveSummary;
import com.netflix.nicobar.core.persistence.RepositorySummary;

/**
 * Base Tests for {@link ArchiveRepository} implementations
 *
 * @author James Kojo
 */
public abstract class ArchiveRepositoryTest {
    private Path testArchiveJarFile;

    @BeforeClass
    public void setup() throws Exception {
        URL testJarUrl = getClass().getClassLoader().getResource(TEST_MODULE_SPEC_JAR.getResourcePath());
        if (testJarUrl == null) {
            fail("Couldn't locate " + TEST_MODULE_SPEC_JAR.getResourcePath());
        }
        testArchiveJarFile = Files.createTempFile(TEST_MODULE_SPEC_JAR.getModuleId().toString(), ".jar");
        InputStream inputStream = testJarUrl.openStream();
        Files.copy(inputStream, testArchiveJarFile, StandardCopyOption.REPLACE_EXISTING);
        IOUtils.closeQuietly(inputStream);
    }
     /**
     * Create an instance to be test
     */
    public abstract ArchiveRepository createRepository();

    /**
     * Test insert, update, delete
     */
    @Test
    public void testRoundTrip() throws Exception {
        ArchiveRepository repository = createRepository();
        JarScriptArchive jarScriptArchive = new JarScriptArchive.Builder(testArchiveJarFile).build();
        ModuleId testModuleId = TEST_MODULE_SPEC_JAR.getModuleId();
        repository.insertArchive(jarScriptArchive);
        Map<ModuleId, Long> archiveUpdateTimes = repository.getDefaultView().getArchiveUpdateTimes();
        long expectedUpdateTime = Files.getLastModifiedTime(testArchiveJarFile).toMillis();
        assertEquals(archiveUpdateTimes, Collections.singletonMap(testModuleId, expectedUpdateTime));

        // assert getScriptArchives
        Set<ScriptArchive> scriptArchives = repository.getScriptArchives(archiveUpdateTimes.keySet());
        assertEquals(scriptArchives.size(), 1, scriptArchives.toString());
        ScriptArchive scriptArchive = scriptArchives.iterator().next();
        assertEquals(scriptArchive.getModuleSpec().getModuleId(), testModuleId);
        assertEquals(scriptArchive.getCreateTime(), expectedUpdateTime);

        // assert getArchiveSummaries
        List<ArchiveSummary> archiveSummaries = repository.getDefaultView().getArchiveSummaries();
        assertEquals(archiveSummaries.size(), 1);
        ArchiveSummary archiveSummary = archiveSummaries.get(0);
        assertEquals(archiveSummary.getModuleId(), testModuleId);
        assertEquals(archiveSummary.getLastUpdateTime(), expectedUpdateTime);

        // assert getRepositorySummary
        RepositorySummary repositorySummary = repository.getDefaultView().getRepositorySummary();
        assertNotNull(repositorySummary);
        assertEquals(repositorySummary.getArchiveCount(), 1);
        assertEquals(repositorySummary.getLastUpdated(), expectedUpdateTime);

        // advance the timestamp by 10 seconds and update
        expectedUpdateTime = expectedUpdateTime + 10000;
        Files.setLastModifiedTime(testArchiveJarFile, FileTime.fromMillis(expectedUpdateTime));
        jarScriptArchive = new JarScriptArchive.Builder(testArchiveJarFile).build();
        repository.insertArchive(jarScriptArchive);
        archiveUpdateTimes = repository.getDefaultView().getArchiveUpdateTimes();
        assertEquals(archiveUpdateTimes, Collections.singletonMap(testModuleId, expectedUpdateTime));

        // assert getScriptArchives
        scriptArchives = repository.getScriptArchives(archiveUpdateTimes.keySet());
        assertEquals(scriptArchives.size(), 1, scriptArchives.toString());
        scriptArchive = scriptArchives.iterator().next();
        assertEquals(scriptArchive.getModuleSpec().getModuleId(), testModuleId);
        assertEquals(scriptArchive.getCreateTime(), expectedUpdateTime);

        // assert getArchiveSummaries
        archiveSummaries = repository.getDefaultView().getArchiveSummaries();
        assertEquals(archiveSummaries.size(), 1);
        archiveSummary = archiveSummaries.get(0);
        assertEquals(archiveSummary.getModuleId(), testModuleId);
        assertEquals(archiveSummary.getLastUpdateTime(), expectedUpdateTime);

        // assert getRepositorySummary
        repositorySummary = repository.getDefaultView().getRepositorySummary();
        assertNotNull(repositorySummary);
        assertEquals(repositorySummary.getArchiveCount(), 1);
        assertEquals(repositorySummary.getLastUpdated(), expectedUpdateTime);

        // delete module
        repository.deleteArchive(testModuleId);
        archiveUpdateTimes = repository.getDefaultView().getArchiveUpdateTimes();
        assertTrue(archiveUpdateTimes.isEmpty(), archiveUpdateTimes.toString());
    }

    @Test
    public void testExternalizedModuleSpec() throws Exception {
        ArchiveRepository repository = createRepository();
        ModuleId testModuleId = TEST_MODULE_SPEC_JAR.getModuleId();
        ScriptModuleSpec expectedModuleSpec = new ScriptModuleSpec.Builder(testModuleId)
            .addMetadata("externalizedMetaDataName1", "externalizedMetaDataValue1")
            .build();
        JarScriptArchive jarScriptArchive = new JarScriptArchive.Builder(testArchiveJarFile)
            .setModuleSpec(expectedModuleSpec)
            .build();
        repository.insertArchive(jarScriptArchive);
        Set<ScriptArchive> scriptArchives = repository.getScriptArchives(Collections.singleton(testModuleId));
        assertEquals(scriptArchives.size(), 1, scriptArchives.toString());
        ScriptModuleSpec actualModuleSpec = scriptArchives.iterator().next().getModuleSpec();
        assertEquals(actualModuleSpec, expectedModuleSpec);
    }
}

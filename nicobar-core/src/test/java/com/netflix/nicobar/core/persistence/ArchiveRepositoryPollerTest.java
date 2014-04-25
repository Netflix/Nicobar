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
import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_TEXT_JAR;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleListener;
import com.netflix.nicobar.core.module.ScriptModuleLoader;
import com.netflix.nicobar.core.plugin.NoOpCompilerPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource;

/**
 * Base integration tests for {@link PathArchiveRepository}
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public abstract class ArchiveRepositoryPollerTest {
    private final static Logger logger = LoggerFactory.getLogger(ScriptModuleLoader.class);
    protected ArchiveRepository archiveRepository;
    protected ScriptModuleLoader moduleLoader;

    /**
     * Create the repository to plug in to the integration gets
     * @return
     */
    public abstract ArchiveRepository createArchiveRepository(Path rootArchiveDirectory);

    @BeforeClass
    public void classSetup() throws Exception {
        Path rootArchiveDirectory = Files.createTempDirectory(ArchiveRepositoryPollerTest.class.getSimpleName()+"_");
        logger.info("rootArchiveDirectory: {}", rootArchiveDirectory);
        FileUtils.forceDeleteOnExit(rootArchiveDirectory.toFile());

        archiveRepository = createArchiveRepository(rootArchiveDirectory);
        long now = System.currentTimeMillis();
        deployJarArchive(TEST_TEXT_JAR, now);
        deployJarArchive(TEST_MODULE_SPEC_JAR, now);
    }

    @BeforeMethod
    public void testSetup() throws Exception {
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder(NoOpCompilerPlugin.PLUGIN_ID)
            .withPluginClassName(NoOpCompilerPlugin.class.getName())
            .build();
        moduleLoader = new ScriptModuleLoader.Builder().addPluginSpec(pluginSpec).build();
    }

    /**
     * Simulate what a client might do at startup
     */
    @Test
    public void testInitialLoad() throws Exception {
        ArchiveRepositoryPoller poller = new ArchiveRepositoryPoller.Builder(moduleLoader).build();
        poller.addRepository(archiveRepository, 10, TimeUnit.SECONDS, true);
        Map<ModuleId, ScriptModule> scriptModules = moduleLoader.getAllScriptModules();

        assertEquals(scriptModules.keySet(), new HashSet<ModuleId>(Arrays.asList(TEST_TEXT_JAR.getModuleId(), TEST_MODULE_SPEC_JAR.getModuleId())));
        List<ArchiveSummary> archiveSummaries = archiveRepository.getDefaultView().getArchiveSummaries();
        for (ArchiveSummary archiveSummary : archiveSummaries) {
            ScriptModule scriptModule = moduleLoader.getScriptModule(archiveSummary.getModuleId());
            assertNotNull(scriptModule);
            assertEquals(scriptModule.getCreateTime(), archiveSummary.getLastUpdateTime());
        }
        poller.shutdown();
    }

    /**
     * Simulate what a client might do on an on-going bases
     */
    @Test
    public void testPolling() throws Exception {
        ScriptModuleListener mockListener = mock(ScriptModuleListener.class);
        moduleLoader.addListeners(Collections.singleton(mockListener));

        // initial startup phase
        ArchiveRepositoryPoller poller = new ArchiveRepositoryPoller.Builder(moduleLoader).build();
        poller.addRepository(archiveRepository, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, true);
        Map<ModuleId, Long> origUpdateTimes = archiveRepository.getDefaultView().getArchiveUpdateTimes();
        verify(mockListener, times(2)).moduleUpdated(any(ScriptModule.class), eq((ScriptModule)null));
        verifyNoMoreInteractions(mockListener);

        // poll for changes
        poller.pollRepository(archiveRepository);
        verifyNoMoreInteractions(mockListener);

        // touch a file to force a reload then poll. some filesystems only have 1 second granularity, so advance by at least that much
        long updateTime = origUpdateTimes.get(TEST_MODULE_SPEC_JAR.getModuleId()) + 1000;
        deployJarArchive(TEST_MODULE_SPEC_JAR, updateTime);
        poller.pollRepository(archiveRepository);
        verify(mockListener).moduleUpdated(any(ScriptModule.class), (ScriptModule)Mockito.notNull());
        verifyNoMoreInteractions(mockListener);

        // poll one more time to make sure the state has been reset properly
        poller.pollRepository(archiveRepository);
        verifyNoMoreInteractions(mockListener);
     }

    /**
     * Simulate a deletion
     */
    @Test(priority=Integer.MAX_VALUE) // run this last
    public void testDelete() throws Exception {
        ScriptModuleListener mockListener = mock(ScriptModuleListener.class);
        moduleLoader.addListeners(Collections.singleton(mockListener));

        // initial startup phase
        ArchiveRepositoryPoller poller = new ArchiveRepositoryPoller.Builder(moduleLoader).build();
        poller.addRepository(archiveRepository, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, true);
        verify(mockListener, times(2)).moduleUpdated(any(ScriptModule.class), eq((ScriptModule)null));
        verifyNoMoreInteractions(mockListener);

        // delete a module
        archiveRepository.deleteArchive(TEST_MODULE_SPEC_JAR.getModuleId());
        poller.pollRepository(archiveRepository);
        verify(mockListener).moduleUpdated(eq((ScriptModule)null), any(ScriptModule.class));
        verifyNoMoreInteractions(mockListener);


        // poll one more time to make sure the state has been reset properly
        poller.pollRepository(archiveRepository);
        verifyNoMoreInteractions(mockListener);

        // restore the module and reload
        reset(mockListener);
        deployJarArchive(TEST_MODULE_SPEC_JAR, System.currentTimeMillis());
        poller.pollRepository(archiveRepository);
        verify(mockListener).moduleUpdated(any(ScriptModule.class), eq((ScriptModule)null));
        verifyNoMoreInteractions(mockListener);
    }


    /**
     * inert the given archive resource to the test archive repository
     */
    protected void deployJarArchive(TestResource testResource, long updateTime) throws Exception {
        String testResourcePath = testResource.getResourcePath();
        URL archiveUrl = getClass().getClassLoader().getResource(testResourcePath);
        assertNotNull(archiveUrl, "couldn't find test resource with path " + testResourcePath);
        Path archiveJarPath = Paths.get(archiveUrl.toURI());
        JarScriptArchive jarScriptArchive = new JarScriptArchive.Builder(archiveJarPath)
            .setCreateTime(updateTime)
            .build();
        archiveRepository.insertArchive(jarScriptArchive);
    }
}

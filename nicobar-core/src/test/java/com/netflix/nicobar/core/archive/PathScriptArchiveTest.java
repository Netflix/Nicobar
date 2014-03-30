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

import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_MODULE_SPEC_PATH;
import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_TEXT_PATH;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.PathScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;


/**
 * Unit tests for {@link PathScriptArchive}
 *
 * @author James Kojo
 */
public class PathScriptArchiveTest {
    @Test
    public void testLoadTextPath() throws Exception {
        URL rootPathUrl = getClass().getClassLoader().getResource(TEST_TEXT_PATH.getResourcePath());
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();

        ModuleId moduleId = ModuleId.create("testModuleId");
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(rootPath)
            .setModuleSpec(new ScriptModuleSpec.Builder(moduleId).build())
            .build();
        assertEquals(scriptArchive.getModuleSpec().getModuleId(), moduleId);
        Set<String> archiveEntryNames = scriptArchive.getArchiveEntryNames();
        assertEquals(archiveEntryNames, TEST_TEXT_PATH.getContentPaths());
        for (String entryName : archiveEntryNames) {
            URL entryUrl = scriptArchive.getEntry(entryName);
            assertNotNull(entryUrl);
            InputStream inputStream = entryUrl.openStream();
            String content = IOUtils.toString(inputStream, Charsets.UTF_8);
            assertNotNull(content);
        }
    }

    @Test
    public void testDefaultModuleId() throws Exception {
        URL rootPathUrl = getClass().getClassLoader().getResource(TEST_TEXT_PATH.getResourcePath());
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(rootPath).build();
        assertEquals(scriptArchive.getModuleSpec().getModuleId(), TEST_TEXT_PATH.getModuleId());
    }

    @Test
    public void testLoadWithModuleSpec() throws Exception {
        URL rootPathUrl = getClass().getClassLoader().getResource(TEST_MODULE_SPEC_PATH.getResourcePath());
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();

        // if the module spec isn't provided, it should be discovered in the path
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(rootPath).build();
        ScriptModuleSpec moduleSpec = scriptArchive.getModuleSpec();
        assertEquals(moduleSpec.getModuleId(), TEST_MODULE_SPEC_PATH.getModuleId());
        assertEquals(moduleSpec.getModuleDependencies(), Collections.emptySet());
        Map<String, String> expectedMetadata = new HashMap<String, String>();
        expectedMetadata.put("metadataName1", "metadataValue1");
        expectedMetadata.put("metadataName2", "metadataValue2");
    }
}

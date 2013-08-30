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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;


/**
 * Unit tests for {@link PathScriptArchive}
 *
 * @author James Kojo
 */
public class PathScriptArchiveTest {
    private final static String TEXT_PATH_RESOURCE_NAME = "paths/test-text";
    private final static String MODULE_SPEC_PATH_RESOURCE_NAME = "paths/test-modulespec";
    @Test
    public void testLoadTextPath() throws Exception {
        URL rootPathUrl = getClass().getClassLoader().getResource(TEXT_PATH_RESOURCE_NAME);
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();

        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(rootPath)
            .setModuleSpec(new ScriptModuleSpec.Builder("testModuleId").build())
            .build();
        assertEquals(scriptArchive.getModuleSpec().getModuleId(), "testModuleId");
        Set<String> archiveEntryNames = scriptArchive.getArchiveEntryNames();
        assertEquals(archiveEntryNames, new HashSet<String>(Arrays.asList("sub1/sub1.txt", "sub2/sub2.txt", "root.txt", "META-INF/MANIFEST.MF")));
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
        URL rootPathUrl = getClass().getClassLoader().getResource(TEXT_PATH_RESOURCE_NAME);
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(rootPath).build();
        assertEquals(scriptArchive.getModuleSpec().getModuleId(), "test-text");
    }

    @Test
    public void testLoadWithModuleSpec() throws Exception {
        URL rootPathUrl = getClass().getClassLoader().getResource(MODULE_SPEC_PATH_RESOURCE_NAME);
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();

        // if the module spec isn't provided, it should be discovered in the path
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(rootPath).build();
        ScriptModuleSpec moduleSpec = scriptArchive.getModuleSpec();
        assertEquals(moduleSpec.getModuleId(), "test-modulespec-moduleId");
        assertEquals(moduleSpec.getDependencies(), new HashSet<String>(Arrays.asList("dependencyModuleId1", "dependencyModuleId2")));
        Map<String, String> expectedMetadata = new HashMap<String, String>();
        expectedMetadata.put("metadataName1", "metadataValue1");
        expectedMetadata.put("metadataName2", "metadataValue2");
    }
}

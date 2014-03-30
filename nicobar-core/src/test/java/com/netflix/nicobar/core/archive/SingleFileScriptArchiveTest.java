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

import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_SCRIPTS_PATH;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;


/**
 * Unit tests for {@link SingleFileScriptArchive}
 *
 */
public class SingleFileScriptArchiveTest {
    @Test
    public void testDefaultModuleSpec() throws Exception {
        URL rootPathUrl = getClass().getClassLoader().getResource(TEST_SCRIPTS_PATH.getResourcePath());
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();
        Set<String> singleFileScripts = TEST_SCRIPTS_PATH.getContentPaths();
        for (String script: singleFileScripts) {
            Path scriptPath = rootPath.resolve(script);
            SingleFileScriptArchive scriptArchive = new SingleFileScriptArchive.Builder(scriptPath)
                .build();
            String moduleId = script.replaceAll("\\.", "_");
            assertEquals(scriptArchive.getModuleSpec().getModuleId().toString(), moduleId);
            Set<String> archiveEntryNames = scriptArchive.getArchiveEntryNames();
            assertEquals(archiveEntryNames.size(), 1);
            for (String entryName : archiveEntryNames) {
                URL entryUrl = scriptArchive.getEntry(entryName);
                assertNotNull(entryUrl);
                InputStream inputStream = entryUrl.openStream();
                String content = IOUtils.toString(inputStream, Charsets.UTF_8);

                // We have stored the file name as the content of the file
                assertEquals(content, script + "\n");
            }
        }
    }

    @Test
    public void testWithModuleSpec() throws Exception {
        URL rootPathUrl = getClass().getClassLoader().getResource(TEST_SCRIPTS_PATH.getResourcePath());
        Path rootPath = Paths.get(rootPathUrl.toURI()).toAbsolutePath();
        Set<String> singleFileScripts = TEST_SCRIPTS_PATH.getContentPaths();
        ModuleId moduleId = ModuleId.create("testModuleId");
        for (String script: singleFileScripts) {
            Path scriptPath = rootPath.resolve(script);
            SingleFileScriptArchive scriptArchive = new SingleFileScriptArchive.Builder(scriptPath)
                .setModuleSpec(new ScriptModuleSpec.Builder(moduleId).build())
                .build();
            assertEquals(scriptArchive.getModuleSpec().getModuleId(), moduleId);
            // We just need to test one script in the set
            break;
        }
    }
}

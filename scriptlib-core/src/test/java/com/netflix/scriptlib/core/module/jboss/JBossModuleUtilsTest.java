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
package com.netflix.scriptlib.core.module.jboss;

import static com.netflix.scriptlib.core.testutil.CoreTestResourceUtil.TestResource.TEST_TEXT_JAR;
import static com.netflix.scriptlib.core.testutil.CoreTestResourceUtil.TestResource.TEST_TEXT_PATH;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.testng.TestNG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.netflix.scriptlib.core.archive.JarScriptArchive;
import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptModuleSpec;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPlugin;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.scriptlib.core.testutil.CoreTestResourceUtil;


/**
 * Unit tests for {@link JBossModuleUtils}
 *
 * @author James Kojo
 */
public class JBossModuleUtilsTest {
    private static final String METADATA_NAME = "TestMetadataName";
    private static final String METADATA_VALUE = "TestMetadataValue";
    @BeforeClass
    public void setup() {
        //Module.setModuleLogger(new StreamModuleLogger(System.err));
    }

    /**
     * Verify that the module creates the expected set of dependencies for a {@link ScriptCompilerPlugin}
     */
    @Test
    public void testExpectedPluginDependencies() throws Exception {
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder("TestPlugin")
            .addMetatdata(METADATA_NAME, METADATA_VALUE)
            .build();
        ModuleIdentifier pluginId = JBossModuleUtils.getModuleId(pluginSpec);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(pluginId);
        JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, pluginSpec);

        JBossModuleLoader moduleLoader = new JBossModuleLoader();
        moduleLoader.addModuleSpec(moduleSpecBuilder.create());
        Module module = moduleLoader.loadModule(pluginId);
        assertNotNull(module);
        ModuleClassLoader moduleClassLoader = module.getClassLoader();

        // verify the metadata was transfered
        assertEquals(module.getProperty(METADATA_NAME), METADATA_VALUE);

        // verify the module can import the core classes
        assertNotNull(moduleClassLoader.loadClass(ScriptCompilerPlugin.class.getName()));

        // verify the module can find the JDK classes
        assertNotNull(moduleClassLoader.loadClass("org.w3c.dom.Element"));

        // verify that nothing else from the classpath leaked through
        assertClassNotFound(TestNG.class.getName(), moduleClassLoader);
    }

    /**
     * Verify that the module creates the expected set of dependencies for a {@link JarScriptArchive}
     */
    @Test
    public void testJarResources() throws Exception {
        Path jarPath = CoreTestResourceUtil.getResourceAsPath(TEST_TEXT_JAR);
        ScriptArchive jarScriptArchive = new JarScriptArchive.Builder(jarPath)
                .setModuleSpec(new ScriptModuleSpec.Builder("testModuleId")
                .addMetadata(METADATA_NAME, METADATA_VALUE)
                .build())
            .build();

        ModuleIdentifier revisionId = JBossModuleUtils.createRevisionId(TEST_TEXT_JAR.getModuleId(), 1);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(revisionId);
        JBossModuleLoader moduleLoader = new JBossModuleLoader();
        JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, jarScriptArchive, moduleLoader.getLatestRevisionIds());

        moduleLoader.addModuleSpec(moduleSpecBuilder.create());
        Module module = moduleLoader.loadModule(revisionId);
        ModuleClassLoader moduleClassLoader = module.getClassLoader();

        // verify the metadata was transfered
        assertEquals(module.getProperty(METADATA_NAME), METADATA_VALUE);
        // verify that the archive resource match exactly the module resources
        Set<String> actualPaths = getResourcePaths(moduleClassLoader);

        assertEquals(actualPaths, TEST_TEXT_JAR.getContentPaths());
    }

    /**
     * Verify that the module creates the expected set of dependencies for a {@link PathScriptArchive}
     */
    @Test
    public void testPathResources() throws Exception {
        Path jarPath = CoreTestResourceUtil.getResourceAsPath(TEST_TEXT_PATH);
        ScriptArchive jarScriptArchive = new PathScriptArchive.Builder(jarPath)
            .setModuleSpec(new ScriptModuleSpec.Builder("testModuleId")
                .addMetadata(METADATA_NAME, METADATA_VALUE)
                .build())
            .build();
        ModuleIdentifier revisionId = JBossModuleUtils.createRevisionId(TEST_TEXT_PATH.getModuleId(), 1);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(revisionId);
        JBossModuleLoader moduleLoader = new JBossModuleLoader();
        JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, jarScriptArchive, moduleLoader.getLatestRevisionIds());
        moduleLoader.addModuleSpec(moduleSpecBuilder.create());

        Module module = moduleLoader.loadModule(revisionId);
        ModuleClassLoader moduleClassLoader = module.getClassLoader();

        // verify the metadata was transfered
        assertEquals(module.getProperty(METADATA_NAME), METADATA_VALUE);
        // verify that the archive resource match exactly the module resources
        Set<String> actualPaths = getResourcePaths(moduleClassLoader);

        assertEquals(actualPaths, TEST_TEXT_PATH.getContentPaths());
    }

    private void assertClassNotFound(String className, ModuleClassLoader moduleClassLoader) {
        Class<?> foundClass;
        try {
            foundClass = moduleClassLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            foundClass = null;
        }
        assertNull(foundClass);
    }

    private Set<String> getResourcePaths(ModuleClassLoader moduleClassLoader) {
        Set<String> result = new HashSet<String>();
        Iterator<Resource> resources = moduleClassLoader.iterateResources("", true);
        while (resources.hasNext()) {
            Resource resource = resources.next();
            result.add(resource.getName());
        }
        return result;
    }
}

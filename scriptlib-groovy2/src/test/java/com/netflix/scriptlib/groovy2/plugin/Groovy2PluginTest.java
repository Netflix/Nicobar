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
package com.netflix.scriptlib.groovy2.plugin;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptModuleSpec;
import com.netflix.scriptlib.core.compile.ScriptCompiler.MetadataName;
import com.netflix.scriptlib.core.module.ScriptModule;
import com.netflix.scriptlib.core.module.ScriptModuleLoader;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.scriptlib.groovy2.testutil.GroovyTestResourceUtil;
import com.netflix.scriptlib.groovy2.testutil.GroovyTestResourceUtil.TestScript;

/**
 * Integration tests for the Groovy2 language plugin
 *
 * Future tests:
 *  * test can't delete plugin spec
 *
 * @author James Kojo
 */
public class Groovy2PluginTest {

    private static final String GROOVY2_COMPILER_PLUGIN = Groovy2CompilerPlugin.class.getName();

    @BeforeClass
    public void setup() {
        //Module.setModuleLogger(new StreamModuleLogger(System.err));
    }

    @Test
    public void testLoadSimpleScript() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader();
        // create a new script archive consisting of HellowWorld.groovy and add it the loader.
        // Declares a dependency on the Groovy2RuntimeModule.
        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.HELLO_WORLD);
        ScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.HELLO_WORLD.getScriptPath())
            .setModuleSpec(new ScriptModuleSpec.Builder(TestScript.HELLO_WORLD.getModuleId())
                .addDependency("Groovy2RuntimeModule")
                .addMetadata(MetadataName.SCRIPT_LANGUAGE.name(), "groovy2")
                .build())
            .build();
        Set<ScriptModule> scriptModules = moduleLoader.addScriptArchives(Collections.singleton(scriptArchive));
        assertNotNull(CollectionUtils.isNotEmpty(scriptModules));

        // locate the class file in the module and execute it
        Class<?> clazz = findClassByName(scriptModules, TestScript.HELLO_WORLD.getClassName());
        assertGetMessage(clazz, "Hello, World!");
    }

    /**
     * Test loading/executing a script which has a dependency on a library
     */
    @Test
    public void testLoadScriptWithLibrary() throws Exception {
        ScriptModuleLoader moduleLoader = createGroovyModuleLoader();
        Path dependsOnARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.DEPENDS_ON_A);

        ScriptArchive dependsOnAArchive = new PathScriptArchive.Builder(dependsOnARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.DEPENDS_ON_A.getScriptPath())
            .setModuleSpec(new ScriptModuleSpec.Builder(TestScript.DEPENDS_ON_A.getModuleId())
                .addDependency("Groovy2RuntimeModule")
                .addDependency(TestScript.LIBRARY_A.getModuleId())
                .addMetadata(MetadataName.SCRIPT_LANGUAGE.name(), "groovy2")
                .build())
            .build();
        Path libARootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.LIBRARY_A);
        ScriptArchive libAArchive = new PathScriptArchive.Builder(libARootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.LIBRARY_A.getScriptPath())
            .setModuleSpec(new ScriptModuleSpec.Builder(TestScript.LIBRARY_A.getModuleId())
                .addDependency("Groovy2RuntimeModule")
                .addMetadata(MetadataName.SCRIPT_LANGUAGE.name(), "groovy2")
                .build())
            .build();
        // load them in dependency order to make sure that transitive dependency resolution is working
        Set<ScriptModule> scriptModules = moduleLoader.addScriptArchives(new LinkedHashSet<ScriptArchive>(Arrays.asList(dependsOnAArchive, libAArchive)));
        assertTrue(CollectionUtils.isNotEmpty(scriptModules));

        // locate the class file in the module and execute it
        Class<?> clazz = findClassByName(scriptModules, TestScript.DEPENDS_ON_A.getClassName());
        assertGetMessage(clazz, "DepondOnA: Called LibraryA and got message:'I'm LibraryA!'");
    }

    /**
     * Create a module loader this is wired up with the groovy compiler plugin
     */
    private ScriptModuleLoader createGroovyModuleLoader() throws Exception {
        // create the groovy plugin spec. this plugin specified a new module and classloader called "Groovy2Runtime"
        // which contains the groovy-all-2.1.6.jar and the scriptlib-groovy2 project.
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder("Groovy2RuntimeModule", 1)
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyRuntime())
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyPluginLocation())
            // hack to make the gradle build work. still doesn't seem to properly instrument the code
            // should probably add a classloader dependency on the system classloader instead
            .addRuntimeResource(GroovyTestResourceUtil.getCoberturaJar(getClass().getClassLoader()))
            .withPluginClassName(GROOVY2_COMPILER_PLUGIN)
            .build();

        // create and start the loader with the plugin
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader(Collections.singleton(pluginSpec));
        return moduleLoader;

    }

    private Class<?> findClassByName(Set<ScriptModule> scriptModules, String className) {
        for (ScriptModule scriptModule : scriptModules) {
            Set<Class<?>> classes = scriptModule.getLoadedClasses();
            for (Class<?> clazz : classes) {
                if (clazz.getName().equals(className)) {
                    return clazz;
                }
            }
        }
        fail("couldn't find class " + className);
        return null;
    }

    private void assertGetMessage(Class<?> targetClass, String expectedMessage) throws Exception {
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, expectedMessage);
    }
}

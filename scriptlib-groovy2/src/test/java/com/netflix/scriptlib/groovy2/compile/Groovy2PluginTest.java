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
package com.netflix.scriptlib.groovy2.compile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoader;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptCompiler.MetadataName;
import com.netflix.scriptlib.core.module.ScriptModule;
import com.netflix.scriptlib.core.module.ScriptModuleLoader;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.scriptlib.groovy2.testutil.GroovyTestResourceUtil;
import com.netflix.scriptlib.groovy2.testutil.GroovyTestResourceUtil.TestScript;

/**
 * Integration tests for the Groovy2 language plugin
 *
 * @author James Kojo
 */
public class Groovy2PluginTest {

    /**  */
    private static final String GROOVY2_COMPILER_PROVIDER = "com.netflix.scriptlib.groovy2.plugin.Groovy2CompilerProvider";

    @BeforeClass
    public void setup() {
        //Module.setModuleLogger(new StreamModuleLogger(System.err));
    }


    @Test
    public void testLoadSimpleScript() throws Exception {
        // create the groovy plugin spec. this plugin specified a new module and classloader called "Groovy2Runtime"
        // which contains the groovy-all-2.1.6.jar and the scriptlib-groovy2 project.
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder("Groovy2RuntimeModule")
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyRuntime())
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyPluginLocation())
            .withCompilerProviderClassName(GROOVY2_COMPILER_PROVIDER)
            .build();

        // create and start the loader with the plugin
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader(Collections.singleton(pluginSpec));
        moduleLoader.start();

        // create a new script archive and add it the loader. Contains all of the scripts in
        // resources/scripts/. Declares a dependency on the Groovy2RuntimeModule.
        Path scriptFullPath = GroovyTestResourceUtil.getScriptAsPath(TestScript.HELLO_WORLD);
        ScriptArchive scriptArchive = new PathScriptArchive.Builder("TestScripts", 1, scriptFullPath.getParent().toAbsolutePath())
            .addDependency("Groovy2RuntimeModule")
            .addMetadata(MetadataName.COMPILER_NAME.name(), "groovy2")
            .build();
        Set<ScriptModule> scriptModules = moduleLoader.addScriptArchives(Collections.singleton(scriptArchive));
        assertNotNull(CollectionUtils.isNotEmpty(scriptModules));

        // locate the class file in the module and execute it
        ScriptModule scriptModule = scriptModules.iterator().next();
        Set<Class<?>> loadedClasses = scriptModule.getLoadedClasses();
        assertTrue(CollectionUtils.isNotEmpty(loadedClasses));

        for (Class<?> loadedClass : loadedClasses) {
            if (loadedClass.getName().equals(TestScript.HELLO_WORLD.getClassName())) {
                Object instance = loadedClass.newInstance();
                Method method = loadedClass.getMethod("getMessage");
                String message = (String)method.invoke(instance);
                assertEquals(message, "Hello, World!");
                return;
            }

        }
        fail("Couldn't find executable class");
    }

    protected static class TestModuleLoader extends ModuleLoader {
        protected TestModuleLoader(ModuleFinder finder) {
            super(new ModuleFinder[] {finder});
        }
    }
}

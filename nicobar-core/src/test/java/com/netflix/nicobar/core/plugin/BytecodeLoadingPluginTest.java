/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.nicobar.core.plugin;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleLoader;
import com.netflix.nicobar.core.module.ScriptModuleUtils;
import com.netflix.nicobar.core.utils.ClassPathUtils;

/**
 * Tests for the Bytecode language plugin.
 * @author Vasanth Asokan
 */
public class BytecodeLoadingPluginTest {

    private ScriptModuleLoader moduleLoader;

    @BeforeTest
    public void setup() throws Exception {
        ScriptCompilerPluginSpec pluginSpec = getCompilerSpec();

        // Create a set of app packages to allow access by the compilers, as well as the scripts.
        // We take a hammer approach and just exclude all com/netflix from the set of app packages exposed.
        Set<String> excludes = new HashSet<String>();
        Collections.addAll(excludes, "com/netflix");
        Set<String> pathSet = ClassPathUtils.scanClassPathWithExcludes(System.getProperty("java.class.path"),
                Collections.<String> emptySet(),
                excludes);

        moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(pluginSpec)
            // TODO: The BytecodeLoadingPlugin seems to work without the app package filter
            // that we add below. This is likely something in our system, allowing packages to leak.
            // Need to follow up and see how IOUtils resolves correctly in the BytecodeLoadingPlugin
            // without an app package filter as below.
            .addAppPackages(pathSet)
            .build();
    }

    /**
     * Test a plain archive with no dependencies.
     * @throws Exception
     */
    @Test
    public void testHelloHelperJar() throws Exception {
        URL jarPath = getClass().getClassLoader().getResource("testmodules/hellohelper.jar");

        JarScriptArchive jarArchive = new JarScriptArchive.Builder(Paths.get(jarPath.getFile()))
            .build();
        moduleLoader.updateScriptArchives(Collections.singleton((ScriptArchive)jarArchive));
        ScriptModule module = moduleLoader.getScriptModule(ModuleId.create("hellohelper"));
        assertNotNull(module);

        Class<?> targetClass = ScriptModuleUtils.findClass(module, "com.netflix.nicobar.test.HelloHelper");
        assertNotNull(targetClass);
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("execute");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello Nicobar World!");
    }

    /**
     * Test an archive with module dependencies.
     * @throws Exception
     */
    @Test
    public void testHelloworldWithDeps() throws Exception {
        URL jarPath = getClass().getClassLoader().getResource("testmodules/helloworld.jar");
        JarScriptArchive jarArchive = new JarScriptArchive.Builder(Paths.get(jarPath.getFile()))
            .build();
        URL depJarPath = getClass().getClassLoader().getResource("testmodules/hellohelper.jar");
        JarScriptArchive depArchive = new JarScriptArchive.Builder(Paths.get(depJarPath.getFile()))
            .build();
        Set<ScriptArchive> archives = new HashSet<ScriptArchive>();
        Collections.<ScriptArchive>addAll(archives, depArchive, jarArchive);
        moduleLoader.updateScriptArchives(archives);
        ScriptModule module = moduleLoader.getScriptModule(ModuleId.create("helloworld"));
        assertNotNull(module);

        Class<?> targetClass = ScriptModuleUtils.findClass(module, "com.netflix.nicobar.test.Helloworld");
        assertNotNull(targetClass);
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("execute");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello Nicobar World!");
    }

    private ScriptCompilerPluginSpec getCompilerSpec() {
        // Create a compiler spec for the bytecode loading plugin
        ScriptCompilerPluginSpec compilerSpec = new ScriptCompilerPluginSpec.Builder(BytecodeLoadingPlugin.PLUGIN_ID)
            .withPluginClassName(BytecodeLoadingPlugin.class.getName())
            .build();

        return compilerSpec;
    }
}
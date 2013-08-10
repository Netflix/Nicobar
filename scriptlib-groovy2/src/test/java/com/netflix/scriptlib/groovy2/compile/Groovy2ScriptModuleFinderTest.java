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
import static org.testng.Assert.fail;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.testng.annotations.Test;

import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.groovy2.module.Groovy2ScriptModuleFinder;

/**
 * Tests for {@link Groovy2ScriptModuleFinder}
 *
 * @author James Kojo
 */
public class Groovy2ScriptModuleFinderTest {
    private static final Path SCRIPTS_RESOURCE_PATH = Paths.get("scripts/");
    private static final Path HELLO_WORLD_RESOURCE_PATH = SCRIPTS_RESOURCE_PATH.resolve("helloworld/HelloWorld.groovy");

    @Test
    public void testLoadSimpleScript() throws Exception {
        URL scriptUrl = getClass().getClassLoader().getResource(SCRIPTS_RESOURCE_PATH.toString());
        if (scriptUrl == null) {
            fail("couldn't load resource " + SCRIPTS_RESOURCE_PATH);
        }
        Path scriptFullPath = Paths.get(scriptUrl.toURI());

        PathScriptArchive scriptArchive = new PathScriptArchive.Builder("HelloWorld", 1,
            scriptFullPath.getParent().toAbsolutePath()).build();

        Groovy2ScriptModuleFinder moduleFinder = new Groovy2ScriptModuleFinder();
        moduleFinder.addToRepository(scriptArchive);
        TestModuleLoader loader = new TestModuleLoader(moduleFinder);
        Module module = loader.loadModule(ModuleIdentifier.create("HelloWorld"));
        Class<?> loadedClass = module.getClassLoader().loadClass(pathToClassName(HELLO_WORLD_RESOURCE_PATH.toString()));
        assertNotNull(loadedClass);
        Object instance = loadedClass.newInstance();
        Method method = loadedClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello, World!");
    }

    /**
     *
     * @param filePath  path to the script file relative to the source root
     * @return
     */
    private static String pathToClassName(String filePath) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        Path directoryPath = path.getParent();
        StringBuilder sb = new StringBuilder();
        if (directoryPath != null) {
            for (Path subPath : directoryPath) {
                sb.append(subPath).append(".");
            }
        }
        int endIndex = fileName.lastIndexOf(".");
        endIndex = endIndex < 0 ? fileName.length() : endIndex;
        sb.append(fileName.substring(0, endIndex));
        return sb.toString();

    }
    protected static class TestModuleLoader extends ModuleLoader {
        protected TestModuleLoader(ModuleFinder finder) {
            super(new ModuleFinder[] {finder});
        }
    }
}

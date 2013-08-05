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

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.testng.annotations.Test;

import com.netflix.scriptlib.core.compile.ScriptArchive;
import com.netflix.scriptlib.groovy2.module.Groovy2ScriptModuleFinder;

/**
 * Tests for {@link Groovy2ScriptModuleFinder}
 *
 * @author James Kojo
 */
public class Groovy2ScriptModuleFinderTest {

    @Test
    public void testLoadSimpleScript() throws Exception {
        URI scriptUri = getClass().getClassLoader().getResource("scripts/HelloWorld.groovy").toURI();
        Path scriptFullPath = Paths.get(scriptUri);
        Path scriptRoot = scriptFullPath.getParent();
        Path scriptPath = scriptFullPath.getFileName();
        ScriptArchive scriptArchive = new ScriptArchive("HelloWorld", 1, scriptRoot, Collections.singletonList(scriptPath), null, null);

        Groovy2ScriptModuleFinder moduleFinder = new Groovy2ScriptModuleFinder();
        moduleFinder.addToRepository(scriptArchive);
        TestModuleLoader loader = new TestModuleLoader(moduleFinder);
        Module module = loader.loadModule(ModuleIdentifier.create("HelloWorld"));
        Class<?> loadedClass = module.getClassLoader().loadClass("HelloWorld");
        assertNotNull(loadedClass);
        Object instance = loadedClass.newInstance();
        Method method = loadedClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello, World!");
    }

    protected static class TestModuleLoader extends ModuleLoader {
        protected TestModuleLoader(ModuleFinder finder) {
            super(new ModuleFinder[] {finder});
        }
    }
}

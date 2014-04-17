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
package com.netflix.nicobar.groovy2.compile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.codehaus.groovy.tools.GroovyClass;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.PathScriptArchive;
import com.netflix.nicobar.groovy2.internal.compile.Groovy2CompilerHelper;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil.TestScript;

/**
 * Unit Tests for {@link Groovy2CompilerHelper}
 *
 * @author James Kojo
 */
public class Groovy2CompilerHelperTest {

    /**
     * Compile using current classloader, and no dependencies
     * @throws Exception
     */
    @Test
    public void testSimpleCompile() throws Exception {
        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.HELLO_WORLD);
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.HELLO_WORLD.getScriptPath())
            .build();

        Set<GroovyClass> compiledClasses = new Groovy2CompilerHelper(Files.createTempDirectory("Groovy2CompilerHelperTest"))
            .addScriptArchive(scriptArchive)
            .compile();

        assertFalse(CollectionUtils.isEmpty(compiledClasses));

        TestByteLoadingClassLoader testClassLoader = new TestByteLoadingClassLoader(getClass().getClassLoader(), compiledClasses);
        Class<?> loadedClass = testClassLoader.loadClass(TestScript.HELLO_WORLD.getClassName());
        assertNotNull(loadedClass);
        Object instance = loadedClass.newInstance();
        Method method = loadedClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello, World!");
    }

    /**
     * Compile a script with a package name using current classloader, and no dependencies
     */
    @Test
    public void testCompileWithPackage() throws Exception {
        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.HELLO_PACKAGE);
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.HELLO_PACKAGE.getScriptPath())
            .build();

        Set<GroovyClass> compiledClasses = new Groovy2CompilerHelper(Files.createTempDirectory("Groovy2CompilerHelperTest"))
            .addScriptArchive(scriptArchive)
            .compile();

        assertFalse(CollectionUtils.isEmpty(compiledClasses));

        TestByteLoadingClassLoader testClassLoader = new TestByteLoadingClassLoader(getClass().getClassLoader(), compiledClasses);
        Class<?> loadedClass = testClassLoader.loadClass(TestScript.HELLO_PACKAGE.getClassName());
        assertNotNull(loadedClass);
        Object instance = loadedClass.newInstance();
        Method method = loadedClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello, Package!");
    }

    /**
     * Test class loader that can load bytes provided by the groovy compiler
     */
    public static class TestByteLoadingClassLoader extends ClassLoader {
        private final Map<String, byte[]> classBytes;

        public TestByteLoadingClassLoader(ClassLoader parentClassLoader, Set<GroovyClass> groovyClasses) {
            super(parentClassLoader);
            this.classBytes = new HashMap<String, byte[]>();
            for (GroovyClass groovyClass : groovyClasses) {
                classBytes.put(groovyClass.getName(), groovyClass.getBytes());
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException("Couldn't find " + name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}

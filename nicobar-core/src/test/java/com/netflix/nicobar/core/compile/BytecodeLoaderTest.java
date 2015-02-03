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
package com.netflix.nicobar.core.compile;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.internal.compile.BytecodeLoader;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;

/**
 * Tests for the BytecodeLoader.
 * @author Vasanth Asokan
 */
public class BytecodeLoaderTest {
    @SuppressWarnings("rawtypes")
    private Class compiledClass;

    @BeforeMethod
    public void setup() {
        // Randomly assign some class
        compiledClass = this.getClass();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHelloworldArchive() throws Exception {
        URL jarPath = getClass().getClassLoader().getResource("testmodules/testmodule.jar");
        JarScriptArchive scriptArchive = new JarScriptArchive.Builder(Paths.get(jarPath.getFile()))
            .build();
        JBossModuleClassLoader moduleClassLoader = mock(JBossModuleClassLoader.class);
        BytecodeLoader loader = new BytecodeLoader();

        when(moduleClassLoader.loadClassLocal(Mockito.anyString(), anyBoolean())).thenReturn(compiledClass);
        Set<Class<?>> compiledClasses = loader.compile(scriptArchive, moduleClassLoader, Files.createTempDirectory("BytecodeLoaderTest"));

        verify(moduleClassLoader).addClasses(compiledClasses);
        assertEquals(compiledClasses.size(), 1);
        Iterator<Class<?>> iterator = compiledClasses.iterator();
        assertEquals(compiledClass, iterator.next());
    }
}

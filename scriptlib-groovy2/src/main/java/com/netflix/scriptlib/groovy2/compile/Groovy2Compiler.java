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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.codehaus.groovy.tools.GroovyClass;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptArchiveCompiler;
import com.netflix.scriptlib.core.compile.ScriptCompilationException;
import com.netflix.scriptlib.core.module.jboss.JBossModuleClassLoader;

/**
 * Groovy specific implementation of the {@link ScriptArchiveCompiler}
 *
 * @author James Kojo
 */
public class Groovy2Compiler implements ScriptArchiveCompiler {
    public final static String GROOVY2_COMPILER_ID = "groovy2";
    @Override
    public boolean shouldCompile(ScriptArchive archive) {
       return archive.getModuleSpec().getCompilerDependencies().contains(GROOVY2_COMPILER_ID);
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader)
        throws ScriptCompilationException, IOException {
         Set<GroovyClass> groovyClasses = new Groovy2CompilerHelper()
            .addScriptArchive(archive)
            .withParentClassloader(moduleClassLoader)
            .compile();
         HashSet<Class<?>> addedClasses = new HashSet<Class<?>>(archive.getArchiveEntryNames().size());
         for (GroovyClass groovyClass : groovyClasses) {
            Class<?> addedClass = moduleClassLoader.addClassBytes(groovyClass.getName(), groovyClass.getBytes());
            addedClasses.add(addedClass);
        }
        return Collections.unmodifiableSet(addedClasses);
    }
}

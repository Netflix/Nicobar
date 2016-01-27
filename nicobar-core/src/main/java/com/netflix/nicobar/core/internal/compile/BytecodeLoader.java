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
package com.netflix.nicobar.core.internal.compile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;

/**
 * A {@link ScriptArchiveCompiler} that loads java bytecode from .class files in a {@link ScriptArchive}.
 *
 * @author Vasanth Asokan
 */
public class BytecodeLoader implements ScriptArchiveCompiler {

    /**
     * Compile (load from) an archive, if it contains any .class files.
     */
    @Override
    public boolean shouldCompile(ScriptArchive archive) {

        Set<String> entries = archive.getArchiveEntryNames();
        boolean shouldCompile = false;
        for (String entry: entries) {
            if (entry.endsWith(".class")) {
                shouldCompile = true;
            }
        }

        return shouldCompile;
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader, Path targetDir)
            throws ScriptCompilationException, IOException {
        HashSet<Class<?>> addedClasses = new HashSet<Class<?>>(archive.getArchiveEntryNames().size());
        for (String entry : archive.getArchiveEntryNames()) {
            if (!entry.endsWith(".class")) {
                continue;
            }
            // Load from the underlying archive class resource
            String entryName = entry.replace(".class", "").replace("/", ".");
            try {
                Class<?> addedClass = moduleClassLoader.loadClassLocal(entryName, true);
                addedClasses.add(addedClass);
            } catch (Exception e) {
                throw new ScriptCompilationException("Unable to load class: " + entryName, e);
            }
        }
        moduleClassLoader.addClasses(addedClasses);

        return Collections.unmodifiableSet(addedClasses);
    }
}

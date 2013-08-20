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

import groovy.lang.GroovyClassLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptCompilationException;

/**
 * Helper class for compiling Groovy files into classes. This class takes as it's input a collection
 * of {@link ScriptArchive}s and outputs a {@link GroovyClassLoader} with the classes pre-loaded into it.
 *
 * If a parent {@link ClassLoader} is not provided, the current thread context classloader is used.
 *
 * @author James Kojo
 */
public class Groovy2CompilerHelper {
    private final List<Path> sourceFiles = new LinkedList<Path>();
    private final List<ScriptArchive> scriptArchives = new LinkedList<ScriptArchive>();
    private ClassLoader parentClassLoader;
    private CompilerConfiguration compileConfig;

    public Groovy2CompilerHelper() {
    }

    public Groovy2CompilerHelper withParentClassloader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
        return this;
    }

    public Groovy2CompilerHelper addSourceFile(Path groovyFile) {
        if (groovyFile != null) {
            sourceFiles.add(groovyFile);
        }
        return this;
    }

    public Groovy2CompilerHelper addScriptArchive(ScriptArchive archive) {
        if (archive != null) {
            scriptArchives.add(archive);
        }
        return this;
    }

    public Groovy2CompilerHelper withConfiguration(CompilerConfiguration compilerConfig) {
        if (compilerConfig != null) {
            this.compileConfig = compilerConfig;
        }
        return this;
    }

    /**
     * Compile the given source and load the resultant classes into a new {@link ClassNotFoundException}
     * @return initialized and laoded classes
     * @throws ScriptCompilationException
     */
    @SuppressWarnings("unchecked")
    public Set<GroovyClass> compile() throws ScriptCompilationException {
        final CompilerConfiguration conf = compileConfig != null ? compileConfig: CompilerConfiguration.DEFAULT;
        conf.setTolerance(0);
        conf.setVerbose(true);
        final ClassLoader buildParentClassloader = parentClassLoader != null ?
            parentClassLoader : Thread.currentThread().getContextClassLoader();
        GroovyClassLoader groovyClassLoader = AccessController.doPrivileged(new PrivilegedAction<GroovyClassLoader>() {
            public GroovyClassLoader run() {
                return new GroovyClassLoader(buildParentClassloader, conf, false);
            }
        });

        CompilationUnit unit = new CompilationUnit(conf, null, groovyClassLoader);
        Set<String> scriptExtensions = conf.getScriptExtensions();
        try {
            for (ScriptArchive scriptArchive : scriptArchives) {
                // gives the resultant classloader access to the resource files in the archive
                groovyClassLoader.addURL(scriptArchive.getRootUrl());
                Set<String> entryNames = scriptArchive.getArchiveEntryNames();
                for (String entryName : entryNames) {
                    for (String extension : scriptExtensions) {
                        if (entryName.endsWith(extension)) {
                            // identified groovy file
                            unit.addSource(scriptArchive.getEntry(entryName));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new ScriptCompilationException("Exception loading source files", e);
        }
        for (Path sourceFile : sourceFiles) {
            unit.addSource(sourceFile.toFile());
        }
        try {
            unit.compile(Phases.CLASS_GENERATION);
        } catch (CompilationFailedException e) {
           throw new ScriptCompilationException("Exception during script compilation", e);
        }
        return new HashSet<GroovyClass>(unit.getClasses());
    }
}

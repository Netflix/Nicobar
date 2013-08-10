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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptCompilationException;

/**
 * Helper class for compiling Groovy files into classes. This class takes as it's input a collection
 * of groovy source files and related resources represented as {@link Path}s. It outputs compiled
 * classes as well as copies of the resource files into a given output directory.
 *
 * @author James Kojo
 */
public class Groovy2CompilerHelper {
    private final static String GROOVY_FILE_SUFFIX = ".groovy";

    private final Path baseOutputDir;
    private final List<Path> sourceFiles = new LinkedList<Path>();
    private final List<Path> resourceFiles = new LinkedList<Path>();
    private ClassLoader compileClassLoader;
    private final List<CompilationCustomizer> customizers = new LinkedList<CompilationCustomizer>();

    public Groovy2CompilerHelper(Path baseOutputDir) {
        this.baseOutputDir = Objects.requireNonNull(baseOutputDir, "baseOutputDir");
    }

    public Groovy2CompilerHelper withCompileClassloader(ClassLoader compileClassLoader) {
        this.compileClassLoader = compileClassLoader;
        return this;
    }

    public Groovy2CompilerHelper addSourceFile(Path groovyFile) {
        if (groovyFile != null) {
            sourceFiles.add(groovyFile);
        }
        return this;
    }

    /**
     * Resource files are non-groovy files that are copied directly to the output path
     */
    public Groovy2CompilerHelper addResourceFile(Path resourceFile) {
        if (resourceFile != null) {
            resourceFiles.add(resourceFile);
        }
        return this;
    }

    public Groovy2CompilerHelper addScriptArchive(ScriptArchive archive) {
        if (archive != null) {
            for (Path file : archive.getFiles()) {
                if (file.toFile().getName().endsWith(GROOVY_FILE_SUFFIX)) {
                    addSourceFile(file);
                } else {
                    addResourceFile(file);
                }
            }
        }
        return this;
    }

    public Groovy2CompilerHelper addCustomizer(CompilationCustomizer customizer) {
        if (customizer != null) {
            customizers.add(customizer);
        }
        return this;
    }

    public void compile() throws IOException, ScriptCompilationException {
        // Copy resource files into base directory
        for (Path resourceFile : resourceFiles) {
            // convert the entry path to a relative path if necessary in order to join to the base output path
            if (resourceFile.isAbsolute()) {
                resourceFile = resourceFile.subpath(0, resourceFile.getNameCount());
            }
            Path resourceFileCopy = baseOutputDir.resolve(resourceFile);
            Files.copy(resourceFile, resourceFileCopy);
        }

        GroovyClassLoader loader;
        if (compileClassLoader != null) {
            loader = new GroovyClassLoader(compileClassLoader);
        } else {
            loader = new GroovyClassLoader();
        }
        CompilerConfiguration conf = new CompilerConfiguration();

        if (!customizers.isEmpty()) {
            conf.addCompilationCustomizers(customizers.toArray(new CompilationCustomizer[customizers.size()]));
        }
        conf.setTargetDirectory(baseOutputDir.toFile());
        conf.setTolerance(0);
        conf.setVerbose(true);

        CompilationUnit unit = new CompilationUnit(conf, null, loader);
        for (Path sourceFile : sourceFiles) {
            unit.addSource(sourceFile.toFile());
        }
        try {
            unit.compile();
        } catch (CompilationFailedException e) {
           throw new ScriptCompilationException("Exception during script compilation", e);
        }
    }
}

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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import com.netflix.scriptlib.core.ScriptModule;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptArchiveCompiler;
import com.netflix.scriptlib.core.compile.ScriptCompilationException;

/**
 *
 *
 * @author James Kojo
 */
public class BasicGroovy2ArchiveCompiler implements ScriptArchiveCompiler {
    private final static String GROOVY_FILE_SUFFIX = ".groovy";
    private final static String CLASS_FILE_SUFFIX = ".class";

    @Override
    public boolean shouldCompile(ScriptArchive archive) {
        for (Path file : archive.getFiles()) {
            if (file.endsWith(GROOVY_FILE_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ScriptModule compile(ScriptArchive archive) throws IOException, ScriptCompilationException {
        // TODO: pluggable classloaders for compiling and loading
        ClassLoader compileClassloader = Thread.currentThread().getContextClassLoader();
        String archiveName = archive.getArchiveName();
        int archiveVersion = archive.getArchiveVersion();
        Path baseOutputPath = Files.createTempDirectory(archiveName + "-" + archiveVersion);
        new Groovy2CompilerHelper(baseOutputPath)
            .withCompileClassloader(compileClassloader)
            .addScriptArchive(archive)
            .compile();

        List<Path> archiveFiles = archive.getFiles();
        return createScriptModule(archiveName, archiveVersion, archiveFiles, baseOutputPath, compileClassloader);
    }

    //  TODO: move this somewhere else
    public static ScriptModule createScriptModule(String archiveName, int archiveVersion, List<Path> archiveFiles, Path baseClassPath, ClassLoader parentClassLoader) throws IOException {
        // load the exploded archive into the classloader
        URL[] baseUrl = new URL[] { baseClassPath.toUri().toURL() };
        final URLClassLoader moduleClassloader = new URLClassLoader(baseUrl, parentClassLoader);
        final List<Class<?>> loadedClasses = new ArrayList<Class<?>>(archiveFiles.size());
        final List<String> resourceFilesNames = new ArrayList<String>(archiveFiles.size());
        Files.walkFileTree(baseClassPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                FileVisitResult result = super.visitFile(file, attrs);
                String fileName = file.toFile().getName();
                if (fileName.endsWith(CLASS_FILE_SUFFIX)) {
                    Path parentDir = file.getParent().normalize();
                    StringBuffer fqn = new StringBuffer();
                    for (int i = 0; i < parentDir.getNameCount(); i++) {
                        String dirName = parentDir.getName(i).toString();
                        fqn.append(dirName).append(".");
                    }
                    fqn.append(fileName.substring(0, fileName.length() - CLASS_FILE_SUFFIX.length()));
                    try {
                        Class<?> loadedClass = moduleClassloader.loadClass(fqn.toString());
                        loadedClasses.add(loadedClass);
                    } catch (ClassNotFoundException e) {
                        throw new IOException("couldn't load class " + file, e);
                    }
                } else {
                    resourceFilesNames.add(fileName);
                }
                return result;
            }
        });
        return new ScriptModule(archiveName, archiveVersion, loadedClasses, resourceFilesNames, moduleClassloader);
    }
}

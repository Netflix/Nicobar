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
package com.netflix.scriptlib.groovy2.module;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.module.ScriptArchiveModuleClassLoader;
import com.netflix.scriptlib.groovy2.compile.ScriptArchiveGroovyClassloader;

/**
 * Adapts a {@link GroovyClassLoader} to a {@link ModuleClassLoader}
 *
 * @author James Kojo
 */
public class Groovy2ModuleClassLoader extends ScriptArchiveModuleClassLoader {

    /**
     * instantiates a factory which provides the ModuleClassLoader. This is used to inject the custom
     * ModuleClassloader in the Module framework
     * @param rootSourcePath root path of the source files
     */
    public static ModuleClassLoaderFactory createFactory(final ScriptArchive scriptArchive) {
        return new ModuleClassLoaderFactory() {
            @Override
            public ModuleClassLoader create(Configuration configuration) {
                return new Groovy2ModuleClassLoader(configuration, scriptArchive);
            }
        };
    }

    private final ScriptArchiveGroovyClassloader groovyClassLoader;
    public Groovy2ModuleClassLoader(Configuration configuration, ScriptArchive scriptArchive) {
        super(configuration, scriptArchive);
        groovyClassLoader = new ScriptArchiveGroovyClassloader(this, null, false);
        groovyClassLoader.addURL(scriptArchive.getRootUrl());
    }

    @Override
    public void initialize() throws Exception {
    }

    @Override
    public Class<?> loadClassLocal(String className, boolean resolve) throws ClassNotFoundException {
        // see if we already loaded the class
        Class classCacheEntry = groovyClassLoader.getClassCacheEntry(className);
        if (classCacheEntry != null) {
            return classCacheEntry;
        }
        // determine if we have the source file
        String filePath = classNameToPath(className);
        if (scriptArchive.getArchiveEntryNames().contains(filePath)) {
            try {
                URL source = scriptArchive.getEntry(filePath);
                return groovyClassLoader.compileLocal(className, source);
            } catch (IOException e) {
                throw new ClassNotFoundException(null, e);
            }
        }
        return null;
    }

    private static String classNameToPath(String className) {
        String fileName = className.replace(".", File.separator);
        if (!fileName.endsWith(".groovy")) {
            fileName = fileName + ".groovy";
        }
        return fileName;
    }


}

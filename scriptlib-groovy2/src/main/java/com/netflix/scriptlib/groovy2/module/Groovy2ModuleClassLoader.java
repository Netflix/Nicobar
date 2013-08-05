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

import java.net.URL;
import java.net.URLClassLoader;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;

/**
 * Adapts a {@link GroovyClassLoader} to a {@link ModuleClassLoader}
 *
 * @author James Kojo
 */
public class Groovy2ModuleClassLoader extends ModuleClassLoader {

    /**
     * instantiates a factory which provides the ModuleClassLoader. This is used to inject the custom
     * ModuleClassloader in the Module framework
     * @param rootSourcePath root path of the source files
     */
    public static ModuleClassLoaderFactory createFactory(final URL rootSourcePath) {
        return new ModuleClassLoaderFactory() {
            @Override
            public ModuleClassLoader create(Configuration configuration) {
                return new Groovy2ModuleClassLoader(configuration, rootSourcePath);
            }
        };
    }

    private final GroovyClassLoader groovyClassLoader;
    public Groovy2ModuleClassLoader(Configuration configuration, URL rootSourcePath) {
        super(configuration);
        URLClassLoader moduleClassLoader = new URLClassLoader(new URL[] {rootSourcePath});
        groovyClassLoader = new GroovyClassLoader(moduleClassLoader);
    }

    @Override
    public Class<?> loadClassLocal(String className, boolean resolve) throws ClassNotFoundException {
       return groovyClassLoader.loadClass(className, true, true, resolve);
    }
}

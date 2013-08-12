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
package com.netflix.scriptlib.core.module;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.jboss.modules.ModuleSpec;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * base implementation of {@link ModuleClassLoader}s for this library
 * Holds a {@link ScriptArchive} and adds simple life-cycle hooks
 * adds a post-construction hook to inject classes into the classloader
 * @author James Kojo
 */
public class ScriptModuleClassLoader extends ModuleClassLoader {
    private final ScriptArchive scriptArchive;
    private final Map<String, Class<?>> localClassCache;

    public ScriptModuleClassLoader(Configuration moduleClassLoaderContext, ScriptArchive scriptArchive) {
        super(moduleClassLoaderContext);
        this.scriptArchive = scriptArchive;
        this.localClassCache = new ConcurrentHashMap<String, Class<?>>(scriptArchive.getArchiveEntryNames().size());
    }

    /**
     * Creates a ModuleClassLoaderFactory that produces a {@link ScriptModuleClassLoader}.
     * This method is necessary to inject our custom {@link ModuleClassLoader} into
     * the {@link ModuleSpec}
     */
    protected static ModuleClassLoaderFactory createFactory(final ScriptArchive scriptArchive) {
        return new ModuleClassLoaderFactory() {
            @Override
            public ModuleClassLoader create(Configuration configuration) {
                return new ScriptModuleClassLoader(configuration, scriptArchive);
            }
        };
    }

    /**
     * Manually add the compiled classes to this classloader
     */
    public void addClasses(Set<Class<?>> classes) {
        for (Class<?> classToAdd: classes) {
            localClassCache.put(classToAdd.getName(), classToAdd);
        }
    }

    /**
     * Manually add the compiled classes to this classloader
     * @return the loaded class
     */
    public Class<?> addClassBytes(String name, byte[] classBytes) {
        Class<?> newClass = defineClass(name, classBytes, 0, classBytes.length);
        resolveClass(newClass);
        localClassCache.put(newClass.getName(), newClass);
        return newClass;
    }

    @Override
    public Class<?> loadClassLocal(String className, boolean resolve) throws ClassNotFoundException {
        Class<?> local = localClassCache.get(className);
        if (local != null) {
            return local;
        }
        return super.loadClassLocal(className, resolve);
    }

    public ScriptArchive getScriptArchive() {
        return scriptArchive;
    }

    public Set<Class<?>> getLoadedClasses() {
        return Collections.unmodifiableSet(new HashSet<Class<?>>(localClassCache.values()));
    }
}

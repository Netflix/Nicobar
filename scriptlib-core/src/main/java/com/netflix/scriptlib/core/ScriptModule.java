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
package com.netflix.scriptlib.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * Encapsulates a the compiled classes and the resources in a {@link ScriptArchive}
 *
 * @author James Kojo
 */
public class ScriptModule {
    private final String moduleName;
    private final int moduleVersion;
    private final List<Class<?>> loadedClasses;
    private final List<String> resourceNames;
    private final ClassLoader classLoader;


    public ScriptModule(String moduleName, int moduleVersion, List<Class<?>> loadedClasses, List<String> resources, ClassLoader classLoader) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.moduleVersion = moduleVersion;
        Objects.requireNonNull(loadedClasses, "loadedClasses");
        this.loadedClasses = Collections.unmodifiableList(loadedClasses);
        this.resourceNames = Objects.requireNonNull(resources, "resources");
        this.classLoader =  Objects.requireNonNull(classLoader, "classLoader");
    }

    public String getModuleName() {
        return moduleName;
    }

    public int getModuleVersion() {
        return moduleVersion;
    }

    public List<Class<?>> getLoadedClasses() {
        return loadedClasses;
    }

    public List<String> getResourceNames() {
        return resourceNames;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
}

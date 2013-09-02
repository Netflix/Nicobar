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

import java.util.Set;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * Encapsulates a the compiled classes and the resources in a {@link ScriptArchive}
 *
 * @author James Kojo
 */
public interface ScriptModule {

    /**
     * @return module identifier
     */
    public String getModuleId();

    /**
     * @return the classes that were compiled and loaded from the scripts
     */
    public Set<Class<?>> getLoadedClasses();

    /**
     * Get the module classloader that is currently plugged into the graph
     * of classloaders per the module specificaiton. note that this classloader
     * was not necessarily the one used to load the classes in getLoadedClasses(),
     * since thos may have been injected.
     */
    public ScriptModuleClassLoader getModuleClassLoader();

}
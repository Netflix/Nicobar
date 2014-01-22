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
package com.netflix.nicobar.core.module;

import java.util.Set;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;

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
    public JBossModuleClassLoader getModuleClassLoader();

    /**
     * Timestamp used to resolve multiple revisions of a {@link ScriptModule}. This is usually
     * copied from the {@link ScriptArchive} which sourced this {@link ScriptModule}
     */
    public long getCreateTime();

    /**
     * @return the original archive this module was produced from
     */
    ScriptArchive getSourceArchive();

}
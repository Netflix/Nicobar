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

import java.util.Objects;
import java.util.Set;

import org.jboss.modules.Module;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * Encapsulates a the compiled classes and the resources in a {@link ScriptArchive}
 *
 * @author James Kojo
 */
public class JBossScriptModule implements ScriptModule {
    private final String moduleName;
    private final int moduleVersion;
    private final Module jbossModule;

    public JBossScriptModule(String moduleName, int moduleVersion, Module jbossModule) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
        this.moduleVersion = moduleVersion;
        this.jbossModule =  Objects.requireNonNull(jbossModule, "jbossModule");
    }

    /**
     * @return module identifier
     */
    @Override
    public String getModuleName() {
        return moduleName;
    }

    /**
     * @return module version identifier
     */
    @Override
    public int getModuleVersion() {
        return moduleVersion;
    }

    /**
     * @return the classes that were compiled and loaded from the scripts
     */
    @Override
    public Set<Class<?>> getLoadedClasses() {
        return getModuleClassLoader().getLoadedClasses();
    }

    @Override
    public ScriptModuleClassLoader getModuleClassLoader() {
        return (ScriptModuleClassLoader)jbossModule.getClassLoader();
    }
}

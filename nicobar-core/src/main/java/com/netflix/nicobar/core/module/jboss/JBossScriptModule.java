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
package com.netflix.nicobar.core.module.jboss;

import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.jboss.modules.Module;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.module.ScriptModule;

/**
 * Encapsulates a the compiled classes and the resources in a {@link ScriptArchive}
 *
 * @author James Kojo
 */
public class JBossScriptModule implements ScriptModule {
    private final String moduleId;
    private final Module jbossModule;
    private final long createTime;
    private final ScriptArchive sourceArchive;

    public JBossScriptModule(String moduleName, Module jbossModule, ScriptArchive sourceArchive) {
        this.moduleId = Objects.requireNonNull(moduleName, "moduleName");
        this.jbossModule =  Objects.requireNonNull(jbossModule, "jbossModule");
        this.createTime = sourceArchive.getCreateTime();
        this.sourceArchive = Objects.requireNonNull(sourceArchive, "sourceArchive");
    }

    /**
     * @return module identifier
     */
    @Override
    public String getModuleId() {
        return moduleId;
    }

    /**
     * @return the classes that were compiled and loaded from the scripts
     */
    @Override
    public Set<Class<?>> getLoadedClasses() {
        return getModuleClassLoader().getLoadedClasses();
    }

    @Override
    public JBossModuleClassLoader getModuleClassLoader() {
        return (JBossModuleClassLoader)jbossModule.getClassLoader();
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("moduleId", moduleId)
            .append("jbossModule", jbossModule)
            .append("createTime", createTime)
            .append("sourceArchive", sourceArchive)
            .toString();
    }

    public ScriptArchive getSourceArchive() {
        return sourceArchive;
    }
}

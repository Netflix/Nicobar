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

import org.jboss.modules.ModuleClassLoader;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 * base class script based {@link ModuleClassLoader}s
 *
 * @author James Kojo
 */
public abstract class ScriptArchiveModuleClassLoader extends ModuleClassLoader {
    protected final ScriptArchive scriptArchive;

    public ScriptArchiveModuleClassLoader(Configuration configuration, ScriptArchive scriptArchive) {
        super(configuration);
        this.scriptArchive = scriptArchive;
    }

    /**
     * Will be called as post-loading the module. Implementations must assume
     * this may be called multiple times;
     * @throws Exception
     */
    public abstract void initialize() throws Exception;
}

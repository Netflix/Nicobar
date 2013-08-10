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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.module.ModuleUtils;

/**
 * ModuleFinder which is backed by a {@link ConcurrentHashMap}.
 * Integration class used to help the conversion of {@link ScriptArchive}s to
 * Modules
 *
 * @author James Kojo
 */
public class Groovy2ScriptModuleFinder implements ModuleFinder {

    private final Map<ModuleIdentifier, ScriptArchive> scriptArchiveRepo =
        new ConcurrentHashMap<ModuleIdentifier, ScriptArchive>();

    public Groovy2ScriptModuleFinder() {
    }
    @Override
    public ModuleSpec findModule(ModuleIdentifier moduleId, ModuleLoader delegateLoader) throws ModuleLoadException {
        ScriptArchive scriptArchive = scriptArchiveRepo.get(moduleId);
        if (scriptArchive == null) {
            return null;
        }
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);
        try {
            ModuleUtils.populateModuleSpec(moduleSpecBuilder, scriptArchive);
        } catch (IOException e) {
            throw new ModuleLoadException(e);
        }
        // inject a custom classloader that can compile and load groovy files
        moduleSpecBuilder.setModuleClassLoaderFactory(Groovy2ModuleClassLoader.createFactory(scriptArchive));

        return moduleSpecBuilder.create();
    }

    public ScriptArchive addToRepository(ScriptArchive archive) {
        return scriptArchiveRepo.put(ModuleIdentifier.create(archive.getArchiveName()), archive);
    }

    public ScriptArchive removeFromRepository(String moduleName) {
        return scriptArchiveRepo.remove(ModuleIdentifier.create(moduleName));
    }
}

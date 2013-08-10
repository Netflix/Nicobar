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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.ConcreteModuleSpec;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

import com.netflix.scriptlib.core.archive.ScriptArchive;

/**
 *
 * {@link ModuleLoader} implementation which is provides an explicit
 * add function for {@link ScriptArchive} instead of using the {@link ModuleFinder} interface.
 *
 * @author James Kojo
 */
public class ScriptArchiveModuleLoader extends ModuleLoader {

    private static interface ModuleIdFunction<T> {
        public T apply(ModuleIdentifier module);
    }

    private final Map<ModuleIdentifier, ModuleSpec> moduleSpecs = new ConcurrentHashMap<ModuleIdentifier, ModuleSpec>();
    private final Map<ModuleIdentifier, ScriptArchive> scriptArchiveRepo =
        new ConcurrentHashMap<ModuleIdentifier, ScriptArchive>();

    // make this a batch loader which also compiles directly to ScriptModule?
    public void addScriptArchive(ScriptArchive scriptArchive) throws IOException {
        ModuleIdentifier moduleId = ModuleIdentifier.create(scriptArchive.getArchiveName());
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, scriptArchive);
        ModuleSpec moduleSpec = moduleSpecBuilder.create();

        // safe to load? may not need transitive loading logic in preloadModule.
        // if already exists, should unload?
        moduleSpecs.put(moduleId, moduleSpec);
    }

    public void removeScriptArchive(String archiveName) {
        ModuleIdentifier moduleIdentifier = ModuleIdentifier.create(archiveName);
        scriptArchiveRepo.remove(moduleIdentifier);
        moduleSpecs.remove(moduleIdentifier);
        Module module = findLoadedModuleLocal(moduleIdentifier);
        if (module != null) {
            unloadModuleLocal(module);
        }
    }

    @Override
    protected ModuleSpec findModule(ModuleIdentifier moduleIdentifier) throws ModuleLoadException {
        return moduleSpecs.get(moduleIdentifier);
    }
    @Override
    protected Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        // catch module load exception and add to "PENDING" list.
        ModuleSpec thisModuleSpec = findModule(identifier);
        if (thisModuleSpec instanceof ConcreteModuleSpec) {
            DependencySpec[] dependencies = ((ConcreteModuleSpec)thisModuleSpec).getDependencies();
            for (DependencySpec dependencySpec : dependencies) {
                if (dependencySpec instanceof ModuleDependencySpec) {
                    ModuleDependencySpec moduleDependencySpec = (ModuleDependencySpec)dependencySpec;
                    ModuleIdentifier dependencyModuleId = moduleDependencySpec.getIdentifier();
                    preloadModule(dependencyModuleId);
                }
            }
        }
        Module thisModule = super.preloadModule(identifier);
        return thisModule;
    }


}

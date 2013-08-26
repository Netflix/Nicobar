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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptCompiler;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPlugin;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;

/**
 * Builds and maintains interdependent script modules.
 * Support pluggable compilers via the {@link ScriptCompilerPluginSpec}.
 *
 * @author James Kojo
 */
public class ScriptModuleLoader extends ModuleLoader {
    private final Map<ModuleIdentifier, ModuleSpec> moduleSpecRepo = new ConcurrentHashMap<ModuleIdentifier, ModuleSpec>();
    private final List<ScriptCompiler> compilers = new ArrayList<ScriptCompiler>();
    private final Set<ScriptCompilerPluginSpec> pluginSpecs;

    public ScriptModuleLoader(Set<ScriptCompilerPluginSpec> pluginSpecs) {
        this.pluginSpecs = Objects.requireNonNull(pluginSpecs);
    }

    /**
     * Start the module loader.
     * Loads the compiler plugins.
     * @throws ModuleLoadException
     */
    public synchronized void start() throws ModuleLoadException {
        for (ScriptCompilerPluginSpec pluginSpec : pluginSpecs) {
            addCompilerPlugin(pluginSpec);
        }
    }

    public synchronized Set<ScriptModule> addScriptArchives(Set<? extends ScriptArchive> archives)  {
        Objects.requireNonNull(archives);
        List<ModuleIdentifier> moduleIds = new ArrayList<ModuleIdentifier>(archives.size());

        // add all module specs before trying to load the modules because loading will
        // cause the transitive dependencies to be compiled
        for (ScriptArchive scriptArchive : archives) {
            ModuleIdentifier moduleId;
            try {
                moduleId = addScriptArchive(scriptArchive);
                moduleIds.add(moduleId);
            } catch (ModuleLoadException e) {
                // TODO: add real logging. perhaps adds this modules to a "try again later" queue?
                Module.getModuleLogger().trace(e, "Exception loading archive " + scriptArchive.getArchiveName());
                continue;
            }
        }
        LinkedHashSet<ScriptModule> scriptModules = new LinkedHashSet<ScriptModule>(archives.size());
        for (ModuleIdentifier moduleId : moduleIds) {
            Module module;
            try {
                module = loadModule(moduleId);
            } catch (ModuleLoadException e) {
                Module.getModuleLogger().trace(e, "Exception loading module " + moduleId);
                continue;
            }
            JBossScriptModule scriptModule = new JBossScriptModule(moduleId.getName(), 1, module);
            scriptModules.add(scriptModule);
        }
        return scriptModules;
    }

    /**
     * Creates {@link ModuleSpec} for the archive, and add it to the module spec repo so that
     * the module is ready to load.
     */
    protected synchronized ModuleIdentifier addScriptArchive(ScriptArchive scriptArchive) throws ModuleLoadException {
        String archiveName = scriptArchive.getArchiveName();
        ModuleIdentifier moduleId = ModuleUtils.getModuleId(scriptArchive);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, scriptArchive);
        ModuleSpec moduleSpec = moduleSpecBuilder.create();

        // if it already exists, unload the old version
        ModuleSpec replaced = moduleSpecRepo.get(moduleId);
        if (replaced != null) {
            removeScriptArchive(archiveName);
        }
        moduleSpecRepo.put(moduleId, moduleSpec);
        return moduleId;
    }

    /**
     * Add a language plugin to this module
     * @param pluginSpec
     * @throws ModuleLoadException
     */
    protected void addCompilerPlugin(ScriptCompilerPluginSpec pluginSpec) throws ModuleLoadException  {
        ModuleIdentifier moduleId = ModuleUtils.getModuleId(pluginSpec);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, pluginSpec);
        ModuleSpec moduleSpec = moduleSpecBuilder.create();
        moduleSpecRepo.put(moduleId, moduleSpec);

        // spin up the module, and get the compiled classes from it's classloader
        String providerClassName = pluginSpec.getPluginClassName();
        if (providerClassName != null) {
            Module pluginModule = loadModule(moduleId);
            ModuleClassLoader pluginClassLoader = pluginModule.getClassLoader();
            Class<?> compilerProviderClass;
            try {
                compilerProviderClass = pluginClassLoader.loadClass(providerClassName);
                ScriptCompilerPlugin pluginBootstrap = (ScriptCompilerPlugin) compilerProviderClass.newInstance();
                Set<? extends ScriptCompiler> pluginCompilers = pluginBootstrap.getCompilers();
                compilers.addAll(pluginCompilers);
            } catch (Exception e) {
                throw new ModuleLoadException(e);
            }
        }
    }

    public synchronized void removeScriptArchive(String archiveName) {
        ModuleIdentifier moduleIdentifier = ModuleIdentifier.create(archiveName);
        moduleSpecRepo.remove(moduleIdentifier);
        Module module = findLoadedModuleLocal(moduleIdentifier);
        if (module != null) {
            unloadModuleLocal(module);
        }
    }

    @Override
    protected ModuleSpec findModule(ModuleIdentifier moduleIdentifier) throws ModuleLoadException {
        return moduleSpecRepo.get(moduleIdentifier);
    }
    @Override
    protected Module preloadModule(ModuleIdentifier moduleId) throws ModuleLoadException {
        // for some reason, the modules framework is calling preloadModule() directly from the path-linking logic
        // so that a given module may be "preloaded" several times. This guards against double initialization
        Module module = findLoadedModuleLocal(moduleId);
        if (module != null) {
            return module;
        }
        module = super.preloadModule(moduleId);
        if (module == null) {
            throw new ModuleLoadException("Failed to locate module " + moduleId);
        }
        // compile the script archive for the module, and inject the resultant classes into
        // the ModuleClassLoader
        ModuleClassLoader moduleClassLoader = module.getClassLoader();
        if (moduleClassLoader instanceof ScriptModuleClassLoader) {
            ScriptModuleClassLoader scriptModuleClassLoader = (ScriptModuleClassLoader)moduleClassLoader;
            ScriptArchive scriptArchive = scriptModuleClassLoader.getScriptArchive();
            ScriptCompiler compiler = findCompiler(scriptArchive);
            if (compiler != null) {
                try {
                    compiler.compile(scriptArchive, scriptModuleClassLoader);
                } catch (Exception e) {
                    throw new ModuleLoadException("Exception while compiling module " + moduleId, e);
                }
            }
        }
        return module;
    }

    /**
     * Match a compiler up to the given archive
     */
    protected ScriptCompiler findCompiler(ScriptArchive archive) {
        for (ScriptCompiler compiler : compilers) {
            if (compiler.shouldCompile(archive)) {
                return compiler;
            }
        }
        return null;
    }
}

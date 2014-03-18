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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;
import com.netflix.nicobar.core.module.jboss.JBossModuleLoader;
import com.netflix.nicobar.core.module.jboss.JBossModuleUtils;
import com.netflix.nicobar.core.module.jboss.JBossScriptModule;
import com.netflix.nicobar.core.plugin.ScriptCompilerPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;

/**
 * Top level API for loading and accessing scripts.
 * Builds and maintains interdependent script modules.
 * Performs coordination between components neccessary for
 * finding, compiling and loading scripts and notifying event listeners.
 *
 * Listeners can be added at any time during the repositories lifecycle, but if they are
 * added at construction time, they are guaranteed to receive the events associate with the loading
 * of archives, while those that are added later will only get events generated after the listener was added.
 *
 * Support pluggable compilers via the {@link ScriptCompilerPluginSpec}.
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class ScriptModuleLoader {
    private final static Logger logger = LoggerFactory.getLogger(ScriptModuleLoader.class);

    /**
     * Builder used to constract a {@link ScriptModuleLoader}
     */
    public static class Builder {
        private final Set<ScriptCompilerPluginSpec> pluginSpecs=  new LinkedHashSet<ScriptCompilerPluginSpec>();
        private final Set<ScriptModuleListener> listeners = new LinkedHashSet<ScriptModuleListener>();
        private final Set<String> paths = new LinkedHashSet<String>();
        private ClassLoader appClassLoader = ScriptModuleLoader.class.getClassLoader();

        public Builder() {
        }

        /** Add a language compiler plugin specification to the loader */
        public Builder addPluginSpec(ScriptCompilerPluginSpec pluginSpec) {
            if (pluginSpec != null) {
                pluginSpecs.add(pluginSpec);
            }
            return this;
        }

        /**
         * Use a specific classloader as the application classloader.
         * @param loader the application classloader
         */
        public Builder withAppClassLoader(ClassLoader loader) {
            Objects.requireNonNull(loader);
            this.appClassLoader = loader;
            return this;
        }
        /**
         * Specify a set of packages to make available from the application classloader
         * as runtime dependencies for all scripts loaded by this script module.
         * @param incomingPaths a set of / separated package paths. No wildcards.
         *        e.g. com/netflix/api/service/video. All classes under
         *        com.netflix.api.service.video will be available to loaded modules.
         */
        public Builder addAppPackages(Set<String> incomingPaths) {
            if (incomingPaths != null) {
                paths.addAll(incomingPaths);
            }
            return this;
        }

        /** Add a archive poller which will be polled at the given interval */
        public Builder addListener(ScriptModuleListener listener) {
            if (listener != null) {
                listeners.add(listener);
            }
            return this;
        }

        public ScriptModuleLoader build() throws ModuleLoadException {
           return new ScriptModuleLoader(pluginSpecs, appClassLoader, paths, listeners);
        }
    }

    /** Map of script ModuleId to the loaded ScriptModules */
    protected final Map<String, ScriptModule> loadedScriptModules = new ConcurrentHashMap<String, ScriptModule>();

    protected final Set<ScriptCompilerPluginSpec> pluginSpecs;
    protected final ClassLoader appClassLoader;
    protected final Set<String> appPackagePaths;
    protected final List<ScriptArchiveCompiler> compilers = new ArrayList<ScriptArchiveCompiler>();

    protected final Set<ScriptModuleListener> listeners =
        Collections.newSetFromMap(new ConcurrentHashMap<ScriptModuleListener, Boolean>());

    protected final JBossModuleLoader jbossModuleLoader;

    protected ScriptModuleLoader(final Set<ScriptCompilerPluginSpec> pluginSpecs,
            final ClassLoader appClassLoader,
            final Set<String> appPackagePaths,
            final Set<ScriptModuleListener> listeners) throws ModuleLoadException {
        this.pluginSpecs = Objects.requireNonNull(pluginSpecs);
        this.appClassLoader = Objects.requireNonNull(appClassLoader);
        this.appPackagePaths = Objects.requireNonNull(appPackagePaths);
        this.jbossModuleLoader = new JBossModuleLoader();
        for (ScriptCompilerPluginSpec pluginSpec : pluginSpecs) {
            addCompilerPlugin(pluginSpec);
        }
        addListeners(Objects.requireNonNull(listeners));
    }

    /**
     * Add or update the existing {@link ScriptModule}s with the given script archives.
     * This method will convert the archives to modules and then compile + link them in to the
     * dependency graph. It will then recursively re-link any modules depending on the new modules.
     * If this loader already contains an old version of the module, it will be unloaded on
     * successful compile of the new module.
     *
     * @param candidateArchives archives to load or update
     */
    public synchronized void updateScriptArchives(Set<ScriptArchive> candidateArchives)  {
        Objects.requireNonNull(candidateArchives);
        long updateNumber = System.currentTimeMillis();

        // map script module id to archive to be compiled
        Map<String, ScriptArchive> archivesToCompile = new HashMap<String, ScriptArchive>(candidateArchives.size()*2);

        // create an updated mapping of the scriptModuleId to latest revisionId including the yet-to-be-compiled archives
        Map<String, ModuleIdentifier> oldRevisionIdMap = jbossModuleLoader.getLatestRevisionIds();
        Map<String, ModuleIdentifier> updatedRevisionIdMap = new HashMap<String, ModuleIdentifier>((oldRevisionIdMap.size()+candidateArchives.size())*2);
        updatedRevisionIdMap.putAll(oldRevisionIdMap);

        // Map of the scriptModuleId to it's updated set of dependencies
        Map<String, Set<String>> archiveDependencies = new HashMap<String, Set<String>>();
        for (ScriptArchive scriptArchive : candidateArchives) {
            String scriptModuleId = scriptArchive.getModuleSpec().getModuleId();

            // filter out archives that have a newer module already loaded
            long createTime = scriptArchive.getCreateTime();
            ScriptModule scriptModule = loadedScriptModules.get(scriptModuleId);
            long latestCreateTime = scriptModule != null ? scriptModule.getCreateTime() : 0;
            if (createTime < latestCreateTime) {
                notifyArchiveRejected(scriptArchive, ArchiveRejectedReason.HIGHER_REVISION_AVAILABLE, null);
                continue;
            }

            // create the new revisionIds that should be used for the linkages when the new modules
            // are defined.
            ModuleIdentifier newRevisionId = JBossModuleUtils.createRevisionId(scriptModuleId, updateNumber);
            updatedRevisionIdMap.put(scriptModuleId, newRevisionId);

            archivesToCompile.put(scriptModuleId, scriptArchive);

            // create a dependency map of the incoming archives so that we can later build a candidate graph
            archiveDependencies.put(scriptModuleId, scriptArchive.getModuleSpec().getModuleDependencies());
        }

        // create a dependency graph with the candidates swapped in in order to figure out the
        // order in which the candidates should be loaded
        DirectedGraph<String, DefaultEdge> candidateGraph = jbossModuleLoader.getModuleNameGraph();
        GraphUtils.swapVertices(candidateGraph, archiveDependencies);

        // iterate over the graph in reverse dependency order
        Set<String> leaves = GraphUtils.getLeafVertices(candidateGraph);
        while (!leaves.isEmpty()) {
            for (String scriptModuleId : leaves) {
                ScriptArchive scriptArchive = archivesToCompile.get(scriptModuleId);
                if (scriptArchive == null) {
                    continue;
                }
                ModuleSpec moduleSpec;
                ModuleIdentifier candidateRevisionId;
                try {
                    // create the jboss module pre-cursor artifact
                    candidateRevisionId = updatedRevisionIdMap.get(scriptModuleId);
                    ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(candidateRevisionId);
                    // Populate the modulespec with the scriptArchive dependencies
                    JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, scriptArchive, updatedRevisionIdMap);
                    // Add to the moduleSpec application classloader dependencies
                    JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, appClassLoader, appPackagePaths);
                    moduleSpec = moduleSpecBuilder.create();
                } catch (ModuleLoadException e) {
                    logger.error("Exception loading archive " +
                        scriptArchive.getModuleSpec().getModuleId(), e);
                    notifyArchiveRejected(scriptArchive, ArchiveRejectedReason.ARCHIVE_IO_EXCEPTION, e);
                    continue;
                }

                // load and compile the module
                jbossModuleLoader.addModuleSpec(moduleSpec);
                Module jbossModule = null;
                try {
                    jbossModule = jbossModuleLoader.loadModule(candidateRevisionId);
                    compileModule(jbossModule);
                } catch (Exception e) {
                    // rollback
                    logger.error("Exception loading module " + candidateRevisionId, e);
                    if (candidateArchives.contains(scriptArchive)) {
                        // this spec came from a candidate archive. Send reject notification
                        notifyArchiveRejected(scriptArchive, ArchiveRejectedReason.COMPILE_FAILURE, e);
                    }
                    if (jbossModule != null) {
                        jbossModuleLoader.unloadModule(jbossModule);
                    }
                    continue;
                }

                // commit the change by removing the old module
                ModuleIdentifier oldRevisionId = oldRevisionIdMap.get(scriptModuleId);
                if (oldRevisionId != null) {
                    jbossModuleLoader.unloadModule(oldRevisionId);
                }

                JBossScriptModule scriptModule = new JBossScriptModule(scriptModuleId, jbossModule, scriptArchive);
                ScriptModule oldModule = loadedScriptModules.put(scriptModuleId, scriptModule);
                notifyModuleUpdate(scriptModule, oldModule);

                // find dependents and add them to the to be compiled set
                Set<String> dependents = GraphUtils.getIncomingVertices(candidateGraph, scriptModuleId);
                for (String dependentScriptModuleId : dependents) {
                    if (!archivesToCompile.containsKey(dependentScriptModuleId)) {
                        ScriptModule dependentScriptModule = loadedScriptModules.get(dependentScriptModuleId);
                        if (dependentScriptModule != null) {
                            archivesToCompile.put(dependentScriptModuleId, dependentScriptModule.getSourceArchive());
                            ModuleIdentifier dependentRevisionId = JBossModuleUtils.createRevisionId(dependentScriptModuleId, updateNumber);
                            updatedRevisionIdMap.put(dependentScriptModuleId, dependentRevisionId);
                        }
                    }
                }
            }

            GraphUtils.removeVertices(candidateGraph, leaves);
            leaves = GraphUtils.getLeafVertices(candidateGraph);
        }
    }

    /**
     * Compiles and links the scripts within the module by locating the correct compiler
     * and delegating the compilation. the classes will be loaded into the module's classloader
     * upon completion.
     * @param module module to be compiled
     */
    protected void compileModule(Module module) throws ScriptCompilationException, IOException {
        // compile the script archive for the module, and inject the resultant classes into
        // the ModuleClassLoader
        ModuleClassLoader moduleClassLoader = module.getClassLoader();
        if (moduleClassLoader instanceof JBossModuleClassLoader) {
            JBossModuleClassLoader jBossModuleClassLoader = (JBossModuleClassLoader)moduleClassLoader;
            ScriptArchive scriptArchive = jBossModuleClassLoader.getScriptArchive();
            List<ScriptArchiveCompiler> candidateCompilers = findCompiler(scriptArchive);
            if (candidateCompilers.size() == 0) {
                throw new ScriptCompilationException("Could not find a suitable compiler for this archive.");
            }

            // Compile iteratively
            for (ScriptArchiveCompiler compiler: candidateCompilers) {
                compiler.compile(scriptArchive, jBossModuleClassLoader);
            }
        }
    }

    /**
     * Add a language plugin to this module
     * @param pluginSpec
     * @throws ModuleLoadException
     */
    public synchronized void addCompilerPlugin(ScriptCompilerPluginSpec pluginSpec) throws ModuleLoadException  {
        Objects.requireNonNull(pluginSpec, "pluginSpec");
        ModuleIdentifier pluginModuleId = JBossModuleUtils.getPluginModuleId(pluginSpec);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(pluginModuleId);
        JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, pluginSpec);
        // TODO: We expose the full set of app packages to the compiler too.
        // Maybe more control over what is exposed is needed here.
        JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, appClassLoader, appPackagePaths);
        ModuleSpec moduleSpec = moduleSpecBuilder.create();

        // spin up the module, and get the compiled classes from it's classloader
        String providerClassName = pluginSpec.getPluginClassName();
        if (providerClassName != null) {
            jbossModuleLoader.addModuleSpec(moduleSpec);
            Module pluginModule = jbossModuleLoader.loadModule(pluginModuleId);
            ModuleClassLoader pluginClassLoader = pluginModule.getClassLoader();
            Class<?> compilerProviderClass;
            try {
                compilerProviderClass = pluginClassLoader.loadClass(providerClassName);
                ScriptCompilerPlugin pluginBootstrap = (ScriptCompilerPlugin) compilerProviderClass.newInstance();
                Set<? extends ScriptArchiveCompiler> pluginCompilers = pluginBootstrap.getCompilers();
                compilers.addAll(pluginCompilers);
            } catch (Exception e) {
                throw new ModuleLoadException(e);
            }
        }
    }

    /**
     * Remove a module from being served by this instance. Note that any
     * instances of the module cached outside of this module loader will remain
     * un-effected and will continue to operate.
     */
    public synchronized void removeScriptModule(String scriptModuleId) {
        jbossModuleLoader.unloadAllModuleRevision(scriptModuleId);
        ScriptModule oldScriptModule = loadedScriptModules.remove(scriptModuleId);
        if (oldScriptModule != null) {
            notifyModuleUpdate(null, oldScriptModule);
        }
    }

    @Nullable
    public ScriptModule getScriptModule(String scriptModuleId) {
        return loadedScriptModules.get(scriptModuleId);
    }

    /**
     * Get a view of the loaded script modules
     * @return immutable view of the Map that retains the script modules. Map ModuleId the loaded ScriptModule
     */
    public Map<String, ScriptModule> getAllScriptModules() {
        return Collections.unmodifiableMap(loadedScriptModules);
    }
    /**
     * Add listeners to this module loader. Listeners will only be notified of events that occurred after they
     * were added.
     * @param listeners listeners to add
     */
    public void addListeners(Set<ScriptModuleListener> listeners) {
        Objects.requireNonNull(listeners);
        this.listeners.addAll(listeners);
    }

    /**
     * Select a set of compilers to compile this archive.
     */
    protected List<ScriptArchiveCompiler> findCompiler(ScriptArchive archive) {
        List<ScriptArchiveCompiler> candidateCompilers = new ArrayList<ScriptArchiveCompiler>();
        for (ScriptArchiveCompiler compiler : compilers) {
            if (compiler.shouldCompile(archive)) {
                candidateCompilers.add(compiler);
            }
        }
        return candidateCompilers;
    }

    /**
     * Convenience method to notify the listeners that there was an update to the script module
     * @param newModule newly loaded module
     * @param oldModule module that was displaced by the new module
     */
    protected void notifyModuleUpdate(@Nullable ScriptModule newModule, @Nullable ScriptModule oldModule) {
        for (ScriptModuleListener listener : listeners) {
            listener.moduleUpdated(newModule, oldModule);
        }
    }

    /**
     * Notify listeners that a script archive was rejected by this loader
     * @param scriptArchive archive that was rejected
     * @param reason reason it was rejected
     * @param cause underlying exception which triggered the rejection
     */
    protected void notifyArchiveRejected(ScriptArchive scriptArchive, ArchiveRejectedReason reason, @Nullable Throwable cause) {
        for (ScriptModuleListener listener : listeners) {
            listener.archiveRejected(scriptArchive, reason, cause);
        }
    }
}
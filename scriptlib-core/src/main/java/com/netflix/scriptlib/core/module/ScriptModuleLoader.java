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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptArchiveCompiler;
import com.netflix.scriptlib.core.compile.ScriptCompilationException;
import com.netflix.scriptlib.core.module.jboss.JBossModuleClassLoader;
import com.netflix.scriptlib.core.module.jboss.JBossModuleLoader;
import com.netflix.scriptlib.core.module.jboss.JBossModuleUtils;
import com.netflix.scriptlib.core.module.jboss.JBossScriptModule;
import com.netflix.scriptlib.core.persistence.ScriptArchivePoller;
import com.netflix.scriptlib.core.persistence.ScriptArchivePoller.PollResult;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPlugin;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
 */
public class ScriptModuleLoader {
    /** Thread factory used for the default poller thread pool */
    private final static ThreadFactory DEFAULT_POLLER_THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, ScriptModuleLoader.class.getSimpleName() + "-" + "PollerThread");
        }
    };

    /**
     * Builder used to constract a {@link ScriptModuleLoader}
     */
    public static class Builder {
        private final Set<ScriptCompilerPluginSpec> pluginSpecs=  new LinkedHashSet<ScriptCompilerPluginSpec>();
        private final Map<ScriptArchivePoller, Integer> pollers = new LinkedHashMap<ScriptArchivePoller, Integer>();
        private final Set<ScriptModuleListener> listeners = new LinkedHashSet<ScriptModuleListener>();
        private ScheduledExecutorService pollerThreadPool;
        public Builder() {
        }

        /** Add a language compiler plugin specification to the loader */
        public Builder addPluginSpec(ScriptCompilerPluginSpec pluginSpec) {
            if (pluginSpec != null) {
                pluginSpecs.add(pluginSpec);
            }
            return this;
        }
        /** Add a archive poller which will be polled at the given rate */
        public Builder addPoller(ScriptArchivePoller poller, int pollIntervalSeconds) {
            if (poller != null) {
                pollers.put(poller, pollIntervalSeconds);
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
        /** Add a archive poller which will be polled at the given interval */
        public Builder setPollerThreadPool(ScheduledExecutorService pollerThreadPool) {
            this.pollerThreadPool = pollerThreadPool;
            return this;
        }
        public ScriptModuleLoader build() throws ModuleLoadException {
           ScheduledExecutorService buildPollerThreadPool = pollerThreadPool;
           if (buildPollerThreadPool == null ) {
               buildPollerThreadPool = Executors.newSingleThreadScheduledExecutor(DEFAULT_POLLER_THREAD_FACTORY);
           }
           return new ScriptModuleLoader(pluginSpecs, pollers, listeners, buildPollerThreadPool);
        }
    }

    /** used for book-keeping  of pollers */
    protected static class ArchivePollerContext {
        protected final int pollInterval;
        protected volatile long lastPollTime;
        @SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification="will use later")
        protected volatile ScheduledFuture<?> future;
        protected ArchivePollerContext(int pollInterval) {
            this.pollInterval = pollInterval;
        }
    }

    /** Contains transient state required for pollers */
    protected final ConcurrentHashMap<ScriptArchivePoller, ArchivePollerContext> pollerContexts =
        new ConcurrentHashMap<ScriptArchivePoller, ArchivePollerContext>();

    /** Thread pool used by the pollers */
    protected final ScheduledExecutorService pollerThreadPool;

    /** Map of script ModuleId to the loaded ScriptModules */
    protected final Map<String, ScriptModule> loadedScriptModules = new ConcurrentHashMap<String, ScriptModule>();

    protected final Set<ScriptCompilerPluginSpec> pluginSpecs;
    protected final List<ScriptArchiveCompiler> compilers = new ArrayList<ScriptArchiveCompiler>();

    protected final Set<ScriptModuleListener> listeners =
        Collections.newSetFromMap(new ConcurrentHashMap<ScriptModuleListener, Boolean>());

    protected final JBossModuleLoader jbossModuleLoader;

    protected ScriptModuleLoader(final Set<ScriptCompilerPluginSpec> pluginSpecs,
            final Map<ScriptArchivePoller, Integer> pollers,
            final Set<ScriptModuleListener> listeners,
            final ScheduledExecutorService pollerThreadPool) throws ModuleLoadException {
        this.pluginSpecs = Objects.requireNonNull(pluginSpecs);
        this.jbossModuleLoader = new JBossModuleLoader();
        for (ScriptCompilerPluginSpec pluginSpec : pluginSpecs) {
            addCompilerPlugin(pluginSpec);
        }

        // setup pollers
        Objects.requireNonNull(pollers);
        this.pollerThreadPool = Objects.requireNonNull(pollerThreadPool);
        addListeners(Objects.requireNonNull(listeners));
        for (Entry<ScriptArchivePoller, Integer> entry : pollers.entrySet()) {
            addPoller(entry.getKey(), entry.getValue());
        }
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
            archiveDependencies.put(scriptModuleId, scriptArchive.getModuleSpec().getDependencies());
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
                    JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, scriptArchive, updatedRevisionIdMap);
                    moduleSpec = moduleSpecBuilder.create();
                } catch (ModuleLoadException e) {
                    // TODO: add real logging.
                    Module.getModuleLogger().trace(e, "Exception loading archive " +
                        scriptArchive.getModuleSpec().getModuleId());
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
                    Module.getModuleLogger().trace(e, "Exception loading module " + candidateRevisionId);
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
            ScriptArchiveCompiler compiler = findCompiler(scriptArchive);
            if (compiler != null) {
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
        ModuleIdentifier scriptModuleId = JBossModuleUtils.getModuleId(pluginSpec);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(scriptModuleId);
        JBossModuleUtils.populateModuleSpec(moduleSpecBuilder, pluginSpec);
        ModuleSpec moduleSpec = moduleSpecBuilder.create();

        // spin up the module, and get the compiled classes from it's classloader
        String providerClassName = pluginSpec.getPluginClassName();
        if (providerClassName != null) {
            jbossModuleLoader.addModuleSpec(moduleSpec);
            Module pluginModule = jbossModuleLoader.loadModule(scriptModuleId);
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

    public boolean addPoller(final ScriptArchivePoller poller, final int pollInterval) {
        if (pollInterval <= 0) {
            throw new IllegalArgumentException("invalid pollInterval " + pollInterval);
        }
        ArchivePollerContext pollerContext = new ArchivePollerContext(pollInterval);
        ArchivePollerContext oldContext = pollerContexts.putIfAbsent(poller, pollerContext);
        if (oldContext != null) {
            return false;
        }
        ScheduledFuture<?> future = pollerThreadPool.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                ArchivePollerContext context = pollerContexts.get(poller);
                if (context == null) {

                    return;
                }
                long now = System.currentTimeMillis();
                pollForUpdates(poller, context.lastPollTime);
                context.lastPollTime = now;
            }
        },
        0, pollInterval, TimeUnit.SECONDS);
        pollerContext.future = future;
        return true;
    }

    protected void pollForUpdates(ScriptArchivePoller poller, long lastPollTime) {
        PollResult pollResult;
        try {
            pollResult = poller.poll(lastPollTime);
        } catch (IOException e) {
            Module.getModuleLogger().trace(e, "Error attempting to poll");
            return;
        }
        Set<ScriptArchive> updatedArchives = pollResult.getUpdatedArchives();
        if (!updatedArchives.isEmpty()) {
            updateScriptArchives(updatedArchives);
        }
        Set<String> deletedModuleIds = pollResult.getDeletedModuleIds();
        if (!deletedModuleIds.isEmpty()) {
            for (String scriptModuleId : deletedModuleIds) {
                removeScriptModule(scriptModuleId);
            }
        }
    }

    /**
     * Match a compiler up to the given archive
     */
    protected ScriptArchiveCompiler findCompiler(ScriptArchive archive) {
        for (ScriptArchiveCompiler compiler : compilers) {
            if (compiler.shouldCompile(archive)) {
                return compiler;
            }
        }
        return null;
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
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
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.compile.ScriptCompilationException;
import com.netflix.scriptlib.core.compile.ScriptCompiler;
import com.netflix.scriptlib.core.persistence.ScriptArchivePoller;
import com.netflix.scriptlib.core.persistence.ScriptArchivePoller.PollResult;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPlugin;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;

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

    /** Map of the ModuleId to the Module specifications for {@link ScriptModule}s. This is used for the jboss-modules integration */
    protected final Map<ModuleIdentifier, ModuleSpec> scriptModuleSpecs = new ConcurrentHashMap<ModuleIdentifier, ModuleSpec>();

    /** Map of the ModuleId to the Module specifications for {@link ScriptCompilerPlugin}. This is used for the jboss-modules integration */
    protected final Map<ModuleIdentifier, ModuleSpec> pluginModuleSpecs = new ConcurrentHashMap<ModuleIdentifier, ModuleSpec>();

    protected final Set<ScriptCompilerPluginSpec> pluginSpecs;
    protected final List<ScriptCompiler> compilers = new ArrayList<ScriptCompiler>();

    protected final Set<ScriptModuleListener> listeners =
        Collections.newSetFromMap(new ConcurrentHashMap<ScriptModuleListener, Boolean>());

    protected final JBossModuleLoader jbossModuleLoader;

    protected ScriptModuleLoader(final Set<ScriptCompilerPluginSpec> pluginSpecs,
            final Map<ScriptArchivePoller, Integer> pollers,
            final Set<ScriptModuleListener> listeners,
            final ScheduledExecutorService pollerThreadPool) throws ModuleLoadException {
        this.pluginSpecs = Objects.requireNonNull(pluginSpecs);
        ModuleFinder moduleFinder = new ModuleFinder() {
            @Override
            public ModuleSpec findModule(ModuleIdentifier moduleIdentifier, ModuleLoader delegateLoader) throws ModuleLoadException {
                // check all of the different maps that we store precurser module artifacts in
                ModuleSpec moduleSpec = scriptModuleSpecs.get(moduleIdentifier);
                if (moduleSpec == null) {
                    moduleSpec = pluginModuleSpecs.get(moduleIdentifier);
                }
                return moduleSpec;
            }
        };
        this.jbossModuleLoader = new JBossModuleLoader(new ModuleFinder[] {moduleFinder});
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

    public synchronized Set<ScriptModule> updateScriptArchives(Set<? extends ScriptArchive> archives)  {
        Objects.requireNonNull(archives);
        List<ModuleIdentifier> moduleIds = new ArrayList<ModuleIdentifier>(archives.size());
        // setup the precursor artifacts so jboss-modules can find them.
        for (ScriptArchive scriptArchive : archives) {
            ModuleSpec moduleSpec;
            try {
                moduleSpec = createModuleSpec(scriptArchive);
            } catch (ModuleLoadException e) {
                // TODO: add real logging.
                Module.getModuleLogger().trace(e, "Exception loading archive " +
                    scriptArchive.getModuleSpec().getModuleId());
                continue;
            }
            ModuleIdentifier moduleIdentifier = moduleSpec.getModuleIdentifier();
            moduleIds.add(moduleIdentifier);
            ModuleSpec replacedSpec = scriptModuleSpecs.put(moduleIdentifier, moduleSpec);
            if (replacedSpec != null) {
                // unload the module or jboss won't reload it
                Module replacedModule = jbossModuleLoader.findLoadedModule(moduleIdentifier);
                jbossModuleLoader.unloadModule(replacedModule);
            }
        }
        // the graphs is now wired up. compile the new modules.
        LinkedHashSet<ScriptModule> scriptModules = new LinkedHashSet<ScriptModule>(archives.size());
        for (ModuleIdentifier moduleId : moduleIds) {
            String scriptModuleId = moduleId.getName();
            Module module;
            try {
                module = jbossModuleLoader.loadModule(moduleId);
                compileModule(module);
            } catch (Exception e) {
                // TODO: unfortunately, jboss-modules doesn't provide the ability to roll-back the change.
                // this leaves us in a state in which any subsequent attempts to link to this module will
                // fail even though the previous version worked. Remove the script module from the local caches in order
                // to stay consistent with the jboss moduleloader because it was unloaded from the jboss module loader above.
                // put rollback logic here once we have a solution
                Module.getModuleLogger().trace(e, "Exception loading module " + moduleId);
                removeScriptModule(scriptModuleId);
                continue;
            }
            JBossScriptModule scriptModule = new JBossScriptModule(scriptModuleId, module);
            scriptModules.add(scriptModule);
            ScriptModule oldModule = loadedScriptModules.put(scriptModuleId, scriptModule);
            notifyModuleUpdate(scriptModule, oldModule);
        }
        // TODO: re-link the dependents of the newly loaded modules. as it stands, even the recently added
        // modules may be linked to old versions of their dependencies depending on the order in which they were
        // compiled.
        return scriptModules;
    }

    /**
     * Create a ModuleSpec artifact which is used to prepare a {@link ScriptArchive} for loading.
     */
    protected synchronized ModuleSpec createModuleSpec(ScriptArchive scriptArchive) throws ModuleLoadException {
        ModuleIdentifier moduleId = ModuleUtils.getModuleId(scriptArchive);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, scriptArchive);
        ModuleSpec moduleSpec = moduleSpecBuilder.create();
        return moduleSpec;
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
        if (moduleClassLoader instanceof ScriptModuleClassLoader) {
            ScriptModuleClassLoader scriptModuleClassLoader = (ScriptModuleClassLoader)moduleClassLoader;
            ScriptArchive scriptArchive = scriptModuleClassLoader.getScriptArchive();
            ScriptCompiler compiler = findCompiler(scriptArchive);
            if (compiler != null) {
                compiler.compile(scriptArchive, scriptModuleClassLoader);
            }
        }
    }

    /**
     * Add a language plugin to this module
     * @param pluginSpec
     * @throws ModuleLoadException
     */
    public void addCompilerPlugin(ScriptCompilerPluginSpec pluginSpec) throws ModuleLoadException  {
        Objects.requireNonNull(pluginSpec, "pluginSpec");
        ModuleIdentifier moduleId = ModuleUtils.getModuleId(pluginSpec);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, pluginSpec);
        ModuleSpec moduleSpec = moduleSpecBuilder.create();
        pluginModuleSpecs.put(moduleId, moduleSpec);

        // spin up the module, and get the compiled classes from it's classloader
        String providerClassName = pluginSpec.getPluginClassName();
        if (providerClassName != null) {
            Module pluginModule = jbossModuleLoader.loadModule(moduleId);
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

    public synchronized void removeScriptModule(String moduleId) {
        ModuleIdentifier moduleIdentifier = ModuleIdentifier.create(moduleId);
        scriptModuleSpecs.remove(moduleIdentifier);
        Module loadedModule = jbossModuleLoader.findLoadedModule(moduleIdentifier);
        if (loadedModule != null) {
            jbossModuleLoader.unloadModule(loadedModule);
        }
        ScriptModule oldScriptModule = loadedScriptModules.remove(moduleId);
        if (oldScriptModule != null) {
            notifyModuleUpdate(null, oldScriptModule);
        }
    }

    @Nullable
    public ScriptModule getScriptModule(String scriptModuleId) {
        return loadedScriptModules.get(scriptModuleId);
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
                pollForUpdates(poller);
            }
        },
        0, pollInterval, TimeUnit.SECONDS);
        pollerContext.future = future;
        return true;
    }

    protected void pollForUpdates(ScriptArchivePoller poller) {
        ArchivePollerContext context = pollerContexts.get(poller);
        if (context == null) {
            return;
        }
        synchronized (context) {
            long now = System.currentTimeMillis();
            PollResult pollResult;
            try {
                pollResult = poller.poll(context.lastPollTime);
                context.lastPollTime = now;
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
                for (String moduleId : deletedModuleIds) {
                    removeScriptModule(moduleId);
                }
            }
        }
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
}
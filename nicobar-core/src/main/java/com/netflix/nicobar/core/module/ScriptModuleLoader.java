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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

import org.apache.commons.io.FileUtils;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
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
 * @author Aaron Tull
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
        private Path compilationRootDir;
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
         *        e.g. Specifying com/foo/bar/baz implies that all classes in packages
         *        named com.foo.bar.baz.* will be visible to loaded modules.
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

        public ScriptModuleLoader build() throws ModuleLoadException, IOException {
            if (compilationRootDir == null) {
                compilationRootDir = Files.createTempDirectory("ScriptModuleLoader");
            }

            return new ScriptModuleLoader(pluginSpecs, appClassLoader, paths, listeners, compilationRootDir);
        }
    }

    /** Map of script ModuleId to the loaded ScriptModules */
    protected final Map<ModuleId, ScriptModule> loadedScriptModules = new ConcurrentHashMap<ModuleId, ScriptModule>();
    protected final Map<String, ClassLoader> compilerClassLoaders = new ConcurrentHashMap<String, ClassLoader>();
    protected final Set<ScriptCompilerPluginSpec> pluginSpecs;
    protected final ClassLoader appClassLoader;
    protected final Set<String> appPackagePaths;
    protected final List<ScriptArchiveCompiler> compilers = new ArrayList<ScriptArchiveCompiler>();
    protected final Path compilationRootDir;

    protected final Set<ScriptModuleListener> listeners =
        Collections.newSetFromMap(new ConcurrentHashMap<ScriptModuleListener, Boolean>());

    protected final JBossModuleLoader jbossModuleLoader;

    protected ScriptModuleLoader(final Set<ScriptCompilerPluginSpec> pluginSpecs,
            final ClassLoader appClassLoader,
            final Set<String> appPackagePaths,
            final Set<ScriptModuleListener> listeners,
            final Path compilationRootDir) throws ModuleLoadException {
        this.pluginSpecs = Objects.requireNonNull(pluginSpecs);
        this.appClassLoader = Objects.requireNonNull(appClassLoader);
        this.appPackagePaths = Objects.requireNonNull(appPackagePaths);
        this.jbossModuleLoader = new JBossModuleLoader();
        for (ScriptCompilerPluginSpec pluginSpec : pluginSpecs) {
            addCompilerPlugin(pluginSpec);
        }
        addListeners(Objects.requireNonNull(listeners));
        this.compilationRootDir = compilationRootDir;
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
    public synchronized void updateScriptArchives(Set<? extends ScriptArchive> candidateArchives)  {
        Objects.requireNonNull(candidateArchives);
        long updateNumber = System.currentTimeMillis();

        // map script module id to archive to be compiled
        Map<ModuleId, ScriptArchive> archivesToCompile = new HashMap<ModuleId, ScriptArchive>(candidateArchives.size()*2);

        // create an updated mapping of the scriptModuleId to latest revisionId including the yet-to-be-compiled archives
        Map<ModuleId, ModuleIdentifier> oldRevisionIdMap = jbossModuleLoader.getLatestRevisionIds();
        Map<ModuleId, ModuleIdentifier> updatedRevisionIdMap = new HashMap<ModuleId, ModuleIdentifier>((oldRevisionIdMap.size()+candidateArchives.size())*2);
        updatedRevisionIdMap.putAll(oldRevisionIdMap);

        // Map of the scriptModuleId to it's updated set of dependencies
        Map<ModuleId, Set<ModuleId>> archiveDependencies = new HashMap<ModuleId, Set<ModuleId>>();
        for (ScriptArchive scriptArchive : candidateArchives) {
            ModuleId scriptModuleId = scriptArchive.getModuleSpec().getModuleId();

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
        DirectedGraph<ModuleId, DefaultEdge> candidateGraph = jbossModuleLoader.getModuleNameGraph();
        GraphUtils.swapVertices(candidateGraph, archiveDependencies);

        // iterate over the graph in reverse dependency order
        Set<ModuleId> leaves = GraphUtils.getLeafVertices(candidateGraph);
        while (!leaves.isEmpty()) {
            for (ModuleId scriptModuleId : leaves) {
                ScriptArchive scriptArchive = archivesToCompile.get(scriptModuleId);
                if (scriptArchive == null) {
                    continue;
                }
                ModuleSpec moduleSpec;
                ModuleIdentifier candidateRevisionId = updatedRevisionIdMap.get(scriptModuleId);
                Path modulePath = Paths.get(candidateRevisionId.toString());
                final Path moduleCompilationRoot = compilationRootDir.resolve(modulePath);
                FileUtils.deleteQuietly(moduleCompilationRoot.toFile());
                try {
                    Files.createDirectories(moduleCompilationRoot);
                } catch (IOException ioe) {
                    notifyArchiveRejected(scriptArchive, ArchiveRejectedReason.ARCHIVE_IO_EXCEPTION, ioe);
                }

                try {
                   moduleSpec = createModuleSpec(scriptArchive, candidateRevisionId, updatedRevisionIdMap, moduleCompilationRoot);
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
                    compileModule(jbossModule, moduleCompilationRoot);

                    // Now refresh the resource loaders for this module, and load the set of
                    // compiled classes and populate into the module's local class cache.
                    jbossModuleLoader.rescanModule(jbossModule);

                    final Set<String> classesToLoad = new LinkedHashSet<String>();
                    Files.walkFileTree(moduleCompilationRoot, new SimpleFileVisitor<Path>() {
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String relativePath = moduleCompilationRoot.relativize(file).toString();
                            if (relativePath.endsWith(".class")) {
                                String className = relativePath.replaceAll(".class", "").replace("/", ".");
                                classesToLoad.add(className);
                            }
                            return FileVisitResult.CONTINUE;
                        };
                    });
                    for (String loadClass: classesToLoad) {
                        Class<?> loadedClass = jbossModule.getClassLoader().loadClassLocal(loadClass, true);
                        if (loadedClass == null)
                            throw new ScriptCompilationException("Unable to load compiled class: " + loadClass);
                    }
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
                Set<ModuleId> dependents = GraphUtils.getIncomingVertices(candidateGraph, scriptModuleId);
                for (ModuleId dependentScriptModuleId : dependents) {
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
     * Create a JBoss module spec for an about to be created script module.
     * @param archive the script archive being converted to a module.
     * @param moduleId the JBoss module identifier.
     * @param moduleIdMap a map of loaded script module IDs to jboss module identifiers
     * @param moduleCompilationRoot a path to a directory that will hold compiled classes for this module.
     * @throws ModuleLoadException
     */
    protected ModuleSpec createModuleSpec(ScriptArchive archive,
            ModuleIdentifier moduleId,
            Map<ModuleId, ModuleIdentifier> moduleIdMap,
            Path moduleCompilationRoot) throws ModuleLoadException {
        ScriptModuleSpec archiveSpec = archive.getModuleSpec();
        // create the jboss module pre-cursor artifact
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);

        JBossModuleUtils.populateModuleSpecWithResources(moduleSpecBuilder, archive);
        JBossModuleUtils.populateModuleSpecWithCoreDependencies(moduleSpecBuilder, archive);
        JBossModuleUtils.populateModuleSpecWithAppImports(moduleSpecBuilder,
                appClassLoader, archiveSpec.getAppImportFilterPaths() == null ? appPackagePaths : archiveSpec.getAppImportFilterPaths());
        // Allow compiled class files to fetched as resources later on.
        JBossModuleUtils.populateModuleSpecWithCompilationRoot(moduleSpecBuilder, moduleCompilationRoot);

        // Populate the modulespec with the scriptArchive dependencies
        for (ModuleId dependencyModuleId : archiveSpec.getModuleDependencies()) {
            ScriptModule dependencyModule = getScriptModule(dependencyModuleId);
            Set<String> exportPaths = dependencyModule.getSourceArchive().getModuleSpec().getModuleExportFilterPaths();

            JBossModuleUtils.populateModuleSpecWithModuleDependency(moduleSpecBuilder,
                archiveSpec.getModuleImportFilterPaths(), exportPaths, moduleIdMap.get(dependencyModuleId));
        }

        return moduleSpecBuilder.create();
    }

    /**
     * Compiles and links the scripts within the module by locating the correct compiler
     * and delegating the compilation. the classes will be loaded into the module's classloader
     * upon completion.
     * @param module module to be compiled
     * @param moduleCompilationRoot the directory to store compiled classes in.
     */
    protected void compileModule(Module module, Path moduleCompilationRoot) throws ScriptCompilationException, IOException {
        // compile the script archive for the module, and inject the resultant classes into
        // the ModuleClassLoader
        ModuleClassLoader moduleClassLoader = module.getClassLoader();
        if (moduleClassLoader instanceof JBossModuleClassLoader) {
            JBossModuleClassLoader jBossModuleClassLoader = (JBossModuleClassLoader)moduleClassLoader;
            ScriptArchive scriptArchive = jBossModuleClassLoader.getScriptArchive();
            List<ScriptArchiveCompiler> candidateCompilers = findCompilers(scriptArchive);
            if (candidateCompilers.size() == 0) {
                throw new ScriptCompilationException("Could not find a suitable compiler for this archive.");
            }

            // Compile iteratively
            for (ScriptArchiveCompiler compiler: candidateCompilers) {
                compiler.compile(scriptArchive, jBossModuleClassLoader, moduleCompilationRoot);
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
        Map<ModuleId, ModuleIdentifier> latestRevisionIds = jbossModuleLoader.getLatestRevisionIds();
        JBossModuleUtils.populateCompilerModuleSpec(moduleSpecBuilder, pluginSpec, latestRevisionIds);
        // Add app package dependencies, while blocking them from leaking (being exported) to downstream modules
        // TODO: We expose the full set of app packages to the compiler too.
        // Maybe more control over what is exposed is needed here.
        JBossModuleUtils.populateModuleSpecWithAppImports(moduleSpecBuilder, appClassLoader, appPackagePaths);
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

            // Save classloader away, in case clients would like access to compiler plugin's classes.
            compilerClassLoaders.put(pluginSpec.getPluginId(), pluginModule.getClassLoader());
        }
    }

    /**
     * Remove a module from being served by this instance. Note that any
     * instances of the module cached outside of this module loader will remain
     * un-effected and will continue to operate.
     */
    public synchronized void removeScriptModule(ModuleId scriptModuleId) {
        jbossModuleLoader.unloadAllModuleRevision(scriptModuleId.toString());
        ScriptModule oldScriptModule = loadedScriptModules.remove(scriptModuleId);
        if (oldScriptModule != null) {
            notifyModuleUpdate(null, oldScriptModule);
        }
    }

    @Nullable
    public ScriptModule getScriptModule(String scriptModuleId) {
        return loadedScriptModules.get(ModuleId.fromString(scriptModuleId));
    }

    @Nullable
    public ClassLoader getCompilerPluginClassLoader(String pluginModuleId) {
        return compilerClassLoaders.get(pluginModuleId);
    }

    @Nullable
    public ScriptModule getScriptModule(ModuleId scriptModuleId) {
        return loadedScriptModules.get(scriptModuleId);
    }

    /**
     * Get a view of the loaded script modules
     * @return immutable view of the Map that retains the script modules. Map ModuleId the loaded ScriptModule
     */
    public Map<ModuleId, ScriptModule> getAllScriptModules() {
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
    protected List<ScriptArchiveCompiler> findCompilers(ScriptArchive archive) {
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
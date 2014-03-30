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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.annotation.Nullable;

import org.jboss.modules.ConcreteModuleSpec;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.module.GraphUtils;

/**
 * Specialization of the {@link ModuleLoader} class which exposes
 * many of the protected methods for convenience.
 *
 * The {@link ModuleSpec} repository is backed by a Map contained within
 * this class so that ModuleSpecs can be directly injected instead of
 * located via a {@link ModuleFinder}.
 *
 * This class exposes operations for different revisions of a given module
 * using the {@link ModuleIdentifier} "slot" as a revision holder.
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class JBossModuleLoader extends ModuleLoader {
    /** Comparator used for the module revision sorting */
    protected final static Comparator<ModuleIdentifier> MODULE_ID_COMPARATOR = new Comparator<ModuleIdentifier>() {
        @Override
        public int compare(ModuleIdentifier id1, ModuleIdentifier id2) {
            if (id1 == null && id2 == null || id1 == id2) {
                return 0;
            } else if (id1 == null) {
                return -1;
            } else if (id2 == null) {
                return 1;
            }
            int result = id1.getName().compareTo(id2.getName());
            if (result == 0) {
                // descending order
                result = (int)(Long.parseLong(id2.getSlot()) - Long.parseLong(id1.getSlot()));
            }
            return result;
        }
    };

    /** Module Spec repo.  Map of the revisionId to the Module specifications. */
    protected final SortedMap<ModuleIdentifier, ModuleSpec> moduleSpecs;

    /**
     * Construct a instance with an empty module spec repository.
     */
    public JBossModuleLoader() {
        this(new ConcurrentSkipListMap<ModuleIdentifier, ModuleSpec>(MODULE_ID_COMPARATOR));
    }

    private JBossModuleLoader(final SortedMap<ModuleIdentifier, ModuleSpec> moduleSpecs) {
        // create a finder that is backed by the local module spec map
        super(new ModuleFinder[] { new ModuleFinder() {
            @Override
            public ModuleSpec findModule(ModuleIdentifier revisionId, ModuleLoader delegateLoader) throws ModuleLoadException {
                return moduleSpecs.get(revisionId);
            }
        }});
        this.moduleSpecs = Objects.requireNonNull(moduleSpecs);
    }

    /**
     * Unload all module revisions with the give script module id
     */
    public void unloadAllModuleRevision(String scriptModuleId) {
        for (ModuleIdentifier revisionId : getAllRevisionIds(scriptModuleId)) {
            if (revisionId.getName().equals(scriptModuleId)) {
                unloadModule(revisionId);
            }
        }
    }
    /**
     * Unload the given revision of a module from the local repository.
     * @param revisionId {@link ModuleIdentifier} of the revision to unload
     */
    public void unloadModule(ModuleIdentifier revisionId) {
        Objects.requireNonNull(revisionId, "revisionId");
        Module module = findLoadedModule(revisionId);
        if (module != null) {
            unloadModule(module);
        }
    }

    /**
     * Unload a module from the local repository. Equivalent to
     * {@link #unloadModuleLocal(Module)}.
     * @param module the module to unload.
     */
    public void unloadModule(Module module) {
        Objects.requireNonNull(module, "module");
        unloadModuleLocal(module);
        moduleSpecs.remove(module.getIdentifier());
    }

    /**
     * Find a module from the local repository. Equivalent to {@link #findLoadedModuleLocal(ModuleIdentifier)}.
     * @param revisionId revisionId of the module to find
     * @return the loaded module or null if it doesn't exist
     */
    @Nullable
    public Module findLoadedModule(ModuleIdentifier revisionId) {
        Objects.requireNonNull(revisionId, "revisionId");
        return findLoadedModuleLocal(revisionId);
    }

    /**
     * Add a {@link ModuleSpec} to the internal repository making it ready to load. Note, this doesn't
     * actually load the {@link Module}.
     * @see #loadModule(ModuleIdentifier)
     *
     * @param moduleSpec spec to add
     * @return true if the instance was added
     */
    @Nullable
    public boolean addModuleSpec(ModuleSpec moduleSpec) {
        Objects.requireNonNull(moduleSpec, "moduleSpec");
        ModuleIdentifier revisionId = moduleSpec.getModuleIdentifier();
        boolean available = !moduleSpecs.containsKey(revisionId);
        if (available) {
            moduleSpecs.put(revisionId, moduleSpec);
        }
        return available;
    }

    /**
     * Get a {@link ModuleSpec} that was added to this instance
     * @param revisionId id to search for
     * @return the instance associated with the given revisionIds
     */
    @Nullable
    public ModuleSpec getModuleSpec(ModuleIdentifier revisionId) {
        Objects.requireNonNull(revisionId, "revisionId");
        return moduleSpecs.get(revisionId);
    }

    /**
     * Find the highest revision for the given scriptModuleId
     * @param scriptModuleId name to search for
     * @return the highest revision number or -1 if no revisions exist
     */
    public long getLatestRevisionNumber(ModuleId scriptModuleId) {
        Objects.requireNonNull(scriptModuleId, "scriptModuleId");
        ModuleIdentifier searchIdentifier = JBossModuleUtils.createRevisionId(scriptModuleId, 0);
        SortedMap<ModuleIdentifier,ModuleSpec> tailMap = moduleSpecs.tailMap(searchIdentifier);
        long revisionNumber = -1;
        for (ModuleIdentifier revisionId : tailMap.keySet()) {
            if (revisionId.getName().equals(scriptModuleId.toString())) {
                revisionNumber = getRevisionNumber(revisionId);
            } else {
                break;
            }
        }
        return revisionNumber;
    }

    /**
     * Find the highest revision for the given scriptModuleId
     * @param scriptModuleId name to search for
     * @return the revisionId for the highest revision number. Revision defaults to 0 if it doesn't exist.
     */
    public ModuleIdentifier getLatestRevisionId(ModuleId scriptModuleId) {
        Objects.requireNonNull(scriptModuleId, "scriptModuleId");
        long latestRevision = getLatestRevisionNumber(scriptModuleId);
        if (latestRevision < 0) {
            latestRevision = 0;
        }
        return JBossModuleUtils.createRevisionId(scriptModuleId,latestRevision);
    }

    /**
     * Find all module revisionIds with a common name
     */
    public Set<ModuleIdentifier> getAllRevisionIds(String scriptModuleId) {
        Objects.requireNonNull(scriptModuleId, "scriptModuleId");
        Set<ModuleIdentifier> revisionIds = new LinkedHashSet<ModuleIdentifier>();
        for (ModuleIdentifier revisionId : moduleSpecs.keySet()) {
            if (revisionId.getName().equals(scriptModuleId)) {
                revisionIds.add(revisionId);
            }
        }
        return Collections.unmodifiableSet(revisionIds);
    }

    /**
     * Get a map of the the moduleId to {@link ModuleIdentifier} with the highest revision
     * @return immutable snapshot of the latest module revisionIds
     */
    public Map<ModuleId, ModuleIdentifier> getLatestRevisionIds() {
        Map<ModuleId, ModuleIdentifier> nameToIdMap = new HashMap<ModuleId, ModuleIdentifier>(moduleSpecs.size()*2);
        for (Entry<ModuleIdentifier, ModuleSpec> entry : moduleSpecs.entrySet()) {
            ModuleId scriptModuleId = ModuleId.fromString(entry.getKey().getName());
            ModuleSpec moduleSpec = entry.getValue();
            nameToIdMap.put(scriptModuleId, moduleSpec.getModuleIdentifier());
        }
        // reserve the ability to convert this to an immutable view later
        return Collections.unmodifiableMap(nameToIdMap);
    }

    /**
     * Helper method to parse out the revision number from a {@link ModuleIdentifier}
     * @return revision number or -1 if it couldn't be parsed
     */
    public static long getRevisionNumber(ModuleIdentifier revisionId) {
        int revision;
        try {
            revision = Integer.parseInt(revisionId.getSlot());
        } catch (NumberFormatException nf) {
            revision = -1;
        }
        return revision;
    }

    /**
     * Construct the Module dependency graph of a module loader where each vertex is the module name
     * @return a mutable snapshot of the underlying dependency
     */
    public DirectedGraph<ModuleId, DefaultEdge> getModuleNameGraph() {
        SimpleDirectedGraph<ModuleId, DefaultEdge> graph = new SimpleDirectedGraph<ModuleId, DefaultEdge>(DefaultEdge.class);
        Map<ModuleId, ModuleIdentifier> moduleIdentifiers = getLatestRevisionIds();
        GraphUtils.addAllVertices(graph, moduleIdentifiers.keySet());
        for (Entry<ModuleId, ModuleIdentifier> entry : moduleIdentifiers.entrySet()) {
            ModuleId scriptModuleId = entry.getKey();
            ModuleIdentifier revisionID = entry.getValue();
            ModuleSpec moduleSpec = moduleSpecs.get(revisionID);
            Set<ModuleId> dependencyNames = getDependencyScriptModuleIds(moduleSpec);
            GraphUtils.addOutgoingEdges(graph, scriptModuleId, dependencyNames);
        }
        return graph;
    }

    /**
     * Extract the Module dependencies for the given module in the form
     * of ScriptModule ids.
     */
    public static Set<ModuleId> getDependencyScriptModuleIds(ModuleSpec moduleSpec) {
        Objects.requireNonNull(moduleSpec, "moduleSpec");
        if (!(moduleSpec instanceof ConcreteModuleSpec)) {
            throw new IllegalArgumentException("Unsupported ModuleSpec implementation: " + moduleSpec.getClass().getName());
        }
        Set<ModuleId> dependencyNames = new LinkedHashSet<ModuleId>();
        ConcreteModuleSpec concreteSpec = (ConcreteModuleSpec)moduleSpec;
        for (DependencySpec dependencSpec : concreteSpec.getDependencies()) {
            if (dependencSpec instanceof ModuleDependencySpec) {
                ModuleIdentifier revisionId = ((ModuleDependencySpec)dependencSpec).getIdentifier();
                dependencyNames.add(ModuleId.fromString(revisionId.getName()));
            }
        }
        return dependencyNames;
    }
}

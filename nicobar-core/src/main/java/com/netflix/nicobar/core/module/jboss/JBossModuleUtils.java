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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilters;

import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.utils.ClassPathUtils;


/**
 * Utility methods for working with {@link Module}s
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class JBossModuleUtils {
    /** Dependency specification which allows for importing the core library classes */
    public static final DependencySpec NICOBAR_CORE_DEPENDENCY_SPEC;
    /** Dependency specification which allows for importing the core JRE classes */
    public static final DependencySpec JRE_DEPENDENCY_SPEC;
    static {
        // TODO: find a maintainable way to get these values and a better place to store these constants
        Set<String> pathFilter = new HashSet<String>();
        pathFilter.add("com/netflix/nicobar/core");
        pathFilter.add("com/netflix/nicobar/core/archive");
        pathFilter.add("com/netflix/nicobar/core/compile");
        pathFilter.add("com/netflix/nicobar/core/module");
        pathFilter.add("com/netflix/nicobar/core/module/jboss");
        pathFilter.add("com/netflix/nicobar/core/plugin");
        NICOBAR_CORE_DEPENDENCY_SPEC = DependencySpec.createClassLoaderDependencySpec(JBossModuleUtils.class.getClassLoader(), pathFilter);
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        JRE_DEPENDENCY_SPEC = DependencySpec.createClassLoaderDependencySpec(systemClassLoader, ClassPathUtils.getJdkPaths());
    }

    /**
     * Populates a builder with source files, resources, dependencies and properties from the
     * {@link ScriptArchive}
     * @param moduleSpecBuilder builder to populate
     * @param scriptArchive {@link ScriptArchive} to copy from
     * @param latestRevisionIds used to lookup the latest dependencies. see {@link JBossModuleLoader#getLatestRevisionIds()}
     */
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptArchive scriptArchive, Map<ModuleId, ModuleIdentifier> latestRevisionIds) throws ModuleLoadException {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(moduleSpecBuilder, "scriptArchive");
        Objects.requireNonNull(latestRevisionIds, "latestRevisionIds");

        MultiplePathFilterBuilder pathFilterBuilder = PathFilters.multiplePathFilterBuilder(true);
        Set<String> archiveEntryNames = scriptArchive.getArchiveEntryNames();
        pathFilterBuilder.addFilter(PathFilters.in(archiveEntryNames), true);

        // add the root resources and classes to the module. If the root is a directory, all files under the tree
        // are added to the module. If it's a jar, then all files in the jar are added.
        URL url = scriptArchive.getRootUrl();
        if (url != null) {
            File file = Paths.get(url.getPath()).toFile();
            String filePath = file.getPath();
            ResourceLoader rootResourceLoader = null;
            if (file.isDirectory()) {
                rootResourceLoader = ResourceLoaders.createFileResourceLoader(filePath, file);
            } else if (file.getPath().endsWith(".jar")) {
                try {
                    rootResourceLoader = ResourceLoaders.createJarResourceLoader(filePath, new JarFile(file));
                } catch (IOException e) {
                    throw new ModuleLoadException(e);
                }
            }
            if (rootResourceLoader != null) {
                moduleSpecBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(rootResourceLoader, pathFilterBuilder.create()));
            }
        }
        // add dependencies to the module spec
        ScriptModuleSpec scriptModuleSpec = scriptArchive.getModuleSpec();
        Set<ModuleId> dependencies = scriptModuleSpec.getModuleDependencies();
        for (ModuleId scriptModuleId : dependencies) {
            ModuleIdentifier latestIdentifier = latestRevisionIds.get(scriptModuleId);
            if (latestIdentifier == null) {
                latestIdentifier = JBossModuleUtils.createRevisionId(scriptModuleId, 0);
            }
            moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(latestIdentifier, true, false));
        }
        Set<String> compilerPlugins = scriptModuleSpec.getCompilerPluginIds();
        for (String compilerPluginId : compilerPlugins) {
            moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(getPluginModuleId(compilerPluginId), true, false));
        }
        moduleSpecBuilder.addDependency(JRE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(NICOBAR_CORE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());

        // add string based properties from module spec metadata to the module spec being created
        Map<String, Object> archiveMetadata = scriptModuleSpec.getMetadata();
        Map<String, String> stringMetadata = new HashMap<String, String>();
        for (Entry<String, Object> entry: archiveMetadata.entrySet()) {
            if (entry.getValue() instanceof String) {
                stringMetadata.put(entry.getKey(), (String)entry.getValue());
            }
        }
        addPropertiesToSpec(moduleSpecBuilder, stringMetadata);

        // override the default ModuleClassLoader to use our customer classloader
        moduleSpecBuilder.setModuleClassLoaderFactory(JBossModuleClassLoader.createFactory(scriptArchive));
    }

    /**
     * Populates a {@link ModuleSpec} with a dependency on application runtime packages
     * specified as a set of package paths, loaded within the given classloader. This is the
     * primary way that a module gains access to packages defined in the application classloader
     *
     * @param moduleSpecBuilder builder to populate
     * @param classLoader a classloader
     * @param appPackages set of / separated package names (n
     */
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ClassLoader classLoader, Set<String> appPackages)
            throws ModuleLoadException {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(appPackages, "appPackages");

        moduleSpecBuilder.addDependency(DependencySpec.createClassLoaderDependencySpec(classLoader, appPackages));
    }

    /**
     * Populates a {@link ModuleSpec} with runtime resources, dependencies and properties from the
     * {@link ScriptCompilerPluginSpec}
     * Helpful when creating a {@link ModuleSpec} from a ScriptLibPluginSpec
     *
     * @param moduleSpecBuilder builder to populate
     * @param pluginSpec {@link ScriptCompilerPluginSpec} to copy from
     * @param latestRevisionIds used to lookup the latest dependencies. see {@link JBossModuleLoader#getLatestRevisionIds()}
     */
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptCompilerPluginSpec pluginSpec, Map<ModuleId, ModuleIdentifier> latestRevisionIds) throws ModuleLoadException {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(pluginSpec, "pluginSpec");
        Objects.requireNonNull(latestRevisionIds, "latestRevisionIds");
        Set<Path> pluginRuntime = pluginSpec.getRuntimeResources();
        for (Path resourcePath : pluginRuntime) {
            File file = resourcePath.toFile();
            String pathString = resourcePath.toString();
            ResourceLoader rootResourceLoader = null;
            if (file.isDirectory()) {
                rootResourceLoader = ResourceLoaders.createFileResourceLoader(pathString, file);
            } else if (pathString.endsWith(".jar")) {
                try {
                    rootResourceLoader = ResourceLoaders.createJarResourceLoader(pathString, new JarFile(file));
                } catch (IOException e) {
                    throw new ModuleLoadException(e);
                }
            }
            if (rootResourceLoader != null) {
                moduleSpecBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(rootResourceLoader));
            }
        }
        moduleSpecBuilder.addDependency(JRE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(NICOBAR_CORE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        // add dependencies to the module spec
        Set<ModuleId> dependencies = pluginSpec.getModuleDependencies();
        for (ModuleId scriptModuleId : dependencies) {
            ModuleIdentifier latestIdentifier = latestRevisionIds.get(scriptModuleId);
            if (latestIdentifier == null) {
                throw new ModuleLoadException("Cannot find dependent module: " + scriptModuleId);
            }

            moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(latestIdentifier, true, false));
        }

        Map<String, String> pluginMetadata = pluginSpec.getPluginMetadata();
        addPropertiesToSpec(moduleSpecBuilder, pluginMetadata);
    }

    /**
     * Add properties to the {@link ModuleSpec}
     *
     * @param moduleSpecBuilder builder to populate
     * @param properties properties to add
     */
    public static void addPropertiesToSpec(ModuleSpec.Builder moduleSpecBuilder, Map<String, String> properties) {
        for (Entry<String, String> entry : properties.entrySet()) {
            moduleSpecBuilder.addProperty(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Create the {@link ModuleIdentifier} for the given ScriptCompilerPluginSpec
     */
    public static ModuleIdentifier getPluginModuleId(ScriptCompilerPluginSpec pluginSpec) {
        return getPluginModuleId(pluginSpec.getPluginId());
    }

    /**
     * Create the {@link ModuleIdentifier} for the given ScriptCompilerPluginSpec ID
     */
    public static ModuleIdentifier getPluginModuleId(String pluginId) {
        return ModuleIdentifier.create(pluginId);
    }
    /**
     * Helper method to create a revisionId in a consistent manner
     */
    public static ModuleIdentifier createRevisionId(ModuleId scriptModuleId, long revisionNumber) {
        Objects.requireNonNull(scriptModuleId, "scriptModuleId");
        return ModuleIdentifier.create(scriptModuleId.toString(), Long.toString(revisionNumber));
    }
}

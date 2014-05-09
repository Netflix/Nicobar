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

import javax.annotation.Nullable;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilter;
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
 * @author Aaron Tull
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
     * Populates a module spec builder with source files, resources and properties from the {@link ScriptArchive}
     *
     * @param moduleSpecBuilder builder to populate
     * @param scriptArchive {@link ScriptArchive} to copy from
     */
    public static void populateModuleSpecWithResources(ModuleSpec.Builder moduleSpecBuilder, ScriptArchive scriptArchive) throws ModuleLoadException {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(scriptArchive, "scriptArchive");

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
     * Populates a module spec builder with core dependencies on JRE, Nicobar, itself, and compiler plugins.
     *
     * @param moduleSpecBuilder builder to populate
     * @param scriptArchive {@link ScriptArchive} to copy from
     */
    public static void populateModuleSpecWithCoreDependencies(ModuleSpec.Builder moduleSpecBuilder, ScriptArchive scriptArchive) throws ModuleLoadException {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(scriptArchive, "scriptArchive");

        Set<String> compilerPlugins = scriptArchive.getModuleSpec().getCompilerPluginIds();
        for (String compilerPluginId : compilerPlugins) {
            moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(getPluginModuleId(compilerPluginId), false));
        }
        moduleSpecBuilder.addDependency(JRE_DEPENDENCY_SPEC);
        // TODO: Why does a module need a dependency on Nicobar itself?
        moduleSpecBuilder.addDependency(NICOBAR_CORE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());
    }

    /**
     * Populate a module spec builder with a dependencies on other modules.
     * @param moduleSpecBuilder builder to populate
     * @param moduleImportFilterPaths paths valid for importing into the module being built.
     *                                Can be null or empty to indicate that no filters should be applied.
     * @param dependencyExportFilterPaths export paths for the dependency being linked
     * @param dependentModuleIdentifier used to lookup the latest dependencies. see {@link JBossModuleLoader#getLatestRevisionIds()}
     */
    public static void populateModuleSpecWithModuleDependency(ModuleSpec.Builder moduleSpecBuilder,
            @Nullable Set<String> moduleImportFilterPaths,
            @Nullable Set<String> dependencyExportFilterPaths,
            ModuleIdentifier dependentModuleIdentifier) {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        PathFilter moduleImportFilters = buildFilters(moduleImportFilterPaths, false);
        PathFilter dependencyExportFilters = buildFilters(dependencyExportFilterPaths, false);
        PathFilter importFilters = PathFilters.all(dependencyExportFilters, moduleImportFilters);
        moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(importFilters, dependencyExportFilters, null, dependentModuleIdentifier, false));
    }

    /**
     * Populates a builder with a {@link ResourceLoaderSpec} to a filesystem resource root.
     * {@link ScriptArchive}
     * @param moduleSpecBuilder builder to populate
     * @param compilationRoot a path to the compilation resource root directory
     */
    public static void populateModuleSpecWithCompilationRoot(ModuleSpec.Builder moduleSpecBuilder, Path compilationRoot) {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(compilationRoot, "resourceRoot");
        ResourceLoader resourceLoader = ResourceLoaders.createFileResourceLoader(compilationRoot.toString(), compilationRoot.toFile());
        moduleSpecBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader));
    }

    /**
     * Populates a {@link ModuleSpec} with a dependency on application runtime packages
     * specified as a set of package paths, loaded within the given classloader. This is the
     * primary way that a module gains access to packages defined in the application classloader
     *
     * @param moduleSpecBuilder builder to populate
     * @param appClassLoader a classloader the application classloader.
     * @param appPackages the global set of application package paths.
     * @param importFilterPaths a set of imports to restrict this module to,
     *        can be null to indicate that no filters should be applied (accept all),
     *        can be empty to indicate that everything should be filtered (reject all).
     * @param exportFilterPaths a set of app exports to restrict this module to,
     *        can be null to indicate that no filters should be applied (accept all),
     *        can be empty to indicate that everything should be filtered (reject all).
     */
    public static void populateModuleSpecWithAppImports(ModuleSpec.Builder moduleSpecBuilder,
            ClassLoader appClassLoader,
            Set<String> appPackages,
            @Nullable Set<String> importFilterPaths,
            @Nullable Set<String> exportFilterPaths) {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(appClassLoader, "classLoader");
        PathFilter moduleImportFilters = buildFilters(importFilterPaths, false);
        PathFilter moduleExportFilters = buildFilters(exportFilterPaths, false);
        populateAppPackageDependency(moduleSpecBuilder, appClassLoader, appPackages, moduleImportFilters, moduleExportFilters);
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
    public static void populateCompilerModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptCompilerPluginSpec pluginSpec, Map<ModuleId, ModuleIdentifier> latestRevisionIds) throws ModuleLoadException {
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

    /**
     * Build a PathFilter for a set of filter paths
     *
     * @param filterPaths the set of paths to filter on.
     *        Can be null to indicate that no filters should be applied (accept all),
     *        can be empty to indicate that everything should be filtered (reject all).
     * @param failedMatchValue the value the PathFilter returns when a path does not match.
     * @return a PathFilter.
     */
    private static PathFilter buildFilters(Set<String> filterPaths, boolean failedMatchValue) {
        if (filterPaths == null)
            return PathFilters.acceptAll();
        else if (filterPaths.isEmpty()) {
            return PathFilters.rejectAll();
        } else {
            MultiplePathFilterBuilder builder = PathFilters.multiplePathFilterBuilder(failedMatchValue);
            for (String importPathGlob : filterPaths)
                builder.addFilter(PathFilters.match(importPathGlob), !failedMatchValue);
            return builder.create();
        }
    }

    /**
     * Populate a module spec with a dependency spec on the app classloader.
     *
     * @param moduleSpecBuilder the builder for the module spec being constructed.
     * @param classLoader the classloader on which this module is dependent.
     * @param appPackages the global set of app package paths exposed by the classloader.
     * @param moduleImportFilters the set of package paths to filter this module to.
     */
    private static void populateAppPackageDependency(ModuleSpec.Builder moduleSpecBuilder, ClassLoader classLoader, Set<String> appPackages, PathFilter moduleImportFilters, PathFilter moduleExportFilters) {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(appPackages, "appPackages");
        DependencySpec dependencySpec = DependencySpec.createClassLoaderDependencySpec(moduleImportFilters, moduleExportFilters, classLoader, appPackages);
        moduleSpecBuilder.addDependency(dependencySpec);
    }
}

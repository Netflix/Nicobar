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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
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

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchiveDescriptor;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;


/**
 * Utility methods for working with {@link Module}s
 *
 * @author James Kojo
 */
public class ModuleUtils {
    /** Dependency specification which allows for importing the core library classes */
    public static final DependencySpec SCRIPTLIB_CORE_DEPENDENCY_SPEC;
    /** Dependency specification which allows for importing the core JRE classes */
    public static final DependencySpec JRE_DEPENDENCY_SPEC;
    static {
        // TODO: find a maintainable way to get these values and a better place to store these constants
        Set<String> pathFilter = new HashSet<String>();
        pathFilter.add("com/netflix/scriptlib/core");
        pathFilter.add("com/netflix/scriptlib/core/archive");
        pathFilter.add("com/netflix/scriptlib/core/compile");
        pathFilter.add("com/netflix/scriptlib/core/module");
        pathFilter.add("com/netflix/scriptlib/core/plugin");
        SCRIPTLIB_CORE_DEPENDENCY_SPEC = DependencySpec.createClassLoaderDependencySpec(ModuleUtils.class.getClassLoader(), pathFilter);
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        JRE_DEPENDENCY_SPEC = DependencySpec.createClassLoaderDependencySpec(systemClassLoader, __JDKPaths.JDK);
    }

    /**
     * Populates a builder with source files, resources, dependencies and properties from the
     * {@link ScriptArchive}
     * @param moduleSpecBuilder builder to populate
     * @param scriptArchive {@link ScriptArchive} to copy from
     */
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptArchive scriptArchive) throws ModuleLoadException {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(moduleSpecBuilder, "scriptArchive");
        MultiplePathFilterBuilder pathFilterBuilder = PathFilters.multiplePathFilterBuilder(true);
        Set<String> archiveEntryNames = scriptArchive.getArchiveEntryNames();
        pathFilterBuilder.addFilter(PathFilters.in(archiveEntryNames), true);

        // add the root resouces and classes to the module. If the root is a directory, all files under the tree
        // are added to the module. If it's a jar, then all files in the jar are added.
        URL url = scriptArchive.getRootUrl();
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
        // add dependencies to the module spec
        ScriptArchiveDescriptor descriptor = scriptArchive.getDescriptor();
        List<String> dependencies = descriptor.getDependencies();
        for (String moduleName : dependencies) {
            moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(ModuleIdentifier.create(moduleName), true, false));
        }
        moduleSpecBuilder.addDependency(JRE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(SCRIPTLIB_CORE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());

        // add properties to the module spec
        Map<String, String> archiveMetadata = descriptor.getArchiveMetadata();
        addPropertiesToSpec(moduleSpecBuilder, archiveMetadata);

        // override the default ModuleClassLoader to use our customer classloader
        moduleSpecBuilder.setModuleClassLoaderFactory(ScriptModuleClassLoader.createFactory(scriptArchive));
    }

    /**

     * Populates a {@link ModuleSpec} with runtime resources, dependencies and properties from the
     * {@link ScriptCompilerPluginSpec}
     * Helpful when creating a {@link ModuleSpec} from a ScriptLibPluginSpec
     *
     * @param moduleSpecBuilder builder to populate
     * @param pluginSpec {@link ScriptCompilerPluginSpec} to copy from
     */
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptCompilerPluginSpec pluginSpec) throws ModuleLoadException {
        Objects.requireNonNull(moduleSpecBuilder, "moduleSpecBuilder");
        Objects.requireNonNull(pluginSpec, "pluginSpec");
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
        moduleSpecBuilder.addDependency(SCRIPTLIB_CORE_DEPENDENCY_SPEC);
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());

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
     * Create the {@link ModuleIdentifier} for the given {@link ScriptArchive}
     */
    public static ModuleIdentifier getModuleId(ScriptArchive archive) {
        return ModuleIdentifier.create(archive.getDescriptor().getArchiveId());
    }

    /**
     * Create the {@link ModuleIdentifier} for the given ScriptCompilerPluginSpec
     */
    public static ModuleIdentifier getModuleId(ScriptCompilerPluginSpec pluginSpec) {
        return ModuleIdentifier.create(pluginSpec.getPluginName());
    }
}

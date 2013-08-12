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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;


/**
 * Utility methods for working with {@link Module}s
 *
 * @author James Kojo
 */
public class ModuleUtils {

    /** class paths of the core classes */
    private static final Set<String> SYSTEM_PATH_FILTER;
    static {
        // TODO: find a maintainable way to get these values
        Set<String> pathFilter = new HashSet<String>();
        pathFilter.add("com/netflix/scriptlib/core");
        pathFilter.add("com/netflix/scriptlib/core/archive");
        pathFilter.add("com/netflix/scriptlib/core/compile");
        pathFilter.add("com/netflix/scriptlib/core/module");
        pathFilter.add("com/netflix/scriptlib/core/plugin");
        SYSTEM_PATH_FILTER = Collections.unmodifiableSet(pathFilter);
    }

    /**
     * populates a builder with source files, resources, dependencies and properties from the
     * {@link ScriptArchive}
     **/
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptArchive scriptArchive) throws ModuleLoadException {
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
        List<String> dependencies = scriptArchive.getDependencies();
        for (String moduleName : dependencies) {
            moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(ModuleIdentifier.create(moduleName), true, false));
        }
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());

        // add properties to the module spec
        Map<String, String> archiveMetadata = scriptArchive.getArchiveMetadata();
        for (Entry<String, String> entry : archiveMetadata.entrySet()) {
            moduleSpecBuilder.addProperty(entry.getKey(), entry.getValue());
        }

        // override the default ModuleClassLoader to use our customer classloader
        moduleSpecBuilder.setModuleClassLoaderFactory(ScriptModuleClassLoader.createFactory(scriptArchive));
        return;
    }

    /**
     * Helping when creating a {@link ModuleSpec} from a ScriptLibPluginSpec
     * populates a builder with source files, resources, dependencies and properties from the
     * {@link ScriptCompilerPluginSpec}
     */
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptCompilerPluginSpec pluginSpec) throws ModuleLoadException {
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

        // allow a subset of the application classloader classes to leak through, such as
        // the script-lib core classes
        moduleSpecBuilder.addDependency(DependencySpec.createSystemDependencySpec(SYSTEM_PATH_FILTER));
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        // add properties to the module spec
        Map<String, String> archiveMetadata = pluginSpec.getPluginMetadata();
        for (Entry<String, String> entry : archiveMetadata.entrySet()) {
            moduleSpecBuilder.addProperty(entry.getKey(), entry.getValue());
        }
        return;
    }
}

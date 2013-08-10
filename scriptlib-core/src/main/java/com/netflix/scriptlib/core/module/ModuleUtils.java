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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarFile;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilters;

import com.netflix.scriptlib.core.archive.ScriptArchive;


/**
 * Utility methods for working with {@link Module}s
 *
 * @author James Kojo
 */
public class ModuleUtils {

    /**
     * populates a builder with source files, resources, dependencies and properties
     * @param moduleId
     * @param scriptArchive
     * @return
     * @throws IOException
     */
    public static void populateModuleSpec(ModuleSpec.Builder moduleSpecBuilder, ScriptArchive scriptArchive) throws IOException {
        // add the source and resource files to the module spec
        String archiveName = scriptArchive.getArchiveName();

        MultiplePathFilterBuilder pathFilterBuilder = PathFilters.multiplePathFilterBuilder(true);
        Set<String> archiveEntryNames = scriptArchive.getArchiveEntryNames();
        pathFilterBuilder.addFilter(PathFilters.in(archiveEntryNames), true);

        URL url = scriptArchive.getRootUrl();
        String file = url.getFile();
        ResourceLoader rootResourceLoader = null;
        String protocol = url.getProtocol();
        if (protocol.startsWith("file")) {
            rootResourceLoader = ResourceLoaders.createFileResourceLoader(archiveName, new File(file));
        } else if (protocol.startsWith("jar")) {
            rootResourceLoader = ResourceLoaders.createJarResourceLoader(archiveName, new JarFile(file));
        }
        if (rootResourceLoader != null) {
            moduleSpecBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(rootResourceLoader,pathFilterBuilder.create()));
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
        return;
    }
}

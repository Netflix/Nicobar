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
package com.netflix.scriptlib.groovy2.module;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilters;

import com.netflix.scriptlib.core.compile.ScriptArchive;

/**
 * ModuleFinder which is backed by a {@link ConcurrentHashMap}.
 * Integration class used to help the conversion of {@link ScriptArchive}s to
 * Modules
 *
 * @author James Kojo
 */
public class Groovy2ScriptModuleFinder implements ModuleFinder {

    private final Map<ModuleIdentifier, ScriptArchive> scriptArchiveRepo =
        new ConcurrentHashMap<ModuleIdentifier, ScriptArchive>();

    public Groovy2ScriptModuleFinder() {
    }
    @Override
    public ModuleSpec findModule(ModuleIdentifier moduleId, ModuleLoader delegateLoader) throws ModuleLoadException {
        ScriptArchive scriptArchive = scriptArchiveRepo.get(moduleId);
        if (scriptArchive == null) {
            return null;
        }

        // add the source and resource files to the module spec
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(moduleId);
        String archiveName = scriptArchive.getName();
        Path archiveRootPath = scriptArchive.getRootPath();
        ResourceLoader rootResourceLoader = ResourceLoaders.createFileResourceLoader(archiveName, archiveRootPath.toFile());
        MultiplePathFilterBuilder pathFilterBuilder = PathFilters.multiplePathFilterBuilder(false);
        List<Path> archiveFiles = scriptArchive.getFiles();
        for (Path archiveFile : archiveFiles) {
            pathFilterBuilder.addFilter(PathFilters.is(archiveFile.toString()), true);
        }
        ResourceLoader filteredResourceLoader = ResourceLoaders.createFilteredResourceLoader(pathFilterBuilder.create(), rootResourceLoader);
        moduleSpecBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(filteredResourceLoader));

        // add dependencies to the module spec
        List<String> dependencies = scriptArchive.getDependencies();
        for (String moduleName : dependencies) {
            moduleSpecBuilder.addDependency(DependencySpec.createModuleDependencySpec(ModuleIdentifier.create(moduleName), true, false));
        }

        // add properties to the module spec
        Map<String, String> archiveMetadata = scriptArchive.getArchiveMetadata();
        for (Entry<String, String> entry : archiveMetadata.entrySet()) {
            moduleSpecBuilder.addProperty(entry.getKey(), entry.getValue());
        }

        // inject a custom classloader that can compile and load groovy files
        try {
            moduleSpecBuilder.setModuleClassLoaderFactory(Groovy2ModuleClassLoader.createFactory(archiveRootPath.toUri().toURL()));
        } catch (MalformedURLException e) {
            throw new ModuleLoadException(e);
        }
        moduleSpecBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        return moduleSpecBuilder.create();
    }

    public ScriptArchive addToRepository(ScriptArchive archive) {
        return scriptArchiveRepo.put(ModuleIdentifier.create(archive.getName()), archive);
    }

    public ScriptArchive removeFromRepository(String moduleName) {
        return scriptArchiveRepo.remove(ModuleIdentifier.create(moduleName));
    }
}

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

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;

import com.netflix.nicobar.core.archive.GsonScriptModuleSpecSerializer;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.archive.ScriptModuleSpecSerializer;
import com.netflix.nicobar.core.plugin.BytecodeLoadingPlugin;

/**
 * Utility classes for ScriptModule processing
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class ScriptModuleUtils {

    /**
     * Find all of the classes in the module that are subclasses or equal to the target class
     * @param module module to search
     * @param targetClass target type to search for
     * @return first instance that matches the given type
     */
    public static Set<Class<?>> findAssignableClasses(ScriptModule module, Class<?> targetClass) {
        Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        for (Class<?> candidateClass : module.getLoadedClasses()) {
            if (targetClass.isAssignableFrom(candidateClass)) {
                result.add(candidateClass);
            }
        }
        return result;
    }

    /**
     * Find the first class in the module that is a subclasses or equal to the target class
     * @param module module to search
     * @param targetClass target type to search for
     * @return first instance that matches the given type
     */
    @Nullable
    public static Class<?> findAssignableClass(ScriptModule module, Class<?> targetClass) {
        for (Class<?> candidateClass : module.getLoadedClasses()) {
            if (targetClass.isAssignableFrom(candidateClass)) {
                return candidateClass;
            }
        }
        return null;
    }

    /**
     * Find a class in the module that matches the given className
     *
     * @param module the script module to search
     * @param className the class name in dotted form.
     * @return the found class, or null.
     */
    @Nullable
    public static Class<?> findClass(ScriptModule module, String className) {
        Set<Class<?>> classes = module.getLoadedClasses();
        Class<?> targetClass = null;
        for (Class<?> clazz : classes) {
            if (clazz.getName().equals(className)) {
                targetClass = clazz;
                break;
            }
        }

        return targetClass;
    }

    /**
     * Convert a ScriptModule to its compiled equivalent ScriptArchive.
     * <p>
     * A jar script archive is created containing compiled bytecode
     * from a script module, as well as resources and other metadata from
     * the source script archive.
     * <p>
     * This involves serializing the class bytes of all the loaded classes in
     * the script module, as well as copying over all entries in the original
     * script archive, minus any that have excluded extensions. The module spec
     * of the source script archive is transferred as is to the target bytecode
     * archive.
     *
     * @param module the input script module containing loaded classes
     * @param jarPath the path to a destination JarScriptArchive.
     * @param excludedExtensions a set of extensions with which
     *        source script archive entries can be excluded.
     *
     * @throws Exception
     */
    public static void toCompiledScriptArchive(ScriptModule module, Path jarPath,
            Set<String> excludeExtensions) throws Exception {
        ScriptArchive sourceArchive = module.getSourceArchive();
        JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jarPath.toFile()));
        FileOutputStream outputStream = null;
        try {
            // First copy all resources (excluding those with excluded extensions)
            // from the source script archive, into the target script archive
            for (String archiveEntry : sourceArchive.getArchiveEntryNames()) {
                URL entryUrl = sourceArchive.getEntry(archiveEntry);
                boolean skip = false;
                for (String extension: excludeExtensions) {
                    if (entryUrl.toString().endsWith(extension)) {
                        skip = true;
                        break;
                    }
                }

                if (skip)
                    continue;

                InputStream entryStream = entryUrl.openStream();
                byte[] entryBytes = IOUtils.toByteArray(entryStream);
                entryStream.close();

                jarStream.putNextEntry(new ZipEntry(archiveEntry));
                jarStream.write(entryBytes);
                jarStream.closeEntry();
            }

            // Now copy all compiled / loaded classes from the script module.
            Set<Class<?>> loadedClasses = module.getModuleClassLoader().getLoadedClasses();
            Iterator<Class<?>> iterator = loadedClasses.iterator();
            while (iterator.hasNext()) {
                Class<?> clazz = iterator.next();
                String classPath = clazz.getName().replace(".", "/") + ".class";
                URL resourceURL = module.getModuleClassLoader().getResource(classPath);
                if (resourceURL == null) {
                    throw new Exception("Unable to find class resource for: " + clazz.getName());
                }

                InputStream resourceStream = resourceURL.openStream();
                jarStream.putNextEntry(new ZipEntry(classPath));
                byte[] classBytes = IOUtils.toByteArray(resourceStream);
                resourceStream.close();
                jarStream.write(classBytes);
                jarStream.closeEntry();
            }

            // Copy the source moduleSpec, but tweak it to specify the bytecode compiler in the
            // compiler plugin IDs list.
            ScriptModuleSpec moduleSpec = sourceArchive.getModuleSpec();
            ScriptModuleSpec.Builder newModuleSpecBuilder = new ScriptModuleSpec.Builder(moduleSpec.getModuleId());
            newModuleSpecBuilder.addCompilerPluginIds(moduleSpec.getCompilerPluginIds());
            newModuleSpecBuilder.addCompilerPluginId(BytecodeLoadingPlugin.PLUGIN_ID);
            newModuleSpecBuilder.addMetadata(moduleSpec.getMetadata());
            newModuleSpecBuilder.addModuleDependencies(moduleSpec.getModuleDependencies());

            // Serialize the modulespec with GSON and its default spec file name
            ScriptModuleSpecSerializer specSerializer = new GsonScriptModuleSpecSerializer();
            String json = specSerializer.serialize(newModuleSpecBuilder.build());
            jarStream.putNextEntry(new ZipEntry(specSerializer.getModuleSpecFileName()));
            jarStream.write(json.getBytes());
            jarStream.closeEntry();
        } finally {
            if (outputStream != null) {
                IOUtils.closeQuietly(outputStream);
            }
            if (jarStream != null) {
                jarStream.close();
            }
        }
    }
}

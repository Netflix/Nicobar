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
package com.netflix.nicobar.core.plugin;

import com.google.common.base.Joiner;
import com.netflix.nicobar.core.archive.ModuleId;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.nio.file.FileVisitOption.FOLLOW_LINKS;

/**
 * This library supports pluggable language compilers. Compiler plugins will be loaded
 * into a separate class loader to provider extra isolation in case there are multiple
 * versions of them in the JVM.
 * <p/>
 * This class provides the metadata required to locate a compiler plugin and load it.
 *
 * @author James Kojo
 */
public class ScriptCompilerPluginSpec {
    /**
     * Used to construct a {@link ScriptCompilerPluginSpec}
     */
    public static class Builder {
        private final String pluginId;
        private Set<Path> runtimeResources = new LinkedHashSet<Path>();
        private String providerClassName;
        private Map<String, String> pluginMetadata = new LinkedHashMap<String, String>();
        private Map<String, Object> compilerParams = new LinkedHashMap<String, Object>();
        private final Set<ModuleId> moduleDependencies = new LinkedHashSet<ModuleId>();

        /**
         * Start a builder with the required parameters
         * @param pluginId name of this plugin. Will be used to construct the Module.
         */
        public Builder(String pluginId) {
            this.pluginId = pluginId;
        }

        /**
         * @param className of the plugin class which implements {@link ScriptCompilerPlugin}
         */
        public Builder withPluginClassName(String className) {
            providerClassName = className;
            return this;
        }

        /**
         * @param resourcePath Paths to jars and resources needed to create the language deploy module. This
         *  includes the language deploy as well as the jar/path to the provider class project.
         */
        public Builder addRuntimeResource(Path resourcePath) {
            if (resourcePath != null) {
                runtimeResources.add(resourcePath);
            }
            return this;
        }

        /**
         * @param resourcePaths Paths to jars and resources needed to create the language deploy module. This
         *  includes the language deploy as well as the jar/path to the provider class project.
         */
        public Builder addRuntimeResources(Set<Path> resourcePaths) {
            if (resourcePaths != null) {
                for (Path path : resourcePaths) {
                    runtimeResources.add(path);
                }
            }
            return this;
        }

        /**
         * @param resourceRootPath Path to the root of resource directory
         * @param fileExtensions   null or empty means to find any files, file extensions are case sensitive
         * @param recursively      find files recursively starting from the resourceRootPath
         * @throws IOException
         */
        public Builder addRuntimeResources(Path resourceRootPath, Set<String> fileExtensions, boolean recursively) throws IOException {
            if (resourceRootPath != null) {

                final int maxDepth = recursively ? Integer.MAX_VALUE : 1;
                final boolean isFileExtensionEmpty = fileExtensions == null || fileExtensions.isEmpty();

                final String extensions = !isFileExtensionEmpty ? Joiner.on(",").skipNulls().join(fileExtensions) : null;
                final PathMatcher pathMatcher = !isFileExtensionEmpty ? FileSystems.getDefault().getPathMatcher("glob:*.{" + extensions + "}") : null;

                Files.walkFileTree(resourceRootPath,
                        EnumSet.of(FOLLOW_LINKS),
                        maxDepth,
                        new SimpleFileVisitor<Path>() {

                            private void addPath(Path filePath) {
                                Path name = filePath.getFileName();
                                if (name != null) {
                                    if (isFileExtensionEmpty) {
                                        runtimeResources.add(filePath);
                                    } else if (pathMatcher.matches(name)) {
                                        runtimeResources.add(filePath);
                                    }
                                }
                            }

                            @Override
                            public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                                this.addPath(filePath);
                                return FileVisitResult.CONTINUE;
                            }
                        }
                );
            }
            return this;
        }

        /** Append metadata */
        public Builder addMetatdata(String name, String value) {
            if (name != null && value != null) {
                pluginMetadata.put(name, value);
            }
            return this;
        }
        /** Append all metadata */
        public Builder addMetatdata(Map<String, String> metadata) {
            if (metadata != null) {
                pluginMetadata.putAll(metadata);
            }
            return this;
        }

        /**
         * Adds one compiler parameter. Compiler parameters can be used to pass any random object to a compiler.
         * Plugin implementation should be aware of how to process any particular parameter, otherwise it will be ignored.
         */
        public Builder addCompilerParams(String name, Object value) {
            if (name != null && value != null) {
                compilerParams.put(name, value);
            }
            return this;
        }

        /**
         * Appends all compiler parameters. Compiler parameters can be used to pass any random object to a compiler.
         * Plugin implementation should be aware of how to process any particular parameter, otherwise it will be ignored.
         */
        public Builder addCompilerParams(Map<String, Object> params) {
            if (params != null) {
                compilerParams.putAll(params);
            }
            return this;
        }

        /** Add Module dependency. */
        public Builder addModuleDependency(String dependencyName) {
            if (dependencyName != null) {
                moduleDependencies.add(ModuleId.fromString(dependencyName));
            }
            return this;
        }

        /** Add Module dependencies. */
        public Builder addModuleDependencies(Set<String> dependencies) {
            if (dependencies != null) {
                for (String dependency : dependencies) {
                    addModuleDependency(dependency);
                }
            }
            return this;
        }

        /** Build the instance. */
        public ScriptCompilerPluginSpec build() {
            return new ScriptCompilerPluginSpec(pluginId,
                    moduleDependencies,
                    runtimeResources,
                    providerClassName,
                    pluginMetadata,
                    compilerParams);
        }
    }

    private final String pluginId;
    private final Set<Path> runtimeResources;
    private final String pluginClassName;
    private final Map<String, String> pluginMetadata;
    private final Map<String, Object> compilerParameters;
    private final Set<ModuleId> moduleDependencies;

    /**
     * @param pluginId language plugin id. will be used to create a module identifier.
     * @param runtimeResources Paths to jars and resources needed to create the language runtime module. This
     *        includes the language runtime as well as the jar/path to the provider class project.
     * @param pluginClassName fully qualified classname of the implementation of the {@link ScriptCompilerPlugin} class
     */
    protected ScriptCompilerPluginSpec(String pluginId, Set<ModuleId> moduleDependencies, Set<Path> runtimeResources, String pluginClassName, Map<String, String> pluginMetadata, Map<String, Object> compilerParams) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginName");
        this.moduleDependencies = Collections.unmodifiableSet(Objects.requireNonNull(moduleDependencies, "moduleDependencies"));
        this.runtimeResources = Collections.unmodifiableSet(Objects.requireNonNull(runtimeResources, "runtimeResources"));
        this.pluginClassName = pluginClassName;
        this.pluginMetadata = Collections.unmodifiableMap(Objects.requireNonNull(pluginMetadata, "pluginMetadata"));
        this.compilerParameters = compilerParams;
    }

    public String getPluginId() {
        return pluginId;
    }

    /**
     * Get the language deploy resources (jars or directories.)
     */
    public Set<Path> getRuntimeResources() {
        return runtimeResources;
    }

    /**
     * @return the names of the modules that this compiler plugin depends on
     */
    public Set<ModuleId> getModuleDependencies() {
        return moduleDependencies;
    }

    /**
     * @return application specific metadata
     */
    public Map<String, String> getPluginMetadata() {
        return pluginMetadata;
    }

    /**
     * @return application specific compiler params
     */
    public Map<String, Object> getCompilerParams() {
        return compilerParameters;
    }

    /**
     * @return fully qualified classname for instance of {@link ScriptCompilerPlugin} implementation for this plugin
     */
    public String getPluginClassName() {
        return pluginClassName;
    }
}

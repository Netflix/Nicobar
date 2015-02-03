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
package com.netflix.nicobar.groovy2.utils;

import java.nio.file.Path;

import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.utils.ClassPathUtils;
import com.netflix.nicobar.groovy2.plugin.Groovy2CompilerPlugin;

/**
 * Utility class for working with nicobar-groovy2
 * @author Vasanth Asokan
 */
public class Groovy2PluginUtils {
    /**
     * Helper method to orchestrate commonly required setup of nicobar-groovy2, and return a groovy2 compiler spec.
     * @return a {@link ScriptCompilerPluginSpec} that is instantiated for the Groovy2 language, and is specified to
     *         depend on both the underyling groovy runtime, as well as nicobar-groov2.
     */
    public static ScriptCompilerPluginSpec getCompilerSpec() {
        Path groovyRuntimePath = ClassPathUtils.findRootPathForResource("META-INF/groovy-release-info.properties",
                Groovy2PluginUtils.class.getClassLoader());
        if (groovyRuntimePath == null) {
            throw new IllegalStateException("couldn't find groovy-all.n.n.n.jar in the classpath.");
        }

        String resourceName = ClassPathUtils.classNameToResourceName(Groovy2CompilerPlugin.class.getName());
        Path nicobarGroovyPluginPath = ClassPathUtils.findRootPathForResource(resourceName,
                Groovy2PluginUtils.class.getClassLoader());
        if (nicobarGroovyPluginPath == null) {
            throw new IllegalStateException("couldn't find nicobar-groovy2 in the classpath.");
        }

        // Create the compiler spec
        ScriptCompilerPluginSpec compilerSpec = new ScriptCompilerPluginSpec.Builder(Groovy2CompilerPlugin.PLUGIN_ID)
            .withPluginClassName(Groovy2CompilerPlugin.class.getName())
            .addRuntimeResource(groovyRuntimePath)
            .addRuntimeResource(nicobarGroovyPluginPath)
            .build();

        return compilerSpec;
    }
}

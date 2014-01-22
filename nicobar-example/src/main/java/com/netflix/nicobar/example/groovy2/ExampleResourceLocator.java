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
package com.netflix.nicobar.example.groovy2;

import java.nio.file.Path;

import com.netflix.nicobar.core.utils.ClassPathUtils;

/**
 * Examples of how to find resource jars in the classpath for loading as separate modules.
 *
 * @author James Kojo
 */
public class ExampleResourceLocator {

    // module ID to use for the Groovy plugin
    public static final String GROOVY2_PLUGIN_ID = "groovy2";
    public static final String GROOVY2_COMPILER_PLUGIN_CLASS = "com.netflix.nicobar.groovy2.plugin.Groovy2CompilerPlugin";

    /**
     * Locate the groovy-all-n.n.n.jar file on the classpath.
     *
     * The ScriptModuleLoader will attempt to load the scripting runtimes into their own classloaders.
     * To accomplish this, the loader requires the actual file location of the groovy runtime jar.
     * This method is an example of a strategy for locating the groovy jar file in a programmatic way.
     *
     * This strategy assumes that the classloader of this example application has the
     * script runtime somewhere in it's classpath.
     * This is not necessarily true of all applications, depending on disposition of the deployed application artifacts.
     *
     * It further assumes that the groovy runtime contains the file "groovy-release-info.properties"
     * which was true of the time of groovy-all-2.1.6.jar
     */
    public static Path getGroovyRuntime() {
        Path path = ClassPathUtils.findRootPathForResource("META-INF/groovy-release-info.properties", ExampleResourceLocator.class.getClassLoader());
        if (path == null) {
            throw new IllegalStateException("coudln't find groovy-all.n.n.n.jar in the classpath.");
        }
        return path;
    }

    /**
     * Locate the classpath root which contains the groovy2 plugin.
     *
     * The ScriptModuleLoader will load the groovy2 plugin into the same classlaoder as the
     * groovy runtime. To accomplish this, the loader requires the file location of the groovy2 plugin.
     * This method is an example strategy for locating the plugin's jar file in a programmatic way.
     *
     * This strategy assumes that the classloader of this example application has the plugin in it' classpath.
     * see {@link ExampleResourceLocator#getGroovyRuntime()}.
     */
    public static Path getGroovyPluginLocation() {
        String resourceName = ClassPathUtils.classNameToResourceName(GROOVY2_COMPILER_PLUGIN_CLASS);
        Path path = ClassPathUtils.findRootPathForResource(resourceName, ExampleResourceLocator.class.getClassLoader());
        if (path == null) {
            throw new IllegalStateException("coudln't find groovy2 plugin jar in the classpath.");
        }
        return path;
    }
}

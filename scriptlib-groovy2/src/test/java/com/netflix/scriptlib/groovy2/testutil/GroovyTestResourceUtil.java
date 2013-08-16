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
package com.netflix.scriptlib.groovy2.testutil;

import static org.testng.Assert.assertNotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nullable;

import com.netflix.scriptlib.core.utils.ClassPathUtils;
import com.netflix.scriptlib.groovy2.plugin.Groovy2CompilerPlugin;

/**
 * utility class for finding test resources such as scripts
 *
 * @author James Kojo
 */
public class GroovyTestResourceUtil {

    /**
     * Metadata for test script found in test/resource
     */
    public static enum TestScript {
        HELLO_WORLD("helloworld/HelloWorld.groovy", "helloworld.HelloWorld"),
        LIBRARY_A("libA/LibraryA.groovy", "libA.LibraryA"),
        DEPENDS_ON_A("dependsona/DependsOnA.groovy", "dependsona.DependsOnA");

        private final Path resourcePath;
        private final String className;
        private TestScript(String resourcePath, String className) {
            this.resourcePath = Paths.get(resourcePath);
            this.className = className;
        }
        /**
         * @return relative path name suitable for passing to {@link ClassLoader#getResource(String)}
         */
        public Path getResourcePath() {
            return resourcePath;
        }

        /**
         * @return name of the resultant class after it's been loaded
         */
        public String getClassName() {
            return className;
        }
    }

    /**
     * Locate the root directory for the given script in the class path
     * @param script script identifier
     * @return absolute path to the root of the script
     */
    public static Path findRootPathForScript(TestScript script) throws Exception {
        return ClassPathUtils.findRootPathForResource(script.getResourcePath().toString(),
            GroovyTestResourceUtil.class.getClassLoader());
    }

   /**
    * locate the groovy-all jar on the classpath
    */
   public static Path getGroovyRuntime() {
       // assume that the classloader of this test has the runtime in it's classpath.
       // further assume that the runtime contains the file "groovy-release-info.properties"
       // which it had at the time of groovy-all-2.1.6.jar
       Path path = ClassPathUtils.findRootPathForResource("META-INF/groovy-release-info.properties", GroovyTestResourceUtil.class.getClassLoader());
       assertNotNull(path, "coudln't find groovy-all jar");
       return path;
   }

   /**
    * Locate the classpath root which contains the scriptlib-groovy2 project
    */
   public static Path getGroovyPluginLocation() {
       Path path = ClassPathUtils.findRootPathForClass(Groovy2CompilerPlugin.class);
       assertNotNull(path, "scriptlib-groovy2 project on classpath.");
       return path;
   }

   /**
    * Locate the cobertura jar
    * @param classLoader {@link ClassLoader} to search
    * @return path of the jar or null if not present
    */
   @Nullable
   public static Path getCoberturaJar(ClassLoader classLoader) {
       return ClassPathUtils.findRootPathForResource("net/sourceforge/cobertura/coveragedata/HasBeenInstrumented.class",
           GroovyTestResourceUtil.class.getClassLoader());
   }
}

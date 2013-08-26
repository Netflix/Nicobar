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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nullable;

import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.utils.ClassPathUtils;
import com.netflix.scriptlib.groovy2.plugin.Groovy2CompilerPlugin;

/**
 * utility class for finding test resources such as scripts
 *
 * @author James Kojo
 */
public class GroovyTestResourceUtil {

    /** name of the root directory where the test modules are located */
    private static final String TEST_MODULES_BASE_DIR = "testmodules";
    /**
     * Metadata for test script found in test/resource.
     * Use in conjunction with {@link GroovyTestResourceUtil#findRootPathForScript(TestScript)}.
     */
    public static enum TestScript {
        HELLO_WORLD("helloworld", "HelloWorld.groovy", "HelloWorld"),
        HELLO_PACKAGE("hellopackage", "package1/HelloPackage.groovy", "package1.HelloPackage"),
        LIBRARY_A("libA", "LibraryA.groovy", "LibraryA"),
        DEPENDS_ON_A("dependsonA", "DependsOnA.groovy", "DependsOnA");

        private String archiveName;
        private final Path scriptPath;
        private final String className;
        private TestScript(String archiveName, String resourcePath, String className) {
            this.archiveName = archiveName;
            this.scriptPath = Paths.get(resourcePath);
            this.className = className;
        }
        /**
         * @return relative path suitable for passing to {@link PathScriptArchive.Builder#addFile(Path)}
         */
        public Path getScriptPath() {
            return scriptPath;
        }

        /**
         * @return name of the resultant class after it's been loaded
         */
        public String getClassName() {
            return className;
        }

        /**
         * @return the default archive name if this script is converted to an archive
         */
        public String getArchiveName() {
            return archiveName;
        }
    }

    /**
     * Locate the root directory for the given script in the class path. Used for
     * generating the root path for the {@link PathScriptArchive}
     *
     * @param script script identifier
     * @return absolute path to the root of the script
     */
    public static Path findRootPathForScript(TestScript script) throws Exception {
        URL resourceUrl = GroovyTestResourceUtil.class.getClassLoader().getResource(TEST_MODULES_BASE_DIR + "/" + script.getArchiveName());
        assertNotNull(resourceUrl, "couldn't locate directory for script  " + script.getArchiveName());
        assertEquals(resourceUrl.getProtocol(), "file");
        return Paths.get(resourceUrl.getFile());
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

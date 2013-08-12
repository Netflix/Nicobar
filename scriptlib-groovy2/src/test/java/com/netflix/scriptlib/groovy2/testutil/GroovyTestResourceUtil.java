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
import static org.testng.Assert.fail;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nullable;

import org.testng.annotations.Test;

/**
 * utility class for finding test resources such as scripts
 *
 * @author James Kojo
 */
public class GroovyTestResourceUtil {

    public static enum TestScript {
        HELLO_WORLD("scripts/helloworld/HelloWorld.groovy", "HelloWorld");

        private final String resourcePath;
        private final String className;
        private TestScript(String resourcePath, String className) {
            this.resourcePath = resourcePath;
            this.className = className;
        }
        /**
         * @return path name suitable for passing to {@link ClassLoader#getResource(String)}
         */
        public String getResourcePath() {
            return resourcePath;
        }
        /**
         *
         * @return name of the resultant class after it's been loaded
         */
        public String getClassName() {
            return className;
        }
    }

    /**
     * Locate the given script in the class path
     * @param script script identifier
     * @return absolute path to the test script
     */
    public static Path getScriptAsPath(TestScript script) throws Exception {
        URL scriptUrl = Thread.currentThread().getContextClassLoader().getResource(script.getResourcePath());
        if (scriptUrl == null) {
            fail("couldn't load resource " + script.getResourcePath());
        }
        return Paths.get(scriptUrl.toURI());
    }
   /**
    * Convert a file path to an implied class name
    * @param filePath path to the script file relative to the source root
    * @return
    */
   public static String pathToClassName(String filePath) {
       Path path = Paths.get(filePath);
       String fileName = path.getFileName().toString();
       Path directoryPath = path.getParent();
       StringBuilder sb = new StringBuilder();
       if (directoryPath != null) {
           for (Path subPath : directoryPath) {
               sb.append(subPath).append(".");
           }
       }
       int endIndex = fileName.lastIndexOf(".");
       endIndex = endIndex < 0 ? fileName.length() : endIndex;
       sb.append(fileName.substring(0, endIndex));
       return sb.toString();

   }

   @Test
   public void testLoadClassloaderJar() throws Exception {
       URL propertyFile = getClass().getClassLoader().getResource("META-INF/groovy-release-info.properties");
       assertNotNull(propertyFile, "couldn't find resource file on classpath");
       System.out.printf("resource URL from getResource: '%s'\n", propertyFile);
       Path propertyPath = Paths.get(propertyFile.getPath());
       System.out.printf("Paths.get():: '%s'\n", propertyPath);
       String fileString = propertyFile.getFile();
       System.out.printf("url.getPath(): '%s'\n", fileString);
   }
   /**
    * locate the groovy-all jar on the classpath
    */
   public static Path getGroovyRuntime() {
       // assume that the classloader of this test has the runtime in it's classpath.
       // further assume that the runtime contains the file "groovy-release-info.properties"
       // which it had at the time of groovy-all-2.1.6.jar
       return getJarPathFromResourceUrl("META-INF/groovy-release-info.properties", GroovyTestResourceUtil.class.getClassLoader());
   }

   public static Path getGroovyPluginLocation() {
       URL propertyFile = GroovyTestResourceUtil.class.getClassLoader().getResource("META-INF/scriptlib-groovy2-release-info.properties");
       assertNotNull(propertyFile, "couldn't find groovy resource file on classpath.");
       assertEquals(propertyFile.getProtocol(), "file");
       return Paths.get(propertyFile.getPath()).getParent().getParent();
   }

   /**
    * Find the jar containing the given resource.
    *
    * @param resourceName relative path of the resource to locate
    * @return {@link Path} to the Jar containing the resource.
    */
   @Nullable
   public static Path getJarPathFromResourceUrl(String resourceName, ClassLoader classloader) {
       URL propertyFile = classloader.getResource(resourceName);
       assertNotNull(propertyFile, "couldn't find groovy resource file on classpath.");
       assertEquals(propertyFile.getProtocol(), "jar");
       String pathString = propertyFile.getPath();
       // path is in the form of: file:/path/to/groovy/groovy-all-2.1.6.jar!/META-INF/groovy-release-info.properties
       int startIndex = pathString.startsWith("file:") ? 5 : 0;
       int endIndex = pathString.lastIndexOf("!");
       Path jarPath = Paths.get(pathString.substring(startIndex, endIndex));
       return jarPath;
   }
}

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
package com.netflix.scriptlib.core.testutil;

import static org.testng.Assert.fail;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility class to locate test resources
 *
 * @author James Kojo
 */
public class CoreTestResourceUtil {
    /**
     * Metadata for test resources found in test/resource
     */
    public static enum TestResource {
        TEST_TEXT_PATH("paths/test-text", "sub1/sub1.txt", "sub2/sub2.txt", "root.txt", "META-INF/MANIFEST.MF"),
        TEST_TEXT_JAR("jars/test-text.jar", "sub1/sub1.txt", "sub2/sub2.txt", "root.txt", "META-INF/MANIFEST.MF");

        private final String resourcePath;
        private final Set<String> contentPaths;
        private TestResource(String resourcePath, String... contentPaths) {
            this.resourcePath = resourcePath;
            this.contentPaths = new LinkedHashSet<String>(Arrays.asList(contentPaths));
        }
        /**
         * @return path name suitable for passing to {@link ClassLoader#getResource(String)}
         */
        public String getResourcePath() {
            return resourcePath;
        }

        /**
         * @return the relative path names found in the resource
         */
        public Set<String> getContentPaths() {
            return contentPaths;
        }
    }

    /**
     * Locate the given script in the class path
     * @param script script identifier
     * @return absolute path to the test script
     */
    public static Path getResourceAsPath(TestResource script) throws Exception {
        URL scriptUrl = Thread.currentThread().getContextClassLoader().getResource(script.getResourcePath());
        if (scriptUrl == null) {
            fail("couldn't load resource " + script.getResourcePath());
        }
        return Paths.get(scriptUrl.toURI());
    }
}

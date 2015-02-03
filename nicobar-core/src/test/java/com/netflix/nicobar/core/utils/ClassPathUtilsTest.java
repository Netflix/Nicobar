/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.nicobar.core.utils;

import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.testutil.CoreTestResourceUtil;
import com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource;

/**
 * Unit tests for {@link ClassPathUtils}
 *
 * @author Vasanth Asokan
 */
public class ClassPathUtilsTest {

    private String classPathStr = "";

    @BeforeTest
    public void testSetup() throws Exception {
        String rootDir = CoreTestResourceUtil.getResourceAsPath(TestResource.TEST_CLASSPATHDIR_PATH).toString();
        String classesDir = rootDir + File.separator + "classes";
        String jarPath = rootDir + File.separator + "libs" + File.separator + "nicobar-test-v1.0.jar";
        classPathStr =  classesDir + File.pathSeparator + jarPath;
    }

    @Test
    public void testScanClassPath() throws Exception {
        Set<String> pkgPaths = ClassPathUtils.scanClassPath(classPathStr,
                                    Collections.<String>emptySet());
        Set<String> expected = new HashSet<String>();
        Collections.addAll(expected,
                "com/netflix/nicobar/hello",
                "com/netflix/nicobar/test/ui",
                "com/netflix/foo",
                "com/netflix/foo/bar",
                "com/netflix/baz/internal",
                "com/netflix/nicobar/test/ui/internal",
                "com/netflix/nicobar/test",
                "com/netflix/nicobar/hello/internal");
        assertTrue(pkgPaths.containsAll(expected));
    }

    @Test
    public void testScanClassPathWithExcludes() throws Exception {
        Set<String> excludePrefixes = new HashSet<String>();
        Collections.addAll(excludePrefixes, "com/netflix/foo/bar");
        Set<String> pkgPaths = ClassPathUtils.scanClassPathWithExcludes(classPathStr,
                        Collections.<String>emptySet(),
                        excludePrefixes);
        Set<String> notExpected = new HashSet<String>();
        Collections.addAll(notExpected, "com/netflix/foo/bar");
        Set<String> expected = new HashSet<String>();
        Collections.addAll(expected,
                "com/netflix/nicobar/hello",
                "com/netflix/nicobar/test/ui",
                "com/netflix/foo",
                "com/netflix/baz/internal",
                "com/netflix/nicobar/test/ui/internal",
                "com/netflix/nicobar/test",
                "com/netflix/nicobar/hello/internal");

        assertTrue(pkgPaths.containsAll(expected));
        for (String excluded: notExpected) {
            assertTrue(!pkgPaths.contains(excluded));
        }
    }

    @Test
    public void testScanClassPathWithIncludes() throws Exception {
        Set<String> includePrefixes = new HashSet<String>();
        Collections.addAll(includePrefixes, "com/netflix/foo");
        Set<String> pkgPaths = ClassPathUtils.scanClassPathWithIncludes(classPathStr,
                        Collections.<String>emptySet(),
                        includePrefixes);
        Set<String> expected = new HashSet<String>();
        Collections.addAll(expected,
                "com/netflix/foo",
                "com/netflix/foo/bar");
        assertTrue(pkgPaths.equals(expected));
    }

    @Test
    public void testScanClassPathWithIncludesAndExcludes() throws Exception {
        Set<String> includePrefixes = new HashSet<String>();
        Collections.addAll(includePrefixes, "com/netflix/nicobar");
        Set<String> excludePrefixes = new HashSet<String>();
        Collections.addAll(excludePrefixes, "com/netflix/foo/bar");
        Set<String> pkgPaths = ClassPathUtils.scanClassPath(classPathStr,
                        Collections.<String>emptySet(),
                        excludePrefixes,
                        includePrefixes);
        Set<String> expected = new HashSet<String>();
        Collections.addAll(expected, "com/netflix/nicobar/hello",
                "com/netflix/nicobar/test/ui",
                "com/netflix/nicobar/test/ui/internal",
                "com/netflix/nicobar/test",
                "com/netflix/nicobar/hello/internal");

        assertTrue(pkgPaths.equals(expected));
    }
}

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
package com.netflix.nicobar.core.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;

/**
 * Utility methods for dealing with classes and resources in a {@link ClassLoader}
 *
 * @author James Kojo
 */
public class ClassPathUtils {

    /**
     * Get a list of JDK packages in the classpath, by scanning the bootstrap classpath.
     * @return a set of package path strings (package paths separated by '/' and not '.').
     */
    public static Set<String> getJdkPaths() {
        return __JDKPaths.JDK;
    }

    /**
     * Find the root path for the given resource. If the resource is found in a Jar file, then the
     * result will be an absolute path to the jar file. If the resource is found in a directory,
     * then the result will be the parent path of the given resource.
     *
     * For example, if the resourceName is given as "scripts/myscript.groovy", and the path to the file is
     * "/root/sub1/script/myscript.groovy", then this method will return "/root/sub1"
     *
     * @param resourceName relative path of the resource to search for. E.G. "scripts/myscript.groovy"
     * @param classLoader the {@link ClassLoader} to search
     * @return absolute path of the root of the resource.
     */
    @Nullable
    public static Path findRootPathForResource(String resourceName, ClassLoader classLoader)  {
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(classLoader, "classLoader");

        URL resource = classLoader.getResource(resourceName);
        if (resource != null) {
            String protocol = resource.getProtocol();
            if (protocol.equals("jar")) {
                return getJarPathFromUrl(resource);
            } else if (protocol.equals("file")) {
                return getRootPathFromDirectory(resourceName, resource);
            } else {
                throw new IllegalStateException("Unsupported URL protocol: " + protocol);
            }
        }
        return null;
    }


    private static Path getRootPathFromDirectory(String resourceName, URL resource) {
        try {
            Path result = Paths.get(resource.toURI());
            int relativePathSize = Paths.get(resourceName).getNameCount();
            for (int i = 0; i < relativePathSize; i++) {
                result = result.getParent();
            }
            return result;
        } catch (URISyntaxException e){
            throw new IllegalStateException("Unsupported URL syntax: " + resource, e);
        }
    }

    /**
     * Find the root path for the given class. If the class is found in a Jar file, then the
     * result will be an absolute path to the jar file. If the resource is found in a directory,
     * then the result will be the parent path of the given resource.
     *
     * @param clazz class to search for
     * @return absolute path of the root of the resource.
     */
    @Nullable
    public static Path findRootPathForClass(Class<?> clazz) {
        Objects.requireNonNull(clazz, "resourceName");
        String resourceName = classToResourceName(clazz);
        return findRootPathForResource(resourceName, clazz.getClassLoader());
    }

    /**
     * Find the jar containing the given resource.
     *
     * @param jarUrl URL that came from a jar that needs to be parsed
     * @return {@link Path} to the Jar containing the resource.
     */
    public static Path getJarPathFromUrl(URL jarUrl) {
        try {
            String pathString = jarUrl.getPath();
            // for Jar URL, the path is in the form of: file:/path/to/groovy/myJar.jar!/path/to/resource/myResource.txt
            int endIndex = pathString.lastIndexOf("!");
            return Paths.get(new URI(pathString.substring(0, endIndex)));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unsupported URL syntax: " + jarUrl.getPath(), e);
        }
    }

    /**
     * Get all of the directory paths in a zip/jar file
     * @param pathToJarFile location of the jarfile. can also be a zipfile
     * @return set of directory paths relative to the root of the jar
     */
    public static Set<Path> getDirectoriesFromJar(Path pathToJarFile) throws IOException {
        Set<Path> result = new HashSet<Path>();
        ZipFile jarfile = new ZipFile(pathToJarFile.toFile());
        try {
            final Enumeration<? extends ZipEntry> entries = jarfile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    result.add(Paths.get(entry.getName()));
                }
            }
            jarfile.close();
        } finally {
            IOUtils.closeQuietly(jarfile);
        }
        return result;
    }

    /**
     * Get the resource name for the class file for the given class
     * @param clazz the class to convert
     * @return resource name appropriate for using with {@link ClassLoader#getResource(String)}
     */
    public static String classToResourceName(Class<?> clazz) {
        return classNameToResourceName(clazz.getName());
    }

    /**
     * Get the resource name for the class file for the given class name
     * @param className fully qualified classname to convert
     * @return resource name appropriate for using with {@link ClassLoader#getResource(String)}
     */
    public static String classNameToResourceName(String className) {
        // from URLClassLoader.findClass()
        return className.replace('.', '/').concat(".class");
    }


    /**
     * Scan the classpath string provided, and collect a set of package paths found in jars and classes on the path.
     *
     * @param classPath the classpath string
     * @param excludeJarSet a set of jars to exclude from scanning
     * @return the results of the scan, as a set of package paths (separated by '/').
     */
    public static Set<String> scanClassPath(final String classPath, final Set<String> excludeJarSet) {
        final Set<String> pathSet = new HashSet<String>();
        // Defer to JDKPaths to do the actual classpath scanning.
        __JDKPaths.processClassPathItem(classPath, excludeJarSet, pathSet);
        return pathSet;
    }

    /**
     * Scan the classpath string provided, and collect a set of package paths found in jars and classes on the path.
     * On the resulting path set, first exclude those that match any exclude prefixes, and then include
     * those that match a set of include prefixes.
     *
     * @param classPath the classpath string
     * @param excludeJarSet a set of jars to exclude from scanning
     * @param excludePrefixes a set of path prefixes that determine what is excluded
     * @param includePrefixes a set of path prefixes that determine what is included
     * @return the results of the scan, as a set of package paths (separated by '/').
     */
    public static Set<String> scanClassPath(final String classPath, final Set<String> excludeJarSet, final Set<String> excludePrefixes, final Set<String> includePrefixes) {
        final Set<String> pathSet = new HashSet<String>();
        // Defer to JDKPaths to do the actual classpath scanning.
        __JDKPaths.processClassPathItem(classPath, excludeJarSet, pathSet);

        return filterPathSet(pathSet, excludePrefixes, includePrefixes);
    }

    /**
     * Scan the classpath string provided, and collect a set of package paths found in jars and classes on the path,
     * excluding any that match a set of exclude prefixes.
     *
     * @param classPath the classpath string
     * @param excludeJarSet a set of jars to exclude from scanning
     * @param excludePrefixes a set of path prefixes that determine what is excluded
     * @return the results of the scan, as a set of package paths (separated by '/').
     */
    public static Set<String> scanClassPathWithExcludes(final String classPath, final Set<String> excludeJarSet, final Set<String> excludePrefixes) {
        final Set<String> pathSet = new HashSet<String>();
        // Defer to JDKPaths to do the actual classpath scanning.
        __JDKPaths.processClassPathItem(classPath, excludeJarSet, pathSet);

        return filterPathSet(pathSet, excludePrefixes, Collections.<String>emptySet());
    }

    /**
     * Scan the classpath string provided, and collect a set of package paths found in jars and classes on the path,
     * including only those that match a set of include prefixes.
     *
     * @param classPath the classpath string
     * @param excludeJarSet a set of jars to exclude from scanning
     * @param includePrefixes a set of path prefixes that determine what is included
     * @return the results of the scan, as a set of package paths (separated by '/').
     */
    public static Set<String> scanClassPathWithIncludes(final String classPath, final Set<String> excludeJarSet, final Set<String> includePrefixes) {
        final Set<String> pathSet = new HashSet<String>();
        // Defer to JDKPaths to do the actual classpath scanning.
        __JDKPaths.processClassPathItem(classPath, excludeJarSet, pathSet);

        return filterPathSet(pathSet, Collections.<String>emptySet(), includePrefixes);
    }

    private static Set<String> filterPathSet(Set<String> pathSet, Set<String> excludePrefixes, Set<String> includePrefixes) {
        Set<String> filteredSet = new HashSet<String>(pathSet);

        // Ideally, we would use a trie, but we are talking ~100s of paths and a few excludes and includes,
        // not to mention these are throw away scans and not reused typically.

        // First process the excludes
        for (String exclude: excludePrefixes) {
            Iterator<String> setIterator = filteredSet.iterator();
            while(setIterator.hasNext()) {
                String path = setIterator.next();
                if (path.startsWith(exclude))
                    setIterator.remove();
            }
        }

        // An empty set of includes indicates include everything
        if (includePrefixes.size() == 0) {
            return filteredSet;
        }

        // Now, create a filtered set based on the includes
        Iterator<String> setIterator = filteredSet.iterator();
        while(setIterator.hasNext()) {
            String path = setIterator.next();
            boolean shouldInclude = false;
            for (String include: includePrefixes) {
                if (path.startsWith(include)) {
                    shouldInclude = true;
                    break;
                }
            }

            // Remove if none of the includes specify this package path
            if (!shouldInclude) {
                setIterator.remove();
            }
        }

        return filteredSet;
    }
}

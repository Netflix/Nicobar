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
package com.netflix.scriptlib.core.utils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
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
    public static Path findRootPathForResource(String resourceName, ClassLoader classLoader) {
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(classLoader, "classLoader");

        URL resource = classLoader.getResource(resourceName);
        if (resource != null) {
            String protocol = resource.getProtocol();
            if (protocol.equals("jar")) {
                return getJarPathFromUrl(resource);
            } else if (protocol.equals("file")) {
                int relativePathSize = Paths.get(resourceName).getNameCount();
                Path result = Paths.get(resource.getFile());
                for (int i = 0; i < relativePathSize; i++) {
                    result = result.getParent();
                }
                return result;
            } else {
                throw new IllegalStateException("Unsupported URL protocol: " + protocol);
            }
        }
        return null;
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
        String pathString = jarUrl.getPath();
        // for Jar URL, the path is in the form of: file:/path/to/groovy/myJar.jar!/path/to/resource/myResource.txt
        int startIndex = pathString.startsWith("file:") ? 5 : 0;
        int endIndex = pathString.lastIndexOf("!");
        Path jarPath = Paths.get(pathString.substring(startIndex, endIndex));
        return jarPath;
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
        // from URLClassLoader.findClass()
        return clazz.getName().replace('.', '/').concat(".class");
    }
}

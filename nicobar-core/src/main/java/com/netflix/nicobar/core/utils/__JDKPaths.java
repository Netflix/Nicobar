/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.netflix.nicobar.core.utils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

/**
 * cloned from jboss-modules. This is required because there doesn't seem to be a good way to get the contents
 * of the bootstrap classloader without getting the full application classpath, which may contain dependencies that
 * we don't wish to expose.
 * TODO: ask for an enhancement so we don't close this.
 *
 * A utility class which maintains the set of JDK paths.  Makes certain assumptions about the disposition of the
 * class loader used to load JBoss Modules; thus this class should only be used when booted up via the "-jar" or "-cp"
 * switches.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class __JDKPaths {
    static final Set<String> JDK;

    static {
        final Set<String> pathSet = new HashSet<String>(1024);
        final Set<String> jarSet = new HashSet<String>(1024);
        final String sunBootClassPath = System.getProperty("sun.boot.class.path");
        processClassPathItem(sunBootClassPath, jarSet, pathSet);
        JDK = Collections.unmodifiableSet(pathSet);
    }

    private __JDKPaths() {
    }

    static void processClassPathItem(final String classPath, final Set<String> jarSet, final Set<String> pathSet) {
        if (classPath == null) return;
        int s = 0, e;
        do {
            e = classPath.indexOf(File.pathSeparatorChar, s);
            String item = e == -1 ? classPath.substring(s) : classPath.substring(s, e);
            if (jarSet != null && !jarSet.contains(item)) {
                final File file = new File(item);
                if (file.isDirectory()) {
                    processDirectory0(pathSet, file);
                } else {
                    try {
                        processJar(pathSet, file);
                    } catch (IOException ex) { // NOPMD
                    }
                }
            }
            s = e + 1;
        } while (e != -1);
    }

    static void processJar(final Set<String> pathSet, final File file) throws IOException {
        final ZipFile zipFile = new ZipFile(file);
        try {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                final int lastSlash = name.lastIndexOf('/');
                if (lastSlash != -1) {
                    pathSet.add(name.substring(0, lastSlash));
                }
            }
            zipFile.close();
        } finally {
            IOUtils.closeQuietly(zipFile);
        }
    }

    static void processDirectory0(final Set<String> pathSet, final File file) {
        for (File entry : file.listFiles()) {
            if (entry.isDirectory()) {
                processDirectory1(pathSet, entry, file.getPath());
            } else {
                final String parent = entry.getParent();
                if (parent != null) pathSet.add(parent);
            }
        }
    }

    static void processDirectory1(final Set<String> pathSet, final File file, final String pathBase) {
        for (File entry : file.listFiles()) {
            if (entry.isDirectory()) {
                processDirectory1(pathSet, entry, pathBase);
            } else {
                String packagePath = entry.getParent();
                if (packagePath != null) {
                    packagePath = packagePath.substring(pathBase.length()).replace('\\', '/');
                    if(packagePath.startsWith("/")) {
                        packagePath = packagePath.substring(1);
                    }
                    pathSet.add(packagePath);
                }
            }
        }
    }
}

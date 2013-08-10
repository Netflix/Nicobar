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
package com.netflix.scriptlib.groovy2.compile;

import groovy.lang.GroovyClassLoader;

import java.io.IOException;
import java.net.URL;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;

import com.sun.org.apache.bcel.internal.generic.LoadClass;

/**
 *
 * Specialized {@link GroovyClassLoader} for use with this library.
 *
 * Adds the following features:
 * <pre>
 * </pre>
 * @author James Kojo
 */
public class ScriptArchiveGroovyClassloader extends GroovyClassLoader {

    public ScriptArchiveGroovyClassloader(ClassLoader parent, CompilerConfiguration config, boolean useConfigurationClasspath) {
        super(parent, config, useConfigurationClasspath);
    }

    @Override
    public Class getClassCacheEntry(String name) {
        return super.getClassCacheEntry(name);
    }

    /**
     * Compile the given class name by loading the script from the
     * classpath and compiling it. Similar to {@link LoadClass} except
     * that it skips the call to the parent classloader;
     * @param className
     * @return
     * @throws IOException
     * @throws CompilationFailedException
     */
    public Class compileLocal(String className, URL source) throws CompilationFailedException, IOException {
        return recompile(source, className, null);
    }

}

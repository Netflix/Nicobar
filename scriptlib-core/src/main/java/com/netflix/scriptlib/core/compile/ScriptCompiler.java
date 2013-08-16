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
package com.netflix.scriptlib.core.compile;

import java.io.IOException;
import java.util.Set;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.module.ScriptModuleClassLoader;


/**
 * Converts a Script Archive into a Set of classes
 *
 * @author James Kojo
 */
public interface ScriptCompiler {
    /**
     * Common names used to query the {@link ScriptArchive#getArchiveMetadata()} that are relevant
     * to compilers.
     */
    public static enum MetadataName {
        SCRIPT_LANGUAGE;
    }
    /**
     * Whether or not this compiler should be used to compile the archive
     */
    public boolean shouldCompile(ScriptArchive archive);

    /**
     * Compile the archive into a ScriptModule
     * @param archive archive to generate class files for
     * @param moduleClassLoader class loader which can be used to find all classes and resources for modules.
     *  The resultant classes of the compile operation should be injected into the classloader
     * for which the given archive has a declared dependency on
     * @return The set of classes that were compiled
     * @throws ScriptCompilationException if there was a compilation issue in the archive.
     * @throws IOException if there was a problem reading/writing the archive
     */
    public Set<Class<?>> compile(ScriptArchive archive, ScriptModuleClassLoader moduleClassLoader) throws ScriptCompilationException, IOException;
}

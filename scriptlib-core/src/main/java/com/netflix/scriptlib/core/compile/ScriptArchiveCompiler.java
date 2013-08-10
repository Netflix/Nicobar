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

import com.netflix.scriptlib.core.ScriptModule;
import com.netflix.scriptlib.core.archive.ScriptArchive;


/**
 * Converts a Script Archive into a Module
 *
 * @author James Kojo
 */
public interface ScriptArchiveCompiler {
    /**
     * Whether or not this compiler should be used to compile the archive
     */
    public boolean shouldCompile(ScriptArchive archive) throws IOException;

    /**
     * Compile the archive into a ScriptModule
     * @param archive
     * @return
     * @throws ScriptCompilationException if there was a compilation issue in the archive.
     * @throws IOException if there was a problem reading/writing the archive
     */
    public ScriptModule compile(ScriptArchive archive) throws ScriptCompilationException, IOException;
}

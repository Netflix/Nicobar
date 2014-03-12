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
package com.netflix.nicobar.groovy2.plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.plugin.ScriptCompilerPlugin;
import com.netflix.nicobar.groovy2.compile.Groovy2BytecodeCompiler;
import com.netflix.nicobar.groovy2.compile.Groovy2Compiler;

/**
 * Factory class for the Groovy 2 language plug-in
 *
 * @author James Kojo
 */
public class Groovy2CompilerPlugin implements ScriptCompilerPlugin {

    /**
     * The compiler for groovy2 sources
     */
    public static final String GROOVY2_SOURCE_COMPILER_ID = "groovy2";

    /**
     * The compiler (loader) for groovy2 compiled bytecode.
     */
    public static final String GROOVY2_BYTECODE_COMPILER_ID = "groovy2-bytecode";

    public Groovy2CompilerPlugin() {
    }

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers() {
        HashSet<ScriptArchiveCompiler> compilers = new HashSet<ScriptArchiveCompiler>();
        Collections.addAll(compilers, new Groovy2Compiler(), new Groovy2BytecodeCompiler());
        return Collections.unmodifiableSet(compilers);
    }
}

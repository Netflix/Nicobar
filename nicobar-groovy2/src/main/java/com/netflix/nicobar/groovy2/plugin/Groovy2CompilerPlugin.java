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
import java.util.Set;

import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.plugin.ScriptCompilerPlugin;
import com.netflix.nicobar.groovy2.compile.Groovy2Compiler;

/**
 * Factory class for the Groovy 2 language plug-in
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class Groovy2CompilerPlugin implements ScriptCompilerPlugin {

    public static final String PLUGIN_ID = "groovy2";

    public Groovy2CompilerPlugin() {
    }

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers() {
        return Collections.singleton(new Groovy2Compiler());
    }
}

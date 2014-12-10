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
package com.netflix.nicobar.groovy2.internal.compile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;

/**
 * Groovy specific implementation of the {@link ScriptArchiveCompiler}
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class Groovy2Compiler implements ScriptArchiveCompiler {
    public final static String GROOVY2_COMPILER_ID = "groovy2";
    public final static String GROOVY2_COMPILER_PARAMS_CUSTOMIZERS = "compilerCustomizers";
    
    private List<CompilationCustomizer> compilerCustomizers = new LinkedList<CompilationCustomizer>();
    
    public Groovy2Compiler(Map<String, Object> compilerParams) {
        this.processCompilerParams(compilerParams);
    }

    private void processCompilerParams(Map<String, Object> compilerParams) {
        if (compilerParams.containsKey(GROOVY2_COMPILER_PARAMS_CUSTOMIZERS)) {
            Object customizers = compilerParams.get(GROOVY2_COMPILER_PARAMS_CUSTOMIZERS);

            if (customizers instanceof List) {
                for (Object customizer: (List<?>) customizers) {
                    if (customizer instanceof CompilationCustomizer) {
                        this.compilerCustomizers.add((CompilationCustomizer) customizer);
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldCompile(ScriptArchive archive) {
       return archive.getModuleSpec().getCompilerPluginIds().contains(GROOVY2_COMPILER_ID);
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader, Path compilationRootDir)
        throws ScriptCompilationException, IOException {
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(this.compilerCustomizers.toArray(new CompilationCustomizer[0]));
        
         new Groovy2CompilerHelper(compilationRootDir)
            .addScriptArchive(archive)
            .withParentClassloader(moduleClassLoader)
            .withConfiguration(config)
            .compile();
        return Collections.emptySet();
    }
}

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
    public final static String GROOVY2_COMPILER_PARAMS_CUSTOMIZERS = "customizerClassNames";

    private List<String> customizerClassNames = new LinkedList<String>();

    public Groovy2Compiler(Map<String, Object> compilerParams) {
        this.processCompilerParams(compilerParams);
    }

    private void processCompilerParams(Map<String, Object> compilerParams) {
        
        // filtering compilation customizers class names
        if (compilerParams.containsKey(GROOVY2_COMPILER_PARAMS_CUSTOMIZERS)) {
            Object customizers = compilerParams.get(GROOVY2_COMPILER_PARAMS_CUSTOMIZERS);

            if (customizers instanceof List) {
                for (Object customizerClassName: (List<?>) customizers) {
                    if (customizerClassName instanceof String) {
                        this.customizerClassNames.add((String)customizerClassName);
                    }
                }
            }
        }
    }

    private CompilationCustomizer getCustomizerInstanceFromString(String className, JBossModuleClassLoader moduleClassLoader) {
        CompilationCustomizer instance = null;

        try {
            Class<?> klass = moduleClassLoader.loadClass(className);
            instance = (CompilationCustomizer)klass.newInstance();
        } 
        catch (InstantiationException | IllegalAccessException | ClassNotFoundException | ClassCastException e) {
            e.printStackTrace();
        }
        return instance;
    }

    @Override
    public boolean shouldCompile(ScriptArchive archive) {
       return archive.getModuleSpec().getCompilerPluginIds().contains(GROOVY2_COMPILER_ID);
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader, Path compilationRootDir)
        throws ScriptCompilationException, IOException {
        
        List<CompilationCustomizer> customizers = new LinkedList<CompilationCustomizer>();

        for (String klassName: this.customizerClassNames) {
            CompilationCustomizer instance = this.getCustomizerInstanceFromString(klassName, moduleClassLoader);
            if (instance != null ) {
                customizers.add(instance);
            }
        }

        CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        config.addCompilationCustomizers(customizers.toArray(new CompilationCustomizer[0]));

         new Groovy2CompilerHelper(compilationRootDir)
            .addScriptArchive(archive)
            .withParentClassloader(moduleClassLoader)
            .withConfiguration(config)
            .compile();
        return Collections.emptySet();
    }
}

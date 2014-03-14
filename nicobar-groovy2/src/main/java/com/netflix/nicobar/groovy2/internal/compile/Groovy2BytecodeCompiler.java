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
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;
import com.netflix.nicobar.groovy2.plugin.Groovy2CompilerPlugin;

/**
 * A Groovy2 {@link ScriptArchiveCompiler} that loads compiled groovy classes, 
 * rather than compile from source.  
 *
 */
public class Groovy2BytecodeCompiler implements ScriptArchiveCompiler {
    
    @Override
    public String getId() {
       return Groovy2CompilerPlugin.GROOVY2_BYTECODE_COMPILER_ID;
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader)
        throws ScriptCompilationException, IOException {
        HashSet<Class<?>> addedClasses = new HashSet<Class<?>>(archive.getArchiveEntryNames().size());
        for (String entryName : archive.getArchiveEntryNames()) {
            if (!entryName.endsWith(".class"))
                continue;
            
            URL archiveEntry = archive.getEntry(entryName);
            byte [] classBytes = IOUtils.toByteArray(archiveEntry.openStream());
            String classEntry = entryName.replace(".class", "").replace("/", ".");
            Class<?> addedClass = moduleClassLoader.addClassBytes(classEntry, classBytes);
            addedClasses.add(addedClass); 
        }
                
        return Collections.unmodifiableSet(addedClasses);
    }
}

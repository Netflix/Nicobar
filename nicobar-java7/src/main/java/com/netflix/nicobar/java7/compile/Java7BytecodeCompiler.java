package com.netflix.nicobar.java7.compile;

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
import com.netflix.nicobar.java7.plugin.Java7CompilerPlugin;

/**
 * A Java7 {@link ScriptArchiveCompiler} that loads compiled java classes.  
 *
 */
public class Java7BytecodeCompiler implements ScriptArchiveCompiler {
    
    @Override
    public String getId() {
       return Java7CompilerPlugin.JAVA7_BYTECODE_COMPILER_ID;
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

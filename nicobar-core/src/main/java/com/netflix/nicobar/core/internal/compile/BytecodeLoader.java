package com.netflix.nicobar.core.internal.compile;

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

/**
 * A {@link ScriptArchiveCompiler} that loads java bytecode from .class files in a {@link ScriptArchive}.
 *
 * @author Vasanth Asokan
 */
public class BytecodeLoader implements ScriptArchiveCompiler {

    /**
     * Compile (load from) an archive, if it contains any .class files.
     */
    @Override
    public boolean shouldCompile(ScriptArchive archive) {

        Set<String> entries = archive.getArchiveEntryNames();
        boolean shouldCompile = false;
        for (String entry: entries) {
            if (entry.endsWith(".class")) {
                shouldCompile = true;
            }
        }

        return shouldCompile;
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader)
            throws ScriptCompilationException, IOException {
        HashSet<Class<?>> addedClasses = new HashSet<Class<?>>(archive.getArchiveEntryNames().size());
        for (String entry : archive.getArchiveEntryNames()) {
            if (!entry.endsWith(".class")) {
                continue;
            }

            URL archiveEntry = archive.getEntry(entry);
            byte [] classBytes = IOUtils.toByteArray(archiveEntry.openStream());
            String classEntry = entry.replace(".class", "").replace("/", ".");
            Class<?> addedClass = moduleClassLoader.addClassBytes(classEntry, classBytes);
            addedClasses.add(addedClass);
        }

        return Collections.unmodifiableSet(addedClasses);
    }
}

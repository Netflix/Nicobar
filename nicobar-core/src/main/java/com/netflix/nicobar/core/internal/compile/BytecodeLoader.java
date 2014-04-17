package com.netflix.nicobar.core.internal.compile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader, Path targetDir)
            throws ScriptCompilationException, IOException {
        HashSet<Class<?>> addedClasses = new HashSet<Class<?>>(archive.getArchiveEntryNames().size());

        for (String entry : archive.getArchiveEntryNames()) {
            if (!entry.endsWith(".class")) {
                continue;
            }
            String entryName = entry.replace(".class", "").replace("/", ".");
            try {
                // Load from the underlying archive class resource
                Class<?> addedClass = moduleClassLoader.loadClassLocal(entryName, true);
                addedClasses.add(addedClass);
            } catch (Exception e) {
                throw new ScriptCompilationException("Unable to load class: " + entryName, e);
            }

            moduleClassLoader.addClasses(addedClasses);
        }

        return Collections.unmodifiableSet(addedClasses);
    }
}

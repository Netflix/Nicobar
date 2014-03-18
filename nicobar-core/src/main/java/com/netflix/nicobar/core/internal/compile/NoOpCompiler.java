package com.netflix.nicobar.core.internal.compile;

import java.util.Collections;
import java.util.Set;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;

/**
 * A {@link ScriptArchiveCompiler} that does nothing. Intended for use during testing.
 *
 * @author Vasanth Asokan
 */
public class NoOpCompiler implements ScriptArchiveCompiler {

    @Override
    public boolean shouldCompile(ScriptArchive archive) {
        return true;
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader) {
        return Collections.<Class<?>>emptySet();
    }
}

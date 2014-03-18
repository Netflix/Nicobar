package com.netflix.nicobar.core.plugin;

import java.util.Collections;
import java.util.Set;

import com.netflix.nicobar.core.internal.compile.BytecodeLoader;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;

/**
 * A {@link ScriptCompilerPlugin} for script archives that include java bytecode.
 *
 * @author Vasanth Asokan
 */
public class BytecodeLoadingPlugin implements ScriptCompilerPlugin {

    public static final String PLUGIN_ID = "bytecode";

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers() {
        return Collections.singleton(new BytecodeLoader());
    }
}

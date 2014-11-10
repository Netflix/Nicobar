package com.netflix.nicobar.core.plugin;

import java.util.Collections;
import java.util.Set;

import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.internal.compile.NoOpCompiler;

public class NashornScriptCompilerPlugin implements ScriptCompilerPlugin {
    public static final String PLUGIN_ID = "nolang";

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers() {
        return Collections.singleton(new NoOpCompiler());
    }

}

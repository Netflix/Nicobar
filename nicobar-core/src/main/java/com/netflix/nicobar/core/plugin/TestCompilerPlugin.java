package com.netflix.nicobar.core.plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.internal.compile.NoOpCompiler;

/**
 * A {@link ScriptCompilerPlugin} for script archives with a no-op compiler included.
 * Intended for use during testing.
 *
 * @author Vasanth Asokan
 */
public class TestCompilerPlugin implements ScriptCompilerPlugin {

    public static final String PLUGIN_ID = "nolang";

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers(Map<String, Object> compilerParams) {
        return Collections.singleton(new NoOpCompiler());
    }
}

package com.netflix.nicobar.core.plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.netflix.nicobar.core.internal.compile.NoOpCompiler;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;

/**
 * A {@link ScriptCompilerPlugin} for script archives with a no-op compiler included.
 * Intended for use during testing.
 *
 * @author Vasanth Asokan
 */
public class NoOpCompilerPlugin implements ScriptCompilerPlugin {

    public static final String PLUGIN_ID = "nolang";

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers(Map<String, Object> pluginParams) {
        return Collections.singleton(new NoOpCompiler());
    }
}

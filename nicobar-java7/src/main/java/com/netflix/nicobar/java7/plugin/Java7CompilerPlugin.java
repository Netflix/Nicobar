package com.netflix.nicobar.java7.plugin;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.plugin.ScriptCompilerPlugin;
import com.netflix.nicobar.java7.compile.Java7BytecodeCompiler;


/**
 * Factory class for the Java 7 language plug-in
 *
 */
public class Java7CompilerPlugin implements ScriptCompilerPlugin {

    /**
     * The compiler (loader) for java7 compiled bytecode.
     */
    public static final String JAVA7_BYTECODE_COMPILER_ID = "java7-bytecode";

    public Java7CompilerPlugin() {
    }

    @Override
    public Set<? extends ScriptArchiveCompiler> getCompilers() {
        HashSet<ScriptArchiveCompiler> compilers = new HashSet<ScriptArchiveCompiler>();
        Collections.addAll(compilers, new Java7BytecodeCompiler());
        return Collections.unmodifiableSet(compilers);
    }
}



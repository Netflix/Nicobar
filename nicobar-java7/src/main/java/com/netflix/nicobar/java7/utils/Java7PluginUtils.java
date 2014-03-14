package com.netflix.nicobar.java7.utils;
import java.nio.file.Path;

import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.utils.ClassPathUtils;

/**
 * Utility class for working with nicobar-java7
 */
public class Java7PluginUtils {

    private static final String NICOBAR_JAVA7_PLUGIN_CLASS = "com.netflix.nicobar.java7.plugin.Java7CompilerPlugin";
    private static final String NICOBAR_JAVA7_LANG_RUNTIME = "Java7Runtime";
    
    /**
     * Helper method to orchestrate commonly required setup of nicobar-java7, and return a java7 compiler spec.
     * @return a {@link ScriptCompilerPluginSpec} that is instantiated for the Java7 language, and is specified to 
     *         depend on nicobar-java7.
     */
    public static ScriptCompilerPluginSpec getCompilerSpec() {
        String resourceName = ClassPathUtils.classNameToResourceName(NICOBAR_JAVA7_PLUGIN_CLASS);
        Path nicobarJava7PluginPath = ClassPathUtils.findRootPathForResource(resourceName, 
                Java7PluginUtils.class.getClassLoader());
        if (nicobarJava7PluginPath == null) {
            throw new IllegalStateException("couldn't find nicobar-java7 in the classpath.");
        }
        
        // Create the compiler spec
        ScriptCompilerPluginSpec compilerSpec = new ScriptCompilerPluginSpec.Builder(NICOBAR_JAVA7_LANG_RUNTIME)
            .withPluginClassName(NICOBAR_JAVA7_PLUGIN_CLASS)
            .addRuntimeResource(nicobarJava7PluginPath)
            .build();

        return compilerSpec;
    }
}

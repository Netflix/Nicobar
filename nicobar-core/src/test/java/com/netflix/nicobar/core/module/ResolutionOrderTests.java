package com.netflix.nicobar.core.module;

import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_DEPENDENT;
import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_SERVICE;
import static org.testng.AssertJUnit.assertEquals;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import org.jboss.modules.ModuleLoadException;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.plugin.BytecodeLoadingPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.testutil.CoreTestResourceUtil;

/**
 * Unit tests to test classloader resolution order
 *
 * @author Vasanth Asokan
 */
public class ResolutionOrderTests {
    private ScriptModuleLoader moduleLoader;

    /**
     * This test will test that a class found in the application classpath,
     * will override a class defined in a downstream module.
     *
     * In other words, if the module dependency is like this
     *
     *  A -> B -> [App Classpath]
     *
     *  and if both B, and [App Classpath] contain a class Foo,
     *  a reference to Foo in module A, will be resolved from the [App Classpath]
     *  and not from its immediate parent module.
     *
     *  This is standard java classloader resolution. A class is resolved as far up
     *  as possible, in the classloader hierarchy.
     *
     * @throws Exception
     */
    @Test
    public void testAppClasspathPrecedence() throws Exception {
        setupModuleLoader(Collections.singleton("com/netflix/nicobar/test"));
        setupDependentModules();
        ScriptModule dependentModule = moduleLoader.getScriptModule("dependent");
        Class<?> dependentClass = ScriptModuleUtils.findClass(dependentModule, "com.netflix.nicobar.test.Dependent");
        Method m = dependentClass.getMethod("execute");
        String result = (String)m.invoke(null);
        assertEquals("From App Classpath", result);
    }

    /**
     * This test shows, that the only way to resolve a class from a module
     * instead of the application classpath is to black list it from
     * the script module loader's application package list.
     *
     * @throws Exception
     */
    @Test
    public void testAppClasspathBlacklist() throws Exception {
        // Blacklist all packages from application classpath
        setupModuleLoader(Collections.<String>emptySet());
        setupDependentModules();
        ScriptModule dependentModule = moduleLoader.getScriptModule("dependent");
        Class<?> dependentClass = ScriptModuleUtils.findClass(dependentModule, "com.netflix.nicobar.test.Dependent");
        Method m = dependentClass.getMethod("execute");
        String result = (String)m.invoke(null);
        assertEquals("From Module", result);
    }

    private void setupDependentModules() throws Exception {
        Path dependentPath = CoreTestResourceUtil.getResourceAsPath(TEST_DEPENDENT);
        Path servicePath = CoreTestResourceUtil.getResourceAsPath(TEST_SERVICE);

        ScriptArchive serviceArchive = new JarScriptArchive.Builder(servicePath)
            .setModuleSpec(new ScriptModuleSpec.Builder("service")
                .addCompilerPluginId(BytecodeLoadingPlugin.PLUGIN_ID)
                .build())
            .build();

        ScriptArchive dependentArchive = new JarScriptArchive.Builder(dependentPath)
            .setModuleSpec(new ScriptModuleSpec.Builder("dependent")
                    .addCompilerPluginId(BytecodeLoadingPlugin.PLUGIN_ID)
                    .addModuleDependency("service")
                    .build())
            .build();

        moduleLoader.updateScriptArchives(Collections.singleton(serviceArchive));
        moduleLoader.updateScriptArchives(Collections.singleton(dependentArchive));
    }

    private void setupModuleLoader(Set<String> appPackages) throws ModuleLoadException, IOException {
        moduleLoader = new ScriptModuleLoader.Builder()
        .addPluginSpec(new ScriptCompilerPluginSpec.Builder(BytecodeLoadingPlugin.PLUGIN_ID)
            .withPluginClassName(BytecodeLoadingPlugin.class.getName()).build())
        .addAppPackages(appPackages)
        .build();
    }
}

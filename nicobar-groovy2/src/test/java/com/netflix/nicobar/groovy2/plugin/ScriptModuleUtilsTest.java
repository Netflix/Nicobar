package com.netflix.nicobar.groovy2.plugin;


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.testng.annotations.Test;

import com.google.common.collect.Sets;
import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.PathScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleLoader;
import com.netflix.nicobar.core.module.ScriptModuleUtils;
import com.netflix.nicobar.core.plugin.BytecodeLoadingPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.groovy2.internal.compile.Groovy2Compiler;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil.TestScript;

/**
 * Unit tests for {@link ScriptModuleUtils}
 *
 * @author Vasanth Asokan
 */
public class ScriptModuleUtilsTest {

    private ScriptModuleLoader moduleLoader;

    /**
     * Convert a groovy based ScriptModule to a bytecode ScriptArchive
     * using {@link ScriptModuleUtils.toCompiledScriptArchive()} and
     * ensure that it works the same.
     *
     * @throws Exception
     */
    @Test
    public void testScriptModuleConversion() throws Exception {
        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.HELLO_WORLD);
        ScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.HELLO_WORLD.getScriptPath())
            .setModuleSpec(createGroovyModuleSpec(TestScript.HELLO_WORLD.getModuleId()).build())
            .build();
        moduleLoader = createGroovyModuleLoader().build();
        moduleLoader.updateScriptArchives(Collections.singleton(scriptArchive));

        // locate the class file in the module and execute it
        ScriptModule scriptModule = moduleLoader.getScriptModule(TestScript.HELLO_WORLD.getModuleId());
        assertNotNull(scriptModule);
        Path tmpDir = Files.createTempDirectory("ScriptModuleUtilsTest");
        Path convertedJarPath = tmpDir.resolve("converted.jar");
        ScriptModuleUtils.toCompiledScriptArchive(scriptModule, convertedJarPath, Sets.newHashSet(".class", ".groovy"));
        moduleLoader.removeScriptModule(ModuleId.create(TestScript.HELLO_WORLD.getModuleId()));

        // Now load the module again from the the converted jar archive
        JarScriptArchive convertedJarArchive = new JarScriptArchive.Builder(convertedJarPath).build();
        moduleLoader.updateScriptArchives(Collections.singleton(convertedJarArchive));
        scriptModule = moduleLoader.getScriptModule(TestScript.HELLO_WORLD.getModuleId());
        assertNotNull(scriptModule);
        Class<?> targetClass = ScriptModuleUtils.findClass(scriptModule, "HelloWorld");
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello, World!");
    }

    private ScriptModuleLoader.Builder createGroovyModuleLoader() throws Exception {
        // create the groovy plugin spec. this plugin specified a new module and classloader called "Groovy2Runtime"
        // which contains the groovy-all-2.1.6.jar and the nicobar-groovy2 project.
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder(Groovy2Compiler.GROOVY2_COMPILER_ID)
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyRuntime())
            .addRuntimeResource(GroovyTestResourceUtil.getGroovyPluginLocation())
            // hack to make the gradle build work. still doesn't seem to properly instrument the code
            // should probably add a classloader dependency on the system classloader instead
            .addRuntimeResource(GroovyTestResourceUtil.getCoberturaJar(getClass().getClassLoader()))
            .withPluginClassName(Groovy2CompilerPlugin.class.getName())
            .build();

        // Create a compiler spec for the bytecode loading plugin
        ScriptCompilerPluginSpec bytecodeCompilerSpec = new ScriptCompilerPluginSpec.Builder(BytecodeLoadingPlugin.PLUGIN_ID)
            .withPluginClassName(BytecodeLoadingPlugin.class.getName())
            .build();

        // create and start the builder with the plugin
        return new ScriptModuleLoader.Builder().addPluginSpec(pluginSpec).addPluginSpec(bytecodeCompilerSpec);
    }

    /**
     * Create a module spec builder with pre-populated groovy dependency
     */
    private ScriptModuleSpec.Builder createGroovyModuleSpec(String moduleId) {
        return new ScriptModuleSpec.Builder(moduleId)
            .addCompilerPluginId(Groovy2CompilerPlugin.PLUGIN_ID);
    }
}

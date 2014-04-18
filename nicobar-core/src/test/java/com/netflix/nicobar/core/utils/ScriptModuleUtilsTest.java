package com.netflix.nicobar.core.utils;


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.modules.ModuleLoadException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.Sets;
import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleLoader;
import com.netflix.nicobar.core.module.ScriptModuleUtils;
import com.netflix.nicobar.core.plugin.BytecodeLoadingPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;

/**
 * Unit tests for {@link ScriptModuleUtils}
 *
 * @author Vasanth Asokan
 */
public class ScriptModuleUtilsTest {

    private ScriptModuleLoader moduleLoader;

    @BeforeClass
    public void setup() throws ModuleLoadException, IOException {
        ScriptCompilerPluginSpec pluginSpec = getCompilerSpec();

        // Create a set of app packages to allow access by the compilers, as well as the scripts.
        // We take a hammer approach and just exclude all com/netflix from the set of app packages exposed.
        Set<String> excludes = new HashSet<String>();
        Collections.addAll(excludes, "com/netflix");
        Set<String> pathSet = ClassPathUtils.scanClassPathWithExcludes(System.getProperty("java.class.path"),
                Collections.<String> emptySet(),
                excludes);

        moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(pluginSpec)
            // TODO: The BytecodeLoadingPlugin seems to work without the app package filter
            // that we add below. This is likely something in our system, allowing packages to leak.
            // Need to follow up and see how IOUtils resolves correctly in the BytecodeLoadingPlugin
            // without an app package filter as below.
            .addAppPackages(pathSet)
            .build();
    }

    @Test
    public void testScriptModuleConversion() throws Exception {
        URL jarPath = getClass().getClassLoader().getResource("testmodules/hellohelper.jar");

        JarScriptArchive jarArchive = new JarScriptArchive.Builder(Paths.get(jarPath.getFile()))
            .build();
        ModuleId moduleId = ModuleId.create("hellohelper");
        moduleLoader.updateScriptArchives(Collections.singleton((ScriptArchive)jarArchive));
        ScriptModule module = moduleLoader.getScriptModule(moduleId);
        assertNotNull(module);

        Path tmpDir = Files.createTempDirectory("ScriptModuleUtilsTest");
        Path convertedJarPath = tmpDir.resolve("converted.jar");
        ScriptModuleUtils.toCompiledScriptArchive(module, convertedJarPath, Sets.newHashSet(".class", ".java"));
        moduleLoader.removeScriptModule(moduleId);

        // Ensure that the converted archive works just as well as the new archive
        JarScriptArchive convertedJarArchive = new JarScriptArchive.Builder(convertedJarPath).build();
        moduleLoader.updateScriptArchives(Collections.singleton(convertedJarArchive));
        module = moduleLoader.getScriptModule(moduleId);
        assertNotNull(module);
        Class<?> targetClass = ScriptModuleUtils.findClass(module, "com.netflix.nicobar.test.HelloHelper");
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("execute");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello Nicobar World!");
    }

    private ScriptCompilerPluginSpec getCompilerSpec() {
        // Create a compiler spec for the bytecode loading plugin
        ScriptCompilerPluginSpec compilerSpec = new ScriptCompilerPluginSpec.Builder(BytecodeLoadingPlugin.PLUGIN_ID)
            .withPluginClassName(BytecodeLoadingPlugin.class.getName())
            .build();

        return compilerSpec;
    }
}

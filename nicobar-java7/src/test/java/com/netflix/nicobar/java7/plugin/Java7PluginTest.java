package com.netflix.nicobar.java7.plugin;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.modules.ModuleLoadException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.module.ScriptModule;
import com.netflix.nicobar.core.module.ScriptModuleLoader;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.utils.ClassPathUtils;
import com.netflix.nicobar.java7.utils.Java7PluginUtils;


/**
 * Tests for the Java7 language plugin.
 */
public class Java7PluginTest {

    private ScriptModuleLoader moduleLoader;
    
    @BeforeTest
    public void setup() throws ModuleLoadException {
        ScriptCompilerPluginSpec pluginSpec = Java7PluginUtils.getCompilerSpec();
        
        // Create a set of app packages to allow access by the compilers, as well as the scripts.
        Set<String> excludes = new HashSet<String>();
        Collections.addAll(excludes, "com/netflix");
        Set<String> pathSet = ClassPathUtils.scanClassPathWithExcludes(System.getProperty("java.class.path"), 
                Collections.<String> emptySet(), 
                excludes);
        
        moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(pluginSpec)
            .addAppPackages(pathSet)
            .build();
    }
    
    @Test
    public void testHelloJar() throws Exception {
        URL jarPath = getClass().getClassLoader().getResource("testmodules/helloworld/helloworld.jar");

        JarScriptArchive jarArchive = new JarScriptArchive.Builder(Paths.get(jarPath.getFile()))
            .build();
        moduleLoader.updateScriptArchives(Collections.singleton((ScriptArchive)jarArchive));
        ScriptModule module = moduleLoader.getScriptModule("helloworld");
        assertNotNull(module);
        
        Class<?> targetClass = findClass(module, "com.netflix.nicobar.java7.example.Helloworld");
        assertNotNull(targetClass);
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("sayHello");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello world!");
    }

    @Test
    public void testDepmodule() throws Exception {
        URL jarPath = getClass().getClassLoader().getResource("testmodules/depmodule/depmodule.jar");
        JarScriptArchive jarArchive = new JarScriptArchive.Builder(Paths.get(jarPath.getFile()))
            .build();
        URL depJarPath = getClass().getClassLoader().getResource("testmodules/helloworld/helloworld.jar");
        JarScriptArchive depArchive = new JarScriptArchive.Builder(Paths.get(depJarPath.getFile()))
            .build();
        Set<ScriptArchive> archives = new HashSet<ScriptArchive>();
        Collections.<ScriptArchive>addAll(archives, depArchive, jarArchive);
        moduleLoader.updateScriptArchives(archives);
        ScriptModule module = moduleLoader.getScriptModule("depmodule");
        assertNotNull(module);
        
        Class<?> targetClass = findClass(module, "com.netflix.nicobar.java7.example.DepModule");
        assertNotNull(targetClass);
        Object instance = targetClass.newInstance();
        Method method = targetClass.getMethod("getMessage");
        String message = (String)method.invoke(instance);
        assertEquals(message, "Hello world!");
    }
    
    private Class<?> findClass(ScriptModule module, String className) {
        Set<Class<?>> classes = module.getLoadedClasses();
        Class<?> targetClass = null;
        for (Class<?> clazz : classes) {
            if (clazz.getName().equals(className)) {
                targetClass = clazz;
                break;
            }
        }
        
        return targetClass;
    }
}

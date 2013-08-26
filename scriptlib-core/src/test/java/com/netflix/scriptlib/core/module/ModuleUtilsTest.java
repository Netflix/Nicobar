package com.netflix.scriptlib.core.module;

import static com.netflix.scriptlib.core.testutil.CoreTestResourceUtil.TestResource.TEST_TEXT_JAR;
import static com.netflix.scriptlib.core.testutil.CoreTestResourceUtil.TestResource.TEST_TEXT_PATH;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.testng.TestNG;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.netflix.scriptlib.core.archive.JarScriptArchive;
import com.netflix.scriptlib.core.archive.PathScriptArchive;
import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPlugin;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.scriptlib.core.testutil.CoreTestResourceUtil;
import com.netflix.scriptlib.core.testutil.TestModuleLoader;


/**
 * Unit tests for {@link ModuleUtils}
 *
 * @author James Kojo
 */
public class ModuleUtilsTest {
    private static final String METADATA_NAME = "TestMetadataName";
    private static final String METADATA_VALUE = "TestMetadataValue";
    @BeforeClass
    public void setup() {
        //Module.setModuleLogger(new StreamModuleLogger(System.err));
    }

    /**
     * Verify that the module creates the expected set of dependencies for a {@link ScriptCompilerPlugin}
     */
    @Test
    public void testExpectedPluginDependencies() throws Exception {
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder("TestPlugin", 1)
            .addMetatdata(METADATA_NAME, METADATA_VALUE)
            .build();
        ModuleIdentifier pluginId = ModuleUtils.getModuleId(pluginSpec);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(pluginId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, pluginSpec);

        TestModuleLoader moduleLoader = new TestModuleLoader(moduleSpecBuilder.create());
        Module module = moduleLoader.loadModule(pluginId);
        assertNotNull(module);
        ModuleClassLoader moduleClassLoader = module.getClassLoader();

        // verify the metadata was transfered
        assertEquals(module.getProperty(METADATA_NAME), METADATA_VALUE);

        // verify the module can import the core classes
        assertNotNull(moduleClassLoader.loadClass(ScriptCompilerPlugin.class.getName()));

        // verify the module can find the JDK classes
        assertNotNull(moduleClassLoader.loadClass("org.w3c.dom.Element"));

        // verify that nothing else from the classpath leaked through
        assertClassNotFound(TestNG.class.getName(), moduleClassLoader);
    }

    /**
     * Verify that the module creates the expected set of dependencies for a {@link JarScriptArchive}
     */
    @Test
    public void testJarResources() throws Exception {
        Path jarPath = CoreTestResourceUtil.getResourceAsPath(TEST_TEXT_JAR);
        ScriptArchive jarScriptArchive = new JarScriptArchive.Builder(TEST_TEXT_JAR.name(), jarPath)
            .addMetadata(METADATA_NAME, METADATA_VALUE)
            .build();
        ModuleIdentifier pluginId = ModuleUtils.getModuleId(jarScriptArchive);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(pluginId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, jarScriptArchive);

        TestModuleLoader moduleLoader = new TestModuleLoader(moduleSpecBuilder.create());
        Module module = moduleLoader.loadModule(pluginId);
        ModuleClassLoader moduleClassLoader = module.getClassLoader();

        // verify the metadata was transfered
        assertEquals(module.getProperty(METADATA_NAME), METADATA_VALUE);
        // verify that the archive resource match exactly the module resources
        Set<String> actualPaths = getResourcePaths(moduleClassLoader);

        assertEquals(actualPaths, TEST_TEXT_JAR.getContentPaths());
    }

    /**
     * Verify that the module creates the expected set of dependencies for a {@link PathScriptArchive}
     */
    @Test
    public void testPathResources() throws Exception {
        Path jarPath = CoreTestResourceUtil.getResourceAsPath(TEST_TEXT_PATH);
        ScriptArchive jarScriptArchive = new PathScriptArchive.Builder(TEST_TEXT_PATH.name(), jarPath)
            .addMetadata(METADATA_NAME, METADATA_VALUE)
            .build();
        ModuleIdentifier pluginId = ModuleUtils.getModuleId(jarScriptArchive);
        ModuleSpec.Builder moduleSpecBuilder = ModuleSpec.build(pluginId);
        ModuleUtils.populateModuleSpec(moduleSpecBuilder, jarScriptArchive);

        TestModuleLoader moduleLoader = new TestModuleLoader(moduleSpecBuilder.create());
        Module module = moduleLoader.loadModule(pluginId);
        ModuleClassLoader moduleClassLoader = module.getClassLoader();

        // verify the metadata was transfered
        assertEquals(module.getProperty(METADATA_NAME), METADATA_VALUE);
        // verify that the archive resource match exactly the module resources
        Set<String> actualPaths = getResourcePaths(moduleClassLoader);

        assertEquals(actualPaths, TEST_TEXT_PATH.getContentPaths());
    }

    private void assertClassNotFound(String className, ModuleClassLoader moduleClassLoader) {
        Class<?> foundClass;
        try {
            foundClass = moduleClassLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            foundClass = null;
        }
        assertNull(foundClass);
    }

    private Set<String> getResourcePaths(ModuleClassLoader moduleClassLoader) {
        Set<String> result = new HashSet<String>();
        Iterator<Resource> resources = moduleClassLoader.iterateResources("", true);
        while (resources.hasNext()) {
            Resource resource = resources.next();
            result.add(resource.getName());
        }
        return result;
    }
}

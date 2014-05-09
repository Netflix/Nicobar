package com.netflix.nicobar.core.module;

import static org.testng.AssertJUnit.assertNotNull;
import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_CLASSPATH_DEPENDENT;
import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_DEPENDENCIES_DEPENDENT;
import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_DEPENDENCIES_PRIMARY;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;
import com.netflix.nicobar.core.plugin.BytecodeLoadingPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.testutil.CoreTestResourceUtil;

/**
 * Tests different conditions of interdependent modules.
 * @author Aaron Tull
 *
 */
public class ScriptModuleLoaderDependenciesTest {
    @Test(expectedExceptions=java.lang.LinkageError.class)
    public void testDependenciesExportFilterExcludesNonMatching() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters("path.to.public.interface", null);
        exerciseDependentModules(moduleLoader);

        ScriptModule primaryModule = moduleLoader.getScriptModule(TEST_DEPENDENCIES_DEPENDENT.getModuleId());
        JBossModuleClassLoader primaryModuleLoader = primaryModule.getModuleClassLoader();
        primaryModuleLoader.loadClass("impl.ManagerImpl");
    }

    @Test(expectedExceptions=java.lang.LinkageError.class)
    public void testDependenciesImportFilterExcludesNonMatching() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters(null, "path.to.public.interface");

        ScriptModule primaryModule = moduleLoader.getScriptModule(TEST_DEPENDENCIES_DEPENDENT.getModuleId());
        JBossModuleClassLoader primaryModuleLoader = primaryModule.getModuleClassLoader();
        primaryModuleLoader.loadClass("impl.ManagerImpl");
    }

    @Test
    public void testDependenciesImportFilterIncludesMatching() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters(null, "interfaces");
        exerciseDependentModules(moduleLoader);
    }

    @Test
    public void testDependenciesExportFilterIncludesMatching() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters("interfaces", null);
        exerciseDependentModules(moduleLoader);
    }

    @Test
    public void testDependenciesBothFiltersIncludeMatching() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters("interfaces", "interfaces");
        exerciseDependentModules(moduleLoader);
    }

    @Test(expectedExceptions=java.lang.LinkageError.class)
    public void testDependenciesImportCanOverrideExport() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters("interfaces", "public.interface");
        exerciseDependentModules(moduleLoader);
    }

    @Test(expectedExceptions=java.lang.LinkageError.class)
    public void testDependenciesExportCanOverrideImport() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters("public.interface", "interfaces");
        exerciseDependentModules(moduleLoader);
    }

    @Test
    public void testDependentModules() throws Exception {
        ScriptModuleLoader moduleLoader = setupDependentModulesWithFilters(null, null);
        exerciseDependentModules(moduleLoader);
    }

    private void exerciseDependentModules(ScriptModuleLoader moduleLoader) throws ClassNotFoundException,
        InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ScriptModule primaryModule = moduleLoader.getScriptModule(TEST_DEPENDENCIES_DEPENDENT.getModuleId());
        JBossModuleClassLoader primaryModuleLoader = primaryModule.getModuleClassLoader();
        Class<?> helperClass = primaryModuleLoader.loadClass("impl.HelperImpl");
        Class<?> helperInterface = primaryModuleLoader.loadClass("interfaces.Helper");
        Object helper = helperClass.newInstance();
        Method doWorkmethod = helperClass.getMethod("doWork");
        Object doWorkResult = doWorkmethod.invoke(helper);
        Assert.assertTrue(doWorkResult instanceof String);
        Assert.assertEquals(doWorkResult, "nothing");
        Class<?> managerClass = primaryModuleLoader.loadClass("impl.ManagerImpl");
        Object manager = managerClass.newInstance();
        Method superviseMethod = managerClass.getMethod("supervise", helperInterface);
        Object superviseResult = superviseMethod.invoke(manager, helper);
        Assert.assertTrue(superviseResult instanceof String);
        Assert.assertEquals(superviseResult, "impl.ManagerImpl supervising impl.HelperImpl doing nothing");
    }

    private ScriptModuleLoader setupDependentModulesWithFilters(String exportFilter, String importFilter)
        throws ModuleLoadException, IOException, Exception {
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder(BytecodeLoadingPlugin.PLUGIN_ID)
        .withPluginClassName(BytecodeLoadingPlugin.class.getName())
        .build();

        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(pluginSpec)
            .build();

        Path primaryJarPath = CoreTestResourceUtil.getResourceAsPath(TEST_DEPENDENCIES_PRIMARY);
        ScriptModuleSpec.Builder primarySpecBuilder = new ScriptModuleSpec.Builder(TEST_DEPENDENCIES_PRIMARY.getModuleId())
                .addModuleExportFilter(exportFilter)
                .addCompilerPluginId(BytecodeLoadingPlugin.PLUGIN_ID);
        final ScriptArchive primaryJarArchive = new JarScriptArchive.Builder(primaryJarPath)
                .setModuleSpec(primarySpecBuilder.build())
                .build();

        Path dependentJarPath = CoreTestResourceUtil.getResourceAsPath(TEST_DEPENDENCIES_DEPENDENT);
        ScriptModuleSpec.Builder dependentSpecBuilder = new ScriptModuleSpec.Builder(TEST_DEPENDENCIES_DEPENDENT.getModuleId())
            .addCompilerPluginId(BytecodeLoadingPlugin.PLUGIN_ID)
            .addModuleImportFilter(importFilter)
            .addModuleDependency(TEST_DEPENDENCIES_PRIMARY.getModuleId());
        final ScriptArchive dependentJarArchive = new JarScriptArchive.Builder(dependentJarPath)
            .setModuleSpec(dependentSpecBuilder.build())
            .build();

        moduleLoader.updateScriptArchives(Collections.unmodifiableSet(new HashSet<ScriptArchive>() {
            private static final long serialVersionUID = -5461608508917035441L;

            {
                add(primaryJarArchive);
                add(dependentJarArchive);
            }
        }));
        return moduleLoader;
    }

    private ScriptModuleLoader setupClassPathDependentWithFilter(String importFilter)
        throws ModuleLoadException, IOException, Exception {
        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder(BytecodeLoadingPlugin.PLUGIN_ID)
            .withPluginClassName(BytecodeLoadingPlugin.class.getName())
            .build();

        Set<String> packages = new HashSet<String>();
        packages.add("org/jboss/modules");
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(pluginSpec)
            .addAppPackages(packages )
            .build();

        Path primaryJarPath = CoreTestResourceUtil.getResourceAsPath(TEST_CLASSPATH_DEPENDENT);
        ScriptModuleSpec.Builder primarySpecBuilder = new ScriptModuleSpec.Builder(TEST_CLASSPATH_DEPENDENT.getModuleId())
                .addCompilerPluginId(BytecodeLoadingPlugin.PLUGIN_ID)
                .addAppImportFilter(importFilter);
        final ScriptArchive primaryJarArchive = new JarScriptArchive.Builder(primaryJarPath)
                .setModuleSpec(primarySpecBuilder.build())
                .build();

        moduleLoader.updateScriptArchives(Collections.unmodifiableSet(new HashSet<ScriptArchive>() {
            private static final long serialVersionUID = -5461608508917035441L;
            {
                add(primaryJarArchive);
            }
        }));
        return moduleLoader;
    }

    private void exerciseClasspathDependentModule(ScriptModuleLoader moduleLoader) throws ClassNotFoundException,
        InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ScriptModule module = moduleLoader.getScriptModule(TEST_CLASSPATH_DEPENDENT.getModuleId());
        JBossModuleClassLoader primaryModuleLoader = module.getModuleClassLoader();
        Class<?> dependentClass = primaryModuleLoader.loadClass("DependentClass");
        Object helper = dependentClass.newInstance();
        assertNotNull(helper);
    }


    @Test
    public void failsIfImportFilterExcludesNecessaryPathFromAppPackage() throws ModuleLoadException, IOException, Exception {
        ScriptModuleLoader moduleLoader = setupClassPathDependentWithFilter("java");
        boolean didFail = false;
        Module.forClass(Object.class);
        try {
            exerciseClasspathDependentModule(moduleLoader);
        } catch (java.lang.NoClassDefFoundError e) {
            Assert.assertTrue(e.getMessage().contains("org/jboss/modules/Module"));
            didFail = true;
        }

        Assert.assertTrue(didFail);
    }

    @Test
    public void passesIfImportFilterIncludesAllNecessaryPathsFromAppPackage() throws ModuleLoadException, IOException, Exception {
        ScriptModuleLoader moduleLoader = setupClassPathDependentWithFilter("org/jboss/modules");
        exerciseClasspathDependentModule(moduleLoader);
    }

}

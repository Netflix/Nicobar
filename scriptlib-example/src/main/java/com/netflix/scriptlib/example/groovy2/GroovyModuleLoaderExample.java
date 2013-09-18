/*
 *
 *  Copyright 2013 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.scriptlib.example.groovy2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.io.IOUtils;

import com.netflix.hystrix.Hystrix;
import com.netflix.scriptlib.core.execution.HystrixScriptModuleExecutor;
import com.netflix.scriptlib.core.execution.ScriptModuleExecutable;
import com.netflix.scriptlib.core.module.BaseScriptModuleListener;
import com.netflix.scriptlib.core.module.ScriptModule;
import com.netflix.scriptlib.core.module.ScriptModuleLoader;
import com.netflix.scriptlib.core.module.ScriptModuleUtils;
import com.netflix.scriptlib.core.persistence.PathScriptArchivePoller;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.scriptlib.core.utils.ClassPathUtils;
import com.netflix.scriptlib.groovy2.testutil.GroovyTestResourceUtil;

/**
 * Example of how to build a script runtime that polls for Groovy based archives on disk.
 * At the end of this example, there will be a the following classloaders
 *
 *<pre>
 *  Bootstrap Classloader  (/jre/lib) - virtual
 *  ExtClassLoader         (/jre/lib/exp)
 *  AppClassLoader         (scriptlib-core, scriptlib-example)
 *  Groovy2RuntimeModule   (scriptlib-groovy2, groovy-all.jar)
 *  HelloWorld             (HelloWorld.class)
 *  </pre>
 * @author James Kojo
 */
public class GroovyModuleLoaderExample {

    // module ID to use for the Groovy plugin
    private static final String GROOVY2_MODULE_ID = "Groovy2RuntimeModule";
    private static final String GROOVY2_COMPILER_PLUGIN_CLASS = "com.netflix.scriptlib.groovy2.plugin.Groovy2CompilerPlugin";

    // test script module info
    private static final String SCRIPT_MODULE_ID = "HelloWorld";
    private static final Path SCRIPT_RELATIVE_PATH = Paths.get("helloworld", "HelloWorld.groovy");
    private static final Path MODULE_SPEC_RELATIVE_PATH = Paths.get("helloworld", "moduleSpec.json");

    public static void main(String[] args) throws Exception {
        new GroovyModuleLoaderExample().runExample();
    }

    public void runExample() throws Exception {
        // setup a directory to deploy new archives to
        Path baseArchiveDir = Files.createTempDirectory(GroovyModuleLoaderExample.class.getSimpleName());
        // helper latch to coordinate the main thread for this example. you woudln't normally do this.
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        // simulate deploying a new archive by copying our test archive into the archive directory
        deployTestArchive(baseArchiveDir);

        // create and start the loader with the plugin
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder(GROOVY2_MODULE_ID) // configure Groovy plugin
                .addRuntimeResource(getGroovyRuntime())
                .addRuntimeResource(getGroovyPluginLocation())
                .withPluginClassName(GROOVY2_COMPILER_PLUGIN_CLASS)
                .build())
            .addPoller(new PathScriptArchivePoller(baseArchiveDir) , 5)  // add a poller to detect new archives
            .addListener(new BaseScriptModuleListener() {                // add an example listener for module updates
                public void moduleUpdated(ScriptModule newScriptModule, ScriptModule oldScriptModule) {
                    countDownLatch.countDown();
                }
            })
            .build();

        // the module loader has now started and is listening for new archives.
        countDownLatch.await();

        // the test module has now been compiled and is ready for execution.
        // create a closure which knows how to bind any request time inputs (if any) and execute the module.
        ScriptModuleExecutable<String> executable = new ScriptModuleExecutable<String>() {
            @SuppressWarnings({"rawtypes","unchecked"})
            @Override
            public String execute(ScriptModule scriptModule) throws Exception {
                // the script doesn't necessarily have to implement any specific interfaces, but it does need to
                // be compilable to a class.
                Class<Callable> callable = ScriptModuleUtils.findAssignableClass(scriptModule, Callable.class);
                Callable<String> instance = callable.newInstance();
                String result = instance.call();
                return result;
            }
        };

        // Execute it in a Hystrix command.
        HystrixScriptModuleExecutor<String> executor = new HystrixScriptModuleExecutor<String>("TestModuleExecutor");
        List<String> results = executor.executeModules(Collections.singletonList(SCRIPT_MODULE_ID), executable, moduleLoader);
        System.out.println("Module(s) have been executed. Output: " + results);

        // release the Hystrix resources
        Hystrix.reset();
    }

    /**
     * Locate the groovy-all-n.n.n.jar file on the classpath.
     *
     * The ScriptModuleLoader will attempt to load the scripting runtimes into their own classloaders.
     * To accomplish this, the loader requires the actual file location of the groovy runtime jar.
     * This method is an example of a strategy for locating the groovy jar file in a programmatic way.
     *
     * This strategy assumes that the classloader of this example application has the
     * script runtime somewhere in it's classpath.
     * This is not necessarily true of all applications, depending on disposition of the deployed application artifacts.
     *
     * It further assumes that the groovy runtime contains the file "groovy-release-info.properties"
     * which was true of the time of groovy-all-2.1.6.jar
     */
    public static Path getGroovyRuntime() {
        Path path = ClassPathUtils.findRootPathForResource("META-INF/groovy-release-info.properties", GroovyTestResourceUtil.class.getClassLoader());
        if (path == null) {
            throw new IllegalStateException("coudln't find groovy-all.n.n.n.jar in the classpath.");
        }
        return path;
    }

    /**
     * Locate the classpath root which contains the groovy2 plugin.
     *
     * The ScriptModuleLoader will load the groovy2 plugin into the same classlaoder as the
     * groovy runtime. To accomplish this, the loader requires the file location of the groovy2 plugin.
     * This method is an example strategy for locating the plugin's jar file in a programmatic way.
     *
     * This strategy assumes that the classloader of this example application has the plugin in it' classpath.
     * see {@link GroovyModuleLoaderExample#getGroovyRuntime()}.
     */
    public static Path getGroovyPluginLocation() {
        String resourceName = ClassPathUtils.classNameToResourceName(GROOVY2_COMPILER_PLUGIN_CLASS);
        Path path = ClassPathUtils.findRootPathForResource(resourceName, GroovyTestResourceUtil.class.getClassLoader());
        if (path == null) {
            throw new IllegalStateException("coudln't find groovy2 plugin jar in the classpath.");
        }
        return path;
    }

    /*
     * Copy the example script module files to the given output directory
     */
    private static void deployTestArchive(Path baseArchiveDir) throws IOException {
        InputStream scriptSource = GroovyModuleLoaderExample.class.getClassLoader().getResourceAsStream(SCRIPT_RELATIVE_PATH.toString());
        InputStream moduleSpecSource =  GroovyModuleLoaderExample.class.getClassLoader().getResourceAsStream(MODULE_SPEC_RELATIVE_PATH.toString());
        Path moduleRoot = Files.createTempDirectory("helloworld");
        Path scriptOutputPath = moduleRoot.resolve(SCRIPT_RELATIVE_PATH.getFileName());
        Path specOutputPath = moduleRoot.resolve(MODULE_SPEC_RELATIVE_PATH.getFileName());
        Files.copy(scriptSource, scriptOutputPath);
        Files.copy(moduleSpecSource, specOutputPath);
        IOUtils.closeQuietly(scriptSource);
        IOUtils.closeQuietly(moduleSpecSource);
        Files.createSymbolicLink(baseArchiveDir.resolve("helloworld"), moduleRoot);
    }
}

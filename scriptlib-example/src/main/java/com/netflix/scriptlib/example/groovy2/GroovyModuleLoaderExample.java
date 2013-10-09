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

import static com.netflix.scriptlib.example.groovy2.ExampleResourceLocator.GROOVY2_COMPILER_PLUGIN_CLASS;
import static com.netflix.scriptlib.example.groovy2.ExampleResourceLocator.GROOVY2_PLUGIN_ID;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import com.netflix.hystrix.Hystrix;
import com.netflix.scriptlib.core.execution.HystrixScriptModuleExecutor;
import com.netflix.scriptlib.core.execution.ScriptModuleExecutable;
import com.netflix.scriptlib.core.module.BaseScriptModuleListener;
import com.netflix.scriptlib.core.module.ScriptModule;
import com.netflix.scriptlib.core.module.ScriptModuleLoader;
import com.netflix.scriptlib.core.module.ScriptModuleUtils;
import com.netflix.scriptlib.core.persistence.ArchiveRepository;
import com.netflix.scriptlib.core.persistence.ArchiveRepositoryPoller;
import com.netflix.scriptlib.core.persistence.JarArchiveRepository;
import com.netflix.scriptlib.core.plugin.ScriptCompilerPluginSpec;

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
    // test script module info
    private static final String SCRIPT_MODULE_ID = "HelloWorld";
    private static final String ARCHIVE_JAR_NAME = "HelloWorld.jar";

    public static void main(String[] args) throws Exception {
        new GroovyModuleLoaderExample().runExample();
    }

    public void runExample() throws Exception {

        // create the loader with the groovy plugin
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder(GROOVY2_PLUGIN_ID) // configure Groovy plugin
                .addRuntimeResource(ExampleResourceLocator.getGroovyRuntime())
                .addRuntimeResource(ExampleResourceLocator.getGroovyPluginLocation())
                .withPluginClassName(GROOVY2_COMPILER_PLUGIN_CLASS)
                .build())
            .addListener(new BaseScriptModuleListener() {                // add an example listener for module updates
                public void moduleUpdated(ScriptModule newScriptModule, ScriptModule oldScriptModule) {
                    System.out.printf("Received module update event. newModule: %s,  oldModule: %s%n", newScriptModule, oldScriptModule);
                }
            })
            .build();

        // create an archive repository and wrap a poller around it to feed updates to the module loader
        Path baseArchiveDir = Files.createTempDirectory(GroovyModuleLoaderExample.class.getSimpleName());
        JarArchiveRepository repository = new JarArchiveRepository.Builder(baseArchiveDir).build();
        deployTestArchive(repository);
        ArchiveRepositoryPoller poller = new ArchiveRepositoryPoller.Builder(moduleLoader).build();
        poller.addRepository(repository, 30, TimeUnit.SECONDS, true);

        // the test module has now been compiled and is ready for execution.
        // create a closure which knows how to bind any request time inputs (if any) and execute the module.
        ScriptModuleExecutable<String> executable = new ScriptModuleExecutable<String>() {
            @Override
            public String execute(ScriptModule scriptModule) throws Exception {
                // the script doesn't necessarily have to implement any specific interfaces, but it does need to
                // be compilable to a class.
                Class<?> callable = ScriptModuleUtils.findAssignableClass(scriptModule, Callable.class);
                @SuppressWarnings("unchecked")
                Callable<String> instance = (Callable<String>) callable.newInstance();
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

    /*
     * Copy the example script module files to temporary directory and insert it into the repository
     */
    private static void deployTestArchive(ArchiveRepository repository) throws IOException {
        InputStream archiveJarIs = GroovyModuleLoaderExample.class.getClassLoader().getResourceAsStream(ARCHIVE_JAR_NAME);
        Path archiveToDeploy = Files.createTempFile(SCRIPT_MODULE_ID, ".jar");
        Files.copy(archiveJarIs, archiveToDeploy, StandardCopyOption.REPLACE_EXISTING);
        IOUtils.closeQuietly(archiveJarIs);

        repository.insertArchive(SCRIPT_MODULE_ID, archiveToDeploy, null);
    }
}

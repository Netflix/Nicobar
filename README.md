<img src="https://github.com/Netflix/Nicobar/blob/master/images/nicobar_logo_210x210.png">

# Nicobar: Dynamic Scripting and Module Loader Framework for Java

Nicobar is a dynamic scripting framework for java, driven by a powerful module loading system based
on [JBoss Modules](https://github.com/jboss-modules/jboss-modules). Scripts can be source, written in JVM
compatible languages (like Groovy), or can be compiled bytecode, in the form of .class
files. Scripts can be fetched from persistence dynamically, (compiled and) converted into modules,
and inserted in the correct place in a runtime module graph based upon module metadata.

## Full Documentation

See the [Wiki](https://github.com/Netflix/Nicobar/wiki/) for full documentation, examples, operational details and other information.

See the [Javadoc](http://netflix.github.com/Nicobar/javadoc) for the API.

## What does it do?

#### 1) Dynamically compile and load scripts

Dynamically compile and load JVM compatible script sources, or compiled bytecode archives into your
running JVM.
   
#### 2) Establish complex  module dependency graphs

Establish an arbitrary dependency graph between script modules, with the ability to filter imported
and exported packages from each module. Modules are isolated from each other via classloaders.  

#### 3) Provides useful management and persistence features

Persist and fetch script modules from pluggable repository implementations, including filesystem and
cassandra based ones. Use management interface to publish script archives to repositories. Query
published archives from repository.

## Hello Nicobar!

Here is how you initialize your the Nicobar script module loader to support Groovy scripts.

```java
public void initializeNicobar() throws Exception {
    // create the loader with the groovy plugin
    ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
        .addPluginSpec(new ScriptCompilerPluginSpec.Builder(GROOVY2_PLUGIN_ID) // configure Groovy plugin
            .addRuntimeResource(ExampleResourceLocator.getGroovyRuntime())
            .addRuntimeResource(ExampleResourceLocator.getGroovyPluginLocation())
            .withPluginClassName(GROOVY2_COMPILER_PLUGIN_CLASS)
            .build())
        .build();
}
```

You will typically have ArchiveRepository containing Nicobar scripts. The example below initializes
a repository that is laid out as directories at some file system path. Nicobar provides a repository
poller which can look for updates inside a repository, and load updated modules into the module
loader. 

```java
    // create an archive repository and wrap a poller around it to feed updates to the module loader
    Path baseArchiveDir = Paths.get("/tmp/archiveRepo");
    JarArchiveRepository repository = new JarArchiveRepository.Builder(baseArchiveDir).build();
    ArchiveRepositoryPoller poller = new ArchiveRepositoryPoller.Builder(moduleLoader).build();
    poller.addRepository(repository, 30, TimeUnit.SECONDS, true);
```
ScriptModules can be retrieved out of the module loader by name (and an optional version). Classes
 can be retrieved from ScriptModules by name, or by type and exercised: 

```java
ScriptModule module = moduleLoader.getScriptModule("hellomodule");
Class<?> callableClass = ScriptModuleUtils.findAssignableClass(module, Callable.class);
Callable<String> instance = (Callable<String>) callableClass.newInstance();
String result = instance.call();
```

More examples and information can be found in the [How To Use](https://github.com/Netflix/Nicobar/wiki/How-To-Use) section.

Example source code can be found in the [nicobar-examples](https://github.com/Netflix/Nicobar/tree/master/nicobar-example) subproject.

## Binaries

Binaries and dependency information for Maven, Ivy, Gradle and others can be found at [http://search.maven.org](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.netflix.Nicobar%22%20AND%20a%3A%22nicobar-core%22).

Example for Maven:

```xml
<dependency>
    <groupId>com.netflix.Nicobar</groupId>
    <artifactId>nicobar-core</artifactId>
    <version>x.y.z</version>
</dependency>
```
and for Ivy:

```xml
<dependency org="com.netflix.Nicobar" name="nicobar-core" rev="x.y.z" />
```

You need Java 6 or later.

## Build

To build:

```
$ git clone git@github.com:Netflix/Nicobar.git
$ cd Nicobar/
$ ./gradlew build
```

## Bugs and Feedback

For bugs, questions and discussions please use the [Github Issues](https://github.com/Netflix/Nicobar/issues).
 
## LICENSE

Copyright 2015 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

##  Notes

1. `nicobar-core` unit tests rely on test jars containing classes. In order for these to be
maintainable, there is a separate project `nicobar-core/nicobar-test-classes`. If you are modifying
the test classes, Make sure to run the `copyTestClassJars` task on `nicobar-test-classes`. This will
generate test resource jars in `nicobar-test-classes/build/testJars`. Manually copy the jars into
`nicobar-core/src/test/resources` overwriting any existing jars. 

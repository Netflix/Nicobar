package com.netflix.nicobar.core.plugin;

import com.google.common.collect.ImmutableSet;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Test for {@link ScriptCompilerPluginSpec}
 */
public class ScriptCompilerPluginSpecTest {

    final static String PLUGIN_ID = "ANY_PLUGIN_ID";

    Path rootPath;
    final String directories = "sublevel2/sublevel3/sublevel4";

    String[] files = {
            "rootPackage.jar",
            "file0.txt",
            "sublevel2/package2.jar",
            "sublevel2/file.txt",
            "sublevel2/sublevel3/package3.jar",
            "sublevel2/sublevel3/file.json",
            "sublevel2/sublevel3/sublevel4/package4.jar",
            "sublevel2/sublevel3/sublevel4/file.txt",
    };

    @BeforeTest
    public void setup() throws IOException {
        rootPath = Files.createTempDirectory("rootPath");
        Files.createDirectories(Paths.get(rootPath.toString(), directories));

        for (String fileName : files) {
            Files.createFile(Paths.get(rootPath.toString(), fileName));
        }
    }

    @AfterTest
    public void cleanup() throws IOException {
        Files.walkFileTree(rootPath.toAbsolutePath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Tests for adding multiple runtime resources
     *
     * @throws IOException
     */
    @Test
    public void testAddRuntimeResources() throws IOException {

        ScriptCompilerPluginSpec pluginSpec = new ScriptCompilerPluginSpec.Builder(PLUGIN_ID)
                .addRuntimeResources(ImmutableSet.of(
                        Paths.get(rootPath.toString(), files[0]),
                        Paths.get(rootPath.toString(), files[1]),
                        Paths.get(rootPath.toString(), files[2])
                ))
                .build();
        assertEquals(3, pluginSpec.getRuntimeResources().size());


        // pure jars + recursively = true
        pluginSpec = new ScriptCompilerPluginSpec.Builder(PLUGIN_ID)
                .addRuntimeResources(rootPath, Collections.singleton("jar"), true)
                .build();
        assertEquals(4, pluginSpec.getRuntimeResources().size());


        // files "txt" + recursively = false
        pluginSpec = new ScriptCompilerPluginSpec.Builder(PLUGIN_ID)
                .addRuntimeResources(rootPath, Collections.singleton("txt"), false)
                .build();
        assertEquals(1, pluginSpec.getRuntimeResources().size());
        assertTrue(pluginSpec.getRuntimeResources().iterator().next().endsWith("file0.txt"));


        // files "txt", "json" + recursively = true
        pluginSpec = new ScriptCompilerPluginSpec.Builder(PLUGIN_ID)
                .addRuntimeResources(rootPath, ImmutableSet.of("txt", "json"), true)
                .build();
        assertEquals(4, pluginSpec.getRuntimeResources().size());
    }

}
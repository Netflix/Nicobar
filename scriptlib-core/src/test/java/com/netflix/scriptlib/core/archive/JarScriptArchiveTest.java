package com.netflix.scriptlib.core.archive;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;


/**
 * Unit Tests for {@link JarScriptArchive}
 *
 * @author James Kojo
 */
public class JarScriptArchiveTest {
    private final static String TEXT_JAR_RESOURCE_NAME = "jars/test-text.jar";
    @Test
    public void testLoadTextJar() throws Exception {
        URL testJarUrl = getClass().getClassLoader().getResource(TEXT_JAR_RESOURCE_NAME);
        Path jarPath = Paths.get(testJarUrl.toURI()).toAbsolutePath();

        JarScriptArchive scriptArchive = new JarScriptArchive.Builder("test-txt", 1, jarPath).build();
        Set<String> archiveEntryNames = scriptArchive.getArchiveEntryNames();
        assertEquals(archiveEntryNames, new HashSet<String>(Arrays.asList("sub1/sub1.txt", "sub2/sub2.txt", "root.txt", "META-INF/MANIFEST.MF")));
        for (String entryName : archiveEntryNames) {
            URL entryUrl = scriptArchive.getEntry(entryName);
            assertNotNull(entryUrl);
            InputStream inputStream = entryUrl.openStream();
            String content = IOUtils.toString(inputStream, Charsets.UTF_8);
            assertNotNull(content);
        }
    }
}

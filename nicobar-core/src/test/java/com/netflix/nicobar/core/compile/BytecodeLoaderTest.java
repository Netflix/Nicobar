package com.netflix.nicobar.core.compile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.AssertJUnit.assertEquals;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;

/**
 * Tests for the BytecodeLoader.
 * @author Vasanth Asokan
 */
public class BytecodeLoaderTest {
    private Class<?> compiledClass;

    @BeforeMethod
    public void setup() {
        // Randomly assign some class
        compiledClass = this.getClass();
    }

    @Test
    public void testHelloworldArchive() throws Exception {
        URL jarPath = getClass().getClassLoader().getResource("testmodules/testmodule.jar");

        JarScriptArchive scriptArchive = new JarScriptArchive.Builder(Paths.get(jarPath.getFile()))
            .build();

        JBossModuleClassLoader moduleClassLoader = mock(JBossModuleClassLoader.class);
        BytecodeLoader loader = new BytecodeLoader();
        Mockito.doReturn(compiledClass).when(moduleClassLoader).addClassBytes(Mockito.anyString(), Mockito.any(byte[].class));

        Set<Class<?>> compiledClasses = loader.compile(scriptArchive, moduleClassLoader);

        verify(moduleClassLoader).addClassBytes("com.netflix.nicobar.test.Fake", "fake bytes".getBytes(Charset.forName("UTF-8")));
        assertEquals(compiledClasses.size(), 1);
        Iterator<Class<?>> iterator = compiledClasses.iterator();
        assertEquals(compiledClass, iterator.next());
    }
}

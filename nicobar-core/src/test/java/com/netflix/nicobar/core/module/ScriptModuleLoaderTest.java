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
package com.netflix.nicobar.core.module;

import static com.netflix.nicobar.core.testutil.CoreTestResourceUtil.TestResource.TEST_MODULE_SPEC_JAR;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.JarScriptArchive;
import com.netflix.nicobar.core.archive.ModuleId;
import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.archive.ScriptModuleSpec;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;
import com.netflix.nicobar.core.plugin.NoOpCompilerPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPlugin;
import com.netflix.nicobar.core.plugin.ScriptCompilerPluginSpec;
import com.netflix.nicobar.core.testutil.CoreTestResourceUtil;

/**
 * Unit tests for {@link ScriptModuleLoader}
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class ScriptModuleLoaderTest {
    // shared mock compiler. THis is needed because of the indirect construction style of the compiler plugin.
    private final static ScriptArchiveCompiler MOCK_COMPILER = mock(ScriptArchiveCompiler.class);

    @BeforeMethod
    public void resetMocks() {
        reset(MOCK_COMPILER);
    }

    @Test
    public void testLoadArchive() throws Exception {
        Path jarPath = CoreTestResourceUtil.getResourceAsPath(TEST_MODULE_SPEC_JAR);

        ScriptArchive scriptArchive = new JarScriptArchive.Builder(jarPath).build();
        when(MOCK_COMPILER.shouldCompile(Mockito.eq(scriptArchive))).thenReturn(true);
        when(MOCK_COMPILER.compile(Mockito.eq(scriptArchive), Mockito.any(JBossModuleClassLoader.class))).thenReturn(Collections.<Class<?>>emptySet());
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder(NoOpCompilerPlugin.PLUGIN_ID)
                .withPluginClassName(MockScriptCompilerPlugin.class.getName()).build())
            .build();
        moduleLoader.updateScriptArchives(Collections.singleton(scriptArchive));

        ModuleId moduleId = scriptArchive.getModuleSpec().getModuleId();
        ScriptModule scriptModule = moduleLoader.getScriptModule(moduleId);

        assertNotNull(scriptModule);
        assertEquals(scriptModule.getModuleId(), moduleId);
        JBossModuleClassLoader moduleClassLoader = scriptModule.getModuleClassLoader();
        for (String entryName : scriptArchive.getArchiveEntryNames()) {
            URL resourceUrl = moduleClassLoader.findResource(entryName, true);
            assertNotNull(resourceUrl, "couldn't find entry in the classloader: " + entryName);
        }
        verify(MOCK_COMPILER).shouldCompile(Mockito.eq(scriptArchive));
        verify(MOCK_COMPILER).compile(Mockito.eq(scriptArchive), Mockito.any(JBossModuleClassLoader.class));
        verifyNoMoreInteractions(MOCK_COMPILER);
    }


    @Test
    public void testBadModulSpec() throws Exception {
        final URL badJarUrl = new URL("file:///somepath/myMadJarName.jar");
        ScriptArchive badScriptArchive = new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").build(), 1) {
            @Override
            public URL getRootUrl() {
                return badJarUrl;
            }
        };
        ScriptModuleListener mockListener = createMockListener();
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder().addListener(mockListener).build();
        moduleLoader.updateScriptArchives(Collections.singleton(badScriptArchive));
        verify(mockListener).archiveRejected(Mockito.same(badScriptArchive), Mockito.same(ArchiveRejectedReason.ARCHIVE_IO_EXCEPTION), Mockito.any(Exception.class));
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testReloadWithUpdatedDepdencies() throws Exception {
        // original graph: A->B->C->D
        long originalCreateTime = 1000;
        Set<ScriptArchive> updateArchives = new HashSet<ScriptArchive>();
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").addCompilerPluginId("mockPlugin").addModuleDependency("B").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("B").addCompilerPluginId("mockPlugin").addModuleDependency("C").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("D").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("D").addCompilerPluginId("mockPlugin").build(), originalCreateTime));

        ScriptModuleListener mockListener = createMockListener();
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addListener(mockListener)
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder("mockPlugin")
                .withPluginClassName(MockScriptCompilerPlugin.class.getName()).build())
            .build();

        when(MOCK_COMPILER.shouldCompile(Mockito.any(ScriptArchive.class))).thenReturn(true);
        when(MOCK_COMPILER.compile(Mockito.any(ScriptArchive.class), Mockito.any(JBossModuleClassLoader.class))).thenReturn(Collections.<Class<?>>emptySet());
        moduleLoader.updateScriptArchives(updateArchives);

        // validate that they were compiled in reverse dependency order
        InOrder orderVerifier = inOrder(mockListener);
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("D", originalCreateTime), (ScriptModule)Mockito.isNull());
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("C", originalCreateTime), (ScriptModule)Mockito.isNull());
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("B", originalCreateTime), (ScriptModule)Mockito.isNull());
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("A", originalCreateTime), (ScriptModule)Mockito.isNull());
        orderVerifier.verifyNoMoreInteractions();

        // updated graph: D->C->B->A
        updateArchives.clear();
        long updatedCreateTime = 2000;
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("D").addCompilerPluginId("mockPlugin").addModuleDependency("C").build(), updatedCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("B").build(), updatedCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("B").addCompilerPluginId("mockPlugin").addModuleDependency("A").build(), updatedCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").addCompilerPluginId("mockPlugin").build(), updatedCreateTime));

        moduleLoader.updateScriptArchives(updateArchives);

        // validate that they were compiled in the updated reverse dependency order
        orderVerifier = inOrder(mockListener);
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("A", updatedCreateTime), moduleEquals("A", originalCreateTime));
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("B", updatedCreateTime), moduleEquals("B", originalCreateTime));
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("C", updatedCreateTime), moduleEquals("C", originalCreateTime));
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("D", updatedCreateTime), moduleEquals("D", originalCreateTime));
        orderVerifier.verifyNoMoreInteractions();

        // validate the post-condition of the module database
        assertEquals(moduleLoader.getScriptModule("A").getCreateTime(), updatedCreateTime);
        assertEquals(moduleLoader.getScriptModule("B").getCreateTime(), updatedCreateTime);
        assertEquals(moduleLoader.getScriptModule("C").getCreateTime(), updatedCreateTime);
        assertEquals(moduleLoader.getScriptModule("D").getCreateTime(), updatedCreateTime);
        assertEquals(moduleLoader.getAllScriptModules().size(),4);
    }

    @Test
    public void testRelinkDependents() throws Exception {
        // original graph: A->B->C->D
        long originalCreateTime = 1000;
        Set<ScriptArchive> updateArchives = new HashSet<ScriptArchive>();
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").addCompilerPluginId("mockPlugin").addModuleDependency("B").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("B").addCompilerPluginId("mockPlugin").addModuleDependency("C").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("D").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("D").addCompilerPluginId("mockPlugin").build(), originalCreateTime));

        ScriptModuleListener mockListener = createMockListener();
        when(MOCK_COMPILER.shouldCompile(Mockito.any(ScriptArchive.class))).thenReturn(true);
        when(MOCK_COMPILER.compile(Mockito.any(ScriptArchive.class), Mockito.any(JBossModuleClassLoader.class))).thenReturn(Collections.<Class<?>>emptySet());
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addListener(mockListener)
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder("mockPlugin")
                .withPluginClassName(MockScriptCompilerPlugin.class.getName()).build())
            .build();
        moduleLoader.updateScriptArchives(updateArchives);

        // don't need to re-validate since already validated in testReloadWithUpdatedDepdencies
        reset(mockListener);

        // update C. should cause C,B,A to be compiled in order
        updateArchives.clear();
        long updatedCreateTime = 2000;
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("D").build(), updatedCreateTime));

        moduleLoader.updateScriptArchives(updateArchives);

        // validate that they were compiled in the updated reverse dependency order
        InOrder orderVerifier = inOrder(mockListener);
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("C", updatedCreateTime), moduleEquals("C", originalCreateTime));
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("B", originalCreateTime), moduleEquals("B", originalCreateTime));
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("A", originalCreateTime), moduleEquals("A", originalCreateTime));
        orderVerifier.verifyNoMoreInteractions();

        // validate the post-condition of the module database
        assertEquals(moduleLoader.getScriptModule("A").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getScriptModule("B").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getScriptModule("C").getCreateTime(), updatedCreateTime);
        assertEquals(moduleLoader.getScriptModule("D").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getAllScriptModules().size(),4);
    }

    @Test
    public void testCompileErrorAbortsRelink() throws Exception {
        // original graph: A->B->C->D
        long originalCreateTime = 1000;
        Set<ScriptArchive> updateArchives = new HashSet<ScriptArchive>();
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").addCompilerPluginId("mockPlugin").addModuleDependency("B").build(), originalCreateTime));
        ScriptArchive archiveB = new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("B").addCompilerPluginId("mockPlugin").addModuleDependency("C").build(), originalCreateTime);
        updateArchives.add(archiveB);
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("D").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("D").addCompilerPluginId("mockPlugin").build(), originalCreateTime));

        when(MOCK_COMPILER.shouldCompile(Mockito.any(ScriptArchive.class))).thenReturn(true);
        when(MOCK_COMPILER.compile(Mockito.any(ScriptArchive.class), Mockito.any(JBossModuleClassLoader.class))).thenReturn(Collections.<Class<?>>emptySet());

        ScriptModuleListener mockListener = createMockListener();
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addListener(mockListener)
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder("mockPlugin")
                    .withPluginClassName(MockScriptCompilerPlugin.class.getName())
                .build())
            .build();

        moduleLoader.updateScriptArchives(updateArchives);
        reset(mockListener);
        reset(MOCK_COMPILER);
        when(MOCK_COMPILER.shouldCompile(Mockito.any(ScriptArchive.class))).thenReturn(true);
        when(MOCK_COMPILER.compile(Mockito.eq(archiveB), Mockito.any(JBossModuleClassLoader.class))).thenThrow(new ScriptCompilationException("TestCompileException", null));
        // update C. would normally cause C,B,A to be compiled in order, but B will fail, so A will be skipped
        updateArchives.clear();
        long updatedCreateTime = 2000;
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("D").build(), updatedCreateTime));

        moduleLoader.updateScriptArchives(updateArchives);

        // validate that only C was compiled.
        InOrder orderVerifier = inOrder(mockListener);
        orderVerifier.verify(mockListener).moduleUpdated(moduleEquals("C", updatedCreateTime), moduleEquals("C", originalCreateTime));
        orderVerifier.verifyNoMoreInteractions();

        // validate the post-condition of the module database
        assertEquals(moduleLoader.getScriptModule("A").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getScriptModule("B").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getScriptModule("C").getCreateTime(), updatedCreateTime);
        assertEquals(moduleLoader.getScriptModule("D").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getAllScriptModules().size(),4);
    }

    @Test
    public void testCompileErrorSendsNotification() throws Exception {
        // original graph: A->B->C->D
        long originalCreateTime = 1000;
        Set<ScriptArchive> updateArchives = new HashSet<ScriptArchive>();
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").addCompilerPluginId("mockPlugin").addModuleDependency("B").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("B").addCompilerPluginId("mockPlugin").addModuleDependency("C").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("D").build(), originalCreateTime));
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("D").addCompilerPluginId("mockPlugin").build(), originalCreateTime));

        ScriptModuleListener mockListener = createMockListener();
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addListener(mockListener)
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder("mockPlugin")
                    .withPluginClassName(MockScriptCompilerPlugin.class.getName())
                .build())
            .build();

        when(MOCK_COMPILER.shouldCompile(Mockito.any(ScriptArchive.class))).thenReturn(true);

        moduleLoader.updateScriptArchives(updateArchives);
        reset(mockListener);

        // update C, but set compilation to fail.
        updateArchives.clear();
        long updatedCreateTime = 2000;
        TestDependecyScriptArchive updatedArchiveC = new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("C").addCompilerPluginId("mockPlugin").addModuleDependency("D").build(), updatedCreateTime);
        updateArchives.add(updatedArchiveC);
        reset(MOCK_COMPILER);
        when(MOCK_COMPILER.shouldCompile(Mockito.eq(updatedArchiveC))).thenReturn(true);
        ScriptCompilationException compilationException = new ScriptCompilationException("TestCompileException", null);
        when(MOCK_COMPILER.compile(Mockito.eq(updatedArchiveC), Mockito.any(JBossModuleClassLoader.class))).thenThrow(compilationException);

        moduleLoader.updateScriptArchives(updateArchives);

        // validate that they were compiled in the updated reverse dependency order
        verify(mockListener).archiveRejected(updatedArchiveC, ArchiveRejectedReason.COMPILE_FAILURE, compilationException);
        verifyNoMoreInteractions(mockListener);

        // validate the post-condition of the module database
        assertEquals(moduleLoader.getScriptModule("A").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getScriptModule("B").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getScriptModule("C").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getScriptModule("D").getCreateTime(), originalCreateTime);
        assertEquals(moduleLoader.getAllScriptModules().size(), 4);
    }

    @Test
    public void testOldArchiveRejected() throws Exception {
        long originalCreateTime = 2000;
        Set<ScriptArchive> updateArchives = new HashSet<ScriptArchive>();
        updateArchives.add(new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").addCompilerPluginId("mockPlugin").build(), originalCreateTime));

        when(MOCK_COMPILER.shouldCompile(Mockito.any(ScriptArchive.class))).thenReturn(true);
        when(MOCK_COMPILER.compile(Mockito.any(ScriptArchive.class), Mockito.any(JBossModuleClassLoader.class))).thenReturn(Collections.<Class<?>>emptySet());

        ScriptModuleListener mockListener = createMockListener();
        ScriptModuleLoader moduleLoader = new ScriptModuleLoader.Builder()
            .addPluginSpec(new ScriptCompilerPluginSpec.Builder("mockPlugin")
                .withPluginClassName(MockScriptCompilerPlugin.class.getName()).build())
            .addListener(mockListener).build();
        moduleLoader.updateScriptArchives(updateArchives);
        reset(mockListener);

        // updated graph: D->C->B->A
        updateArchives.clear();
        long updatedCreateTime = 1000;
        TestDependecyScriptArchive updatedArchive = new TestDependecyScriptArchive(new ScriptModuleSpec.Builder("A").addCompilerPluginId("mockPlugin").build(), updatedCreateTime);
        updateArchives.add(updatedArchive);

        moduleLoader.updateScriptArchives(updateArchives);

        // validate that the update was rejected due to a old timestamp
        verify(mockListener).archiveRejected(updatedArchive, ArchiveRejectedReason.HIGHER_REVISION_AVAILABLE, null);
        verifyNoMoreInteractions(mockListener);

        // validate the post-condition of the module database
        assertEquals(moduleLoader.getScriptModule("A").getCreateTime(), originalCreateTime);
    }

    /**
     * Custom mockito/hamcrest matcher which will inspect a ScriptModule and see if its moduleId
     * equals the input moduleId and likewise for the creation time
     */
    private ScriptModule moduleEquals(final String scriptModuleId, final long createTime) {
        return Mockito.argThat(new ArgumentMatcher<ScriptModule>() {
            @Override
            public boolean matches(Object argument) {
                ScriptModule scriptModule = (ScriptModule)argument;
                return scriptModule != null &&
                    scriptModule.getModuleId().toString().equals(scriptModuleId) &&
                    scriptModule.getCreateTime() == createTime;
            }
            @Override
            public void describeTo(Description description) {
                description.appendText("ScriptModule.getModuleId().equals(\"" + scriptModuleId + "\")");
            }
        });
    }

    private ScriptModuleListener createMockListener() {
        ScriptModuleListener mockListener = mock(ScriptModuleListener.class);
        return mockListener;
    }

    private static class TestDependecyScriptArchive implements ScriptArchive {
        private final long createTime;
        private final ScriptModuleSpec scriptModuleSpec;


        private TestDependecyScriptArchive(ScriptModuleSpec scriptModuleSpec, long createTime) {
            this.createTime = createTime;
            this.scriptModuleSpec = scriptModuleSpec;
        }

        @Override
        public ScriptModuleSpec getModuleSpec() {
            return scriptModuleSpec;
        }

        @Override
        public URL getRootUrl() {
            return null;
        }

        @Override
        public Set<String> getArchiveEntryNames() {
            return Collections.emptySet();
        }

        @Override
        public URL getEntry(String entryName) throws IOException {
            return null;
        }

        @Override
        public long getCreateTime() {
            return createTime;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("scriptModuleSpec", scriptModuleSpec)
                .append("createTime", createTime)
                .toString();
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }
            ScriptModuleLoaderTest.TestDependecyScriptArchive other = (ScriptModuleLoaderTest.TestDependecyScriptArchive) o;
            return Objects.equals(this.createTime, other.createTime) &&
                Objects.equals(this.scriptModuleSpec, other.scriptModuleSpec);
        }
        @Override
        public int hashCode() {
            return Objects.hash(createTime, createTime);
        }
    }

    /** trivial compiler plugin implementation which returns the static mock */
    public static class MockScriptCompilerPlugin implements ScriptCompilerPlugin {
        @Override
        public Set<? extends ScriptArchiveCompiler> getCompilers() {
            return Collections.singleton(MOCK_COMPILER);
        }
    }
}

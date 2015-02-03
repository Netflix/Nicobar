package com.netflix.nicobar.groovy2.compile;

import static org.testng.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.testng.annotations.Test;

import com.netflix.nicobar.core.archive.PathScriptArchive;
import com.netflix.nicobar.groovy2.internal.compile.Groovy2Compiler;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil;
import com.netflix.nicobar.groovy2.testutil.GroovyTestResourceUtil.TestScript;


public class Groovy2CompilerTest {
    @SuppressWarnings("unchecked")
    @Test
    public void testCustomiizerParamsProcessing() throws Exception {
        Groovy2Compiler compiler;
        List<CompilationCustomizer> customizers;
        Map<String, Object> compilerParams;

        Field f = Groovy2Compiler.class.getDeclaredField("customizerClassNames");
        f.setAccessible(true);

        // empty parameters map
        compiler = new Groovy2Compiler(new HashMap<String, Object>());
        customizers = (List<CompilationCustomizer>)f.get(compiler);
        assertTrue(customizers.size() == 0, "no valid objects expected");

        // null value customizers parameter
        compilerParams = new HashMap<String, Object>();
        compilerParams.put(Groovy2Compiler.GROOVY2_COMPILER_PARAMS_CUSTOMIZERS, null);

        compiler = new Groovy2Compiler(compilerParams);
        customizers = (List)f.get(compiler);
        assertTrue(customizers.size() == 0, "no valid objects expected");

        // list with valid customizer
        compilerParams = new HashMap<String, Object>();
        compilerParams.put(Groovy2Compiler.GROOVY2_COMPILER_PARAMS_CUSTOMIZERS, Arrays.asList(new String[] {"org.codehaus.groovy.control.customizers.ImportCustomizer"}));

        compiler = new Groovy2Compiler(compilerParams);
        customizers = (List)f.get(compiler);
        assertTrue(customizers.size() == 1, "one valid object expected");

        // list with invalid objects
        compilerParams = new HashMap<String, Object>();
        compilerParams.put(Groovy2Compiler.GROOVY2_COMPILER_PARAMS_CUSTOMIZERS, Arrays.asList(new Object[] {"org.codehaus.groovy.control.customizers.ImportCustomizer", "org.codehaus.groovy.control.customizers.ImportCustomizer", new HashMap<String, Object>(), null}));

        compiler = new Groovy2Compiler(compilerParams);
        customizers = (List)f.get(compiler);
        assertTrue(customizers.size() == 2, "two valid objects expected");
    }

    @Test
    public void testCompile() throws Exception {
        Groovy2Compiler compiler;
        List<CompilationCustomizer> customizers;
        Map<String, Object> compilerParams;

        Path scriptRootPath = GroovyTestResourceUtil.findRootPathForScript(TestScript.HELLO_WORLD);
        PathScriptArchive scriptArchive = new PathScriptArchive.Builder(scriptRootPath)
            .setRecurseRoot(false)
            .addFile(TestScript.HELLO_WORLD.getScriptPath())
            .build();

        compilerParams = new HashMap<String, Object>();
        compilerParams.put(Groovy2Compiler.GROOVY2_COMPILER_PARAMS_CUSTOMIZERS, Arrays.asList(new Object[] {"testmodule.customizers.TestCompilationCustomizer"}));

        compiler = new Groovy2Compiler(compilerParams);
        compiler.compile(scriptArchive, null, scriptRootPath);
    }
}

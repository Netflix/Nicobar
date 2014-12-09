package com.netflix.nicobar.groovy2.compile;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static org.testng.Assert.assertTrue;

import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.testng.annotations.Test;

import com.netflix.nicobar.groovy2.internal.compile.Groovy2Compiler;


public class Groovy2CompilerTest {

    @Test
    public void testCustomiizerParamsProcessing() throws Exception {
        Groovy2Compiler compiler;
        List<CompilationCustomizer> customizers;
        Map<String, Object> compilerParams;

        Field f = Groovy2Compiler.class.getDeclaredField("compilerCustomizers");
        f.setAccessible(true);
        
        // empty parameters map
        compiler = new Groovy2Compiler(new HashMap<String, Object>());
        customizers = (List)f.get(compiler);
        assertTrue(customizers.size() == 0, "no valid objects expected");
        
        // null value customizers parameter
        compilerParams = new HashMap<String, Object>();
        compilerParams.put(Groovy2Compiler.GROOVY2_COMPILER_PARAMS_CUSTOMIZERS, null);
        
        compiler = new Groovy2Compiler(compilerParams);
        customizers = (List)f.get(compiler);
        assertTrue(customizers.size() == 0, "no valid objects expected");
        
        // list with valid customizer
        compilerParams = new HashMap<String, Object>();
        compilerParams.put(Groovy2Compiler.GROOVY2_COMPILER_PARAMS_CUSTOMIZERS, Arrays.asList(new CompilationCustomizer[] {new ImportCustomizer()}));
        
        compiler = new Groovy2Compiler(compilerParams);
        customizers = (List)f.get(compiler);
        assertTrue(customizers.size() == 1, "one valid object expected");

        // list with invalid objects
        compilerParams = new HashMap<String, Object>();
        compilerParams.put(Groovy2Compiler.GROOVY2_COMPILER_PARAMS_CUSTOMIZERS, Arrays.asList(new Object[] {new ImportCustomizer(), new ImportCustomizer(), new HashMap<String, String>(), new String(""), null}));
        
        compiler = new Groovy2Compiler(compilerParams);
        customizers = (List)f.get(compiler);
        assertTrue(customizers.size() == 2, "two valid objects expected");
        
        
    }
}

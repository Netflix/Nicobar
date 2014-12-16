package testmodule.customizers;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

public class TestCompilationCustomizer extends CompilationCustomizer {

    public TestCompilationCustomizer() {
        super(CompilePhase.SEMANTIC_ANALYSIS);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context,
            ClassNode classNode) throws CompilationFailedException {
        // no op customizer
    };
}

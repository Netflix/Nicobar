package com.netflix.nicobar.core.internal.compile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import com.netflix.nicobar.core.archive.ScriptArchive;
import com.netflix.nicobar.core.compile.ScriptArchiveCompiler;
import com.netflix.nicobar.core.compile.ScriptCompilationException;
import com.netflix.nicobar.core.module.jboss.JBossModuleClassLoader;

public class NashornScriptCompiler implements ScriptArchiveCompiler {

    @Override
    public boolean shouldCompile(ScriptArchive archive) {
        Object moduleFileName = archive.getModuleSpec().getMetadata().get("moduleFileName");
        try {
            return moduleFileName instanceof String && !((String)moduleFileName).isEmpty() && (archive.getEntry((String) moduleFileName) != null);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Set<Class<?>> compile(ScriptArchive archive, JBossModuleClassLoader moduleClassLoader, Path targetDir) throws ScriptCompilationException, IOException {
        // TODO Auto-generated method stub
        return null;
    }

}

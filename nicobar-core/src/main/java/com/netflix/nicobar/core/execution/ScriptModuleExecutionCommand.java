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
package com.netflix.nicobar.core.execution;

import java.util.Objects;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.nicobar.core.module.ScriptModule;

/**
 * Hystrix protected command to execute {@link ScriptModuleExecutable}s.
 * This provide limited sandboxing to script execution in terms of running time. It also provides basic statistics
 * about script execution counts and latencies.
 *
 * @author James Kojo
 * @param <R> Type of return value from the command
 */
public class ScriptModuleExecutionCommand<R> extends HystrixCommand<R>{
    private final  ScriptModuleExecutable<R> executable;
    private final ScriptModule module;

    public ScriptModuleExecutionCommand(String moduleExecutorId, ScriptModuleExecutable<R> executable, ScriptModule module) {
        super(HystrixCommand.Setter
            .withGroupKey(HystrixCommandGroupKey.Factory.asKey(moduleExecutorId))
            .andCommandKey(HystrixCommandKey.Factory.asKey(module.getModuleId()))
            .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withFallbackEnabled(false)));

        Objects.requireNonNull(moduleExecutorId, "moduleExecutorId");
        this.executable = Objects.requireNonNull(executable, "executable");
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    protected R run() throws Exception {
        return executable.execute(module);
    }
}

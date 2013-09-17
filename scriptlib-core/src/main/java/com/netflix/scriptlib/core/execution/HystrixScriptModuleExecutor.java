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
package com.netflix.scriptlib.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.scriptlib.core.module.ScriptModule;
import com.netflix.scriptlib.core.module.ScriptModuleLoader;

/**
 * Hystrix based executor for {@link ScriptModuleExecutable}s.
 *
 * See {@link ScriptModuleExecutionCommand}.
 *
 * @author James Kojo
 */
public class HystrixScriptModuleExecutor<V> {
    private final static Logger logger = LoggerFactory.getLogger(HystrixScriptModuleExecutor.class);

    /**
     * Statistics holder for a given module's executions.
     */
    public static class ExecutionStatistics {
        private final AtomicLong executionCount = new AtomicLong();
        private final AtomicLong lastExecutionTime = new AtomicLong();

        public long getExecutionCount() {
            return executionCount.get();
        }
        public long getLastExecutionTime() {
            return lastExecutionTime.get();
        }
    }

    private final ConcurrentMap<String, ExecutionStatistics> statistics = new ConcurrentHashMap<String, ExecutionStatistics>();
    private final String executorId;

    /**
     * Construct an instance of the executor.
     * @param executorId descriptive name for this executor which will be used for reporting purposes.
     */
    public HystrixScriptModuleExecutor(String executorId) {
        this.executorId = Objects.requireNonNull(executorId, "executorId");
    }

    /**
     * Execute a collection of ScriptModules identified by moduleId.
     *
     * @param moduleIds moduleIds for modules to execute
     * @param executable execution logic to be performed for each module.
     * @param moduleLoader loader which manages the modules.
     * @return
     */
    public List<V> executeModules(List<String> moduleIds, ScriptModuleExecutable<V> executable, ScriptModuleLoader moduleLoader) {
        Objects.requireNonNull(moduleIds, "moduleIds");
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(moduleLoader, "moduleLoader");

        List<ScriptModule> modules = new ArrayList<ScriptModule>(moduleIds.size());
        for (String moduleId : moduleIds) {
           ScriptModule module = moduleLoader.getScriptModule(moduleId);
           modules.add(module);
        }
        return executeModules(modules, executable);
    }

    /**
     * Execute a collection of modules.
     *
     * @param modules modules to execute.
     * @param executable execution logic to be performed for each module.
     * @return
     */
    public List<V> executeModules(List<ScriptModule> modules, ScriptModuleExecutable<V> executable) {
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(executable, "executable");

        List<Future<V>> futureResults = new ArrayList<Future<V>>(modules.size());
        for (ScriptModule module : modules) {
           Future<V> future = new ScriptModuleExecutionCommand<V>(executorId, executable, module).queue();
           futureResults.add(future);

           ExecutionStatistics moduleStats = getOrCreateModuleStatistics(module.getModuleId());
           moduleStats.executionCount.incrementAndGet();
           moduleStats.lastExecutionTime.set(System.currentTimeMillis());
        }

        List<V> results = new ArrayList<V>(modules.size());
        for (int i = 0; i < futureResults.size(); i++) {
            Future<V> futureResult = futureResults.get(i);
            try {
                V result = futureResult.get();
                results.add(result);
            } catch (Exception e) {
                // the exception is already logged by the hystrix command, so just add some additional context
                ScriptModule failedModule = modules.get(i);
                logger.error("moduleId {} with creationTime: {} failed execution. see hystrix command log for deatils.",
                    failedModule.getModuleId(), failedModule.getCreateTime());
                continue;
            }
        }
        return results;
    }

    /**
     * Get the statistics for the given moduleId
     */
    @Nullable
    public ExecutionStatistics getModuleStatistics(String moduleId) {
        ExecutionStatistics moduleStats = statistics.get(moduleId);
        return moduleStats;
    }

    /**
     * Helper method to get or create a ExecutionStatistics instance
     * @param moduleId
     * @return new or existing module statistics
     */
    protected ExecutionStatistics getOrCreateModuleStatistics(String moduleId) {
        ExecutionStatistics moduleStats = statistics.get(moduleId);
        if (moduleStats == null) {
            moduleStats = new ExecutionStatistics();
            ExecutionStatistics existing = statistics.put(moduleId, moduleStats);
            if (existing != null) {
                moduleStats = existing;
            }
        }
        return moduleStats;
    }
}

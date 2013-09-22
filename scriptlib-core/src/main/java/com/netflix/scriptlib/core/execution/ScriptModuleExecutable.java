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

import com.netflix.scriptlib.core.module.ScriptModule;

/**
 * Interface for executing a ScriptModule.
 *
 * @author James Kojo
 * @param <V> the result type of method
 */
public interface ScriptModuleExecutable<V> {

    /**
     * Execute the given ScriptModule.
     * @param scriptModule the module to be executed provided by the executor
     * @return the output of the script module execution
     * @throws Exception on any failures
     */
    V execute(ScriptModule scriptModule) throws Exception;
}

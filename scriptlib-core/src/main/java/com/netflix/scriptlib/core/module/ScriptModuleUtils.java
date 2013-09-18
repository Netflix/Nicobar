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
package com.netflix.scriptlib.core.module;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Utility classes for ScriptModule processing
 *
 * @author James Kojo
 */
public class ScriptModuleUtils {

    /**
     * Find all of the classes in the module that are subclasses or equal to the target class
     * @param module module to search
     * @param targetClass target type to search for
     * @return first instance that matches the given type
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<Class<T>> findAssignableClasses(ScriptModule module, Class<T> targetClass) {
        Set<Class<T>> result = new LinkedHashSet<Class<T>>();
        for (Class<?> candidateClass : module.getLoadedClasses()) {
            if (targetClass.isAssignableFrom(candidateClass)) {
                result.add((Class<T>) candidateClass);
            }
        }
        return result;
    }

    /**
     * Find the first class in the module that is a subclasses or equal to the target class
     * @param module module to search
     * @param targetClass target type to search for
     * @return first instance that matches the given type
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> Class<T> findAssignableClass(ScriptModule module, Class<T> targetClass) {
        for (Class<?> candidateClass : module.getLoadedClasses()) {
            if (targetClass.isAssignableFrom(candidateClass)) {
                return (Class<T>) candidateClass;
            }
        }
        return null;
    }
}

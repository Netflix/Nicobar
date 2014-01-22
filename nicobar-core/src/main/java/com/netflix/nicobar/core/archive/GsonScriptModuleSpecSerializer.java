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
package com.netflix.nicobar.core.archive;

import java.util.Objects;

import com.google.gson.Gson;

/**
 * Gson based implementation of the {@link ScriptModuleSpecSerializer}
 *
 * @author James Kojo
 */
public class GsonScriptModuleSpecSerializer implements ScriptModuleSpecSerializer {
    /** Default file name of the optional {@link ScriptModuleSpec} in the archive */
    public final static String DEFAULT_MODULE_SPEC_FILE_NAME = "moduleSpec.json";
    private static Gson SERIALIZER = new Gson();
    private final String moduleSpecFileName;

    public GsonScriptModuleSpecSerializer() {
        this(DEFAULT_MODULE_SPEC_FILE_NAME);
    }

    /**
     * @param moduleSpecFileName file name to use when outputting a serialized moduleSpec
     */
    public GsonScriptModuleSpecSerializer(String moduleSpecFileName) {
        this.moduleSpecFileName = Objects.requireNonNull(moduleSpecFileName, "moduleSpecFileName");
    }

    /**
     * Convert the {@link ScriptModuleSpec} to a JSON String
     */
    @Override
    public String serialize(ScriptModuleSpec moduleSpec) {
        Objects.requireNonNull(moduleSpec, "moduleSpec");
        String json = SERIALIZER.toJson(moduleSpec);
        return json;
    }
    /**
     * Convert the input JSON String to a {@link ScriptModuleSpec}
     */
    @Override
    public ScriptModuleSpec deserialize(String json) {
        Objects.requireNonNull(json, "json");
        ScriptModuleSpec moduleSpec = SERIALIZER.fromJson(json, ScriptModuleSpec.class);
        return moduleSpec;

    }
    @Override
    public String getModuleSpecFileName() {
        return moduleSpecFileName;
    }

    /**
     * @return the serializer. Override this to customize the serialization logic
     */
    protected Gson getSerializer() {
        return SERIALIZER;
    }

}

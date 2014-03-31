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

import java.lang.reflect.Type;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Gson based implementation of the {@link ScriptModuleSpecSerializer}
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public class GsonScriptModuleSpecSerializer implements ScriptModuleSpecSerializer {
    /** Default file name of the optional {@link ScriptModuleSpec} in the archive */
    public final static String DEFAULT_MODULE_SPEC_FILE_NAME = "moduleSpec.json";
    private static Gson SERIALIZER = new GsonBuilder()
        .registerTypeAdapter(ModuleId.class, new ModuleIdGsonTransformer())
        .registerTypeAdapter(Double.class,  new DoubleGsonTransformer())
        .create();
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

    /**
     * Custom GSON serializer and deserializer for ModuleId
     */
    private static class ModuleIdGsonTransformer implements JsonSerializer<ModuleId>, JsonDeserializer<ModuleId> {
        @Override
        public JsonElement serialize(ModuleId src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.toString());
        }

        @Override
        public ModuleId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) throws JsonParseException
        {
            String moduleId = json.getAsString();
            return ModuleId.fromString(moduleId);
        }
    }

    /**
     * Custom GSON Double serializer
     * This overrides default Gson behavior, which converts integers and longs into
     * floats and doubles before writing out as JSON numbers.
     */
    private static class DoubleGsonTransformer implements JsonSerializer<Double> {
        @Override
        public JsonElement serialize(Double src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == src.longValue())
                return new JsonPrimitive(src.longValue());
            return new JsonPrimitive(src);
        }
    }
}
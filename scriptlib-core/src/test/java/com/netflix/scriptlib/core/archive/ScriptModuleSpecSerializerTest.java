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
package com.netflix.scriptlib.core.archive;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 *
 * @author James Kojo
 */
public class ScriptModuleSpecSerializerTest {

    @Test
    public void testRoundTrip() {
        ScriptModuleSpec expected = new ScriptModuleSpec.Builder("myModuleId")
            .addDependency("dependencyModuleId1")
            .addDependency("dependencyModuleId2")
            .addMetadata("metadataName1", "metadataValue1")
            .addMetadata("metadataName2", "metadataValue2")
            .build();
        ScriptModuleSpecSerializer serializer = new GsonScriptModuleSpecSerializer();
        String json = serializer.serialize(expected);
        System.out.println("json: " + json);
        ScriptModuleSpec deserialized = serializer.deserialize(json);
        assertEquals(deserialized, expected);
    }
}

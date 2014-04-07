package com.netflix.nicobar.core.archive;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.Test;

/**
 * Unit tests for {@link ModuleId}
 *
 * @author Vasanth Asokan
 */
public class ModuleIdTest {

    @Test
    public void testDefaultVersion() {
        ModuleId moduleId = ModuleId.create("/test/module");
        assertEquals(moduleId.toString(), "/test/module");
    }

    @Test
    public void testWithVersion() {
        ModuleId moduleId = ModuleId.create("test-Module", "v1");
        assertEquals(moduleId.toString(), "test-Module" + ModuleId.MODULE_VERSION_SEPARATOR + "v1");
    }

    @Test
    public void testFromStringDefaultVersion() {
        ModuleId moduleId = ModuleId.fromString("test-Module");
        assertEquals(moduleId.toString(), "test-Module");
    }

    @Test
    public void testFromStringWithVersion() {
        ModuleId moduleId = ModuleId.fromString("test-Module.v2");
        assertEquals(moduleId.toString(), "test-Module.v2");
    }

    @Test
    public void testBadModuleName() {
        // Just to make PMD happy about empty catch blocks,
        // We set a dummy operation.
        @SuppressWarnings("unused")
        boolean passed = false;

        try {
            ModuleId.fromString("test.Module.v2");
            fail("Should disallow in module name");
        } catch (IllegalArgumentException e) {
            passed = true;
        }
        try {
            ModuleId.create("test.Module", "v2");
            fail("Should disallow dots in module name");
        } catch (IllegalArgumentException e) {
            passed = true;
        }
        try {
            ModuleId.create("test.Module");
            fail("Should disallow dots in module name");
        } catch (IllegalArgumentException e) {
            passed = true;
        }
        try {
            ModuleId.create("", "v2");
            fail("Should disallow empty module name");
        } catch (IllegalArgumentException e) {
            passed = true;
        }

        char [] disallowedChars = { '#', '!', '(', ')', '.'};
        for (char c: disallowedChars) {
            try {
                ModuleId.create("testModule" + Character.toString(c) + "suffix", "v1");
                fail("Should disallow " + Character.toString(c) +  " in module name");
            } catch (IllegalArgumentException e) {
                passed = true;
            }
        }
    }
}

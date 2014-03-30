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
        ModuleId moduleId = ModuleId.create("testModule");
        assertEquals("testModule", moduleId.toString());
    }

    @Test
    public void testWithVersion() {
        ModuleId moduleId = ModuleId.create("testModule", "v1");
        assertEquals("testModule" + ModuleId.MODULE_VERSION_SEPARATOR + "v1", moduleId.toString());
    }

    @Test
    public void testFromStringDefaultVersion() {
        ModuleId moduleId = ModuleId.fromString("testModule");
        assertEquals("testModule", moduleId.toString());
    }

    @Test
    public void testFromStringWithVersion() {
        ModuleId moduleId = ModuleId.fromString("testModule.v2");
        assertEquals("testModule.v2", moduleId.toString());
    }

    @Test
    public void testBadModuleName() {
        try {
            ModuleId.fromString("test.Module.v2");
            fail("Should disallow in module name");
        } catch (IllegalArgumentException e) {
        }
        try {
            ModuleId.create("test.Module", "v2");
            fail("Should disallow dots in module name");
        } catch (IllegalArgumentException e) {
        }
        try {
            ModuleId.create("test.Module");
            fail("Should disallow dots in module name");
        } catch (IllegalArgumentException e) {
        }
        try {
            ModuleId.create("", "v2");
            fail("Should disallow empty module name");
        } catch (IllegalArgumentException e) {
        }

        char [] disallowedChars = { '#', '!', '(', ')', '.'};
        for (char c: disallowedChars) {
            try {
                ModuleId.create("testModule" + Character.toString(c) + "suffix", "v1");
                fail("Should disallow " + Character.toString(c) +  " in module name");
            } catch (IllegalArgumentException e) {
            }
        }
    }
}

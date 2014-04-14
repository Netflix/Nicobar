package com.netflix.nicobar.endpoints;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

/**
 * Tests for {@link EndpointURI}
 *
 * @author George Campbell
 * @author Vasanth Asokan
 */
public class EndpointURITest {

    @Test
    public void testUriTemplateMatching() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        assertTrue(endpoint.matches("/a/123/bar/endpoint"));
        assertTrue(endpoint.matches("/a/0/foobar/endpoint"));
        assertTrue(endpoint.matches("/a/1/a/endpoint"));
        assertTrue(endpoint.matches("/a/thevar158374/XBOX360/endpoint"));

        assertFalse(endpoint.matches("/a/123/bar/endpoint/more"));
        assertFalse(endpoint.matches("/a/123/bar/endpoint?"));
        assertFalse(endpoint.matches("/a/123/endpoint"));
        assertFalse(endpoint.matches("/a/123/bar/another/endpoint"));
        assertFalse(endpoint.matches("/a/123/bar/difference"));
        assertFalse(endpoint.matches("/a/123/bar/difference"));
        assertFalse(endpoint.matches("/a/123/bar/endpoint/another"));
    }

    /**
     * Passing in the template again should match, even though this typically
     * shouldn't be a normal use-case
     */
    @Test
    public void testUriTemplateMatching2() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        assertTrue(endpoint.matches("/a/{var1}/{var2}/endpoint"));
    }

    /**
     * Passing in a different template shouldn't match
     */
    @Test
    public void testUriTemplateMatching3() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        assertFalse(endpoint.matches("/a/{var1}/endpoint"));
    }

    /**
     * Passing in a value/template mixture should not match
     */
    @Test
    public void testUriTemplateMatching4() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        assertFalse(endpoint.matches("/a/{var1}/foobar/endpoint"));
    }

    /**
     * Valid extraction
     */
    @Test
    public void testExtractVariableFromTemplatedURI1() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/123/bar/endpoint");
        assertEquals(2, values.size());
        assertEquals("123", values.get("var1"));
        assertEquals("bar", values.get("var2"));
    }

    /**
     * Passing in the same template definition shouldn't do anything
     */
    @Test
    public void testExtractVariableFromTemplatedURI2() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        Map<String, String> values = endpoint
                .getVariablesFromURI("/a/{var1}/{var2}/endpoint");
        assertEquals(0, values.size());
    }

    /**
     * Passing in a different template definition shouldn't do anything
     */
    @Test
    public void testExtractVariableFromTemplatedURI2b() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/{var1}/endpoint");
        assertEquals(0, values.size());
    }

    /**
     * If it wouldn't match, then it shouldn't return values.
     */
    @Test
    public void testExtractVariableFromTemplatedURI3() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/");
        assertEquals(0, values.size());
    }

    /**
     * Not-templated URI should never return anything
     */
    @Test
    public void testExtractVariableFromTemplatedURI4() {
        EndpointURI endpoint = new EndpointURI("/a");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/123/bar/endpoint");
        assertEquals(0, values.size());
    }

    /**
     * Not-templated URI should never return anything
     */
    @Test
    public void testExtractVariableFromTemplatedURI4b() {
        EndpointURI endpoint = new EndpointURI("/a");

        Map<String, String> values = endpoint.getVariablesFromURI("/a");
        assertEquals(0, values.size());
    }

    /**
     * Valid extraction
     */
    @Test
    public void testExtractVariableFromTemplatedURI5() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/123");
        assertEquals(1, values.size());
        assertEquals("123", values.get("var1"));
    }

    /**
     * Valid extraction
     */
    @Test
    public void testExtractVariableFromTemplatedURI6() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/endpoint");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/123/endpoint");
        assertEquals(1, values.size());
        assertEquals("123", values.get("var1"));
    }

    /**
     * If it wouldn't match, then it shouldn't return values.
     */
    @Test
    public void testExtractVariableFromTemplatedURI7() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/123/endpoint");
        assertEquals(0, values.size());
    }

    /**
     * If it wouldn't match, then it shouldn't return values.
     */
    @Test
    public void testExtractVariableFromTemplatedURI8() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/endpoint");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/123");
        assertEquals(0, values.size());
    }

    /**
     * Test extraction in order defined by template
     */
    @Test
    public void testExtractVariableFromTemplatedURIinDefinedOrder() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/endpoint");

        Map<String, String> values = endpoint.getVariablesFromURI("/a/123/bar/endpoint");
        String variables[] = values.values().toArray(new String[2]);
        assertEquals(2, variables.length);
        assertEquals("123", variables[0]);
        assertEquals("bar", variables[1]);
    }

    /**
     * Test extraction in order defined by template
     */
    @Test
    public void testExtractVariableFromTemplatedURIinDefinedOrder2() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/{3}/{4}/{5}/{6}/endpoint");

        Map<String, String> values = endpoint
                .getVariablesFromURI("/a/123/bar/three/four/five/six/endpoint");
        String variables[] = values.values().toArray(new String[2]);
        assertEquals(6, variables.length);
        assertEquals("123", variables[0]);
        assertEquals("bar", variables[1]);
        assertEquals("three", variables[2]);
        assertEquals("four", variables[3]);
        assertEquals("five", variables[4]);
        assertEquals("six", variables[5]);
    }

    /**
     * Test extraction in order defined by template
     */
    @Test
    public void testExtractVariableFromTemplatedURIinDefinedOrder3() {
        EndpointURI endpoint = new EndpointURI("/a/{var1}/{var2}/{3}/{4}/{5}/{6}/endpoint");

        List<String> values = endpoint.getVariableListFromURI("/a/123/bar/three/four/five/six/endpoint");
        assertEquals(6, values.size());
        assertEquals("123", values.get(0));
        assertEquals("bar", values.get(1));
        assertEquals("three", values.get(2));
        assertEquals("four", values.get(3));
        assertEquals("five", values.get(4));
        assertEquals("six", values.get(5));
    }

    @Test
    public void testNonTemplatedURI() {
        EndpointURI endpoint = new EndpointURI("/normal/uri");
        assertTrue(endpoint.matches("/normal/uri"));
        assertFalse(endpoint.matches("/normal/uri/more"));
        assertFalse(endpoint.matches("/uri"));
    }

    @Test
    public void testGetPrefix() {
        EndpointURI endpoint = new EndpointURI("/");
        assertEquals("", endpoint.getFirstPathComponent());

        endpoint = new EndpointURI("/");
        assertEquals("", endpoint.getFirstPathComponent());

        endpoint = new EndpointURI("bar");
        assertEquals("bar", endpoint.getFirstPathComponent());

        endpoint = new EndpointURI("bar/");
        assertEquals("bar", endpoint.getFirstPathComponent());

        endpoint = new EndpointURI("/foo");
        assertEquals("foo", endpoint.getFirstPathComponent());

        endpoint = new EndpointURI("/foo/");
        assertEquals("foo", endpoint.getFirstPathComponent());

        endpoint = new EndpointURI("/bar/foo");
        assertEquals("bar", endpoint.getFirstPathComponent());

        endpoint = new EndpointURI("/bar/foo/");
        assertEquals("bar", endpoint.getFirstPathComponent());
    }
}

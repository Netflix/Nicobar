package com.netflix.nicobar.endpoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.jersey.api.uri.UriTemplateParser;

/**
 * URI for endpoints that supports templating.
 * <p>
 * Supports matching against raw URI strings.
 * <p>
 * Example with 2 templates: /a/{b}/{c}/d
 * <p>
 * The URI template defined above would then respond to any of the following:
 * <p>
 * <ul>
 * <li>/a/123/ps3/d</li>
 * <li>/a/456/foo/d</li>
 * <li>/a/foo/bar/d</li>
 * </ul>
 * <p>
 * It would not match the following:
 * <p>
 * <ul>
 * <li>/a/123/foo/bar/d</li>
 * <li>/foo/456/bar/d</li>
 * <li>/foo/bar/d</li>
 * </ul>
 *
 * The underlying parser is a Jersey UriTemplateParser, with templating functionality.
 *
 * @author George Campbell
 * @author Vasanth Asokan
 */
public class EndpointURI implements Comparable<EndpointURI> {
    private final String uri;
    private final Pattern pattern;
    private final List<String> variableNames;
    private final UriTemplateParser parser;

    public EndpointURI(String uri) {
        uri = cleanURI(uri);
        this.uri = uri;
        this.parser = new UriTemplateParser(uri);
        this.pattern = parser.getPattern();
        this.variableNames = parser.getNames();
    }

    /**
     * Strip extra characters and ensure the URI is clean.
     *
     * Example:
     * <ul>
     * <li>'/a/123/ ' becomes '/a/123'</li>
     * <li>'a/123/' becomes '/a/123'</li>
     * </ul>
     *
     * @param uri the URI string.
     * @return the cleaned URI string.
     */
    public static String cleanURI(String uri) {
        if (uri == null) {
            return null;
        }
        uri = uri.trim();

        if (uri.equals("/")) {
            return uri;
        } else if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }

        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        return uri;
    }

    /**
     * Gets the URI string.
     *
     * @return a URI string.
     */
    public String getURI() {
        return uri;
    }

    /**
     * Return true if the URI has a template in it.
     * <p>
     * Example: /a/b/{param} would return true
     *
     * @return true, if this URI is templated, false otherwise.
     */
    public boolean isTemplated() {
        return uri.indexOf('{') > -1;
    }

    /**
     * Match the given concrete endpoint URI string, against this template.
     *
     * @param uri a concrete endpoint URI string.
     * @return true if the concrete URI, matches the template. false otherwise.
     */
    public boolean matches(String uri) {
        if (uri == null) {
            return false;
        }

        uri = cleanURI(uri);
        // If it has template definitions, return true only if it is an exact match
        if (uri.contains("{")) {
            return this.uri.equals(uri);
        }

        return pattern.matcher(uri).matches();
    }

    /**
     * Extract actual values for templated variables, by matching against this URI
     *
     * <p>
     * e.g. If this template URI is: /a/{var}/{anotherVar}/b
     * <p>
     * and the input URI is /a/123/234/b, then the following list is returned
     * <p>
     * <ul>
     * <li>123</li>
     * <li>234</li>
     * </ul>
     * <p>
     *
     * @param uri a URI string with actual values for templat variables.
     * @return a list of string values corresponding to variables.
     */
    public List<String> getVariableListFromURI(String uri) {
        return Collections.unmodifiableList(new ArrayList<String>(getVariablesFromURI(uri).values()));
    }

    /**
     * Extract actual values for templated variables, by matching against this URI
     *
     * <p>
     * e.g. If this template URI is: /a/{var}/{anotherVar}/b
     * <p>
     * and the input URI is /a/123/234/b, then the following map is returned
     * <p>
     * <ul>
     * <li>var: 123</li>
     * <li>anotherVar : 234</li>
     * </ul>
     * <p>
     *
     * @param uri a URI string with actual values for templat variables.
     * @return a map of variables to their string values.
     */
    public Map<String, String> getVariablesFromURI(String uri) {
        if (matches(uri) && !this.uri.equals(uri)) {
            Matcher m = pattern.matcher(uri);
            if (m.matches()) {
                // Retain ordering
                Map<String, String> values = new LinkedHashMap<String, String>();
                for (int i = 0; i < m.groupCount(); i++) {
                    values.put(variableNames.get(i), m.group(i + 1));
                }
                return Collections.unmodifiableMap(values);
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Get the first component of the endpoint pathname
     *
     * <p>
     * e.g.
     * "/".getPrefix() == ""
     * "/foo".getPrefix() == "foo"
     * "foo".getPrefix() == "foo"
     * "/foo/".getPrefix() == "foo"
     * "/bar/foo".getPrefix() == "bar"
     * "/bar/foo/".getPrefix() == "bar"
     *
     * @return a prefix string which may be empty, but not null
     */
    public String getFirstPathComponent() {
        if (uri == null || uri.equals("") || uri.equals("/"))
            return "";

        int start = 0;
        if (uri.startsWith("/"))
            start = 1;

        int end = uri.indexOf("/", start);
        if (end == -1)
            end = uri.length();

        return uri.substring(start, end);
    }

    @Override
    public String toString() {
        return uri;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        EndpointURI other = (EndpointURI) obj;
        if (uri == null) {
            if (other.uri != null)
                return false;
        } else if (!uri.equals(other.uri))
            return false;
        return true;
    }

    @Override
    public int compareTo(EndpointURI o) {
        return uri.compareTo(o.uri);
    }

    /* package */ UriTemplateParser getParser() {
        return parser;
    }
}

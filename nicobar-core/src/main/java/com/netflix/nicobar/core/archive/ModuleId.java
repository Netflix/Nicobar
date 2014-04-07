package com.netflix.nicobar.core.archive;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A unique identifier for a script module, composed out of a name and a
 * version identifier.
 *
 * @author Vasanth Asokan, based on ModuleIdentifier.java in org.jboss.modules.
 */
public final class ModuleId {
    private static final String MODULE_NAME_PATTERN_STR = "^[a-zA-Z0-9_/][a-zA-Z0-9_\\-{}\\\\@$:<>/]*$";
    private static Pattern MODULE_NAME_PATTERN = Pattern.compile(MODULE_NAME_PATTERN_STR);
    public static final String DEFAULT_VERSION = "";
    public static final String MODULE_VERSION_SEPARATOR = ".";

    private final String name;
    private final String version;
    private final int hashCode;

    private ModuleId(final String name, final String version) {
        if (name == null || name.equals(""))
            throw new IllegalArgumentException("Module name can not be null or empty.");
        if (!MODULE_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Module name must match " + MODULE_NAME_PATTERN_STR);
        }
        this.name = name;

        if (version == null)
            this.version = DEFAULT_VERSION;
        else
            this.version = version;

        hashCode = Objects.hash(name, version);
    }

    /**
     * Get the module name.
     *
     * @return the module name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the module version.
     *
     * @return the version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Determine the hash code of this module identifier.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        ModuleId other = (ModuleId) o;
        return Objects.equals(this.name, other.name) &&
            Objects.equals(this.version, other.version);
    }

    /**
     * Get the string representation of this module identifier.
     * If version != DEFAULT_VERSION, this is constructed as "<name>.<version>"
     * Else, this is constructed as "<name>".
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        if (version.equals(DEFAULT_VERSION))
            return name;
        else
            return name + MODULE_VERSION_SEPARATOR + version;

    }

    /**
     * Parse a module id from a string.
     * If a version identifier cannot be extracted, the version identifier will
     * be set to "" (empty string).
     *
     * @param moduleId the id string
     * @return the module identifier
     * @throws IllegalArgumentException if the format of the module specification is invalid or it is {@code null}
     */
    public static ModuleId fromString(String moduleId) throws IllegalArgumentException {
        if (moduleId == null) {
            throw new IllegalArgumentException("Module Id String is null");
        }
        if (moduleId.length() == 0) {
            throw new IllegalArgumentException("Empty Module Id String");
        }

        final int c1 = moduleId.lastIndexOf(MODULE_VERSION_SEPARATOR);
        final String name;
        final String version;
        if (c1 != -1) {
            name = moduleId.substring(0, c1);
            version = moduleId.substring(c1 + 1);
        } else {
            name = moduleId;
            version = DEFAULT_VERSION;
        }

        return new ModuleId(name, version);
    }

    /**
     * Creates a new module identifier using the specified name and version.
     * An unspecified or empty version ("") can be treated as the
     * canonical, latest version of the module, though the ultimate treatment
     * is upto the integration code using modules.
     *
     * @param name the name of the module
     * @param version the version string
     * @return the identifier
     */
    public static ModuleId create(final String name, String version) {
        return new ModuleId(name, version);
    }

    /**
     * Creates a new module identifier using the specified name.
     *
     * @param name the name of the module
     * @return the identifier
     */
    public static ModuleId create(String name) {
        return create(name, null);
    }
}
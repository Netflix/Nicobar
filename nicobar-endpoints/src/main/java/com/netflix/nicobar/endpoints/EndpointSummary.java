package com.netflix.nicobar.endpoints;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A condensed summary of an endpoint's state vis–a–vis, its backing script archives.
 * May be extended to contain other runtime/deployment related data.
 *
 * @author Vasanth Asokan
 */
public class EndpointSummary {
    private String activeVersion;
    private String latestVersion;
    private long lastModified;

    /**
     * Construct an endpoint summary.
     *
     * @param activeVersion the active version string, if this endpoint is active.
     * @param latestVersion the latest version string of a backing script archive
     *                      for this endpoint.
     * @param lastModified the last modified timestamp.
     */
    public EndpointSummary(@Nullable String activeVersion, String latestVersion, long lastModified) {
        Objects.requireNonNull(lastModified, "latestVersion");
        Objects.requireNonNull(lastModified, "lastModified");
        this.activeVersion = activeVersion;
        this.latestVersion = latestVersion;
        this.lastModified = lastModified;
    }

    /**
     * Return the active version string,
     * if this endpoint is active, else null.
     *
     * @return a version string, possibly null.
     */
    @Nullable
    public String getActiveVersion() {
        return activeVersion;
    }

    /**
     * Set the active version string for this endpoint.
     *
     * @param activeVersion the active version string. If null,
     *        indicates that this endpoint is not active.
     */
    public void setActiveVersion(@Nullable String activeVersion) {
        this.activeVersion = activeVersion;
    }

    /**
     * Return the latest version string.
     *
     * @return a version string.
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Set the latest version string. Cannot be null.
     *
     * @param latestVersion the latest version string for this endpoint.
     */
    public void setLatestVersion(@Nonnull String latestVersion) {
        Objects.requireNonNull(latestVersion, "latestVersion");
        this.latestVersion = latestVersion;
    }

    /**
     * Return the last modified timestamp.
     *
     * @return a timestamp.
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Set the last modified timestamp.
     *
     * @param lastModified a timestamp.
     */
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        EndpointSummary other = (EndpointSummary) o;
        return Objects.equals(this.activeVersion, other.activeVersion) &&
            Objects.equals(this.latestVersion, other.latestVersion) &&
            Objects.equals(this.lastModified, other.lastModified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.activeVersion, this.latestVersion, this.lastModified);
    }
}

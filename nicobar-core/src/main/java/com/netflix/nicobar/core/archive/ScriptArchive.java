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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Data object which represents a bundle of scripts and associated resources.
 * Also contains a {@link ScriptModuleSpec} to describe how this archive
 * is to be converted into a module, and optionally, a set of deploy specs that
 * describes deploy data useful in operating the module.
 *
 * @author James Kojo
 * @author Vasanth Asokan
 */
public interface ScriptArchive {

    /**
     * @return the module spec for this archive
     */
    public ScriptModuleSpec getModuleSpec();

    /**
     * Deployment specs for an archive, are assumed to be created and managed
     * by the application code using Nicobar. These may determine how an archive
     * is deployed into a {@link ScriptModuleLoader} as well as determine
     * execution parameters for script module executors.
     *
     * @return the deployment specs for this archive
     */
    public Map<String, Object> getDeploySpecs();

    /**
     * @return {@link URL}s representing the contents of this archive. should be
     * suitable for passing to a {@link URLClassLoader}
     */
    @Nullable
    public URL getRootUrl();

    /**
     * @return relative path names of all the entries in the script archive.
     */
    public Set<String> getArchiveEntryNames();

    /**
     * @return a URL to the resource.
     * @throws IOException
     */
    @Nullable
    public URL getEntry(String entryName) throws IOException;

    /**
     * Timestamp used to resolve multiple revisions of the archive. If multiple archives
     * are submitted with the same moduleId, only the one with the highest timestamp will be used.
     */
    public long getCreateTime();
}
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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * Data object which represents a bundle of scripts and associated resources.
 *
 * An archive is uniquely identified by it's name and version number.
 * @author James Kojo
 */
public interface ScriptArchive {

    /**
     * @return the module spec for this archive
     */
    ScriptModuleSpec getModuleSpec();

    /**
     * @return {@link URL}s representing the contents of this archive. should be
     * suitable for passing to a {@link URLClassLoader}
     */
    public URL getRootUrl();

    /**
     * @return relative path names of all the entries in the script archive.
     */
    public Set<String> getArchiveEntryNames();

    /**
     * @return a URL to the resource.
     * @throws IOException
     */
    public URL getEntry(String entryName) throws IOException;
}
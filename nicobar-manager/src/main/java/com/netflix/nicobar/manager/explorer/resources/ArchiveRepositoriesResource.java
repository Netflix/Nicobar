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
package com.netflix.nicobar.manager.explorer.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections.CollectionUtils;

import com.netflix.nicobar.core.persistence.ArchiveRepository;
import com.netflix.nicobar.core.persistence.ArchiveSummary;
import com.netflix.nicobar.core.persistence.RepositorySummary;
import com.sun.jersey.api.NotFoundException;
import com.sun.jersey.api.view.Viewable;

/**
 * REST resource for accessing the a collection of {@link ArchiveRepository}s.
 *
 * @author James Kojo
 */
@Path("scriptmanager")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArchiveRepositoriesResource {
    private final Map<String, ArchiveRepository> repositories;

    // avoid exception for classpath scanners which attempt to instantiate this resource
    public ArchiveRepositoriesResource() {
        this(Collections.<String, ArchiveRepository>emptyMap());
    }

    public ArchiveRepositoriesResource(Map<String, ArchiveRepository> repositories) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
    }

    @GET
    @Produces( MediaType.TEXT_HTML )
    public Viewable showIndex() {
        Map<String, Object> model = new HashMap<String, Object>();
        return new Viewable( "/scriptmanager/repository_list.ftl", model);
    }

    /**
     * Get a list of all of the repository summaries
     */
    @GET
    @Path("/repositorysummaries")
    public List<RepositorySummary> getRepositorySummaries() {
        List<RepositorySummary> result = new ArrayList<RepositorySummary>(repositories.size());
        for (String repositoryId : repositories.keySet()) {
            RepositorySummary repositorySummary = getScriptRepo(repositoryId).getRepositorySummary();
            result.add(repositorySummary);
        }
        return result;
    }

    /**
     * Get a map of summaries from different repositories.
     * @param repositoryIds ids for repositories to query. if empty, then all repositories will be queried.
     * @return map of repository id to list of summaries in the respective repository
     */
    @GET
    @Path("/archivesummaries")
    public Map<String, List<ArchiveSummary>> getArchiveSummaries(@QueryParam("repositoryIds") Set<String> repositoryIds) {
        if (CollectionUtils.isEmpty(repositoryIds)) {
            repositoryIds = repositories.keySet();
        }
        Map<String, List<ArchiveSummary>> result = new LinkedHashMap<String, List<ArchiveSummary>>();
        for (String repositoryId : repositoryIds) {
            List<ArchiveSummary> repoSummaries = getScriptRepo(repositoryId).getSummaries();
            result.put(repositoryId, repoSummaries);
        }
        return result;
    }

    @Path("/{repositoryId}")
    public ArchiveRepositoryResource getScriptRepo(@PathParam("repositoryId") String repositoryId) {
        ArchiveRepository repository = repositories.get(repositoryId);
        if (repository == null) {
            throw new NotFoundException("no such repository '" + repositoryId + "'");
        }
        return new ArchiveRepositoryResource(repository);
    }
}

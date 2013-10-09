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
package com.netflix.scriptlib.core.persistence;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.scriptlib.core.archive.ScriptArchive;
import com.netflix.scriptlib.core.module.ScriptModuleLoader;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Poller which periodically scans a list of {@link ArchiveRepository} for updates and publishes
 * them to a {@link ScriptModuleLoader}
 *
 * @author James Kojo
 */
public class ArchiveRepositoryPoller {
    public static class Builder {
        protected final ScriptModuleLoader moduleLoader;
        protected ScheduledExecutorService pollerThreadPool;

        public Builder(ScriptModuleLoader moduleLoader) {
            this.moduleLoader = moduleLoader;
        }

        /** Override the default scheduler */
        public Builder setThreadPool(ScheduledExecutorService pollerThreadPool) {
            this.pollerThreadPool = pollerThreadPool;
            return this;
        }

        public ArchiveRepositoryPoller build() {
            ScheduledExecutorService buildPollerThreadPool = pollerThreadPool;
            if (buildPollerThreadPool == null ) {
                buildPollerThreadPool = Executors.newSingleThreadScheduledExecutor(DEFAULT_POLLER_THREAD_FACTORY);
            }
            return new ArchiveRepositoryPoller(buildPollerThreadPool, moduleLoader);
        }
    }

    private final static Logger logger = LoggerFactory.getLogger(ArchiveRepositoryPoller.class);

    /** Thread factory used for the default poller thread pool */
    private final static ThreadFactory DEFAULT_POLLER_THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
             Thread thread = new Thread(r, ArchiveRepositoryPoller.class.getSimpleName() + "-" + "PollerThread");
             thread.setDaemon(true);
             return thread;
        }
    };
    /** used for book-keeping  of repositories that are being polled */
    protected static class RepositoryPollerContext {
        /** Map of moduleId to last known update time of the archive */
        protected  final Map<String, Long> lastUpdateTimes = new HashMap<String, Long>();

        @SuppressFBWarnings(value="URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification="will use later")
        protected volatile ScheduledFuture<?> future;
        protected RepositoryPollerContext() {
        }
    }

    /** Contains transient state required for calculating deltas */
    protected final ConcurrentHashMap<ArchiveRepository, RepositoryPollerContext> repositoryContexts =
        new ConcurrentHashMap<ArchiveRepository, RepositoryPollerContext>();

    /** Thread pool used by the pollers */
    protected final ScheduledExecutorService pollerThreadPool;
    protected final ScriptModuleLoader moduleLoader;

    protected ArchiveRepositoryPoller(ScheduledExecutorService pollerThreadPool, ScriptModuleLoader moduleLoader) {
        this.pollerThreadPool = Objects.requireNonNull(pollerThreadPool, "pollerThreadPool");
        this.moduleLoader = Objects.requireNonNull(moduleLoader, "moduleLoader");
    }

    /**
     * Add a repository and schedule polling
     * @param archiveRepository repository to scan
     * @param pollInterval how often this repository should be scanned
     * @param timeUnit unit of the pollInterval param
     * @param waitForInitialPoll whether or not to block until the initial poll is complete
     * @return true if the repository was added. false if it already exists
     */
    public boolean addRepository(final ArchiveRepository archiveRepository, final int pollInterval, TimeUnit timeUnit, boolean waitForInitialPoll) {
        if (pollInterval <= 0) {
            throw new IllegalArgumentException("invalid pollInterval " + pollInterval);
        }
        Objects.requireNonNull(timeUnit, "timeUnit");
        RepositoryPollerContext pollerContext = new RepositoryPollerContext();
        RepositoryPollerContext oldContext = repositoryContexts.putIfAbsent(archiveRepository, pollerContext);
        if (oldContext != null) {
            return false;
        }
        final CountDownLatch initialPollLatch = new CountDownLatch(1);
        ScheduledFuture<?> future = pollerThreadPool.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                try {
                    pollRepository(archiveRepository);
                    initialPollLatch.countDown();
                } catch (Throwable t) {
                    // should never happen
                    logger.error("Excecution exception on poll" , t);
                }
            }
        }, 0, pollInterval, timeUnit);
        pollerContext.future = future;
        if (waitForInitialPoll) {
            try {
                initialPollLatch.await();
            } catch (Exception e) {
                // should never happen
                logger.error("Excecution exception on poll" , e);
            }
        }
        return true;
    }

    protected void pollRepository(ArchiveRepository archiveRepository) {
        RepositoryPollerContext context = repositoryContexts.get(archiveRepository);
        Map<String, Long> repoUpdateTimes;
        try {
            repoUpdateTimes = archiveRepository.getArchiveUpdateTimes();
        } catch (IOException e) {
            logger.error("Exception while fetching update times for repository " +
                archiveRepository.getRepositoryId(), e);
            return;
        }

        // search for new/updated archives by comparing update times reported by the repo
        // to the local repository context.
        Set<String> updatedModuleIds = new HashSet<String>(repoUpdateTimes.size());
        for (Entry<String, Long> entry : repoUpdateTimes.entrySet()) {
            String moduleId = entry.getKey();
            Long queryUpdateTime = entry.getValue();
            Long lastUpdateTime = context.lastUpdateTimes.get(moduleId);
            if (lastUpdateTime == null || lastUpdateTime < queryUpdateTime) {
                // this is a new archive or a new revision of an existing archive.
                lastUpdateTime = queryUpdateTime;
                context.lastUpdateTimes.put(moduleId, lastUpdateTime);
                updatedModuleIds.add(moduleId);
            }
        }

        // find deleted modules by taking the set difference of moduleIds in the repository
        // and module ids in the repository context
        Set<String> deletedModuleIds = new HashSet<String>(context.lastUpdateTimes.keySet());
        deletedModuleIds.removeAll(repoUpdateTimes.keySet());
        context.lastUpdateTimes.keySet().removeAll(deletedModuleIds);

        // lookup updated archives and update archive times
        if (!updatedModuleIds.isEmpty()) {
            Set<ScriptArchive> scriptArchives;
            try {
                scriptArchives = archiveRepository.getScriptArchives(updatedModuleIds);
                moduleLoader.updateScriptArchives(scriptArchives);
            } catch (Exception e) {
                logger.error("Exception when attempting to Fetch archives for moduleIds: " +
                    updatedModuleIds, e);
            }
        }

        if (!deletedModuleIds.isEmpty()) {
            for (String scriptModuleId : deletedModuleIds) {
                moduleLoader.removeScriptModule(scriptModuleId);
            }
        }
    }

    public void shutdown() {
        pollerThreadPool.shutdown();
    }
}

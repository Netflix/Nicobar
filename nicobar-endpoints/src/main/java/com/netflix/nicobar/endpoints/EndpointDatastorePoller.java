package com.netflix.nicobar.endpoints;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A periodic poller for a set of {@link EndpointDatastore} instances.
 * <p>
 * Reports the result of the poll, back to an {@link BaseEndpointRegistry}.
 *
 * @author Vasanth Asokan
 */
public class EndpointDatastorePoller {
    private final static Logger logger = LoggerFactory.getLogger(EndpointDatastorePoller.class);


    /** Thread factory used for the default poller thread pool */
    private final static ThreadFactory DEFAULT_POLLER_THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
             Thread thread = new Thread(r, EndpointDatastore.class.getSimpleName() + "-" + "PollerThread");
             thread.setDaemon(true);
             return thread;
        }
    };

    /**
     * A builder for the poller.
     */
    public static class Builder {
        protected final BaseEndpointRegistry<?> registry;
        private ScheduledExecutorService pollerThreadPool;

        public Builder(BaseEndpointRegistry<?> registry) {
            this.registry = registry;
        }

        /** Override the default scheduler */
        public Builder setThreadPool(ScheduledExecutorService pollerThreadPool) {
            this.pollerThreadPool = pollerThreadPool;
            return this;
        }

        public EndpointDatastorePoller build() {
            ScheduledExecutorService buildPollerThreadPool = pollerThreadPool;
            if (buildPollerThreadPool == null ) {
                buildPollerThreadPool = Executors.newSingleThreadScheduledExecutor(DEFAULT_POLLER_THREAD_FACTORY);
            }
            return new EndpointDatastorePoller(registry, buildPollerThreadPool);
        }
    }

    /** used for book-keeping  of datastores that are being polled */
    protected static class DatastoreContext {
        /** Map of Endpoint URIs to last fetched update summaries */
        protected  final Map<String, EndpointSummary> lastSummaries = new HashMap<String, EndpointSummary>();

        protected DatastoreContext() {
        }
    }

    protected final BaseEndpointRegistry<?> registry;
    private ScheduledExecutorService pollerThreadPool;
    /**
     * Datastore context required for calculating deltas.
     */
    protected final ConcurrentHashMap<EndpointDatastore, DatastoreContext> datastoreContexts =
        new ConcurrentHashMap<EndpointDatastore, DatastoreContext>();

    private EndpointDatastorePoller(BaseEndpointRegistry<?> registry, ScheduledExecutorService pollerThreadPool) {
        this.registry = registry;
        this.pollerThreadPool = pollerThreadPool;
    }

    /**
     * Add a datastore and schedule polling
     * @param datastore datastore to scan.
     * @param pollInterval how often this datastore should be scanned.
     * @param timeUnit unit of the pollInterval param.
     * @param waitForInitialPoll whether or not to block until the initial poll is complete.
     * @return true if the datastore was added. false if it already exists.
     */
    public boolean addDatastore(final EndpointDatastore datastore, final int pollInterval, TimeUnit timeUnit, boolean waitForInitialPoll) {
        if (pollInterval <= 0) {
            throw new IllegalArgumentException("invalid pollInterval " + pollInterval);
        }

        Objects.requireNonNull(timeUnit, "timeUnit");
        DatastoreContext pollerContext = new DatastoreContext();
        DatastoreContext oldContext = datastoreContexts.putIfAbsent(datastore, pollerContext);
        if (oldContext != null) {
            return false;
        }

        final CountDownLatch initialPollLatch = new CountDownLatch(1);
        pollerThreadPool.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                try {
                    pollDatastore(datastore);
                    initialPollLatch.countDown();
                } catch (Throwable t) {
                    logger.error("Excecution exception on poll" , t);
                }
            }
        }, 0, pollInterval, timeUnit);

        if (waitForInitialPoll) {
            try {
                initialPollLatch.await();
            } catch (Exception e) {
                logger.error("Excecution exception on poll" , e);
            }
        }
        return true;
    }

    /**
     * Poll the datatore, process deltas, and notify registry of
     * poll result.
     *
     * @param datastore
     * @throws Exception
     */
    protected void pollDatastore(EndpointDatastore datastore) throws Exception {
        try {
            logger.info("Polling for endpoint summaries on datastore: " + datastore.getDatastoreId());

            Map<String, EndpointSummary> currentSummaries = Collections.unmodifiableMap(datastore.getEndpointSummaries());
            Map<String, EndpointSummary> modifiedSummaries = new HashMap<String, EndpointSummary>();

            DatastoreContext context = datastoreContexts.get(datastore);

            // Seed the removed summaries list with everything,
            // Will remove summaries as the current set is being iterated over.
            Map<String, EndpointSummary> removedSummaries = new HashMap<String, EndpointSummary>(context.lastSummaries);
            Map<String, EndpointSummary> newSummaries = new HashMap<String, EndpointSummary>();

            // Iterate over the current summary set
            for (Map.Entry<String, EndpointSummary> summary: currentSummaries.entrySet()) {
                EndpointSummary oldSummary = context.lastSummaries.get(summary.getKey());
                // This is a new summary
                if (oldSummary == null) {
                    newSummaries.put(summary.getKey(), summary.getValue());
                } else {
                    removedSummaries.remove(summary.getKey());
                    // See if this summary has changed, and add it to the modified set accordingly
                    if (!summary.getValue().equals(oldSummary))
                        modifiedSummaries.put(summary.getKey(), summary.getValue());
                }
            }

            registry.pollResult(datastore.getDatastoreId(), newSummaries, modifiedSummaries, removedSummaries);

            // Save context
            context.lastSummaries.clear();
            context.lastSummaries.putAll(currentSummaries);
        } catch (IOException e) {
            throw new Exception(e);
        }
    }

    /**
     * Shut down the poller threadpool.
     */
    public void shutdown() {
        pollerThreadPool.shutdown();
    }
}

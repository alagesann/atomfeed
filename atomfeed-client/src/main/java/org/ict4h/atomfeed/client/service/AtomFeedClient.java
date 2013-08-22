package org.ict4h.atomfeed.client.service;

import com.sun.syndication.feed.atom.Entry;
import com.sun.syndication.feed.atom.Feed;
import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.domain.FailedEvent;
import org.ict4h.atomfeed.client.domain.Marker;
import org.ict4h.atomfeed.client.exceptions.AtomFeedClientException;
import org.ict4h.atomfeed.client.factory.AtomFeedProperties;
import org.ict4h.atomfeed.client.repository.AllFailedEvents;
import org.ict4h.atomfeed.client.repository.AllFeeds;
import org.ict4h.atomfeed.client.repository.AllMarkers;
import org.ict4h.atomfeed.client.util.Util;
import org.ict4h.atomfeed.jdbc.JdbcConnectionProvider;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class AtomFeedClient implements FeedClient {
    private static final int FAILED_EVENTS_PROCESS_BATCH_SIZE = 5;

    private static Logger logger = Logger.getLogger(AtomFeedClient.class);

    private AllFeeds allFeeds;
    private AtomFeedProperties atomFeedProperties;
    private JdbcConnectionProvider jdbcConnectionProvider;
    private URI feedUri;
    private EventWorker eventWorker;
    private Map<String, String> cookies;
    private AllMarkers allMarkers;
    private AllFailedEvents allFailedEvents;

    AtomFeedClient(AllFeeds allFeeds, AllMarkers allMarkers, AllFailedEvents allFailedEvents, URI feedUri, EventWorker eventWorker) {
        this(allFeeds, allMarkers, allFailedEvents, new AtomFeedProperties(), null, feedUri, eventWorker);
    }

    public AtomFeedClient(AllFeeds allFeeds, AllMarkers allMarkers, AllFailedEvents allFailedEvents, AtomFeedProperties atomFeedProperties,
                          JdbcConnectionProvider jdbcConnectionProvider,
                          URI feedUri, EventWorker eventWorker) {
        this.allFeeds = allFeeds;
        this.allMarkers = allMarkers;
        this.allFailedEvents = allFailedEvents;
        this.atomFeedProperties = atomFeedProperties;
        this.jdbcConnectionProvider = jdbcConnectionProvider;
        this.feedUri = feedUri;
        this.eventWorker = eventWorker;
        this.cookies = cookies;
    }

    @Override
    public void processEvents() {
        logger.info(String.format("Processing events for feed URI : %s using event worker : %s", feedUri, eventWorker.getClass().getSimpleName()));

        Connection connection = null;
        boolean autoCommit = false;
        try {
            connection = jdbcConnectionProvider.getConnection();
            autoCommit = connection.getAutoCommit();
            if (atomFeedProperties.controlsEventProcessing()) {
                connection.setAutoCommit(false);
            }

            Marker marker = allMarkers.get(feedUri);
            if (marker == null) marker = new Marker(feedUri, null, null);
            FeedEnumerator feedEnumerator = new FeedEnumerator(allFeeds, marker);

            Event event = null;
            for (Entry entry : feedEnumerator) {
                if (shouldNotProcessEvents(feedUri)) {
                    logger.warn("Too many failed events have failed while processing. Cannot continue.");
                    return;
                }
                try {
                    event = new Event(entry, getEntryFeedUri(feedEnumerator));
                    logger.debug("Processing event : " + event);
                    eventWorker.process(event);
                    if (atomFeedProperties.controlsEventProcessing()) {
                        allMarkers.put(feedUri, entry.getId(), Util.getViaLink(feedEnumerator.getCurrentFeed()));
                        connection.commit();
                    }
                } catch (Exception e) {
                    logger.error("", e);
                    if (atomFeedProperties.controlsEventProcessing()) {
                        connection.rollback();
                    }
                    handleFailedEvent(entry, feedUri, e, feedEnumerator.getCurrentFeed(), event);
                    if (atomFeedProperties.controlsEventProcessing()) {
                        connection.commit();
                    }
                } finally {
                    eventWorker.cleanUp(event);
                }
            }
        } catch (SQLException e) {
            throw new AtomFeedClientException(e);
        } finally {
            if (connection != null) {
                try {
                    if (atomFeedProperties.controlsEventProcessing()) {
                        connection.setAutoCommit(autoCommit);
                    }
                    connection.close();
                } catch (SQLException e) {
                    throw new AtomFeedClientException(e);
                }
            }
        }
    }


    @Override
    public void processFailedEvents() {
        logger.info(String.format("Processing failed events for feed URI : %s using event worker : %s", feedUri, eventWorker.getClass().getSimpleName()));

        Connection connection = null;
        boolean autoCommit = false;
        try {
            connection = jdbcConnectionProvider.getConnection();
            autoCommit = connection.getAutoCommit();
            if (atomFeedProperties.controlsEventProcessing()) {
                connection.setAutoCommit(false);
            }

            List<FailedEvent> failedEvents =
                    allFailedEvents.getOldestNFailedEvents(feedUri.toString(), FAILED_EVENTS_PROCESS_BATCH_SIZE);

            for (FailedEvent failedEvent : failedEvents) {
                try {
                    logger.debug(String.format("Processing failed event : %s", failedEvent));
                    eventWorker.process(failedEvent.getEvent());
                    if (atomFeedProperties.controlsEventProcessing()) {
                        allFailedEvents.remove(failedEvent);
                        connection.commit();
                    }
                } catch (Exception e) {
                    logger.error("", e);
                    if (atomFeedProperties.controlsEventProcessing()) {
                        connection.rollback();
                        failedEvent.setFailedAt(new Date().getTime());
                        failedEvent.setErrorMessage(Util.getExceptionString(e));
                        allFailedEvents.addOrUpdate(failedEvent);
                        connection.commit();
                    }
                    logger.info(String.format("Failed to process failed event. %s", failedEvent));
                }
            }
        } catch (SQLException e) {
            throw new AtomFeedClientException(e);
        } finally {
            if (connection != null) {
                try {
                    if (atomFeedProperties.controlsEventProcessing()) {
                        connection.setAutoCommit(autoCommit);
                    }
                    connection.close();
                } catch (SQLException e) {
                    throw new AtomFeedClientException(e);
                }
            }
        }
    }

    private String getEntryFeedUri(FeedEnumerator feedEnumerator) {
        return Util.getSelfLink(feedEnumerator.getCurrentFeed()).toString();
    }

    private boolean shouldNotProcessEvents(URI feedUri) {
        return (allFailedEvents.getNumberOfFailedEvents(feedUri.toString()) >= atomFeedProperties.getMaxFailedEvents());
    }

    private void handleFailedEvent(Entry entry, URI feedUri, Exception e, Feed feed, Event event) {
        allFailedEvents.addOrUpdate(new FailedEvent(feedUri.toString(), event, Util.getExceptionString(e)));
        if (atomFeedProperties.controlsEventProcessing())
            allMarkers.put(this.feedUri, entry.getId(), Util.getViaLink(feed));
    }
}
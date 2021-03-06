/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.eventhubs.sendrecv;

import com.microsoft.azure.eventhubs.*;
import com.microsoft.azure.eventhubs.impl.AmqpConstants;
import com.microsoft.azure.eventhubs.lib.ApiTestBase;
import com.microsoft.azure.eventhubs.lib.TestBase;
import com.microsoft.azure.eventhubs.lib.TestContext;
import org.junit.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class ReceiveTest extends ApiTestBase {
    static final String cgName = TestContext.getConsumerGroupName();
    static final String partitionId = "0";

    static EventHubClient ehClient;

    PartitionReceiver offsetReceiver = null;
    PartitionReceiver datetimeReceiver = null;

    @BeforeClass
    public static void initialize() throws Exception {
        final ConnectionStringBuilder connectionString = TestContext.getConnectionString();
        initializeEventHub(connectionString);
    }

    public static void initializeEventHub(ConnectionStringBuilder connectionString) throws Exception {
        ehClient = EventHubClient.createSync(connectionString.toString(), TestContext.EXECUTOR_SERVICE);
        TestBase.pushEventsToPartition(ehClient, partitionId, 25).get();
    }

    @AfterClass()
    public static void cleanup() throws EventHubException {
        if (ehClient != null) {
            ehClient.closeSync();
        }
    }

    @Test()
    public void testReceiverStartOfStreamFilters() throws EventHubException {
        offsetReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromStartOfStream());
        Iterable<EventData> startingEventsUsingOffsetReceiver = offsetReceiver.receiveSync(100);

        Assert.assertTrue(startingEventsUsingOffsetReceiver != null && startingEventsUsingOffsetReceiver.iterator().hasNext());

        datetimeReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromEnqueuedTime(Instant.EPOCH));
        Iterable<EventData> startingEventsUsingDateTimeReceiver = datetimeReceiver.receiveSync(100);

        Assert.assertTrue(startingEventsUsingOffsetReceiver != null && startingEventsUsingDateTimeReceiver.iterator().hasNext());

        Iterator<EventData> dateTimeIterator = startingEventsUsingDateTimeReceiver.iterator();
        for (EventData eventDataUsingOffset : startingEventsUsingOffsetReceiver) {
            EventData eventDataUsingDateTime = dateTimeIterator.next();
            Assert.assertTrue(
                    String.format("START_OF_STREAM offset: %s, EPOCH offset: %s", eventDataUsingOffset.getSystemProperties().getOffset(), eventDataUsingDateTime.getSystemProperties().getOffset()),
                    eventDataUsingOffset.getSystemProperties().getOffset().equalsIgnoreCase(eventDataUsingDateTime.getSystemProperties().getOffset()));

            if (!dateTimeIterator.hasNext())
                break;
        }
    }

    @Test()
    public void testReceiverLatestFilter() throws EventHubException, ExecutionException, InterruptedException {
        offsetReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromEndOfStream());
        Iterable<EventData> events = offsetReceiver.receiveSync(100);
        Assert.assertTrue(events == null);

        TestBase.pushEventsToPartition(ehClient, partitionId, 10).get();
        events = offsetReceiver.receiveSync(100);
        Assert.assertTrue(events != null && events.iterator().hasNext());
    }

    @Test()
    public void testReceiverOffsetInclusiveFilter() throws EventHubException {
        datetimeReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromEnqueuedTime(Instant.EPOCH));
        final Iterable<EventData> events = datetimeReceiver.receiveSync(100);

        Assert.assertTrue(events != null && events.iterator().hasNext());
        final EventData event = events.iterator().next();

        offsetReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromOffset(event.getSystemProperties().getOffset(), true));
        final EventData eventReturnedByOffsetReceiver = offsetReceiver.receiveSync(10).iterator().next();

        Assert.assertTrue(eventReturnedByOffsetReceiver.getSystemProperties().getOffset().equals(event.getSystemProperties().getOffset()));
        Assert.assertTrue(eventReturnedByOffsetReceiver.getSystemProperties().getSequenceNumber() == event.getSystemProperties().getSequenceNumber());
    }

    @Test()
    public void testReceiverOffsetNonInclusiveFilter() throws EventHubException {
        datetimeReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromEnqueuedTime(Instant.EPOCH));
        Iterable<EventData> events = datetimeReceiver.receiveSync(100);

        Assert.assertTrue(events != null && events.iterator().hasNext());

        EventData event = events.iterator().next();
        offsetReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromOffset(event.getSystemProperties().getOffset(), false));
        EventData eventReturnedByOffsetReceiver = offsetReceiver.receiveSync(10).iterator().next();

        Assert.assertTrue(eventReturnedByOffsetReceiver.getSystemProperties().getSequenceNumber() == event.getSystemProperties().getSequenceNumber() + 1);
    }

    @Test()
    public void testReceiverSequenceNumberInclusiveFilter() throws EventHubException {
        datetimeReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromEnqueuedTime(Instant.EPOCH));
        Iterable<EventData> events = datetimeReceiver.receiveSync(100);

        Assert.assertTrue(events != null && events.iterator().hasNext());
        EventData event = events.iterator().next();

        offsetReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromSequenceNumber(event.getSystemProperties().getSequenceNumber(), true));
        EventData eventReturnedByOffsetReceiver = offsetReceiver.receiveSync(10).iterator().next();

        Assert.assertTrue(eventReturnedByOffsetReceiver.getSystemProperties().getOffset().equals(event.getSystemProperties().getOffset()));
        Assert.assertTrue(eventReturnedByOffsetReceiver.getSystemProperties().getSequenceNumber() == event.getSystemProperties().getSequenceNumber());
    }

    @Test()
    public void testReceiverSequenceNumberNonInclusiveFilter() throws EventHubException {
        datetimeReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromEnqueuedTime(Instant.EPOCH));
        Iterable<EventData> events = datetimeReceiver.receiveSync(100);

        Assert.assertTrue(events != null && events.iterator().hasNext());

        EventData event = events.iterator().next();
        offsetReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromSequenceNumber(event.getSystemProperties().getSequenceNumber(), false));
        EventData eventReturnedByOffsetReceiver = offsetReceiver.receiveSync(10).iterator().next();

        Assert.assertTrue(eventReturnedByOffsetReceiver.getSystemProperties().getSequenceNumber() == event.getSystemProperties().getSequenceNumber() + 1);
    }

    @Test()
    public void testReceivedBodyAndProperties() throws EventHubException {
        datetimeReceiver = ehClient.createReceiverSync(cgName, partitionId, EventPosition.fromEndOfStream());
        datetimeReceiver.setReceiveTimeout(Duration.ofSeconds(5));

        Iterable<EventData> drainedEvents = datetimeReceiver.receiveSync(100);
        while (drainedEvents != null && drainedEvents.iterator().hasNext()) {
            drainedEvents = datetimeReceiver.receiveSync(100);
        }

        final String payload = "TestMessage1";
        final String property1 = "property1";
        final String propertyValue1 = "something1";
        final String property2 = AmqpConstants.AMQP_PROPERTY_MESSAGE_ID;
        final String propertyValue2 = "something2";

        final Consumer<EventData> validateReceivedEvent = new Consumer<EventData>() {
            @Override
            public void accept(EventData event) {
                Assert.assertEquals(new String(event.getBytes()), payload);
                Assert.assertTrue(event.getProperties().containsKey(property1) && event.getProperties().get(property1).equals(propertyValue1));
                Assert.assertTrue(event.getProperties().containsKey(property2) && event.getProperties().get(property2).equals(propertyValue2));
                Assert.assertTrue(event.getSystemProperties().getOffset() != null);
                Assert.assertTrue(event.getSystemProperties().getSequenceNumber() > 0L);
                Assert.assertTrue(event.getSystemProperties().getEnqueuedTime() != null);
                Assert.assertTrue(event.getSystemProperties().getPartitionKey() == null);
                Assert.assertTrue(event.getSystemProperties().getPublisher() == null);
            }
        };

        final EventData sentEvent = EventData.create(payload.getBytes());
        sentEvent.getProperties().put(property1, propertyValue1);
        sentEvent.getProperties().put(property2, propertyValue2);
        final PartitionSender sender = ehClient.createPartitionSenderSync(partitionId);
        try {
            sender.sendSync(sentEvent);
            final EventData receivedEvent = datetimeReceiver.receiveSync(10).iterator().next();
            validateReceivedEvent.accept(receivedEvent);

            sender.sendSync(receivedEvent);
            final EventData reSendReceivedEvent = datetimeReceiver.receiveSync(10).iterator().next();
            validateReceivedEvent.accept(reSendReceivedEvent);
        } finally {
            sender.closeSync();
        }
    }

    @After
    public void testCleanup() throws EventHubException {
        if (offsetReceiver != null) {
            offsetReceiver.closeSync();
            offsetReceiver = null;
        }

        if (datetimeReceiver != null) {
            datetimeReceiver.closeSync();
            datetimeReceiver = null;
        }
    }
}

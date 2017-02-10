package com.google.pubsub.jms.light.integration;

import autovalue.shaded.com.google.common.common.collect.Lists;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.Message;
import com.google.cloud.pubsub.PubSub;
import com.google.pubsub.jms.light.PubSubConnectionFactory;
import com.google.pubsub.jms.light.destination.PubSubTopicDestination;
import io.grpc.ManagedChannelBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

@RunWith(MockitoJUnitRunner.class)
public class PublisherIntegrationTest extends BaseIntegrationTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger(PublisherIntegrationTest.class);
  private List<String> toSend = Lists.newArrayList(
      "\"Mystery creates wonder and wonder is the basis of man's desire to understand.\" -- Neil Armstrong",
      "\"A-OK full go.\" -- Alan B. Shepard",
      "\"Houston, Tranquillity Base here. The Eagle has landed.\" -- Neil Armstrong"
  );

  @Spy
  private ConnectionFactory connectionFactory = new PubSubConnectionFactory();

  @Spy
  private Topic topic = new PubSubTopicDestination(String.format("projects/%s/topics/%s", PROJECT_ID, TOPIC_NAME));

  @Mock
  private GoogleCredentials googleCredentials;

  @Before
  public void setUp()
  {
    PubSubConnectionFactory factory = (PubSubConnectionFactory) connectionFactory;
    factory.setChannelBuilder(ManagedChannelBuilder.forAddress(getServiceHost(), getServicePort()).usePlaintext(true));
    factory.setCredentials(googleCredentials);
  }

  @Test
  public void sunnyDayPublish() throws JMSException
  {
    try (final Connection connection = connectionFactory.createConnection())
    {
      final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      // prepare producer 
      final MessageProducer producer = session.createProducer(topic);
      
      // prepare messages to send
      for (final String text : toSend)
      {
        final TextMessage wowMessage = session.createTextMessage(text);
        producer.send(wowMessage);
      }

      // verify
      // configure PubSub's subscriber and message processor. Not the JMS.
      final WowMessageProcessor messageProcessor = new WowMessageProcessor();
      getServiceSubscription().pullAsync(messageProcessor);

      // wait till all messages received.
      await().atMost(5, SECONDS).until(
          new Callable<List<Message>>()
          {
            @Override public List<Message> call() throws Exception {return messageProcessor.getReceivedMessages();}
          },
          hasSize(greaterThanOrEqualTo(toSend.size())));
    }
  }

  static class WowMessageProcessor implements PubSub.MessageProcessor
  {
    final List<Message> received = Lists.newArrayList();

    @Override
    public void process(final Message message) throws Exception
    {
      received.add(message);

      LOGGER.info(
          String.format("Received: [id: %s, payload: %s]", message.getId(), message.getPayloadAsString()));
    }

    List<Message> getReceivedMessages()
    {
      return Collections.unmodifiableList(received);
    }
  }
}

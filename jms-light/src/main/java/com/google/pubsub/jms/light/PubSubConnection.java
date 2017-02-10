package com.google.pubsub.jms.light;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Sets;
import io.grpc.ManagedChannelBuilder;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import java.io.IOException;
import java.util.Set;

/**
 * Default PubSub {@link Connection} implementation.
 *
 * @author Maksym Prokhorenko
 */
public class PubSubConnection implements Connection
{
  private String clientId;
  private ExceptionListener exceptionListener;
  private Set<Session> sessions = Sets.newConcurrentHashSet();

  private GoogleCredentials credentials;
  private ManagedChannelBuilder channelBuilder;

  /**
   * Default constructor.
   * @param channelBuilder is .
   * @param credentials .
   * @throws IOException by credentials lookup process.
   */
  public PubSubConnection(final ManagedChannelBuilder channelBuilder,
                          final GoogleCredentials credentials)
      throws IOException
  {
    this.channelBuilder = channelBuilder;
    this.credentials = credentials;
  }

  @Override
  public Session createSession(final boolean transacted, final int acknowledgeMode) throws JMSException
  {
    final Session result;
    if (transacted)
    {
      throw new UnsupportedOperationException("Transactions is not supported yet.");
    }
    else
    {
      result = createSession(acknowledgeMode);
    }

    return result;
  }

  @Override
  public Session createSession(final int acknowledgeMode) throws JMSException
  {
    final Session result;

    if (Session.SESSION_TRANSACTED == acknowledgeMode)
    {
      throw new UnsupportedOperationException("Transactions is not supported yet.");
    }
    else
    {
      result = new PubSubSession(this, false, acknowledgeMode);
      sessions.add(result);
    }

    return result;
  }

  @Override
  public Session createSession() throws JMSException
  {
    return createSession(Session.AUTO_ACKNOWLEDGE);
  }

  @Override
  public String getClientID() throws JMSException
  {
    return clientId;
  }

  @Override
  public void setClientID(final String clientId) throws JMSException
  {
    this.clientId = clientId;
  }

  @Override
  public ConnectionMetaData getMetaData() throws JMSException
  {
    return null;
  }

  @Override
  public ExceptionListener getExceptionListener() throws JMSException
  {
    return exceptionListener;
  }

  @Override
  public void setExceptionListener(final ExceptionListener exceptionListener) throws JMSException
  {
    this.exceptionListener = exceptionListener;
  }

  public ManagedChannelBuilder getChannelBuilder()
  {
    return channelBuilder;
  }

  public GoogleCredentials getCredentials()
  {
    return credentials;
  }

  @Override
  public void start() throws JMSException
  {

  }

  @Override
  public void stop() throws JMSException
  {

  }

  @Override
  public void close() throws JMSException
  {
    for (final Session session : sessions)
    {
      session.close();
    }
  }

  @Override
  public ConnectionConsumer createConnectionConsumer(final Destination destination,
                                                     final String messageSelector,
                                                     final ServerSessionPool serverSessionPool,
                                                     final int maxMessages)
      throws JMSException
  {
    return null;
  }

  @Override
  public ConnectionConsumer createSharedConnectionConsumer(final Topic topic,
                                                           final String subscriptionName,
                                                           final String messageSelector,
                                                           final ServerSessionPool serverSessionPool,
                                                           final int maxMessages)
      throws JMSException
  {
    return null;
  }

  @Override
  public ConnectionConsumer createDurableConnectionConsumer(final Topic topic,
                                                            final String subscriptionName,
                                                            final String messageSelector,
                                                            final ServerSessionPool serverSessionPool,
                                                            final int maxMessages)
      throws JMSException
  {
    return null;
  }

  @Override
  public ConnectionConsumer createSharedDurableConnectionConsumer(final Topic topic,
                                                                  final String subscriptionName,
                                                                  final String messageSelector,
                                                                  final ServerSessionPool serverSessionPool,
                                                                  final int maxMessages)
      throws JMSException
  {
    return null;
  }
}

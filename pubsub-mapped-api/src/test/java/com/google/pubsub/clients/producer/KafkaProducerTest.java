// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.pubsub.clients.producer;

import static org.powermock.api.mockito.PowerMockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Publisher.Builder;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.common.collect.ImmutableMap;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Publisher.class, TopicAdminClient.class})
public class KafkaProducerTest {

  private ApiFuture lf;
  private Publisher stub;
  private Builder stubBuilder;
  private Properties properties;
  private Serializer serializer;
  private Deserializer deserializer;
  private TopicAdminClient topicAdmin;
  private ArgumentCaptor<PubsubMessage> captor;
  private KafkaProducer<String, Integer> publisher;

  @Before
  public void setUp() throws IOException {
    properties = new Properties();
    properties.putAll(new ImmutableMap.Builder<>()
            .put("acks", "1")
            .put("project", "project")
            .put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            .put("value.serializer", "org.apache.kafka.common.serialization.IntegerSerializer")
            .build()
    );

    lf = PowerMockito.mock(ApiFuture.class);
    captor = ArgumentCaptor.forClass(PubsubMessage.class);

    stub = PowerMockito.mock(Publisher.class, Mockito.RETURNS_DEEP_STUBS);
    stubBuilder = PowerMockito.mock(Builder.class, Mockito.RETURNS_DEEP_STUBS);
    topicAdmin = PowerMockito.mock(TopicAdminClient.class, Mockito.RETURNS_DEEP_STUBS);

    PowerMockito.mockStatic(Publisher.class);
    PowerMockito.mockStatic(TopicAdminClient.class);

    when(stubBuilder.build()).thenReturn(stub);
    when(stub.publish(captor.capture())).thenReturn(lf);
    when(TopicAdminClient.create()).thenReturn(topicAdmin);
    when(Publisher.defaultBuilder(Matchers.<TopicName>any())).thenReturn(stubBuilder);
    when(stubBuilder.setRetrySettings(Matchers.<RetrySettings>any())).thenReturn(stubBuilder);
    when(stubBuilder.setBatchingSettings(Matchers.<BatchingSettings>any())).thenReturn(stubBuilder);

    publisher = new KafkaProducer<String, Integer>(properties, null, null);
  }

  @Test
  public void flush() {
    publisher.send(new ProducerRecord<String, Integer>("topic", 123));

    publisher.flush();

    publisher.send(new ProducerRecord<String, Integer>("topic", 456));

    try {
      Mockito.verify(stub, Mockito.times(1)).shutdown();

      Mockito.verify(stub, Mockito.times(2))
          .publish(Matchers.<PubsubMessage>any());

      Mockito.verify(stubBuilder, Mockito.times(2)).build();
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  @Test
  public void callback() {
    serializer = new IntegerSerializer();

    Callback cb = new Callback() {
      @Override
      public void onCompletion(RecordMetadata metadata, Exception exception) {
        Assert.assertEquals(metadata.topic(), "topic");

        Assert.assertEquals(metadata.serializedKeySize(), 0);

        Assert.assertEquals(metadata.serializedValueSize(),
            serializer.serialize("topic", 123).length);
      }
    };

    publisher.send(new ProducerRecord<String, Integer>("topic", 123), cb);
  }

  @Test
  public void serializers() {
    publisher.send(new ProducerRecord<String, Integer>("topic", 123));

    deserializer = new StringDeserializer();

    Assert.assertEquals("Key should be an empty string.",
        "", deserializer.deserialize("topic",
            captor.getValue().getAttributesMap().get("key").getBytes()));

    deserializer = new IntegerDeserializer();

    Assert.assertEquals("Value should be the one previously provided.",
        123, deserializer.deserialize("topic",
            captor.getValue().getData().toByteArray()));
  }

  @Test (expected = NullPointerException.class)
  public void publishNull() {
    publisher.send(new ProducerRecord<String, Integer>("topic", null));
  }

  @Test (expected = IllegalArgumentException.class)
  public void negativeTimeout() {
    publisher.close(-1, TimeUnit.SECONDS);
  }

  @Test
  public void closeOnCompletion() {
    publisher.send(new ProducerRecord<String, Integer>("topic", 123));

    publisher.close();

    try {
      publisher.send(new ProducerRecord<String, Integer>("topic", 123));
    } catch (Exception e) {}

    try {
      Mockito.verify(stub, Mockito.times(1)).shutdown();

      Mockito.verify(stub, Mockito.times(1))
          .publish(Matchers.<PubsubMessage>any());
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  @Test (expected = NullPointerException.class)
  public void emptyRecordPublish() {
    publisher.send(null);
  }

  @Test (expected = NullPointerException.class)
  public void publishEmptyMessage() {
    KafkaProducer<String, String> pub = new KafkaProducer<String, String>(properties,
        null, new StringSerializer());
    pub.send(new ProducerRecord<String, String>("topic", ""));
  }

  @Test
  public void numberOfPublishIssued() {
    publisher.send(new ProducerRecord<String, Integer>("topic", 123));

    Mockito.verify(stub, Mockito.times(1))
        .publish(Matchers.<PubsubMessage>any());

    publisher.send(new ProducerRecord<String, Integer>("topic", 456));

    Mockito.verify(stub, Mockito.times(2))
        .publish(Matchers.<PubsubMessage>any());
  }

  @Test (expected = RuntimeException.class)
  public void publishToClosedPublisher() {
    publisher.close();

    publisher.send(new ProducerRecord<String, Integer>("topic", 123));
  }

  @Test
  public void interceptRecords() {
    int key = 123;

    PrintStream original = System.out;
    OutputStream os = new ByteArrayOutputStream(100);

    System.setOut(new PrintStream(os));

    properties.put("interceptor.classes", MultiplyByTenInterceptor.class.getName());

    KafkaProducer<String, Integer> pub =
            new KafkaProducer<String, Integer>(properties, null, null);

    pub.send(new ProducerRecord<String, Integer>("topic", key));

    System.setOut(original);

    Assert.assertEquals(10 * key, Integer.parseInt(os.toString()));
  }

  //This one is supposed to work as the previous but log an exception.
  @Test
  public void interceptRecordsWithException() {
    int key = 123;

    PrintStream original = System.out;
    OutputStream os = new ByteArrayOutputStream();

    System.setOut(new PrintStream(os));

    List<String> list = new ArrayList<>();
    list.add(MultiplyByTenInterceptor.class.getName());
    list.add(ThrowExceptionInterceptor.class.getName());

    properties.put("interceptor.classes", list);

    KafkaProducer<String, Integer> pub =
            new KafkaProducer<String, Integer>(properties, null, null);

    pub.send(new ProducerRecord<String, Integer>("topic", key));

    System.setOut(original);

    Assert.assertEquals(10 * key, Integer.parseInt(os.toString()));
  }

  public static class MultiplyByTenInterceptor implements ProducerInterceptor<String, Integer> {
    @Override
    public ProducerRecord<String, Integer> onSend(ProducerRecord<String, Integer> producerRecord) {
      int updatedValue = 10 * producerRecord.value();
      System.out.print(updatedValue);
      return new ProducerRecord<String, Integer>(producerRecord.topic(), producerRecord.key(), updatedValue);
    }

    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) { }

    @Override
    public void close() {
      System.out.print("Closed");
    }

    @Override
    public void configure(Map<String, ?> map) { }
  }

  public static class ThrowExceptionInterceptor implements ProducerInterceptor<String, Integer> {
    @Override
    public ProducerRecord<String, Integer> onSend(ProducerRecord<String, Integer> producerRecord) {
      throw new RuntimeException();
    }

    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) { }

    @Override
    public void close() {
      System.out.print("Closed");
    }

    @Override
    public void configure(Map<String, ?> map) { }
  }
}
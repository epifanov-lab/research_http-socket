/*
 * WebClientSocket.java
 * webka
 *
 * Copyright (C) 2019, Realtime Technologies Ltd. All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the
 * property of Realtime Technologies Limited and its SUPPLIERS, if any.
 *
 * The intellectual and technical concepts contained herein are
 * proprietary to Realtime Technologies Limited and its suppliers and
 * may be covered by Russian Federation and Foreign Patents, patents
 * in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Realtime Technologies Limited.
 */

package com.example.research_httpsocket;

import org.json.JSONObject;
import org.reactivestreams.Publisher;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.util.function.Tuple2;
import ru.realtimetech.webka.utils.java.Json;
import ru.realtimetech.webka.utils.reactor.Attempts;
import ru.realtimetech.webka.utils.reactor.Schedule;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * @author Gleb Nikitenko
 * @since 09.04.19
 */
@Singleton
public final class WebClientSocket {

  /** Initial predicate. */
  static final Predicate<String> INITIAL = "42[\"channel_inited\",null]"::equals;//"40"::equals;

  /** Packet types. */
  private static final char
    ENGINE_CONNECT = '0',
    ENGINE_CLOSE = '1',
    ENGINE_PING = '2',
    ENGINE_PONG = '3',
    ENGINE_MESSAGE = '4',
    ENGINE_UPGRADE = '5',
    ENGINE_NOOP = '6';

  /** Socket types. */
  private static final char
    SOCKET_CONNECT = '0',
    SOCKET_DISCONNECT = '1',
    SOCKET_EVENT = '2',
    SOCKET_ASK = '3',
    SOCKET_ERROR = '4';

  /** No message (for stream filtering). */
  private static final String EMPTY_MESSAGE = "";

  /** Socket updater. */
  private static final AtomicReferenceFieldUpdater<WebClientSocket, WebSocket>
    SOCKET = newUpdater(WebClientSocket.class, WebSocket.class, "mSocket");

  /** Socket processor. */
  private final DirectProcessor<WebSocket>
    mProcessor = DirectProcessor.create();

  /** Connected state. */
  public final Flux<Boolean> connected =
    mProcessor.map(v -> v != WebSocket.EMPTY);

  /** Controller sink. */
  private final FluxSink<WebSocket> mSocketSink =
    mProcessor.sink(FluxSink.OverflowStrategy.BUFFER);

  /** Common Flow Source. */
  private final Flux<Object> mSource;

  /** Volatile websocket. */
  private volatile WebSocket mSocket = WebSocket.EMPTY;

  @Inject
  WebClientSocket
    (Function<? super Publisher<Tuple2<String, Predicate<String>>>, ? extends
      Publisher<String>> socket, @Named("session") Flux<JSONObject> session) {

    final Flux<Tuple2<String, Predicate<String>>> input = Flux.create(sink -> {
      setController(WebSocket.create(sink, session));
      sink.onDispose(() -> setController(WebSocket.EMPTY));
    }, FluxSink.OverflowStrategy.BUFFER);

    mSource = Flux.merge
      (mProcessor, input
        .transform(socket)
        .transform(WebClientSocket::skip42Only)
        .transform(WebClientSocket::skipNoEmpty)
        .transform(Attempts::backOff)
      ).publish(1).refCount(1);
  }

  /**
   * @param source source flux
   *
   * @return transformed flux
   */
  private static Flux<String> skip42Only(Flux<String> source) {
    return source.map(v -> {
      final char type = v.charAt(0);
      switch (type) {
        case ENGINE_CONNECT:
          return EMPTY_MESSAGE;
        case ENGINE_CLOSE:
          return EMPTY_MESSAGE;
        case ENGINE_PING:
          return EMPTY_MESSAGE;
        case ENGINE_PONG:
          return EMPTY_MESSAGE;
        case ENGINE_MESSAGE:
          final char message = v.charAt(1);
          switch (message) {
            case SOCKET_CONNECT:
              return v;
            case SOCKET_DISCONNECT:
              return EMPTY_MESSAGE;
            case SOCKET_EVENT:
              return v.substring(2);
            case SOCKET_ASK:
              return EMPTY_MESSAGE;
            case SOCKET_ERROR:
              return EMPTY_MESSAGE;
            default:
              return EMPTY_MESSAGE;
          }
        case ENGINE_UPGRADE:
          return EMPTY_MESSAGE;
        case ENGINE_NOOP:
          return EMPTY_MESSAGE;
        default:
          return EMPTY_MESSAGE;
      }
    });
  }

  /**
   * @param source source flux
   *
   * @return transformed flux
   */
  private static Flux<String> skipNoEmpty(Flux<String> source) {
    return source.filter(v -> v.length() > 0);
  }

  /** @param controller new controller */
  private void setController(WebSocket controller) {
    WebSocket previous;
    do if ((previous = SOCKET.get(this)) == controller) return;
    while (!SOCKET.compareAndSet(this, previous, controller));
    previous.dispose();
    mSocketSink.next(controller);
  }

  /**
   * @param name name of events
   * @param json json-request body
   *
   * @return stream of events
   */
  public Flux<JSONObject> events(String name, Json.JsonBuilder json, String filter) {
    final Disposable.Swap swap = Disposables.swap();
    return
      mSource
        .doOnNext(next ->
          events(next, swap, name, json))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(v -> v.contains(filter))
        .doOnCancel(swap::dispose)
        .transform(Schedule::io_work)
        .map(Json::array)
        .map(v -> v.optJSONObject(1))
        .map(v -> v.optJSONObject("data"));
  }

  /**
   * @param next    next flow element
   * @param swap    swap disposable holder
   * @param name    name of subscription
   * @param builder json body arguments
   */
  private void events
  (Object next, Disposable.Swap swap, String name, Json.JsonBuilder builder) {
    if (!(next instanceof WebSocket)) return;
    final WebSocket socket = (WebSocket) next;
    final String subscribe = "subscribe_" + name;
    final JSONObject json = Json.newJson(builder);
    swap.update(socket.command(subscribe, json)
      .subscribe(v -> {}, v -> {}, () -> swap.update(() ->
        socket.command("un" + subscribe, json).block())));
  }
}

/*
 * WebClientHttp.java
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

import android.net.Uri;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;

import javax.inject.Inject;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import ru.realtimetech.webka.utils.java.Json;
import ru.realtimetech.webka.utils.java.Json.JsonBuilder;
import ru.realtimetech.webka.utils.reactor.AtomicDisposable;
import ru.realtimetech.webka.utils.reactor.Schedule;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static reactor.core.publisher.Mono.fromCallable;
import static ru.realtimetech.webka.infra.WebModule.JSON;
import static ru.realtimetech.webka.infra.WebModule.TEXT;

/**
 * OkHttp-Based Web Client.
 * Domain Server calls in reactive api.
 *
 * @author Gleb Nikitenko
 * @since 23.03.19
 */
@SuppressWarnings("unused")
public final class WebClientHttp {

  /** Proprietary OkHttp Way vs Reactive Way. */
  private static final boolean ENQUEUE_MODE = false;

  /** Base Uri Builder. */
  public final Uri base = Uri.parse(WebModule.httpUrl().build().toString());

  /** OkHttp Client. */
  private final OkHttpClient mOkHttp;

  /**
   * Constructs a new {@link WebClientHttp}.
   *
   * @param okHttp okHttp instance
   */
  @Inject
  public WebClientHttp(OkHttpClient okHttp) {
    mOkHttp = okHttp;
  }

  /**
   * @param http   client instance
   * @param url    request httpUrl
   * @param method method
   * @param body   request body
   *
   * @return http call
   */
  private static Call call(OkHttpClient http,
                           Consumer<HttpUrl.Builder> url,
                           String method, RequestBody body) {
    final HttpUrl.Builder urlBldr = WebModule.httpUrl();
    url.accept(urlBldr);
    final Request.Builder reqBldr = new Request.Builder().url(urlBldr.build());
    reqBldr.method(method, body);
    return http.newCall(reqBldr.build());
  }

  /**
   * @param value body content
   *
   * @return okhttp request body
   */
  private static RequestBody toJson(JSONObject value) {
    return RequestBody.create(JSON, value.toString());
  }

  /**
   * @param value body content
   *
   * @return okhttp request body
   */
  @SuppressWarnings("unused")
  static RequestBody toText(String value) {
    return RequestBody.create(TEXT, value);
  }

  /**
   * @param call okhttp call
   *
   * @return reactive mono
   */
  private static Mono<String> mono(Call call) {
    return ENQUEUE_MODE ?
      Mono.create((Consumer<MonoSink<String>>) sink -> {
        sink.onDispose(new AtomicDisposable(call::cancel));
        call.enqueue(new Callback(sink));
      }) :
      fromCallable(() -> requireNonNull(call.execute().body()).string())
        .transform(Schedule::io_work);
  }

  /**
   * GET/POST request(depends on body nullability).
   *
   * @param u httpUrl builder
   * @param b json body or null (for simple get's)
   *
   * @return async result
   */
  public final Mono<String> call(Consumer<HttpUrl.Builder> u, JsonBuilder b) {
    final Optional<JsonBuilder> optional = ofNullable(b);
    return mono(call(mOkHttp, u, optional.map(value -> "POST").orElse("GET"),
      optional.map(Json::newJson).map(WebClientHttp::toJson).orElse(null)));
  }

  /**
   * DELETE request.
   *
   * @param u httpUrl builder
   * @param b optional body
   *
   * @return async result
   */
  public final Mono<String> delete(Consumer<HttpUrl.Builder> u, JsonBuilder b) {
    return mono(call(mOkHttp, u, "DELETE", ofNullable(b)
      .map(Json::newJson).map(WebClientHttp::toJson).orElse(null)));
  }

  /**
   * PUT request.
   *
   * @param u httpUrl builder
   * @param b required body
   *
   * @return async result
   */
  public final Mono<String> put(Consumer<HttpUrl.Builder> u, JsonBuilder b) {
    return mono(call(mOkHttp, u, "PUT", toJson(Json.newJson(requireNonNull(b)))));
  }

  /** Internal Callback. */
  private static final class Callback implements okhttp3.Callback {

    /** Disposed state. */
    private static final MonoSink<String> DISPOSED = null;

    /** This class */
    private static final Class<Callback> CLAZZ = Callback.class;

    /** Callable atomic updater. */
    private static final AtomicReferenceFieldUpdater<Callback, MonoSink>
      RUNNABLE = AtomicReferenceFieldUpdater.newUpdater
      (CLAZZ, MonoSink.class, "mDisposable");

    /** Volatile disposable. */
    @SuppressWarnings("unused")
    private volatile MonoSink<String> mDisposable;

    /**
     * Constructs a new {@link Callback}.
     *
     * @param disposable mono-sink
     */
    Callback(MonoSink<String> disposable) {
      mDisposable = disposable;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public final void onFailure(Call call, IOException error) {
      MonoSink<String> current;
      do if ((current = RUNNABLE.get(this)) == DISPOSED) return;
      while (!RUNNABLE.compareAndSet(this, current, DISPOSED));
      current.error(error);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public final void onResponse(Call call, Response response) {
      MonoSink<String> current;
      do if ((current = RUNNABLE.get(this)) == DISPOSED) return;
      while (!RUNNABLE.compareAndSet(this, current, DISPOSED));
      final ResponseBody body = response.body();
      if (body == null) current.success();
      else
        try {current.success(body.string());}
        catch (IOException exception)
        {current.error(exception);}
    }
  }
}

package com.example.research_httpsocket;

import java.io.IOException;
import java.util.function.Function;

import okhttp3.Call;
import okhttp3.Call.Factory;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.util.annotation.NonNull;

public final class Layer1 {

  /**
   * @param client  http-client
   * @param enqueue enqueue mode
   *
   * @return http executor
   */
  @NonNull
  public static Function<Request, Mono<Response>>
  createA(@NonNull Factory client, boolean enqueue) {
    return enqueue ?
      v -> enqueue(client, v) :
      v -> execute(client, v);
  }

  @NonNull
  public static AsyncHodilka
  create(@NonNull Factory client, boolean enqueue) {
    return enqueue ?
      v -> enqueue(client, v) :
      v -> execute(client, v);
  }

  @NonNull
  public static SyncHodilka
  create(@NonNull Interceptor.Chain chain) {
    return chain::proceed;
  }

  @NonNull
  public static SyncHodilka
  create(@NonNull Factory client) {
    return request -> client.newCall(request).execute();
  }


  @NonNull
  public static SyncHodilka
  create() {
    return request -> {
      throw new IOException("not implemented!"); // http url connection
    };
  }

  /**
   * @param client  okHttp client
   * @param request http-request
   *
   * @return async response result
   */
  private static Mono<Response> enqueue(@NonNull Factory client, @NonNull Request request) {
    return Mono.create(sink -> {
      final Call call = client.newCall(request);
      call.enqueue(callback(sink.onCancel(disposable(call))));
    });
  }

  /**
   * @param client  okHttp client
   * @param request http-request
   *
   * @return async response result
   */
  private static Mono<Response> execute(@NonNull Factory client, @NonNull Request request) {
    return Mono.fromCallable(() -> client.newCall(request).execute());
  }

  /**
   * @param call okHttp call
   *
   * @return reactor's disposable
   */
  private static Disposable disposable(@NonNull Call call) {
    return new Disposable() {
      @Override
      public final void dispose() {
        call.cancel();
      }

      @Override
      public final boolean isDisposed() {
        return call.isCanceled();
      }
    };
  }

  /**
   * @param sink target mono's sink
   *
   * @return okHttp-compatibility callback
   */
  private static Callback   callback(@NonNull MonoSink<Response> sink) {
    return new Callback() {
      @Override
      public final void onFailure(@NonNull Call call, @NonNull IOException exception) {
        sink.error(exception);
      }

      @Override
      public final void onResponse(@NonNull Call call, @NonNull Response response) {
        sink.success(response);
      }
    };
  }

}

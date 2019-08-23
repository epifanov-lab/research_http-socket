package com.example.research_httpsocket;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.research_httpsocket.field_changing_research.FieldHolder;
import com.example.research_httpsocket.infra.AsyncHodilka;
import com.example.research_httpsocket.infra.Layer1;
import com.example.research_httpsocket.infra.SyncHodilka;

import org.json.JSONObject;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import reactor.core.publisher.Mono;

public class MainActivity extends AppCompatActivity {

  public final static HttpUrl apiUrl = new HttpUrl.Builder()
    .scheme("http")
    //.host("api.int.rtt.space")
    .host("api.webka.com")
    .addPathSegment("api")
    .addPathSegment("v1")
    .addPathSegment("init")
    .build();

  // wss://webka.com/wss/?EIO=3&transport=websocket
  public final static HttpUrl wsUrl = new HttpUrl.Builder()
    .scheme("https") // may be wss ?
    //.host("api.int.rtt.space")
    .host("webka.com")
    //.addPathSegment("api")
    .addPathSegment("wss/")
    .addQueryParameter("EIO", "3")
    .addQueryParameter("transport", "websocket")
    .build();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    //test1();
    test2();

  }

  private void test2() {

    FieldHolder data = new FieldHolder("INITIAL");

    Runnable R1 = () -> System.out.println("R1 fired: " + data.getField());
    Runnable R2 = () -> System.out.println("R2 fired: " + data.getField());
    Runnable R3 = () -> System.out.println("R3 fired: " + data.getField());
    Runnable R4 = () -> System.out.println("R4 fired: " + data.getField());

    data.addOnFieldChangedListener(R1);

    data.setField("duck");

    new Thread(() -> {
      int i = 0;
      do {
        try {
          data.setField("T1: " + i);
          i++;
          Thread.sleep(1000);

          if (i == 4) {
            data.addOnFieldChangedListener(R2);
          }

        } catch (InterruptedException e) {
          e.printStackTrace();
        }

      } while (i < 9);
    }).start();

    new Thread(() -> {
      int i = 0;
      do {
        try {
          data.setField("T2: " + i);
          i++;
          Thread.sleep(900);

          if (i == 3) {
            data.addOnFieldChangedListener(R3);
            data.removeOnFieldChangedListener(R1);
          }

        } catch (InterruptedException e) {
          e.printStackTrace();
        }

      } while (i < 9);
    }).start();

    new Thread(() -> {
      int i = 0;
      do {
        try {
          data.setField("T3: " + i);
          i++;
          Thread.sleep(1100);

          if (i == 2) {
            data.addOnFieldChangedListener(R4);
          }

        } catch (InterruptedException e) {
          e.printStackTrace();
        }

      } while (i < 9);
    }).start();

  }

  private void test1() {
    CookieJar cookieJar = cookieJar(new CookieManager().getCookieStore());
    OkHttpClient client = new OkHttpClient.Builder()
      .cookieJar(cookieJar)
      .build();

    AsyncHodilka asyncHodilka = Layer1.create(
      client, true);

    SyncHodilka syncHodilka = Layer1.create(
      client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build());

    new Thread(() -> {
      try {

        System.out.println(" !!! api before " + cookieJar.loadForRequest(apiUrl));
        System.out.println(" !!! ws before " + cookieJar.loadForRequest(wsUrl));

        init(Layer1.createA(
          client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build(),
          false), cookieJar);

        System.out.println(" !!! api after " + cookieJar.loadForRequest(apiUrl));
        System.out.println(" !!! ws after " + cookieJar.loadForRequest(wsUrl));

      } catch (Throwable throwable) {
        throwable.printStackTrace();
      }
    }).start();
  }

  /**
   * @param cookieJar мы ожидаем что куки джар будет нашим
   *                  и методы "loadForRequest", "saveFromResponse"
   *                  - будут блокировать куки стор, если УРЛ == инит
   */
  private static void init(Function<Request, Mono<Response>> client, CookieJar cookieJar) throws Throwable {

    // cookieJar будет нашим и load или другой метод включит полную блокирову КукиСтораджа
    // loadForRequestAndLock(apiUrl), должен идти самым первым. так как блокирует всем доступ к иниту

    List<Cookie> okHttpCookies = cookieJar.loadForRequest(apiUrl);

    List<Cookie> resultWSCookies = new ArrayList<>();

    Request.Builder requestBuilder = new Request.Builder();

    if (!okHttpCookies.isEmpty()) {
      requestBuilder.header("Cookie", cookieHeader(okHttpCookies));
    }

    Request request = requestBuilder.url(apiUrl).build();

    try (Response response = client.apply(request).block()) {

      String wsData = new JSONObject(response.body().string())
        .getJSONObject("result").toString(); // сделать без пересериализации

      System.out.println("wsData: " + wsData);

      List<Cookie> cookies = Cookie.parseAll(apiUrl, response.headers());

      Cookie apiCookie = cookies.stream()
        .filter(cookie -> "PHPSESSID".equals(cookie.name()))
        .findFirst()
        .orElseGet(() ->
          okHttpCookies.stream()
            .filter(httpCookie -> "PHPSESSID".equals(httpCookie.name()))
            .findFirst()
            .orElse(null));

      System.out.println("apiCookie: " + apiCookie  + " " + apiCookie.domain());

      Cookie.Builder builder = new Cookie.Builder()
        .name("WSSESSID")
        .value(wsData)
        .expiresAt(apiCookie.expiresAt())

        .domain(wsUrl.host())
        //.hostOnlyDomain(apiCookie.domain());

        .path(apiCookie.path());

      if (apiCookie.secure()) builder.secure();
      if (apiCookie.httpOnly()) builder.httpOnly();

      Cookie wsCookie = builder.build();

      /* --- */
      resultWSCookies.add(wsCookie);

      System.out.println("wsCookie: " + wsCookie   + " " + wsCookie.domain());

      // !!! всё оно обёрнуто в try {} finally { СНИМАЕМ БЛОКИРОВКУ }
    } catch (RuntimeException t) {
      final Throwable cause = t.getCause();
      if (cause != null) throw cause;

    } finally {
      // unlock

      cookieJar.saveFromResponse(wsUrl, resultWSCookies);
    }
  }

  /** Returns a 'Cookie' HTTP request header with all cookies, like {@code a=b; c=d}. */
  private static String cookieHeader(List<Cookie> cookies) {
    StringBuilder cookieHeader = new StringBuilder();
    for (int i = 0, size = cookies.size(); i < size; i++) {
      if (i > 0) {
        cookieHeader.append("; ");
      }
      Cookie cookie = cookies.get(i);
      cookieHeader.append(cookie.name()).append('=').append(cookie.value());
    }
    return cookieHeader.toString();
  }

  static CookieJar cookieJar(CookieStore cookies) {
    final CookieHandler cookieHandler = new CookieManager(cookies,
      CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    return new JavaNetCookieJar(cookieHandler);
  }
}
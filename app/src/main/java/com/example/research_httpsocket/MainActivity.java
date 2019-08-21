package com.example.research_httpsocket;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

  public static final String SERVER_URL = "http://httpbin.org/post";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  }

  private void test_1() {
    final OkHttpClient client = new OkHttpClient();

    final Request request = new Request.Builder()
      .url(SERVER_URL)
      .post(new FormBody.Builder()
        //.add("message", "Your message")
        .build())
      .build();

    System.out.println("REQUEST: " + request);

    doAsync(() -> {
      try {
        Response response = client.newCall(request).execute();
        ResponseBody serverAnswer = response.body();
        System.out.println("RESPONSE: " + serverAnswer);

      } catch (IOException e) {
        e.printStackTrace();
      }
    });
  }

  private void doAsync(Runnable runnable) {
    new Thread(runnable).start();
  }

}

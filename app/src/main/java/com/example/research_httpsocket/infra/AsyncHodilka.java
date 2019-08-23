package com.example.research_httpsocket.infra;


import okhttp3.Request;
import okhttp3.Response;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AsyncHodilka {

  Mono<Response> perform(Request request);

}


package com.example.research_httpsocket;


import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;

@FunctionalInterface
public interface SyncHodilka {

  Response perform(Request request) throws IOException;

}


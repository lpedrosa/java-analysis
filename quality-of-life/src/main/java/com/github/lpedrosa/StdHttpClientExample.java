package com.github.lpedrosa;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StdHttpClientExample {
    public static void main(String[] args) throws Exception {
        // Things to test:
        // * simple request API, sending entities, decoding entities, etc.
        // * timeouts
        // * configuring the underlying executor
        // * configuring SSL keystore, MTLS?

        // had to provide custom executor for this to work with maven exec:java plugin
        var executor = Executors.newCachedThreadPool();
        simpleHttpRequest(executor);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void simpleHttpRequest(Executor executor) throws Exception {
        // there an HttpRequest class with a builder, which helps you build requests
        // here is a simple get example
        var request = HttpRequest.newBuilder(URI.create("https://httpstat.us/200")).GET()
                .header("Accepts", "text/plain").build();

        // you need an http client to run this request
        // let's create one with good defaults (check HttpClient#newHttpClient for more
        // details)
        var client = HttpClient.newBuilder().executor(executor).build();

        // we need to provide a body handler, so the client knows how to handle the
        // response.
        // In this case, we want the incoming response as a string
        var bodyHandler = BodyHandlers.ofString();

        // we can now make the request
        var response = client.send(request, bodyHandler);

        // the response object contains all sorts of information about the response
        var statusCode = response.statusCode();
        var headers = response.headers();

        // NOTE: the response body will have the same type as the return type of the
        // BodyHandler#apply method
        var body = response.body();

        System.out.println("== simpleHttpRequest -> response info ==");
        System.out.println("status code: " + statusCode);

        System.out.println("headers:");
        var entries = headers.map().entrySet();
        for (var entry : entries) {
            Function<List<String>, String> prettyPrintList = (s) -> s.stream()
                    .collect(Collectors.joining(",", "[", "]"));

            var s = String.format("\t %s: %s", entry.getKey(), prettyPrintList.apply(entry.getValue()));
            System.out.println(s);
        }

        System.out.println("body: " + body);
    }

    private StdHttpClientExample() {
    }
}
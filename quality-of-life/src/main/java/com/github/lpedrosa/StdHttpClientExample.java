package com.github.lpedrosa;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.stream.Collectors;

public final class StdHttpClientExample {
    public static void main(String[] args) throws Exception {
        // Things to test:
        // * simple request API, sending entities, decoding entities, etc.
        // * timeouts
        // * configuring the underlying executor
        // * configuring SSL keystore, MTLS?
        // we will need an http client to run some of these examples
        // let's create one with good defaults (check HttpClient#newHttpClient for more
        // details)
        var client = HttpClient.newHttpClient();
        simpleHttpRequest(client);
        sendingSomeData(client);
    }

    private static void simpleHttpRequest(HttpClient client) throws Exception {
        // there an HttpRequest class with a builder, which helps you build requests
        // here is a simple get example
        var request = HttpRequest.newBuilder(URI.create("https://httpstat.us/200"))
                                 .GET()
                                 .header("Accept", "text/plain; charset=utf-8")
                                 .build();

        // we need to provide a body handler, so the client knows how to handle the
        // response.
        // In this case, we want the incoming response as a string
        var bodyHandler = BodyHandlers.ofString();

        // we can now make the request
        var response = client.send(request, bodyHandler);

        printResponse("simpleHttpRequest", response);
    }

    private static void sendingSomeData(HttpClient client) throws Exception {
        // sending data requires a BodyPublisher
        // NOTE: these body publisher factory methods, do not automatically
        // set the 'Content-Type' header. You need to set it yourself
        var requestBody = BodyPublishers.ofString("Hello");

        var request = HttpRequest.newBuilder(URI.create("https://httpstat.us/204"))
                                 .POST(requestBody)
                                 .header("Content-Type", "text/plain; charset=utf-8")
                                 .build();

        var response = client.send(request, BodyHandlers.ofString());

        printResponse("sendingSomeData", response);
    }

    private static <T> void printResponse(String callerName, HttpResponse<T> response) {
        System.out.println(String.format("== %s -> response info ==", callerName));
        System.out.println("status code: " + response.statusCode());

        System.out.println("headers:");
        var headerEntries = response.headers()
                                    .map()
                                    .entrySet();
        for (var entry : headerEntries) {
            var s = String.format("\t %s: %s", entry.getKey(), prettyPrintList(entry.getValue()));
            System.out.println(s);
        }

        System.out.println("body: " + response.body());
    }

    private static String prettyPrintList(List<String> strings) {
        if (strings.isEmpty()) {
            return "Nothing";
        } else if (strings.size() == 1) {
            return strings.get(0);
        } else {
            return strings.stream()
                          .collect(Collectors.joining(",", "[", "]"));
        }
    }

    private StdHttpClientExample() {
    }
}

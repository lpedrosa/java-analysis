package com.github.lpedrosa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        decodingSomeJson(client);
        settingTimeouts(client);
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

    private static void decodingSomeJson(HttpClient client) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("https://httpstat.us/200"))
                                 .GET()
                                 .header("Accept", "application/json")
                                 .build();

        // we read the json to a string first because we will re-use it bellow
        // and the response is quite small (which will result into a small string
        // allocation)
        // otherwise we would probably capture the InputStream instead
        var response = client.send(request, BodyHandlers.ofString());
        printResponse("decodingSomeJson", response);

        // using the tree reader
        var mapper = new ObjectMapper();
        var root = mapper.readTree(response.body());

        System.out.println("Decoded response using jackson's tree mapper");
        System.out.println(root.get("code")
                               .asInt());
        System.out.println(root.get("description")
                               .asText());

        // using the object mapper
        var mappedResponse = mapper.readValue(response.body(), HttpStatusResponse.class);
        System.out.println("Decoded response using the object mapper");
        System.out.println(mappedResponse.getCode());
        System.out.println(mappedResponse.getDescription());

        // we can also plug into the whole BodyHandler machinery, by defining
        // our own BodySubscriber. The subscriber use is taken straight from the jdk
        // docs
        // https://docs.oracle.com/en/java/javase/13/docs/api/java.net.http/java/net/http/HttpResponse.BodySubscribers.html#mapping(java.net.http.HttpResponse.BodySubscriber,java.util.function.Function)
        var response2 = client.send(request, jsonBodyHandler(mapper, HttpStatusResponse.class));

        // NOTE the BodyHandler returns a Supplier, because we want the json decoding
        // to happen in the caller's thread, and not on the httpclient's thread
        // I should probably blog about this at some point
        var yetAnotherMappedResponse = response2.body()
                                                .get();
        System.out.println("Decoded response using BodyHandler machinery");
        System.out.println(yetAnotherMappedResponse.getCode());
        System.out.println(yetAnotherMappedResponse.getDescription());
    }

    private static void settingTimeouts(HttpClient client) throws Exception {
        // setting a request timeout as simple as adding a new builder option
        // here we are asking for httpsta.us service to delay the request by 1 sec
        var request = HttpRequest.newBuilder(URI.create("https://httpstat.us/200?sleep=1000"))
                                 .timeout(Duration.ofMillis(500)) // here
                                 .GET()
                                 .build();

        try {
            client.send(request, BodyHandlers.discarding());
        } catch (HttpTimeoutException e) {
            System.err.println("Timed out while receiving the response: " + e.getMessage());
        }

        // You can also set the connection timeout as you are creating the http client
        var clientWithTimeout = HttpClient.newBuilder()
                                          .connectTimeout(Duration.ofMillis(500))
                                          .build();

        request = HttpRequest.newBuilder(URI.create("https://localhost:1"))
                             .GET()
                             .build();

        try {
            clientWithTimeout.send(request, BodyHandlers.discarding());
        } catch (HttpConnectTimeoutException e) {
            System.err.println("Timed out while connecting: " + e.getMessage());
        }
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

    private static class HttpStatusResponse {
        private final int code;
        private final String description;

        @JsonCreator
        public HttpStatusResponse(
                @JsonProperty("code") int code,
                @JsonProperty("description") String description) {
            this.code = code;
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public int getCode() {
            return code;
        }
    }

    private static <T> BodyHandler<Supplier<T>> jsonBodyHandler(ObjectMapper objectMapper, Class<T> targetType) {
        return (HttpResponse.ResponseInfo ri) -> asJSON(objectMapper, targetType);
    }

    private static <T> BodySubscriber<Supplier<T>> asJSON(ObjectMapper objectMapper, Class<T> targetType) {
        BodySubscriber<InputStream> upstream = BodySubscribers.ofInputStream();

        BodySubscriber<Supplier<T>> downstream = BodySubscribers.mapping(
                upstream,
                (InputStream is) -> () -> {
                    try (InputStream stream = is) {
                        return objectMapper.readValue(stream, targetType);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        return downstream;
    }

    private StdHttpClientExample() {
    }
}

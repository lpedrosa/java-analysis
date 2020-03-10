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
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StdHttpClientExample {
    public static void main(String[] args) throws Exception {
        // let's create one with good defaults (check HttpClient#newHttpClient for more
        // details)
        var client = HttpClient.newHttpClient();
        simpleHttpRequest(client);
        sendingSomeData(client);
        decodingSomeJson(client);
        settingTimeouts(client);

        // here we want to configure the http client a bit more
        configuringExecutor();

        // here we configure the different SSL options
        configuringSslOptions();
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

    private static void configuringExecutor() throws Exception {
        // the jdk httpclient uses an executor under the hood, to execute http requests
        // so far we have been using the default one which is a cachedThreadPoolExecutor
        // but you can pass your own executor
        // this is useful if you want to instrument the executor, e.g. report the queue
        // size, etc.

        // let's restrict the thread pool to a single thread
        // NOTE: this executor's queue is unbounded, and the thread factory produces
        // non-daemon threads
        // NOTE: the underlying httpclient implementation uses NIO, which means you
        // will still get concurrent requests, their processing/handling is still
        // single-threaded e.g. think nodejs
        var myExecutor = Executors.newSingleThreadExecutor();

        var client = HttpClient.newBuilder()
                               .executor(myExecutor)
                               .build();

        var urls = List.of(
                URI.create("https://google.com"),
                URI.create("https://microsoft.com"),
                URI.create("https://facebook.com"));

        Stream<HttpRequest> requests = urls.stream()
                                           .map(HttpRequest::newBuilder)
                                           .map(b -> b.build());

        try {
            var start = Instant.now();
            CompletableFuture.allOf(requests.map(request -> client.sendAsync(request, BodyHandlers.discarding()))
                                            .map(futureResponse -> futureResponse.thenRun(
                                                    () -> System.out.println("Completed response")))
                                            .toArray(CompletableFuture<?>[]::new))
                             .join();
            var end = Instant.now();

            System.out.println("Took " + Duration.between(start, end)
                                                 .toMillis()
                    + " to complete the request");
        } finally {
            // the executors threads are not daemon by default, so we need to shut it down
            // properly
            myExecutor.shutdown();
            myExecutor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static void configuringSslOptions() throws Exception {
        // you can create a client that ignores cert validation i.e. similar to "curl
        // -k" by giving it a trust manager that accepts everything.
        // The whole SSL and certificate machinery can get very involved and I should
        // probably write a blog post about it.

        // the SSLContext uses a TrustManager to verify server certificates
        // here we are creating one that accepts everything
        var trustManagers = new TrustManager[] { AllowAllTrustManager.getInstance() };

        // we initialize the context with our TrustManager
        var sslContext = SSLContext.getInstance("TLS");

        sslContext.init(null, trustManagers, null);

        // we can now build the http client
        var client = HttpClient.newBuilder()
                               .sslContext(sslContext)
                               .build();

        // this host uses a self signed cert that we don't normally trust
        var request = HttpRequest.newBuilder(URI.create("https://self-signed.badssl.com/"))
                                 .GET()
                                 .build();

        var response = client.send(request, BodyHandlers.discarding());

        printResponse("configuringSslOptions - allow all", response);

        // if we want to add a particular certificate to the chain then we just need to
        // load it into the client:
        // * create a keystore (or use an existing one)
        // * load up the certs you want
        // * initialize a trust manager factory with the previous keystore
        // * initialize the ssl context with the right trust managers
        // fun right?
        var ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (var is = StdHttpClientExample.class.getResourceAsStream("/badsslcom-self-signed.crt")) {
            ks.load(null, null);
            // load public cert into the key store
            var cert = CertificateFactory.getInstance("X509")
                                         .generateCertificate(is);
            // here the alias name doesn't really matter, unless you where exporting this
            // keystore somewhere
            ks.setCertificateEntry("1", cert);
        }

        var tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        var sslContextWithCerts = SSLContext.getInstance("TLS");

        sslContextWithCerts.init(null, tmf.getTrustManagers(), null);

        var clientWithCerts = HttpClient.newBuilder()
                                        .sslContext(sslContextWithCerts)
                                        .build();

        // using the request above
        response = clientWithCerts.send(request, BodyHandlers.discarding());

        printResponse("configuringSslOptions - with loaded cert", response);
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

    private static final class AllowAllTrustManager implements X509TrustManager {

        private static final AllowAllTrustManager INSTANCE = new AllowAllTrustManager();

        public static final AllowAllTrustManager getInstance() {
            return INSTANCE;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // accept everything, yay!
            System.err.println("WARNING: Accepting client with cert" + chain[0].getSubjectDN());
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // accept everything, yay!
            System.err.println("WARNING: Accepting server with cert" + chain[0].getSubjectDN());
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

    }

    private StdHttpClientExample() {
    }
}

package com.github.lpedrosa;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OptionalExample {
    public static void main(String[] args) {
        generalUsage();
        combinatorsExample();
        optionalArgs();
    }

    private static void generalUsage() {
        // Optional is mostly useful when defining APIs, consumer code, etc.
        // This is because it signals if a particular return value can be null

        // And a method that consumes the result
        Consumer<String> pingHost = s -> System.out.println("Pinging host: " + s);

        // host might not have a value
        var host = fetchHostnameFromService();

        // A way to handle this scenario is to provide a default value with
        // Optional#orElse
        pingHost.accept(host.orElse("defaultHostname"));

        // Consider an alternative way of fetching an hostname, which might also come
        // back empty
        Supplier<Optional<String>> fetchHostnameFromDb = () -> Optional.of("hostnameFromDB");

        // you can combine multiple optional sources with Optional#or
        // the first source to resolve it will be value of the optional
        var maybeHost = fetchHostnameFromService().or(fetchHostnameFromDb);

        // you still need to resolve it, in order to consume it
        pingHost.accept(maybeHost.orElse("defaultHostname"));

        // NOTE: if there is a sensible default that works for most cases, you should
        // always
        // make it easy for your consumers to call your API.
        Supplier<String> fetchHostnameOrDefault = () -> fetchHostnameFromService().orElse("defaultHostname");

        // works and your consumers don't have to unwrap the optional value
        pingHost.accept(fetchHostnameOrDefault.get());

        // Rule of thumb is:
        // Use Optional when you want your consumer to handle the absence of a value
        // explicitly
    }

    private static void combinatorsExample() {
        var host = fetchHostnameFromService();
        // The Optional type provides some combinators (i.e. map, flatMap) which help
        // you combine
        // or transform the optional, without resolving it.

        // For example, you might have a good default for a consuming method,
        // which incompatible with your supplier
        Consumer<InetSocketAddress> readFromSocket = sa -> System.out
                                                                     .println("Reading from host: "
                                                                             + sa.getHostString());

        // use hostname provided or use a default localhost socket instead
        var socketAddress = host.map(h -> InetSocketAddress.createUnresolved(h, 8080))
                                .orElse(InetSocketAddress.createUnresolved("localhost", 8080));

        readFromSocket.accept(socketAddress);

        // if you combine an option with a method that also returns an option, you can
        // flatten it with
        // Option#flatMap
        // i.e. if you use Option#map, you'll end up with Option<Option<Config>>
        var config = host.flatMap(OptionalExample::fetchConfigFor)
                         .orElse(Config.DEFAULT);

        config.getHostname()
              .ifPresentOrElse(h -> {
                  System.out.println("Using config for host: " + h);
              }, () -> {
                  System.out.println("Using default config");
              });
    }

    private static void optionalArgs() {
        // If you have an method argument that is effectively optional, you should make
        // use of method overloading instead
        // This avoids having your consumers instantiating Optional to call your methods

        // consider the config class below
        new Config("someHostname");

        // hostname is optional so we use the overloaded constructor
        new Config();

        // this is better than having your caller instantiating the option e.g.
        // new Config(Optional.ofNullable(null))
    }

    // Consider a method which models a remote call
    private static final Optional<String> fetchHostnameFromService() {
        return Optional.empty();
    }

    private static final class Config {
        private final String hostname;

        public static final Config DEFAULT = new Config();

        public Config() {
            this(null);
        }

        public Config(String hostname) {
            this.hostname = hostname;
        }

        public Optional<String> getHostname() {
            return Optional.ofNullable(this.hostname);
        }
    }

    private static final Optional<Config> fetchConfigFor(String hostname) {
        return Optional.of(new Config(hostname));
    }

    private OptionalExample() {
    }
}

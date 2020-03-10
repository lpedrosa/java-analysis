# Language quality of life features

This contains some examples showing some QoL improvements from Java 8 to 11. All of these files are runnable.

## Running the examples

Run the gradle task `runExample` and provide the class you want to run through a gradle property, for example:

```
.\gradlew runExample -PmainClass=com.github.lpedrosa.StdHttpClientExample
```

You can replace the `-Pmainclass` property value with any of the class examples found in [this folder](./src/main/java/com/github/lpedrosa).

## `var` for optional typing

The full JEP can be found [here](https://openjdk.java.net/jeps/323)

I went through a bunch of examples that you might encounter in real-life code, as well as some gotchas:

* `var` will always infer to the concrete type, which a particular method/constructor returns, and not to the interface e.g. `ArrayList<T>` rather than `List<T>`

More examples can be found [VarExample.java](./src/main/java/com/github/lpedrosa/VarExample.java).

**Q: Should you use `var` all the time in code?**

I think it's fine.

It's likely that you are using an IDE of sort (or a Language Server powering you favourite text editor). This means you can always inspect the type if needed.

---

**Anecdotal evidence**

I have used var on a C# codebase for quite some time and I don't remember when I wished that I had explicitly declared a type.

## `Optional<T>`

This got introduced in Java 8 so it's not new, but it helps a lot in domain modelling.

It represents a value that may or may not be null. This effectively helps consumers of a method by flagging that they will have to handle the absence of that value.

The container class provides all sorts of helpers to deal with the absence of the value i.e. defaulting to an explicit value, combining it with other methods, etc.

More examples can be found in [OptionalExample.java](./src/main/java/com/github/lpedrosa/Optional.java)

## Immutable collections

Java doesn't have literals literals for initialising collections inline. This means you would have to instantiate an collection object and then populate it.

Java 9 added some static factory methods to initialise collections. Not only they initialise a collection but also make it immutable i.e. you can't add new items to it.

One usage example is getting rid of static initialisers of collections on classes, which would never change during the object lifetime:

```
public class MyClass {
    private static final Map<Integer, String> myMap;
    static {
        Map<Integer, String> aMap = ....;
        aMap.put(1, "one");
        aMap.put(2, "two");
        myMap = Collections.unmodifiableMap(aMap);
    }
}
```

can effectively be changed to:

```
public class MyClass {
    private static final Map<Integer, String> myMap = Map.of(1, "one", 2, "two");
}
```

More examples can be found in [ImmutableCollectionsExample.java](./src/main/java/com/github/lpedrosa/ImmutableCollectionsExample.java)

## Native Java 11 HTTP Client

This is a big one, for me at least. Quite often you'd have to pull in the [apache http client](https://hc.apache.org/), or even square's [okhttp](https://square.github.io/okhttp/). You can now lower the amount of dependencies of your project by using the standard library's http client.

It supports HTTP2, it's async by default (based on non-blocking IO) and it actually has a nice API.

You build `HttpRequest` instances that can be re-used, as such:

```
var request = HttpRequest.newBuilder(URI.create("https://httpstat.us/200"))
                         .GET()
                         .header("Accept", "text/plain; charset=utf-8")
                         .build();
```

Once you have a request, you can use it in a client instance. You also need to specify a response body handler once you make the request. It basically handles the whole body processing using reactive streams (meaning you can define back-pressure, how much data you want to read and other fancy stuff; this is all based on [java.util.concurrent.Flow](https://docs.oracle.com/en/java/javase/13/docs/api/java.base/java/util/concurrent/Flow.html) stuff).

```
// create a client with good defaults e.g. use HTTP 2 when you can
var client = HttpClient.newHttpClient();

// here we just want to read the body into a string
var bodyHandler = BodyHandlers.ofString();

// we can now make the request
var response = client.send(request, bodyHandler);

System.out.println("status code: " + response.statusCode());
System.out.println("body: " + response.body());
```

More examples can be found in [StdHttpClientExample.java](./src/main/java/com/github/lpedrosa/StdHttpClientExample.java), especially how to load trust certificates into it, etc.

## jshell

Even though I did not add an example, the Java REPL `jshell` was very useful while I was trying out some of these new APIs e.g. the new `java.net.http.HttpClient`.

Find more about it [here](https://docs.oracle.com/en/java/javase/13/jshell/introduction-jshell.html)

## Conclusion

Overall I am quite excited by the things added so far, since they help developers:

* build more expressive APIs (using `Optional<T>`)
* reduce the amount of dependencies needed in their projects (the new native HttpClient)
* reduce typing, by inferring the variable types

The next big thing will probably be [records](https://openjdk.java.net/jeps/359) (coming in java 14), which will help with domain modelling and hopefully removing the need of [Lombok](https://projectlombok.org/) on some projects. Also [pattern matching](https://cr.openjdk.java.net/~briangoetz/amber/pattern-match.html) and [sealed types](https://openjdk.java.net/jeps/360) could complement records quite nicely and further enhance the ability of modelling business logic.

If you want to know what this looks like, I recommend readying more about the ML family of languages (e.g. SML, Haskell, OCaml and also F#). I have been playing with F# for some time and I was really surprise about the balance between _getting things done_ and modelling your business logic so that it catches bugs sooner (bearing in mind that I have tried Scala and OCaml as well).

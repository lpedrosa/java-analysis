# Language quality of life features

This contains some examples showing some QoL improvements from Java 8 to 11. All of these files are runnable.

## `var` for optional typing

The full JEP can be found [here](https://openjdk.java.net/jeps/323)

I went through a bunch of examples that you might encounter in real-life code, 
as well as some gotchas:

* `var` will always infer to the concrete type and not to the interface e.g. `ArrayList<T>` rather than `List<T>`

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

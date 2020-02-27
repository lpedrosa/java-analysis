package com.github.lpedrosa;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class VarExample {

    public static void main(String[] args) {
        // will infer into an int
        var someValue = 2;

        // as can be seen here
        int result = add2(someValue);

        System.out.println(result);

        // will infer into an long
        var someLongValue = 2L;

        System.out.println(someLongValue);

        // works with user defined types
        var person = new Person("John", 25);

        System.out.println(person.getName());
        System.out.println(person.getAge());

        // Some gotchas:

        // var will *always* resolve to the explicit implementation
        // for types that implement interfaces i.e. ArrayList
        // explicitList will resolve to ArrayList<String>
        //
        // NOTE: that ArrayList<> will infer ArrayList<Object>
        var explicitList = new ArrayList<String>();
        explicitList.add("apple");
        explicitList.add("orange");

        // this is not an issue if your consumers only take the interface
        Function<List<String>, Integer> mySize = l -> l.size();

        // works as expected since ArrayList<String> implements List<String>
        var size = mySize.apply(explicitList);
        System.out.println(size);

        // bear in mind that implementation specific methods will be available,
        // as expected
        var fooImpl = new FooImpl();

        // works
        fooImpl.foo();
        // works since fooImpl resolved to FooImpl and not Fooer
        fooImpl.bar();

        // as mentioned above, this is not an issue since consumers should restrict
        // their input to the necessary bare minimum i.e. require interfaces, etc.
        //
        // you can always restrict their inline type if needed
        Fooer fooer = new FooImpl();

        // works
        fooer.foo();
        // doesn't work
        // fooer.bar();
    }

    // Helper types and methods

    private static int add2(int value) {
        return value + 2;
    }

    private static final class Person {
        private final String name;
        private final int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public int getAge() {
            return age;
        }

        public String getName() {
            return name;
        }
    }

    private interface Fooer {
        void foo();
    }

    private static final class FooImpl implements Fooer {
        @Override
        public void foo() {
            // Do nothing
        }

        public void bar() {
            // Do nothing
        }
    }

    private VarExample() {
    }
}

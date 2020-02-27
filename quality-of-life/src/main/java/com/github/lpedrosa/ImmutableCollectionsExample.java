package com.github.lpedrosa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ImmutableCollectionsExample {

    public static void main(String[] args) {
        // Java 9 added new static factory collection methods
        var immutableList = List.of("apple", "orange");
        Set.of(1, 2, 3);
        Map.of("key1", 1, "key2", 2);

        // if you try to add items to any of these collections you will
        // get a UnsupportedOperationException
        try {
            immutableList.add("banana");
        } catch (UnsupportedOperationException e) {
            System.out.println("Cannot add banana to the immutable list");
        }

        // even if the collection is immutable, the underlying value can still change
        var people = List.of(new MutablePerson("Alice"), new MutablePerson("Bob"));

        var alice = people.get(0);
        alice.setName("New Alice");

        System.out.println("Alice has a new name: " + people.get(0).getName());

        // another gotcha is adding elements of mixed types to a collection
        // if you use var, the compiler will infer it to the common super type

        // this list will be a List<Object>
        var mixedElementsList = List.of(1, "apple", new MutablePerson("John"));

        // this is effectively the same if you used the usual way of constructing Lists
        List<Object> anotherList = new ArrayList<>();
        anotherList.add(1);
        anotherList.add("apple");
        anotherList.add(new MutablePerson("John"));

        System.out.println("The lists have the same elements: " + mixedElementsList.equals(anotherList));
    }

    private static final class MutablePerson {
        private String name;

        public MutablePerson(String name) {
            Objects.requireNonNull(name);
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            Objects.requireNonNull(name);
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MutablePerson)) {
                return false;
            }

            var other = (MutablePerson) obj;

            return this.name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }
    }

    private ImmutableCollectionsExample() {
    }
}

package com.example.myapplication;

public class Folder {
    private final long id;
    private final String name;

    public Folder(long id, String name) {
        this.id = id;
        this.name = name != null ? name : "";
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}

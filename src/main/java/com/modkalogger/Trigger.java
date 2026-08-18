package com.modkalogger;

public class Trigger {
    static {
        try {
            Class.forName("com.modkalogger.ModKaLogger");
        } catch (ClassNotFoundException e) {}
    }
    
    public static void init() {}
}

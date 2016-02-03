package com.netflix.nicobar.test;

import com.netflix.nicobar.test.Service;

public class Dependent {
    public static String execute() {
        String result = new Service().service();
        System.out.println("Execution result: " + result);
        return result;
    }
}

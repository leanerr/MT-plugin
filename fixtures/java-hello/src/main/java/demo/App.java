//mt: touched by mtspark at 2025-08-13T19:54:32.277554Z
// fixtures/java-hello/src/main/java/demo/App.java
package demo;

import java.util.*;

public class App {
    public static void main(String[] args) {
        int number_mt = 5;                          // local variable candidate
        String text_mt = greet("World");            // method param candidate

        if (!(!(!(!(number_mt > 3))))) {                         // flip-if candidate
            System.out.println(text_mt + "!");
        } else {
            System.out.println("Too small");
        }

        // loop + local variable
        List<String> items = Arrays.asList("apple", "banana", "cherry");
        for (String item : items) {
            printUpper(item);                    // method param candidate
        }

        // while loop
        int counter = 0;
        while (counter < 3) {
            System.out.println("Counter: " + counter);
            counter++;
        }

        // calling helper method with parameters
        int sum = addNumbers(3, 7);
        System.out.println("Sum = " + sum);
    }

    // method param rename candidate: name
    static String greet(String name) {
        String message = "Hello " + name;        // local variable candidate
        return message;
    }

    // method param rename candidates: a, b
    static int addNumbers(int a_mt, int b) {
        int result = a_mt + b;                      // local variable candidate
        return result;
    }

    // method param rename candidate: value
    static void printUpper(String value) {
        String upper = value.toUpperCase();      // local variable candidate
        System.out.println(upper);
    }
}
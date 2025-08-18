// fixtures/java-hello/src/main/java/demo/Target.java
package demo;

public class Target {
    public static void main(String[] args) {
        int number = 5;                // local variable candidate
        String text = greet("World");  // method param candidate

        if (number > 3) {               // flip-if candidate
            System.out.println(text + "!");
        } else {
            System.out.println("Too small");
        }
    }

    // method param rename candidate: name
    static String greet(String name) {
        String message = "Hello " + name; // local variable candidate
        return message;
    }
}
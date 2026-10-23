package dev.kuratoon.image;

/** Stand-in for the vips CLI in {@link VipsRunnerTest}, run with the test JVM's java binary. */
public final class FakeVips {

    private FakeVips() {}

    public static void main(String[] args) throws InterruptedException {
        switch (args.length == 0 ? "" : args[0]) {
            case "sleep" -> Thread.sleep(60_000);
            case "fail" -> {
                System.err.println("VipsError: unable to load \"x.jpg\"");
                System.exit(3);
            }
            case "flood" -> {
                // More output than the runner keeps; it must not block on a full pipe.
                String line = "x".repeat(1023);
                for (int i = 0; i < 1024; i++) {
                    System.out.println(line);
                    System.err.println(line);
                }
            }
            default -> System.out.println(String.join(" ", args));
        }
    }
}

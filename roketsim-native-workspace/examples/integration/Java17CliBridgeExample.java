import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** RoketSim Native JSON CLI'ını Java 17 ProcessBuilder ile çağırma örneği. */
public final class Java17CliBridgeExample {
    private Java17CliBridgeExample() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        String python = args.length > 0 ? args[0] : "python";
        Path requestPath = args.length > 1
                ? Path.of(args[1])
                : Path.of("examples", "integration", "request-v1.json");
        String requestJson = Files.readString(requestPath, StandardCharsets.UTF_8);

        Path engineDirectory = Path.of("roketsim-native-engine").toAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder(
                python, "-m", "roketsim_native.integration.cli");
        builder.directory(engineDirectory.toFile());
        builder.environment().put(
                "PYTHONPATH", engineDirectory.resolve("src").toString());

        Process process = builder.start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(requestJson.getBytes(StandardCharsets.UTF_8));
        }
        String stdout = new String(
                process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(
                process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        System.out.println("exitCode=" + exitCode);
        System.out.println("stdout=" + stdout);
        if (!stderr.isBlank()) {
            System.err.println("stderr=" + stderr);
        }
    }
}

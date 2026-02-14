package com.example.compiler.service.plugin;

import com.example.compiler.model.CompilationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Helper class that encapsulates G++ specific compilation behavior.
 * Not a Spring bean; used internally by `CppCompilerPlugin`.
 */
public class GccCompilerPlugin implements CompilerPlugin {
    private static final Logger log = LoggerFactory.getLogger(GccCompilerPlugin.class);
    private static final String GXX = "g++";

    @Override
    public String getLanguage() {
        return "cpp";
    }

    @Override
    public String[] getSupportedCompilers() {
        return new String[] { GXX };
    }

    @Override
    public String[] getDefaultCompilerOptions() {
        return new String[] { "-O0", "-O1", "-O2", "-O3" };
    }

    @Override
    public CompilationResult compile(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(GXX, sourceFile.toString(), "-o", sourceFile.toString() + ".exe")
                .directory(sourceFile.getParent().toFile());

        if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
            for (String opt : request.getCompilerOptions().trim().split("\\s+")) {
                pb.command().add(opt);
            }
        }

        Process process = pb.start();
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("C++ compilation timed out");
        }

        if (process.exitValue() != 0) {
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }
            return CompilationResult.failure(errorOutput.toString());
        }

        log.debug("G++ compilation succeeded: {}", sourceFile);
        return CompilationResult.success();
    }

    @Override
    public String generateAssembly(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(GXX, "-S", "-o", "-", sourceFile.toString())
                .directory(sourceFile.getParent().toFile());

        if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
            for (String opt : request.getCompilerOptions().trim().split("\\s+")) {
                pb.command().add(pb.command().size() - 1, opt);
            }
        }

        Process process = pb.start();
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Assembly generation timed out");
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to generate assembly output");
        }

        return output.toString();
    }

    @Override
    public String execute(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        // Compile to executable
        Path exeFile = Path.of(sourceFile.toString().replace(".cpp", ".exe"));
        ProcessBuilder compilePb = new ProcessBuilder(GXX, sourceFile.toString(), "-o", exeFile.toString())
                .directory(sourceFile.getParent().toFile());

        if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
            for (String opt : request.getCompilerOptions().trim().split("\\s+")) {
                compilePb.command().add(opt);
            }
        }

        Process compileProcess = compilePb.start();
        boolean compileCompleted = compileProcess.waitFor(timeout, TimeUnit.SECONDS);
        if (!compileCompleted) {
            compileProcess.destroyForcibly();
            throw new RuntimeException("C++ executable compilation timed out");
        }

        if (compileProcess.exitValue() != 0) {
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }
            throw new RuntimeException("C++ compilation failed: " + errorOutput.toString());
        }

        ProcessBuilder pb = new ProcessBuilder(exeFile.toString()).redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("C++ execution timed out");
        }

        return output.toString();
    }

    @Override
    public Path createSourceFile(String code, Path workDirectory) throws Exception {
        String fileName = UUID.randomUUID().toString();
        Path filePath = workDirectory.resolve(fileName + ".cpp");
        Files.writeString(filePath, code);
        log.debug("Created C++ source file: {}", filePath);
        return filePath;
    }

    @Override
    public void cleanup(Path sourceFile) {
        try {
            if (sourceFile != null && Files.exists(sourceFile)) {
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(Path.of(sourceFile.toString() + ".exe"));
                Files.deleteIfExists(Path.of(sourceFile.toString() + ".obj"));
                Files.deleteIfExists(Path.of(sourceFile.toString() + ".asm"));
                log.debug("Cleaned up C++ files: {}", sourceFile);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up C++ files", e);
        }
    }
}

package com.example.compiler.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import com.example.compiler.model.CompilationRequest;
import com.example.compiler.model.CompilationResponse;

@Service
public class CompilerService {

    private static final int TIMEOUT_SECONDS = 10;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CompilerService.class);

    private final Path workDirectory;

    public CompilerService() throws IOException {
        // Create a unique working directory for this instance
        String appPrefix = "compiler-explorer-";
        String instanceId = UUID.randomUUID().toString();
        this.workDirectory = Files.createTempDirectory(appPrefix + instanceId + "-");

        // Set directory permissions to be private to the current user
        try {
            Set<PosixFilePermission> perms = Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(workDirectory, perms);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions, falling back to default
            log.debug("POSIX file permissions not supported on this system");
        }

        log.info("Created working directory: {}", workDirectory);
    }

    @PreDestroy
    public void cleanup() {
        try {
            // Recursively delete the working directory and all its contents
            Files.walk(workDirectory)
                    .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.info("Cleaned up working directory: {}", workDirectory);
        } catch (IOException e) {
            log.error("Error cleaning up working directory", e);
        }
    }

    public CompilationResponse compile(CompilationRequest request) {
        CompilationResponse response = new CompilationResponse();
        log.debug("Received compilation request for language: {}", request.getLanguage());

        Path sourceFile = null;
        try {
            String className = request.getLanguage().equals("java") ? extractClassName(request.getCode()) : null;
            if (request.getLanguage().equals("java") && className == null) {
                throw new RuntimeException("Could not find class name in Java code");
            }

            sourceFile = createSourceFile(request, className);
            compileCode(request, response, sourceFile);
            if (!response.isSuccess()) {
                return response;
            }

            getAssemblyOutput(request, response, sourceFile, className);

        } catch (Exception e) {
            log.error("Error during compilation", e);
            response.setSuccess(false);
            response.setError("Error during compilation: " + e.getMessage());
        } finally {
            // Clean up the source and output files
            cleanupFiles(sourceFile);
        }

        return response;
    }

    private void getAssemblyOutput(CompilationRequest request, CompilationResponse response, Path sourceFile,
            String className)
            throws IOException, InterruptedException {
        boolean completed;
        // Get assembly output
        ProcessBuilder asmBuilder = createAsmProcess(sourceFile.toString(), request.getLanguage(), className);
        Process asmProcess = asmBuilder.start();

        completed = asmProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            asmProcess.destroyForcibly();
            throw new RuntimeException("Assembly generation timed out");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(asmProcess.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        if (asmProcess.exitValue() != 0) {
            log.error("Assembly generation failed with exit code: {}", asmProcess.exitValue());
            throw new RuntimeException("Failed to generate assembly output");
        }

        response.setSuccess(true);
        response.setAssemblyOutput(output.toString());
    }

    private CompilationResponse compileCode(CompilationRequest request, CompilationResponse response, Path sourceFile)
            throws IOException, InterruptedException {
        // Compile the code
        ProcessBuilder compileBuilder = createCompileProcess(sourceFile.toString(), request);
        Process compileProcess = compileBuilder.start();

        boolean completed = compileProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            compileProcess.destroyForcibly();
            throw new RuntimeException("Compilation timed out");
        }

        if (compileProcess.exitValue() != 0) {
            // Compilation failed
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }

            response.setSuccess(false);
            response.setError(errorOutput.toString());
            return response;
        } else {
            // Compilation succeeded
            log.info("Compilation succeeded for source file: {}", sourceFile);
            response.setSuccess(true);
            return response;
        }
    }

    private Path createSourceFile(CompilationRequest request, String className) throws IOException {
        Path sourceFile;
        // Create source file in the instance's working directory
        sourceFile = createSourceFile(request.getCode(),
                request.getLanguage().equals("java") ? className : UUID.randomUUID().toString(),
                request.getLanguage());
        log.debug("Created source file: {}", sourceFile);
        return sourceFile;
    }

    private String extractClassName(String code) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Path createSourceFile(String code, String fileName, String language) throws IOException {
        String extension = language.equals("cpp") ? ".cpp" : ".java";
        Path filePath = workDirectory.resolve(fileName + extension);
        Files.writeString(filePath, code);
        return filePath;
    }

    private ProcessBuilder createCompileProcess(String sourceFile, CompilationRequest request) {
        ProcessBuilder pb;
        if (request.getLanguage().equals("cpp")) {
            pb = new ProcessBuilder("g++", sourceFile, "-o", sourceFile + ".exe");
            if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
                pb.command().add(request.getCompilerOptions());
            }
        } else {
            pb = new ProcessBuilder("javac", sourceFile);
        }
        pb.redirectErrorStream(true);
        return pb;
    }

    private ProcessBuilder createAsmProcess(String sourceFile, String language, String className) {
        if (language.equals("cpp")) {
            return new ProcessBuilder("g++", "-S", "-o", "-", sourceFile);
        } else {
            // For Java, we'll use javap to get the bytecode
            File classFile = new File(sourceFile).getParentFile();
            return new ProcessBuilder("javap", "-c", "-p", className)
                    .directory(classFile); // Set working directory to where the .class file is
        }
    }

    private void cleanupFiles(Path sourceFile) {
        if (sourceFile == null)
            return;

        try {
            // Delete source file
            Files.deleteIfExists(sourceFile);

            // Delete related files
            String sourcePath = sourceFile.toString();
            if (sourcePath.endsWith(".cpp")) {
                Files.deleteIfExists(Path.of(sourcePath + ".exe"));
            } else if (sourcePath.endsWith(".java")) {
                Files.deleteIfExists(Path.of(sourcePath.replace(".java", ".class")));
            }
        } catch (IOException e) {
            log.warn("Error cleaning up temporary files", e);
        }
    }
}

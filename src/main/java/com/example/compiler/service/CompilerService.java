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
import java.util.Map;
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

    private static final String CPP = "cpp";
    private static final String JAVA = "java";
    private static final String GXX = "g++";
    private static final String CL = "cl";

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
        Path asmFile = null;

        try {
            String className = request.getLanguage().equals(JAVA) ? extractClassName(request.getCode()) : null;
            if (request.getLanguage().equals(JAVA) && className == null) {
                throw new RuntimeException("Could not find class name in Java code");
            }

            // Create source file in the instance's working directory
            sourceFile = createSourceFile(request.getCode(),
                    request.getLanguage().equals(JAVA) ? className : UUID.randomUUID().toString(),
                    request.getLanguage());
            log.debug("Created source file: {}", sourceFile);

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
            }

            // Get assembly output
            ProcessBuilder asmBuilder = createAsmProcess(sourceFile.toString(), request.getLanguage(), className);
            Process asmProcess = asmBuilder.start();

            completed = asmProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                asmProcess.destroyForcibly();
                throw new RuntimeException("Assembly generation timed out");
            }

            StringBuilder output = new StringBuilder();

            if (request.getLanguage().equals(CPP) && request.getCompiler() != null
                    && request.getCompiler().equals(CL)) {
                // For MSVC, read the generated .asm file
                asmFile = Path.of(sourceFile.toString() + ".asm");
                if (Files.exists(asmFile)) {
                    log.debug("Reading MSVC assembly output from: {}", asmFile);
                    output.append(Files.readString(asmFile));
                } else {
                    throw new RuntimeException("Assembly file not generated by MSVC");
                }
            } else {
                // For G++ and Java, read from process output
                BufferedReader reader = new BufferedReader(new InputStreamReader(asmProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (asmProcess.exitValue() != 0) {
                log.error("Assembly generation failed with exit code: {}", asmProcess.exitValue());
                throw new RuntimeException("Failed to generate assembly output");
            }

            response.setSuccess(true);
            response.setAssemblyOutput(output.toString());

        } catch (Exception e) {
            log.error("Error during compilation", e);
            response.setSuccess(false);
            response.setError("Error during compilation: " + e.getMessage());
        } finally {
            // Clean up all generated files
            try {
                if (sourceFile != null) {
                    Files.deleteIfExists(sourceFile);
                    if (sourceFile.toString().endsWith(".cpp")) {
                        Files.deleteIfExists(Path.of(sourceFile.toString() + ".exe"));
                        if (asmFile != null) {
                            Files.deleteIfExists(asmFile);
                        }
                        // Clean up MSVC additional files
                        Files.deleteIfExists(Path.of(sourceFile.toString() + ".obj"));
                    } else if (sourceFile.toString().endsWith(".java")) {
                        Files.deleteIfExists(Path.of(sourceFile.toString().replace(".java", ".class")));
                    }
                }
            } catch (IOException e) {
                log.warn("Error cleaning up files", e);
            }
        }

        return response;
    }

    private void cleanupFiles(Path sourceFile, Path asmFile) {
        if (sourceFile != null) {
            try {
                Files.deleteIfExists(sourceFile);
                if (sourceFile.toString().endsWith(".cpp")) {
                    Files.deleteIfExists(Path.of(sourceFile.toString() + ".exe"));
                    if (asmFile != null) {
                        Files.deleteIfExists(asmFile);
                    }
                } else if (sourceFile.toString().endsWith(".java")) {
                    Files.deleteIfExists(Path.of(sourceFile.toString().replace(".java", ".class")));
                }
            } catch (IOException e) {
                log.warn("Error cleaning up files", e);
            }
        }
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
                request.getLanguage().equals(JAVA) ? className : UUID.randomUUID().toString(),
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
        String extension = language.equals(CPP) ? ".cpp" : ".java";
        Path filePath = workDirectory.resolve(fileName + extension);
        Files.writeString(filePath, code);
        return filePath;
    }

    private ProcessBuilder createCompileProcess(String sourceFile, CompilationRequest request) {
        ProcessBuilder pb;
        if (request.getLanguage().equals(CPP)) {
            pb = createCppCompileProcess(sourceFile, request);
        } else {
            pb = new ProcessBuilder("javac", sourceFile);
        }
        pb.redirectErrorStream(true);
        return pb;
    }

    private ProcessBuilder createCppCompileProcess(String sourceFile, CompilationRequest request) {
        String compiler = request.getCompiler() != null ? request.getCompiler() : GXX;
        ProcessBuilder pb;

        switch (compiler) {
            case GXX:
                pb = new ProcessBuilder(GXX, sourceFile, "-o", sourceFile + ".exe");
                if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
                    pb.command().add(request.getCompilerOptions());
                }
                break;

            case CL:
                // MSVC compiler (cl.exe) has different command-line syntax
                pb = new ProcessBuilder(
                        "cmd", "/c",
                        CL, "/nologo", "/EHsc", sourceFile,
                        "/Fe:" + sourceFile + ".exe");
                if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
                    // Add MSVC-specific options
                    pb.command().add(request.getCompilerOptions());
                }
                // Set up Visual Studio environment if needed
                setupMSVCEnvironment(pb);
                break;

            default:
                throw new IllegalArgumentException("Unsupported C++ compiler: " + compiler);
        }

        return pb;
    }

    private ProcessBuilder createAsmProcess(String sourceFile, String language, String className) {
        if (language.equals(CPP)) {
            return createCppAsmProcess(sourceFile);
        } else {
            // For Java, we'll use javap to get the bytecode
            File classFile = new File(sourceFile).getParentFile();
            return new ProcessBuilder("javap", "-c", "-p", className)
                    .directory(classFile); // Set working directory to where the .class file is
        }
    }

    private ProcessBuilder createCppAsmProcess(String sourceFile) {
        // First, check which compiler is available
        String compiler = determineCppCompiler();
        log.debug("Using C++ compiler: {}", compiler);

        switch (compiler) {
            case GXX:
                return new ProcessBuilder(GXX, "-S", "-o", "-", sourceFile);

            case CL:
                // For MSVC, we need to generate assembly listing
                ProcessBuilder pb = new ProcessBuilder(
                        "cmd", "/c",
                        CL, "/nologo", "/c", "/FAs", // /FAs creates assembly listing with source code
                        "/Fa" + sourceFile + ".asm", // Specify assembly output file
                        sourceFile);
                setupMSVCEnvironment(pb);
                return pb;

            default:
                throw new IllegalStateException("No supported C++ compiler found");
        }
    }

    private String determineCppCompiler() {
        // Try MSVC first if we're on Windows
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // Common paths for cl.exe
            String[] possiblePaths = {
                    "D:\\software\\VisualStudio\\VC\\Tools\\MSVC\\14.44.35207\\bin\\Hostx64\\x64\\cl.exe",
                    "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.38.33130\\bin\\Hostx64\\x64\\cl.exe",
                    "C:\\Program Files\\Microsoft Visual Studio\\2022\\Professional\\VC\\Tools\\MSVC\\14.38.33130\\bin\\Hostx64\\x64\\cl.exe",
                    "C:\\Program Files\\Microsoft Visual Studio\\2022\\Enterprise\\VC\\Tools\\MSVC\\14.38.33130\\bin\\Hostx64\\x64\\cl.exe"
            };

            // Try the exact path we found in your system
            for (String path : possiblePaths) {
                if (new File(path).exists()) {
                    log.info("Found MSVC at: {}", path);
                    return CL;
                }
            }

            // If not found in common paths, try using vswhere to locate it
            try {
                Process vsWhere = new ProcessBuilder(
                        "C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe",
                        "-latest",
                        "-products", "*",
                        "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                        "-property", "installationPath").start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(vsWhere.getInputStream()));
                String vsPath = reader.readLine();

                if (vsPath != null && !vsPath.isEmpty()) {
                    log.info("Found Visual Studio installation at: {}", vsPath);
                    return CL;
                }
            } catch (Exception e) {
                log.debug("VSWhere detection failed", e);
            }

            // Last resort: try cl.exe directly (it might be in PATH)
            try {
                ProcessBuilder pb = new ProcessBuilder("cl");
                pb.environment().put("PATH", System.getenv("PATH"));
                Process clProcess = pb.start();
                if (clProcess.waitFor(5, TimeUnit.SECONDS)) {
                    log.info("Found cl.exe in PATH");
                    return CL;
                }
            } catch (Exception e) {
                log.debug("Failed to find cl.exe in PATH", e);
            }
        }

        // Try G++
        try {
            Process gppProcess = new ProcessBuilder("g++", "--version").start();
            if (gppProcess.waitFor(5, TimeUnit.SECONDS) && gppProcess.exitValue() == 0) {
                log.info("Found G++");
                return GXX;
            }
        } catch (Exception e) {
            log.debug("G++ not found or not working", e);
        }

        // Log the current PATH for debugging
        log.error("No C++ compiler found. Current PATH: {}", System.getenv("PATH"));
        throw new IllegalStateException(
                "No C++ compiler found. Please install either G++ or MSVC. MSVC should be available at: " +
                        "D:\\software\\VisualStudio\\VC\\Tools\\MSVC\\14.44.35207\\bin\\Hostx64\\x64\\cl.exe");
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

    private void setupMSVCEnvironment(ProcessBuilder pb) {
        try {
            // First try the known path from your system
            String knownPath = "D:\\software\\VisualStudio\\VC\\Tools\\MSVC\\14.44.35207";
            if (new File(knownPath).exists()) {
                String vcvarsPath = knownPath + "\\..\\..\\..\\..\\Auxiliary\\Build\\vcvars64.bat";
                if (new File(vcvarsPath).exists()) {
                    setupVCVarsEnvironment(pb, vcvarsPath);
                    return;
                }
            }

            // Fallback to using vswhere
            Process vsWhere = new ProcessBuilder(
                    "C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe",
                    "-latest",
                    "-products", "*",
                    "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                    "-property", "installationPath").start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(vsWhere.getInputStream()));
            String vsPath = reader.readLine();

            if (vsPath != null && !vsPath.isEmpty()) {
                String vcvarsPath = vsPath + "\\VC\\Auxiliary\\Build\\vcvars64.bat";
                setupVCVarsEnvironment(pb, vcvarsPath);
            } else {
                log.warn("Could not find Visual Studio installation");
                // Try to use environment as-is
                pb.environment().put("PATH", System.getenv("PATH"));
            }
        } catch (Exception e) {
            log.error("Error setting up MSVC environment", e);
            throw new RuntimeException("Failed to set up MSVC environment: " + e.getMessage(), e);
        }
    }

    private void setupVCVarsEnvironment(ProcessBuilder pb, String vcvarsPath) throws Exception {
        log.debug("Setting up MSVC environment using vcvars64.bat at: {}", vcvarsPath);

        // Create a temporary batch file to capture environment variables
        Path tempBat = Files.createTempFile("vcvars", ".bat");
        Files.writeString(tempBat,
                "@echo off\n" +
                        "call \"" + vcvarsPath + "\"\n" +
                        "set\n");

        // Run the batch file and capture environment variables
        Process envProcess = new ProcessBuilder("cmd", "/c", tempBat.toString())
                .redirectErrorStream(true)
                .start();

        Map<String, String> env = pb.environment();
        BufferedReader reader = new BufferedReader(new InputStreamReader(envProcess.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                env.put(parts[0], parts[1]);
            }
        }

        boolean completed = envProcess.waitFor(10, TimeUnit.SECONDS);
        if (!completed || envProcess.exitValue() != 0) {
            log.error("Failed to set up MSVC environment using vcvars64.bat");
            throw new RuntimeException("Failed to set up MSVC environment");
        }

        // Cleanup
        Files.delete(tempBat);
        log.debug("Successfully set up MSVC environment");
    }
}

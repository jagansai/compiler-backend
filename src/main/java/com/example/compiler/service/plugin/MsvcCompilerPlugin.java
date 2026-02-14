package com.example.compiler.service.plugin;

import com.example.compiler.config.CompilerConfig;
import com.example.compiler.service.CompilerConfigService;
import com.example.compiler.model.CompilationRequest;
import com.example.compiler.model.CompilerInfo;
import com.example.compiler.model.LanguageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper class that encapsulates MSVC-specific behavior. Not a Spring bean;
 * used internally by `CppCompilerPlugin`.
 */
public class MsvcCompilerPlugin implements CompilerPlugin {
    private static final Logger log = LoggerFactory.getLogger(MsvcCompilerPlugin.class);
    private static final String CL = "cl";

    private final CompilerConfigService configService;
    private final Environment environment;

    public MsvcCompilerPlugin(CompilerConfig config, CompilerConfigService configService, Environment environment) {
        this.configService = configService;
        this.environment = environment;
    }

    @Override
    public String getLanguage() {
        return "cpp";
    }

    @Override
    public String[] getSupportedCompilers() {
        return new String[] { CL };
    }

    @Override
    public String[] getDefaultCompilerOptions() {
        return new String[] { "/EHsc" };
    }

    @Override
    public CompilationResult compile(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        String sourceFileStr = sourceFile.toString();
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", CL, "/nologo", "/EHsc", sourceFileStr, "/Fe:" + sourceFileStr + ".exe")
                .directory(sourceFile.getParent().toFile());

        if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
            for (String opt : request.getCompilerOptions().trim().split("\\s+")) {
                pb.command().add(opt);
            }
        }

        setupMSVCEnvironment(pb, request);

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

        log.debug("MSVC compilation succeeded: {}", sourceFile);
        return CompilationResult.success();
    }

    @Override
    public String generateAssembly(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        String sourceFileStr = sourceFile.toString();
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", CL, "/nologo", "/c", "/FAs", "/Fa" + sourceFileStr + ".asm", sourceFileStr)
                .directory(sourceFile.getParent().toFile());

        if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
            for (String opt : request.getCompilerOptions().trim().split("\\s+")) {
                pb.command().add(pb.command().size() - 1, opt);
            }
        }

        setupMSVCEnvironment(pb, request);

        Process process = pb.start();
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Assembly generation timed out");
        }

        StringBuilder output = new StringBuilder();
        Path asmFile = Path.of(sourceFileStr + ".asm");
        if (Files.exists(asmFile)) {
            output.append(Files.readString(asmFile));
            Files.deleteIfExists(asmFile);
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to generate assembly output");
        }

        return sanitizeMSVCAssembly(output.toString());
    }

    @Override
    public String execute(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        // Compile to executable using MSVC-specific invocation
        Path exeFile = Path.of(sourceFile.toString().replace(".cpp", ".exe"));
        ProcessBuilder compilePb = new ProcessBuilder("cmd", "/c", CL, "/nologo", "/EHsc", sourceFile.toString(), "/Fe" + exeFile)
                .directory(sourceFile.getParent().toFile());

        if (request.getCompilerOptions() != null && !request.getCompilerOptions().isEmpty()) {
            for (String opt : request.getCompilerOptions().trim().split("\\s+")) {
                compilePb.command().add(opt);
            }
        }

        setupMSVCEnvironment(compilePb, request);

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
        // Delegate to a simple implementation similar to GccPlugin
        String fileName = java.util.UUID.randomUUID().toString();
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
                log.debug("Cleaned up MSVC files: {}", sourceFile);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up MSVC files", e);
        }
    }

    // ---- MSVC specific helpers moved from old CppCompilerPlugin ----
    public boolean isMsvcAvailable() {
        // Check configured paths from application properties
        for (int i = 0; i < 8; i++) {
            String key = "compiler.cpp.msvc-paths[" + i + "]";
            String path = environment.getProperty(key);
            if (path != null && !path.isEmpty() && new File(path).exists()) {
                log.info("Found MSVC at configured path: {}", path);
                return true;
            }
        }

        String vswherePath = environment.getProperty("compiler.cpp.vswhere-path");
        if (vswherePath != null && !vswherePath.isEmpty() && new File(vswherePath).exists()) {
            try {
                Process vsWhere = new ProcessBuilder(vswherePath, "-latest", "-products", "*",
                        "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                        "-property", "installationPath").start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(vsWhere.getInputStream()));
                String vsPath = reader.readLine();

                if (vsPath != null && !vsPath.isEmpty()) {
                    log.info("Found Visual Studio installation at: {}", vsPath);
                    return true;
                }
            } catch (Exception e) {
                log.debug("VSWhere detection failed", e);
            }
        }

        try {
            Process clProcess = new ProcessBuilder("cl").start();
            if (clProcess.waitFor(5, TimeUnit.SECONDS)) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Failed to find cl.exe in PATH", e);
        }

        return false;
    }

    private void setupMSVCEnvironment(ProcessBuilder pb, CompilationRequest request) {
        try {
            try {
                LanguageConfig lang = configService != null ? configService.getLanguageConfig("cpp") : null;
                if (lang != null && request != null) {
                    CompilerInfo info = lang.getCompilerById(request.getCompilerId());
                    if (info != null && info.getMetadata() != null) {
                        String vcvarsMeta = info.getMetadata().get("vcvarsPath");
                        if (vcvarsMeta != null && !vcvarsMeta.isEmpty() && new File(vcvarsMeta).exists()) {
                            setupVCVarsEnvironment(pb, vcvarsMeta);
                            return;
                        }
                        String msvcMeta = info.getMetadata().get("msvcPaths");
                        if (msvcMeta != null && !msvcMeta.isEmpty()) {
                            for (String msvcPath : msvcMeta.split(",")) {
                                if (msvcPath != null && !msvcPath.isEmpty() && new File(msvcPath).exists()) {
                                    File clFile = new File(msvcPath.trim());
                                    File current = clFile.getParentFile();
                                    for (int i = 0; i < 8 && current != null; i++) {
                                        File candidate = new File(current, "Auxiliary\\Build\\vcvars64.bat");
                                        if (candidate.exists()) {
                                            setupVCVarsEnvironment(pb, candidate.getAbsolutePath());
                                            return;
                                        }
                                        current = current.getParentFile();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to read compiler metadata for MSVC", e);
            }

            String vcvarsPath = environment.getProperty("compiler.cpp.vcvars-path");
            if (vcvarsPath != null && !vcvarsPath.isEmpty() && new File(vcvarsPath).exists()) {
                setupVCVarsEnvironment(pb, vcvarsPath);
                return;
            }

            String envVcvars = System.getenv("VCVARS_PATH");
            if (envVcvars == null)
                envVcvars = System.getenv("VCVARS64_PATH");
            if (envVcvars != null && !envVcvars.isEmpty() && new File(envVcvars).exists()) {
                setupVCVarsEnvironment(pb, envVcvars);
                return;
            }

            try {
                Process where = new ProcessBuilder("where", "cl").start();
                BufferedReader r = new BufferedReader(new InputStreamReader(where.getInputStream()));
                String found = r.readLine();
                if (found != null && !found.isEmpty()) {
                    File clFile = new File(found);
                    File current = clFile.getParentFile();
                    for (int i = 0; i < 8 && current != null; i++) {
                        File candidate = new File(current, "Auxiliary\\Build\\vcvars64.bat");
                        if (candidate.exists()) {
                            setupVCVarsEnvironment(pb, candidate.getAbsolutePath());
                            return;
                        }
                        current = current.getParentFile();
                    }
                }
            } catch (Exception ignored) {
                log.debug("'where cl' check failed", ignored);
            }

            try {
                Process vsWhere = new ProcessBuilder("vswhere", "-latest", "-products", "*",
                        "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64", "-property",
                        "installationPath").start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(vsWhere.getInputStream()));
                String vsPath = reader.readLine();
                if (vsPath != null && !vsPath.isEmpty()) {
                    String vcvars = vsPath + "\\VC\\Auxiliary\\Build\\vcvars64.bat";
                    if (new File(vcvars).exists()) {
                        setupVCVarsEnvironment(pb, vcvars);
                        return;
                    }
                }
            } catch (Exception ignored) {
                log.debug("vswhere check failed", ignored);
            }

            log.warn("Could not find Visual Studio installation; leaving PATH as-is");
            pb.environment().put("PATH", System.getenv("PATH"));
        } catch (Exception e) {
            log.error("Error setting up MSVC environment", e);
            throw new RuntimeException("Failed to set up MSVC environment: " + e.getMessage(), e);
        }
    }

    private void setupVCVarsEnvironment(ProcessBuilder pb, String vcvarsPath) throws Exception {
        log.debug("Setting up MSVC environment using vcvars64.bat at: {}", vcvarsPath);

        Path tempBat = Files.createTempFile("vcvars", ".bat");
        Files.writeString(tempBat,
                "@echo off\n" +
                        "call \"" + vcvarsPath + "\"\n" +
                        "set\n");

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

        Files.delete(tempBat);
        log.debug("Successfully set up MSVC environment");
    }

    private String sanitizeMSVCAssembly(String assembly) {
        if (assembly == null || assembly.isEmpty()) {
            return assembly;
        }

        StringBuilder sanitized = new StringBuilder();
        String[] lines = assembly.split("\n");
        boolean inCodeSection = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("include ") || trimmed.startsWith("INCLUDE ")) {
                continue;
            }

            if (trimmed.startsWith("INCLUDELIB ")) {
                continue;
            }

            if (trimmed.startsWith("; Listing generated by")) {
                continue;
            }

            if (trimmed.startsWith("; File ")) {
                String filename = trimmed.substring(6).trim();
                // Remove any leading path components (both backslash and forward slash)
                filename = filename.replaceAll("^.*[\\\\/]", "");
                sanitized.append("; File ").append(filename).append("\n");
                continue;
            }

            if (!inCodeSection && trimmed.isEmpty()) {
                continue;
            }

            if (!trimmed.isEmpty()) {
                inCodeSection = true;
            }

            sanitized.append(line).append("\n");
        }

        return sanitized.toString();
    }
}

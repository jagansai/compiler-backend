package com.example.compiler.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.compiler.config.CompilerConfig;
import com.example.compiler.model.CompilationRequest;
import com.example.compiler.model.CompilationResponse;
import com.example.compiler.model.CompilerInfo;
import com.example.compiler.model.LanguageConfig;
import com.example.compiler.service.plugin.CompilationResult;
import com.example.compiler.service.plugin.CompilerPlugin;
import com.example.compiler.service.plugin.CompilerPluginRegistry;

/**
 * Orchestrator service for compilation requests.
 * Delegates language-specific compilation to appropriate plugins.
 */
@Service
public class CompilerService {

    private static final Logger log = LoggerFactory.getLogger(CompilerService.class);

    private final Path workDirectory;
    private final CompilerPluginRegistry pluginRegistry;
    private final CompilerConfig compilerConfig;
    private final CompilerConfigService configService;

    public CompilerService(CompilerPluginRegistry pluginRegistry, CompilerConfig compilerConfig,
            CompilerConfigService configService) throws IOException {
        this.pluginRegistry = pluginRegistry;
        this.compilerConfig = compilerConfig;
        this.configService = configService;

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

    Path getWorkDirectory() {
        return workDirectory;
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

    /**
     * Compile code and generate assembly output using appropriate language plugin.
     */
    public CompilationResponse compile(CompilationRequest request) {
        CompilationResponse response = new CompilationResponse();
        log.debug("Received compilation request for language: {} with compiler: {}",
                request.getLanguage(), request.getCompilerId());

        Path sourceFile = null;
        Path requestDir = null;
        CompilerPlugin plugin = null;

        try {
            // Validate compiler configuration
            LanguageConfig langConfig = configService.getLanguageConfig(request.getLanguage());
            if (langConfig == null) {
                throw new IllegalArgumentException("Unsupported language: " + request.getLanguage());
            }

            CompilerInfo compilerInfo = langConfig.getCompilerById(request.getCompilerId());
            if (compilerInfo == null) {
                throw new IllegalArgumentException("Invalid compiler ID: " + request.getCompilerId() +
                        " for language: " + request.getLanguage());
            }

            log.debug("Using compiler: {} ({})", compilerInfo.getName(), compilerInfo.getPath());

            // Set compiler path in request for plugins to use
            request.setCompilerPath(compilerInfo.getPath());

            // Get the appropriate plugin for the language
            plugin = pluginRegistry.getPlugin(request.getLanguage());

            // Create a per-request subdirectory to isolate filenames
            requestDir = Files.createTempDirectory(workDirectory, "req-" + UUID.randomUUID() + "-");
            sourceFile = plugin.createSourceFile(request.getCode(), requestDir);
            log.debug("Created source file: {}", sourceFile);

            // Compile the code
            CompilationResult compileResult = plugin.compile(request, sourceFile, compilerConfig.getTimeoutSeconds());

            if (!compileResult.isSuccess()) {
                log.error("Compilation failed: {}", compileResult.getErrorOutput());
                return new CompilationResponse(null, null, compileResult.getErrorOutput(), false);
            }

            // Generate assembly output
            String assemblyOutput = plugin.generateAssembly(request, sourceFile, compilerConfig.getTimeoutSeconds());
            response = new CompilationResponse(assemblyOutput, null, null, true);

        } catch (Exception e) {
            log.error("Error during compilation", e);
            response = new CompilationResponse(null, null, "Error during compilation: " + e.getMessage(), false);
        } finally {
            // Clean up generated files using plugin
            if (plugin != null && sourceFile != null) {
                plugin.cleanup(sourceFile);
            }
            // Delete per-request directory if it was created
            if (requestDir != null && Files.exists(requestDir)) {
                try {
                    Files.walk(requestDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    log.warn("Failed to delete request working directory {}", requestDir, e);
                }
            }
        }

        return response;
    }

    /**
     * Compile and execute code, returning the program output.
     */
    public CompilationResponse execute(CompilationRequest request) {
        CompilationResponse response = new CompilationResponse();
        log.debug("Received execution request for language: {} with compiler: {}",
                request.getLanguage(), request.getCompilerId());

        Path sourceFile = null;
        Path requestDir = null;
        CompilerPlugin plugin = null;

        try {
            // Validate compiler configuration
            LanguageConfig langConfig = configService.getLanguageConfig(request.getLanguage());
            if (langConfig == null) {
                throw new IllegalArgumentException("Unsupported language: " + request.getLanguage());
            }

            CompilerInfo compilerInfo = langConfig.getCompilerById(request.getCompilerId());
            if (compilerInfo == null) {
                throw new IllegalArgumentException("Invalid compiler ID: " + request.getCompilerId() +
                        " for language: " + request.getLanguage());
            }

            log.debug("Using compiler for execution: {} ({})", compilerInfo.getName(), compilerInfo.getPath());

            // Set compiler path in request for plugins to use
            request.setCompilerPath(compilerInfo.getPath());

            // Get the appropriate plugin for the language
            plugin = pluginRegistry.getPlugin(request.getLanguage());

            // Create a per-request subdirectory to isolate filenames
            requestDir = Files.createTempDirectory(workDirectory, "req-" + UUID.randomUUID() + "-");
            sourceFile = plugin.createSourceFile(request.getCode(), requestDir);
            log.debug("Created source file for execution: {}", sourceFile);

            // Compile the code
            CompilationResult compileResult = plugin.compile(request, sourceFile, compilerConfig.getTimeoutSeconds());

            if (!compileResult.isSuccess()) {
                log.error("Compilation failed during execution: {}", compileResult.getErrorOutput());
                return new CompilationResponse(null, null, compileResult.getErrorOutput(), false);
            }

            // Execute the code
            String executionOutput = plugin.execute(request, sourceFile, compilerConfig.getTimeoutSeconds());
            response = new CompilationResponse(null, executionOutput, null, true);

        } catch (Exception e) {
            log.error("Error during execution", e);
            response = new CompilationResponse(null, null, "Error during execution: " + e.getMessage(), false);
        } finally {
            // Clean up generated files using plugin
            if (plugin != null && sourceFile != null) {
                plugin.cleanup(sourceFile);
            }
            // Delete per-request directory if it was created
            if (requestDir != null && Files.exists(requestDir)) {
                try {
                    Files.walk(requestDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    log.warn("Failed to delete request working directory {}", requestDir, e);
                }
            }
        }

        return response;
    }
}

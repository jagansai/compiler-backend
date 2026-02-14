package com.example.compiler.service.plugin;

import com.example.compiler.model.CompilationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Plugin for Python compilation and bytecode disassembly.
 */
@Component
public class PythonCompilerPlugin implements CompilerPlugin {
    
    private static final Logger log = LoggerFactory.getLogger(PythonCompilerPlugin.class);
    private static final String LANGUAGE = "python";
    
    @Override
    public String getLanguage() {
        return LANGUAGE;
    }
    
    @Override
    public String[] getSupportedCompilers() {
        return new String[] { "python" };
    }
    
    @Override
    public String[] getDefaultCompilerOptions() {
        return new String[] {};
    }
    
    @Override
    public CompilationResult compile(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        // For Python, compilation means syntax checking with py_compile
        String pythonPath = request.getCompilerPath() != null ? request.getCompilerPath() : "python";
        ProcessBuilder pb = new ProcessBuilder(pythonPath, "-m", "py_compile", sourceFile.toString())
                .redirectErrorStream(true);
        Process process = pb.start();
        
        // Read output while process is running to prevent buffer blocking
        StringBuilder output = new StringBuilder();
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                log.warn("Error reading python compile output", e);
            }
        });
        outputReader.start();
        
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        outputReader.join(1000);  // Give reader thread a second to finish
        
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Python compilation timed out");
        }
        
        if (process.exitValue() != 0) {
            return CompilationResult.failure(output.toString());
        }
        
        log.debug("Python compilation succeeded for: {}", sourceFile);
        return CompilationResult.success();
    }
    
    @Override
    public String generateAssembly(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        // Use Python's dis module to disassemble bytecode
        String pythonPath = request.getCompilerPath() != null ? request.getCompilerPath() : "python";
        String disassembleScript = String.format(
            "import dis\n" +
            "import sys\n" +
            "with open(r'%s', 'r', encoding='utf-8') as f:\n" +
            "    code = compile(f.read(), r'%s', 'exec')\n" +
            "    dis.dis(code)",
            sourceFile.toString().replace("\\", "\\\\"),
            sourceFile.getFileName().toString()
        );
        
        ProcessBuilder pb = new ProcessBuilder(pythonPath, "-c", disassembleScript)
                .redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output while process is running to prevent buffer blocking
        StringBuilder output = new StringBuilder();
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                log.warn("Error reading python disassembly output", e);
            }
        });
        outputReader.start();
        
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        outputReader.join(1000);  // Give reader thread a second to finish
        
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Python bytecode disassembly timed out");
        }
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to disassemble Python bytecode: " + output.toString());
        }
        
        return output.toString();
    }
    
    @Override
    public String execute(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        // Execute the Python script
        String pythonPath = request.getCompilerPath() != null ? request.getCompilerPath() : "python";
        
        ProcessBuilder pb = new ProcessBuilder(pythonPath, sourceFile.toString())
                .redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output while process is running to prevent buffer blocking
        StringBuilder output = new StringBuilder();
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                log.warn("Error reading python execution output", e);
            }
        });
        outputReader.start();
        
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        outputReader.join(1000);  // Give reader thread a second to finish
        
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Python execution timed out");
        }
        
        if (process.exitValue() != 0) {
            log.warn("Python execution returned non-zero exit code: {}", process.exitValue());
        }
        
        return output.toString();
    }
    
    @Override
    public Path createSourceFile(String code, Path workDirectory) throws Exception {
        // Python files are typically named with .py extension
        // Use a simple default name for the script
        Path filePath = workDirectory.resolve("script.py");
        Files.writeString(filePath, code);
        log.debug("Created Python source file: {}", filePath);
        return filePath;
    }
    
    @Override
    public void cleanup(Path sourceFile) {
        try {
            if (sourceFile != null && Files.exists(sourceFile)) {
                // Delete .pyc files in __pycache__ directory
                Path parentDir = sourceFile.getParent();
                Path pycacheDir = parentDir.resolve("__pycache__");
                
                if (Files.exists(pycacheDir)) {
                    Files.walk(pycacheDir)
                            .sorted((a, b) -> b.compareTo(a))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (Exception e) {
                                    log.warn("Error deleting cached file: {}", path, e);
                                }
                            });
                }
                
                // Delete source file
                Files.deleteIfExists(sourceFile);
                log.debug("Cleaned up Python files: {}", sourceFile);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up Python files", e);
        }
    }
}

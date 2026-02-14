package com.example.compiler.service.plugin;

import com.example.compiler.model.CompilationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugin for Java compilation and bytecode disassembly.
 */
@Component
public class JavaCompilerPlugin implements CompilerPlugin {
    
    private static final Logger log = LoggerFactory.getLogger(JavaCompilerPlugin.class);
    private static final String LANGUAGE = "java";
    
    @Override
    public String getLanguage() {
        return LANGUAGE;
    }
    
    @Override
    public String[] getSupportedCompilers() {
        return new String[] { "javac" };
    }
    
    @Override
    public String[] getDefaultCompilerOptions() {
        return new String[] { "-g", "-verbose", "-nowarn" };
    }
    
    @Override
    public CompilationResult compile(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        String compilerPath = request.getCompilerPath() != null ? request.getCompilerPath() : "javac";
        ProcessBuilder pb = new ProcessBuilder(compilerPath, sourceFile.toString())
                .redirectErrorStream(true);  // Merge error stream into output
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
                log.warn("Error reading javac output", e);
            }
        });
        outputReader.start();
        
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        outputReader.join(1000);  // Give reader thread a second to finish
        
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Java compilation timed out");
        }
        
        if (process.exitValue() != 0) {
            return CompilationResult.failure(output.toString());
        }
        
        log.debug("Java compilation succeeded for: {}", sourceFile);
        return CompilationResult.success();
    }
    
    @Override
    public String generateAssembly(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        // Find all .class files generated in the same directory
        File classFileDir = sourceFile.getParent().toFile();
        File[] classFiles = classFileDir.listFiles((dir, name) -> name.endsWith(".class"));
        
        if (classFiles == null || classFiles.length == 0) {
            throw new RuntimeException("No .class files found after compilation");
        }
        
        StringBuilder output = new StringBuilder();
        
        // Disassemble each class file
        for (File classFile : classFiles) {
            String className = classFile.getName().replace(".class", "");
            
            ProcessBuilder pb = new ProcessBuilder("javap", "-c", "-p", className)
                    .directory(classFileDir)
                    .redirectErrorStream(true);  // Merge error stream into output
            
            Process process = pb.start();
            
            // Read output while process is running to prevent buffer blocking
            StringBuilder classOutput = new StringBuilder();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        classOutput.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("Error reading javap output", e);
                }
            });
            outputReader.start();
            
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            outputReader.join(1000);  // Give reader thread a second to finish
            
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("Java bytecode disassembly timed out for " + className);
            }
            
            output.append(classOutput);
            
            if (process.exitValue() != 0) {
                log.warn("Failed to disassemble class: {}", className);
            }
            
            // Add separator between classes for readability
            output.append("\n");
        }
        
        return output.toString();
    }
    
    @Override
    public String execute(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        // Find the main class file
        File classFileDir = sourceFile.getParent().toFile();
        String className = extractClassName(request.getCode());
        
        if (className == null) {
            throw new RuntimeException("Could not find class name for execution");
        }
        
        String javaPath = request.getCompilerPath() != null ? 
            request.getCompilerPath().replace("javac", "java") : "java";
        
        ProcessBuilder pb = new ProcessBuilder(javaPath, className)
                .directory(classFileDir)
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
                log.warn("Error reading java execution output", e);
            }
        });
        outputReader.start();
        
        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        outputReader.join(1000);  // Give reader thread a second to finish
        
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Java execution timed out");
        }
        
        if (process.exitValue() != 0) {
            log.warn("Java execution returned non-zero exit code: {}", process.exitValue());
        }
        
        return output.toString();
    }
    
    @Override
    public Path createSourceFile(String code, Path workDirectory) throws Exception {
        String className = extractClassName(code);
        if (className == null) {
            throw new RuntimeException("Could not find class name in Java code");
        }
        
        Path filePath = workDirectory.resolve(className + ".java");
        Files.writeString(filePath, code);
        log.debug("Created Java source file: {}", filePath);
        return filePath;
    }
    
    @Override
    public void cleanup(Path sourceFile) {
        try {
            if (sourceFile != null && Files.exists(sourceFile)) {
                // Delete all .class files in the same directory
                File classFileDir = sourceFile.getParent().toFile();
                File[] classFiles = classFileDir.listFiles((dir, name) -> name.endsWith(".class"));
                
                if (classFiles != null) {
                    for (File classFile : classFiles) {
                        Files.deleteIfExists(classFile.toPath());
                    }
                }
                
                // Delete source file
                Files.deleteIfExists(sourceFile);
                log.debug("Cleaned up Java files: {}", sourceFile);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up Java files", e);
        }
    }
    
    private String extractClassName(String code) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

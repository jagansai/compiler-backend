package com.example.compiler.service.plugin;

import com.example.compiler.model.CompilationRequest;
import java.nio.file.Path;

/**
 * Plugin interface for language-specific compiler implementations.
 * Each language (C++, Java, Python, etc.) implements this interface
 * to handle compilation and assembly generation.
 */
public interface CompilerPlugin {
    
    /**
     * @return The language this plugin handles (e.g., "cpp", "java")
     */
    String getLanguage();
    
    /**
     * @return The compilers this plugin supports (e.g., ["g++", "cl"] for C++)
     */
    String[] getSupportedCompilers();
    
    /**
     * @return Default compiler options for this language
     */
    String[] getDefaultCompilerOptions();
    
    /**
     * Compile source code to executable/class files.
     * 
     * @param request The compilation request
     * @param sourceFile Path to the source file
     * @param timeout Timeout in seconds
     * @return CompilationResult with success status and error messages
     * @throws Exception if compilation fails
     */
    CompilationResult compile(CompilationRequest request, Path sourceFile, int timeout) throws Exception;
    
    /**
     * Generate assembly output from source code.
     * 
     * @param request The compilation request
     * @param sourceFile Path to the source file
     * @param timeout Timeout in seconds
     * @return Assembly output as string
     * @throws Exception if assembly generation fails
     */
    String generateAssembly(CompilationRequest request, Path sourceFile, int timeout) throws Exception;
    
    /**
     * Execute the compiled code and return the program output.
     * 
     * @param request The compilation request
     * @param sourceFile Path to the source file
     * @param timeout Timeout in seconds
     * @return Program execution output as string
     * @throws Exception if execution fails
     */
    String execute(CompilationRequest request, Path sourceFile, int timeout) throws Exception;
    
    /**
     * Create source file with appropriate extension and naming.
     * 
     * @param code Source code content
     * @param workDirectory Working directory for file creation
     * @return Path to created source file
     * @throws Exception if file creation fails
     */
    Path createSourceFile(String code, Path workDirectory) throws Exception;
    
    /**
     * Clean up files generated during compilation.
     * 
     * @param sourceFile The source file to clean up along with generated artifacts
     */
    void cleanup(Path sourceFile);
}

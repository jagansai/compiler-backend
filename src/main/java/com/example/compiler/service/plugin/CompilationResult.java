package com.example.compiler.service.plugin;

/**
 * Result of a compilation operation.
 */
public class CompilationResult {
    private final boolean success;
    private final String errorOutput;
    
    public CompilationResult(boolean success, String errorOutput) {
        this.success = success;
        this.errorOutput = errorOutput;
    }
    
    public static CompilationResult success() {
        return new CompilationResult(true, null);
    }
    
    public static CompilationResult failure(String errorOutput) {
        return new CompilationResult(false, errorOutput);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorOutput() {
        return errorOutput;
    }
}

package com.example.compiler.model;

public record CompilationResponse(String assemblyOutput, String output, String error, boolean success) {
    public CompilationResponse() {
        this(null, null, null, false);
    }
}

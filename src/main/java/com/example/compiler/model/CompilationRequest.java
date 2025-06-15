package com.example.compiler.model;

public class CompilationRequest {
    private String language;
    private String code;
    private String compilerOptions;
    private String compiler; // e.g., "g++", "cl"

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCompilerOptions() {
        return compilerOptions;
    }

    public void setCompilerOptions(String compilerOptions) {
        this.compilerOptions = compilerOptions;
    }

    public String getCompiler() {
        return compiler;
    }

    public void setCompiler(String compiler) {
        this.compiler = compiler;
    }
}

package com.example.compiler.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CompilationRequest {

    @NotBlank(message = "Language is required")
    private String language;

    @NotBlank(message = "Compiler ID is required")
    private String compilerId;

    @NotBlank(message = "Code is required")
    @Size(max = 100000, message = "Code must not exceed 100,000 characters")
    private String code;

    @Size(max = 500, message = "Compiler options must not exceed 500 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-/:.]*$", message = "Compiler options contain invalid characters")
    private String compilerOptions;

    // Set internally by CompilerService, not from user input
    private String compilerPath;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCompilerId() {
        return compilerId;
    }

    public void setCompilerId(String compilerId) {
        this.compilerId = compilerId;
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

    public String getCompilerPath() {
        return compilerPath;
    }

    public void setCompilerPath(String compilerPath) {
        this.compilerPath = compilerPath;
    }
}

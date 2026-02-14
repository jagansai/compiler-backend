package com.example.compiler.model;

public class CompilationResponse {
    private String assemblyOutput;
    private String output;
    private String error;
    private boolean success;

    public String getAssemblyOutput() {
        return assemblyOutput;
    }

    public void setAssemblyOutput(String assemblyOutput) {
        this.assemblyOutput = assemblyOutput;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}

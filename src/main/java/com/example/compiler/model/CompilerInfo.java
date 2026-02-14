package com.example.compiler.model;

public class CompilerInfo {
    private String id;
    private String name;
    private String path;
    private String version;
    private boolean isDefault;
    private String defaultArgs;
    private java.util.Map<String, String> metadata = new java.util.HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getDefaultArgs() {
        return defaultArgs;
    }

    public void setDefaultArgs(String defaultArgs) {
        this.defaultArgs = defaultArgs;
    }

    public java.util.Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.Map<String, String> metadata) {
        this.metadata = metadata;
    }
}

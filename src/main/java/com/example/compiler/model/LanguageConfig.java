package com.example.compiler.model;

import java.util.List;

public class LanguageConfig {
    private String id;
    private String name;
    private String fileExtension;
    private boolean allowCustomArgs;
    private String defaultCodePath;
    private String defaultCode;  // Populated from defaultCodePath file
    private String editorLanguage;
    private List<CompilerInfo> compilers;

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

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public boolean isAllowCustomArgs() {
        return allowCustomArgs;
    }

    public void setAllowCustomArgs(boolean allowCustomArgs) {
        this.allowCustomArgs = allowCustomArgs;
    }

    public String getDefaultCodePath() {
        return defaultCodePath;
    }

    public void setDefaultCodePath(String defaultCodePath) {
        this.defaultCodePath = defaultCodePath;
    }

    public String getDefaultCode() {
        return defaultCode;
    }

    public void setDefaultCode(String defaultCode) {
        this.defaultCode = defaultCode;
    }

    public String getEditorLanguage() {
        return editorLanguage;
    }

    public void setEditorLanguage(String editorLanguage) {
        this.editorLanguage = editorLanguage;
    }

    public List<CompilerInfo> getCompilers() {
        return compilers;
    }

    public void setCompilers(List<CompilerInfo> compilers) {
        this.compilers = compilers;
    }

    public CompilerInfo getDefaultCompiler() {
        return compilers.stream()
                .filter(CompilerInfo::isDefault)
                .findFirst()
                .orElse(compilers.isEmpty() ? null : compilers.get(0));
    }

    public CompilerInfo getCompilerById(String compilerId) {
        return compilers.stream()
                .filter(c -> c.getId().equals(compilerId))
                .findFirst()
                .orElse(null);
    }
}

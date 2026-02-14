package com.example.compiler.model;

import java.util.List;

public class CompilerConfigModel {
    private List<LanguageConfig> languages;

    public List<LanguageConfig> getLanguages() {
        return languages;
    }

    public void setLanguages(List<LanguageConfig> languages) {
        this.languages = languages;
    }

    public LanguageConfig getLanguageById(String languageId) {
        return languages.stream()
                .filter(lang -> lang.getId().equals(languageId))
                .findFirst()
                .orElse(null);
    }
}

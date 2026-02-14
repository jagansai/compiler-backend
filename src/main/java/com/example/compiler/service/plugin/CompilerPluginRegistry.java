package com.example.compiler.service.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for managing compiler plugins.
 * Auto-wires all available compiler plugins and provides lookup by language.
 */
@Component
public class CompilerPluginRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(CompilerPluginRegistry.class);
    private final Map<String, CompilerPlugin> pluginMap = new HashMap<>();
    
    /**
     * Constructor that auto-wires all CompilerPlugin beans.
     */
    public CompilerPluginRegistry(List<CompilerPlugin> plugins) {
        for (CompilerPlugin plugin : plugins) {
            pluginMap.put(plugin.getLanguage().toLowerCase(), plugin);
            log.info("Registered compiler plugin for language: {} (compilers: {})", 
                    plugin.getLanguage(), 
                    String.join(", ", plugin.getSupportedCompilers()));
        }
    }
    
    /**
     * Get the appropriate compiler plugin for a language.
     * 
     * @param language The programming language (e.g., "cpp", "java")
     * @return The compiler plugin for the language
     * @throws IllegalArgumentException if no plugin exists for the language
     */
    public CompilerPlugin getPlugin(String language) {
        if (language == null || language.isEmpty()) {
            throw new IllegalArgumentException("Language cannot be null or empty");
        }
        
        CompilerPlugin plugin = pluginMap.get(language.toLowerCase());
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "Unsupported language: " + language + ". Supported languages: " + 
                    String.join(", ", pluginMap.keySet()));
        }
        
        return plugin;
    }
    
    /**
     * Check if a language is supported.
     */
    public boolean isLanguageSupported(String language) {
        return language != null && pluginMap.containsKey(language.toLowerCase());
    }
    
    /**
     * Get all supported languages.
     */
    public String[] getSupportedLanguages() {
        return pluginMap.keySet().toArray(new String[0]);
    }
}

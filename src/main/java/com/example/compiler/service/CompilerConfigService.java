package com.example.compiler.service;

import com.example.compiler.model.CompilerConfigModel;
import com.example.compiler.model.CompilerInfo;
import com.example.compiler.model.LanguageConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompilerConfigService {
    private static final Logger log = LoggerFactory.getLogger(CompilerConfigService.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    private CompilerConfigModel config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Environment environment;
    
    public CompilerConfigService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void loadConfig() {
        try {
            ClassPathResource resource = new ClassPathResource("compiler-config.json");
            try (InputStream inputStream = resource.getInputStream()) {
                config = objectMapper.readValue(inputStream, CompilerConfigModel.class);
                
                // Load default code from files
                loadDefaultCode();
                
                // Resolve property placeholders in compiler paths
                resolvePlaceholders();
                
                log.info("Loaded compiler configuration with {} languages", config.getLanguages().size());
            }
        } catch (IOException e) {
            log.error("Failed to load compiler configuration", e);
            throw new RuntimeException("Failed to load compiler configuration", e);
        }
    }
    
    private void loadDefaultCode() {
        for (LanguageConfig langConfig : config.getLanguages()) {
            String defaultCodePath = langConfig.getDefaultCodePath();
            if (defaultCodePath != null && !defaultCodePath.isEmpty()) {
                try {
                    ClassPathResource codeResource = new ClassPathResource(defaultCodePath);
                    try (InputStream codeStream = codeResource.getInputStream()) {
                        String code = new String(codeStream.readAllBytes());
                        langConfig.setDefaultCode(code);
                        log.debug("Loaded default code for {}: {}", langConfig.getId(), defaultCodePath);
                    }
                } catch (IOException e) {
                    log.warn("Failed to load default code from {}: {}", defaultCodePath, e.getMessage());
                    langConfig.setDefaultCode(""); // Set empty string as fallback
                }
            }
        }
    }
    
    private void resolvePlaceholders() {
        for (LanguageConfig langConfig : config.getLanguages()) {
            for (CompilerInfo compiler : langConfig.getCompilers()) {
                String path = compiler.getPath();
                if (path != null && path.contains("${")) {
                    String resolved = resolvePropertyPlaceholder(path);
                    compiler.setPath(resolved);
                    log.debug("Resolved compiler path for {}: {} -> {}", 
                             compiler.getId(), path, resolved);
                }
            }
        }
    }
    
    private String resolvePropertyPlaceholder(String value) {
        if (value == null) {
            return null;
        }
        
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String resolved = environment.getProperty(placeholder);
            
            if (resolved == null) {
                log.warn("Property placeholder not found: ${{}}", placeholder);
                resolved = ""; // Keep empty or original?
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    public CompilerConfigModel getConfig() {
        return config;
    }

    public LanguageConfig getLanguageConfig(String languageId) {
        return config.getLanguageById(languageId);
    }
}

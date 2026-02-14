package com.example.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for compiler settings.
 * Externalized settings from application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "compiler")
public class CompilerConfig {
    
    /**
     * Timeout in seconds for compilation and assembly generation.
     */
    private int timeoutSeconds = 20;
    
    /**
     * Maximum code size in characters.
     */
    private int maxCodeSize = 100000;
    
    /**
     * Maximum compiler options length.
     */
    private int maxOptionsLength = 500;
    
    /**
     * CORS allowed origins (comma-separated).
     */
    private String corsOrigins = "*";
    
    /**
     * C++ compiler configuration.
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public int getMaxCodeSize() {
        return maxCodeSize;
    }
    
    public void setMaxCodeSize(int maxCodeSize) {
        this.maxCodeSize = maxCodeSize;
    }
    
    public int getMaxOptionsLength() {
        return maxOptionsLength;
    }
    
    public void setMaxOptionsLength(int maxOptionsLength) {
        this.maxOptionsLength = maxOptionsLength;
    }
    
    public String getCorsOrigins() {
        return corsOrigins;
    }
    
    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }
    // C++-specific settings are loaded via CompilerConfigService or environment
}

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
    private CppConfig cpp = new CppConfig();
    
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
    
    public CppConfig getCpp() {
        return cpp;
    }
    
    public void setCpp(CppConfig cpp) {
        this.cpp = cpp;
    }
    
    /**
     * C++ specific configuration.
     */
    public static class CppConfig {
        
        /**
         * Preferred C++ compiler (g++ or cl). If not set, auto-detect.
         */
        private String preferredCompiler;
        
        /**
         * Custom MSVC paths to try (in addition to standard locations).
         */
        private List<String> msvcPaths = new ArrayList<>();
        
        /**
         * Path to vcvars64.bat for MSVC environment setup.
         */
        private String vcvarsPath;
        
        /**
         * Path to vswhere.exe for Visual Studio detection.
         */
        private String vswherePath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe";
        
        public String getPreferredCompiler() {
            return preferredCompiler;
        }
        
        public void setPreferredCompiler(String preferredCompiler) {
            this.preferredCompiler = preferredCompiler;
        }
        
        public List<String> getMsvcPaths() {
            return msvcPaths;
        }
        
        public void setMsvcPaths(List<String> msvcPaths) {
            this.msvcPaths = msvcPaths;
        }
        
        public String getVcvarsPath() {
            return vcvarsPath;
        }
        
        public void setVcvarsPath(String vcvarsPath) {
            this.vcvarsPath = vcvarsPath;
        }
        
        public String getVswherePath() {
            return vswherePath;
        }
        
        public void setVswherePath(String vswherePath) {
            this.vswherePath = vswherePath;
        }
    }
}

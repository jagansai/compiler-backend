package com.example.compiler.service.plugin;

import com.example.compiler.config.CompilerConfig;
import org.springframework.core.env.Environment;
import com.example.compiler.service.CompilerConfigService;
import com.example.compiler.model.CompilationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Plugin for C++ compilation and assembly generation.
 * Supports g++ and MSVC (cl.exe) compilers.
 */
@Component
public class CppCompilerPlugin implements CompilerPlugin {
    
    private static final Logger log = LoggerFactory.getLogger(CppCompilerPlugin.class);
    private static final String LANGUAGE = "cpp";
    private static final String GXX = "g++";
    private static final String CL = "cl";
    
    private final CompilerConfigService configService;
    private final Environment environment;
    private final GccCompilerPlugin gccPlugin;
    private final MsvcCompilerPlugin msvcPlugin;
    
    public CppCompilerPlugin(CompilerConfig config, CompilerConfigService configService, Environment environment) {
        this.configService = configService;
        this.environment = environment;
        this.gccPlugin = new GccCompilerPlugin();
        this.msvcPlugin = new MsvcCompilerPlugin(config, configService, environment);
    }

    /**
     * Return default compiler options for a specific compiler id (e.g. "g++" or "msvc").
     * This is used by the controller when a client asks for options for a particular
     * C++ compiler.
     */
    public String[] getDefaultCompilerOptions(String compilerId) {
        if (compilerId != null && (compilerId.contains("msvc") || compilerId.contains("cl"))) {
            return msvcPlugin.getDefaultCompilerOptions();
        }
        return gccPlugin.getDefaultCompilerOptions();
    }
    
    @Override
    public String getLanguage() {
        return LANGUAGE;
    }
    
    @Override
    public String[] getSupportedCompilers() {
        return new String[] { GXX, CL };
    }
    
    @Override
    public String[] getDefaultCompilerOptions() {
        return new String[] {};
    }
    
    @Override
    public CompilationResult compile(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        String compiler = determineCompiler(request);
        if (CL.equals(compiler)) {
            return msvcPlugin.compile(request, sourceFile, timeout);
        }
        return gccPlugin.compile(request, sourceFile, timeout);
    }
    
    @Override
    public String generateAssembly(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        String compiler = determineCompiler(request);
        if (CL.equals(compiler)) {
            return msvcPlugin.generateAssembly(request, sourceFile, timeout);
        }
        return gccPlugin.generateAssembly(request, sourceFile, timeout);
    }
    
    @Override
    public String execute(CompilationRequest request, Path sourceFile, int timeout) throws Exception {
        String compiler = determineCompiler(request);
        if (CL.equals(compiler)) {
            return msvcPlugin.execute(request, sourceFile, timeout);
        }
        return gccPlugin.execute(request, sourceFile, timeout);
    }
    
    @Override
    public Path createSourceFile(String code, Path workDirectory) throws Exception {
        // Use G++ implementation for creating source files
        return gccPlugin.createSourceFile(code, workDirectory);
    }
    
    @Override
    public void cleanup(Path sourceFile) {
        try {
            // Attempt both cleanups to cover artifacts from either compiler
            gccPlugin.cleanup(sourceFile);
            msvcPlugin.cleanup(sourceFile);
        } catch (Exception e) {
            log.warn("Error cleaning up C++ files", e);
        }
    }
    
    private String determineCompiler(CompilationRequest request) {
        // Use compilerId to determine compiler
        // For now, map compilerId to compiler command
        // In future, this should use the config service to get actual paths
        String compilerId = request.getCompilerId();
        
        if (compilerId != null) {
            if (compilerId.equals("msvc") || compilerId.contains("cl")) {
                return CL;
            } else if (compilerId.equals("gcc") || compilerId.contains("g++") || compilerId.contains("gcc")) {
                return GXX;
            }
        }
        
        // Use preferred compiler from environment or language config if set
        String preferredCompiler = environment.getProperty("compiler.cpp.preferred-compiler");
        if (preferredCompiler == null || preferredCompiler.isEmpty()) {
            try {
                var lang = configService.getLanguageConfig("cpp");
                if (lang != null && lang.getDefaultCompiler() != null) {
                    preferredCompiler = lang.getDefaultCompiler().getId();
                }
            } catch (Exception ignored) {
            }
        }

        if (preferredCompiler != null && !preferredCompiler.isEmpty()) {
            if (preferredCompiler.contains("g") || preferredCompiler.contains("gcc")
                    || preferredCompiler.contains("g++")) {
                if (isGppAvailable()) {
                    log.info("Using preferred compiler: g++");
                    return GXX;
                }
            } else if (preferredCompiler.contains("msvc") || preferredCompiler.contains("cl")) {
                if (isMsvcAvailable()) {
                    log.info("Using preferred compiler: cl (MSVC)");
                    return CL;
                }
            }
            log.warn("Preferred compiler {} not available, auto-detecting", preferredCompiler);
        }
        
        // Auto-detect available compiler
        return detectAvailableCompiler();
    }
    
    private String detectAvailableCompiler() {
        // Try MSVC first if we're on Windows
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            if (isMsvcAvailable()) {
                log.info("Using MSVC compiler");
                return CL;
            }
        }
        
        // Try G++
        if (isGppAvailable()) {
            log.info("Using G++ compiler");
            return GXX;
        }
        
        log.error("No C++ compiler found. Current PATH: {}", System.getenv("PATH"));
        throw new IllegalStateException(
                "No C++ compiler found. Please install either G++ or MSVC.");
    }
    
    private boolean isMsvcAvailable() {
        return msvcPlugin.isMsvcAvailable();
    }
    
    private boolean isGppAvailable() {
        try {
            Process gppProcess = new ProcessBuilder("g++", "--version").start();
            return gppProcess.waitFor(5, TimeUnit.SECONDS) && gppProcess.exitValue() == 0;
        } catch (Exception e) {
            log.debug("G++ not found or not working", e);
            return false;
        }
    }

}

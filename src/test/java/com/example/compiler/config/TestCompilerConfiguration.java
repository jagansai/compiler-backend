package com.example.compiler.config;

import com.example.compiler.service.plugin.CompilerPlugin;
import com.example.compiler.service.plugin.CompilerPluginRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@TestConfiguration
public class TestCompilerConfiguration {

    @Bean
    public CompilerConfig testCompilerConfig() {
        CompilerConfig config = new CompilerConfig();
        config.setTimeoutSeconds(20);
        config.setMaxCodeSize(100000);
        
        CompilerConfig.CppConfig cppConfig = new CompilerConfig.CppConfig();
        cppConfig.setPreferredCompiler("g++");
        cppConfig.setMsvcPaths(List.of());
        cppConfig.setVswherePath("C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe");
        cppConfig.setVcvarsPath("");
        
        config.setCpp(cppConfig);
        
        return config;
    }
    
    @Bean
    public CompilerPluginRegistry testCompilerPluginRegistry(List<CompilerPlugin> plugins) {
        return new CompilerPluginRegistry(plugins);
    }
}

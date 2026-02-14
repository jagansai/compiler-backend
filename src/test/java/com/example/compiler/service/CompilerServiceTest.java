package com.example.compiler.service;

import com.example.compiler.config.CompilerConfig;
import com.example.compiler.model.CompilationRequest;
import com.example.compiler.model.CompilationResponse;
import com.example.compiler.service.plugin.CompilationResult;
import com.example.compiler.service.plugin.CompilerPlugin;
import com.example.compiler.service.plugin.CompilerPluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

class CompilerServiceTest {

    private CompilerPluginRegistry pluginRegistry;
    private CompilerPlugin mockPlugin;
    private CompilerConfig config;
    private CompilerService compilerService;

    @BeforeEach
    void setUp() throws Exception {
        // Create real config object
        config = new CompilerConfig();
        config.setTimeoutSeconds(20);
        config.setMaxCodeSize(100000);
        
        CompilerConfig.CppConfig cppConfig = new CompilerConfig.CppConfig();
        cppConfig.setPreferredCompiler("");
        cppConfig.setMsvcPaths(Arrays.asList());
        cppConfig.setVswherePath("C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe");
        cppConfig.setVcvarsPath("");
        config.setCpp(cppConfig);
        
        // Create mock plugin with doReturn for array methods
        mockPlugin = Mockito.mock(CompilerPlugin.class);
        when(mockPlugin.getLanguage()).thenReturn("java");
        doReturn(new String[]{"javac"}).when(mockPlugin).getSupportedCompilers();
        doReturn(new String[]{}).when(mockPlugin).getDefaultCompilerOptions();
        
        // Create real registry with mock plugin
        List<CompilerPlugin> plugins = Arrays.asList(mockPlugin);
        pluginRegistry = new CompilerPluginRegistry(plugins);
        
        compilerService = new CompilerService(pluginRegistry, config);
    }

    @Test
    void testCompileSuccess() throws Exception {
        String code = "public class Test {}";
        String assemblyOutput = "Assembly output here";
        Path mockSourceFile = Paths.get("test.java");
        
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCode(code);
        
        when(mockPlugin.createSourceFile(eq(code), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(eq(request), eq(mockSourceFile), anyInt()))
            .thenReturn(CompilationResult.success());
        when(mockPlugin.generateAssembly(eq(request), eq(mockSourceFile), anyInt()))
            .thenReturn(assemblyOutput);
        
        CompilationResponse response = compilerService.compile(request);
        
        assertTrue(response.isSuccess());
        assertEquals(assemblyOutput, response.getAssemblyOutput());
        assertNull(response.getError());
        
        verify(mockPlugin).createSourceFile(eq(code), any(Path.class));
        verify(mockPlugin).compile(eq(request), eq(mockSourceFile), eq(20));
        verify(mockPlugin).generateAssembly(eq(request), eq(mockSourceFile), eq(20));
        verify(mockPlugin).cleanup(mockSourceFile);
    }

    @Test
    void testCompileFailure() throws Exception {
        String code = "public class Invalid { invalid syntax }";
        String errorOutput = "Compilation error: syntax error";
        Path mockSourceFile = Paths.get("test.java");
        
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCode(code);
        
        when(mockPlugin.createSourceFile(eq(code), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(eq(request), eq(mockSourceFile), anyInt()))
            .thenReturn(CompilationResult.failure(errorOutput));
        
        CompilationResponse response = compilerService.compile(request);
        
        assertFalse(response.isSuccess());
        assertEquals(errorOutput, response.getError());
        assertNull(response.getAssemblyOutput());
        
        verify(mockPlugin).compile(eq(request), eq(mockSourceFile), anyInt());
        verify(mockPlugin, never()).generateAssembly(any(), any(), anyInt());
        verify(mockPlugin).cleanup(mockSourceFile);
    }

    @Test
    void testCompileUnsupportedLanguage() {
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("python");
        request.setCode("print('hello')");
        
        // Real registry will throw IllegalArgumentException for unsupported language
        // CompilerService catches it and returns as error response
        CompilationResponse response = compilerService.compile(request);
        
        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Unsupported language"));
    }

    @Test
    void testCompilePluginThrowsException() throws Exception {
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");  // Changed from cpp to java
        request.setCode("public class Test {}");
        
        Path mockSourceFile = Paths.get("test.java");
        
        when(mockPlugin.createSourceFile(anyString(), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(any(), any(), anyInt()))
            .thenThrow(new RuntimeException("Compiler not found"));
        
        CompilationResponse response = compilerService.compile(request);
        
        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Compiler not found"));
        
        verify(mockPlugin).cleanup(mockSourceFile);
    }

    @Test
    void testCompileUsesConfiguredTimeout() throws Exception {
        // Create a new config with different timeout
        CompilerConfig customConfig = new CompilerConfig();
        customConfig.setTimeoutSeconds(30);
        
        CompilerConfig.CppConfig cppConfig = new CompilerConfig.CppConfig();
        cppConfig.setPreferredCompiler("");
        cppConfig.setMsvcPaths(Arrays.asList());
        cppConfig.setVswherePath("C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe");
        cppConfig.setVcvarsPath("");
        customConfig.setCpp(cppConfig);
        
        // Create new mock plugin for this test
        CompilerPlugin testPlugin = Mockito.mock(CompilerPlugin.class);
        when(testPlugin.getLanguage()).thenReturn("java");
        doReturn(new String[]{"javac"}).when(testPlugin).getSupportedCompilers();
        doReturn(new String[]{}).when(testPlugin).getDefaultCompilerOptions();
        
        List<CompilerPlugin> plugins = Arrays.asList(testPlugin);
        CompilerPluginRegistry testRegistry = new CompilerPluginRegistry(plugins);
        CompilerService serviceWithCustomTimeout = new CompilerService(testRegistry, customConfig);
        
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCode("public class Test {}");
        
        Path mockSourceFile = Paths.get("test.java");
        
        when(testPlugin.createSourceFile(anyString(), any(Path.class))).thenReturn(mockSourceFile);
        when(testPlugin.compile(any(), any(), anyInt())).thenReturn(CompilationResult.success());
        when(testPlugin.generateAssembly(any(), any(), anyInt())).thenReturn("asm");
        
        serviceWithCustomTimeout.compile(request);
        
        verify(testPlugin).compile(any(), any(), eq(30));
        verify(testPlugin).generateAssembly(any(), any(), eq(30));
    }

    @Test
    void testCleanupCalledEvenOnException() throws Exception {
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");  // Changed from cpp to java since only java plugin is registered
        request.setCode("public class Test {}");
        
        Path mockSourceFile = Paths.get("test.java");
        
        when(mockPlugin.createSourceFile(anyString(), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(any(), any(), anyInt()))
            .thenThrow(new RuntimeException("Test exception"));
        
        compilerService.compile(request);
        
        verify(mockPlugin).cleanup(mockSourceFile);
    }
}

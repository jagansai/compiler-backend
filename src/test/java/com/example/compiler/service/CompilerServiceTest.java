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
import com.example.compiler.model.LanguageConfig;
import com.example.compiler.model.CompilerInfo;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

class CompilerServiceTest {

    private CompilerPluginRegistry pluginRegistry;
    private CompilerPlugin mockPlugin;
    private CompilerConfig config;
    private CompilerConfigService compilerConfigService;
    private CompilerService compilerService;

    @BeforeEach
    void setUp() throws Exception {
        // Create real config object
        config = new CompilerConfig();
        config.setTimeoutSeconds(20);
        config.setMaxCodeSize(100000);
        // C++-specific settings are provided via compiler-config.json or environment in tests
        
        // Create mock plugin with doReturn for array methods
        mockPlugin = Mockito.mock(CompilerPlugin.class);
        when(mockPlugin.getLanguage()).thenReturn("java");
        doReturn(new String[]{"javac"}).when(mockPlugin).getSupportedCompilers();
        doReturn(new String[]{}).when(mockPlugin).getDefaultCompilerOptions();
        
        // Create real registry with mock plugin
        List<CompilerPlugin> plugins = Arrays.asList(mockPlugin);
        pluginRegistry = new CompilerPluginRegistry(plugins);

        // Create a simple CompilerConfigService stub that returns a LanguageConfig for
        // "java"
        LanguageConfig javaLang = new LanguageConfig();
        javaLang.setId("java");
        CompilerInfo javacInfo = new CompilerInfo();
        javacInfo.setId("javac");
        javacInfo.setName("javac");
        javacInfo.setPath("javac");
        javaLang.setCompilers(Arrays.asList(javacInfo));

        compilerConfigService = new CompilerConfigService(null) {
            @Override
            public LanguageConfig getLanguageConfig(String languageId) {
                if ("java".equals(languageId))
                    return javaLang;
                return null;
            }
        };

        compilerService = new CompilerService(pluginRegistry, config, compilerConfigService);
    }

    @Test
    void testCompileSuccess() throws Exception {
        String code = "public class Test {}";
        String assemblyOutput = "Assembly output here";
        Path mockSourceFile = Paths.get("test.java");
        
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCompilerId("javac");
        request.setCode(code);
        
        when(mockPlugin.createSourceFile(eq(code), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(eq(request), eq(mockSourceFile), anyInt()))
            .thenReturn(CompilationResult.success());
        when(mockPlugin.generateAssembly(eq(request), eq(mockSourceFile), anyInt()))
            .thenReturn(assemblyOutput);
        
        CompilationResponse response = compilerService.compile(request);

        assertTrue(response.success());
        assertEquals(assemblyOutput, response.assemblyOutput());
        assertNull(response.error());
        
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
        request.setCompilerId("javac");
        request.setCode(code);
        
        when(mockPlugin.createSourceFile(eq(code), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(eq(request), eq(mockSourceFile), anyInt()))
            .thenReturn(CompilationResult.failure(errorOutput));
        
        CompilationResponse response = compilerService.compile(request);

        assertFalse(response.success());
        assertEquals(errorOutput, response.error());
        assertNull(response.assemblyOutput());
        
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

        assertFalse(response.success());
        assertNotNull(response.error());
        assertTrue(response.error().contains("Unsupported language"));
    }

    @Test
    void testCompilePluginThrowsException() throws Exception {
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");  // Changed from cpp to java
        request.setCompilerId("javac");
        request.setCode("public class Test {}");
        
        Path mockSourceFile = Paths.get("test.java");
        
        when(mockPlugin.createSourceFile(anyString(), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(any(), any(), anyInt()))
            .thenThrow(new RuntimeException("Compiler not found"));
        
        CompilationResponse response = compilerService.compile(request);

        assertFalse(response.success());
        assertNotNull(response.error());
        assertTrue(response.error().contains("Compiler not found"));
        
        verify(mockPlugin).cleanup(mockSourceFile);
    }

    @Test
    void testPerRequestDirectoryIsCreatedAndDeleted() throws Exception {
        String code = "public class Test {}";

        // Arrange: make the mock createSourceFile actually write into the provided dir
        when(mockPlugin.createSourceFile(anyString(), any(Path.class))).thenAnswer(invocation -> {
            Path dir = invocation.getArgument(1);
            Path file = dir.resolve("Test.java");
            Files.writeString(file, code);
            return file;
        });

        when(mockPlugin.compile(any(), any(), anyInt())).thenReturn(CompilationResult.success());
        when(mockPlugin.generateAssembly(any(), any(), anyInt())).thenReturn("asm");

        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCompilerId("javac");
        request.setCode(code);

        // Act
        CompilationResponse response = compilerService.compile(request);

        // Assert
        assertTrue(response.success());

        verify(mockPlugin).createSourceFile(eq(code), any(Path.class));
        // Ensure the service's working directory does not contain lingering Test.java
        // files.
        Files.walk(compilerService.getWorkDirectory())
                .filter(p -> p.getFileName().toString().equals("Test.java"))
                .forEach(p -> fail("Found leftover source file: " + p));
    }

    @Test
    void testCompileUsesConfiguredTimeout() throws Exception {
        // Create a new config with different timeout
        CompilerConfig customConfig = new CompilerConfig();
        customConfig.setTimeoutSeconds(30);
        
        // Create new mock plugin for this test
        CompilerPlugin testPlugin = Mockito.mock(CompilerPlugin.class);
        when(testPlugin.getLanguage()).thenReturn("java");
        doReturn(new String[]{"javac"}).when(testPlugin).getSupportedCompilers();
        doReturn(new String[]{}).when(testPlugin).getDefaultCompilerOptions();
        
        List<CompilerPlugin> plugins = Arrays.asList(testPlugin);
        CompilerPluginRegistry testRegistry = new CompilerPluginRegistry(plugins);
        // Create a simple CompilerConfigService stub for the custom registry
        CompilerConfigService customConfigService = new CompilerConfigService(null) {
            @Override
            public LanguageConfig getLanguageConfig(String languageId) {
                if ("java".equals(languageId)) {
                    LanguageConfig javaLang = new LanguageConfig();
                    javaLang.setId("java");
                    CompilerInfo javacInfo = new CompilerInfo();
                    javacInfo.setId("javac");
                    javacInfo.setName("javac");
                    javacInfo.setPath("javac");
                    javaLang.setCompilers(Arrays.asList(javacInfo));
                    return javaLang;
                }
                return null;
            }
        };

        CompilerService serviceWithCustomTimeout = new CompilerService(testRegistry, customConfig, customConfigService);
        
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCompilerId("javac");
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
        request.setCompilerId("javac");
        request.setCode("public class Test {}");
        
        Path mockSourceFile = Paths.get("test.java");
        
        when(mockPlugin.createSourceFile(anyString(), any(Path.class))).thenReturn(mockSourceFile);
        when(mockPlugin.compile(any(), any(), anyInt()))
            .thenThrow(new RuntimeException("Test exception"));
        
        compilerService.compile(request);
        
        verify(mockPlugin).cleanup(mockSourceFile);
    }
}

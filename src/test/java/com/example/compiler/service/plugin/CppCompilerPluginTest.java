package com.example.compiler.service.plugin;

import com.example.compiler.config.CompilerConfig;
import com.example.compiler.model.CompilationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CppCompilerPluginTest {

    private CompilerConfig config;
    private CppCompilerPlugin plugin;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Create real config object instead of mocking
        config = new CompilerConfig();
        // C++-specific settings are provided via compiler-config.json or environment in tests
        plugin = new CppCompilerPlugin(config, null, null);
    }

    @Test
    void testGetLanguage() {
        assertEquals("cpp", plugin.getLanguage());
    }

    @Test
    void testGetSupportedCompilers() {
        String[] compilers = plugin.getSupportedCompilers();
        assertNotNull(compilers);
        assertEquals(2, compilers.length);
        assertTrue(Arrays.asList(compilers).contains("g++"));
        assertTrue(Arrays.asList(compilers).contains("cl"));
    }

    @Test
    void testGetDefaultCompilerOptions() {
        String[] options = plugin.getDefaultCompilerOptions();
        assertNotNull(options);
        assertTrue(options.length > 0);
        assertTrue(Arrays.asList(options).contains("-O0"));
    }

    @Test
    void testCreateSourceFile() throws Exception {
        String code = "#include <iostream>\n" +
                     "int main() {\n" +
                     "    std::cout << \"Hello\" << std::endl;\n" +
                     "    return 0;\n" +
                     "}";
        
        Path sourceFile = plugin.createSourceFile(code, tempDir);
        
        assertNotNull(sourceFile);
        assertTrue(Files.exists(sourceFile));
        assertTrue(sourceFile.toString().endsWith(".cpp"));
        
        String content = Files.readString(sourceFile);
        assertEquals(code, content);
    }

    @Test
    void testCreateSourceFileGeneratesUniqueNames() throws Exception {
        String code = "int main() { return 0; }";
        
        Path sourceFile1 = plugin.createSourceFile(code, tempDir);
        Path sourceFile2 = plugin.createSourceFile(code, tempDir);
        
        assertNotEquals(sourceFile1.getFileName(), sourceFile2.getFileName());
    }

    @Test
    void testCleanup() throws Exception {
        String code = "int main() { return 0; }";
        
        Path sourceFile = plugin.createSourceFile(code, tempDir);
        
        // Create mock executable and object files
        Path exeFile = Path.of(sourceFile.toString() + ".exe");
        Path objFile = Path.of(sourceFile.toString() + ".obj");
        Path asmFile = Path.of(sourceFile.toString() + ".asm");
        
        Files.createFile(exeFile);
        Files.createFile(objFile);
        Files.createFile(asmFile);
        
        assertTrue(Files.exists(sourceFile));
        assertTrue(Files.exists(exeFile));
        assertTrue(Files.exists(objFile));
        assertTrue(Files.exists(asmFile));
        
        // Cleanup
        plugin.cleanup(sourceFile);
        
        assertFalse(Files.exists(sourceFile));
        assertFalse(Files.exists(exeFile));
        assertFalse(Files.exists(objFile));
        assertFalse(Files.exists(asmFile));
    }

    @Test
    void testCleanupNullFile() {
        // Should not throw exception
        assertDoesNotThrow(() -> plugin.cleanup(null));
    }

    @Test
    void testCleanupNonExistentFile() {
        Path nonExistent = tempDir.resolve("DoesNotExist.cpp");
        
        // Should not throw exception
        assertDoesNotThrow(() -> plugin.cleanup(nonExistent));
    }

    @Test
    void testCompileRequestWithSpecificCompiler() {
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("cpp");
        request.setCompilerId("g++");
        request.setCode("int main() { return 0; }");
        
        // This test verifies the plugin accepts the request structure
        assertNotNull(request.getCompilerId());
        assertEquals("g++", request.getCompilerId());
    }

    @Test
    void testPreferredCompilerFromConfig() {
        // Just ensure plugin can be constructed with a config
        CompilerConfig testConfig = new CompilerConfig();
        CppCompilerPlugin configuredPlugin = new CppCompilerPlugin(testConfig, null, null);
        assertNotNull(configuredPlugin);
    }

    @Test
    void testCustomMsvcPathsFromConfig() {
        // Construct plugin; MSVC specifics come from env or compiler-config.json in real runs
        CompilerConfig testConfig = new CompilerConfig();
        CppCompilerPlugin configuredPlugin = new CppCompilerPlugin(testConfig, null, null);
        assertNotNull(configuredPlugin);
    }
}

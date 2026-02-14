package com.example.compiler.service.plugin;

import com.example.compiler.model.CompilationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaCompilerPluginTest {

    private JavaCompilerPlugin plugin;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        plugin = new JavaCompilerPlugin();
    }

    @Test
    void testGetLanguage() {
        assertEquals("java", plugin.getLanguage());
    }

    @Test
    void testGetSupportedCompilers() {
        String[] compilers = plugin.getSupportedCompilers();
        assertNotNull(compilers);
        assertEquals(1, compilers.length);
        assertEquals("javac", compilers[0]);
    }

    @Test
    void testGetDefaultCompilerOptions() {
        String[] options = plugin.getDefaultCompilerOptions();
        assertNotNull(options);
        assertTrue(options.length > 0);
    }

    @Test
    void testCreateSourceFile() throws Exception {
        String code = "public class HelloWorld {\n" +
                     "    public static void main(String[] args) {\n" +
                     "        System.out.println(\"Hello\");\n" +
                     "    }\n" +
                     "}";
        
        Path sourceFile = plugin.createSourceFile(code, tempDir);
        
        assertNotNull(sourceFile);
        assertTrue(Files.exists(sourceFile));
        assertTrue(sourceFile.toString().endsWith(".java"));
        assertTrue(sourceFile.toString().contains("HelloWorld"));
        
        String content = Files.readString(sourceFile);
        assertEquals(code, content);
    }

    @Test
    void testCreateSourceFileNoClassName() {
        String code = "// Just a comment, no class";
        
        Exception exception = assertThrows(RuntimeException.class, () -> {
            plugin.createSourceFile(code, tempDir);
        });
        
        assertTrue(exception.getMessage().contains("Could not find class name"));
    }

    @Test
    void testCreateSourceFileWithPackage() throws Exception {
        String code = "package com.test;\n" +
                     "public class MyClass {\n" +
                     "}";
        
        Path sourceFile = plugin.createSourceFile(code, tempDir);
        
        assertNotNull(sourceFile);
        assertTrue(sourceFile.toString().contains("MyClass"));
    }

    @Test
    void testCompileValidCode() throws Exception {
        String code = "public class SimpleClass {\n" +
                     "    public void method() {}\n" +
                     "}";
        
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCode(code);
        
        Path sourceFile = plugin.createSourceFile(code, tempDir);
        CompilationResult result = plugin.compile(request, sourceFile, 10);
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorOutput());
        
        // Verify class file was created
        Path classFile = Path.of(sourceFile.toString().replace(".java", ".class"));
        assertTrue(Files.exists(classFile));
    }

    @Test
    void testCompileInvalidCode() throws Exception {
        String code = "public class Invalid {\n" +
                     "    this is not valid java\n" +
                     "}";
        
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCode(code);
        
        Path sourceFile = plugin.createSourceFile(code, tempDir);
        CompilationResult result = plugin.compile(request, sourceFile, 10);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorOutput());
        assertTrue(result.getErrorOutput().length() > 0);
    }

    @Test
    void testCleanup() throws Exception {
        String code = "public class CleanupTest {\n" +
                     "    public void method() {}\n" +
                     "}";
        
        Path sourceFile = plugin.createSourceFile(code, tempDir);
        
        // Compile to create .class file
        CompilationRequest request = new CompilationRequest();
        request.setLanguage("java");
        request.setCode(code);
        plugin.compile(request, sourceFile, 10);
        
        Path classFile = Path.of(sourceFile.toString().replace(".java", ".class"));
        assertTrue(Files.exists(sourceFile));
        assertTrue(Files.exists(classFile));
        
        // Cleanup
        plugin.cleanup(sourceFile);
        
        assertFalse(Files.exists(sourceFile));
        assertFalse(Files.exists(classFile));
    }

    @Test
    void testCleanupNonExistentFile() {
        Path nonExistent = tempDir.resolve("DoesNotExist.java");
        
        // Should not throw exception
        assertDoesNotThrow(() -> plugin.cleanup(nonExistent));
    }

    @Test
    void testCleanupNullFile() {
        // Should not throw exception
        assertDoesNotThrow(() -> plugin.cleanup(null));
    }
}

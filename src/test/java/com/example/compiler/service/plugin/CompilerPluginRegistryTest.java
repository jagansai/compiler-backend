package com.example.compiler.service.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CompilerPluginRegistryTest {

    @Mock
    private CompilerPlugin cppPlugin;
    
    @Mock
    private CompilerPlugin javaPlugin;
    
    private CompilerPluginRegistry registry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(cppPlugin.getLanguage()).thenReturn("cpp");
        when(cppPlugin.getSupportedCompilers()).thenReturn(new String[]{"g++", "cl"});
        
        when(javaPlugin.getLanguage()).thenReturn("java");
        when(javaPlugin.getSupportedCompilers()).thenReturn(new String[]{"javac"});
        
        registry = new CompilerPluginRegistry(Arrays.asList(cppPlugin, javaPlugin));
    }

    @Test
    void testPluginRegistration() {
        assertNotNull(registry);
        assertTrue(registry.isLanguageSupported("cpp"));
        assertTrue(registry.isLanguageSupported("java"));
    }

    @Test
    void testGetPluginByCpp() {
        CompilerPlugin plugin = registry.getPlugin("cpp");
        assertNotNull(plugin);
        assertEquals("cpp", plugin.getLanguage());
    }

    @Test
    void testGetPluginByJava() {
        CompilerPlugin plugin = registry.getPlugin("java");
        assertNotNull(plugin);
        assertEquals("java", plugin.getLanguage());
    }

    @Test
    void testGetPluginCaseInsensitive() {
        CompilerPlugin plugin1 = registry.getPlugin("CPP");
        CompilerPlugin plugin2 = registry.getPlugin("JAVA");
        
        assertNotNull(plugin1);
        assertNotNull(plugin2);
        assertEquals("cpp", plugin1.getLanguage());
        assertEquals("java", plugin2.getLanguage());
    }

    @Test
    void testGetPluginUnsupportedLanguage() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.getPlugin("python");
        });
        
        assertTrue(exception.getMessage().contains("Unsupported language"));
        assertTrue(exception.getMessage().contains("python"));
    }

    @Test
    void testGetPluginNullLanguage() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.getPlugin(null);
        });
        
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void testGetPluginEmptyLanguage() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.getPlugin("");
        });
        
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void testGetSupportedLanguages() {
        String[] languages = registry.getSupportedLanguages();
        
        assertNotNull(languages);
        assertEquals(2, languages.length);
        assertTrue(Arrays.asList(languages).contains("cpp"));
        assertTrue(Arrays.asList(languages).contains("java"));
    }

    @Test
    void testIsLanguageSupported() {
        assertTrue(registry.isLanguageSupported("cpp"));
        assertTrue(registry.isLanguageSupported("java"));
        assertTrue(registry.isLanguageSupported("CPP")); // case insensitive
        assertFalse(registry.isLanguageSupported("python"));
        assertFalse(registry.isLanguageSupported(null));
    }

    @Test
    void testEmptyPluginList() {
        CompilerPluginRegistry emptyRegistry = new CompilerPluginRegistry(Collections.emptyList());
        
        assertEquals(0, emptyRegistry.getSupportedLanguages().length);
        assertFalse(emptyRegistry.isLanguageSupported("cpp"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            emptyRegistry.getPlugin("cpp");
        });
    }
}

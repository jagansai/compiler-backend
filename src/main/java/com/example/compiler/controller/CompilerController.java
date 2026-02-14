package com.example.compiler.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.compiler.model.CompilationRequest;
import com.example.compiler.model.CompilationResponse;
import com.example.compiler.model.CompilerConfigModel;
import com.example.compiler.service.CompilerConfigService;
import com.example.compiler.service.CompilerService;
import com.example.compiler.service.plugin.CompilerPlugin;
import com.example.compiler.service.plugin.CompilerPluginRegistry;

@RestController
@RequestMapping("/api/compiler")
public class CompilerController {

    @Autowired
    private CompilerService compilerService;

    @Autowired
    private CompilerPluginRegistry pluginRegistry;

    @Autowired
    private CompilerConfigService configService;

    @GetMapping("/config")
    public ResponseEntity<CompilerConfigModel> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    @PostMapping("/compile")
    public ResponseEntity<CompilationResponse> compile(@Valid @RequestBody CompilationRequest request) {
        CompilationResponse response = compilerService.compile(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute")
    public ResponseEntity<CompilationResponse> execute(@Valid @RequestBody CompilationRequest request) {
        CompilationResponse response = compilerService.execute(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/languages")
    public ResponseEntity<String[]> getSupportedLanguages() {
        return ResponseEntity.ok(pluginRegistry.getSupportedLanguages());
    }

    @GetMapping("/options/{language}")
    public ResponseEntity<String[]> getCompilerOptions(@PathVariable String language,
                                                       @RequestParam(required = false) String compiler) {
        try {
            CompilerPlugin plugin = pluginRegistry.getPlugin(language);
            // Call the compiler-aware overload; plugins that don't care will ignore the param
            return ResponseEntity.ok(plugin.getDefaultCompilerOptions(compiler));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/compilers/{language}")
    public ResponseEntity<String[]> getSupportedCompilers(@PathVariable String language) {
        try {
            CompilerPlugin plugin = pluginRegistry.getPlugin(language);
            return ResponseEntity.ok(plugin.getSupportedCompilers());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

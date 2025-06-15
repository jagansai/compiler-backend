package com.example.compiler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.compiler.model.CompilationRequest;
import com.example.compiler.model.CompilationResponse;
import com.example.compiler.service.CompilerService;

@RestController
@RequestMapping("/api/compiler")
@CrossOrigin(origins = "*") // For development only - configure appropriately for production
public class CompilerController {

    @Autowired
    private CompilerService compilerService;

    @PostMapping("/compile")
    public ResponseEntity<CompilationResponse> compile(@RequestBody CompilationRequest request) {
        CompilationResponse response = compilerService.compile(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/languages")
    public ResponseEntity<String[]> getSupportedLanguages() {
        return ResponseEntity.ok(new String[] { "cpp", "java" });
    }

    @GetMapping("/options/{language}")
    public ResponseEntity<String[]> getCompilerOptions(@PathVariable String language) {
        if (language.equals("cpp")) {
            return ResponseEntity.ok(new String[] { "-O0", "-O1", "-O2", "-O3" });
        } else if (language.equals("java")) {
            return ResponseEntity.ok(new String[] { "-g", "-verbose", "-nowarn" });
        }
        return ResponseEntity.notFound().build();
    }
}

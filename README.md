# Compiler Explorer Backend

A Spring Boot backend service that compiles and returns assembly output for Java and C++ code.

## Features

- Supports Java and C++ code compilation
- Returns assembly/bytecode output
- REST API endpoint for code compilation
- Secure file handling with isolated workspaces
- Configurable compiler options

## Prerequisites

- Java 17 or higher
- Maven
- GCC/G++ (for C++ compilation)
- JDK (for Java compilation)

## API Endpoints

### POST /api/compiler/compile

Compiles source code and returns assembly output.

Request body:
```json
{
  "language": "java|cpp",
  "code": "source code here",
  "compilerOptions": "optional compiler options"
}
```

Response:
```json
{
  "success": true|false,
  "assemblyOutput": "assembly/bytecode output",
  "error": "error message if any"
}
```

## Building and Running

1. Clone the repository
2. Navigate to the project directory
3. Build the project:
   ```bash
   mvn clean install
   ```
4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The server will start on port 8083 by default.

## Configuration

Server configuration can be modified in `src/main/resources/application.properties`.

## Security

- Each service instance uses an isolated working directory
- Temporary files are automatically cleaned up
- POSIX file permissions are set when available
- UUID-based file naming to prevent collisions

Start-Sleep -Seconds 5

$code = @'
#include <cstdio>
int main() {
    int number = 42;
    printf("%d\n", number);
}
'@

$body = @{
    language        = "cpp"
    compiler        = "cl"
    code            = $code
    compilerOptions = "/O2"
} | ConvertTo-Json

curl -X POST http://localhost:8083/api/compiler/compile `
    -H "Content-Type: application/json" `
    -d $body
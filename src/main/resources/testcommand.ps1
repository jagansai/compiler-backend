Start-Sleep -Seconds 5

$code = @'
public class TestClass {
    private int number = 42;
    
    public int getNumber() {
        return number;
    }
}
'@

$body = @{
    language = "java"
    code = $code
} | ConvertTo-Json

curl -X POST http://localhost:8083/api/compiler/compile `
  -H "Content-Type: application/json" `
  -d $body
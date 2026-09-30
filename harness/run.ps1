# 中文：停止遇到 PowerShell 错误时继续执行，避免启动失败后误以为 Harness 已正常运行。
# English: Stop on PowerShell errors so a failed Harness launch is not mistaken for a successful one.
$ErrorActionPreference = "Stop"

# 中文：启动 Harness 的本地 Web UI；--no-open 避免脚本擅自打开浏览器。
# English: Start the local Harness web UI; --no-open prevents the command from opening a browser automatically.
Write-Host "Starting DeepSeek Harness Web UI..."
npx @deepseek-ai/dsh web --no-open

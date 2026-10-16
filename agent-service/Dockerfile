# 中文：使用项目约定的 Python 3.13 运行时；构建依赖与应用文件分层复制以便复用依赖层。
# English: Use the project's Python 3.13 runtime and copy dependencies separately so Docker can reuse that layer.
FROM python:3.13-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
# 中文：强制 h11 协议实现。httptools 实现会丢弃带 Connection: Upgrade（如 JDK HttpClient 的 h2c 升级）请求的 body，
#       导致 Spring Boot 4 RestClient 调用时 FastAPI 报 422 Field required。h11 能正确处理该场景。
# English: Pin the h11 protocol impl. httptools drops the body of requests carrying Connection: Upgrade (e.g. the
#          h2c upgrade sent by the JDK HttpClient used by Spring Boot 4's RestClient), which makes FastAPI return
#          422 Field required. h11 handles that case correctly.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--http", "h11"]

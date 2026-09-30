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
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]

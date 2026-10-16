FROM python:3.13-slim
COPY --from=ghcr.io/astral-sh/uv:0.12.19 /uv /uvx /bin/
WORKDIR /app
# 中文：先复制并安装锁定依赖，再复制应用代码，以便复用依赖镜像层。
# English: Install locked dependencies before copying app code so Docker can reuse the dependency layer.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    PATH="/app/.venv/bin:$PATH"
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev
COPY --chown=10001:10001 . .
RUN groupadd --system --gid 10001 agent \
    && useradd --system --uid 10001 --gid agent --home-dir /app --shell /usr/sbin/nologin agent
USER 10001:10001
EXPOSE 8000
# 中文：强制 h11 协议实现。httptools 实现会丢弃带 Connection: Upgrade（如 JDK HttpClient 的 h2c 升级）请求的 body，
#       导致 Spring Boot 4 RestClient 调用时 FastAPI 报 422 Field required。h11 能正确处理该场景。
# English: Pin the h11 protocol impl. httptools drops the body of requests carrying Connection: Upgrade (e.g. the
#          h2c upgrade sent by the JDK HttpClient used by Spring Boot 4's RestClient), which makes FastAPI return
#          422 Field required. h11 handles that case correctly.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--http", "h11"]

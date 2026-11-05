FROM python:3.11-slim
WORKDIR /app
COPY app.py .
RUN python -m py_compile app.py
USER 10001:10001
EXPOSE 8080
CMD ["python", "app.py"]

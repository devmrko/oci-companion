# Bootstrap only: the application image itself is assembled inside this Function.
# Supply a digest-pinned ARM64 Python 3.13 image and a verified crane release archive.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}
RUN pip install --no-cache-dir fdk==0.1.128 uv==0.10.6
COPY dependencies /opt/build-deps/
WORKDIR /opt/build-deps
RUN uv sync --frozen --no-install-project
WORKDIR /function
COPY crane /usr/local/bin/crane
COPY func.py image_layer.py dependency_cache.py source_bundle.py /function/
ENV PYTHONPATH=/function
USER 10001
ENTRYPOINT ["/usr/local/bin/fdk", "/function/func.py", "handler"]

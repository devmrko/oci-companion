ARG BASE_IMAGE
FROM ${BASE_IMAGE}
USER root
RUN microdnf install -y git tar gzip && microdnf clean all
RUN pip3 install --no-cache-dir oci==2.168.0 fdk==0.1.128
WORKDIR /function
COPY func.py python_build.py source_bundle.py /function/
ENTRYPOINT ["/usr/local/bin/fdk", "/function/func.py", "handler"]

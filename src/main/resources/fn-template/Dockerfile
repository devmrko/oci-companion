FROM container-registry.oracle.com/oci_functions/python:3.12
USER root
RUN microdnf install -y java-21-openjdk-devel git tar gzip curl lsof && microdnf clean all
RUN pip3 install --no-cache-dir oci==2.168.0 fdk==0.1.128
RUN curl -fsSL https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz -o /tmp/maven.tgz \
    && curl -fsSL https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz.sha512 -o /tmp/maven.sha512 \
    && echo "$(cat /tmp/maven.sha512)  /tmp/maven.tgz" | sha512sum -c - \
    && tar xzf /tmp/maven.tgz -C /opt && rm /tmp/maven.tgz /tmp/maven.sha512
ENV PATH="/opt/apache-maven-3.9.9/bin:${PATH}"
WORKDIR /function
COPY func.py /function/func.py
ENTRYPOINT ["/usr/local/bin/fdk", "/function/func.py", "handler"]

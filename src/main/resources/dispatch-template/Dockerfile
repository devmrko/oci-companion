FROM container-registry.oracle.com/oci_functions/python:3.12
RUN pip3 install --no-cache-dir oci==2.168.0 fdk==0.1.128
WORKDIR /function
COPY func.py /function/func.py
ENTRYPOINT ["/usr/local/bin/fdk", "/function/func.py", "handler"]

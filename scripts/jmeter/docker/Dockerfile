FROM eclipse-temurin:17-jre-jammy

ARG JMETER_VERSION=5.6.3

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl tar \
  && rm -rf /var/lib/apt/lists/* \
  && curl -fsSL "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz" -o /tmp/jmeter.tgz \
  && mkdir -p /opt \
  && tar -xzf /tmp/jmeter.tgz -C /opt \
  && ln -s "/opt/apache-jmeter-${JMETER_VERSION}/bin/jmeter" /usr/local/bin/jmeter \
  && rm /tmp/jmeter.tgz

ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}
ENV PATH="${JMETER_HOME}/bin:${PATH}"
ENV JVM_ARGS="-Dhttps.protocols=TLSv1.3,TLSv1.2 -Djdk.tls.client.protocols=TLSv1.3,TLSv1.2"

WORKDIR /work

ENTRYPOINT ["jmeter"]

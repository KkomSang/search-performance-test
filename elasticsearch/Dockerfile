FROM eclipse-temurin:17-jdk AS plugin
RUN apt-get update \
 && apt-get install -y --no-install-recommends git \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /build
RUN git clone --depth 1 --branch v8.6.2 https://github.com/netcrazy/elasticsearch-jaso-analyzer.git .
RUN sh gradlew clean build buildPluginZip -x test --no-daemon

FROM docker.elastic.co/elasticsearch/elasticsearch:8.6.2
COPY --from=plugin /build/build/distributions/jaso-analyzer-plugin-8.6.2-plugin.zip /tmp/
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch \
    file:///tmp/jaso-analyzer-plugin-8.6.2-plugin.zip

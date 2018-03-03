FROM openjdk:8-jdk-alpine

# install curl
RUN apk update && \
  apk add bash ncurses curl ca-certificates && \
  rm -rf /var/cache/apk/*

# install ammonite
RUN curl -L -o /usr/local/bin/amm-1.0.3-2.12 https://github.com/lihaoyi/Ammonite/releases/download/1.0.3/2.12-1.0.3 && \
  chmod +x /usr/local/bin/amm-1.0.3-2.12 && \
  /usr/local/bin/amm-1.0.3-2.12 --predef-code "sys.exit(0)"

ENTRYPOINT ["/usr/local/bin/amm-1.0.3-2.12"]

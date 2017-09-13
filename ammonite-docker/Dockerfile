FROM openjdk:8-jdk-alpine

# install curl
RUN apk update && \
  apk add bash ncurses curl ca-certificates && \
  rm -rf /var/cache/apk/*

# install ammonite
RUN curl -L -o /usr/local/bin/amm-2.11 https://git.io/v7cxd && \
  chmod +x /usr/local/bin/amm-2.11 && \
  /usr/local/bin/amm-2.11 --predef-code "sys.exit(0)"

ENTRYPOINT ["/usr/local/bin/amm-2.11"]

FROM mesosphere/openjdk-alpine-ammonite:1.1.2-2.11

COPY lib/ /lib/
COPY bin/ /bin/
COPY marathon.jar /marathon.jar

RUN amm-2.11 --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"

ENTRYPOINT ["/bin/storage-tool.sh"]

LIB_FILES := $(foreach f,$(wildcard src/1.4.x/lib/*),$(notdir $f))

.SECONDARY:

artifacts/marathon-%.tgz:
	mkdir -p artifacts/
	curl -f -o $@.tmp https://downloads.mesosphere.com/marathon/v$*/marathon-$*.tgz
	mv $@.tmp $@

targets/1.4.%/marathon.jar: artifacts/marathon-1.4.%.tgz
	mkdir -p targets/1.4.$*
	mkdir -p tmp/marathon-1.4.$*/; cd tmp/marathon-1.4.$*; tar xzf ../../artifacts/marathon-1.4.$*.tgz
	find tmp/marathon-1.4.$*/ -name "marathon-*.jar" -exec mv {} $@ \;
	rm -rf tmp/marathon-1.4.$*
	[ -f $@ ] && touch $@

targets/1.4.%/lib:
	mkdir -p $@

define copy_lib_template
targets/1.4.%/lib/$(1): targets/1.4.%/lib src/1.4.x/lib/$(1)
	cp src/1.4.x/lib/$(1) $$@
endef

$(foreach file,$(LIB_FILES),$(eval $(call copy_lib_template,$(file))))

targets/1.4.%/bin/storage-tool.sh: targets/1.4.%/lib src/1.4.x/bin/storage-tool.sh
	mkdir -p $(@D)
	cp src/1.4.x/bin/storage-tool.sh $@

targets/1.4.%/verified: targets/1.4.%/lib/bindings.sc targets/1.4.%/lib/load-jar.sc targets/1.4.%/lib/predef.sc targets/1.4.%/lib/dsl.sc targets/1.4.%/lib/helpers.sc  targets/1.4.%/marathon.jar
	cd targets/1.4.$*; amm-2.11 --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"
	touch $@

targets/1.4.%/Dockerfile: src/1.4.x/Dockerfile
	mkdir -p $(@D)
	cp $< $@

targets/%/docker-built: targets/%/verified targets/%/bin/storage-tool.sh targets/%/Dockerfile
	cd $(@D); docker build . -t mesosphere/marathon-storage-tool:$*
	touch $@

targets/%/docker-pushed: targets/%/docker-built
	docker push mesosphere/marathon-storage-tool:$*
	touch $@

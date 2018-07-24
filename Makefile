AMMONITE_212:=/usr/local/bin/amm-2.12
.SECONDARY:

# General

artifacts/marathon-1.4.%.tgz:
	mkdir -p artifacts/
	curl -f -o $@.tmp https://downloads.mesosphere.com/marathon/v1.4.$*/marathon-1.4.$*.tgz
	mv $@.tmp $@

artifacts/marathon-1.5.0.tgz:
	mkdir -p artifacts/
	curl -f -o $@.tmp https://downloads.mesosphere.com/marathon/v1.5.0/marathon-1.5.0.tgz
	mv $@.tmp $@

artifacts/marathon-1.5.1.tgz:
	mkdir -p artifacts/
	curl -f -o $@.tmp https://downloads.mesosphere.com/marathon/v1.5.1/marathon-1.5.1.tgz
	mv $@.tmp $@

artifacts/marathon-1.5.%.tgz:
	mkdir -p artifacts/
	curl -f -o $@.tmp https://downloads.mesosphere.io/marathon/releases/1.5.$*/marathon-1.5.$*.tgz
	mv $@.tmp $@

artifacts/marathon-1.6.%.tgz:
	mkdir -p artifacts/
	bash -x bin/download-marathon 1.6.$* $@.tmp
	mv $@.tmp $@

targets/%/lib:
	mkdir -p $@

targets/%/docker-built: targets/%/verified targets/%/Dockerfile
	cd $(@D); docker build . -t mesosphere/marathon-storage-tool:$*
	touch $@

targets/%/docker-pushed: targets/%/docker-built
	docker push mesosphere/marathon-storage-tool:$*
	touch $@

# 1.6.x
targets/1.6.%/marathon: artifacts/marathon-1.6.%.tgz
	mkdir -p targets/1.6.$*
	mkdir -p tmp/marathon-1.6.$*/; cd tmp/marathon-1.6.$*; tar xzf ../../artifacts/marathon-1.6.$*.tgz
	mv tmp/marathon-1.6.$*/marathon-1.6.* $@
	rm -rf tmp/marathon-1.6.$*
	touch $@

define copy_16_lib_template
targets/1.6.%/lib/$(1): targets/1.6.%/lib src/1.6.x/lib/$(1)
	cp src/1.6.x/lib/$(1) $$@
endef

$(foreach file,$(foreach f,$(wildcard src/1.6.x/lib/*),$(notdir $f)),$(eval $(call copy_16_lib_template,$(file))))

targets/1.6.%/bin/storage-tool.sh: targets/1.6.%/lib src/1.6.x/bin/storage-tool.sh
	mkdir -p $(@D)
	cp src/1.6.x/bin/storage-tool.sh $@

targets/1.6.%/verified: targets/1.6.%/bin/storage-tool.sh targets/1.6.%/lib/bindings.sc targets/1.6.%/lib/load-jar.sc targets/1.6.%/lib/predef.sc targets/1.6.%/lib/dsl.sc targets/1.6.%/lib/helpers.sc  targets/1.6.%/marathon
	cd targets/1.6.$*; $(AMMONITE_212) --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"
	touch $@

targets/1.6.%/Dockerfile: src/1.6.x/Dockerfile
	mkdir -p $(@D)
	cp $< $@



# 1.5.x
targets/1.5.%/marathon: artifacts/marathon-1.5.%.tgz
	mkdir -p targets/1.5.$*
	mkdir -p tmp/marathon-1.5.$*/; cd tmp/marathon-1.5.$*; tar xzf ../../artifacts/marathon-1.5.$*.tgz
	mv tmp/marathon-1.5.$*/marathon-1.5.* $@
	rm -rf tmp/marathon-1.5.$*
	touch $@

define copy_15_lib_template
targets/1.5.%/lib/$(1): targets/1.5.%/lib src/1.5.x/lib/$(1)
	cp src/1.5.x/lib/$(1) $$@
endef

$(foreach file,$(foreach f,$(wildcard src/1.5.x/lib/*),$(notdir $f)),$(eval $(call copy_15_lib_template,$(file))))

targets/1.5.%/bin/storage-tool.sh: targets/1.5.%/lib src/1.5.x/bin/storage-tool.sh
	mkdir -p $(@D)
	cp src/1.5.x/bin/storage-tool.sh $@

targets/1.5.%/verified: targets/1.5.%/bin/storage-tool.sh targets/1.5.%/lib/bindings.sc targets/1.5.%/lib/load-jar.sc targets/1.5.%/lib/predef.sc targets/1.5.%/lib/dsl.sc targets/1.5.%/lib/helpers.sc  targets/1.5.%/marathon
	cd targets/1.5.$*; amm-2.11 --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"
	touch $@

targets/1.5.%/Dockerfile: src/1.5.x/Dockerfile
	mkdir -p $(@D)
	cp $< $@


# 1.4.x
targets/1.4.%/marathon.jar: artifacts/marathon-1.4.%.tgz
	mkdir -p targets/1.4.$*
	mkdir -p tmp/marathon-1.4.$*/; cd tmp/marathon-1.4.$*; tar xzf ../../artifacts/marathon-1.4.$*.tgz
	find tmp/marathon-1.4.$*/ -name "marathon-*.jar" -exec mv {} $@ \;
	rm -rf tmp/marathon-1.4.$*
	[ -f $@ ] && touch $@

define copy_14_lib_template
targets/1.4.%/lib/$(1): targets/1.4.%/lib src/1.4.x/lib/$(1)
	cp src/1.4.x/lib/$(1) $$@
endef

$(foreach file,$(foreach f,$(wildcard src/1.4.x/lib/*),$(notdir $f)),$(eval $(call copy_14_lib_template,$(file))))

targets/1.4.%/bin/storage-tool.sh: targets/1.4.%/lib src/1.4.x/bin/storage-tool.sh
	mkdir -p $(@D)
	cp src/1.4.x/bin/storage-tool.sh $@

targets/1.4.%/verified: targets/1.4.%/bin/storage-tool.sh targets/1.4.%/lib/bindings.sc targets/1.4.%/lib/load-jar.sc targets/1.4.%/lib/predef.sc targets/1.4.%/lib/dsl.sc targets/1.4.%/lib/helpers.sc  targets/1.4.%/marathon.jar
	cd targets/1.4.$*; amm-2.11 --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"
	touch $@

targets/1.4.%/Dockerfile: src/1.4.x/Dockerfile
	mkdir -p $(@D)
	cp $< $@

AMMONITE_212:=/usr/local/bin/amm-2.12
.SECONDARY:

# General


artifacts/marathon-1.%.tgz:
	mkdir -p artifacts/
	bash -x bin/download-marathon 1.$* $@.tmp
	mv $@.tmp $@

targets/%/lib:
	mkdir -p $@

targets/%/docker-built: targets/%/verified targets/%/Dockerfile
	cd $(@D); docker build . -t mesosphere/marathon-storage-tool:$*
	docker run --rm -it mesosphere/marathon-storage-tool:$* --help | grep "Show help message"
	touch $@

targets/%/docker-pushed: targets/%/docker-built
	docker push mesosphere/marathon-storage-tool:$*
	touch $@

# 1.7.x
targets/1.7.%/marathon: artifacts/marathon-1.7.%.tgz
	mkdir -p targets/1.7.$*
	mkdir -p tmp/marathon-1.7.$*/; cd tmp/marathon-1.7.$*; tar xzf ../../artifacts/marathon-1.7.$*.tgz
	mv tmp/marathon-1.7.$*/marathon-1.7.* $@
	rm -rf tmp/marathon-1.7.$*
	touch $@

define copy_17_lib_template
targets/1.7.%/lib/$(1): targets/1.7.%/lib src/1.7.x/lib/$(1)
	cp src/1.7.x/lib/$(1) $$@
endef

$(foreach file,$(foreach f,$(wildcard src/1.7.x/lib/*),$(notdir $f)),$(eval $(call copy_17_lib_template,$(file))))

targets/1.7.%/bin/storage-tool.sh: targets/1.7.%/lib src/1.7.x/bin/storage-tool.sh
	mkdir -p $(@D)
	cp src/1.7.x/bin/storage-tool.sh $@

targets/1.7.%/verified: targets/1.7.%/bin/storage-tool.sh targets/1.7.%/lib/version.sc targets/1.7.%/lib/bindings.sc targets/1.7.%/lib/load-jar.sc targets/1.7.%/lib/predef.sc targets/1.7.%/lib/dsl.sc targets/1.7.%/lib/helpers.sc  targets/1.7.%/marathon
	cd targets/1.7.$*; $(AMMONITE_212) --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"
	touch $@

targets/1.7.%/Dockerfile: src/1.7.x/Dockerfile
	mkdir -p $(@D)
	cp $< $@



# 1.8.x
targets/1.8.%/marathon: artifacts/marathon-1.8.%.tgz
	mkdir -p targets/1.8.$*
	mkdir -p tmp/marathon-1.8.$*/; cd tmp/marathon-1.8.$*; tar xzf ../../artifacts/marathon-1.8.$*.tgz
	mv tmp/marathon-1.8.$*/marathon-1.8.* $@
	rm -rf tmp/marathon-1.8.$*
	touch $@

define copy_18_lib_template
targets/1.8.%/lib/$(1): targets/1.8.%/lib src/1.8.x/lib/$(1)
	cp src/1.8.x/lib/$(1) $$@
endef

$(foreach file,$(foreach f,$(wildcard src/1.8.x/lib/*),$(notdir $f)),$(eval $(call copy_18_lib_template,$(file))))

targets/1.8.%/bin/storage-tool.sh: targets/1.8.%/lib src/1.8.x/bin/storage-tool.sh
	mkdir -p $(@D)
	cp src/1.8.x/bin/storage-tool.sh $@

targets/1.8.%/verified: targets/1.8.%/bin/storage-tool.sh targets/1.8.%/lib/version.sc targets/1.8.%/lib/bindings.sc targets/1.8.%/lib/load-jar.sc targets/1.8.%/lib/predef.sc targets/1.8.%/lib/dsl.sc targets/1.8.%/lib/helpers.sc  targets/1.8.%/marathon
	cd targets/1.8.$*; $(AMMONITE_212) --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"
	touch $@

targets/1.8.%/Dockerfile: src/1.8.x/Dockerfile
	mkdir -p $(@D)
	cp $< $@

# 1.9.x
targets/1.9.%/marathon: artifacts/marathon-1.9.%.tgz
	mkdir -p targets/1.9.$*
	mkdir -p tmp/marathon-1.9.$*/; cd tmp/marathon-1.9.$*; tar xzf ../../artifacts/marathon-1.9.$*.tgz
	mv tmp/marathon-1.9.$*/marathon-1.9.* $@
	rm -rf tmp/marathon-1.9.$*
	touch $@

define copy_19_lib_template
targets/1.9.%/lib/$(1): targets/1.9.%/lib src/1.9.x/lib/$(1)
	cp src/1.9.x/lib/$(1) $$@
endef

$(foreach file,$(foreach f,$(wildcard src/1.9.x/lib/*),$(notdir $f)),$(eval $(call copy_19_lib_template,$(file))))

targets/1.9.%/bin/storage-tool.sh: targets/1.9.%/lib src/1.9.x/bin/storage-tool.sh
	mkdir -p $(@D)
	cp src/1.9.x/bin/storage-tool.sh $@

targets/1.9.%/verified: targets/1.9.%/bin/storage-tool.sh targets/1.9.%/lib/version.sc targets/1.9.%/lib/bindings.sc targets/1.9.%/lib/load-jar.sc targets/1.9.%/lib/predef.sc targets/1.9.%/lib/dsl.sc targets/1.9.%/lib/helpers.sc  targets/1.9.%/marathon
	cd targets/1.9.$*; $(AMMONITE_212) --predef lib/predef.sc --predef-code 'println("it worked"); sys.exit(0)' | grep "it worked"
	touch $@

targets/1.9.%/Dockerfile: src/1.9.x/Dockerfile
	mkdir -p $(@D)
	cp $< $@

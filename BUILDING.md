# Building

Right now, only 1.4.x versions of Marathon are supported.

Marathon storage tool must be linked against a specific version of Marathon.

For convenience, use the Makefile!:

```
make targets/1.4.5/docker-built
```

Make will automatically download the artifact from https://downloads.mesosphere.io/. If successful, you'll have a docker image tagged `mesosphere/marathon-storage-tool:1.4.5`. The code is compiled inside and outside of the docker container as a sanity check (and for performance).

If you have the credentials, you can push it to dockerhub:

```
make targets/1.4.5/docker-pushed
```

If you have made code changes and want to rebuild and repush, then run the following and go grab a coffee. And maybe lunch.

```
rm targets/*/docker-*
rm targets/*/verified
make targets/1.4.{0,1,2,3,4,5,6-snap1}/docker-pushed
```

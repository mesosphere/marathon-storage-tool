# Marathon Storage Tool

## About

For an overview, watch the video!

https://www.youtube.com/watch?v=laBSp6VttzI

Marathon-storage-tool supports all of the parameters that Marathon accepts for storage. For a permissive DC/OS cluster running DC/OS 1.4.5, you would launch it like this:

```
docker run --rm -it mesosphere/marathon-storage-tool:1.4.5 --zk zk://zk-1.zk,zk-2.zk,zk-3.zk:2181/marathon
```

Substitute 1.4.5 in the above command with the version of Marathon running. If the tool detects you are attaching to a Marathon cluster state that differs from the tool version, it will show an error and exit.

Some help documentation is shown at launch. This can be shown again with `help`

The tool launches with a fully functional Ammonite REPL, with all of the Marathon libraries loaded and available. An instance of StorageModule is presented as `module`.

Right now, only 1.4.x versions of Marathon are supported.

## Building

See `BUILDING.md`.

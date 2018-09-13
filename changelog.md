# Marathon Storage Tool Changes

## Since v0.1.0

### Migration Support

The `migrate()` command has been added to the DSL, allowing migrations to be performed.

### StorageVersion Validation is now Lazy

Previously, the tool would fail to launch if the storage version did not match the tool. Now, in order to support the migration (and other commands in the future, such as backup and restore), storage version is validated only during DSL commands that access storage entities, or when `module` is accessed.

## v0.0.0 to v0.1.0

- Start versioning Marathon storage tool

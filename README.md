# Ginger

A version control system written in Java.

## Requirements

- JDK 21+
- Maven 3.9+

## Build

```sh
mvn clean package
```

Produces `target/ginger-0.1.0.jar`.

## Run

```sh
java -jar target/ginger-0.1.0.jar --help
java -jar target/ginger-0.1.0.jar --version
java -jar target/ginger-0.1.0.jar init [directory]
```

Or without packaging:

```sh
mvn compile exec:java -Dexec.args="init"
```

## Project layout

```
src/main/java/dev/ginger/
  Main.java              CLI entry point
  cli/                   Argument parsing and command registry
  commands/              One class per subcommand (init, ...)
  repository/            Repository model and on-disk layout
  objects/               Content-addressed object storage
src/test/java/           JUnit 5 tests
```

## Repository format

`ginger init` creates a `.ginger` directory in the work tree:

```
.ginger/
  config         version=1
  HEAD           ref: refs/heads/main
  index          empty until files are staged
  objects/       content-addressed object store
  refs/heads/    one file per branch, created on the first commit
```

- `config` holds `key=value` lines. `version` is the repository format version; other versions are rejected.
- `HEAD` names the current branch as `ref: refs/heads/<branch>` followed by a newline.
- Commands locate the repository by walking up from the current directory to the nearest `.ginger`.
- Running `ginger init` in an existing valid repository changes nothing.

## Object format

Every object is stored as its type, a space, the payload length in decimal, a NUL byte, then the raw payload bytes:

```
<type> <length>\0<payload>
```

- `type` is `blob`, `tree` or `commit`.
- The object ID is the lowercase hex SHA-256 of that whole encoding.
- An object with ID `abcd...` is stored at `.ginger/objects/ab/cd...`.
- Objects are written to a temporary file and moved into place, and existing valid objects are reused.
- Reading an object re-hashes it and rejects any object whose content does not match its ID.

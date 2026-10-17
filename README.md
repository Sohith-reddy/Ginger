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
java -jar target/ginger-0.1.0.jar add <path>...
java -jar target/ginger-0.1.0.jar commit -m "message"
java -jar target/ginger-0.1.0.jar status
java -jar target/ginger-0.1.0.jar log
java -jar target/ginger-0.1.0.jar diff [--staged]
java -jar target/ginger-0.1.0.jar restore [--staged] [--force] <path>...
java -jar target/ginger-0.1.0.jar checkout [--force] <commit-id>
java -jar target/ginger-0.1.0.jar branch [<name>]
java -jar target/ginger-0.1.0.jar switch [--force] <branch>
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
  objects/               Content-addressed objects, trees and commits
  index/                 Staging area
  refs/                  HEAD and branch references
  worktree/              Safe access to files in the work tree
  ops/                   Staging, committing, restoring and checkout
  status/                Working tree status
  diff/                  Line diffs
src/test/java/           JUnit 5 tests
```

## Repository format

`ginger init` creates a `.ginger` directory in the work tree:

```
.ginger/
  config         version=1
  HEAD           ref: refs/heads/main
  index          staged files
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

### Trees and commits

A tree lists its entries sorted by name. Each entry is `<type> <name>`, a NUL byte, then the 32-byte raw object ID, where `type` is `blob` or `tree`.

A commit is UTF-8 text:

```
tree <id>
parent <id>          zero or more
author <name> <email>
date <ISO-8601 UTC timestamp>

<message>
```

The author comes from `GINGER_AUTHOR_NAME` / `GINGER_AUTHOR_EMAIL`, then `user.name` / `user.email` in `.ginger/config`, then the system user name.

A branch file in `refs/heads/` holds a commit ID followed by a newline. A detached `HEAD` holds a commit ID followed by a newline instead of a `ref:` line. Commit and tree objects are written before the branch moves, and refs are replaced atomically.

## Index format

The index is a binary file. An empty file means nothing is staged.

```
"GIDX"                 4-byte signature
version                4-byte big-endian integer, currently 1
count                  4-byte big-endian integer
count entries, sorted by path:
  path length          4-byte big-endian integer
  path                 UTF-8, repository-relative, `/` separators
  object ID            32 raw bytes
checksum               SHA-256 of everything above
```

## Commands

- `add <path>...` stages files relative to the current directory. A directory stages every file under it, and tracked files that no longer exist are removed from the index. Symbolic links and `.ginger` are never staged.
- `commit -m <message>` records the index as a new commit. It refuses an empty message or a tree identical to `HEAD`.
- `status` compares `HEAD`, the index and the work tree separately.
- `log` follows first parents from `HEAD`, newest first.
- `diff` compares the work tree with the index. `diff --staged` compares the index with `HEAD`. Binary files and files over 8 MiB are reported without a line diff.
- `restore <path>...` copies the staged version of tracked files back into the work tree. It refuses to overwrite a file with unstaged changes unless `--force` is given.
- `restore --staged <path>...` resets index entries to `HEAD`, leaving the work tree alone. Files that are not in `HEAD` are unstaged.
- `checkout <commit-id>` moves to a commit with a detached `HEAD`. A unique prefix of at least 4 characters is accepted.
- `branch` lists branches with `*` on the current one. `branch <name>` creates a branch at the current commit; it fails before the first commit, for duplicates, and for names other than letters, digits, `.`, `_` and `-`.
- `switch <branch>` moves to a branch and attaches `HEAD` to it.

`checkout` and `switch` only touch files that differ between the current commit and the target. Local changes to other files are carried over. They refuse to run if a changed file has staged or unstaged edits, or if an untracked file is in the way; `--force` discards those edits instead. Files are written through a temporary file, symbolic links are never followed, and `HEAD` and the index are only updated after every file has been written. A filesystem cannot replace a whole work tree atomically, so if a write fails partway the work tree can be partially updated while `HEAD` still points at the old commit; rerunning the command with `--force` completes it.

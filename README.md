# Ginger

Ginger is a local version control system written in Java. It is inspired by Git but is not compatible with Git repositories or the Git command line.

## Features

| Area | Status |
|---|---|
| Repository init and discovery from subdirectories | Done |
| Content-addressed object storage (SHA-256) | Done |
| Staging with a persistent index | Done |
| Commits, trees and history | Done |
| `status`, `log`, `diff` | Done |
| `restore`, `checkout` | Done |
| Branches and `switch` | Done |
| `.gingerignore` | Done, limited syntax |
| Three-way merge with conflict markers | Done |
| Remotes, push, pull, rebase, tags, stash | Not supported |

## Requirements

- JDK 21 or later
- Maven 3.9 or later

## Build and test

```sh
mvn clean test
mvn clean package
java -jar target/ginger-0.1.0.jar --help
```

`mvn clean package` produces `target/ginger-0.1.0.jar`.

## Windows launcher

`ginger.bat` in the project root runs the packaged JAR so you can type `ginger` from any directory.

1. Build the JAR with `mvn clean package`.
2. Add the project folder (the one containing `ginger.bat`) to your user `Path`: Start → "Edit environment variables for your account" → `Path` → New.
3. Open a new terminal and run `ginger --help`.

The launcher passes all arguments through unchanged, so quoted messages and paths with spaces work, and it returns Ginger's exit code.

Troubleshooting:

- `Error: Ginger JAR not found` — run `mvn clean package` in the project folder. If you change the version in `pom.xml`, update the JAR name in `ginger.bat` too.
- `java was not found on PATH` — install JDK 21 or later and make sure `java -version` works in a new terminal.
- `'ginger' is not recognized` — the folder is not on `Path`, or the terminal was opened before `Path` was changed.

On macOS and Linux, use an alias instead:

```sh
alias ginger='java -jar /path/to/Ginger/target/ginger-0.1.0.jar'
```

## Example

Run this in a scratch folder, not inside the Ginger source tree.

```sh
mkdir demo && cd demo
ginger init
echo "hello" > README.md
ginger add README.md
ginger commit -m "Initial commit"

ginger branch feature
ginger switch feature
echo "feature work" > feature.txt
ginger add feature.txt
ginger commit -m "Add feature"

ginger switch main
ginger merge feature
ginger log
```

## Commands

| Command | Description |
|---|---|
| `ginger init [<directory>]` | Create a repository. Running it again on a valid repository changes nothing. |
| `ginger add <path>...` | Stage files, or every file under a directory. Paths are relative to the current directory. |
| `ginger commit -m <message>` | Record the staged files as a new commit. |
| `ginger status` | Show staged, unstaged, untracked and unmerged files. |
| `ginger log` | Show history from `HEAD`, newest first, following first parents. |
| `ginger diff [--staged]` | Compare the work tree with the index, or the index with `HEAD`. |
| `ginger restore [--force] <path>...` | Copy the staged version of tracked files back into the work tree. |
| `ginger restore --staged <path>...` | Reset index entries to `HEAD` without touching the work tree. |
| `ginger checkout [--force] <commit-id>` | Move to a commit with a detached `HEAD`. Accepts a unique prefix of 4 or more characters. |
| `ginger branch [<name>]` | List branches, or create one at the current commit. |
| `ginger switch [--force] <branch>` | Move to a branch. |
| `ginger merge <branch>` | Merge a branch into the current one. |
| `ginger merge --abort` | Abandon a merge that stopped with conflicts. |

Global options: `--help`, `--version`, `--debug`. Every command also accepts `--help`.

Exit codes: `0` success, `1` unknown command or nothing to do (for example `nothing to commit` or a conflicted merge), `2` invalid arguments, `128` the command failed.

### Behaviour details

- **add**: symbolic links, paths through symbolic links and anything inside `.ginger` are never staged. Adding a tracked file that no longer exists stages its deletion. If any path fails, the index is left unchanged.
- **commit**: refuses an empty message and a tree identical to `HEAD`. The author is `GINGER_AUTHOR_NAME` / `GINGER_AUTHOR_EMAIL`, then `user.name` / `user.email` in `.ginger/config`, then the system user name.
- **diff**: binary files, files over 8 MiB, and changes needing more than 2000 line edits are reported without a line diff.
- **restore** refuses to overwrite a file with unstaged changes unless `--force` is given.
- **checkout** and **switch** only touch files that differ between the current commit and the target, and carry other local changes along. They refuse if a file they would change has staged or unstaged edits, or if an untracked file is in the way. `--force` discards those edits instead.
- **branch** names may contain letters, digits, `.`, `_` and `-`. Branches cannot be created before the first commit.

### Merging

`ginger merge <branch>` requires a clean index and work tree for tracked files.

- If the branch is already contained in `HEAD`, nothing happens.
- If `HEAD` is an ancestor of the branch, it fast-forwards.
- Otherwise Ginger finds the merge base and merges each file three ways. Changes that do not overlap are combined automatically, and a clean result is committed with two parents.

A conflict stops the merge without moving any branch. Conflicts are reported for:

- `content`: both sides changed the same lines of a text file. The file gets `<<<<<<<`, `=======` and `>>>>>>>` markers.
- `add/add`: both sides added the same path with different content. Markers as above.
- `modify/delete` or `delete/modify`: one side changed a file the other deleted. The changed version is left in the work tree.
- `binary`: both sides changed a binary file. Our version is kept.

To finish, edit each file, run `ginger add <path>` (or delete it and `ginger add <path>` to accept the deletion), then `ginger commit -m <message>`. `ginger status` lists the remaining unmerged paths, and `ginger diff` shows the markers. `ginger merge --abort` resets tracked files and the index to `HEAD`. While a merge is in progress, `checkout`, `switch` and another `merge` are refused.

## Ignoring files

A `.gingerignore` file in the repository root lists patterns, one per line:

| Pattern | Matches |
|---|---|
| `# text` or a blank line | Nothing |
| `*.log` | Any file or directory named like this, at any depth |
| `build/` | Directories named `build` at any depth, and everything inside them |
| `/secret.txt` or `docs/draft.md` | That path relative to the repository root, and everything inside it if it is a directory |

`*` matches any characters except `/`, and `?` matches one character except `/`. Negation (`!`), `**` and character classes are not supported and are reported as errors.

Ignore rules only affect `ginger add` on a directory and the untracked list in `ginger status`. Naming an ignored file directly in `ginger add` stages it, and tracked files are always updated regardless of the rules. `.ginger` is always excluded.

## Repository layout

```
.ginger/
  config            key=value lines; version=1
  HEAD              ref: refs/heads/<branch>, or a commit ID when detached
  index             staged files
  objects/ab/cd...  objects, named by their ID
  refs/heads/       one file per branch holding a commit ID
  MERGE_HEAD        only during a conflicted merge
  MERGE_CONFLICTS   only during a conflicted merge
```

Every metadata file is written to a temporary file and moved into place, so a failed write never leaves a half-written file. Malformed metadata is reported with the file's path. An unsupported `version` in `config` is rejected.

### Objects

Every object is stored as its type, a space, the payload length in decimal, a NUL byte, then the payload:

```
<type> <length>\0<payload>
```

The object ID is the lowercase hex SHA-256 of that whole encoding. Reading an object re-hashes it and rejects content that does not match its ID.

- **blob**: the exact file bytes.
- **tree**: entries sorted by name, each `<blob|tree> <name>`, a NUL byte, then the 32-byte raw ID.
- **commit**: UTF-8 text:

  ```
  tree <id>
  parent <id>          zero, one, or two for a merge
  author <name> <email>
  date <ISO-8601 UTC timestamp>

  <message>
  ```

### Index

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

An empty file means nothing is staged. If the index is reported as corrupt, replace it with an empty file and run `ginger restore --staged .` to rebuild it from `HEAD`.

## Debugging

Errors are printed as one `fatal:` line on standard error. Add `--debug` before the command, or set the environment variable `GINGER_DEBUG=1`, to also print the Ginger, Java and OS versions, the command name and a stack trace. File contents are never printed.

```sh
ginger --debug status
```

## Known limitations

- Not compatible with Git repositories or Git's command line.
- Only regular files are tracked. Symbolic links, empty directories and file permissions such as the executable bit are not recorded.
- Objects are stored uncompressed, and files are read fully into memory.
- `checkout`, `switch` and `merge` write files one at a time. A filesystem cannot replace a whole work tree atomically, so if a write fails partway the work tree can be partially updated while `HEAD` still points at the old commit. Rerunning the command with `--force` completes a checkout or switch. After a failed merge, `ginger restore --staged .` followed by `ginger restore --force .` returns tracked files to `HEAD`.
- `log` follows first parents only.
- With criss-cross history that has several equally good merge bases, the most recent one is used.
- File names that the operating system cannot represent in its file-name encoding cannot be stored. On Linux and macOS, run with a UTF-8 locale.

## Project layout

```
src/main/java/dev/ginger/
  Main.java      CLI entry point
  cli/           Argument parsing and command registry
  commands/      One class per command
  repository/    Repository init, discovery and validation
  objects/       Objects, trees, commits and the object store
  index/         Staging area
  refs/          HEAD and branches
  worktree/      Safe work-tree access and ignore rules
  ops/           Staging, committing, restore, checkout and merge
  status/        Status calculation
  diff/          Line diffs
  merge/         Merge base, three-way text merge and merge state
  util/          Atomic file writes
src/test/java/   JUnit 5 tests
ginger.bat       Windows launcher
```

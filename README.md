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
src/test/java/           JUnit 5 tests
```

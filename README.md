# Ginger

A version control system written in Java.

## Requirements

- JDK 21+
- Maven 3.9+

## Build

```sh
mvn clean package
```

Produces `target/ginger.jar`.

## Run

```sh
java -jar target/ginger.jar help
java -jar target/ginger.jar init [directory]
```

Or without packaging:

```sh
mvn compile exec:java -Dexec.args="init"
```

## Project layout

```
src/main/java/com/ginger/
  Ginger.java            CLI entry point and command dispatch
  command/               One class per subcommand (init, ...)
  repository/            Repository model and on-disk layout
src/test/java/           JUnit 5 tests
```

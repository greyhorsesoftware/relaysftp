# Contributing

Thanks for looking at RelaySFTP. Bug reports and pull requests are welcome.

## Before a large change

Open an issue first so we can agree on the approach. Small fixes can go straight to a pull
request.

## Building

Requires Java 21 or newer; the build uses a Java 25 toolchain that Gradle provisions.

```sh
./gradlew clean build      # compiles and runs the unit and integration tests, headless
./gradlew run              # runs the app with a throwaway data folder in build/dev-data
```

`./gradlew run` never touches your real RelaySFTP settings, bookmarks or keychain entries.
Running `java -jar build/libs/RelaySFTP.jar` does use the real data folder.

## Tests

There are three tiers:

| Command | What it runs | Needs |
|---|---|---|
| `./gradlew build` | unit and integration tests (embedded SSHD, FTP and S3 servers) | nothing |
| `./gradlew uiTest` | AssertJ Swing tests that drive real windows | a desktop; do not touch the keyboard or mouse while it runs |
| `scripts/ui-test-loop.sh` | repeats the UI suite to find flaky tests | a desktop |

CI runs the first tier. Please run `uiTest` locally before opening a pull request that touches
the UI.

## Code layout

See `CLAUDE.md` for the package map and conventions. In short: `gcore` holds connections and
protocol plugins, `gja` the job queue, `gideon/*` the Swing UI.

Do not modify the protocol plugins without understanding the threading model: protocol calls run
on job-queue worker threads, never on the event-dispatch thread.

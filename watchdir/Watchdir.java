///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.1 info.picocli:picocli-codegen:4.6.1
//DEPS org.fusesource.jansi:jansi:2.3.2
//DEPS io.methvin:directory-watcher:0.15.0
//DEPS org.slf4j:slf4j-simple:1.7.9

//JAVA 16

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import io.methvin.watcher.DirectoryWatcher;
import org.fusesource.jansi.Ansi;

import static org.fusesource.jansi.Ansi.ansi;

import static java.lang.System.out;

import java.io.IOException;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;

@Command(name = "watchdir", mixinStandardHelpOptions = true, version = "watchdir 0.1", description = "watchdir made with jbang")
class Watchdir implements Callable<Integer> {
    @Option(names = "-v")
    boolean verbose;

    @Option(names = "-c")
    boolean cleanScrean;

    @Option(names = { "-i", "--include" }, description = "glob formatte")
    String include;

    @Parameters
    List<String> params;

    public static void main(String... args) {

        int exitCode = new CommandLine(new Watchdir()).execute(args);
        System.exit(exitCode);
    }

    Ansi ansi = Ansi.ansi();

    @Override
    public Integer call() throws Exception {

        Path path = Paths.get(".");

        final PathMatcher includePattern;
        if (include != null) {
            // glob matcher
            includePattern = FileSystems.getDefault().getPathMatcher("glob:" + include);
        } else {
            includePattern = null;
        }

        var watcher = DirectoryWatcher.builder().path(path) // or use paths(directoriesToWatch)
                .listener(event -> {

                    if (verbose) {
                        System.err.println("type:" + event.eventType() + " " + event.path());
                    }
                    if (shouldRun(includePattern, event.path())) {
                        runProcess();
                    }

                }).fileHashing(false) // defaults to true
                // .logger(logger) // defaults to
                // LoggerFactory.getLogger(DirectoryWatcher.class)
                // .watchService(FileSystems.getDefault().newWatchService()) // defaults based
                // on OS to either JVM
                // WatchService or the JNA macOS WatchService
                .build();

        watcher.watch();

        return 0;

    }

    private void runProcess() {
        try {
            if (cleanScrean) {
                out.println(ansi.cursor(0, 0));
                out.println(ansi.eraseScreen());
            }

            ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", String.join(" ", params));
            builder.inheritIO();
            Process p = builder.start();
            p.waitFor();
            if (!cleanScrean) {
                out.println();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean shouldRun(PathMatcher includePattern, Path filename) {
        if (includePattern != null) {
            return includePattern.matches(filename);
        } else {
            return true;
        }
    }
}

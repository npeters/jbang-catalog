///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS info.picocli:picocli:4.5.0 info.picocli:picocli-codegen:4.6.1
//DEPS org.apache.maven.plugins:maven-shade-plugin:3.2.4
//JAVA 16


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;

import org.apache.maven.plugins.shade.filter.Filter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;

import org.apache.maven.plugins.shade.DefaultShader;
import org.apache.maven.plugins.shade.ShadeRequest;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ManifestResourceTransformer;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.codehaus.plexus.logging.console.ConsoleLogger;

@Command(name = "JarShader", mixinStandardHelpOptions = true, version = "JarShader 0.1", description = """

        Build a jar shader with maven-shade-plugin

        exemple:
          jbang export portable JarShader.java
          jbang run JarShader.java -e -O jarshader  JarShader.jar
        """

)
class JarShader implements Callable<Integer> {

    @Parameters(index = "0", description = "main jar")
    private String mainJar;

    @Option(names = { "-l", "--libs" }, description = "libs with jars Default: ${DEFAULT-VALUE}", defaultValue = "libs")
    private File libsPathFile;

    @Option(names = { "-e",
            "--execute-jar" }, description = "execute jar https://skife.org/java/unix/2011/06/20/really_executable_jars.html")
    private boolean exec;

    @Option(names = { "-O", "--output" }, description = "outputFile")
    private File outputFile;

    public static void main(String... args) {
        int exitCode = new CommandLine(new JarShader()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...

        DefaultShader shader = new DefaultShader();

        final ConsoleLogger logs = new ConsoleLogger(org.codehaus.plexus.logging.Logger.LEVEL_INFO, "shader");
        shader.enableLogging(logs);

        File[] libs = libsPathFile.listFiles((filename) -> filename.getName().endsWith(".jar"));

        final Set<File> classpath;
        if (libs == null) {
            classpath = new LinkedHashSet<>();
        } else {
            classpath = new LinkedHashSet<>(Arrays.asList(libs));
        }

        File mainJarFile = new File(mainJar);
        classpath.add(mainJarFile);

        final ShadeRequest shadeRequest = new ShadeRequest();
        shadeRequest.setJars(classpath);
        shadeRequest.setRelocators(Collections.<Relocator>emptyList());
        shadeRequest.setResourceTransformers(Collections.<ResourceTransformer>emptyList());
        shadeRequest.setFilters(Collections.<Filter>emptyList());

        String mainClass = new JarFile(mainJar).getManifest().getMainAttributes().getValue("Main-Class");

        ManifestResourceTransformer manifestResourceTransformer = new ManifestResourceTransformer();
        manifestResourceTransformer.setMainClass(mainClass);
        shadeRequest.setResourceTransformers(List.of(manifestResourceTransformer));

        if (outputFile == null) {
            new File("target").mkdirs();
            outputFile = new File(mainJarFile.getParent(),
                    "target/" + mainJarFile.getName().replace(".jar", "-uber.jar"));
        }

        Path outputPath = outputFile.toPath();

        shadeRequest.setUberJar(outputFile.getAbsoluteFile());
        shader.shade(shadeRequest);

        if (exec) {
            // doc https://skife.org/java/unix/2011/06/20/really_executable_jars.html
            var script = """
                    #!/bin/sh

                    exec java -jar $0 "$@"
                    """.getBytes("UTF-8");

            var fileBytes = Files.readAllBytes(outputPath);
            var tmp = File.createTempFile("shader", "tmp").toPath();

            byte[] newBytes = new byte[script.length + fileBytes.length];
            System.arraycopy(script, 0, newBytes, 0, script.length);
            System.arraycopy(fileBytes, 0, newBytes, script.length, fileBytes.length);

            Files.write(tmp, newBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            Files.move(tmp, outputPath, StandardCopyOption.REPLACE_EXISTING);

            var perms = new HashSet<>(Files.getPosixFilePermissions(outputPath));

            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(outputPath, perms);
        }

        return 0;
    }
}

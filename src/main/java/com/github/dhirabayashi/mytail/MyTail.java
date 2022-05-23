package com.github.dhirabayashi.mytail;

import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "mytail", mixinStandardHelpOptions = true, version = "mytail 0.1",
        description = "display the last part of a file")
public class MyTail implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The file for display.")
    private File file;

    @CommandLine.Option(names = {"-n", "--lines"}, description = "The location is number lines.")
    private int numberLines = 10;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MyTail()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // 行数を数える
        long lineCount;
        try(var lines = Files.lines(file.toPath())) {
            lineCount = lines.count();
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }

        try(var br = Files.newBufferedReader(file.toPath())) {
            long restCount = lineCount;
            String line;
            while ((line = br.readLine()) != null) {
                if(restCount <= numberLines) {
                    System.out.println(line);
                }
                restCount--;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }
}

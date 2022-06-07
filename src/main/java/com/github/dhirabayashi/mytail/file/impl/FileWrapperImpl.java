package com.github.dhirabayashi.mytail.file.impl;

import com.github.dhirabayashi.mytail.file.api.FileWrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * ファイルIO読み取り実装
 */
public class FileWrapperImpl implements FileWrapper {
    @Override
    public Stream<String> lines(Path path) throws IOException {
        return Files.lines(path);
    }
}

package com.github.dhirabayashi.mytail.file.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * ファイルIOを切り出す
 */
public interface FileWrapper {
    /**
     * テキストファイルの行単位のストリームを返す。呼び出し元でclose()を呼び出す必要がある。
     * @param path 読み取るファイルのパス
     * @return テキストファイルの行単位のストリーム
     */
    Stream<String> lines(Path path) throws IOException;
}

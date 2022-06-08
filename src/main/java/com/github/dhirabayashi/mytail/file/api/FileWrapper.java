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

    /**
     * 読み取り用ファイルチャンネルを開く
     * @param path 対象ファイルのパス
     * @return ファイルチャンネル
     * @throws IOException 入出力エラーが発生した場合
     */
    FileChannelWrapper open(Path path) throws IOException;
}

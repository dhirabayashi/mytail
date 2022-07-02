package com.github.dhirabayashi.mytail.file.api;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * ファイルチャンネルの操作を切り出す
 */
public interface FileChannelWrapper extends Closeable {
    /**
     * チャンネルのサイズを取得する
     * @return サイズ
     * @throws IOException 入出力エラーが発生した場合
     */
    long size() throws IOException;

    /**
     * ファイルの読み取り位置を設定する
     * @param newPosition 設定する位置
     * @throws IOException 入出力エラーが発生した場合
     */
    void position(long newPosition) throws IOException;

    /**
     * ファイルの現在の位置を返す
     * @return ファイルの現在の位置
     * @throws IOException 入出力エラーが発生した場合
     */
    long position() throws IOException;

    /**
     * ファイルの内容を読み取ってバッファに設定する
     * @param dst 内容を設定するバッファ
     * @return バッファ
     * @throws IOException 入出力エラーが発生した場合
     */
    ByteBuffer read(ByteBuffer dst) throws IOException;
}

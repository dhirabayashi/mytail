package com.github.dhirabayashi.mytail.file.impl;

import com.github.dhirabayashi.mytail.file.api.FileChannelWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;

/**
 * ファイルチャンネルの実装
 */
public class FileChannelWrapperImpl implements FileChannelWrapper {

    /**
     * ファイルチャンネルの実体
     */
    private final FileChannel channel;

    /**
     * パスを指定してこのインスタンスを構築する
     * @param path ファイルのパス
     * @throws IOException 入出力エラーが発生した場合
     */
    public FileChannelWrapperImpl(Path path) throws IOException {
        this.channel = FileChannel.open(path, READ);
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public void position(long newPosition) throws IOException {
        channel.position(newPosition);
    }

    @Override
    public long position() throws IOException {
        return channel.position();
    }

    @Override
    public ByteBuffer read(ByteBuffer dst) throws IOException {
        channel.read(dst);
        return dst;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}

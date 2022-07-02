package com.github.dhirabayashi.mytail;

import com.github.dhirabayashi.mytail.file.api.FileChannelWrapper;
import com.github.dhirabayashi.mytail.file.api.FileWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MyTailTest {
    @Mock
    private FileWrapper wrapper;

    @InjectMocks
    private MyTail sut;

    private AutoCloseable closeable;

    private final String text = """
                        aaa
                        bbbb
                        ccccc
                        dddddd
                        fffffff
                        gggggggg
                        """;

    @BeforeEach
    private void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    private void tearDown() throws Exception {
        closeable.close();
    }

    private Path setUpFile(Path tmpDir) throws IOException {
        String filename = "test.txt";
        Path tmpFilePath = tmpDir.resolve(filename);
        Files.writeString(tmpFilePath, text);

        doReturn(text.lines()).when(wrapper).lines(tmpFilePath);

        return tmpFilePath;
    }

    @Test
    void test_readLines(@TempDir Path tmpDir) throws IOException {
        // 準備
        var tmpFilePath = setUpFile(tmpDir);

        // 表示する行数
        int numberLines = 2;

        // 先頭2行のうち (長いほうの長さ + 改行コード分) * 2 * 2
        // (4 + 1) * 2 * 2 = 20
        var expectedByteBuffer = ByteBuffer.allocate(20);

        // 読み込み後のバッファ
        // 行数が同じだと続行するロジック回避のため一行余分に読み込む
        var returnedByteBuffer = ByteBuffer.allocate(20);
        returnedByteBuffer.put("ddd\nfffffff\ngggggggg".getBytes(StandardCharsets.UTF_8));

        long size = text.length();
        try(var fileChannelWrapper = Mockito.spy(FileChannelWrapper.class)) {
            doReturn(size).when(fileChannelWrapper).size();
            doReturn(returnedByteBuffer).when(fileChannelWrapper).read(expectedByteBuffer);

            doReturn(fileChannelWrapper).when(wrapper).open(tmpFilePath);

            // 実行
            var actual = sut.readByLineNum(tmpFilePath.toFile(), numberLines);

            // 検証
            verify(fileChannelWrapper, times(1)).read(expectedByteBuffer);
            assertEquals(List.of("fffffff", "gggggggg"), actual.getLeft());
            assertEquals(0, actual.getRight());
        }
    }

    @Test
    void test_readBytes(@TempDir Path tmpDir) throws IOException {
        // 準備
        var tmpFilePath = setUpFile(tmpDir);

        // バイト数
        var byteLength = 12;
        // バイトバッファ
        var expectedByteBuffer = ByteBuffer.allocate(byteLength);
        // 読み込み後のバッファ
        var returnedByteBuffer = ByteBuffer.allocate(byteLength);
        returnedByteBuffer.put("fff\ngggggggg".getBytes(StandardCharsets.UTF_8));

        long size = text.length();
        try(var fileChannelWrapper = Mockito.spy(FileChannelWrapper.class)) {
            doReturn(size).when(fileChannelWrapper).size();
            doReturn(returnedByteBuffer).when(fileChannelWrapper).read(expectedByteBuffer);

            doReturn(fileChannelWrapper).when(wrapper).open(tmpFilePath);

            // 実行
            var actual = sut.readByByteSize(tmpFilePath.toFile(), byteLength);

            // 検証
            verify(fileChannelWrapper, times(1)).read(expectedByteBuffer);
            assertEquals(List.of("fff\ngggggggg"), actual.getLeft());
            assertEquals(0, actual.getRight());
        }
    }
}
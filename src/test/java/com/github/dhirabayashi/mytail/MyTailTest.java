package com.github.dhirabayashi.mytail;

import com.github.dhirabayashi.mytail.file.api.FileWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MyTailTest {
    @Mock
    private FileWrapper wrapper;

    @InjectMocks
    private MyTail sut;

    private AutoCloseable closeable;

    @BeforeEach
    private void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    private void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void test_notExist() {
        // 実行
        var ret = sut.readLines(new File("not_exists"));

        // 検証
        assertEquals(Collections.emptyList(), ret.getLeft());
        assertEquals(1, ret.getRight());
    }
}
package com.github.dhirabayashi.mytail;

import com.github.dhirabayashi.mytail.file.api.FileWrapper;
import com.github.dhirabayashi.mytail.file.impl.FileWrapperImpl;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "mytail", mixinStandardHelpOptions = true, version = "mytail 0.1",
        description = "display the last part of a file")
public class MyTail implements Callable<Integer> {
    /**
     * 読み取り対象のファイル
     */
    @CommandLine.Parameters(description = "The file for display.")
    private List<File> files;

    /**
     * ファイルの読み取り行数
     */
    @CommandLine.Option(names = {"-n", "--lines"}, description = "The location is number lines.")
    private int numberLines = 10;

    /**
     * ファイル読み取り部分を扱うインスタンス
     */
    private final FileWrapper fileWrapper;

    /**
     * ファイルラッパーを指定してMyTailを構築する
     * @param fileWrapper ファイル読み取り部分を扱うインスタンス
     */
    public MyTail(FileWrapper fileWrapper) {
        this.fileWrapper = fileWrapper;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MyTail(new FileWrapperImpl())).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        int wholeExitCode = 0;
        int length = files.size();
        for(int i = 0; i < length; i++) {
            var file = files.get(i);

            // ファイル名の表示
            if(this.files.size() != 1) {
                System.out.printf("==> %s <==%n", file.toString());
            }

            // 実行
            var pair = readLines(file);

            // 表示
            var lines = pair.getLeft();
            lines.forEach(System.out::println);

            int exitCode = pair.getRight();

            // 終了コードが0でないことが一度でもあれば、全体の終了コードは0以外
            if(exitCode != 0) {
                wholeExitCode = exitCode;
            }

            if(i != length - 1) {
                System.out.println();
            }
        }
        return wholeExitCode;
    }

    /**
     * ファイルから表示する行を取得する
     * @param file ファイルパス
     * @return 行と終了コードの組
     */
    Pair<List<String>, Integer> readLines(File file) {
        if(!file.exists()) {
            System.err.printf("mytail: %s: No such file or directory\n", file);
            return Pair.of(Collections.emptyList(), 1);
        }

        // 先頭から全部読むと遅いため、適当な位置までスキップしてそれ以降から読み取る
        try(var fc = fileWrapper.open(file.toPath())) {
            // スキップ位置の推測
            var byteSize = inferByteSize(file);

            var coefficient = 1;
            while(true) {
                byteSize *= coefficient;
                // ファイルサイズより大きくなってしまったらファイル全体が読まれるように調整
                var displayWhole = false;
                if(byteSize > fc.size()) {
                    byteSize = (int)fc.size();
                    displayWhole = true;
                }

                // バッファ
                var buffer = ByteBuffer.allocate(byteSize);

                // スキップ
                fc.position(fc.size() - byteSize);

                // 読み取り
                fc.read(buffer);

                // 出力
                var tmpLines = new String(buffer.array(), StandardCharsets.UTF_8).lines().toList();

                var displayLines = tmpLines.stream()
                        .skip(Math.max(0, tmpLines.size() - numberLines))
                        .toList();

                // 足りていれば出力（行数が同じの場合、最初の行が完全でない可能性があるため足りないとみなす）
                if(displayWhole || tmpLines.size() > displayLines.size()) {
                    return Pair.of(displayLines, 0);
                } else {
                    // 足りなかったら係数を増やしてやり直す
                    coefficient++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Pair.of(Collections.emptyList(), 1);
        }
    }

    /**
     * 末尾を読み取る際の参考となる推測バイト数を返す。先頭n行を読み込み、そのうち一番長かった行のバイト数 × nを返す
     * @param file ファイルパス
     * @return 末尾の推定バイト数
     * @throws IOException ファイル読み取り時にエラーが発生した場合
     */
    private int inferByteSize(File file) throws IOException {
        try(var lines = fileWrapper.lines(file.toPath())) {
            var maxByteSize = lines
                    .limit(numberLines)
                    .map(line -> line.getBytes(StandardCharsets.UTF_8).length + 1) // 1を足すのは改行コード分
                    .max(Comparator.naturalOrder())
                    .orElse(0);

            // 念の為2倍にして返す
            return maxByteSize * numberLines *  2;
        }
    }
}

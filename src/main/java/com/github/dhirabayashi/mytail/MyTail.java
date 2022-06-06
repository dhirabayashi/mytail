package com.github.dhirabayashi.mytail;

import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import static java.nio.file.StandardOpenOption.READ;

@CommandLine.Command(name = "mytail", mixinStandardHelpOptions = true, version = "mytail 0.1",
        description = "display the last part of a file")
public class MyTail implements Callable<Integer> {
    @CommandLine.Parameters(description = "The file for display.")
    private List<File> files;

    @CommandLine.Option(names = {"-n", "--lines"}, description = "The location is number lines.")
    private int numberLines = 10;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MyTail()).execute(args);
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
            int exitCode = execute(file);
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
     * 処理本体
     * @param file ファイルパス
     * @return 終了コード
     */
    private int execute(File file) {
        if(!file.exists()) {
            System.err.printf("mytail: %s: No such file or directory\n", file);
            return 1;
        }

        // 先頭から全部読むと遅いため、適当な位置までスキップしてそれ以降から読み取る
        try(var fc = FileChannel.open(file.toPath(), READ)) {
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
                    displayLines.forEach(System.out::println);
                    break;
                } else {
                    // 足りなかったら係数を増やしてやり直す
                    coefficient++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    /**
     * 末尾を読み取る際の参考となる推測バイト数を返す。先頭n行を読み込み、そのうち一番長かった行のバイト数 × nを返す
     * @param file ファイルパス
     * @return 末尾の推定バイト数
     * @throws IOException ファイル読み取り時にエラーが発生した場合
     */
    private int inferByteSize(File file) throws IOException {
        try(var lines = Files.lines(file.toPath())) {
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

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
        // 先頭から全部読むと遅いため、適当な位置までスキップしてそれ以降から読み取る
        // TODO 読み取った内容では指定行数に到達しない場合の考慮
        // TODO マルチバイト文字をぶった斬ってしまった場合の考慮
        try(var fc = FileChannel.open(file.toPath(), READ)) {
            // スキップ位置の推測
            var byteSize = inferByteSize();
            // ファイルサイズより大きくなってしまったらファイル全体が読まれるように調整
            if(byteSize > fc.size()) {
                byteSize = (int)fc.size();
            }

            // バッファ
            var buffer = ByteBuffer.allocate(byteSize);

            // スキップ
            fc.position(fc.size() - byteSize);

            // 読み取り
            fc.read(buffer);

            // 出力
            printTail(new String(buffer.array(), StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            // TODO 出力方法の検討
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    /**
     * 末尾を読み取る際の参考となる推測バイト数を返す。先頭n行を読み込み、そのうち一番長かった行のバイト数 × nを返す
     * @return 末尾の推定バイト数
     */
    private int inferByteSize() throws IOException {
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

    /**
     * ファイルの末尾の指定された行数分だけ出力する
     */
    private void printTail(List<String> lines) {
        int lineCount = lines.size();

        lines.stream()
                .skip(Math.max(0, lineCount - numberLines))
                .forEach(System.out::println);
    }
}

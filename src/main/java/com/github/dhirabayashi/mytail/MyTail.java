package com.github.dhirabayashi.mytail;

import com.github.dhirabayashi.mytail.file.api.FileWrapper;
import com.github.dhirabayashi.mytail.file.impl.FileWrapperImpl;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;

@SuppressWarnings("SameParameterValue")
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

    @CommandLine.Option(names = {"-q"}, description = "Suppresses printing of headers when multiple files are being examined.")
    private boolean quiet;

    @CommandLine.Option(names = {"-c", "--bytes"}, description = "The location is number bytes.")
    private Integer bytes;

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
        // ファイルが指定されない場合は標準入力から読む
        if(files == null) {
            var scanner = new Scanner(System.in);
            var lines = new ArrayList<String>();
            while(scanner.hasNext()) {
                lines.add(scanner.nextLine());
            }

            var skipNum = Math.max(lines.size() - numberLines, 0);
            lines.stream()
                    .skip(skipNum)
                    .forEach(System.out::println);

            return 0;
        }

        int wholeExitCode = 0;
        int length = files.size();
        for(int i = 0; i < length; i++) {
            var file = files.get(i);

            // ファイル存在チェック
            if(!file.exists()) {
                System.err.printf("mytail: %s: No such file or directory\n", file);
                wholeExitCode = 1;
                continue;
            }

            // ファイル名の表示
            if(this.files.size() != 1 && !quiet) {
                System.out.printf("==> %s <==%n", file.toString());
            }

            // 実行
            Pair<List<String>, Integer> pair;
            if(bytes != null) {
                pair = readBytes(file);
            } else {
                pair = readLines(file);
            }

            // 表示
            var lines = pair.getLeft();
            lines.forEach(System.out::println);

            int exitCode = pair.getRight();

            // 終了コードが0でないことが一度でもあれば、全体の終了コードは0以外
            if(exitCode != 0) {
                wholeExitCode = exitCode;
            }

            if(i != length - 1 && !quiet) {
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
                buffer = fc.read(buffer);

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

    Pair<List<String>, Integer> readBytes(File file) {
        try(var fc = fileWrapper.open(file.toPath())) {
            // バッファ
            var buffer = ByteBuffer.allocate(bytes);

            // スキップ
            fc.position(fc.size() - bytes);

            // 読み取り
            buffer = fc.read(buffer);

            // 出力
            var content = Collections.singletonList(new String(buffer.array(), StandardCharsets.UTF_8));

            return Pair.of(content, 0);
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

    /**
     * 表示行数を設定する（テスト用）
     * @param numberLines 表示行数
     */
    void setNumberLines(int numberLines) {
        this.numberLines = numberLines;
    }
}

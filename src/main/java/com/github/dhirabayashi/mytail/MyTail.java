package com.github.dhirabayashi.mytail;

import com.github.dhirabayashi.mytail.file.api.FileChannelWrapper;
import com.github.dhirabayashi.mytail.file.api.FileWrapper;
import com.github.dhirabayashi.mytail.file.impl.FileWrapperImpl;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.*;
import java.util.concurrent.Callable;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

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

    @CommandLine.Option(names = {"-f", "--follow"}, description = "Output appended data as the file grows.")
    private Boolean follow;

    /**
     * ファイル読み取り部分を扱うインスタンス
     */
    private final FileWrapper fileWrapper;

    /**
     * ファイルチャンネルを保持するMap（後で監視するのに使用する）
     */
    private static final Map<File, FileChannelWrapper> channels = new HashMap<>();

    /**
     * ファイルの絶対パスを保持するMap（後で監視するのに使用する）
     */
    private static final Map<File, File> absolutePathMap = new HashMap<>();

    /**
     * ファイルラッパーを指定してMyTailを構築する
     * @param fileWrapper ファイル読み取り部分を扱うインスタンス
     */
    public MyTail(FileWrapper fileWrapper) {
        this.fileWrapper = fileWrapper;
    }

    public static void main(String[] args) {
        // 実行
        int exitCode = new CommandLine(new MyTail(new FileWrapperImpl())).execute(args);

        // ファイルの解放
        channels.forEach((file, fc) -> {
            try {
                fc.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        System.exit(exitCode);
    }

    @Override
    public Integer call() throws IOException {
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
                System.out.printf("==> %s <==%n", file);
            }

            // 実行
            Pair<List<String>, Integer> pair;
            if(bytes != null) {
                pair = readByByteSize(file, bytes);
            } else {
                pair = readByLineNum(file, numberLines);
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

        // オプションが指定されていたら更新監視する
        if(follow != null) {
            followFiles();
        }

        return wholeExitCode;
    }

    /**
     * ファイルの更新を監視する
     * @throws IOException 入出力エラーが発生した場合
     */
    void followFiles() throws IOException {
        // 登録
        var watcher = FileSystems.getDefault().newWatchService();
        for(var file : files) {
            file.toPath().getParent().register(watcher, ENTRY_MODIFY);
        }
        // 監視
        while (true) {
            WatchKey watchKey;
            try {
                watchKey = watcher.take();
            } catch (InterruptedException e) {
                return;
            }

            for (var event : watchKey.pollEvents()) {
                // 変更されたファイルの取得
                Path modified = (Path) event.context();
                var file = modified.toFile();
                file = absolutePathMap.get(file);

                // 知らないファイルだったら無視
                if(file == null) {
                    continue;
                }

                // 差分を読み取って出力
                long bytes = file.length() - channels.get(file).position();
                var content = readByByteSize(file, (int)bytes).getLeft().get(0);
                System.out.print(content);
            }

            if (!watchKey.reset()) {
                // 何らかの理由でWatchKeyが無効になった
                return;
            }
        }
    }

    /**
     * ファイルから表示する行を取得する
     * @param file ファイルパス
     * @param numberLines 表示する行数
     * @return 行と終了コードの組
     */
    Pair<List<String>, Integer> readByLineNum(File file, int numberLines) {
        // 先頭から全部読むと遅いため、適当な位置までスキップしてそれ以降から読み取る
        try {
            var fc = open(file);
            // スキップ位置の推測
            var byteSize = inferByteSize(file, numberLines);

            var coefficient = 1;
            while(true) {
                byteSize *= coefficient;
                // ファイルサイズより大きくなってしまったらファイル全体が読まれるように調整
                var displayWhole = false;
                if(byteSize >= fc.size()) {
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

    /**
     * ファイルからバイト単位で内容を取得する
     * @param file 対象のファイル
     * @param bytes 読み取るバイト数
     * @return 行と終了コードの組。行は必ず単一の要素になる
     */
    Pair<List<String>, Integer> readByByteSize(File file, int bytes) {
        try {
            var fc = open(file);

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
     * ファイルを開く
     * @param file 対象のファイル
     * @return 開いたファイルのチャンネル
     * @throws IOException 入出力エラーが発生した場合
     */
    private FileChannelWrapper open(File file) throws IOException {
        var fc = channels.get(file);
        if(fc == null) {
            fc = fileWrapper.open(file.toPath());
            // -fオプションがある場合、後で使うので保持
            channels.put(file, fc);
            absolutePathMap.put(new File(file.getName()), file.getAbsoluteFile());
        }

        return fc;
    }

    /**
     * 末尾を読み取る際の参考となる推測バイト数を返す。先頭n行を読み込み、そのうち一番長かった行のバイト数 × nを返す
     *
     * @param file        ファイルパス
     * @param numberLines 読み込む先頭の行数
     * @return 末尾の推定バイト数
     * @throws IOException ファイル読み取り時にエラーが発生した場合
     */
    private int inferByteSize(File file, int numberLines) throws IOException {
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

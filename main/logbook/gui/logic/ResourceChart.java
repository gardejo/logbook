package logbook.gui.logic;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import logbook.dto.chart.Resource;
import logbook.dto.chart.ResourceLog;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.widgets.Display;
import org.eclipse.wb.swt.SWTResourceManager;

/**
 * 資材チャートを描画する
 *
 */
public class ResourceChart {
    /** タイムゾーンオフセット */
    private static final long TZ_OFFSET = Calendar.getInstance().get(Calendar.ZONE_OFFSET);
    /** グラフエリアの左マージン */
    private static final int LEFT_WIDTH = 60;
    /** グラフエリアの右マージン */
    private static final int RIGHT_WIDTH = 60;
    /** グラフエリアの上マージン */
    private static final int TOP_HEIGHT = 30;
    /** グラフエリアの下マージン */
    private static final int BOTTOM_HEIGHT = 30;

    /** 資材ログ */
    private final ResourceLog log;
    /** 期間 */
    private final long term;
    /** スケールテキスト */
    private final String scaleText;
    /** 刻み */
    private final long notch;
    /** Width */
    private final int width;
    /** Height */
    private final int height;

    private final boolean[] enabled;

    private int max;
    private int min;
    private int max2;
    private int min2;
    private long[] time = {};
    private Resource[] resources = {};

    /**
     * 資材チャート
     * 
     * @param log 資材ログ
     * @param scale 日単位のスケール
     * @param width 幅
     * @param height 高さ
     */
    public ResourceChart(ResourceLog log, int scale, String scaleText, int width, int height, boolean[] enabled) {
        this.log = log;
        this.term = TimeUnit.DAYS.toMillis(scale);
        this.scaleText = scaleText;
        this.notch = (long) (this.term / ((double) (width - LEFT_WIDTH - RIGHT_WIDTH) / 4));
        this.width = width;
        this.height = height;
        this.enabled = enabled;
        // データロード
        this.load();
    }

    /**
     * グラフを描画します
     * 
     * @param gc グラフィックコンテキスト
     */
    public void draw(GC gc) {

        // グラフエリアの幅
        float w = this.width - LEFT_WIDTH - RIGHT_WIDTH;
        // グラフエリアの高さ
        float h = this.height - TOP_HEIGHT - BOTTOM_HEIGHT;
        // お絵かき開始
        gc.setAntialias(SWT.ON);
        gc.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
        gc.fillRectangle(0, 0, this.width, this.height);
        gc.setForeground(SWTResourceManager.getColor(SWT.COLOR_GRAY));
        gc.setLineWidth(2);
        // グラフエリアのラインを描く
        // 縦
        gc.drawLine(LEFT_WIDTH, TOP_HEIGHT, LEFT_WIDTH, this.height - BOTTOM_HEIGHT);
        gc.drawLine(this.width - RIGHT_WIDTH, TOP_HEIGHT, this.width - RIGHT_WIDTH, this.height - BOTTOM_HEIGHT);
        // 横
        gc.drawLine(LEFT_WIDTH - 5, this.height - BOTTOM_HEIGHT,
                (this.width - RIGHT_WIDTH) + 5, this.height - BOTTOM_HEIGHT);

        // 縦軸を描く
        gc.setLineWidth(1);
        for (int i = 0; i < 5; i++) {
            // 軸
            gc.setForeground(SWTResourceManager.getColor(SWT.COLOR_GRAY));
            int jh = (int) ((h * i) / 4) + TOP_HEIGHT;
            gc.drawLine(LEFT_WIDTH - 5, jh, (this.width - RIGHT_WIDTH) + 5, jh);
            //ラベルを設定
            gc.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
            String labelLeft = Integer.toString((int) (((float) (this.max - this.min) * (4 - i)) / 4) + this.min);
            String labelRight = Integer.toString((int) (((float) (this.max2 - this.min2) * (4 - i)) / 4) + this.min2);
            int labelLeftWidth = getStringWidth(gc, labelLeft);
            int labelHeight = gc.getFontMetrics().getHeight();
            if (this.max > this.min) {
                gc.drawString(labelLeft, LEFT_WIDTH - labelLeftWidth - 5, jh - (labelHeight / 2));
            }
            if (this.max2 > this.min2) {
                gc.drawString(labelRight, (this.width - RIGHT_WIDTH) + 10, jh - (labelHeight / 2));
            }
        }
        SimpleDateFormat format = new SimpleDateFormat("M月d日 HH:mm");
        // 横軸を描く
        for (int i = 0; i < 5; i++) {
            //ラベルを設定
            gc.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));

            int idx = (int) (((float) (this.time.length - 1) * i) / 4);
            String label = format.format(new Date(normalizeTime(this.time[idx], TimeUnit.MINUTES.toMillis(10))));
            int labelWidth = getStringWidth(gc, label);
            int x = ((int) ((w * i) / 4) + LEFT_WIDTH) - (labelWidth / 2);
            int y = (this.height - BOTTOM_HEIGHT) + 6;
            gc.drawText(label, x, y, true);
        }
        // 判例を描く
        int hx = LEFT_WIDTH;
        int hy = 5;
        for (int i = 0; i < this.resources.length; i++) {
            gc.setLineWidth(3);
            gc.setForeground(SWTResourceManager.getColor(this.resources[i].color));

            String label = this.resources[i].name;
            int labelWidth = getStringWidth(gc, label);
            int labelHeight = gc.getFontMetrics().getHeight();
            gc.drawLine(hx, hy + (labelHeight / 2), hx += 20, hy + (labelHeight / 2));
            hx += 1;
            gc.drawText(label, hx, hy, true);
            hx += labelWidth + 2;
        }
        // スケールテキストを描く
        int sx = this.width - RIGHT_WIDTH - getStringWidth(gc, this.scaleText);
        int sy = 5;
        gc.setForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
        gc.drawText(this.scaleText, sx, sy, true);

        // グラフを描く
        for (int i = 0; i < this.resources.length; i++) {
            if (this.enabled[i]) {
                gc.setLineWidth(2);
                gc.setForeground(SWTResourceManager.getColor(this.resources[i].color));

                int[] values = this.resources[i].values;
                Path path = new Path(Display.getCurrent());
                if (i < 4) {
                    drawPath(values, this.max, this.min, w, h, path);
                }
                else {
                    drawPath(values, this.max2, this.min2, w, h, path);
                }
                gc.drawPath(path);
            }
        }
    }

    private static void drawPath(int[] values, int max, int min, float w, float h, Path path) {
        float x = LEFT_WIDTH;
        float y = (h * (1 - ((float) (values[0] - min) / (max - min)))) + TOP_HEIGHT;
        path.moveTo(x, y);

        for (int j = 1; j < values.length; j++) {
            // 欠損(-1)データは描かない
            if (values[j] != -1) {

                float x1 = ((w * j) / values.length) + LEFT_WIDTH;
                float y1 = (h * (1 - ((float) (values[j] - min) / (max - min)))) + TOP_HEIGHT;
                path.lineTo(x1, y1);
            }
        }
    }

    /**
     * 資材ログを読み込む
     */
    private void load() {
        ResourceLog log = this.log;

        // 時間はソートされている前提
        // 最新の時間インデックス
        int maxidx = log.time.length - 1;
        // スケールで指定した範囲外で最も最新の時間インデックス、範囲外の時間がない場合0
        int minidx = Math.max(Math.abs(Arrays.binarySearch(log.time, log.time[maxidx] - this.term)) - 2, 0);

        // データを準備する
        // データMax値
        this.max = Integer.MIN_VALUE;
        // データMin値
        this.min = Integer.MAX_VALUE;
        // データMax値
        this.max2 = Integer.MIN_VALUE;
        // データMin値
        this.min2 = Integer.MAX_VALUE;
        // グラフに必要なデータ配列の長さ
        int length = (int) (this.term / this.notch) + 1;
        // 時間軸
        this.time = new long[length];
        // グラフデータ(資材)
        this.resources = new Resource[log.resources.length];
        for (int i = 0; i < log.resources.length; i++) {
            this.resources[i] = new Resource(log.resources[i].name, log.resources[i].color, new int[length]);
        }
        // 時間を用意する
        for (int i = 0; i < this.time.length; i++) {
            this.time[i] = (log.time[maxidx] - this.term) + ((this.term / (length - 1)) * i);
        }
        // 資材を用意する
        float fr = (float) (this.time[0] - log.time[minidx]) / (float) (log.time[minidx + 1] - log.time[minidx]);
        long s = log.time[maxidx] - this.term;
        for (int i = 0; i < this.resources.length; i++) {
            // 補正前のデータ
            int[] prevalues = log.resources[i].values;
            // 補正されたスケールで指定した範囲のデータ
            int[] values = this.resources[i].values;
            // 初期値は-1(欠損)
            Arrays.fill(values, -1);

            if (log.time[minidx] <= this.time[0]) {
                // スケール外データがある場合最初の要素を補完する
                values[0] = (int) (prevalues[minidx] + ((prevalues[minidx + 1] - prevalues[minidx]) * fr));
            }
            for (int j = minidx + 1; j < prevalues.length; j++) {
                int idx = (int) ((log.time[j] - s) / this.notch);
                values[idx] = prevalues[j];
            }
            boolean find = false;
            for (int j = 0; j < (length - 1); j++) {
                // 先頭のデータがない場合0扱いにする
                if (!find) {
                    if (values[j] >= 0) {
                        find = true;
                    } else {
                        values[j] = 0;
                    }
                }
                if ((values[j] >= 0) && this.enabled[i]) {
                    if (i < 4) {
                        // 資材最大数を設定
                        this.max = Math.max(values[j], this.max);
                        // 資材最小数を設定
                        this.min = Math.min(values[j], this.min);
                    }
                    else {
                        // 資材最大数を設定
                        this.max2 = Math.max(values[j], this.max2);
                        // 資材最小数を設定
                        this.min2 = Math.min(values[j], this.min2);
                    }
                }
            }
        }
        if (this.max >= this.min) { // 1つ以上有効なデータがある場合
            // 資材の最大数を1000単位にする、資材の最大数が1000未満なら1000に設定
            this.max = (int) Math.max(normalize(this.max, 1000), 1000);
            // 資材の最小数を0.8でかけた後1000単位にする、
            this.min = (int) Math.max(normalize((long) (this.min * 0.8f), 1000), 0);
        }
        if (this.max2 >= this.max2) { // 1つ以上有効なデータがある場合
            // 資材の最大数を100単位にする、資材の最大数が100未満なら100に設定
            this.max2 = (int) Math.max(normalize(this.max2, 100), 100);
            // 資材の最小数を0.8でかけた後100単位にする、
            this.min2 = (int) Math.max(normalize((long) (this.min2 * 0.8f), 100), 0);
        }
    }

    /**
     * 文字列のデバイス上の幅を返す
     * 
     * @param gc GC
     * @param str 文字列
     * @return 文字列幅
     */
    private static int getStringWidth(GC gc, String str) {
        return gc.textExtent(str).x;
    }

    /**
     * 数値を指定した間隔で刻む
     * 
     * @param value 数値
     * @param notch 刻み
     * @return
     */
    private static long normalize(long value, long notch) {
        long t = value;
        long half = notch / 2;
        long mod = t % notch;
        if (mod >= half) {
            t += notch - mod;
        } else {
            t -= mod;
        }
        return t;
    }

    /**
     * 時刻を指定した間隔で刻む
     * 
     * @param time 時刻
     * @param notch 刻み
     * @return
     */
    private static long normalizeTime(long time, long notch) {
        return normalize(time + TZ_OFFSET, notch) - TZ_OFFSET;
    }
}
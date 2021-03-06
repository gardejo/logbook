package logbook.gui;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import logbook.config.AppConfig;
import logbook.config.ItemConfig;
import logbook.config.ItemMasterConfig;
import logbook.config.ShipConfig;
import logbook.config.ShipGroupConfig;
import logbook.constants.AppConstants;
import logbook.data.context.GlobalContext;
import logbook.dto.BattleExDto;
import logbook.dto.DockDto;
import logbook.dto.MapCellDto;
import logbook.dto.PracticeUserDetailDto;
import logbook.gui.background.AsyncExecApplicationMain;
import logbook.gui.background.AsyncExecUpdateCheck;
import logbook.gui.background.BackgroundInitializer;
import logbook.gui.listener.HelpEventListener;
import logbook.gui.listener.MainShellAdapter;
import logbook.gui.listener.TrayItemMenuListener;
import logbook.gui.listener.TraySelectionListener;
import logbook.gui.logic.LayoutLogic;
import logbook.gui.logic.PushNotify;
import logbook.gui.logic.Sound;
import logbook.gui.widgets.FleetComposite;
import logbook.internal.BattleResultServer;
import logbook.internal.EnemyData;
import logbook.internal.Item;
import logbook.internal.MasterData;
import logbook.internal.Ship;
import logbook.server.proxy.DatabaseClient;
import logbook.server.proxy.ProxyServer;
import logbook.thread.ThreadManager;
import logbook.thread.ThreadStateObserver;
import logbook.util.JIntellitypeWrapper;
import logbook.util.SwtUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;
import org.eclipse.wb.swt.SWTResourceManager;

import com.melloware.jintellitype.HotkeyListener;

/**
 * メイン画面
 *
 */
public final class ApplicationMain extends WindowBase {

    /** ロガー */
    private static final Logger LOG = LogManager.getLogger(ApplicationMain.class);

    private static final int MAX_LOG_LINES = 200;
    private static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat(AppConstants.DATE_SHORT_FORMAT);

    private static final long startTime = System.currentTimeMillis();

    public static void sysPrint(String mes) {
        System.out.println(mes + ": " + (System.currentTimeMillis() - startTime) + " ms");
    }

    public static void timeLogPrint(String mes) {
        logPrint(mes + ": " + (System.currentTimeMillis() - startTime) + " ms");
    }

    public static void logPrint(final String mes) {
        if (main.display.getThread() == Thread.currentThread()) {
            main.printMessage(mes);
        }
        else {
            main.display.asyncExec(new Runnable() {
                @Override
                public void run() {
                    main.printMessage(mes);
                }
            });
        }
    }

    public static ApplicationMain main;
    public static boolean disableUpdate;

    /**
     * <p>
     * 終了処理を行います
     * </p>
     */
    private static final class ShutdownHookThread implements Runnable {

        /** ロガー */
        private static final Logger LOG = LogManager.getLogger(ShutdownHookThread.class);

        @Override
        public void run() {
            try {
                // スレッドを終了する
                endThread();

                // 設定を書き込みます
                AppConfig.store();
                ShipConfig.store();
                ShipGroupConfig.store();
                ItemMasterConfig.store();
                ItemConfig.store();
                MasterData.store();
                EnemyData.store();
            } catch (Exception e) {
                LOG.fatal("シャットダウンスレッドで異常終了しました", e);
            }
        }
    }

    /** ベースクラスの持っているshellと同じ */
    private Shell shell;
    /** 表示しない親ウィンドウ */
    private Shell dummyHolder;
    /** ディスプレイ */
    private Display display;

    /** トレイ */
    private TrayItem trayItem;

    /** タイトルテキスト */
    private String titleText;

    /** キャプチャ */
    private CaptureDialog captureWindow;
    /** ドロップ報告書 */
    private DropReportTable dropReportWindow;
    /** 建造報告書 */
    private CreateShipReportTable createShipReportWindow;
    /** 開発報告書 */
    private CreateItemReportTable createItemReportWindow;
    /** 遠征報告書 */
    private MissionResultTable missionResultWindow;
    /** 所有装備一覧 */
    private ItemTable itemTableWindow;
    /** 艦娘一覧1-4 */
    private final ShipTable[] shipTableWindows = new ShipTable[4];
    /** お風呂に入りたい艦娘 */
    private BathwaterTableDialog bathwaterTablwWindow;
    /** 遠征一覧 */
    private MissionTable missionTableWindow;
    /** 任務一覧 */
    private QuestTable questTableWindow;
    /** 戦況 */
    private BattleWindowLarge battleWindowLarge;
    /** 戦況-横 */
    private BattleWindowSmall battleWindowSmall;
    /** 自軍敵軍パラメータ */
    private BattleShipWindow battleShipWindow;
    /** 経験値計算 */
    private CalcExpDialog calcExpWindow;
    /** 演習経験値計算 */
    private CalcPracticeExpDialog calcPracticeExpWindow;
    /** 出撃統計 */
    private BattleAggDialog battleCounterWindow;
    /** グループエディター */
    private ShipFilterGroupDialog shipFilterGroupWindow;
    /** グループエディター */
    private ResourceChartDialog resourceChartWindow;
    /** ツール */
    private LauncherWindow launcherWindow;

    /** コマンドボタン */
    private Composite commandComposite;
    /** 縮小表示メニューアイテム */
    private MenuItem dispsize;
    /** 所有装備 */
    private Button itemList;
    /** 所有艦娘 */
    private Button shipList;
    /** タブ */
    private CTabFolder tabFolder;
    /** メインコンポジット */
    private Composite mainComposite;
    /** 遠征グループ */
    private Group deckGroup;
    /** 1分前に通知する(遠征) */
    private Button deckNotice;
    /** 遠征.艦隊2の艦隊名 */
    private Label deck1name;
    /** 遠征.艦隊2の帰投時間 */
    private Text deck1time;
    /** 遠征.艦隊3の艦隊名 */
    private Label deck2name;
    /** 遠征.艦隊3の帰投時間 */
    private Text deck2time;
    /** 遠征.艦隊4の艦隊名 */
    private Label deck3name;
    /** 遠征.艦隊4の帰投時間 */
    private Text deck3time;
    /** 入渠グループ **/
    private Group ndockGroup;
    /** 1分前に通知する(入渠) */
    private Button ndockNotice;
    /** 入渠.ドッグ1.艦娘の名前 **/
    private Label ndock1name;
    /** 入渠.ドッグ1.お風呂から上がる時間 **/
    private Text ndock1time;
    /** 入渠.ドッグ2.艦娘の名前 **/
    private Label ndock2name;
    /** 入渠.ドッグ2.お風呂から上がる時間 **/
    private Text ndock2time;
    /** 入渠.ドッグ3.艦娘の名前 **/
    private Label ndock3name;
    /** 入渠.ドッグ3.お風呂から上がる時間 **/
    private Text ndock3time;
    /** 入渠.ドッグ4.艦娘の名前 **/
    private Label ndock4name;
    /** 入渠.ドッグ4.お風呂から上がる時間 **/
    private Text ndock4time;
    /** コンソールコンポジット **/
    private Composite consoleComposite;
    /** エラー表示 **/
    private Label errorLabel;
    /** コンソール **/
    private org.eclipse.swt.widgets.List console;

    /**
     * Launch the application.
     * @param args
     */
    public static void main(String[] args) {
        try {
            // グループ化のためのアプリケーションID (Windows 7以降)
            Display.setAppName(AppConstants.NAME);
            // 設定読み込み
            sysPrint("起動");
            AppConfig.load();
            /*　static initializer に移行
            ShipConfig.load();
            MasterDataConfig.load();
            ShipGroupConfig.load();
            ItemMasterConfig.load();
            ItemConfig.load();
            EnemyData.load();
            */
            sysPrint("基本設定ファイル読み込み完了");
            // シャットダウンフックを登録します
            Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHookThread()));
            // アプリケーション開始
            main = new ApplicationMain();
            sysPrint("メインウィンドウ初期化開始");
            main.restore();
        } catch (Error e) {
            LOG.fatal("メインスレッドが異常終了しました", e);
        } catch (Exception e) {
            LOG.fatal("メインスレッドが異常終了しました", e);
        } finally {
            endThread();
        }
    }

    /**
     * Open the window.
     */
    @Override
    public void open() {
        try {
            Display display = Display.getDefault();
            this.createContents();
            this.registerEvents();
            sysPrint("ウィンドウ表示開始...");
            this.restoreWindows();
            sysPrint("メッセージループに入ります...");
            while (!this.shell.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();

                }
            }
            this.dummyHolder.dispose();
        } finally {
            Tray tray = Display.getDefault().getSystemTray();
            if (tray != null) {
                for (TrayItem item : tray.getItems()) {
                    item.dispose();
                }
            }
        }
    }

    @Override
    protected boolean moveWithDrag() {
        return true;
    }

    /**
     * 画面レイアウトを作成します
     */
    public void createContents() {
        this.display = Display.getDefault();
        super.createContents(this.display, SWT.CLOSE | SWT.TITLE | SWT.MIN | SWT.RESIZE, false);
        this.shell = this.getShell();
        this.shell.setText(AppConstants.TITLEBAR_TEXT);
        this.dummyHolder = new Shell(this.display, SWT.TOOL);
        GridLayout glShell = new GridLayout(1, false);
        glShell.horizontalSpacing = 1;
        glShell.marginTop = 0;
        glShell.marginWidth = 0;
        glShell.marginHeight = 0;
        glShell.marginBottom = 0;
        glShell.verticalSpacing = 1;
        this.shell.setLayout(glShell);
        this.shell.addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent e) {
                // 終了の確認でウインドウ位置を記憶
                ApplicationMain.this.saveWindows();

                if (AppConfig.get().isCheckDoit()) {
                    MessageBox box = new MessageBox(ApplicationMain.this.shell, SWT.YES | SWT.NO
                            | SWT.ICON_QUESTION);
                    box.setText("終了の確認");
                    box.setMessage("航海日誌を終了しますか？");
                    e.doit = box.open() == SWT.YES;
                }

                if (e.doit) {
                    // 他のウィンドウを閉じる
                    for (WindowBase win : ApplicationMain.this.getWindowList()) {
                        win.dispose();
                    }
                }
            }

            @Override
            public void shellActivated(ShellEvent event) {
                // Mainウィンドウがアクティブになった時、子ウィンドウをMainウィンドウの直後に入れる
                if (nativeService.isTopMostAvailable()) {
                    Set<WindowBase> windows = getActivatedWindows();
                    WindowBase[] windowArray = windows.toArray(new WindowBase[windows.size()]);
                    for (int i = windowArray.length - 1; i >= 0; --i) {
                        WindowBase win = windowArray[i];
                        if (win.getShell().getVisible() && (win.getActualParent() == ApplicationMain.this.dummyHolder)) {
                            windowArray[i].setBehindTo(ApplicationMain.this);
                            break;
                        }
                    }
                }
            }

            @Override
            public void shellDeiconified(ShellEvent e) {
                // Main以外のウィンドウも連動させる
                ApplicationMain.this.childDeiconified();
            }

            @Override
            public void shellIconified(ShellEvent e) {
                // Main以外のウィンドウも連動させる
                ApplicationMain.this.childIconified();
            }
        });

        // メニューバー
        Menu menubar = new Menu(this.shell, SWT.BAR);
        this.shell.setMenuBar(menubar);
        MenuItem cmdmenuroot = new MenuItem(menubar, SWT.CASCADE);
        cmdmenuroot.setText("コマンド");
        Menu cmdmenu = new Menu(cmdmenuroot);
        cmdmenuroot.setMenu(cmdmenu);
        MenuItem calcmenuroot = new MenuItem(menubar, SWT.CASCADE);
        calcmenuroot.setText("計算機");
        Menu calcmenu = new Menu(calcmenuroot);
        calcmenuroot.setMenu(calcmenu);
        MenuItem etcroot = new MenuItem(menubar, SWT.CASCADE);
        etcroot.setText("その他");
        Menu etcmenu = new Menu(etcroot);
        etcroot.setMenu(etcmenu);

        // メニュー
        // コマンド-キャプチャ
        MenuItem capture = new MenuItem(cmdmenu, SWT.CHECK);
        capture.setText("キャプチャ(&C)");
        this.captureWindow = new CaptureDialog(this.dummyHolder, capture);
        // セパレータ
        new MenuItem(cmdmenu, SWT.SEPARATOR);
        // コマンド-ドロップ報告書
        MenuItem cmddrop = new MenuItem(cmdmenu, SWT.CHECK);
        cmddrop.setText("ドロップ報告書(&D)\tCtrl+D");
        cmddrop.setAccelerator(SWT.CTRL + 'D');
        this.dropReportWindow = new DropReportTable(this.dummyHolder, cmddrop);
        // コマンド-建造報告書
        MenuItem cmdcreateship = new MenuItem(cmdmenu, SWT.CHECK);
        cmdcreateship.setText("建造報告書(&Y)\tCtrl+Y");
        cmdcreateship.setAccelerator(SWT.CTRL + 'Y');
        this.createShipReportWindow = new CreateShipReportTable(this.dummyHolder, cmdcreateship);
        // コマンド-開発報告書
        MenuItem cmdcreateitem = new MenuItem(cmdmenu, SWT.CHECK);
        cmdcreateitem.setText("開発報告書(&E)\tCtrl+E");
        cmdcreateitem.setAccelerator(SWT.CTRL + 'E');
        this.createItemReportWindow = new CreateItemReportTable(this.dummyHolder, cmdcreateitem);
        // コマンド-遠征報告書
        MenuItem cmdmissionresult = new MenuItem(cmdmenu, SWT.CHECK);
        cmdmissionresult.setText("遠征報告書(&T)\tCtrl+T");
        cmdmissionresult.setAccelerator(SWT.CTRL + 'T');
        this.missionResultWindow = new MissionResultTable(this.dummyHolder, cmdmissionresult);

        // コマンド-遠征一覧
        MenuItem missionlist = new MenuItem(cmdmenu, SWT.CHECK);
        missionlist.setText("遠征一覧");
        this.missionTableWindow = new MissionTable(this.dummyHolder, missionlist);

        // セパレータ
        new MenuItem(cmdmenu, SWT.SEPARATOR);
        // コマンド-所有装備一覧
        MenuItem cmditemlist = new MenuItem(cmdmenu, SWT.CHECK);
        cmditemlist.setText("所有装備一覧(&X)\tCtrl+X");
        cmditemlist.setAccelerator(SWT.CTRL + 'X');
        this.itemTableWindow = new ItemTable(this.dummyHolder, cmditemlist);
        // セパレータ
        new MenuItem(cmdmenu, SWT.SEPARATOR);
        // コマンド-所有艦娘一覧
        for (int i = 0; i < 4; ++i) {
            MenuItem cmdshiplist = new MenuItem(cmdmenu, SWT.CHECK);
            if (i == 0) {
                cmdshiplist.setAccelerator(SWT.CTRL + ('S'));
            }
            else {
                cmdshiplist.setAccelerator(SWT.CTRL + ('1' + i));
            }
            this.shipTableWindows[i] = new ShipTable(this.dummyHolder, cmdshiplist, i);
        }

        // コマンド-お風呂に入りたい艦娘
        MenuItem cmdbathwaterlist = new MenuItem(cmdmenu, SWT.CHECK);
        cmdbathwaterlist.setText("お風呂に入りたい艦娘(&N)\tCtrl+N");
        cmdbathwaterlist.setAccelerator(SWT.CTRL + 'N');
        this.bathwaterTablwWindow = new BathwaterTableDialog(this.dummyHolder, cmdbathwaterlist);
        // セパレータ
        new MenuItem(cmdmenu, SWT.SEPARATOR);

        // コマンド-任務一覧
        MenuItem questlist = new MenuItem(cmdmenu, SWT.CHECK);
        questlist.setText("任務一覧(&Q)\tCtrl+Q");
        questlist.setAccelerator(SWT.CTRL + 'Q');
        this.questTableWindow = new QuestTable(this.dummyHolder, questlist);
        // セパレータ
        new MenuItem(cmdmenu, SWT.SEPARATOR);

        // 表示-戦況ウィンドウ 
        MenuItem battleWinMenu = new MenuItem(cmdmenu, SWT.CHECK);
        battleWinMenu.setText("戦況(&B)\tCtrl+B");
        battleWinMenu.setAccelerator(SWT.CTRL + 'B');
        this.battleWindowLarge = new BattleWindowLarge(this.dummyHolder, battleWinMenu);

        // 表示-戦況ウィンドウ （小）
        MenuItem battleWinSMenu = new MenuItem(cmdmenu, SWT.CHECK);
        battleWinSMenu.setText("戦況-横(&H)\tCtrl+H");
        battleWinSMenu.setAccelerator(SWT.CTRL + 'H');
        this.battleWindowSmall = new BattleWindowSmall(this.dummyHolder, battleWinSMenu);

        // 表示-敵味方パラメータ
        MenuItem battleShipWinMenu = new MenuItem(cmdmenu, SWT.CHECK);
        battleShipWinMenu.setText("自軍敵軍パラメータ(&P)\tCtrl+P");
        battleShipWinMenu.setAccelerator(SWT.CTRL + 'P');
        this.battleShipWindow = new BattleShipWindow(this.dummyHolder, battleShipWinMenu);

        // 表示-縮小表示
        // ウィンドウの右クリックメニューに追加
        new MenuItem(this.getMenu(), SWT.SEPARATOR);
        this.dispsize = new MenuItem(this.getMenu(), SWT.CHECK);
        this.dispsize.setText("縮小表示(&M)\tCtrl+M");
        this.dispsize.setAccelerator(SWT.CTRL + 'M');
        // セパレータ
        new MenuItem(cmdmenu, SWT.SEPARATOR);
        // 終了
        final MenuItem dispose = new MenuItem(cmdmenu, SWT.NONE);
        dispose.setText("終了");
        dispose.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                ApplicationMain.this.shell.close();
            }
        });

        // 計算機-経験値計算
        MenuItem calcexp = new MenuItem(calcmenu, SWT.CHECK);
        calcexp.setText("経験値計算機(&C)\tCtrl+C");
        calcexp.setAccelerator(SWT.CTRL + 'C');
        this.calcExpWindow = new CalcExpDialog(this.dummyHolder, calcexp);

        // 計算機-演習経験値計算
        MenuItem calcpracticeexp = new MenuItem(calcmenu, SWT.CHECK);
        calcpracticeexp.setText("演習経験値計算機(&V)\tCtrl+V");
        calcpracticeexp.setAccelerator(SWT.CTRL + 'V');
        this.calcPracticeExpWindow = new CalcPracticeExpDialog(this.dummyHolder, calcpracticeexp);

        // その他-資材チャート
        MenuItem resourceChart = new MenuItem(etcmenu, SWT.CHECK);
        resourceChart.setText("資材チャート(&R)\tCtrl+R");
        resourceChart.setAccelerator(SWT.CTRL + 'R');
        this.resourceChartWindow = new ResourceChartDialog(this.dummyHolder, resourceChart);

        // コマンド-出撃統計
        MenuItem battleCounter = new MenuItem(etcmenu, SWT.CHECK);
        battleCounter.setText("出撃統計(&A)\tCtrl+A");
        battleCounter.setAccelerator(SWT.CTRL + 'A');
        this.battleCounterWindow = new BattleAggDialog(this.dummyHolder, battleCounter);
        // セパレータ
        new MenuItem(etcmenu, SWT.SEPARATOR);
        // その他-グループエディター
        MenuItem shipgroup = new MenuItem(etcmenu, SWT.CHECK);
        shipgroup.setText("グループエディター(&G)\tCtrl+G");
        shipgroup.setAccelerator(SWT.CTRL + 'G');
        this.shipFilterGroupWindow = new ShipFilterGroupDialog(this.dummyHolder, shipgroup);
        // その他-自動プロキシ構成スクリプトファイル生成
        MenuItem pack = new MenuItem(etcmenu, SWT.NONE);
        pack.setText("自動プロキシ構成スクリプト");
        pack.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                new CreatePacFileDialog(ApplicationMain.this.dummyHolder).open();
            }
        });
        // セパレータ 
        new MenuItem(etcmenu, SWT.SEPARATOR);
        // その他-ツール
        MenuItem toolwindows = new MenuItem(etcmenu, SWT.CHECK);
        toolwindows.setText("ツール");
        this.launcherWindow = new LauncherWindow(this.dummyHolder, toolwindows);
        // その他-ウィンドウをディスプレイ内に移動
        MenuItem movewindows = new MenuItem(etcmenu, SWT.NONE);
        movewindows.setText("画面外のウィンドウを戻す(&W)\tCtrl+W");
        movewindows.setAccelerator(SWT.CTRL + 'W');
        movewindows.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                ApplicationMain.this.moveWindowsIntoDisplay();
            }
        });
        // その他-設定
        MenuItem config = new MenuItem(etcmenu, SWT.NONE);
        config.setText("設定(&O)\tCtrl+O");
        config.setAccelerator(SWT.CTRL + 'O');
        config.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                new ConfigDialog(ApplicationMain.this).open();
            }
        });
        // その他-バージョン情報
        MenuItem version = new MenuItem(etcmenu, SWT.NONE);
        version.setText("バージョン情報(&V)");
        version.addSelectionListener(new HelpEventListener(this));

        // テスト用
        if (AppConfig.get().isEnableTestWindow()) {
            MenuItem testfeeder = new MenuItem(etcmenu, SWT.NONE);
            testfeeder.setText("テストする");
            testfeeder.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    new TestDataFeeder(ApplicationMain.this).open();
                }
            });

            MenuItem itemcsvout = new MenuItem(etcmenu, SWT.NONE);
            itemcsvout.setText("装備アイテムをCSVダンプ");
            itemcsvout.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    try {
                        File file = new File("itemInfo.csv");
                        OutputStreamWriter fw = new OutputStreamWriter(
                                new FileOutputStream(file), AppConstants.CHARSET);
                        Item.dumpCSV(fw);
                        fw.close();
                        SwtUtils.messageDialog("以下のファイルに書き込みました\n" + file.getAbsolutePath(),
                                ApplicationMain.this.shell);
                    } catch (IOException e1) {
                        logPrint("書き込み失敗: " + e1.getMessage());
                    }
                }
            });
            MenuItem shipcsvout = new MenuItem(etcmenu, SWT.NONE);
            shipcsvout.setText("艦娘をCSVダンプ");
            shipcsvout.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    try {
                        File file = new File("shipInfo.csv");
                        OutputStreamWriter fw = new OutputStreamWriter(
                                new FileOutputStream(file), AppConstants.CHARSET);
                        Ship.dumpCSV(fw);
                        fw.close();
                        SwtUtils.messageDialog("以下のファイルに書き込みました\n" + file.getAbsolutePath(),
                                ApplicationMain.this.shell);
                    } catch (IOException e1) {
                        logPrint("書き込み失敗: " + e1.getMessage());
                    }
                }
            });
        }

        // ショートカットキー
        this.display.addFilter(SWT.KeyDown, new Listener() {
            @Override
            public void handleEvent(Event e) {
                if ((e.stateMask & (SWT.CTRL | SWT.SHIFT)) == (SWT.CTRL | SWT.SHIFT))
                {
                    ApplicationMain.this.shortcutKeyPushed(e.keyCode);
                }
            }
        });

        // シェルイベント
        this.shell.addShellListener(new MainShellAdapter());
        // キーが押された時に呼ばれるリスナーを追加します
        this.shell.addHelpListener(new HelpEventListener(this));

        this.trayItem = this.addTrayItem(this.display);

        // コマンドボタン
        this.commandComposite = new Composite(this.shell, SWT.NONE);
        this.commandComposite.setLayout(new FillLayout(SWT.HORIZONTAL));
        this.commandComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.itemList = new Button(this.commandComposite, SWT.PUSH);
        this.itemList.setText("所有装備(0/0)");
        this.itemList.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                ApplicationMain.this.itemTableWindow.open();
                ApplicationMain.this.itemTableWindow.getShell().setActive();
            }
        });
        this.shipList = new Button(this.commandComposite, SWT.PUSH);
        this.shipList.setText("所有艦娘(0/0)");
        this.shipList.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                ApplicationMain.this.shipTableWindows[0].open();
                ApplicationMain.this.shipTableWindows[0].getShell().setActive();
            }
        });

        // タブフォルダー
        this.tabFolder = new CTabFolder(this.shell, SWT.NONE);
        this.tabFolder.setSelectionBackground(Display.getCurrent().getSystemColor(
                SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT));
        this.tabFolder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));
        this.tabFolder.setTabHeight(26);
        this.tabFolder.marginWidth = 0;
        this.tabFolder.setMinimumCharacters(2);

        // 母港タブ
        CTabItem mainItem = new CTabItem(this.tabFolder, SWT.NONE);
        mainItem.setFont(SWTResourceManager.getBoldFont(this.shell.getFont()));
        this.tabFolder.setSelection(mainItem);
        mainItem.setText("母港");

        // メインコンポジット
        this.mainComposite = new Composite(this.tabFolder, SWT.NONE);
        this.mainComposite.setLayout(new FormLayout());
        this.mainComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mainItem.setControl(this.mainComposite);

        // 遠征
        Label deckLabel = new Label(this.mainComposite, SWT.NONE);
        deckLabel.setText("遠征");
        deckLabel.setLayoutData(SwtUtils.makeFormData(
                new FormAttachment(0, 7), // 7 pixel indent
                null, // free width
                new FormAttachment(0),
                null)); // free height

        this.deckNotice = new Button(this.mainComposite, SWT.CHECK);
        this.deckNotice.setSelection(AppConfig.get().isNoticeDeckmission());
        this.deckNotice.setLayoutData(SwtUtils.makeFormData(
                null, // free width
                new FormAttachment(100),
                new FormAttachment(0),
                null)); // free height
        this.deckNotice.setText("1分前に通知する");
        this.deckNotice.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                AppConfig.get().setNoticeDeckmission(ApplicationMain.this.deckNotice.getSelection());
            }
        });

        this.deckGroup = new Group(this.mainComposite, SWT.NONE);
        this.deckGroup.setLayoutData(SwtUtils.makeFormData(
                new FormAttachment(0),
                new FormAttachment(100),
                new FormAttachment(0),
                null));

        GridLayout glDeckGroup = new GridLayout(2, false);
        glDeckGroup.verticalSpacing = 1;
        // なぜかMacだとスペースが全くないので自分で大きさを計算
        glDeckGroup.marginTop = Math.max(0,
                SwtUtils.ComputeHeaderHeight(this.deckGroup, 1.4) - this.deckGroup.getClientArea().y);
        glDeckGroup.marginWidth = 0;
        glDeckGroup.marginHeight = 0;
        glDeckGroup.marginBottom = 0;
        glDeckGroup.horizontalSpacing = 1;
        this.deckGroup.setLayout(glDeckGroup);

        this.deck1name = new Label(this.deckGroup, SWT.NONE);
        this.deck1name.setText("ここに艦隊2の艦隊名が入ります");
        this.deck1name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.deck1time = new Text(this.deckGroup, SWT.SINGLE | SWT.BORDER);
        this.deck1time.setText("艦隊2の帰投時間");
        GridData gddeck1time = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gddeck1time.widthHint = 75;
        this.deck1time.setLayoutData(gddeck1time);

        this.deck2name = new Label(this.deckGroup, SWT.NONE);
        this.deck2name.setText("ここに艦隊3の艦隊名が入ります");
        this.deck2name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.deck2time = new Text(this.deckGroup, SWT.SINGLE | SWT.BORDER);
        this.deck2time.setText("艦隊3の帰投時間");
        GridData gddeck2time = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gddeck2time.widthHint = 75;
        this.deck2time.setLayoutData(gddeck2time);

        this.deck3name = new Label(this.deckGroup, SWT.NONE);
        this.deck3name.setText("ここに艦隊4の艦隊名が入ります");
        this.deck3name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.deck3time = new Text(this.deckGroup, SWT.SINGLE | SWT.BORDER);
        this.deck3time.setText("艦隊4の帰投時間");
        GridData gddeck3time = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gddeck3time.widthHint = 75;
        this.deck3time.setLayoutData(gddeck3time);

        // 入渠
        Label ndockLabel = new Label(this.mainComposite, SWT.NONE);
        ndockLabel.setText("入渠");
        ndockLabel.setLayoutData(SwtUtils.makeFormData(
                new FormAttachment(0, 7), // 7 pixel indent
                null, // free width
                new FormAttachment(this.deckGroup),
                null)); // free height

        this.ndockNotice = new Button(this.mainComposite, SWT.CHECK);
        this.ndockNotice.setSelection(AppConfig.get().isNoticeNdock());
        this.ndockNotice.setLayoutData(SwtUtils.makeFormData(
                null, // free width
                new FormAttachment(100),
                new FormAttachment(this.deckGroup),
                null)); // free height
        this.ndockNotice.setText("1分前に通知する");
        this.ndockNotice.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                AppConfig.get().setNoticeNdock(ApplicationMain.this.ndockNotice.getSelection());
            }
        });

        this.ndockGroup = new Group(this.mainComposite, SWT.NONE);
        this.ndockGroup.setLayoutData(SwtUtils.makeFormData(
                new FormAttachment(0),
                new FormAttachment(100),
                new FormAttachment(this.deckGroup),
                null));

        GridLayout glNdockGroup = new GridLayout(2, false);
        glNdockGroup.verticalSpacing = 1;
        glNdockGroup.marginTop = Math.max(0,
                SwtUtils.ComputeHeaderHeight(this.ndockGroup, 1.4) - this.ndockGroup.getClientArea().y);
        glNdockGroup.marginWidth = 0;
        glNdockGroup.marginHeight = 0;
        glNdockGroup.marginBottom = 0;
        glNdockGroup.horizontalSpacing = 1;
        this.ndockGroup.setLayout(glNdockGroup);

        this.ndock1name = new Label(this.ndockGroup, SWT.NONE);
        this.ndock1name.setText("ドッグ1に浸かっている艦娘の名前");
        this.ndock1name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.ndock1time = new Text(this.ndockGroup, SWT.SINGLE | SWT.BORDER);
        this.ndock1time.setText("お風呂から上がる時間");
        GridData gdndock1time = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gdndock1time.widthHint = 75;
        this.ndock1time.setLayoutData(gdndock1time);

        this.ndock2name = new Label(this.ndockGroup, SWT.NONE);
        this.ndock2name.setText("ドッグ2に浸かっている艦娘の名前");
        this.ndock2name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.ndock2time = new Text(this.ndockGroup, SWT.SINGLE | SWT.BORDER);
        this.ndock2time.setText("お風呂から上がる時間");
        GridData gdndock2time = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gdndock2time.widthHint = 75;
        this.ndock2time.setLayoutData(gdndock2time);

        this.ndock3name = new Label(this.ndockGroup, SWT.NONE);
        this.ndock3name.setText("ドッグ3に浸かっている艦娘の名前");
        this.ndock3name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.ndock3time = new Text(this.ndockGroup, SWT.SINGLE | SWT.BORDER);
        this.ndock3time.setText("お風呂から上がる時間");
        GridData gdndock3time = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gdndock3time.widthHint = 75;
        this.ndock3time.setLayoutData(gdndock3time);

        this.ndock4name = new Label(this.ndockGroup, SWT.NONE);
        this.ndock4name.setText("ドッグ4に浸かっている艦娘の名前");
        this.ndock4name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        this.ndock4time = new Text(this.ndockGroup, SWT.SINGLE | SWT.BORDER);
        this.ndock4time.setText("お風呂から上がる時間");
        GridData gdndock4time = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gdndock4time.widthHint = 75;
        this.ndock4time.setLayoutData(gdndock4time);

        // エラー表示
        this.errorLabel = new Label(this.mainComposite, SWT.NONE);
        this.errorLabel.setLayoutData(SwtUtils.makeFormData(
                new FormAttachment(0),
                new FormAttachment(100),
                new FormAttachment(this.ndockGroup),
                null));
        this.errorLabel.setAlignment(SWT.CENTER);
        this.errorLabel.setBackground(SWTResourceManager.getColor(AppConstants.COND_RED_COLOR));
        this.errorLabel.setText("エラー表示");
        this.errorLabel.setVisible(false);

        // コンソール
        this.consoleComposite = new Composite(this.mainComposite, SWT.NONE);
        GridLayout loglayout = new GridLayout(1, false);
        loglayout.verticalSpacing = 1;
        loglayout.marginTop = 0;
        loglayout.marginWidth = 0;
        loglayout.marginHeight = 0;
        loglayout.marginBottom = 0;
        loglayout.horizontalSpacing = 1;
        this.consoleComposite.setLayout(loglayout);
        this.consoleComposite.setLayoutData(SwtUtils.makeFormData(
                new FormAttachment(0),
                new FormAttachment(100),
                new FormAttachment(this.ndockGroup),
                new FormAttachment(100)));

        this.console = new org.eclipse.swt.widgets.List(this.consoleComposite, SWT.BORDER | SWT.V_SCROLL);
        this.console.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));

        // 初期設定 縮小表示が有効なら縮小表示にする
        if (AppConfig.get().isMinimumLayout()) {
            this.shell.setRedraw(false);
            ApplicationMain.this.hide(true, this.commandComposite, this.deckNotice, this.deck1name, this.deck2name,
                    this.deck3name, this.ndockNotice, this.ndock1name, this.ndock2name, this.ndock3name,
                    this.ndock4name, this.consoleComposite);
            this.dispsize.setSelection(true);
            this.shell.pack();
            this.shell.setRedraw(true);
        }

        // 縮小表示チェック時の動作
        this.dispsize.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Shell shell = ApplicationMain.this.shell;
                shell.setRedraw(false);
                boolean minimum = ApplicationMain.this.dispsize.getSelection();
                // コントロールを隠す
                ApplicationMain.this.hide(minimum, ApplicationMain.this.commandComposite,
                        ApplicationMain.this.deckNotice, ApplicationMain.this.deck1name,
                        ApplicationMain.this.deck2name, ApplicationMain.this.deck3name,
                        ApplicationMain.this.ndockNotice, ApplicationMain.this.ndock1name,
                        ApplicationMain.this.ndock2name, ApplicationMain.this.ndock3name,
                        ApplicationMain.this.ndock4name, ApplicationMain.this.consoleComposite);

                // タブを整理する
                ApplicationMain.this.tabFolder.setSelection(0);

                if (AppConfig.get().isCloseWhenMinimized()) {
                    // 他のウィンドウを連動させる
                    if (minimum) {
                        ApplicationMain.this.childIconified();
                    }
                    else {
                        ApplicationMain.this.childDeiconified();
                    }
                }

                shell.setRedraw(true);
                // ウインドウサイズを調節
                if (minimum) {
                    ApplicationMain.this.tabFolder.setSingle(true);
                    CTabItem[] tabitems = ApplicationMain.this.tabFolder.getItems();
                    for (CTabItem tabitem : tabitems) {
                        Control control = tabitem.getControl();
                        if (control instanceof FleetComposite) {
                            ApplicationMain.this.hide(true, control);
                        }
                    }

                    shell.pack();

                    ApplicationMain.this.tabFolder.setSingle(false);
                    for (CTabItem tabitem : tabitems) {
                        Control control = tabitem.getControl();
                        if (control instanceof FleetComposite) {
                            ApplicationMain.this.hide(false, control);
                        }
                    }
                } else {
                    shell.setSize(ApplicationMain.this.getRestoreSize());
                }
                // 設定を保存
                AppConfig.get().setMinimumLayout(minimum);
            }
        });

        // 選択する項目はドラックで移動できないようにする
        for (Control c : new Control[] { this.commandComposite,
                this.deckNotice, this.ndockNotice,
                this.deck1time, this.deck2time,
                this.deck3time, this.ndock1time, this.ndock2time, this.ndock3time, this.ndock4time,
                this.consoleComposite }) {
            c.setData("disable-drag-move", true);
        }
        this.tabFolder.setData("disable-drag-move-this", true);

        // 処理開始前に必要な値をセット
        BattleResultServer.setLogPath(AppConfig.get().getBattleLogPath());

        this.configUpdated();

        // ホットキー
        JIntellitypeWrapper.addListener(new HotkeyListener() {
            @Override
            public void onHotKey(int arg0) {
                ApplicationMain.this.display.asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        if (ApplicationMain.this.shell.isDisposed() == false) {
                            ApplicationMain.this.shell.forceActive();
                        }
                    }
                });
            }
        });

        sysPrint("ウィンドウ構築完了");

        this.startThread();
        this.updateCheck();
    }

    private void shortcutKeyPushed(int keyCode) {
        switch (keyCode) {
        case 'd':
            this.activate(this.dropReportWindow);
            break;
        case 'y':
            this.activate(this.createShipReportWindow);
            break;
        case 'e':
            this.activate(this.createItemReportWindow);
            break;
        case 't':
            this.activate(this.missionResultWindow);
            break;
        case 'x':
            this.activate(this.itemTableWindow);
            break;
        case '1':
            this.activate(this.shipTableWindows[0]);
            break;
        case 's':
            this.activate(this.shipTableWindows[0]);
            break;
        case '2':
            this.activate(this.shipTableWindows[1]);
            break;
        case '3':
            this.activate(this.shipTableWindows[2]);
            break;
        case '4':
            this.activate(this.shipTableWindows[3]);
            break;
        case 'n':
            this.activate(this.bathwaterTablwWindow);
            break;
        case 'q':
            this.activate(this.questTableWindow);
            break;
        case 'b':
            this.activate(this.battleWindowLarge);
            break;
        case 'h':
            this.activate(this.battleWindowSmall);
            break;
        case 'p':
            this.activate(this.battleShipWindow);
            break;
        case 'c':
            this.activate(this.calcExpWindow);
            break;
        case 'v':
            this.activate(this.calcPracticeExpWindow);
            break;
        case 'g':
            this.activate(this.shipFilterGroupWindow);
            break;
        case 'a':
            this.activate(this.battleCounterWindow);
            break;
        case 'r':
            this.activate(this.resourceChartWindow);
            break;
        case 'z':
            this.shell.setActive();
            break;
        case 'w':
            this.moveWindowsIntoDisplay();
            break;
        }
    }

    private void activate(WindowBase win) {
        if ((win.getShell() == null) || (win.getShell().isVisible() == false)) {
            win.open();
        }
        win.getShell().setActive();
        MenuItem menu = win.getMenuItem();
        if (menu != null) {
            menu.setSelection(true);
        }
    }

    @Override
    public void restore() {
        // メインウィンドウは常に開く
        this.getWindowConfig().setOpened(true);
        super.restore();
    }

    /** ウィンドウ配置を復元 */
    private void restoreWindows() {
        // まずはメインウィンドウを表示する
        this.setVisible(true);
        this.shell.forceActive();
        sysPrint("メインウィンドウ表示完了");
        for (WindowBase window : this.getWindowList()) {
            window.restore();
        }
    }

    private void saveWindows() {
        this.save();
        for (WindowBase window : this.getWindowList()) {
            window.save();
        }
    }

    private void moveWindowsIntoDisplay() {
        this.moveIntoDisplay();
        for (WindowBase window : this.getWindowList()) {
            window.moveIntoDisplay();
        }
    }

    // Main以外のウィンドウも連動させる
    private void childDeiconified() {
        for (Shell shell : ApplicationMain.this.dummyHolder.getShells()) {
            if (shell.getData() instanceof WindowBase) {
                WindowBase window = (WindowBase) shell.getData();
                window.shellDeiconified();
            }
        }
    }

    // Main以外のウィンドウも連動させる
    private void childIconified() {
        for (Shell shell : ApplicationMain.this.dummyHolder.getShells()) {
            if (shell.getData() instanceof WindowBase) {
                WindowBase window = (WindowBase) shell.getData();
                window.shellIconified();
            }
        }
    }

    public WindowBase[] getWindowList() {
        return new WindowBase[] {
                this.captureWindow,
                this.dropReportWindow,
                this.createShipReportWindow,
                this.createItemReportWindow,
                this.missionResultWindow,
                this.missionTableWindow,
                this.itemTableWindow,
                this.shipTableWindows[0],
                this.shipTableWindows[1],
                this.shipTableWindows[2],
                this.shipTableWindows[3],
                this.bathwaterTablwWindow,
                this.questTableWindow,
                this.battleWindowLarge,
                this.battleWindowSmall,
                this.battleShipWindow,
                this.calcExpWindow,
                this.calcPracticeExpWindow,
                this.shipFilterGroupWindow,
                this.resourceChartWindow,
                this.battleCounterWindow,
                this.launcherWindow
        };
    }

    /**
     * トレイアイコンを追加します
     * 
     * @param display
     * @return
     */
    private TrayItem addTrayItem(final Display display) {
        // トレイアイコンを追加します
        Tray tray = display.getSystemTray();
        TrayItem item = new TrayItem(tray, SWT.NONE);
        item.setImage(SWTResourceManager.getImage(WindowBase.class, AppConstants.LOGO));
        item.setToolTipText(AppConstants.TITLEBAR_TEXT);
        item.addListener(SWT.Selection, new TraySelectionListener(this.shell));
        item.addMenuDetectListener(new TrayItemMenuListener());
        return item;
    }

    /**
     * 縮小表示と通常表示とを切り替えます
     * 
     * @param minimum
     * @param controls 隠すコントロール
     */
    private void hide(boolean minimum, Control... controls) {
        for (Control control : controls) {
            LayoutLogic.hide(control, minimum);
        }
        this.shell.layout();
    }

    /**
     * スレッドを開始します
     */
    private void startThread() {
        // 時間のかかる初期化を別スレッドで実行
        new BackgroundInitializer(this.shell).start();

        sysPrint("その他のスレッド起動...");
        // 非同期で画面を更新するスレッド
        ThreadManager.regist(new AsyncExecApplicationMain(this));
        // サウンドを出すスレッド
        ThreadManager.regist(new Sound.PlayerThread());
        // Push通知を行うスレッド
        ThreadManager.regist(new PushNotify.PushNotifyThread());
        // スレッドを監視するスレッド
        ThreadManager.regist(new ThreadStateObserver(this.shell));

        ThreadManager.start();
    }

    private static void endThread() {
        // リソースを開放する
        SWTResourceManager.dispose();
        // プロキシサーバーをシャットダウンする
        ProxyServer.end();
        DatabaseClient.end();
        // ホットキーを解除
        JIntellitypeWrapper.cleanup();
    }

    private void updateCheck() {
        // アップデートチェックする
        final Display display = this.shell.getDisplay();
        new AsyncExecUpdateCheck(new AsyncExecUpdateCheck.UpdateResult() {

            @Override
            public void onSuccess(final String[] okversions) {
                boolean ok = false;
                for (String okversion : okversions) {
                    if (AppConstants.VERSION.equals(okversion)) {
                        ok = true;
                        break;
                    }
                }

                if ((ok == false) && AppConfig.get().isUpdateCheck()) {
                    display.asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            if (ApplicationMain.this.shell.isDisposed()) {
                                // ウインドウが閉じられていたらなにもしない
                                return;
                            }

                            MessageBox box = new MessageBox(ApplicationMain.this.shell, SWT.YES | SWT.NO
                                    | SWT.ICON_QUESTION);
                            box.setText("新しいバージョン");
                            box.setMessage("新しいバージョンがあります。ホームページを開きますか？\r\n"
                                    + "現在のバージョン:" + AppConstants.VERSION + "\r\n"
                                    + "新しいバージョン:" + okversions[0] + "\r\n"
                                    + "※自動アップデートチェックは[その他]-[設定]からOFFに出来ます");

                            // OKを押されたらホームページへ移動する
                            if (box.open() == SWT.YES) {
                                try {
                                    Desktop.getDesktop().browse(AppConstants.HOME_PAGE_URI);
                                } catch (Exception e) {
                                    LOG.warn("ウェブサイトに移動が失敗しました", e);
                                }
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                // チェックしなくてもいい設定の場合はエラーを無視する
                if (AppConfig.get().isUpdateCheck()) {
                    // アップデートチェック失敗はクラス名のみ
                    LOG.info(e.getClass().getName() + "が原因でアップデートチェックに失敗しました");
                }
            }
        }).start();
    }

    @Override
    protected boolean shouldSaveWindowSize() {
        return !AppConfig.get().isMinimumLayout();
    }

    @Override
    protected Point getDefaultSize() {
        return new Point(280, 420);
    }

    /**
     * 設定が更新された
     */
    public void configUpdated() {
        String[] shipNames = AppConfig.get().getShipTableNames();
        for (int i = 0; i < shipNames.length; ++i) {
            int number = i + 1;
            if ((shipNames[i] == null) || (shipNames[i].length() == 0)) {
                shipNames[i] = "艦娘一覧 " + number;
            }
            String menuTitle = (i == 0) ? (shipNames[i] + "(&S)\tCtrl+S")
                    : (shipNames[i] + "(&" + number + ")\tCtrl+" + number);
            this.shipTableWindows[i].getMenuItem().setText(menuTitle);
            this.shipTableWindows[i].windowTitleChanged();
        }
        JIntellitypeWrapper.changeSetting(AppConfig.get().getSystemWideHotKey());
        // プロキシサーバ再起動
        ProxyServer.restart();
    }

    public void setTitleText(String newText) {
        if ((this.titleText == null) || (this.titleText.equals(newText) == false)) {
            this.shell.setText(newText);
            this.trayItem.setText(newText);
            this.titleText = newText;
        }
    }

    /**
     * @return トレイアイコン
     */
    public TrayItem getTrayItem() {
        return this.trayItem;
    }

    /**
     * @return コマンドボタン
     */
    public Composite getCommandComposite() {
        return this.commandComposite;
    }

    /**
     * @return 所有装備
     */
    public Button getItemList() {
        return this.itemList;
    }

    /**
     * @return 所有艦娘
     */
    public Button getShipList() {
        return this.shipList;
    }

    /**
     * @return タブフォルダ
     */
    public CTabFolder getTabFolder() {
        return this.tabFolder;
    }

    /**
     * @return メインコンポジット
     */
    public Composite getMainComposite() {
        return this.mainComposite;
    }

    /**
     * @return 遠征グループ
     */
    public Group getDeckGroup() {
        return this.deckGroup;
    }

    /**
     * @return 1分前に通知する(遠征)
     */
    public Button getDeckNotice() {
        return this.deckNotice;
    }

    /**
     * @return 遠征.艦隊2の艦隊名
     */
    public Label getDeck1name() {
        return this.deck1name;
    }

    /**
     * @return 遠征.艦隊2の帰投時間
     */
    public Text getDeck1time() {
        return this.deck1time;
    }

    /**
     * @return 遠征.艦隊3の艦隊名
     */
    public Label getDeck2name() {
        return this.deck2name;
    }

    /**
     * @return 遠征.艦隊3の帰投時間
     */
    public Text getDeck2time() {
        return this.deck2time;
    }

    /**
     * @return 遠征.艦隊4の艦隊名
     */
    public Label getDeck3name() {
        return this.deck3name;
    }

    /**
     * @return 遠征.艦隊4の帰投時間
     */
    public Text getDeck3time() {
        return this.deck3time;
    }

    /**
     * @return 入渠グループ
     */
    public Group getNdockGroup() {
        return this.ndockGroup;
    }

    /**
     * @return 1分前に通知する(入渠)
     */
    public Button getNdockNotice() {
        return this.ndockNotice;
    }

    /**
     * @return 入渠.ドッグ1.艦娘の名前
     */
    public Label getNdock1name() {
        return this.ndock1name;
    }

    /**
     * @return 入渠.ドッグ1.お風呂から上がる時間
     */
    public Text getNdock1time() {
        return this.ndock1time;
    }

    /**
     * @return 入渠.ドッグ2.艦娘の名前
     */
    public Label getNdock2name() {
        return this.ndock2name;
    }

    /**
     * @return 入渠.ドッグ2.お風呂から上がる時間
     */
    public Text getNdock2time() {
        return this.ndock2time;
    }

    /**
     * @return 入渠.ドッグ3.艦娘の名前
     */
    public Label getNdock3name() {
        return this.ndock3name;
    }

    /**
     * @return 入渠.ドッグ3.お風呂から上がる時間
     */
    public Text getNdock3time() {
        return this.ndock3time;
    }

    /**
     * @return 入渠.ドッグ4.艦娘の名前
     */
    public Label getNdock4name() {
        return this.ndock4name;
    }

    /**
     * @return 入渠.ドッグ4.お風呂から上がる時間
     */
    public Text getNdock4time() {
        return this.ndock4time;
    }

    /**
     * エラーラベルを取得
     * @return
     */
    public Label getErrorLabel() {
        return this.errorLabel;
    }

    /**
     * @return コンソールコンポジット
     */
    public Composite getConsoleComposite() {
        return this.consoleComposite;
    }

    /**
     * コンソールを更新します
     * @param message コンソールに表示するメッセージ
     */
    public void printMessage(final String message) {
        if (disableUpdate || this.console.isDisposed())
            return;
        int size = this.console.getItemCount();
        if (size >= MAX_LOG_LINES) {
            this.console.remove(0);
        }
        this.console.add(LOG_DATE_FORMAT.format(new Date()) + "  " + message);
        this.console.setSelection(this.console.getItemCount() - 1);
    }

    // 出撃更新 //

    private List<DockDto> getSortieDocks() {
        boolean[] isSortie = GlobalContext.getIsSortie();
        if (GlobalContext.isCombined() && isSortie[0]) {
            // 連合艦隊
            return Arrays.asList(new DockDto[] {
                    GlobalContext.getDock("1"),
                    GlobalContext.getDock("2")
            });
        }
        for (int i = 0; i < 4; i++) {
            if (isSortie[i]) {
                DockDto dock = GlobalContext.getDock(Integer.toString(i + 1));
                return Arrays.asList(new DockDto[] { dock });
            }
        }
        return null;
    }

    private BattleWindowBase[] getBattleWindowList() {
        return new BattleWindowBase[] {
                this.battleWindowLarge,
                this.battleWindowSmall,
                this.battleShipWindow
        };
    }

    public void startSortie() {
        List<DockDto> sortieDocks = this.getSortieDocks();
        if (sortieDocks != null) {
            for (BattleWindowBase window : this.getBattleWindowList()) {
                window.updateSortieDock(sortieDocks);
            }
        }
    }

    public void endSortie() {
        for (BattleWindowBase window : this.getBattleWindowList()) {
            window.endSortie();
        }
    }

    public void updateSortieDock() {
        List<DockDto> sortieDocks = this.getSortieDocks();
        if (sortieDocks != null) {
            for (BattleWindowBase window : this.getBattleWindowList()) {
                window.updateSortieDock(sortieDocks);
            }
        }
    }

    public void updateMapCell(MapCellDto mapCellDto) {
        for (BattleWindowBase window : this.getBattleWindowList()) {
            window.updateMapCell(mapCellDto);
        }
    }

    public void updateBattle(BattleExDto battleDto) {
        for (BattleWindowBase window : this.getBattleWindowList()) {
            window.updateBattle(battleDto);
        }
    }

    public void updateCalcPracticeExp(PracticeUserDetailDto practiceUserExDto) {
        this.calcPracticeExpWindow.updatePracticeUser(practiceUserExDto);
    }
}
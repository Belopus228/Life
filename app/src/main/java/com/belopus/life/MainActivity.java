package com.belopus.life;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.belopus.life.engine.CellRegion;
import com.belopus.life.engine.LifeEngine;
import com.belopus.life.engine.PatternLibrary;
import com.belopus.life.engine.RleCodec;
import com.belopus.life.engine.RuleSet;
import com.belopus.life.engine.SimStats;
import com.belopus.life.engine.Symmetry;
import com.belopus.life.engine.Topology;
import com.belopus.life.storage.ExportProvider;
import com.belopus.life.storage.PatternStorage;
import com.belopus.life.ui.LifeCanvasView;
import com.belopus.life.ui.PatternBrowserDialog;
import com.belopus.life.ui.PopulationChartView;
import com.belopus.life.ui.RandomSoupDialog;
import com.belopus.life.ui.SettingsDialog;
import com.belopus.life.ui.SoundEngine;
import com.belopus.life.ui.ThemeManager;
import com.belopus.life.ui.UiHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_OPEN_PATTERN = 1001;
    private static final int REQ_SAVE_RLE = 1002;
    private static final int REQ_SAVE_PNG = 1003;

    private LifeEngine engine;
    private PatternStorage storage;
    private PatternStorage.AppSettings settings;
    private ThemeManager.Theme theme;
    private final SoundEngine soundEngine = new SoundEngine();

    private FrameLayout rootLayout;
    private LifeCanvasView canvasView;
    private LinearLayout topHudContainer;
    private LinearLayout topHudCard;
    private LinearLayout bottomDockContainer;
    private LinearLayout bottomDockCard;
    private LinearLayout selectionBar;
    private LinearLayout stampBar;

    // Top HUD views
    private TextView tvRuleBadge;
    private TextView tvStatusBadge;
    private TextView tvGenCount;
    private TextView tvPopCount;
    private TextView tvDeltaCount;
    private TextView btnStats;
    private TextView btnPatterns;
    private TextView btnMenu;

    // Bottom Dock views
    private TextView btnPanToggle;
    private TextView btnToolPicker;
    private TextView btnSymmetry;
    private SeekBar sbSpeed;
    private TextView tvSpeedVal;

    private TextView btnUndo;
    private TextView btnRedo;
    private TextView btnResetGen0;
    private TextView btnPlayPause;
    private TextView btnStep;
    private TextView btnSoup;
    private TextView btnFit;

    // Stamp bar views
    private TextView tvStampTitle;
    private TextView btnStampMerge;

    // Selection bar views
    private TextView tvSelectionInfo;

    private LifeCanvasView.ToolMode lastDrawTool = LifeCanvasView.ToolMode.DRAW;
    private CellRegion clipboardRegion = null;
    private String pendingSaveRle = null;
    private boolean pendingAutoFit = false;

    // Simulation loop state
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;
    private long lastTickNs = 0L;
    private double stepAccumulator = 0.0;
    private volatile boolean isJumping = false;

    private final Runnable simLoopRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;
            long now = SystemClock.elapsedRealtimeNanos();
            if (lastTickNs == 0L) lastTickNs = now;
            double dtSec = Math.min(0.2, Math.max(0.0, (now - lastTickNs) / 1_000_000_000.0));
            lastTickNs = now;

            stepAccumulator += dtSec * settings.simSpeed;
            int stepsToRun = (int) Math.floor(stepAccumulator);
            if (stepsToRun > 0) {
                stepAccumulator -= stepsToRun;
                int cap = Math.min(stepsToRun, 24);
                long startMs = SystemClock.uptimeMillis();
                for (int i = 0; i < cap; i++) {
                    engine.step();
                    if (SystemClock.uptimeMillis() - startMs >= 11L) {
                        stepAccumulator = 0.0;
                        break;
                    }
                }
                SimStats st = engine.getStats();
                soundEngine.playStepPulse(st.lastBirths, st.lastDeaths);

                if (settings.autoPauseOnStable && (st.status == SimStats.Status.EXTINCT
                        || st.status == SimStats.Status.STILL_LIFE
                        || st.status == SimStats.Status.OSCILLATOR)) {
                    setPlaying(false);
                    soundEngine.playMilestone();
                    Toast.makeText(MainActivity.this,
                            (settings.languageRu ? "Автопауза: " : "Auto-paused: ") + st.getStatusText(settings.languageRu),
                            Toast.LENGTH_SHORT).show();
                }

                canvasView.invalidate();
                updateTelemetry();
            }

            if (isPlaying) {
                handler.postDelayed(this, 16L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        storage = new PatternStorage(this);
        settings = storage.loadSettings();
        theme = ThemeManager.byId(settings.themeId);
        soundEngine.setEnabled(settings.soundEnabled);

        engine = new LifeEngine();
        RuleSet r = RuleSet.parse(settings.ruleCode);
        engine.configureTopology(settings.topology, settings.gridWidth, settings.gridHeight, false);
        engine.setRule(r != null ? r : RuleSet.CONWAY);
        engine.setDecaySteps(settings.decaySteps);

        boolean restored = storage.restoreAutosave(engine);
        if (!restored) {
            PatternLibrary.PatternEntry gun = PatternLibrary.findById("gosper_gun");
            if (gun != null) {
                CellRegion reg = gun.getRegion();
                int ox = engine.getTopology() == Topology.INFINITE
                        ? -reg.width / 2
                        : (engine.getGridWidth() - reg.width) / 2;
                int oy = engine.getTopology() == Topology.INFINITE
                        ? -reg.height / 2
                        : (engine.getGridHeight() - reg.height) / 2;
                engine.pasteRegion(reg, ox, oy, true, false);
                engine.markCurrentAsGen0();
            }
        }
        pendingAutoFit = true;

        buildUi();
        applyThemeToUi();
        updateTelemetry();
        updateTransportButtons();

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            importPatternFromUri(data);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        setPlaying(false);
        storage.saveSettings(settings);
        storage.saveAutosave(engine);
    }

    private void buildUi() {
        rootLayout = new FrameLayout(this);

        // 1. Fullscreen Simulation Canvas
        canvasView = new LifeCanvasView(this);
        canvasView.attachEngine(engine);
        canvasView.setTheme(theme);
        canvasView.setSoundEngine(soundEngine);
        canvasView.setVisualOptions(settings.cellShape, settings.ageColoring, settings.showGrid,
                settings.showAxes, settings.showMinimap);
        canvasView.setToolMode(lastDrawTool);
        canvasView.setListener(new LifeCanvasView.Listener() {
            @Override
            public void onCanvasEdited() {
                updateTelemetry();
                updateTransportButtons();
            }

            @Override
            public void onSelectionChanged(boolean hasSel, int w, int h) {
                updateContextualBars();
                if (hasSel) {
                    tvSelectionInfo.setText(w + "×" + h);
                }
            }

            @Override
            public void onStampMoved(int x, int y) {
                updateContextualBars();
            }

            @Override
            public void onToolFeedback(String msg) {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
        canvasView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (pendingAutoFit && (r - l) > 0 && (b - t) > 0) {
                    pendingAutoFit = false;
                    canvasView.fitToPattern();
                }
            }
        });
        rootLayout.addView(canvasView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Top Telemetry HUD
        topHudContainer = new LinearLayout(this);
        topHudContainer.setOrientation(LinearLayout.VERTICAL);
        topHudContainer.setPadding(UiHelper.dp(this, 10), UiHelper.dp(this, 8), UiHelper.dp(this, 10), 0);

        topHudCard = new LinearLayout(this);
        topHudCard.setOrientation(LinearLayout.VERTICAL);
        topHudCard.setPadding(UiHelper.dp(this, 12), UiHelper.dp(this, 8), UiHelper.dp(this, 10), UiHelper.dp(this, 8));

        // Row 1 of Top HUD: Rule badge, Status badge, Quick icon buttons
        LinearLayout topRow1 = new LinearLayout(this);
        topRow1.setOrientation(LinearLayout.HORIZONTAL);
        topRow1.setGravity(Gravity.CENTER_VERTICAL);

        tvRuleBadge = new TextView(this);
        tvRuleBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvRuleBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRuleBadge.setSingleLine(true);
        tvRuleBadge.setPadding(UiHelper.dp(this, 8), UiHelper.dp(this, 4), UiHelper.dp(this, 8), UiHelper.dp(this, 4));
        tvRuleBadge.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(); }
        });
        topRow1.addView(tvRuleBadge);

        tvStatusBadge = new TextView(this);
        tvStatusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvStatusBadge.setSingleLine(true);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sblp.leftMargin = UiHelper.dp(this, 8);
        tvStatusBadge.setLayoutParams(sblp);
        topRow1.addView(tvStatusBadge);

        btnStats = makeHudIconBtn("📊");
        btnStats.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                PopulationChartView.showStatsDialog(MainActivity.this, engine, theme, settings.languageRu);
            }
        });
        topRow1.addView(btnStats);

        btnPatterns = makeHudIconBtn("🧩");
        btnPatterns.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPatternBrowser(); }
        });
        topRow1.addView(btnPatterns);

        btnMenu = makeHudIconBtn("☰");
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openMainMenu(); }
        });
        topRow1.addView(btnMenu);

        topHudCard.addView(topRow1);

        // Row 2 of Top HUD: Counters (Generation, Population, Birth/Death Delta)
        LinearLayout topRow2 = new LinearLayout(this);
        topRow2.setOrientation(LinearLayout.HORIZONTAL);
        topRow2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tr2lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tr2lp.topMargin = UiHelper.dp(this, 5);
        topRow2.setLayoutParams(tr2lp);

        tvGenCount = new TextView(this);
        tvGenCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvGenCount.setTypeface(null, android.graphics.Typeface.BOLD);
        tvGenCount.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow2.addView(tvGenCount);

        tvPopCount = new TextView(this);
        tvPopCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvPopCount.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPopCount.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow2.addView(tvPopCount);

        tvDeltaCount = new TextView(this);
        tvDeltaCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvDeltaCount.setGravity(Gravity.END);
        topRow2.addView(tvDeltaCount);

        topHudCard.addView(topRow2);
        topHudContainer.addView(topHudCard);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        rootLayout.addView(topHudContainer, topLp);

        // 3. Bottom Control Dock (+ Contextual Selection & Stamp Bars above it)
        bottomDockContainer = new LinearLayout(this);
        bottomDockContainer.setOrientation(LinearLayout.VERTICAL);
        bottomDockContainer.setPadding(UiHelper.dp(this, 10), 0, UiHelper.dp(this, 10), UiHelper.dp(this, 10));

        buildSelectionBar();
        buildStampBar();
        bottomDockContainer.addView(selectionBar);
        bottomDockContainer.addView(stampBar);

        bottomDockCard = new LinearLayout(this);
        bottomDockCard.setOrientation(LinearLayout.VERTICAL);
        bottomDockCard.setPadding(UiHelper.dp(this, 10), UiHelper.dp(this, 8), UiHelper.dp(this, 10), UiHelper.dp(this, 8));

        // Bottom Row 1: Pan/Draw toggle, Tool picker, Symmetry, Speed Slider
        LinearLayout botRow1 = new LinearLayout(this);
        botRow1.setOrientation(LinearLayout.HORIZONTAL);
        botRow1.setGravity(Gravity.CENTER_VERTICAL);

        btnPanToggle = makeDockBtn("✏");
        btnPanToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (canvasView.getToolMode() == LifeCanvasView.ToolMode.PAN) {
                    canvasView.setToolMode(lastDrawTool);
                } else {
                    if (canvasView.getToolMode() != LifeCanvasView.ToolMode.STAMP) {
                        lastDrawTool = canvasView.getToolMode();
                    }
                    canvasView.setToolMode(LifeCanvasView.ToolMode.PAN);
                }
                updateToolButtons();
            }
        });
        botRow1.addView(btnPanToggle);

        btnToolPicker = makeDockBtn("Инстр.");
        btnToolPicker.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showToolPickerDialog(); }
        });
        botRow1.addView(btnToolPicker);

        btnSymmetry = makeDockBtn("Симм");
        btnSymmetry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                canvasView.setSymmetry(canvasView.getSymmetry().next());
                updateToolButtons();
            }
        });
        botRow1.addView(btnSymmetry);

        sbSpeed = new SeekBar(this);
        sbSpeed.setMax(100);
        sbSpeed.setProgress(speedToSlider(settings.simSpeed));
        LinearLayout.LayoutParams sblp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sblp2.leftMargin = UiHelper.dp(this, 4);
        sbSpeed.setLayoutParams(sblp2);
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (fromUser) {
                    settings.simSpeed = sliderToSpeed(p);
                    updateSpeedLabel();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                storage.saveSettings(settings);
            }
        });
        botRow1.addView(sbSpeed);

        tvSpeedVal = new TextView(this);
        tvSpeedVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSpeedVal.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSpeedVal.setGravity(Gravity.END);
        tvSpeedVal.setMinWidth(UiHelper.dp(this, 42));
        tvSpeedVal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSpeedPresetsDialog(); }
        });
        botRow1.addView(tvSpeedVal);

        bottomDockCard.addView(botRow1);

        // Bottom Row 2: Undo, Redo, Gen0 Reset, PLAY/PAUSE, Step (+1 / long-press +N), Soup, Fit
        LinearLayout botRow2 = new LinearLayout(this);
        botRow2.setOrientation(LinearLayout.HORIZONTAL);
        botRow2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams br2lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        br2lp.topMargin = UiHelper.dp(this, 7);
        botRow2.setLayoutParams(br2lp);

        btnUndo = makeTransportBtn("↶", 1f);
        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setPlaying(false);
                if (engine.undo()) {
                    canvasView.invalidate();
                    updateTelemetry();
                    updateTransportButtons();
                }
            }
        });
        botRow2.addView(btnUndo);

        btnRedo = makeTransportBtn("↷", 1f);
        btnRedo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setPlaying(false);
                if (engine.redo()) {
                    canvasView.invalidate();
                    updateTelemetry();
                    updateTransportButtons();
                }
            }
        });
        botRow2.addView(btnRedo);

        btnResetGen0 = makeTransportBtn("⏮", 1f);
        btnResetGen0.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setPlaying(false);
                if (engine.resetToGen0()) {
                    canvasView.invalidate();
                    updateTelemetry();
                    updateTransportButtons();
                    toast(settings.languageRu ? "Возврат к поколению 0" : "Reset to Generation 0");
                }
            }
        });
        botRow2.addView(btnResetGen0);

        btnPlayPause = makeTransportBtn("▶", 1.45f);
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setPlaying(!isPlaying);
            }
        });
        botRow2.addView(btnPlayPause);

        btnStep = makeTransportBtn("⏭", 1f);
        btnStep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setPlaying(false);
                engine.step();
                soundEngine.playStepPulse(engine.getStats().lastBirths, engine.getStats().lastDeaths);
                canvasView.invalidate();
                updateTelemetry();
                updateTransportButtons();
            }
        });
        btnStep.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showJumpDialog();
                return true;
            }
        });
        botRow2.addView(btnStep);

        btnSoup = makeTransportBtn("🎲", 1f);
        btnSoup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setPlaying(false);
                RandomSoupDialog.show(MainActivity.this, theme,
                        engine.getTopology(), engine.getGridWidth(), engine.getGridHeight(),
                        settings.languageRu, new RandomSoupDialog.Callback() {
                            @Override
                            public void onGenerateSoup(int w, int h, float density, Symmetry sym, boolean clearFirst) {
                                int ox, oy;
                                if (engine.getTopology() == Topology.INFINITE) {
                                    ox = -w / 2;
                                    oy = -h / 2;
                                    if (clearFirst) engine.clear(true);
                                    engine.fillRandomRegion(ox, oy, w, h, density, sym, false, !clearFirst);
                                } else {
                                    ox = (engine.getGridWidth() - w) / 2;
                                    oy = (engine.getGridHeight() - h) / 2;
                                    if (clearFirst) engine.clear(true);
                                    engine.fillRandomRegion(ox, oy, w, h, density, sym, false, !clearFirst);
                                }
                                engine.markCurrentAsGen0();
                                canvasView.fitToPattern();
                                updateTelemetry();
                                updateTransportButtons();
                            }
                        });
            }
        });
        botRow2.addView(btnSoup);

        btnFit = makeTransportBtn("🎯", 1f);
        btnFit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                canvasView.fitToPattern();
            }
        });
        btnFit.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                canvasView.centerCameraDefault();
                return true;
            }
        });
        botRow2.addView(btnFit);

        bottomDockCard.addView(botRow2);
        bottomDockContainer.addView(bottomDockCard);

        FrameLayout.LayoutParams botLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        rootLayout.addView(bottomDockContainer, botLp);

        // Apply safe system bar WindowInsets
        rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top;
                int bot;
                int left;
                int right;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                    top = sys.top;
                    bot = sys.bottom;
                    left = sys.left;
                    right = sys.right;
                } else {
                    top = getInsetLegacy(insets, 0);
                    bot = getInsetLegacy(insets, 1);
                    left = getInsetLegacy(insets, 2);
                    right = getInsetLegacy(insets, 3);
                }
                topHudContainer.setPadding(
                        UiHelper.dp(MainActivity.this, 10) + left,
                        UiHelper.dp(MainActivity.this, 8) + top,
                        UiHelper.dp(MainActivity.this, 10) + right, 0);
                bottomDockContainer.setPadding(
                        UiHelper.dp(MainActivity.this, 10) + left, 0,
                        UiHelper.dp(MainActivity.this, 10) + right,
                        UiHelper.dp(MainActivity.this, 10) + bot);
                return insets;
            }
        });

        setContentView(rootLayout);
        updateToolButtons();
        updateSpeedLabel();
        updateContextualBars();
    }

    private void buildSelectionBar() {
        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionBar.setPadding(UiHelper.dp(this, 8), UiHelper.dp(this, 6), UiHelper.dp(this, 8), UiHelper.dp(this, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(this, 6);
        selectionBar.setLayoutParams(lp);
        selectionBar.setVisibility(View.GONE);
    }

    private void rebuildSelectionButtons() {
        boolean ru = settings.languageRu;
        selectionBar.removeAllViews();

        tvSelectionInfo = new TextView(this);
        tvSelectionInfo.setTextColor(theme.accentColor);
        tvSelectionInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSelectionInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSelectionInfo.setPadding(UiHelper.dp(this, 4), 0, UiHelper.dp(this, 8), 0);
        if (canvasView.hasSelection()) {
            int[] b = canvasView.getSelectionBounds();
            tvSelectionInfo.setText((b[2] - b[0] + 1) + "×" + (b[3] - b[1] + 1));
        }
        selectionBar.addView(tvSelectionInfo);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        addCtxPill(row, ru ? "Копир." : "Copy", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                clipboardRegion = engine.extractRegion(b[0], b[1], b[2], b[3]).trimmed();
                if (clipboardRegion.isEmpty()) {
                    toast(settings.languageRu ? "Выделенная область пуста" : "Selection is empty");
                } else {
                    canvasView.clearSelection();
                    canvasView.enterStampMode(clipboardRegion, settings.languageRu ? "Буфер" : "Clipboard");
                    updateToolButtons();
                    updateContextualBars();
                }
            }
        });
        addCtxPill(row, ru ? "Вырез." : "Cut", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                clipboardRegion = engine.extractRegion(b[0], b[1], b[2], b[3]).trimmed();
                engine.clearRegion(b[0], b[1], b[2], b[3], true);
                canvasView.clearSelection();
                if (!clipboardRegion.isEmpty()) {
                    canvasView.enterStampMode(clipboardRegion, settings.languageRu ? "Буфер" : "Clipboard");
                }
                updateTelemetry();
                updateTransportButtons();
                updateToolButtons();
                updateContextualBars();
            }
        });
        addCtxPill(row, "↻ 90°", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                CellRegion reg = engine.extractRegion(b[0], b[1], b[2], b[3]).rotateCW();
                engine.pushUndo();
                engine.clearRegion(b[0], b[1], b[2], b[3], false);
                engine.pasteRegion(reg, b[0], b[1], true, false);
                canvasView.setSelectionBounds(b[0], b[1], b[0] + reg.width - 1, b[1] + reg.height - 1);
                updateTelemetry();
                updateTransportButtons();
            }
        });
        addCtxPill(row, "⇄", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                CellRegion reg = engine.extractRegion(b[0], b[1], b[2], b[3]).flipH();
                engine.pasteRegion(reg, b[0], b[1], false, true);
                canvasView.invalidate();
                updateTelemetry();
                updateTransportButtons();
            }
        });
        addCtxPill(row, ru ? "Инверт." : "Invert", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                engine.invertRegion(b[0], b[1], b[2], b[3], true);
                canvasView.invalidate();
                updateTelemetry();
                updateTransportButtons();
            }
        });
        addCtxPill(row, ru ? "Суп" : "Rand", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                engine.fillRandomRegion(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1,
                        0.32f, Symmetry.NONE, false, true);
                canvasView.invalidate();
                updateTelemetry();
                updateTransportButtons();
            }
        });
        addCtxPill(row, ru ? "Сохранить" : "Save", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                CellRegion reg = engine.extractRegion(b[0], b[1], b[2], b[3]).trimmed();
                if (reg.isEmpty()) {
                    toast(settings.languageRu ? "Выделение пусто" : "Selection is empty");
                    return;
                }
                promptSaveCustomPattern(reg);
            }
        });
        addCtxPill(row, "RLE", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                CellRegion reg = engine.extractRegion(b[0], b[1], b[2], b[3]).trimmed();
                String rle = RleCodec.encodeRle(reg, engine.getRule(), "Selection", "");
                copyText(rle, settings.languageRu ? "RLE выделения скопирован" : "Selection RLE copied");
            }
        });
        addCtxPill(row, ru ? "Очист." : "Clear", new Runnable() {
            @Override public void run() {
                int[] b = canvasView.getSelectionBounds();
                engine.clearRegion(b[0], b[1], b[2], b[3], true);
                canvasView.invalidate();
                updateTelemetry();
                updateTransportButtons();
            }
        });

        hsv.addView(row);
        selectionBar.addView(hsv);

        TextView closeBtn = UiHelper.makePillButton(this, "✕", theme.hudTextColor, theme.bgColor, theme.hudBorderColor, 12f, 10, 5);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                canvasView.clearSelection();
            }
        });
        selectionBar.addView(closeBtn);
    }

    private void buildStampBar() {
        stampBar = new LinearLayout(this);
        stampBar.setOrientation(LinearLayout.HORIZONTAL);
        stampBar.setGravity(Gravity.CENTER_VERTICAL);
        stampBar.setPadding(UiHelper.dp(this, 8), UiHelper.dp(this, 6), UiHelper.dp(this, 8), UiHelper.dp(this, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(this, 6);
        stampBar.setLayoutParams(lp);
        stampBar.setVisibility(View.GONE);
    }

    private void rebuildStampButtons() {
        boolean ru = settings.languageRu;
        stampBar.removeAllViews();

        tvStampTitle = new TextView(this);
        tvStampTitle.setTextColor(theme.accentColor);
        tvStampTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStampTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStampTitle.setSingleLine(true);
        tvStampTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String sn = canvasView.getStampName();
        tvStampTitle.setText((sn.isEmpty() ? (ru ? "Штамп" : "Stamp") : sn));
        stampBar.addView(tvStampTitle);

        addCtxPill(stampBar, "↻ 90°", new Runnable() {
            @Override public void run() { canvasView.rotateStampCW(); }
        });
        addCtxPill(stampBar, "⇄", new Runnable() {
            @Override public void run() { canvasView.flipStampH(); }
        });

        btnStampMerge = UiHelper.makePillButton(this,
                canvasView.isStampMerge() ? (ru ? "Слиян." : "Merge") : (ru ? "Замена" : "Over"),
                theme.hudTextColor, theme.bgColor, theme.hudBorderColor, 11f, 8, 5);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.rightMargin = UiHelper.dp(this, 4);
        btnStampMerge.setLayoutParams(mlp);
        btnStampMerge.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                canvasView.setStampMerge(!canvasView.isStampMerge());
                rebuildStampButtons();
            }
        });
        stampBar.addView(btnStampMerge);

        TextView btnPlace = UiHelper.makePillButton(this, "✓",
                theme.accentTextColor, theme.accentColor, theme.accentColor, 13f, 12, 5);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.rightMargin = UiHelper.dp(this, 4);
        btnPlace.setLayoutParams(plp);
        btnPlace.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                canvasView.commitStamp();
            }
        });
        stampBar.addView(btnPlace);

        TextView btnDone = UiHelper.makePillButton(this, "✕",
                theme.hudTextColor, theme.bgColor, theme.hudBorderColor, 12f, 10, 5);
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                canvasView.exitStampMode();
                updateToolButtons();
                updateContextualBars();
            }
        });
        stampBar.addView(btnDone);
    }

    private void addCtxPill(LinearLayout row, String label, final Runnable action) {
        TextView btn = UiHelper.makePillButton(this, label, theme.hudTextColor, theme.bgColor, theme.hudBorderColor, 11f, 9, 5);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(this, 4);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        row.addView(btn);
    }

    private TextView makeHudIconBtn(String icon) {
        TextView tv = new TextView(this);
        tv.setText(icon);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setGravity(Gravity.CENTER);
        int sz = UiHelper.dp(this, 32);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        lp.leftMargin = UiHelper.dp(this, 5);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView makeDockBtn(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(UiHelper.dp(this, 9), UiHelper.dp(this, 6), UiHelper.dp(this, 9), UiHelper.dp(this, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(this, 5);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView makeTransportBtn(String icon, float weight) {
        TextView tv = new TextView(this);
        tv.setText(icon);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, UiHelper.dp(this, 8), 0, UiHelper.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.leftMargin = UiHelper.dp(this, 3);
        lp.rightMargin = UiHelper.dp(this, 3);
        tv.setLayoutParams(lp);
        return tv;
    }

    @SuppressWarnings("deprecation")
    private static int getInsetLegacy(WindowInsets insets, int which) {
        if (which == 0) return insets.getSystemWindowInsetTop();
        if (which == 1) return insets.getSystemWindowInsetBottom();
        if (which == 2) return insets.getSystemWindowInsetLeft();
        return insets.getSystemWindowInsetRight();
    }

    @SuppressWarnings("deprecation")
    private void applyThemeToUi() {
        getWindow().setStatusBarColor(theme.bgColor);
        getWindow().setNavigationBarColor(theme.bgColor);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ctrl = getWindow().getInsetsController();
            if (ctrl != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctrl.setSystemBarsAppearance(theme.isLight ? mask : 0, mask);
            }
        } else {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (theme.isLight) flags |= lightFlags;
            else flags &= ~lightFlags;
            decor.setSystemUiVisibility(flags);
        }

        canvasView.setTheme(theme);

        topHudCard.setBackground(UiHelper.roundedBox(theme.hudBgColor, theme.hudBorderColor, UiHelper.dp(this, 1), UiHelper.dp(this, 16)));
        bottomDockCard.setBackground(UiHelper.roundedBox(theme.hudBgColor, theme.hudBorderColor, UiHelper.dp(this, 1), UiHelper.dp(this, 18)));
        selectionBar.setBackground(UiHelper.roundedBox(theme.hudBgColor, theme.accentColor, UiHelper.dp(this, 1), UiHelper.dp(this, 14)));
        stampBar.setBackground(UiHelper.roundedBox(theme.hudBgColor, theme.accentColor, UiHelper.dp(this, 1), UiHelper.dp(this, 14)));

        tvRuleBadge.setTextColor(theme.accentTextColor);
        tvRuleBadge.setBackground(UiHelper.roundedBox(theme.accentColor, theme.accentColor, 0, UiHelper.dp(this, 10)));
        tvStatusBadge.setTextColor(theme.hudSubTextColor);
        tvGenCount.setTextColor(theme.hudTextColor);
        tvPopCount.setTextColor(theme.hudTextColor);
        tvDeltaCount.setTextColor(theme.hudSubTextColor);
        tvSpeedVal.setTextColor(theme.accentColor);

        for (TextView b : new TextView[]{btnStats, btnPatterns, btnMenu}) {
            b.setTextColor(theme.hudTextColor);
            b.setBackground(UiHelper.ripplePill(theme.bgColor, theme.hudBorderColor, UiHelper.dp(this, 1), UiHelper.dp(this, 10), 0x33FFFFFF));
        }

        rebuildSelectionButtons();
        rebuildStampButtons();
        updateToolButtons();
        updateTransportButtons();
    }

    private void updateTelemetry() {
        boolean ru = settings.languageRu;
        SimStats st = engine.getStats();

        String topStr = engine.getTopology() == Topology.INFINITE
                ? (ru ? "∞" : "∞")
                : (engine.getGridWidth() + "×" + engine.getGridHeight());
        tvRuleBadge.setText(engine.getRule().getRuleCode() + " · " + topStr);
        tvStatusBadge.setText(st.getStatusText(ru));
        tvGenCount.setText((ru ? "Пок: " : "Gen: ") + st.generation);
        tvPopCount.setText((ru ? "Кл: " : "Pop: ") + st.liveCount);
        tvDeltaCount.setText("+" + st.lastBirths + " / -" + st.lastDeaths);
    }

    private void updateContextualBars() {
        selectionBar.setVisibility(canvasView.hasSelection() ? View.VISIBLE : View.GONE);
        stampBar.setVisibility(canvasView.getToolMode() == LifeCanvasView.ToolMode.STAMP ? View.VISIBLE : View.GONE);
        if (canvasView.getToolMode() == LifeCanvasView.ToolMode.STAMP && tvStampTitle != null) {
            boolean ru = settings.languageRu;
            String sn = canvasView.getStampName();
            tvStampTitle.setText(sn.isEmpty() ? (ru ? "Штамп" : "Stamp") : sn);
        }
    }

    private void updateToolButtons() {
        boolean ru = settings.languageRu;
        LifeCanvasView.ToolMode m = canvasView.getToolMode();
        boolean isPan = (m == LifeCanvasView.ToolMode.PAN);

        btnPanToggle.setText(isPan ? (ru ? "✋ Камера" : "✋ Pan") : (ru ? "✏ Рисовать" : "✏ Draw"));
        btnPanToggle.setTextColor(isPan ? theme.accentTextColor : theme.hudTextColor);
        btnPanToggle.setBackground(UiHelper.ripplePill(
                isPan ? theme.accentColor : theme.bgColor,
                isPan ? theme.accentColor : theme.hudBorderColor,
                UiHelper.dp(this, 1), UiHelper.dp(this, 12), 0x33FFFFFF));

        btnToolPicker.setText(getToolDisplayName(isPan ? lastDrawTool : m, ru));
        btnToolPicker.setTextColor(!isPan ? theme.accentColor : theme.hudTextColor);
        btnToolPicker.setBackground(UiHelper.ripplePill(
                theme.bgColor, !isPan ? theme.accentColor : theme.hudBorderColor,
                UiHelper.dp(this, 1), UiHelper.dp(this, 12), 0x33FFFFFF));

        Symmetry sym = canvasView.getSymmetry();
        btnSymmetry.setText(sym == Symmetry.NONE ? (ru ? "Симм" : "Sym") : sym.getTitle(ru));
        btnSymmetry.setTextColor(sym != Symmetry.NONE ? theme.accentColor : theme.hudTextColor);
        btnSymmetry.setBackground(UiHelper.ripplePill(
                theme.bgColor, sym != Symmetry.NONE ? theme.accentColor : theme.hudBorderColor,
                UiHelper.dp(this, 1), UiHelper.dp(this, 12), 0x33FFFFFF));
    }

    private void updateTransportButtons() {
        btnPlayPause.setText(isPlaying ? "⏸" : "▶");
        btnPlayPause.setTextColor(theme.accentTextColor);
        btnPlayPause.setBackground(UiHelper.ripplePill(theme.accentColor, theme.accentColor, 0, UiHelper.dp(this, 14), 0x44FFFFFF));

        for (TextView b : new TextView[]{btnUndo, btnRedo, btnResetGen0, btnStep, btnSoup, btnFit}) {
            b.setTextColor(theme.hudTextColor);
            b.setBackground(UiHelper.ripplePill(theme.bgColor, theme.hudBorderColor, UiHelper.dp(this, 1), UiHelper.dp(this, 12), 0x33FFFFFF));
        }

        btnUndo.setAlpha(engine.canUndo() ? 1f : 0.35f);
        btnRedo.setAlpha(engine.canRedo() ? 1f : 0.35f);
        btnResetGen0.setAlpha(engine.hasGen0Snapshot() ? 1f : 0.35f);
    }

    private void updateSpeedLabel() {
        float sp = settings.simSpeed;
        String s = sp < 10f ? String.format(Locale.US, "%.1f", sp) : String.valueOf(Math.round(sp));
        tvSpeedVal.setText(s + (settings.languageRu ? " г/с" : " /s"));
    }

    private static int speedToSlider(float speed) {
        // Maps 0.5 .. 120 exponentially to 0 .. 100
        double norm = Math.log(Math.max(0.5, Math.min(120.0, speed)) / 0.5) / Math.log(120.0 / 0.5);
        return Math.max(0, Math.min(100, (int) Math.round(norm * 100.0)));
    }

    private static float sliderToSpeed(int progress) {
        double val = 0.5 * Math.pow(120.0 / 0.5, progress / 100.0);
        if (val < 3.0) return Math.round(val * 2f) / 2f;
        return Math.round(val);
    }

    private String getToolDisplayName(LifeCanvasView.ToolMode m, boolean ru) {
        switch (m) {
            case DRAW: return ru ? "Кисть" : "Draw";
            case ERASE: return ru ? "Ластик" : "Erase";
            case TOGGLE: return ru ? "Инверт" : "Toggle";
            case LINE: return ru ? "Линия" : "Line";
            case RECT_OUTLINE: return ru ? "Рамка" : "Box";
            case RECT_FILL: return ru ? "Прямоуг" : "FillBox";
            case CIRCLE_OUTLINE: return ru ? "Круг" : "Circle";
            case CIRCLE_FILL: return ru ? "Шар" : "Disc";
            case SELECT: return ru ? "Выделить" : "Select";
            case STAMP: return ru ? "Штамп" : "Stamp";
            default: return ru ? "Камера" : "Pan";
        }
    }

    private void setPlaying(boolean play) {
        if (play && isJumping) return;
        isPlaying = play;
        handler.removeCallbacks(simLoopRunnable);
        if (play) {
            lastTickNs = SystemClock.elapsedRealtimeNanos();
            stepAccumulator = 0.0;
            handler.post(simLoopRunnable);
        }
        updateTransportButtons();
    }

    private void showToolPickerDialog() {
        final boolean ru = settings.languageRu;
        final LifeCanvasView.ToolMode[] modes = {
                LifeCanvasView.ToolMode.DRAW,
                LifeCanvasView.ToolMode.ERASE,
                LifeCanvasView.ToolMode.TOGGLE,
                LifeCanvasView.ToolMode.LINE,
                LifeCanvasView.ToolMode.RECT_OUTLINE,
                LifeCanvasView.ToolMode.RECT_FILL,
                LifeCanvasView.ToolMode.CIRCLE_OUTLINE,
                LifeCanvasView.ToolMode.CIRCLE_FILL,
                LifeCanvasView.ToolMode.SELECT
        };
        String[] labels = ru
                ? new String[]{
                "✏ Кисть (рисовать клетки)",
                "🧹 Ластик (стирать клетки)",
                "⇄ Переключатель (инверсия кликом)",
                "╱ Линия (отрезок)",
                "▢ Прямоугольник (контур)",
                "■ Прямоугольник (заливка)",
                "○ Окружность (контур)",
                "● Круг (заливка)",
                "⬚ Выделение области (копир/поворот/RLE)"
        }
                : new String[]{
                "✏ Brush (draw live cells)",
                "🧹 Eraser (clear cells)",
                "⇄ Toggle (invert cell state)",
                "╱ Line",
                "▢ Rectangle (outline)",
                "■ Rectangle (filled)",
                "○ Circle (outline)",
                "● Circle (filled)",
                "⬚ Select region (copy/rotate/RLE)"
        };
        new AlertDialog.Builder(this)
                .setTitle(ru ? "Выберите инструмент" : "Select Tool")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        lastDrawTool = modes[which];
                        canvasView.setToolMode(lastDrawTool);
                        if (lastDrawTool != LifeCanvasView.ToolMode.SELECT) {
                            canvasView.clearSelection();
                        }
                        updateToolButtons();
                        updateContextualBars();
                    }
                }).show();
    }

    private void showSpeedPresetsDialog() {
        final float[] speeds = {0.5f, 1f, 2f, 5f, 12f, 30f, 60f, 120f};
        String[] labels = {"0.5 gen/s", "1 gen/s", "2 gen/s", "5 gen/s", "12 gen/s", "30 gen/s", "60 gen/s", "120 gen/s (MAX)"};
        new AlertDialog.Builder(this)
                .setTitle(settings.languageRu ? "Скорость симуляции" : "Simulation Speed")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        settings.simSpeed = speeds[which];
                        sbSpeed.setProgress(speedToSlider(settings.simSpeed));
                        updateSpeedLabel();
                        storage.saveSettings(settings);
                    }
                }).show();
    }

    private void showJumpDialog() {
        final boolean ru = settings.languageRu;
        final int[] steps = {10, 50, 100, 500, 1000, 5000};
        String[] labels = {
                "+10 " + (ru ? "поколений" : "generations"),
                "+50 " + (ru ? "поколений" : "generations"),
                "+100 " + (ru ? "поколений" : "generations"),
                "+500 " + (ru ? "поколений" : "generations"),
                "+1 000 " + (ru ? "поколений" : "generations"),
                "+5 000 " + (ru ? "поколений" : "generations")
        };
        new AlertDialog.Builder(this)
                .setTitle(ru ? "Быстрая перемотка вперёд" : "Fast-Forward Generations")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        runAsyncStepJump(steps[which]);
                    }
                }).show();
    }

    private void runAsyncStepJump(final int count) {
        if (isJumping) return;
        setPlaying(false);
        isJumping = true;
        toast((settings.languageRu ? "Расчёт +" : "Simulating +") + count + "...");
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                engine.stepN(count);
                handler.post(new Runnable() {
                    @Override public void run() {
                        isJumping = false;
                        canvasView.invalidate();
                        updateTelemetry();
                        updateTransportButtons();
                        soundEngine.playMilestone();
                    }
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void openPatternBrowser() {
        setPlaying(false);
        PatternBrowserDialog.show(this, theme, storage, settings.languageRu, new PatternBrowserDialog.Callback() {
            @Override
            public void onPlaceAsStamp(CellRegion region, String name, RuleSet recommendedRule) {
                applyRecommendedRuleIfNeeded(recommendedRule);
                canvasView.enterStampMode(region, name);
                updateToolButtons();
                updateContextualBars();
            }

            @Override
            public void onLoadCentered(CellRegion region, String name, RuleSet recommendedRule) {
                applyRecommendedRuleIfNeeded(recommendedRule);
                engine.clear(true);
                int ox = engine.getTopology() == Topology.INFINITE
                        ? -region.width / 2
                        : (engine.getGridWidth() - region.width) / 2;
                int oy = engine.getTopology() == Topology.INFINITE
                        ? -region.height / 2
                        : (engine.getGridHeight() - region.height) / 2;
                engine.pasteRegion(region, ox, oy, true, false);
                engine.markCurrentAsGen0();
                canvasView.fitToPattern();
                updateTelemetry();
                updateTransportButtons();
            }
        });
    }

    private void applyRecommendedRuleIfNeeded(RuleSet rec) {
        if (rec != null && !rec.equals(engine.getRule())) {
            engine.setRule(rec);
            settings.ruleCode = engine.getRule().getRuleCode();
            settings.topology = engine.getTopology();
            storage.saveSettings(settings);
            toast((settings.languageRu ? "Правило: " : "Rule: ") + rec.getRuleCode());
            updateTelemetry();
        }
    }

    private void openSettings() {
        setPlaying(false);
        SettingsDialog.show(this, engine, settings, new SettingsDialog.Callback() {
            @Override
            public void onSettingsApplied(PatternStorage.AppSettings ns, boolean topChanged, boolean preserveCells) {
                settings = ns;
                theme = ThemeManager.byId(ns.themeId);
                soundEngine.setEnabled(ns.soundEnabled);

                if (topChanged) {
                    engine.configureTopology(ns.topology, ns.gridWidth, ns.gridHeight, preserveCells);
                }
                RuleSet parsedRule = RuleSet.parse(ns.ruleCode);
                if (parsedRule != null) engine.setRule(parsedRule);
                engine.setDecaySteps(ns.decaySteps);
                ns.topology = engine.getTopology();
                ns.ruleCode = engine.getRule().getRuleCode();

                canvasView.setVisualOptions(ns.cellShape, ns.ageColoring, ns.showGrid, ns.showAxes, ns.showMinimap);
                storage.saveSettings(ns);

                applyThemeToUi();
                updateTelemetry();
                updateSpeedLabel();
                if (topChanged) canvasView.fitToPattern();
            }
        });
    }

    private void openMainMenu() {
        setPlaying(false);
        final boolean ru = settings.languageRu;
        String[] items = ru
                ? new String[]{
                "🧩 Энциклопедия фигур (50+)",
                "💾 Слоты сохранения (1–6)",
                "⏩ Перемотка (+10 / +100 / +5000)",
                "📋 Импорт RLE / Plaintext из текста",
                "📂 Открыть файл (.rle / .cells)",
                "💿 Сохранить в файл (.rle)",
                "📤 Поделиться RLE текстом / файлом",
                "🖼 Экспорт PNG (Поделиться / Сохранить)",
                "📍 Запомнить текущее как Пок. 0",
                "🗑 Очистить поле",
                "⚙ Настройки симуляции и тем",
                "ℹ О сути и правилах игры «Жизнь»"
        }
                : new String[]{
                "🧩 Pattern Encyclopedia (50+)",
                "💾 Quick Save Slots (1–6)",
                "⏩ Fast-Forward (+10 / +100 / +5000)",
                "📋 Import RLE / Plaintext from text",
                "📂 Open file (.rle / .cells)",
                "💿 Save as .rle file",
                "📤 Share RLE text / file",
                "🖼 Export PNG Snapshot (Share / Save)",
                "📍 Set current state as Gen 0",
                "🗑 Clear Grid",
                "⚙ Simulation & Theme Settings",
                "ℹ About Conway's Game of Life"
        };

        new AlertDialog.Builder(this)
                .setTitle(ru ? "Меню игры «Жизнь»" : "Game of Life Menu")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: openPatternBrowser(); break;
                            case 1: openSaveSlotsDialog(); break;
                            case 2: showJumpDialog(); break;
                            case 3: openTextImportDialog(); break;
                            case 4: launchOpenFilePicker(); break;
                            case 5: launchSaveRlePicker(); break;
                            case 6: shareCurrentRle(); break;
                            case 7: openPngExportDialog(); break;
                            case 8:
                                engine.markCurrentAsGen0();
                                updateTelemetry();
                                updateTransportButtons();
                                toast(ru ? "Поколение 0 обновлено" : "Gen 0 updated");
                                break;
                            case 9:
                                engine.clear(true);
                                canvasView.clearSelection();
                                canvasView.invalidate();
                                updateTelemetry();
                                updateTransportButtons();
                                break;
                            case 10: openSettings(); break;
                            case 11: showHelpDialog(); break;
                        }
                    }
                }).show();
    }

    private void openSaveSlotsDialog() {
        final boolean ru = settings.languageRu;
        final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
        String[] labels = new String[PatternStorage.SLOT_COUNT];
        for (int i = 0; i < PatternStorage.SLOT_COUNT; i++) {
            PatternStorage.SavedSlot s = storage.loadSlot(i);
            if (s == null) {
                labels[i] = (i + 1) + ". " + (ru ? "[Пустой слот]" : "[Empty Slot]");
            } else {
                String dt = s.savedAtMs > 0 ? sdf.format(new Date(s.savedAtMs)) : "";
                labels[i] = (i + 1) + ". " + s.name + " · " + s.liveCount + (ru ? " кл." : " cells")
                        + " · " + s.ruleCode + (dt.isEmpty() ? "" : " (" + dt + ")");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(ru ? "Слоты сохранения" : "Quick Save Slots")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, final int slotIdx) {
                        final PatternStorage.SavedSlot existing = storage.loadSlot(slotIdx);
                        String[] actions = existing == null
                                ? new String[]{ru ? "Сохранить текущее поле сюда" : "Save current state here"}
                                : new String[]{
                                ru ? "Загрузить слот" : "Load slot",
                                ru ? "Перезаписать текущим полем" : "Overwrite with current state",
                                ru ? "Удалить слот" : "Delete slot"
                        };
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle((ru ? "Слот #" : "Slot #") + (slotIdx + 1))
                                .setItems(actions, new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int act) {
                                        if (existing == null || (existing != null && act == 1)) {
                                            promptSaveToSlot(slotIdx);
                                        } else if (act == 0) {
                                            loadFromSlot(existing);
                                        } else if (act == 2) {
                                            storage.deleteSlot(slotIdx);
                                            toast(ru ? "Слот очищен" : "Slot deleted");
                                        }
                                    }
                                }).show();
                    }
                }).show();
    }

    private void promptSaveToSlot(final int slotIdx) {
        final boolean ru = settings.languageRu;
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText((ru ? "Мир " : "World ") + (slotIdx + 1));
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(ru ? "Название сохранения" : "Save Name")
                .setView(et)
                .setNegativeButton(ru ? "Отмена" : "Cancel", null)
                .setPositiveButton(ru ? "Сохранить" : "Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String name = et.getText().toString().trim();
                        if (name.isEmpty()) name = "Slot " + (slotIdx + 1);
                        storage.saveSlot(slotIdx, name, engine);
                        toast(ru ? "Сохранено в слот #" + (slotIdx + 1) : "Saved to slot #" + (slotIdx + 1));
                    }
                }).show();
    }

    private void loadFromSlot(PatternStorage.SavedSlot slot) {
        Topology t = Topology.fromId(slot.topologyId);
        RuleSet r = RuleSet.parse(slot.ruleCode);
        engine.pushUndo();
        engine.configureTopology(t, slot.gridWidth, slot.gridHeight, false);
        engine.setRule(r != null ? r : RuleSet.CONWAY);
        RleCodec.DecodedPattern dp = RleCodec.decodeAuto(slot.rle);
        if (!dp.region.isEmpty()) {
            engine.pasteRegion(dp.region, slot.originX, slot.originY, true, false);
        }
        engine.getStats().generation = slot.generation;
        settings.topology = engine.getTopology();
        settings.gridWidth = engine.getGridWidth();
        settings.gridHeight = engine.getGridHeight();
        settings.ruleCode = engine.getRule().getRuleCode();
        storage.saveSettings(settings);
        canvasView.fitToPattern();
        updateTelemetry();
        updateTransportButtons();
    }

    private void promptSaveCustomPattern(final CellRegion reg) {
        final boolean ru = settings.languageRu;
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint(ru ? "Название шаблона" : "Pattern name");
        new AlertDialog.Builder(this)
                .setTitle(ru ? "Сохранить в «Мои шаблоны»" : "Save to Custom Patterns")
                .setView(et)
                .setNegativeButton(ru ? "Отмена" : "Cancel", null)
                .setPositiveButton(ru ? "Сохранить" : "Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String name = et.getText().toString().trim();
                        if (name.isEmpty()) name = ru ? "Мой узор" : "Custom Pattern";
                        storage.addCustomPattern(name, reg, engine.getRule());
                        toast(ru ? "Шаблон сохранён!" : "Pattern saved!");
                    }
                }).show();
    }

    private void openTextImportDialog() {
        final boolean ru = settings.languageRu;
        final EditText et = new EditText(this);
        et.setHint("x = 3, y = 3, rule = B3/S23\nbo$2bo$3o!");
        et.setMinLines(6);
        et.setMaxLines(12);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (clip != null) {
                String s = clip.toString();
                if (s.contains("!") || s.contains("$") || s.contains("O") || s.contains("x =")) {
                    et.setText(s);
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(ru ? "Импорт RLE / Plaintext (.cells)" : "Import RLE / Plaintext (.cells)")
                .setView(et)
                .setNegativeButton(ru ? "Отмена" : "Cancel", null)
                .setNeutralButton(ru ? "Штампом" : "As Stamp", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        importPatternText(et.getText().toString(), true);
                    }
                })
                .setPositiveButton(ru ? "Загрузить по центру" : "Load Centered", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        importPatternText(et.getText().toString(), false);
                    }
                }).show();
    }

    private void importPatternText(String text, boolean asStamp) {
        boolean ru = settings.languageRu;
        RleCodec.DecodedPattern dp = RleCodec.decodeAuto(text);
        if (dp.region.isEmpty()) {
            toast(ru ? "Не удалось распознать клетки в тексте" : "No valid cells found in text");
            return;
        }
        if (dp.rule != null) {
            applyRecommendedRuleIfNeeded(dp.rule);
        }
        String name = !dp.name.isEmpty() ? dp.name : (ru ? "Импорт" : "Imported");
        if (asStamp) {
            canvasView.enterStampMode(dp.region, name);
            updateToolButtons();
            updateContextualBars();
        } else {
            engine.clear(true);
            int ox = engine.getTopology() == Topology.INFINITE
                    ? -dp.region.width / 2
                    : (engine.getGridWidth() - dp.region.width) / 2;
            int oy = engine.getTopology() == Topology.INFINITE
                    ? -dp.region.height / 2
                    : (engine.getGridHeight() - dp.region.height) / 2;
            engine.pasteRegion(dp.region, ox, oy, true, false);
            engine.markCurrentAsGen0();
            canvasView.fitToPattern();
            updateTelemetry();
            updateTransportButtons();
        }
    }

    private void launchOpenFilePicker() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_OPEN_PATTERN);
        } catch (Exception e) {
            toast(settings.languageRu ? "Не удалось открыть проводник" : "Unable to open file picker");
        }
    }

    private void launchSaveRlePicker() {
        CellRegion reg = engine.extractAllLiveBounds();
        if (reg.isEmpty()) {
            toast(settings.languageRu ? "Поле пусто — нечего сохранять" : "Grid is empty");
            return;
        }
        pendingSaveRle = RleCodec.encodeRle(reg, engine.getRule(), "LifePattern", "Exported from Game of Life");
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE, "life_" + System.currentTimeMillis() + ".rle");
            startActivityForResult(i, REQ_SAVE_RLE);
        } catch (Exception e) {
            toast(settings.languageRu ? "Ошибка сохранения файла" : "Unable to save file");
        }
    }

    private void shareCurrentRle() {
        boolean ru = settings.languageRu;
        CellRegion reg = engine.extractAllLiveBounds();
        if (reg.isEmpty()) {
            toast(ru ? "Поле пусто" : "Grid is empty");
            return;
        }
        final String rle = RleCodec.encodeRle(reg, engine.getRule(), "Game of Life Pattern", "");
        String[] opts = ru
                ? new String[]{"Копировать RLE в буфер", "Отправить как текст / файл .rle"}
                : new String[]{"Copy RLE to clipboard", "Share as text / .rle file"};
        new AlertDialog.Builder(this)
                .setTitle(ru ? "Экспорт RLE" : "Export RLE")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            copyText(rle, settings.languageRu ? "RLE скопирован в буфер" : "RLE copied");
                        } else {
                            try {
                                File f = new File(ExportProvider.getExportsDir(MainActivity.this), "pattern.rle");
                                try (FileOutputStream fos = new FileOutputStream(f)) {
                                    fos.write(rle.getBytes(StandardCharsets.UTF_8));
                                }
                                Uri uri = ExportProvider.uriForFile("pattern.rle");
                                Intent send = new Intent(Intent.ACTION_SEND);
                                send.setType("text/plain");
                                send.putExtra(Intent.EXTRA_TEXT, rle);
                                send.putExtra(Intent.EXTRA_STREAM, uri);
                                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(Intent.createChooser(send, settings.languageRu ? "Поделиться RLE" : "Share RLE"));
                            } catch (Exception e) {
                                toast(settings.languageRu ? "Ошибка экспорта" : "Export failed");
                            }
                        }
                    }
                }).show();
    }

    private void openPngExportDialog() {
        final boolean ru = settings.languageRu;
        String[] opts = ru
                ? new String[]{"Поделиться снимком PNG", "Сохранить PNG в файл"}
                : new String[]{"Share PNG Snapshot", "Save PNG to file"};
        new AlertDialog.Builder(this)
                .setTitle(ru ? "Снимок поля (PNG)" : "PNG Snapshot")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            sharePngSnapshot();
                        } else {
                            try {
                                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                i.addCategory(Intent.CATEGORY_OPENABLE);
                                i.setType("image/png");
                                i.putExtra(Intent.EXTRA_TITLE, "life_snapshot_" + System.currentTimeMillis() + ".png");
                                startActivityForResult(i, REQ_SAVE_PNG);
                            } catch (Exception e) {
                                toast(ru ? "Ошибка сохранения PNG" : "Unable to save PNG");
                            }
                        }
                    }
                }).show();
    }

    private void sharePngSnapshot() {
        try {
            Bitmap bmp = canvasView.renderSnapshotBitmap(1440);
            File f = new File(ExportProvider.getExportsDir(this), "snapshot.png");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            bmp.recycle();
            Uri uri = ExportProvider.uriForFile("snapshot.png");
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, settings.languageRu ? "Поделиться снимком" : "Share Snapshot"));
        } catch (Exception e) {
            toast(settings.languageRu ? "Ошибка экспорта PNG" : "PNG export failed");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_OPEN_PATTERN) {
            importPatternFromUri(uri);
        } else if (requestCode == REQ_SAVE_RLE && pendingSaveRle != null) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(pendingSaveRle.getBytes(StandardCharsets.UTF_8));
                    toast(settings.languageRu ? "Файл RLE сохранён!" : "RLE file saved!");
                }
            } catch (Exception e) {
                toast(settings.languageRu ? "Ошибка записи файла" : "Failed to write file");
            }
            pendingSaveRle = null;
        } else if (requestCode == REQ_SAVE_PNG) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    Bitmap bmp = canvasView.renderSnapshotBitmap(1440);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                    bmp.recycle();
                    toast(settings.languageRu ? "Изображение PNG сохранено!" : "PNG image saved!");
                }
            } catch (Exception e) {
                toast(settings.languageRu ? "Ошибка записи PNG" : "Failed to write PNG");
            }
        }
    }

    private void importPatternFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            int total = 0;
            while ((line = br.readLine()) != null && total < 1_000_000) {
                sb.append(line).append('\n');
                total += line.length();
            }
            importPatternText(sb.toString(), false);
        } catch (Exception e) {
            toast(settings.languageRu ? "Не удалось прочитать файл" : "Unable to read file");
        }
    }

    private void showHelpDialog() {
        boolean ru = settings.languageRu;
        String body = ru
                ? "Суть игры «Жизнь» (Джон Конвей, 1970):\n\n"
                + "• Вселенная — клеточная решётка, где каждая клетка жива или мертва, а её судьба зависит только от 8 соседей:\n"
                + "  1. Мёртвая клетка с ровно 3 живыми соседями оживает (рождение).\n"
                + "  2. Живая клетка с 2 или 3 соседями продолжает жить (выживание).\n"
                + "  3. При < 2 или > 3 соседей клетка умирает (одиночество или перенаселение).\n\n"
                + "Жесты и управление:\n"
                + "• 2 пальца — масштаб (щипок) и панорамирование в любом режиме.\n"
                + "• 1 палец — рисование/фигура/выделение (или камера при включённом режиме ✋).\n"
                + "• Удерживайте ⏭ для быстрой перемотки (+10..+5000 поколений).\n"
                + "• Удерживайте 🎯 для возврата камеры к центру (0, 0).\n"
                + "• Нажмите на скорость («12 г/с») для точных пресетов скорости.\n"
                + "• Нажмите на плашку правила («B3/S23») для открытия настроек топологии, правил, тем и графики."
                : "Essence of Conway's Game of Life (1970):\n\n"
                + "• Each cell on the grid is either alive or dead, evolving solely from its 8 Moore neighbors:\n"
                + "  1. A dead cell with exactly 3 live neighbors becomes alive (Birth).\n"
                + "  2. A live cell with 2 or 3 live neighbors survives.\n"
                + "  3. Any other live cell dies from under- or over-population.\n\n"
                + "Gestures & Shortcuts:\n"
                + "• 2 fingers — pinch to zoom and pan in any tool mode.\n"
                + "• 1 finger — draw/shape/select (or pan when ✋ Pan is active).\n"
                + "• Long-press ⏭ to fast-forward (+10..+5000 generations).\n"
                + "• Long-press 🎯 to reset camera to origin (0, 0).\n"
                + "• Tap the speed readout for quick presets, or tap the top rule badge for Settings.";

        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(body);
        tv.setTextColor(theme.hudTextColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int pad = UiHelper.dp(this, 18);
        tv.setPadding(pad, pad, pad, pad);
        sv.setBackgroundColor(theme.hudBgColor);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle(ru ? "О игре «Жизнь»" : "About Game of Life")
                .setView(sv)
                .setPositiveButton("OK", null)
                .show();
    }

    private void copyText(String text, String toastMsg) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Game of Life", text));
            toast(toastMsg);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

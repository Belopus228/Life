package com.belopus.life.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.belopus.life.engine.CellRegion;
import com.belopus.life.engine.LifeEngine;
import com.belopus.life.engine.SimStats;
import com.belopus.life.engine.Symmetry;
import com.belopus.life.engine.Topology;

import java.util.ArrayList;

public class LifeCanvasView extends View {

    public enum ToolMode {
        PAN,
        DRAW,
        ERASE,
        TOGGLE,
        LINE,
        RECT_OUTLINE,
        RECT_FILL,
        CIRCLE_OUTLINE,
        CIRCLE_FILL,
        SELECT,
        STAMP
    }

    public interface Listener {
        void onCanvasEdited();
        void onSelectionChanged(boolean hasSelection, int width, int height);
        void onStampMoved(int x, int y);
        void onToolFeedback(String msg);
    }

    private LifeEngine engine;
    private ThemeManager.Theme theme = ThemeManager.OBSIDIAN;
    private ToolMode toolMode = ToolMode.DRAW;
    private Symmetry symmetry = Symmetry.NONE;
    private int cellShape = 1; // 0=square, 1=rounded, 2=circle
    private boolean ageColoring = true;
    private boolean showGrid = true;
    private boolean showAxes = true;
    private boolean showMinimap = true;
    private SoundEngine soundEngine;
    private Listener listener;

    // Camera state: world coords at screen center
    private double camX = 0.0;
    private double camY = 0.0;
    private float cellPx = 22f;

    // Touch & gesture state
    private final ScaleGestureDetector scaleDetector;
    private boolean multiTouchActive = false;
    private float lastPanX, lastPanY;
    private float lastFocusX, lastFocusY;

    // Drawing state
    private boolean singleStrokeActive = false;
    private boolean strokeConfirmed = false;
    private boolean undoRecordedForStroke = false;
    private int lastDrawCellX, lastDrawCellY;
    private boolean toggleTargetAlive = true;
    private long strokeDownTime = 0L;
    private float strokeDownScreenX, strokeDownScreenY;
    private final ArrayList<int[]> pendingStrokeCells = new ArrayList<>();
    private final Runnable confirmStrokeRunnable = new Runnable() {
        @Override
        public void run() {
            if (singleStrokeActive && !multiTouchActive && !strokeConfirmed) {
                confirmAndFlushPendingStroke();
            }
        }
    };

    // Shape drag preview state
    private boolean shapeDragging = false;
    private int shapeStartX, shapeStartY;
    private int shapeEndX, shapeEndY;

    // Selection state
    private boolean hasSelection = false;
    private boolean selectingDrag = false;
    private int selX0, selY0, selX1, selY1;
    private boolean prevHasSelection = false;
    private int prevSelX0, prevSelY0, prevSelX1, prevSelY1;

    // Stamp state
    private CellRegion stampRegion = null;
    private String stampName = "";
    private boolean stampMerge = true;
    private int stampX = 0, stampY = 0;
    private boolean stampDragging = false;
    private float stampDragStartScreenX, stampDragStartScreenY;
    private int stampDragStartCellX, stampDragStartCellY;

    // Reusable Paints
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint majorGridPaint = new Paint();
    private final Paint axisPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectFillPaint = new Paint();
    private final Paint selectStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symLinePaint = new Paint();
    private final Paint miniBgPaint = new Paint();
    private final Paint miniCellPaint = new Paint();
    private final Paint miniViewPaint = new Paint();
    private final Paint bmpPaint = new Paint();

    // Reusable Bitmap buffer for zoomed-out rendering
    private Bitmap fastBitmap;
    private int[] fastPixels;
    private int fastW, fastH;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    public LifeCanvasView(Context context) {
        super(context);
        gridPaint.setStrokeWidth(1f);
        majorGridPaint.setStrokeWidth(1.5f);
        axisPaint.setStrokeWidth(2f);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);

        selectFillPaint.setStyle(Paint.Style.FILL);
        selectStrokePaint.setStyle(Paint.Style.STROKE);
        selectStrokePaint.setStrokeWidth(3f);
        selectStrokePaint.setPathEffect(new DashPathEffect(new float[]{14f, 10f}, 0f));

        symLinePaint.setStyle(Paint.Style.STROKE);
        symLinePaint.setStrokeWidth(2f);
        symLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0f));

        miniBgPaint.setStyle(Paint.Style.FILL);
        miniViewPaint.setStyle(Paint.Style.STROKE);
        miniViewPaint.setStrokeWidth(2f);

        bmpPaint.setFilterBitmap(false);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                lastFocusX = detector.getFocusX();
                lastFocusY = detector.getFocusY();
                lastPanX = lastFocusX;
                lastPanY = lastFocusY;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                int vw = getWidth();
                int vh = getHeight();
                if (vw <= 0 || vh <= 0) return false;

                float fx = detector.getFocusX();
                float fy = detector.getFocusY();
                double worldFx = camX + (fx - vw * 0.5f) / cellPx;
                double worldFy = camY + (fy - vh * 0.5f) / cellPx;

                float newCellPx = Math.max(0.35f, Math.min(96f, cellPx * detector.getScaleFactor()));
                cellPx = newCellPx;

                // Keep focal world coordinate pinned under fingers + apply 2-finger pan delta
                float panDx = fx - lastFocusX;
                float panDy = fy - lastFocusY;
                camX = worldFx - (fx - vw * 0.5f) / cellPx - panDx / cellPx;
                camY = worldFy - (fy - vh * 0.5f) / cellPx - panDy / cellPx;

                lastFocusX = fx;
                lastFocusY = fy;
                lastPanX = fx;
                lastPanY = fy;
                invalidate();
                return true;
            }
        });
        scaleDetector.setQuickScaleEnabled(false);
    }

    public void attachEngine(LifeEngine engine) {
        this.engine = engine;
        centerCameraDefault();
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSoundEngine(SoundEngine soundEngine) {
        this.soundEngine = soundEngine;
    }

    public void setTheme(ThemeManager.Theme theme) {
        if (theme != null) {
            this.theme = theme;
            invalidate();
        }
    }

    public ThemeManager.Theme getTheme() {
        return theme;
    }

    public void setVisualOptions(int cellShape, boolean ageColoring, boolean showGrid,
                                 boolean showAxes, boolean showMinimap) {
        this.cellShape = cellShape;
        this.ageColoring = ageColoring;
        this.showGrid = showGrid;
        this.showAxes = showAxes;
        this.showMinimap = showMinimap;
        invalidate();
    }

    public void setToolMode(ToolMode mode) {
        this.toolMode = mode;
        if (mode != ToolMode.STAMP) {
            stampDragging = false;
        }
        invalidate();
    }

    public ToolMode getToolMode() {
        return toolMode;
    }

    public void setSymmetry(Symmetry sym) {
        this.symmetry = sym != null ? sym : Symmetry.NONE;
        invalidate();
    }

    public Symmetry getSymmetry() {
        return symmetry;
    }

    public float getCellPx() {
        return cellPx;
    }

    public double getCamX() {
        return camX;
    }

    public double getCamY() {
        return camY;
    }

    public void setCamera(double x, double y, float px) {
        this.camX = x;
        this.camY = y;
        this.cellPx = Math.max(0.35f, Math.min(96f, px));
        invalidate();
    }

    public void centerCameraDefault() {
        if (engine == null) return;
        if (engine.getTopology() == Topology.INFINITE) {
            camX = 0.0;
            camY = 0.0;
        } else {
            camX = engine.getGridWidth() * 0.5;
            camY = engine.getGridHeight() * 0.5;
        }
        cellPx = 22f;
        invalidate();
    }

    public void fitToPattern() {
        if (engine == null) return;
        SimStats st = engine.getStats();
        int vw = Math.max(1, getWidth());
        int vh = Math.max(1, getHeight());
        if (!st.hasBounds) {
            if (engine.getTopology() == Topology.INFINITE) {
                camX = 0.0;
                camY = 0.0;
                cellPx = 22f;
            } else {
                int gw = engine.getGridWidth();
                int gh = engine.getGridHeight();
                camX = gw * 0.5;
                camY = gh * 0.5;
                float fit = Math.min((vw * 0.85f) / gw, (vh * 0.65f) / gh);
                cellPx = Math.max(0.5f, Math.min(48f, fit));
            }
            invalidate();
            return;
        }
        int bw = Math.max(1, st.maxX - st.minX + 1);
        int bh = Math.max(1, st.maxY - st.minY + 1);
        camX = (st.minX + st.maxX + 1) * 0.5;
        camY = (st.minY + st.maxY + 1) * 0.5;
        float padW = Math.max(4, bw + Math.max(8f, bw * 0.35f));
        float padH = Math.max(4, bh + Math.max(8f, bh * 0.35f));
        float fit = Math.min((vw * 0.88f) / padW, (vh * 0.65f) / padH);
        cellPx = Math.max(0.4f, Math.min(48f, fit));
        invalidate();
    }

    // === Selection API ===
    public boolean hasSelection() {
        return hasSelection;
    }

    public void clearSelection() {
        hasSelection = false;
        selectingDrag = false;
        if (listener != null) listener.onSelectionChanged(false, 0, 0);
        invalidate();
    }

    public int[] getSelectionBounds() {
        return new int[]{
                Math.min(selX0, selX1), Math.min(selY0, selY1),
                Math.max(selX0, selX1), Math.max(selY0, selY1)
        };
    }

    public void setSelectionBounds(int x0, int y0, int x1, int y1) {
        this.hasSelection = true;
        this.selX0 = x0;
        this.selY0 = y0;
        this.selX1 = x1;
        this.selY1 = y1;
        if (listener != null) {
            listener.onSelectionChanged(true, Math.abs(x1 - x0) + 1, Math.abs(y1 - y0) + 1);
        }
        invalidate();
    }

    // === Stamp API ===
    public void enterStampMode(CellRegion region, String name) {
        if (region == null || region.width <= 0 || region.height <= 0) return;
        this.stampRegion = region;
        this.stampName = name != null ? name : "";
        this.stampX = (int) Math.round(camX - region.width * 0.5);
        this.stampY = (int) Math.round(camY - region.height * 0.5);
        this.toolMode = ToolMode.STAMP;
        if (listener != null) listener.onStampMoved(stampX, stampY);
        invalidate();
    }

    public CellRegion getStampRegion() {
        return stampRegion;
    }

    public String getStampName() {
        return stampName;
    }

    public boolean isStampMerge() {
        return stampMerge;
    }

    public void setStampMerge(boolean merge) {
        this.stampMerge = merge;
        invalidate();
    }

    public void rotateStampCW() {
        if (stampRegion != null) {
            int cx = stampX + stampRegion.width / 2;
            int cy = stampY + stampRegion.height / 2;
            stampRegion = stampRegion.rotateCW();
            stampX = cx - stampRegion.width / 2;
            stampY = cy - stampRegion.height / 2;
            invalidate();
        }
    }

    public void flipStampH() {
        if (stampRegion != null) {
            stampRegion = stampRegion.flipH();
            invalidate();
        }
    }

    public void commitStamp() {
        if (engine == null || stampRegion == null) return;
        engine.pasteRegion(stampRegion, stampX, stampY, stampMerge, true);
        if (soundEngine != null) soundEngine.playMilestone();
        if (listener != null) listener.onCanvasEdited();
        invalidate();
    }

    public void exitStampMode() {
        stampRegion = null;
        stampDragging = false;
        if (toolMode == ToolMode.STAMP) {
            toolMode = ToolMode.DRAW;
        }
        invalidate();
    }

    private int screenToCellX(float sx) {
        return (int) Math.floor(camX + (sx - getWidth() * 0.5f) / cellPx);
    }

    private int screenToCellY(float sy) {
        return (int) Math.floor(camY + (sy - getHeight() * 0.5f) / cellPx);
    }

    private void confirmAndFlushPendingStroke() {
        removeCallbacks(confirmStrokeRunnable);
        if (engine == null || pendingStrokeCells.isEmpty()) {
            strokeConfirmed = true;
            return;
        }
        strokeConfirmed = true;
        int[] first = pendingStrokeCells.get(0);
        if (toolMode == ToolMode.TOGGLE) {
            toggleTargetAlive = !engine.isAlive(first[0], first[1]);
        }
        boolean alive = (toolMode == ToolMode.DRAW) || (toolMode == ToolMode.TOGGLE && toggleTargetAlive);
        if (!undoRecordedForStroke) {
            engine.pushUndo();
            undoRecordedForStroke = true;
        }
        engine.drawCellWithSymmetry(first[0], first[1], alive, symmetry);
        int prevX = first[0];
        int prevY = first[1];
        for (int i = 1; i < pendingStrokeCells.size(); i++) {
            int[] pt = pendingStrokeCells.get(i);
            if (pt[0] != prevX || pt[1] != prevY) {
                engine.drawLineWithSymmetry(prevX, prevY, pt[0], pt[1], alive, symmetry);
                prevX = pt[0];
                prevY = pt[1];
            }
        }
        lastDrawCellX = prevX;
        lastDrawCellY = prevY;
        pendingStrokeCells.clear();
        engine.notifyManualEditComplete(true);
        if (soundEngine != null) soundEngine.playDrawPop(alive);
        if (listener != null) listener.onCanvasEdited();
        invalidate();
    }

    private void cancelPendingAndActiveEdits() {
        removeCallbacks(confirmStrokeRunnable);
        boolean shouldRevertDraw = undoRecordedForStroke && engine != null;
        pendingStrokeCells.clear();
        singleStrokeActive = false;
        strokeConfirmed = false;
        undoRecordedForStroke = false;
        shapeDragging = false;
        if (shouldRevertDraw) {
            if (engine.undo()) {
                engine.notifyManualEditComplete(true);
                if (listener != null) listener.onCanvasEdited();
            }
            invalidate();
        }
        if (selectingDrag) {
            selectingDrag = false;
            hasSelection = prevHasSelection;
            selX0 = prevSelX0;
            selY0 = prevSelY0;
            selX1 = prevSelX1;
            selY1 = prevSelY1;
            if (listener != null) {
                listener.onSelectionChanged(hasSelection,
                        hasSelection ? Math.abs(selX1 - selX0) + 1 : 0,
                        hasSelection ? Math.abs(selY1 - selY0) + 1 : 0);
            }
            invalidate();
        }
        if (stampDragging) {
            stampDragging = false;
            stampX = stampDragStartCellX;
            stampY = stampDragStartCellY;
            if (listener != null) listener.onStampMoved(stampX, stampY);
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (engine == null) return true;
        scaleDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        int ptrCount = event.getPointerCount();

        if (ptrCount >= 2) {
            if (!multiTouchActive) {
                multiTouchActive = true;
                cancelPendingAndActiveEdits();
                lastPanX = (event.getX(0) + event.getX(1)) * 0.5f;
                lastPanY = (event.getY(0) + event.getY(1)) * 0.5f;
            }
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                lastPanX = (event.getX(0) + event.getX(1)) * 0.5f;
                lastPanY = (event.getY(0) + event.getY(1)) * 0.5f;
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                // Keep remaining pointer(s) midpoint seamless for continued pan
                int upIdx = event.getActionIndex();
                float sx = 0f, sy = 0f;
                int rem = 0;
                for (int i = 0; i < ptrCount; i++) {
                    if (i != upIdx) {
                        sx += event.getX(i);
                        sy += event.getY(i);
                        rem++;
                    }
                }
                if (rem > 0) {
                    lastPanX = sx / rem;
                    lastPanY = sy / rem;
                }
            } else if (action == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress()) {
                float mx = (event.getX(0) + event.getX(1)) * 0.5f;
                float my = (event.getY(0) + event.getY(1)) * 0.5f;
                camX -= (mx - lastPanX) / cellPx;
                camY -= (my - lastPanY) / cellPx;
                lastPanX = mx;
                lastPanY = my;
                invalidate();
            }
            return true;
        }

        // Only 1 pointer below here
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                multiTouchActive = false;
                removeCallbacks(confirmStrokeRunnable);
                pendingStrokeCells.clear();
                float x = event.getX();
                float y = event.getY();
                int cx = screenToCellX(x);
                int cy = screenToCellY(y);

                if (toolMode == ToolMode.PAN) {
                    lastPanX = x;
                    lastPanY = y;
                    singleStrokeActive = true;
                    strokeConfirmed = true;
                } else if (toolMode == ToolMode.DRAW || toolMode == ToolMode.ERASE || toolMode == ToolMode.TOGGLE) {
                    // Defer committing stroke for 115ms so a 2-finger pinch/pan never leaves stray dots!
                    singleStrokeActive = true;
                    strokeConfirmed = false;
                    undoRecordedForStroke = false;
                    strokeDownTime = event.getEventTime();
                    strokeDownScreenX = x;
                    strokeDownScreenY = y;
                    lastDrawCellX = cx;
                    lastDrawCellY = cy;
                    pendingStrokeCells.add(new int[]{cx, cy});
                    postDelayed(confirmStrokeRunnable, 150L);
                } else if (toolMode == ToolMode.LINE || toolMode == ToolMode.RECT_OUTLINE
                        || toolMode == ToolMode.RECT_FILL || toolMode == ToolMode.CIRCLE_OUTLINE
                        || toolMode == ToolMode.CIRCLE_FILL) {
                    shapeDragging = true;
                    shapeStartX = shapeEndX = cx;
                    shapeStartY = shapeEndY = cy;
                    invalidate();
                } else if (toolMode == ToolMode.SELECT) {
                    prevHasSelection = hasSelection;
                    prevSelX0 = selX0;
                    prevSelY0 = selY0;
                    prevSelX1 = selX1;
                    prevSelY1 = selY1;
                    selectingDrag = true;
                    hasSelection = true;
                    selX0 = selX1 = cx;
                    selY0 = selY1 = cy;
                    if (listener != null) listener.onSelectionChanged(true, 1, 1);
                    invalidate();
                } else if (toolMode == ToolMode.STAMP && stampRegion != null) {
                    stampDragging = true;
                    stampDragStartScreenX = x;
                    stampDragStartScreenY = y;
                    stampDragStartCellX = stampX;
                    stampDragStartCellY = stampY;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                float y = event.getY();
                if (multiTouchActive) {
                    // Remaining finger after a 2-finger pinch/pan continues panning cleanly without drawing
                    camX -= (x - lastPanX) / cellPx;
                    camY -= (y - lastPanY) / cellPx;
                    lastPanX = x;
                    lastPanY = y;
                    invalidate();
                    break;
                }
                int cx = screenToCellX(x);
                int cy = screenToCellY(y);

                if (toolMode == ToolMode.PAN && singleStrokeActive) {
                    camX -= (x - lastPanX) / cellPx;
                    camY -= (y - lastPanY) / cellPx;
                    lastPanX = x;
                    lastPanY = y;
                    invalidate();
                } else if ((toolMode == ToolMode.DRAW || toolMode == ToolMode.ERASE || toolMode == ToolMode.TOGGLE)
                        && singleStrokeActive) {
                    if (!strokeConfirmed) {
                        if (cx != lastDrawCellX || cy != lastDrawCellY) {
                            pendingStrokeCells.add(new int[]{cx, cy});
                            lastDrawCellX = cx;
                            lastDrawCellY = cy;
                        }
                        long elapsed = event.getEventTime() - strokeDownTime;
                        float moveDist = (float) Math.hypot(x - strokeDownScreenX, y - strokeDownScreenY);
                        if (elapsed >= 150L || (elapsed >= 85L && moveDist >= UiHelper.dp(getContext(), 12))) {
                            confirmAndFlushPendingStroke();
                        }
                    } else if (cx != lastDrawCellX || cy != lastDrawCellY) {
                        if (!undoRecordedForStroke) {
                            engine.pushUndo();
                            undoRecordedForStroke = true;
                        }
                        boolean alive = (toolMode == ToolMode.DRAW) || (toolMode == ToolMode.TOGGLE && toggleTargetAlive);
                        engine.drawLineWithSymmetry(lastDrawCellX, lastDrawCellY, cx, cy, alive, symmetry);
                        engine.notifyManualEditComplete(true);
                        lastDrawCellX = cx;
                        lastDrawCellY = cy;
                        if (soundEngine != null) soundEngine.playDrawPop(alive);
                        if (listener != null) listener.onCanvasEdited();
                        invalidate();
                    }
                } else if (shapeDragging) {
                    if (cx != shapeEndX || cy != shapeEndY) {
                        shapeEndX = cx;
                        shapeEndY = cy;
                        invalidate();
                    }
                } else if (selectingDrag) {
                    if (cx != selX1 || cy != selY1) {
                        selX1 = cx;
                        selY1 = cy;
                        int w = Math.abs(selX1 - selX0) + 1;
                        int h = Math.abs(selY1 - selY0) + 1;
                        if (listener != null) listener.onSelectionChanged(true, w, h);
                        invalidate();
                    }
                } else if (stampDragging && stampRegion != null) {
                    int dxCells = Math.round((x - stampDragStartScreenX) / cellPx);
                    int dyCells = Math.round((y - stampDragStartScreenY) / cellPx);
                    int nx = stampDragStartCellX + dxCells;
                    int ny = stampDragStartCellY + dyCells;
                    if (nx != stampX || ny != stampY) {
                        stampX = nx;
                        stampY = ny;
                        if (listener != null) listener.onStampMoved(stampX, stampY);
                        invalidate();
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(confirmStrokeRunnable);
                if (!multiTouchActive && action == MotionEvent.ACTION_UP) {
                    if (singleStrokeActive && !strokeConfirmed
                            && (toolMode == ToolMode.DRAW || toolMode == ToolMode.ERASE || toolMode == ToolMode.TOGGLE)) {
                        confirmAndFlushPendingStroke();
                    } else if (shapeDragging) {
                        shapeDragging = false;
                        commitShape();
                    } else if (selectingDrag) {
                        selectingDrag = false;
                        int w = Math.abs(selX1 - selX0) + 1;
                        int h = Math.abs(selY1 - selY0) + 1;
                        if (listener != null) listener.onSelectionChanged(true, w, h);
                        invalidate();
                    } else if (stampDragging && stampRegion != null) {
                        stampDragging = false;
                        float dist = (float) Math.hypot(event.getX() - stampDragStartScreenX, event.getY() - stampDragStartScreenY);
                        if (dist < UiHelper.dp(getContext(), 8)) {
                            // Tap places stamp centered at tapped cell!
                            stampX = screenToCellX(event.getX()) - stampRegion.width / 2;
                            stampY = screenToCellY(event.getY()) - stampRegion.height / 2;
                            if (listener != null) listener.onStampMoved(stampX, stampY);
                            invalidate();
                        }
                    }
                } else {
                    cancelPendingAndActiveEdits();
                }
                pendingStrokeCells.clear();
                singleStrokeActive = false;
                strokeConfirmed = false;
                undoRecordedForStroke = false;
                multiTouchActive = false;
                break;
            }
        }
        return true;
    }

    private void commitShape() {
        if (engine == null) return;
        engine.pushUndo();
        switch (toolMode) {
            case LINE:
                engine.drawLineWithSymmetry(shapeStartX, shapeStartY, shapeEndX, shapeEndY, true, symmetry);
                break;
            case RECT_OUTLINE:
                engine.drawRectWithSymmetry(shapeStartX, shapeStartY, shapeEndX, shapeEndY, false, true, symmetry);
                break;
            case RECT_FILL:
                engine.drawRectWithSymmetry(shapeStartX, shapeStartY, shapeEndX, shapeEndY, true, true, symmetry);
                break;
            case CIRCLE_OUTLINE:
                engine.drawCircleWithSymmetry(shapeStartX, shapeStartY, shapeEndX, shapeEndY, false, true, symmetry);
                break;
            case CIRCLE_FILL:
                engine.drawCircleWithSymmetry(shapeStartX, shapeStartY, shapeEndX, shapeEndY, true, true, symmetry);
                break;
            default:
                break;
        }
        engine.notifyManualEditComplete(true);
        if (soundEngine != null) soundEngine.playDrawPop(true);
        if (listener != null) listener.onCanvasEdited();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (engine == null) return;
        final int vw = getWidth();
        final int vh = getHeight();
        if (vw <= 0 || vh <= 0) return;

        final Topology top = engine.getTopology();
        final int gw = engine.getGridWidth();
        final int gh = engine.getGridHeight();

        // Background
        if (top == Topology.INFINITE) {
            canvas.drawColor(theme.bgColor);
        } else {
            canvas.drawColor(theme.outOfBoundsColor);
            float bl = (float) ((0 - camX) * cellPx + vw * 0.5f);
            float bt = (float) ((0 - camY) * cellPx + vh * 0.5f);
            float br = (float) ((gw - camX) * cellPx + vw * 0.5f);
            float bb = (float) ((gh - camY) * cellPx + vh * 0.5f);
            cellPaint.setColor(theme.bgColor);
            canvas.drawRect(Math.max(-2f, bl), Math.max(-2f, bt), Math.min(vw + 2f, br), Math.min(vh + 2f, bb), cellPaint);
        }

        // Visible world bounds
        final int minVX = (int) Math.floor(camX - (vw * 0.5f) / cellPx) - 1;
        final int maxVX = (int) Math.ceil(camX + (vw * 0.5f) / cellPx) + 1;
        final int minVY = (int) Math.floor(camY - (vh * 0.5f) / cellPx) - 1;
        final int maxVY = (int) Math.ceil(camY + (vh * 0.5f) / cellPx) + 1;

        final int decayMax = engine.getDecaySteps();
        final boolean includeDecay = decayMax > 0;
        final float halfW = vw * 0.5f;
        final float halfH = vh * 0.5f;

        // Grid lines when zoomed in
        if (showGrid && cellPx >= 6.5f) {
            int gMinX = top == Topology.INFINITE ? minVX : Math.max(0, minVX);
            int gMaxX = top == Topology.INFINITE ? maxVX : Math.min(gw, maxVX);
            int gMinY = top == Topology.INFINITE ? minVY : Math.max(0, minVY);
            int gMaxY = top == Topology.INFINITE ? maxVY : Math.min(gh, maxVY);

            int minorAlpha = Math.min(255, Math.round((cellPx - 6.5f) * 18f));
            gridPaint.setColor(theme.gridLineColor);
            gridPaint.setAlpha(Math.min(Color.alpha(theme.gridLineColor), minorAlpha));

            majorGridPaint.setColor(theme.majorGridLineColor);
            majorGridPaint.setAlpha(Math.min(Color.alpha(theme.majorGridLineColor), Math.max(80, minorAlpha)));

            for (int x = gMinX; x <= gMaxX; x++) {
                float sx = (float) ((x - camX) * cellPx + halfW);
                canvas.drawLine(sx, 0, sx, vh, (x % 10 == 0) ? majorGridPaint : gridPaint);
            }
            for (int y = gMinY; y <= gMaxY; y++) {
                float sy = (float) ((y - camY) * cellPx + halfH);
                canvas.drawLine(0, sy, vw, sy, (y % 10 == 0) ? majorGridPaint : gridPaint);
            }
        }

        // Origin / Symmetry axes
        if (showAxes && top == Topology.INFINITE) {
            axisPaint.setColor(theme.axisColor);
            float ox = (float) ((0 - camX) * cellPx + halfW);
            float oy = (float) ((0 - camY) * cellPx + halfH);
            if (ox >= -2 && ox <= vw + 2) canvas.drawLine(ox, 0, ox, vh, axisPaint);
            if (oy >= -2 && oy <= vh + 2) canvas.drawLine(0, oy, vw, oy, axisPaint);
        }

        // Cells rendering: Vector shapes when zoomed in, fast Bitmap buffer when zoomed far out
        if (cellPx >= 3.2f) {
            final float gap = cellPx >= 8f ? Math.min(2.4f, cellPx * 0.12f) : (cellPx >= 4.5f ? 0.6f : 0f);
            final float drawSize = Math.max(1f, cellPx - gap);
            final float halfGap = gap * 0.5f;
            final float cornerR = cellShape == 1 ? drawSize * 0.24f : 0f;
            final float circleR = drawSize * 0.48f;

            engine.visitCellsInRect(minVX, minVY, maxVX, maxVY, includeDecay, new LifeEngine.CellVisitor() {
                @Override
                public void visit(int wx, int wy, byte state) {
                    int col = theme.colorForCell(state, decayMax, ageColoring);
                    if (col == Color.TRANSPARENT) return;
                    cellPaint.setColor(col);
                    float sx = (float) ((wx - camX) * cellPx + halfW) + halfGap;
                    float sy = (float) ((wy - camY) * cellPx + halfH) + halfGap;
                    if (state < 0) {
                        // Decay trail slightly smaller & softer
                        float inset = drawSize * 0.12f;
                        if (cellShape == 2) {
                            canvas.drawCircle(sx + drawSize * 0.5f, sy + drawSize * 0.5f, circleR * 0.78f, cellPaint);
                        } else {
                            canvas.drawRect(sx + inset, sy + inset, sx + drawSize - inset, sy + drawSize - inset, cellPaint);
                        }
                    } else if (cellShape == 2 && drawSize >= 4f) {
                        canvas.drawCircle(sx + drawSize * 0.5f, sy + drawSize * 0.5f, circleR, cellPaint);
                    } else if (cellShape == 1 && cornerR >= 1.2f) {
                        canvas.drawRoundRect(sx, sy, sx + drawSize, sy + drawSize, cornerR, cornerR, cellPaint);
                    } else {
                        canvas.drawRect(sx, sy, sx + drawSize, sy + drawSize, cellPaint);
                    }
                }
            });
        } else {
            drawZoomedOutBitmap(canvas, vw, vh, minVX, minVY, maxVX, maxVY, decayMax, includeDecay);
        }

        // Finite grid boundary frame
        if (top != Topology.INFINITE) {
            float bl = (float) ((0 - camX) * cellPx + halfW);
            float bt = (float) ((0 - camY) * cellPx + halfH);
            float br = (float) ((gw - camX) * cellPx + halfW);
            float bb = (float) ((gh - camY) * cellPx + halfH);
            borderPaint.setColor(theme.borderColor);
            canvas.drawRect(bl, bt, br, bb, borderPaint);
        }

        // Symmetry guide lines
        if (symmetry != Symmetry.NONE) {
            int[] piv2 = new int[2];
            engine.getSymmetryPivot2(piv2);
            float pcx = (float) (((piv2[0] * 0.5 + 0.5) - camX) * cellPx + halfW);
            float pcy = (float) (((piv2[1] * 0.5 + 0.5) - camY) * cellPx + halfH);
            symLinePaint.setColor(theme.selectionColor);
            symLinePaint.setAlpha(120);
            if (symmetry == Symmetry.MIRROR_X || symmetry == Symmetry.FOUR_WAY || symmetry == Symmetry.EIGHT_WAY) {
                canvas.drawLine(pcx, 0, pcx, vh, symLinePaint);
            }
            if (symmetry == Symmetry.MIRROR_Y || symmetry == Symmetry.FOUR_WAY || symmetry == Symmetry.EIGHT_WAY) {
                canvas.drawLine(0, pcy, vw, pcy, symLinePaint);
            }
        }

        // Shape drag preview
        if (shapeDragging) {
            drawShapePreview(canvas, halfW, halfH);
        }

        // Selection box
        if (hasSelection) {
            int x0 = Math.min(selX0, selX1);
            int y0 = Math.min(selY0, selY1);
            int x1 = Math.max(selX0, selX1) + 1;
            int y1 = Math.max(selY0, selY1) + 1;
            float l = (float) ((x0 - camX) * cellPx + halfW);
            float t = (float) ((y0 - camY) * cellPx + halfH);
            float r = (float) ((x1 - camX) * cellPx + halfW);
            float b = (float) ((y1 - camY) * cellPx + halfH);
            selectFillPaint.setColor(theme.selectionColor);
            selectFillPaint.setAlpha(38);
            selectStrokePaint.setColor(theme.selectionColor);
            canvas.drawRect(l, t, r, b, selectFillPaint);
            canvas.drawRect(l, t, r, b, selectStrokePaint);
        }

        // Stamp ghost preview
        if (toolMode == ToolMode.STAMP && stampRegion != null) {
            drawStampPreview(canvas, halfW, halfH);
        }

        // Minimap
        if (showMinimap) {
            drawMinimap(canvas, vw, vh);
        }
    }

    private void drawZoomedOutBitmap(Canvas canvas, int vw, int vh,
                                     int minVX, int minVY, int maxVX, int maxVY,
                                     final int decayMax, boolean includeDecay) {
        final int scaleDown = 2;
        final int bw = Math.max(1, (vw + scaleDown - 1) / scaleDown);
        final int bh = Math.max(1, (vh + scaleDown - 1) / scaleDown);
        if (fastBitmap == null || fastW != bw || fastH != bh) {
            fastW = bw;
            fastH = bh;
            fastBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            fastPixels = new int[bw * bh];
        }
        java.util.Arrays.fill(fastPixels, 0);

        final float halfW = vw * 0.5f;
        final float halfH = vh * 0.5f;
        final float invScale = 1f / scaleDown;
        final int cellSidePx = Math.max(1, Math.round(cellPx * invScale));

        engine.visitCellsInRect(minVX, minVY, maxVX, maxVY, includeDecay, new LifeEngine.CellVisitor() {
            @Override
            public void visit(int wx, int wy, byte state) {
                int col = theme.colorForCell(state, decayMax, ageColoring);
                if (col == Color.TRANSPARENT) return;
                int px = Math.round(((float) ((wx - camX) * cellPx + halfW)) * invScale);
                int py = Math.round(((float) ((wy - camY) * cellPx + halfH)) * invScale);
                for (int dy = 0; dy < cellSidePx; dy++) {
                    int y = py + dy;
                    if (y < 0 || y >= fastH) continue;
                    int row = y * fastW;
                    for (int dx = 0; dx < cellSidePx; dx++) {
                        int x = px + dx;
                        if (x >= 0 && x < fastW) {
                            fastPixels[row + x] = col;
                        }
                    }
                }
            }
        });

        fastBitmap.setPixels(fastPixels, 0, fastW, 0, 0, fastW, fastH);
        srcRect.set(0, 0, fastW, fastH);
        dstRect.set(0, 0, vw, vh);
        canvas.drawBitmap(fastBitmap, srcRect, dstRect, bmpPaint);
    }

    private void drawShapePreview(Canvas canvas, float halfW, float halfH) {
        previewPaint.setColor(theme.selectionColor);
        previewPaint.setAlpha(185);
        final float drawSize = Math.max(2f, cellPx);
        LifeEngine.CellVisitor previewDrawer = new LifeEngine.CellVisitor() {
            @Override
            public void visit(int x, int y, byte s) {
                float sx = (float) ((x - camX) * cellPx + halfW);
                float sy = (float) ((y - camY) * cellPx + halfH);
                canvas.drawRect(sx, sy, sx + drawSize, sy + drawSize, previewPaint);
            }
        };

        if (toolMode == ToolMode.LINE) {
            int dx = Math.abs(shapeEndX - shapeStartX), dy = Math.abs(shapeEndY - shapeStartY);
            int sx = shapeStartX < shapeEndX ? 1 : -1, sy = shapeStartY < shapeEndY ? 1 : -1;
            int err = dx - dy, cx = shapeStartX, cy = shapeStartY;
            int guard = 0;
            while (guard++ < 4000) {
                previewDrawer.visit(cx, cy, (byte) 1);
                if (cx == shapeEndX && cy == shapeEndY) break;
                int e2 = err << 1;
                if (e2 > -dy) { err -= dy; cx += sx; }
                if (e2 < dx) { err += dx; cy += sy; }
            }
        } else if (toolMode == ToolMode.RECT_OUTLINE || toolMode == ToolMode.RECT_FILL) {
            int minX = Math.min(shapeStartX, shapeEndX), maxX = Math.max(shapeStartX, shapeEndX);
            int minY = Math.min(shapeStartY, shapeEndY), maxY = Math.max(shapeStartY, shapeEndY);
            float l = (float) ((minX - camX) * cellPx + halfW);
            float t = (float) ((minY - camY) * cellPx + halfH);
            float r = (float) ((maxX + 1 - camX) * cellPx + halfW);
            float b = (float) ((maxY + 1 - camY) * cellPx + halfH);
            if (toolMode == ToolMode.RECT_FILL) {
                previewPaint.setStyle(Paint.Style.FILL);
                previewPaint.setAlpha(110);
                canvas.drawRect(l, t, r, b, previewPaint);
            } else {
                previewPaint.setStyle(Paint.Style.STROKE);
                previewPaint.setStrokeWidth(Math.max(2f, cellPx * 0.7f));
                canvas.drawRect(l, t, r, b, previewPaint);
                previewPaint.setStyle(Paint.Style.FILL);
            }
        } else if (toolMode == ToolMode.CIRCLE_OUTLINE || toolMode == ToolMode.CIRCLE_FILL) {
            long dx = (long) shapeEndX - shapeStartX;
            long dy = (long) shapeEndY - shapeStartY;
            float radPx = (float) Math.sqrt(dx * dx + dy * dy) * cellPx;
            float scx = (float) ((shapeStartX + 0.5 - camX) * cellPx + halfW);
            float scy = (float) ((shapeStartY + 0.5 - camY) * cellPx + halfH);
            if (toolMode == ToolMode.CIRCLE_FILL) {
                previewPaint.setStyle(Paint.Style.FILL);
                previewPaint.setAlpha(110);
                canvas.drawCircle(scx, scy, Math.max(cellPx * 0.5f, radPx), previewPaint);
            } else {
                previewPaint.setStyle(Paint.Style.STROKE);
                previewPaint.setStrokeWidth(Math.max(2f, cellPx * 0.7f));
                canvas.drawCircle(scx, scy, Math.max(cellPx * 0.5f, radPx), previewPaint);
                previewPaint.setStyle(Paint.Style.FILL);
            }
        }
    }

    private void drawStampPreview(Canvas canvas, float halfW, float halfH) {
        float l = (float) ((stampX - camX) * cellPx + halfW);
        float t = (float) ((stampY - camY) * cellPx + halfH);
        float r = (float) ((stampX + stampRegion.width - camX) * cellPx + halfW);
        float b = (float) ((stampY + stampRegion.height - camY) * cellPx + halfH);

        selectFillPaint.setColor(theme.accentColor);
        selectFillPaint.setAlpha(28);
        canvas.drawRect(l, t, r, b, selectFillPaint);

        selectStrokePaint.setColor(theme.accentColor);
        canvas.drawRect(l, t, r, b, selectStrokePaint);

        previewPaint.setStyle(Paint.Style.FILL);
        previewPaint.setColor(theme.accentColor);
        previewPaint.setAlpha(190);
        float sz = Math.max(1.5f, cellPx * 0.88f);
        for (int y = 0; y < stampRegion.height; y++) {
            for (int x = 0; x < stampRegion.width; x++) {
                if (stampRegion.get(x, y)) {
                    float sx = (float) ((stampX + x - camX) * cellPx + halfW);
                    float sy = (float) ((stampY + y - camY) * cellPx + halfH);
                    canvas.drawRect(sx, sy, sx + sz, sy + sz, previewPaint);
                }
            }
        }
    }

    private void drawMinimap(Canvas canvas, int vw, int vh) {
        SimStats st = engine.getStats();
        int mapSize = UiHelper.dp(getContext(), 78);
        int margin = UiHelper.dp(getContext(), 12);
        // Place at top-right below top HUD (~94dp from top)
        float ml = vw - mapSize - margin;
        float mt = UiHelper.dp(getContext(), 98);
        if (mt + mapSize > vh - UiHelper.dp(getContext(), 130)) return; // skip on tiny landscape

        miniBgPaint.setColor(theme.hudBgColor);
        selectStrokePaint.setColor(theme.hudBorderColor);
        canvas.drawRoundRect(ml, mt, ml + mapSize, mt + mapSize, 14f, 14f, miniBgPaint);

        int bMinX, bMinY, bMaxX, bMaxY;
        if (engine.getTopology() != Topology.INFINITE) {
            bMinX = 0;
            bMinY = 0;
            bMaxX = engine.getGridWidth() - 1;
            bMaxY = engine.getGridHeight() - 1;
        } else if (st.hasBounds) {
            int pad = 16;
            bMinX = st.minX - pad;
            bMinY = st.minY - pad;
            bMaxX = st.maxX + pad;
            bMaxY = st.maxY + pad;
        } else {
            bMinX = -40; bMinY = -40; bMaxX = 40; bMaxY = 40;
        }

        int span = Math.max(16, Math.max(bMaxX - bMinX + 1, bMaxY - bMinY + 1));
        float cx = (bMinX + bMaxX + 1) * 0.5f;
        float cy = (bMinY + bMaxY + 1) * 0.5f;
        final float scale = (mapSize - 10f) / span;
        final float mcx = ml + mapSize * 0.5f;
        final float mcy = mt + mapSize * 0.5f;

        miniCellPaint.setColor(theme.cellMatureColor);
        final int stepSample = span > 200 ? 2 : 1;
        engine.visitCellsInRect(bMinX, bMinY, bMaxX, bMaxY, false, new LifeEngine.CellVisitor() {
            @Override
            public void visit(int wx, int wy, byte s) {
                if (stepSample > 1 && ((wx & 1) != 0 || (wy & 1) != 0)) return;
                float px = mcx + (wx - cx) * scale;
                float py = mcy + (wy - cy) * scale;
                if (px >= ml + 2 && px <= ml + mapSize - 2 && py >= mt + 2 && py <= mt + mapSize - 2) {
                    canvas.drawRect(px, py, px + Math.max(1.5f, scale), py + Math.max(1.5f, scale), miniCellPaint);
                }
            }
        });

        // Viewport indicator box
        float vMinX = (float) (camX - (vw * 0.5) / cellPx);
        float vMaxX = (float) (camX + (vw * 0.5) / cellPx);
        float vMinY = (float) (camY - (vh * 0.5) / cellPx);
        float vMaxY = (float) (camY + (vh * 0.5) / cellPx);

        float vl = Math.max(ml + 2, mcx + (vMinX - cx) * scale);
        float vt = Math.max(mt + 2, mcy + (vMinY - cy) * scale);
        float vr = Math.min(ml + mapSize - 2, mcx + (vMaxX - cx) * scale);
        float vb = Math.min(mt + mapSize - 2, mcy + (vMaxY - cy) * scale);
        if (vr > vl && vb > vt) {
            miniViewPaint.setColor(theme.accentColor);
            canvas.drawRect(vl, vt, vr, vb, miniViewPaint);
        }
    }

    public Bitmap renderSnapshotBitmap(int imgSizePx) {
        Bitmap out = Bitmap.createBitmap(imgSizePx, imgSizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(theme.bgColor);
        if (engine == null) return out;

        SimStats st = engine.getStats();
        int minX, minY, maxX, maxY;
        if (st.hasBounds) {
            int pad = Math.max(4, Math.max(st.maxX - st.minX, st.maxY - st.minY) / 10);
            minX = st.minX - pad;
            minY = st.minY - pad;
            maxX = st.maxX + pad;
            maxY = st.maxY + pad;
        } else {
            minX = -16; minY = -16; maxX = 16; maxY = 16;
        }
        int span = Math.max(1, Math.max(maxX - minX + 1, maxY - minY + 1));
        final float cell = (imgSizePx * 0.90f) / span;
        final float ox = (imgSizePx - (maxX - minX + 1) * cell) * 0.5f;
        final float oy = (imgSizePx - (maxY - minY + 1) * cell) * 0.5f;
        final int decayMax = engine.getDecaySteps();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (cell >= 6f) {
            Paint gp = new Paint();
            gp.setColor(theme.gridLineColor);
            gp.setStrokeWidth(1f);
            for (int x = minX; x <= maxX + 1; x++) {
                float sx = ox + (x - minX) * cell;
                c.drawLine(sx, oy, sx, oy + (maxY - minY + 1) * cell, gp);
            }
            for (int y = minY; y <= maxY + 1; y++) {
                float sy = oy + (y - minY) * cell;
                c.drawLine(ox, sy, ox + (maxX - minX + 1) * cell, sy, gp);
            }
        }

        final float gap = cell >= 6f ? cell * 0.12f : 0f;
        final float drawSz = Math.max(1f, cell - gap);
        final int fMinX = minX, fMinY = minY;
        engine.visitCellsInRect(minX, minY, maxX, maxY, decayMax > 0, new LifeEngine.CellVisitor() {
            @Override
            public void visit(int wx, int wy, byte state) {
                int col = theme.colorForCell(state, decayMax, ageColoring);
                if (col == Color.TRANSPARENT) return;
                p.setColor(col);
                float sx = ox + (wx - fMinX) * cell + gap * 0.5f;
                float sy = oy + (wy - fMinY) * cell + gap * 0.5f;
                if (cellShape == 2 && drawSz >= 4f) {
                    c.drawCircle(sx + drawSz * 0.5f, sy + drawSz * 0.5f, drawSz * 0.48f, p);
                } else if (cellShape == 1 && drawSz >= 4f) {
                    float r = drawSz * 0.24f;
                    c.drawRoundRect(sx, sy, sx + drawSz, sy + drawSz, r, r, p);
                } else {
                    c.drawRect(sx, sy, sx + drawSz, sy + drawSz, p);
                }
            }
        });
        return out;
    }
}

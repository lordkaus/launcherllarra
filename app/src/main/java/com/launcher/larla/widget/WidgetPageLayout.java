package com.launcher.larla.widget;

import android.app.AlertDialog;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.ViewOutlineProvider;
import android.os.Handler;
import android.os.Looper;
import android.widget.OverScroller;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.animation.DecelerateInterpolator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.launcher.larla.R;
import com.launcher.larla.util.GestureConfig;
import com.launcher.larla.view.LongPressIndicator;
import com.launcher.larla.view.ModernSheet;

import java.util.ArrayList;
import java.util.List;

public class WidgetPageLayout extends FrameLayout {

    public static final int CLOCK_WIDGET_ID = -1;
    public static final int MAX_PAGES = 20;

    public interface Callback {
        void onWidgetRemoved(int appWidgetId);
        void onWidgetMoved(int appWidgetId, int page, int x, int y);
        void onWidgetResized(int appWidgetId, int w, int h);
        void onWidgetOpacityChanged(int appWidgetId, int opacity);
        void onPageChanged(int page, int total);
        void onEmptyAreaLongPress(float rawX, float rawY);
        void onWorkspaceContextMenu(int page, boolean canAddPage, boolean canRemovePage);
        void onSwipeUp();
        void onAppShortcutLaunch(String packageName);
        void onAppShortcutGesture(String packageName, String gesture);
        boolean hasAppShortcutDoubleTap(String packageName);
        void onAppShortcutRemove(String packageName);
        void onAppShortcutMove(String packageName, int page, int x, int y);
        void onAppShortcutInfo(String packageName);
        void onAppShortcutGestures(String packageName);
        void onAppShortcutAddToFolder(String packageName);
        void onFolderTap(String folderId);
        void onFolderMenu(View widget, String folderId);
        void onFolderTrash(String folderId);
        void onWorkspaceDropOnFolder(String folderId, Object draggedTag, float rawX, float rawY);
        void onWorkspaceDropOnShortcut(String targetPkg, Object draggedTag, float rawX, float rawY);
        void onGlobalGesture(String gestureKey);
    }

    public static boolean isFolderTag(Object tag) {
        return tag instanceof String && ((String) tag).startsWith("folder:");
    }

    public static String folderIdOf(Object tag) {
        if (!isFolderTag(tag)) return null;
        return ((String) tag).substring(7);
    }

    private static final float MARGIN_DP = 16f;
    private static final float MOVE_THRESHOLD = 10f;
    private static final float HANDLE_RADIUS_DP = 14f;
    private static final float HANDLE_TOUCH_DP = 32f;
    private static final int MIN_SIZE_DP = 40;
    private static final long LONG_PRESS_MS = 400;

    private Callback callback;
    private LinearLayout pagesContainer;
    private List<FrameLayout> pages = new ArrayList<>();
    private int currentPage = 0;
    private OverScroller scroller;
    private ValueAnimator pageAnimator;
    private float density;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // touch state
    private View touchedWidget;
    private View pendingAppWidget;
    private float downRawX, downRawY;
    private boolean moved = false;
    private boolean waitingLongPress = false;
    private boolean waitingEmptyLongPress = false;

    // gestos por atalho: fling + duplo toque
    private long iconDownTime;
    private float lastMoveX, lastMoveY;
    private long lastMoveTime;
    private boolean lastMoveValid;
    private float flingMaxV;
    private String tapPkg;
    private long tapTime;
    private Runnable pendingTap;
    private boolean tapConsumed;

    // drag
    private View dragWidget;
    private float dragOffsetX, dragOffsetY;
    private float dragPageX, dragPageY;
    private float dragStartRawX, dragStartRawY;
    private float saveOrigLeft, saveOrigTop;
    private boolean isDragging = false;
    private final int[] tmpLoc = new int[2];

    // resize
    private View resizeWidget;
    private int resizeHandle = -1;
    private float resizeStartX, resizeStartY;
    private int resizeOrigW, resizeOrigH, resizeOrigLeft, resizeOrigTop;
    private boolean isResizing = false;

    // gestos globais (só em área vazia)
    private GestureConfig gestureConfig;
    private boolean globalArmed;
    private boolean multiTouch;
    private boolean pinchDone;
    private boolean rotDone;
    private boolean twoSwipeDone;
    private float twoX0, twoY0, twoX1, twoY1;
    private float twoStartDist;
    private float twoStartAngle;
    private float twoMidStartX, twoMidStartY;
    private double rotAccum;
    private long lastTapUp;
    private float lastTapX, lastTapY;
    private boolean dtPending;
    private long dtDownTime;
    private float dtStartX, dtStartY;
    private boolean dtDragMode;
    private boolean suppressTap;

    private View trashView;
    private View gearView;
    private boolean trashHover = false;
    private LongPressIndicator indicator;
    private int widgetCornerRadiusDp;
    private LinearLayout workspacePageDots;
    private final Handler dotsHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideDots = () -> {
        if (workspacePageDots != null) {
            workspacePageDots.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> workspacePageDots.setVisibility(View.GONE)).start();
        }
    };
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sizeLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRectF = new RectF();
    private final GestureDetector gd;

    private final Runnable longPressAction = () -> {
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (pendingAppWidget != null && !moved) {
            waitingLongPress = false;
            if (indicator != null) {
                int[] parentLoc = new int[2];
                ((View) indicator.getParent()).getLocationOnScreen(parentLoc);
                float px = downRawX - parentLoc[0] - indicator.getWidth() / 2f;
                float py = downRawY - parentLoc[1] - indicator.getHeight() / 2f;
                indicator.setX(px);
                indicator.setY(py);
                indicator.setFillColor(0xFF00CC00);
                final View pendingTarget = pendingAppWidget;
                indicator.start(() -> {
                    if (pendingTarget == null || pendingTarget.getParent() == null
                            || isDragging || isResizing) return;
                    dragWidget = pendingTarget;
                    isDragging = true;
                    setDragOffset(pendingTarget);
                    showGearIcon(pendingTarget);
                    showTrash(true);
                    if (pendingAppWidget == pendingTarget) pendingAppWidget = null;
                    invalidate();
                });
                return;
            }
            dragWidget = pendingAppWidget;
            isDragging = true;
            setDragOffset(pendingAppWidget);
            showGearIcon(pendingAppWidget);
            showTrash(true);
            pendingAppWidget = null;
            invalidate();
            return;
        }
        if (touchedWidget != null && !moved && !isDragging) {
            waitingLongPress = false;
            if (indicator != null) {
                int[] parentLoc = new int[2];
                ((View) indicator.getParent()).getLocationOnScreen(parentLoc);
                float px = downRawX - parentLoc[0] - indicator.getWidth() / 2f;
                float py = downRawY - parentLoc[1] - indicator.getHeight() / 2f;
                indicator.setX(px);
                indicator.setY(py);
                indicator.setFillColor(0xFF00CC00);
                final View touchedTarget = touchedWidget;
                indicator.start(() -> {
                    if (touchedTarget == null || touchedTarget.getParent() == null
                            || isDragging || isResizing) return;
                    dragWidget = touchedTarget;
                    isDragging = true;
                    setDragOffset(touchedTarget);
                    showTrash(true);
                    invalidate();
                });
                return;
            }
            dragWidget = touchedWidget;
            isDragging = true;
            setDragOffset(touchedWidget);
            showTrash(true);
        }
    };

    private final Runnable emptyAreaLongPressAction = () -> {
        if (!moved && !isDragging && !isResizing) {
            waitingEmptyLongPress = false;
            if (callback != null) {
                callback.onEmptyAreaLongPress(downRawX, downRawY);
            }
        }
    };

    private void setDragOffset(View widget) {
        if (widget == null) return;
        saveOrigLeft = widget.getLeft();
        saveOrigTop = widget.getTop();
        if (currentPage >= 0 && currentPage < pages.size()) pages.get(currentPage).getLocationOnScreen(tmpLoc);
        else { tmpLoc[0] = 0; tmpLoc[1] = 0; }
        dragPageX = tmpLoc[0];
        dragPageY = tmpLoc[1];
        dragStartRawX = downRawX;
        dragStartRawY = downRawY;
        widget.setTranslationX(0);
        widget.setTranslationY(0);
        dragOffsetX = downRawX - tmpLoc[0] - widget.getLeft();
        dragOffsetY = downRawY - tmpLoc[1] - widget.getTop();
    }

    private void applyDragTranslation(float rawX, float rawY) {
        if (dragWidget == null) return;
        dragWidget.setTranslationX(rawX - dragStartRawX);
        dragWidget.setTranslationY(rawY - dragStartRawY);
        updateTrashHover(rawX, rawY);
    }

    private void handleTwoFingerMove(MotionEvent e) {
        if (!pinchDone && twoStartDist > 0) {
            float ratio = fingerDist(e) / twoStartDist;
            if (ratio < 0.85f) {
                pinchDone = true;
                fireGlobalGesture(GestureConfig.PINCH);
            } else if (ratio > 1.18f) {
                pinchDone = true;
                fireGlobalGesture(GestureConfig.ZOOM);
            }
        }
        if (!rotDone) {
            float delta = fingerAngle(e) - twoStartAngle;
            while (delta > 180) delta -= 360;
            while (delta < -180) delta += 360;
            rotAccum = delta;
            if (rotAccum <= -35) {
                rotDone = true;
                fireGlobalGesture(GestureConfig.ROT_CCW);
            } else if (rotAccum >= 35) {
                rotDone = true;
                fireGlobalGesture(GestureConfig.ROT_CW);
            }
        }
        if (!twoSwipeDone) {
            float slop = 64 * density;
            float dx = (e.getX(0) + e.getX(1)) / 2f - twoMidStartX;
            float dy = (e.getY(0) + e.getY(1)) / 2f - twoMidStartY;
            if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                twoSwipeDone = true;
                if (Math.abs(dx) > Math.abs(dy)) {
                    fireGlobalGesture(dx < 0 ? GestureConfig.SWIPE_LEFT : GestureConfig.SWIPE_RIGHT);
                } else {
                    fireGlobalGesture(dy < 0 ? GestureConfig.TWO_UP : GestureConfig.TWO_DOWN);
                }
            }
        }
        moved = true;
    }

    private void updateTrashHover(float rawX, float rawY) {
        boolean over = isDragging && isInTrash(rawX, rawY);
        if (over == trashHover) return;
        trashHover = over;
        if (trashView != null && trashView.getVisibility() == View.VISIBLE) {
            trashView.animate().scaleX(over ? 1.12f : 1f).scaleY(over ? 1.12f : 1f)
                .setDuration(150).start();
        }
    }

    public WidgetPageLayout(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
        density = getResources().getDisplayMetrics().density;

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * density);
        borderPaint.setColor(0xFF00D4FF);

        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0xFF00D4FF);
        handleStroke.setStyle(Paint.Style.STROKE);
        handleStroke.setStrokeWidth(2f * density);
        handleStroke.setColor(0xFFFFFFFF);

        sizeLabelPaint.setColor(0xFFFFFFFF);
        sizeLabelPaint.setTextSize(14f * density);
        sizeLabelPaint.setTextAlign(Paint.Align.CENTER);
        sizeLabelPaint.setShadowLayer(4f * density, 0, 0, 0xAA000000);

        pagesContainer = new LinearLayout(ctx);
        pagesContainer.setOrientation(LinearLayout.HORIZONTAL);
        pagesContainer.setClipChildren(false);
        addView(pagesContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scroller = new OverScroller(ctx);
        addPage();

        gd = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (isDragging || isResizing || multiTouch || suppressTap) return false;
                float swipeUpSlop = 24 * density;
                float pageSlop = 32 * density;
                if (e1 != null && e2 != null && e2.getY() - e1.getY() < -swipeUpSlop && Math.abs(vy) > swipeUpSlop * 3) {
                    fireGlobalGesture(GestureConfig.SWIPE_UP);
                    return true;
                }
                if (e1 != null && e2 != null && e2.getY() - e1.getY() > swipeUpSlop && Math.abs(vy) > swipeUpSlop * 3
                        && Math.abs(vy) > Math.abs(vx)) {
                    fireGlobalGesture(GestureConfig.SWIPE_DOWN);
                    return true;
                }
                if (Math.abs(vx) > pageSlop * 3 && Math.abs(vx) > Math.abs(vy)) {
                    String key = vx < 0 ? GestureConfig.SWIPE_LEFT : GestureConfig.SWIPE_RIGHT;
                    // Prioridade para a transição de páginas; o gesto customizado
                    // só dispara quando não há mais página naquela direção.
                    boolean canTurn = vx < 0 ? currentPage < pages.size() - 1 : currentPage > 0;
                    if (canTurn) {
                        if (vx < 0) goToPage(currentPage + 1);
                        else goToPage(currentPage - 1);
                        return true;
                    }
                    String a = gestures().getAction(key);
                    if (!GestureConfig.ACT_NONE.equals(a)) {
                        fireGlobalGesture(key);
                        return true;
                    }
                    return true;
                }
                return false;
            }
        });
    }

    public void setGestureConfig(GestureConfig g) { this.gestureConfig = g; }

    private GestureConfig gestures() {
        if (gestureConfig == null) gestureConfig = new GestureConfig(getContext());
        return gestureConfig;
    }

    private void fireGlobalGesture(String key) {
        if (callback != null) callback.onGlobalGesture(key);
    }

    public void setCallback(Callback cb) { this.callback = cb; }
    public void setTrashView(View v) { this.trashView = v; }
    public void setLongPressIndicator(LongPressIndicator v) { this.indicator = v; }
    public void setWorkspacePageDots(LinearLayout dots) { this.workspacePageDots = dots; }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
        dotsHandler.removeCallbacksAndMessages(null);
        if (pageAnimator != null) pageAnimator.cancel();
        hideGearIcon();
    }

    public void setWidgetCornerRadius(int dp) {
        widgetCornerRadiusDp = dp;
        if (getWidth() <= 0) {
            post(this::applyRoundingToAllWidgets);
            return;
        }
        applyRoundingToAllWidgets();
    }

    private void applyRoundingToAllWidgets() {
        for (FrameLayout page : pages) {
            for (int i = 0; i < page.getChildCount(); i++) {
                applyRounding(page.getChildAt(i));
            }
        }
    }

    public void applyRounding(View v) {
        // v is the wrapper; find the inner view
        View inner = null;
        if (v instanceof FrameLayout && ((FrameLayout) v).getChildCount() > 0) {
            inner = ((FrameLayout) v).getChildAt(0);
        }

        if (inner instanceof RoundedAppWidgetHostView) {
            if (widgetCornerRadiusDp <= 0) {
                ((RoundedAppWidgetHostView) inner).setCornerRadiusPx(0);
                inner.setClipToOutline(false);
            } else {
                inner.setClipToOutline(true);
                ((RoundedAppWidgetHostView) inner).setCornerRadiusPx(widgetCornerRadiusDp * density);
            }
        } else {
            // clock or non-widget view – apply rounding on wrapper
            if (widgetCornerRadiusDp <= 0) {
                v.setOutlineProvider(null);
                v.setClipToOutline(false);
                return;
            }
            if (v.getWidth() <= 0 || v.getHeight() <= 0) {
                v.post(() -> applyRounding(v));
                return;
            }
            final float r = widgetCornerRadiusDp * density;
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                }
            });
            v.setClipToOutline(true);
        }
    }

    private void showTrash(boolean show) {
        if (trashView != null) {
            trashView.setVisibility(show ? View.VISIBLE : View.GONE);
            trashHover = false;
            trashView.setScaleX(1f);
            trashView.setScaleY(1f);
        }
    }

    private boolean isInTrash(float rawX, float rawY) {
        if (trashView == null || trashView.getVisibility() != View.VISIBLE) return false;
        trashView.getLocationOnScreen(tmpLoc);
        return rawX >= tmpLoc[0] && rawX <= tmpLoc[0] + trashView.getWidth()
            && rawY >= tmpLoc[1] && rawY <= tmpLoc[1] + trashView.getHeight();
    }

    private void stopLongPress() {
        handler.removeCallbacks(longPressAction);
        handler.removeCallbacks(emptyAreaLongPressAction);
        waitingLongPress = false;
        waitingEmptyLongPress = false;
        pendingAppWidget = null;
    }

    // --- GEAR ICON ---

    private void showGearIcon(final View widget) {
        hideGearIcon();
        gearView = new TextView(getContext());
        ((TextView) gearView).setText("\u2699");
        ((TextView) gearView).setTextSize(18);
        ((TextView) gearView).setTextColor(0xFFFFFFFF);
        ((TextView) gearView).setGravity(android.view.Gravity.CENTER);
        int sz = (int) (36 * density);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(0x55000000);
        bg.setStroke((int) (2 * density), 0xFF00D4FF);
        gearView.setBackground(bg);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(sz, sz);
        int off = (int) (-4 * density);
        p.topMargin = Math.max(0, widget.getTop() + off);
        p.leftMargin = Math.max(0, widget.getRight() - sz / 2);
        gearView.setLayoutParams(p);
        gearView.setOnClickListener(v -> {
            showWidgetMenu(widget);
            hideGearIcon();
            isDragging = false;
            dragWidget = null;
            showTrash(false);
            invalidate();
        });
        if (getParent() instanceof FrameLayout) ((FrameLayout) getParent()).addView(gearView);
    }

    private void hideGearIcon() {
        if (gearView != null) {
            if (getParent() instanceof FrameLayout) ((FrameLayout) getParent()).removeView(gearView);
            gearView = null;
        }
    }

    // --- MENU ---

    private void showWidgetMenu(View widget) {
        Object tag = widget.getTag();
        android.app.Activity act = getContext() instanceof android.app.Activity
                ? (android.app.Activity) getContext() : null;
        if (isFolderTag(tag)) {
            if (callback != null) callback.onFolderMenu(widget, folderIdOf(tag));
            return;
        }
        if (tag instanceof String) {
            final String pkg = (String) tag;
            if (act != null) {
                java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
                items.add(new ModernSheet.Item("\uD83D\uDDD1️", getContext().getString(R.string.remove)));
                items.add(new ModernSheet.Item("ℹ️", getContext().getString(R.string.info)));
                items.add(new ModernSheet.Item("\uD83D\uDC46", getContext().getString(R.string.gestures)));
                items.add(new ModernSheet.Item("\uD83D\uDCC1", getContext().getString(R.string.add_to_folder)));
                ModernSheet.show(act, getContext().getString(R.string.shortcut_title), items, which -> {
                    switch (which) {
                        case 0:
                            widget.animate().translationY(-300f).alpha(0f).setDuration(200)
                                .withEndAction(() -> {
                                    FrameLayout p = (FrameLayout) widget.getParent();
                                    if (p != null) p.removeView(widget);
                                    if (callback != null) callback.onAppShortcutRemove(pkg);
                                }).start();
                            break;
                        case 1:
                            if (callback != null) callback.onAppShortcutInfo(pkg);
                            break;
                        case 2:
                            if (callback != null) callback.onAppShortcutGestures(pkg);
                            break;
                        case 3:
                            if (callback != null) callback.onAppShortcutAddToFolder(pkg);
                            break;
                    }
                });
                return;
            }
            new AlertDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.shortcut_title))
                .setItems(new String[]{getContext().getString(R.string.remove), getContext().getString(R.string.info), getContext().getString(R.string.gestures), getContext().getString(R.string.add_to_folder)}, (d, which) -> {
                    switch (which) {
                        case 0:
                            widget.animate().translationY(-300f).alpha(0f).setDuration(200)
                                .withEndAction(() -> {
                                    FrameLayout p = (FrameLayout) widget.getParent();
                                    if (p != null) p.removeView(widget);
                                    if (callback != null) callback.onAppShortcutRemove(pkg);
                                }).start();
                            break;
                        case 1:
                            if (callback != null) callback.onAppShortcutInfo(pkg);
                            break;
                        case 2:
                            if (callback != null) callback.onAppShortcutGestures(pkg);
                            break;
                        case 3:
                            if (callback != null) callback.onAppShortcutAddToFolder(pkg);
                            break;
                    }
                }).show();
            return;
        }
        int id = getWidgetId(widget);
        if (id < 0 && id != CLOCK_WIDGET_ID) return;
        boolean isClock = (id == CLOCK_WIDGET_ID);
        if (act != null) {
            java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
            items.add(new ModernSheet.Item("↔️", getContext().getString(R.string.resize)));
            items.add(new ModernSheet.Item("\uD83D\uDD05", getContext().getString(R.string.opacity)));
            items.add(new ModernSheet.Item("\uD83D\uDDD1️", getContext().getString(R.string.remove)));
            final int wid = id;
            ModernSheet.show(act, getContext().getString(isClock ? R.string.clock_title : R.string.widget_title), items, which -> {
                switch (which) {
                    case 0: enterResizeMode(widget); break;
                    case 1: showOpacityDialog(widget, wid); break;
                    case 2:
                        widget.animate().translationY(-300f).alpha(0f).setDuration(200)
                            .withEndAction(() -> {
                                FrameLayout p = (FrameLayout) widget.getParent();
                                if (p != null) p.removeView(widget);
                                if (isValidWidget(wid) && callback != null) callback.onWidgetRemoved(wid);
                            }).start();
                        break;
                }
            });
            return;
        }
        if (isClock) {
            new AlertDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.clock_title))
                .setItems(new String[]{getContext().getString(R.string.resize), getContext().getString(R.string.opacity), getContext().getString(R.string.remove)}, (d, which) -> {
                    switch (which) {
                        case 0: enterResizeMode(widget); break;
                        case 1: showOpacityDialog(widget, CLOCK_WIDGET_ID); break;
                        case 2:
                            widget.animate().translationY(-300f).alpha(0f).setDuration(200)
                                .withEndAction(() -> {
                                    FrameLayout p = (FrameLayout) widget.getParent();
                                    if (p != null) p.removeView(widget);
                                    if (callback != null) callback.onWidgetRemoved(CLOCK_WIDGET_ID);
                                }).start();
                            break;
                    }
                }).show();
        } else {
            new AlertDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.widget_title))
                .setItems(new String[]{getContext().getString(R.string.resize), getContext().getString(R.string.opacity), getContext().getString(R.string.remove)}, (d, which) -> {
                    switch (which) {
                        case 0:
                            enterResizeMode(widget);
                            break;
                        case 1:
                            showOpacityDialog(widget, id);
                            break;
                        case 2:
                            widget.animate().translationY(-300f).alpha(0f).setDuration(200)
                                .withEndAction(() -> {
                                    FrameLayout p = (FrameLayout) widget.getParent();
                                    if (p != null) p.removeView(widget);
                                    if (isValidWidget(id) && callback != null) callback.onWidgetRemoved(id);
                                }).start();
                            break;
                    }
                }).show();
        }
    }

    private void showOpacityDialog(View wrapper, int widgetId) {
        android.app.Activity act = getContext() instanceof android.app.Activity
                ? (android.app.Activity) getContext() : null;
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(getContext());
        android.view.View dialogView = inflater.inflate(R.layout.dialog_opacity, null);

        SeekBar seekBar = dialogView.findViewById(R.id.seek_opacity);
        TextView label = dialogView.findViewById(R.id.lbl_opacity);

        int current = (int) (wrapper.getAlpha() * 100);
        seekBar.setProgress(current);
        label.setText(current + "%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    label.setText(progress + "%");
                    wrapper.setAlpha(progress / 100f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        if (act != null) {
            ModernSheet.showCustom(act, getContext().getString(R.string.opacity), dialogView,
                getContext().getString(R.string.ok), getContext().getString(R.string.cancel), () -> {
                    int opacity = seekBar.getProgress();
                    if (callback != null) callback.onWidgetOpacityChanged(widgetId, opacity);
                }, () -> wrapper.setAlpha(current / 100f));
            return;
        }
        new AlertDialog.Builder(getContext())
            .setTitle(getContext().getString(R.string.opacity))
            .setView(dialogView)
            .setPositiveButton(getContext().getString(R.string.ok), (d, w) -> {
                int opacity = seekBar.getProgress();
                if (callback != null) callback.onWidgetOpacityChanged(widgetId, opacity);
            })
            .setNegativeButton(getContext().getString(R.string.cancel), (d, w) -> {
                wrapper.setAlpha(current / 100f);
            })
            .show();
    }

    // --- RESIZE ---

    private void enterResizeMode(View widget) {
        resizeWidget = widget;
        isResizing = true;
        resizeOrigLeft = widget.getLeft();
        resizeOrigTop = widget.getTop();
        resizeOrigW = widget.getWidth();
        resizeOrigH = widget.getHeight();
        invalidate();
    }

    private void exitResizeMode() {
        if (resizeWidget != null && isResizing) {
            int id = getWidgetId(resizeWidget);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) resizeWidget.getLayoutParams();
            if (isValidWidget(id) && callback != null) callback.onWidgetResized(id, lp.width, lp.height);
        }
        resizeWidget = null;
        isResizing = false;
        resizeHandle = -1;
        invalidate();
    }

    private int findHandleAt(float rawX, float rawY) {
        if (resizeWidget == null) return -1;
        float r = HANDLE_TOUCH_DP * density;
        float l = resizeWidget.getLeft(), t = resizeWidget.getTop();
        float ri = resizeWidget.getRight(), b = resizeWidget.getBottom();
        if (dist(rawX, rawY, l, t) < r) return 0;
        if (dist(rawX, rawY, ri, t) < r) return 1;
        if (dist(rawX, rawY, l, b) < r) return 2;
        if (dist(rawX, rawY, ri, b) < r) return 3;
        return -1;
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void applyResize(float rawX, float rawY) {
        if (resizeWidget == null || resizeHandle < 0) return;
        float dx = rawX - resizeStartX;
        float dy = rawY - resizeStartY;
        int min = (int) (MIN_SIZE_DP * density);
        int m = (int) (MARGIN_DP * density);
        int maxW = getWidth() - 2 * m;
        int maxH = getHeight() - 2 * m;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) resizeWidget.getLayoutParams();

        int newW, newH;
        switch (resizeHandle) {
            case 0:
                newW = clamp(resizeOrigW - (int) dx, min, maxW);
                newH = clamp(resizeOrigH - (int) dy, min, maxH);
                lp.leftMargin = resizeOrigLeft + resizeOrigW - newW;
                lp.topMargin = resizeOrigTop + resizeOrigH - newH;
                break;
            case 1:
                newW = clamp(resizeOrigW + (int) dx, min, maxW);
                newH = clamp(resizeOrigH - (int) dy, min, maxH);
                lp.leftMargin = resizeOrigLeft;
                lp.topMargin = resizeOrigTop + resizeOrigH - newH;
                break;
            case 2:
                newW = clamp(resizeOrigW - (int) dx, min, maxW);
                newH = clamp(resizeOrigH + (int) dy, min, maxH);
                lp.leftMargin = resizeOrigLeft + resizeOrigW - newW;
                lp.topMargin = resizeOrigTop;
                break;
            default:
                newW = clamp(resizeOrigW + (int) dx, min, maxW);
                newH = clamp(resizeOrigH + (int) dy, min, maxH);
                lp.leftMargin = resizeOrigLeft;
                lp.topMargin = resizeOrigTop;
                break;
        }
        lp.width = newW;
        lp.height = newH;
        resizeWidget.setLayoutParams(lp);
        invalidate();
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(v, max)); }

    // --- PAGES ---

    public int addPage() {
        if (pages.size() >= MAX_PAGES) return -1;
        FrameLayout page = new FrameLayout(getContext());
        page.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        page.setClipChildren(false);
        pages.add(page);
        pagesContainer.addView(page);
        return pages.size() - 1;
    }

    public void removePage(int index) {
        if (pages.size() <= 1 || index < 0 || index >= pages.size()) return;
        FrameLayout page = pages.remove(index);
        pagesContainer.removeView(page);
        if (currentPage >= pages.size()) currentPage = pages.size() - 1;
        goToPage(currentPage);
    }

    public void ensurePagesExist(int pageIndex) {
        while (pages.size() <= pageIndex) {
            if (addPage() < 0) break;
        }
    }

    public boolean isPageEmpty(int index) {
        if (index < 0 || index >= pages.size()) return true;
        return pages.get(index).getChildCount() == 0;
    }

    public void goToPage(int page) {
        goToPage(page, true);
    }

    private void goToPage(int page, boolean animate) {
        if (page < 0 || page >= pages.size()) return;
        final int targetPage = page;
        currentPage = targetPage;
        if (!animate || getWidth() <= 0) {
            pagesContainer.setTranslationX(-targetPage * getWidth());
            if (callback != null) callback.onPageChanged(currentPage, pages.size());
            updateWorkspaceDots();
            return;
        }
        final float startX = pagesContainer.getTranslationX();
        final float endX = -targetPage * getWidth();
        if (Math.abs(startX - endX) < 1f) {
            if (callback != null) callback.onPageChanged(currentPage, pages.size());
            updateWorkspaceDots();
            return;
        }
        if (pageAnimator != null && pageAnimator.isRunning()) pageAnimator.cancel();
        pagesContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        pageAnimator = ValueAnimator.ofFloat(startX, endX);
        pageAnimator.setDuration(280);
        pageAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        pageAnimator.addUpdateListener(a -> {
            float val = (float) a.getAnimatedValue();
            pagesContainer.setTranslationX(val);
        });
        pageAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                pagesContainer.setTranslationX(endX);
                pagesContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                if (callback != null) callback.onPageChanged(currentPage, pages.size());
                updateWorkspaceDots();
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                pagesContainer.setTranslationX(endX);
                pagesContainer.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        pageAnimator.start();
    }

    private android.graphics.drawable.GradientDrawable wsDotOn;
    private android.graphics.drawable.GradientDrawable wsDotOff;

    private void updateWorkspaceDots() {
        if (workspacePageDots == null) return;
        dotsHandler.removeCallbacks(hideDots);
        workspacePageDots.animate().cancel();
        workspacePageDots.setAlpha(1f);

        if (pages.size() <= 1) {
            workspacePageDots.setVisibility(View.GONE);
            return;
        }

        if (wsDotOn == null) {
            wsDotOn = new android.graphics.drawable.GradientDrawable();
            wsDotOn.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            wsDotOn.setColor(0xFFFFFFFF);
            wsDotOff = new android.graphics.drawable.GradientDrawable();
            wsDotOff.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            wsDotOff.setColor(0x44FFFFFF);
        }
        int dotSize = (int) (8 * density);
        int dotMargin = (int) (4 * density);

        int existing = workspacePageDots.getChildCount();
        if (existing != pages.size()) {
            workspacePageDots.removeAllViews();
            for (int i = 0; i < pages.size(); i++) {
                View dot = new View(getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
                lp.setMargins(dotMargin, 0, dotMargin, 0);
                dot.setLayoutParams(lp);
                workspacePageDots.addView(dot);
            }
        }

        for (int i = 0; i < pages.size(); i++) {
            workspacePageDots.getChildAt(i).setBackground(i == currentPage ? wsDotOn : wsDotOff);
        }

        workspacePageDots.setVisibility(View.VISIBLE);
        workspacePageDots.postDelayed(hideDots, 1000);
    }

    public FrameLayout getCurrentPageLayout() { return pages.get(currentPage); }

    public FrameLayout getPageLayout(int index) {
        if (index < 0 || index >= pages.size()) return null;
        return pages.get(index);
    }

    public void startDragOn(View wrapper, float rawX, float rawY, int origLeft, int origTop) {
        saveOrigLeft = origLeft;
        saveOrigTop = origTop;
        if (currentPage >= 0 && currentPage < pages.size()) pages.get(currentPage).getLocationOnScreen(tmpLoc);
        else { tmpLoc[0] = 0; tmpLoc[1] = 0; }
        dragPageX = tmpLoc[0];
        dragPageY = tmpLoc[1];
        dragStartRawX = rawX;
        dragStartRawY = rawY;
        downRawX = rawX;
        downRawY = rawY;
        wrapper.setTranslationX(0);
        wrapper.setTranslationY(0);
        dragOffsetX = rawX - tmpLoc[0] - origLeft;
        dragOffsetY = rawY - tmpLoc[1] - origTop;
        dragWidget = wrapper;
        isDragging = true;
        showTrash(true);
        invalidate();
    }

    public void continueDrag(float rawX, float rawY) {
        if (!isDragging || dragWidget == null) return;
        applyDragTranslation(rawX, rawY);
        invalidate();
    }

    public void endDrag(float rawX, float rawY) {
        finishDrag(dragWidget, rawX, rawY);
    }

    /** Soltura rápida sobre atalho = gesto (swipe_*); senão segue o fluxo de mover. */
    private boolean tryShortcutFling(float rawX, float rawY) {
        try {
            Object tag = dragWidget != null ? dragWidget.getTag() : null;
            if (!(tag instanceof String) || isFolderTag(tag)) return false;
            String pkg = (String) tag;
            float dx = rawX - downRawX, dy = rawY - downRawY;
            if (Math.hypot(dx, dy) < 6 * MOVE_THRESHOLD) return false;
            // Velocidade máxima durante o arrasto (ignora a paradinha final do dedo).
            if (flingMaxV < 700f) return false;
            String gesture;
            if (Math.abs(dx) > Math.abs(dy)) gesture = dx < 0 ? "swipe_left" : "swipe_right";
            else gesture = dy < 0 ? "swipe_up" : "swipe_down";
            dragWidget.setTranslationX(0);
            dragWidget.setTranslationY(0);
            showTrash(false);
            hideGearIcon();
            dragWidget = null;
            isDragging = false;
            touchedWidget = null;
            moved = false;
            tapConsumed = false;
            invalidate();
            if (callback != null) callback.onAppShortcutGesture(pkg, gesture);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void finishDrag(View widget, float rawX, float rawY) {
        if (widget == null) return;
        Object tag = widget.getTag();
        boolean isFolder = isFolderTag(tag);
        boolean isShortcut = tag instanceof String && !isFolder;
        if (isInTrash(rawX, rawY)) {
            final View w = widget;
            if (isFolder) {
                final String fid = folderIdOf(tag);
                w.animate().translationY(-300f).alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        FrameLayout p = (FrameLayout) w.getParent();
                        if (p != null) p.removeView(w);
                        if (callback != null) callback.onFolderTrash(fid);
                    }).start();
            } else if (isShortcut) {
                final String pkg = (String) tag;
                w.animate().translationY(-300f).alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        FrameLayout p = (FrameLayout) w.getParent();
                        if (p != null) p.removeView(w);
                        if (callback != null) callback.onAppShortcutRemove(pkg);
                    }).start();
            } else {
                final int id = getWidgetId(widget);
                w.animate().translationY(-300f).alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        FrameLayout p = (FrameLayout) w.getParent();
                        if (p != null) p.removeView(w);
                        if (isValidWidget(id) && callback != null) callback.onWidgetRemoved(id);
                    }).start();
            }
        } else {
            View dropTarget = findDropTarget(rawX, rawY, widget);
            Object dropTag = dropTarget != null ? dropTarget.getTag() : null;
            if (dropTarget != null && (isFolderTag(dropTag)
                    || (dropTag instanceof String && tag instanceof String && !isFolder))) {
                widget.setTranslationX(0);
                widget.setTranslationY(0);
                if (isFolderTag(dropTag)) {
                    if (callback != null) callback.onWorkspaceDropOnFolder(
                            folderIdOf(dropTag), tag, rawX, rawY);
                } else {
                    if (callback != null) callback.onWorkspaceDropOnShortcut(
                            (String) dropTag, tag, rawX, rawY);
                }
                dragWidget = null;
                isDragging = false;
                showTrash(false);
                invalidate();
                return;
            }
            float tx = widget.getTranslationX();
            float ty = widget.getTranslationY();
            boolean movedSignificantly = Math.abs(tx) > MOVE_THRESHOLD || Math.abs(ty) > MOVE_THRESHOLD;
            if (movedSignificantly) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) widget.getLayoutParams();
                lp.leftMargin = (int) (saveOrigLeft + tx);
                lp.topMargin = (int) (saveOrigTop + ty);
                widget.setTranslationX(0);
                widget.setTranslationY(0);
                widget.setLayoutParams(lp);
                snap(widget);
                FrameLayout.LayoutParams slp = (FrameLayout.LayoutParams) widget.getLayoutParams();
                if (isShortcut) {
                    if (callback != null) callback.onAppShortcutMove((String) tag, currentPage, slp.leftMargin, slp.topMargin);
                } else {
                    int id = getWidgetId(widget);
                    if (isValidWidget(id) && callback != null) {
                        callback.onWidgetMoved(id, currentPage, slp.leftMargin, slp.topMargin);
                    }
                }
            } else {
                widget.setTranslationX(0);
                widget.setTranslationY(0);
                showWidgetMenu(widget);
            }
        }
        dragWidget = null;
        isDragging = false;
        showTrash(false);
        invalidate();
    }

    public int getCurrentPage() { return currentPage; }
    public int getPageCount() { return pages.size(); }

    public void addWidgetToCurrentPage(View view, int x, int y) {
        FrameLayout.LayoutParams lp;
        if (view instanceof AppWidgetHostView) {
            int w = ((AppWidgetHostView) view).getMinimumWidth();
            int h = ((AppWidgetHostView) view).getMinimumHeight();
            if (w <= 0) w = LayoutParams.WRAP_CONTENT;
            if (h <= 0) h = LayoutParams.WRAP_CONTENT;
            lp = new FrameLayout.LayoutParams(w, h);
        } else {
            lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        lp.leftMargin = x;
        lp.topMargin = y;

        view.setTranslationX(0);
        view.setTranslationY(0);

        if (!(view instanceof AppWidgetHostView)) {
            int pad = (int)(6 * density);
            view.setPadding(pad, pad, pad, pad);
        }
        FrameLayout wrapper = new FrameLayout(getContext());
        wrapper.setLayoutParams(lp);
        Object tag = view.getTag();
        if (tag != null) wrapper.setTag(tag);
        wrapper.addView(view, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        pages.get(currentPage).addView(wrapper);
        applyRounding(wrapper);
    }

    private View findDropTarget(float rawX, float rawY, View exclude) {
        if (currentPage < 0 || currentPage >= pages.size()) return null;
        pages.get(currentPage).getLocationOnScreen(tmpLoc);
        float lx = rawX - tmpLoc[0], ly = rawY - tmpLoc[1];
        FrameLayout page = pages.get(currentPage);
        for (int i = page.getChildCount() - 1; i >= 0; i--) {
            View c = page.getChildAt(i);
            if (c == exclude || c.getVisibility() != View.VISIBLE) continue;
            Object t = c.getTag();
            boolean droppable = isFolderTag(t)
                || (t instanceof String && !((String) t).isEmpty());
            if (!droppable) continue;
            float cl = c.getX(), ct = c.getY();
            float cr = cl + c.getWidth(), cb = ct + c.getHeight();
            if (lx >= cl && lx <= cr && ly >= ct && ly <= cb)
                return c;
        }
        return null;
    }

    private View findWidgetAt(float rawX, float rawY) {
        pages.get(currentPage).getLocationOnScreen(tmpLoc);
        float lx = rawX - tmpLoc[0], ly = rawY - tmpLoc[1];
        FrameLayout page = pages.get(currentPage);
        for (int i = page.getChildCount() - 1; i >= 0; i--) {
            View c = page.getChildAt(i);
            float cl = c.getX(), ct = c.getY();
            float cr = cl + c.getWidth(), cb = ct + c.getHeight();
            if (lx >= cl && lx <= cr && ly >= ct && ly <= cb)
                return c;
        }
        return null;
    }

    private int getWidgetId(View v) {
        if (v instanceof AppWidgetHostView) {
            return ((AppWidgetHostView) v).getAppWidgetId();
        }
        if (v instanceof FrameLayout) {
            FrameLayout fl = (FrameLayout) v;
            if (fl.getChildCount() > 0 && fl.getChildAt(0) instanceof AppWidgetHostView)
                return ((AppWidgetHostView) fl.getChildAt(0)).getAppWidgetId();
        }
        Object tag = v.getTag();
        if (tag instanceof Integer && (Integer) tag == CLOCK_WIDGET_ID) return CLOCK_WIDGET_ID;
        return -2;
    }

    private boolean isValidWidget(int id) {
        return id >= 0 || id == CLOCK_WIDGET_ID;
    }

    // --- TOUCH ---

    private void resetGlobalTouch() {
        multiTouch = false;
        pinchDone = false;
        rotDone = false;
        twoSwipeDone = false;
        rotAccum = 0;
        dtPending = false;
        dtDragMode = false;
        suppressTap = false;
        globalArmed = false;
    }

    private float fingerDist(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0;
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float fingerAngle(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0;
        return (float) Math.toDegrees(Math.atan2(
                e.getY(1) - e.getY(0), e.getX(1) - e.getX(0)));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (touchedWidget != null || isDragging || isResizing || multiTouch) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            View hit = findWidgetAt(e.getRawX(), e.getRawY());
            if (hit != null) {
                boolean isAppWidget = (hit instanceof FrameLayout)
                    && ((FrameLayout) hit).getChildCount() > 0
                    && ((FrameLayout) hit).getChildAt(0) instanceof AppWidgetHostView;
                if (isAppWidget) {
                    pendingAppWidget = hit;
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    moved = false;
                    waitingLongPress = true;
                    handler.postDelayed(longPressAction, LONG_PRESS_MS);
                } else {
                    return true;
                }
            }
        }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (waitingLongPress && pendingAppWidget != null && !moved) {
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD) {
                    moved = true;
                    stopLongPress();
                }
            }
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            stopLongPress();
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                moved = false;
                tapConsumed = false;
                resetGlobalTouch();

                // if already dragging, recalculate offset from current touch
                if (isDragging && dragWidget != null) {
                    setDragOffset(dragWidget);
                    return true;
                }

                // resize mode
                if (isResizing) {
                    int h = findHandleAt(e.getRawX(), e.getRawY());
                    if (h >= 0) {
                        resizeHandle = h;
                        resizeStartX = e.getRawX();
                        resizeStartY = e.getRawY();
                        return true;
                    }
                    exitResizeMode();
                    return true;
                }

                // normal touch
                View hit = findWidgetAt(e.getRawX(), e.getRawY());
                if (hit != null) {
                    touchedWidget = hit;
                    waitingLongPress = true;
                    handler.postDelayed(longPressAction, LONG_PRESS_MS);
                    if (pendingTap != null) { handler.removeCallbacks(pendingTap); pendingTap = null; }
                    iconDownTime = System.currentTimeMillis();
                    lastMoveValid = false;
                    flingMaxV = 0;
                    tapConsumed = false;
                    Object tag = hit.getTag();
                    if (tag instanceof String && !isFolderTag(tag) && callback != null) {
                        String dpkg = (String) tag;
                        long now = System.currentTimeMillis();
                        if (dpkg.equals(tapPkg) && now - tapTime < 350) {
                            tapPkg = null;
                            tapConsumed = true;
                            if (callback.hasAppShortcutDoubleTap(dpkg)) {
                                callback.onAppShortcutGesture(dpkg, "double_tap");
                            } else {
                                callback.onAppShortcutLaunch(dpkg);
                            }
                        }
                    }
                } else {
                    touchedWidget = null;
                    globalArmed = true;
                    long now = System.currentTimeMillis();
                    float slop = 24 * density;
                    if (now - lastTapUp < 350
                            && Math.abs(e.getRawX() - lastTapX) < slop * 2
                            && Math.abs(e.getRawY() - lastTapY) < slop * 2) {
                        dtPending = true;
                        dtDownTime = now;
                        dtStartX = e.getRawX();
                        dtStartY = e.getRawY();
                    }
                    waitingEmptyLongPress = true;
                    handler.postDelayed(emptyAreaLongPressAction, LONG_PRESS_MS);
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2 && globalArmed && !isDragging && !isResizing) {
                    multiTouch = true;
                    dtPending = false;
                    dtDragMode = false;
                    pinchDone = false;
                    rotDone = false;
                    twoSwipeDone = false;
                    rotAccum = 0;
                    twoX0 = e.getX(0);
                    twoY0 = e.getY(0);
                    twoX1 = e.getX(1);
                    twoY1 = e.getY(1);
                    twoStartDist = fingerDist(e);
                    twoStartAngle = fingerAngle(e);
                    twoMidStartX = (twoX0 + twoX1) / 2f;
                    twoMidStartY = (twoY0 + twoY1) / 2f;
                    stopLongPress();
                    if (indicator != null && indicator.isRunning()) indicator.cancel();
                    return true;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (multiTouch && e.getPointerCount() >= 2) {
                    handleTwoFingerMove(e);
                    return true;
                }
                if (isResizing && resizeHandle >= 0) {
                    applyResize(e.getRawX(), e.getRawY());
                    return true;
                }
                if (gearView != null && !moved) {
                    float gdx = e.getRawX() - downRawX;
                    float gdy = e.getRawY() - downRawY;
                    if (Math.abs(gdx) > MOVE_THRESHOLD || Math.abs(gdy) > MOVE_THRESHOLD) {
                        hideGearIcon();
                        moved = true;
                    }
                }
                if (waitingEmptyLongPress && !moved) {
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;
                    if (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD) {
                        stopLongPress();
                    }
                }
                if (dtPending && !dtDragMode) {
                    float dx = e.getRawX() - dtStartX;
                    float dy = e.getRawY() - dtStartY;
                    if (Math.abs(dx) > MOVE_THRESHOLD * 2 || Math.abs(dy) > MOVE_THRESHOLD * 2) {
                        dtDragMode = true;
                        stopLongPress();
                    }
                }
                if (touchedWidget != null && !multiTouch) {
                    long mnow = System.currentTimeMillis();
                    if (lastMoveValid) {
                        float md = (float) Math.hypot(
                            e.getRawX() - lastMoveX, e.getRawY() - lastMoveY);
                        long mdt = Math.max(1, mnow - lastMoveTime);
                        if (md > 15) {
                            float mv = md / mdt * 1000f;
                            if (mv > flingMaxV) flingMaxV = mv;
                        }
                    }
                    lastMoveX = e.getRawX();
                    lastMoveY = e.getRawY();
                    lastMoveTime = mnow;
                    lastMoveValid = true;
                }
                if (touchedWidget != null && !moved && !isDragging) {
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;
                    if (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD) {
                        moved = true;
                        stopLongPress();
                        tapConsumed = false;
                        if (indicator != null && indicator.isRunning()) indicator.cancel();
                        // start drag
                        dragWidget = touchedWidget;
                        setDragOffset(touchedWidget);
                        isDragging = true;
                        showTrash(true);
                        touchedWidget = null;
                    }
                }
                if (isDragging && dragWidget != null) {
                    applyDragTranslation(e.getRawX(), e.getRawY());
                    return true;
                }
                return touchedWidget != null;

            case MotionEvent.ACTION_POINTER_UP:
                if (multiTouch) {
                    multiTouch = false;
                    suppressTap = true;
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopLongPress();
                hideGearIcon();
                if (indicator != null && indicator.isRunning()) indicator.cancel();

                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    resetGlobalTouch();
                }
                if (multiTouch) {
                    multiTouch = false;
                    suppressTap = true;
                    return true;
                }
                if (suppressTap) {
                    suppressTap = false;
                    touchedWidget = null;
                    moved = false;
                    return true;
                }

                if (dtDragMode) {
                    dtDragMode = false;
                    dtPending = false;
                    float dx = e.getRawX() - dtStartX;
                    float dy = e.getRawY() - dtStartY;
                    float slop = 24 * density;
                    if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                        fireGlobalGesture(dy < 0 ? GestureConfig.DT_DRAG_UP : GestureConfig.DT_DRAG_DOWN);
                    }
                    touchedWidget = null;
                    moved = false;
                    return true;
                }
                if (dtPending) {
                    dtPending = false;
                    long dtDur = System.currentTimeMillis() - dtDownTime;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && dtDur < 350 && !moved) {
                        fireGlobalGesture(GestureConfig.DOUBLE_TAP);
                        lastTapUp = 0;
                        touchedWidget = null;
                        moved = false;
                        return true;
                    }
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    lastTapUp = System.currentTimeMillis();
                    lastTapX = e.getRawX();
                    lastTapY = e.getRawY();
                }

                if (isResizing && resizeHandle >= 0) {
                    resizeHandle = -1;
                    exitResizeMode();
                    return true;
                }

                if (isDragging && dragWidget != null) {
                    if (tryShortcutFling(e.getRawX(), e.getRawY())) return true;
                    finishDrag(dragWidget, e.getRawX(), e.getRawY());
                    return true;
                }

                // single tap on shortcut → launch (folder → open)
                if (touchedWidget != null && !moved) {
                    Object t = touchedWidget.getTag();
                    if (tapConsumed) {
                        tapConsumed = false;
                    } else if (isFolderTag(t)) {
                        if (callback != null) callback.onFolderTap(folderIdOf(t));
                    } else if (t instanceof String && callback != null) {
                        final String pp = (String) t;
                        if (callback.hasAppShortcutDoubleTap(pp)) {
                            tapPkg = pp;
                            tapTime = System.currentTimeMillis();
                            if (pendingTap != null) handler.removeCallbacks(pendingTap);
                            pendingTap = () -> {
                                pendingTap = null;
                                tapPkg = null;
                                if (callback != null) callback.onAppShortcutLaunch(pp);
                            };
                            handler.postDelayed(pendingTap, 320);
                        } else {
                            callback.onAppShortcutLaunch(pp);
                        }
                    }
                }

                touchedWidget = null;
                moved = false;
                return true;
        }
        return true;
    }

    private void snap(View v) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int m = (int) (MARGIN_DP * density);
        int w = v.getWidth(), h = v.getHeight();
        int cw = getWidth(), ch = getHeight();
        lp.leftMargin = Math.max(m, Math.min(lp.leftMargin, cw - w - m));
        lp.topMargin = Math.max(m, Math.min(lp.topMargin, ch - h - m));
        v.setLayoutParams(lp);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        float pageOffX = 0;
        if (currentPage >= 0 && currentPage < pages.size() && pagesContainer != null) {
            pageOffX = pages.get(currentPage).getLeft() + pagesContainer.getTranslationX();
        }
        if (isDragging && dragWidget != null) {
            float dx = dragWidget.getTranslationX(), dy = dragWidget.getTranslationY();
            tempRectF.set(dragWidget.getLeft() + pageOffX + dx - 4, dragWidget.getTop() + dy - 4,
                    dragWidget.getRight() + pageOffX + dx + 4, dragWidget.getBottom() + dy + 4);
            canvas.drawRoundRect(tempRectF, 8 * density, 8 * density, borderPaint);
        }
        if (isResizing && resizeWidget != null) {
            float hr = HANDLE_RADIUS_DP * density;
            float l = resizeWidget.getLeft() + pageOffX, t = resizeWidget.getTop();
            float ri = resizeWidget.getRight() + pageOffX, b = resizeWidget.getBottom();
            canvas.drawRect(l - 2, t - 2, ri + 2, b + 2, borderPaint);
            canvas.drawCircle(l, t, hr, handlePaint);
            canvas.drawCircle(l, t, hr, handleStroke);
            canvas.drawCircle(ri, t, hr, handlePaint);
            canvas.drawCircle(ri, t, hr, handleStroke);
            canvas.drawCircle(l, b, hr, handlePaint);
            canvas.drawCircle(l, b, hr, handleStroke);
            canvas.drawCircle(ri, b, hr, handlePaint);
            canvas.drawCircle(ri, b, hr, handleStroke);

            int wDp = (int)(resizeWidget.getWidth() / density);
            int hDp = (int)(resizeWidget.getHeight() / density);
            String label = wDp + "\u00d7" + hDp;
            float cx = (l + ri) / 2f;
            float cy = (t + b) / 2f;
            canvas.drawText(label, cx, cy + sizeLabelPaint.getTextSize() / 3f, sizeLabelPaint);
        }
    }
}

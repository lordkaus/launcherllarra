package com.launcher.larla.activity;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.AsyncListDiffer;

import com.launcher.larla.R;
import com.launcher.larla.model.AppInfo;
import com.launcher.larla.util.BitmapCache;
import com.launcher.larla.util.BitmapUtils;
import com.launcher.larla.util.AppCustom;
import com.launcher.larla.util.DockConfig;
import com.launcher.larla.util.FolderConfig;
import com.launcher.larla.util.GestureConfig;
import com.launcher.larla.util.SettingsManager;
import com.launcher.larla.util.ShakeDetector;
import com.launcher.larla.util.WorkspaceAppConfig;
import com.launcher.larla.receiver.PackageReceiver;
import com.launcher.larla.util.IconStyle;
import com.launcher.larla.view.LongPressIndicator;
import com.launcher.larla.view.ModernSheet;
import com.launcher.larla.widget.WidgetConfig;
import com.launcher.larla.widget.WidgetHost;
import com.launcher.larla.widget.WidgetPageLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherActivity extends Activity {

    private interface AppPickerCallback {
        void onAppSelected(AppInfo app);
    }

    private static final String TAG = "LauncherWara";
    private final Random random = new Random();

    private View appDrawer;
    private RecyclerView gridView;
    private SearchView searchView;
    private TextView emptyStateView;
    private LinearLayout clockWidgetContainer;
    private TextView clockTimeText;
    private TextView clockDateText;
    private LinearLayout dockContainer;
    private LinearLayout dockLayout;
    private LinearLayout pageIndicator;
    private LongPressIndicator indicator;
    private int currentPage = 0;
    private boolean animatingDock = false;

    private final Handler pageHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideDots = () -> {
        pageIndicator.animate().alpha(0f).setDuration(300)
            .withEndAction(() -> pageIndicator.setVisibility(View.GONE)).start();
    };

    private List<AppInfo> apps;
    private java.util.HashMap<String, AppInfo> appMap;
    private final ArrayList<String> lowerAppKeys = new ArrayList<>(256);
    private List<DrawerEntry> filtered;
    private AppRecyclerAdapter adapter;
    private boolean drawerOpen = false;

    private Handler clockHandler;
    private Runnable clockTick;
    private BitmapCache iconCache;
    private LruCache<String, Drawable> widgetPreviewCache;
    private SettingsManager settings;
    private DockConfig dockConfig;
    private WorkspaceAppConfig workspaceApps;
    private FolderConfig folders;
    private volatile Thread appsLoadThread;
    private ExecutorService iconLoadExecutor;
    private String lastDockSig = "";
    private PackageReceiver pkgReceiver;
    private boolean activityResumed;
    private GestureConfig gestures;
    private ShakeDetector shakeDetector;
    private final java.util.HashMap<String, android.content.pm.ResolveInfo> resolveCache = new java.util.HashMap<>(256);
    private float cachedDensity;
    private int cachedGridIconPx = 96;
    private String lastGridStyle = "";
    private int lastWsPx = -1;
    private String lastWsStyle = "";

    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private WidgetPageLayout widgetPages;
    private WidgetConfig widgetConfig;

    private static final int HOST_ID = 1024;
    private static final int REQUEST_PICK_WIDGET = 1;
    private static final int REQUEST_CONFIGURE_WIDGET = 2;

    private final SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat df = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"));
    private String filter = "";

    private float touchX, touchY;

    private final AccelerateInterpolator accelerateInterpolator = new AccelerateInterpolator();
    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private final GradientDrawable dotActive = createDotDrawable(0xFFFFFFFF);
    private final GradientDrawable dotInactive = createDotDrawable(0x44FFFFFF);

    private static GradientDrawable createDotDrawable(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        return gd;
    }

    private int pendingWidgetId = -1;
    private ComponentName pendingProvider;
    private float pendingTouchX, pendingTouchY;
    private boolean pickerDragForwarding = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        setContentView(R.layout.activity_launcher);

        iconCache = BitmapCache.getInstance();
        AppCustom.init(this);
        iconLoadExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "icon-decode");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        cachedDensity = getResources().getDisplayMetrics().density;
        widgetPreviewCache = new LruCache<String, Drawable>(512 * 1024) {
            @Override
            protected int sizeOf(String key, Drawable drawable) {
                if (drawable instanceof BitmapDrawable) {
                    Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
                    if (bm != null) return bm.getByteCount();
                }
                int w = drawable.getIntrinsicWidth();
                int h = drawable.getIntrinsicHeight();
                if (w > 0 && h > 0) return w * h * 4;
                return 8192;
            }
        };
        settings = new SettingsManager(this);
        gestures = new GestureConfig(this);
        shakeDetector = new ShakeDetector(this, () -> {
            if (activityResumed && !isFinishing() && !isDestroyed()) {
                performGlobalGesture(GestureConfig.SHAKE);
            }
        });
        dockConfig = new DockConfig(this);
        workspaceApps = new WorkspaceAppConfig(this);
        folders = new FolderConfig(this);

        appWidgetHost = new WidgetHost(this, HOST_ID);
        appWidgetManager = AppWidgetManager.getInstance(this);
        widgetConfig = new WidgetConfig(this);

        appDrawer = findViewById(R.id.app_drawer);
        // O sistema já desloca o conteúdo para baixo da status bar em APIs antigas;
        // no edge-to-edge forçado (API 35+) o conteúdo começa em y=0. Mede em runtime
        // (no content, sempre com layout) e completa só o que falta, sem duplicar o vão.
        final int sbId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        final int sbH = sbId > 0 ? getResources().getDimensionPixelSize(sbId) : 0;
        final int basePadL = appDrawer.getPaddingLeft();
        final int basePadR = appDrawer.getPaddingRight();
        final int basePadB = appDrawer.getPaddingBottom();
        final View contentView = findViewById(android.R.id.content);
        contentView.post(() -> {
            int[] loc = new int[2];
            contentView.getLocationOnScreen(loc);
            int extra = Math.max(0, sbH - loc[1]);
            appDrawer.setPadding(basePadL, extra, basePadR, basePadB);
        });
        gridView = findViewById(R.id.grid_view);
        emptyStateView = findViewById(R.id.empty_state);
        searchView = findViewById(R.id.search_view);
        dockContainer = findViewById(R.id.dock_container);
        dockLayout = findViewById(R.id.dock);
        pageIndicator = findViewById(R.id.page_indicator);
        indicator = findViewById(R.id.long_press_indicator);
        widgetPages = findViewById(R.id.widget_pages);
        widgetPages.setGestureConfig(gestures);
        widgetPages.setTrashView(findViewById(R.id.trash_zone));
        widgetPages.setLongPressIndicator(indicator);
        widgetPages.setWorkspacePageDots(findViewById(R.id.workspace_page_indicator));
        widgetPages.setCallback(new WidgetPageLayout.Callback() {
            @Override
            public void onWidgetRemoved(int appWidgetId) {
                if (appWidgetId == WidgetPageLayout.CLOCK_WIDGET_ID) {
                    widgetConfig.removeClock();
                    removeClockWidgetRefs();
                    return;
                }
                appWidgetHost.deleteAppWidgetId(appWidgetId);
                widgetConfig.removeWidget(appWidgetId);
            }
            @Override
            public void onWidgetMoved(int appWidgetId, int page, int x, int y) {
                widgetConfig.moveWidget(appWidgetId, page, x, y);
            }
            @Override
            public void onWidgetResized(int appWidgetId, int w, int h) {
                widgetConfig.resizeWidget(appWidgetId, w, h);
            }
            @Override
            public void onWidgetOpacityChanged(int appWidgetId, int opacity) {
                widgetConfig.setOpacity(appWidgetId, opacity);
            }
            @Override
            public void onPageChanged(int page, int total) {
                updatePageDots(page, total);
            }
            @Override
            public void onEmptyAreaLongPress(float rawX, float rawY) {
                touchX = rawX;
                touchY = rawY;
                int[] parentLoc = new int[2];
                ((View) indicator.getParent()).getLocationOnScreen(parentLoc);
                float px = rawX - parentLoc[0] - indicator.getWidth() / 2f;
                float py = rawY - parentLoc[1] - indicator.getHeight() / 2f;
                indicator.setX(px);
                indicator.setY(py);
                indicator.start(LauncherActivity.this::showHomeContextMenu);
            }
            @Override
            public void onWorkspaceContextMenu(int page, boolean canAddPage, boolean canRemovePage) {
                showWorkspaceContextMenu(page, canAddPage, canRemovePage);
            }
            @Override
            public void onSwipeUp() {
                performGlobalGesture(GestureConfig.SWIPE_UP);
            }
            @Override
            public void onGlobalGesture(String gestureKey) {
                performGlobalGesture(gestureKey);
            }
            @Override
            public void onAppShortcutLaunch(String pkg) {
                AppInfo app = findApp(pkg);
                if (app != null) launch(app);
            }
            @Override
            public void onAppShortcutGesture(String pkg, String gesture) {
                performIconAction(workspaceApps.getGesture(pkg, gesture));
            }
            @Override
            public boolean hasAppShortcutDoubleTap(String pkg) {
                String a = workspaceApps.getGesture(pkg, "double_tap");
                return a != null && !"none".equals(a);
            }
            @Override
            public void onAppShortcutRemove(String pkg) {
                workspaceApps.remove(pkg);
                showToast("Removido");
            }
            @Override
            public void onAppShortcutMove(String pkg, int page, int x, int y) {
                if (WidgetPageLayout.isFolderTag(pkg)) {
                    String fid = WidgetPageLayout.folderIdOf(pkg);
                    folders.move(fid, page, x, y);
                    return;
                }
                workspaceApps.move(pkg, page, x, y);
            }
            @Override
            public void onAppShortcutInfo(String pkg) {
                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + pkg)));
            }
            @Override
            public void onAppShortcutGestures(String pkg) {
                AppInfo app = findApp(pkg);
                if (app != null) showWorkspaceGestureDialog(app);
            }
            @Override
            public void onAppShortcutAddToFolder(String pkg) {
                AppInfo app = findApp(pkg);
                if (app != null) showAddToFolderSheet(app, FolderConfig.LOC_WORKSPACE);
            }
            @Override
            public void onFolderTap(String folderId) {
                openFolderWindow(folderId);
            }
            @Override
            public void onFolderMenu(View widget, String folderId) {
                showFolderMenuSheet(folderId);
            }
            @Override
            public void onFolderTrash(String folderId) {
                dissolveFolder(folderId, true);
            }
            @Override
            public void onWorkspaceDropOnFolder(String folderId, Object draggedTag, float rawX, float rawY) {
                mergeDropOnWorkspaceFolder(folderId, draggedTag);
            }
            @Override
            public void onWorkspaceDropOnShortcut(String targetPkg, Object draggedTag, float rawX, float rawY) {
                mergeDropOnWorkspaceShortcut(targetPkg, draggedTag);
            }
        });
        setupDropTargets();
        pkgReceiver = new PackageReceiver();
        PackageReceiver.setListener(this::handlePackageChange);
        android.content.IntentFilter pkgFilter = new android.content.IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(pkgReceiver, pkgFilter);
        loadWidgets();
        loadClockWidget();

        appDrawer.setVisibility(View.GONE);
        searchView.setIconified(true);
        searchView.clearFocus();
        filtered = new ArrayList<>(128);

        adapter = new AppRecyclerAdapter(app -> launch(app));
        gridView.setLayoutManager(new GridLayoutManager(this, settings.getGridColumns()));
        gridView.setAdapter(adapter);
        gridView.setHasFixedSize(true);
        gridView.setItemViewCacheSize(16);
        final int itemSpacing = (int) (4 * getResources().getDisplayMetrics().density);
        gridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = itemSpacing / 2;
                outRect.right = itemSpacing / 2;
                outRect.top = itemSpacing / 2;
                outRect.bottom = itemSpacing / 2;
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override
            public boolean onQueryTextChange(String s) {
                String n = s != null ? s.toLowerCase(Locale.getDefault()) : "";
                if (!n.equals(filter)) {
                    filter = n;
                    refilter();
                    adapter.submitList(new ArrayList<>(filtered));
                    // Show empty state when no results
                    boolean empty = filtered.isEmpty();
                    emptyStateView.setVisibility(empty ? View.VISIBLE : View.GONE);
                    gridView.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (empty && !n.isEmpty()) {
                        emptyStateView.setText(getString(R.string.empty_search) + " \u201c" + n + "\u201d");
                    } else {
                        emptyStateView.setText(R.string.empty_search);
                    }
                }
                return true;
            }
        });

        loadApps();
        setupClockHandler();
        setupGestures();
        applySettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        appWidgetHost.startListening();
        if (settings.checkClearCacheFlag()) {
            iconCache.evictAll();
            loadApps();
        }
        if (settings.checkPkgRefresh()) {
            doPackageRefresh();
        } else {
            refreshAppsIfChanged();
        }
        int oldPageCount = dockConfig.getPageCount();
        dockConfig.reload();
        if ((dockConfig.getPages().isEmpty() || dockConfig.getPageCount() != oldPageCount) && apps != null && !apps.isEmpty()) {
            List<String> allPkgs = new ArrayList<>();
            for (AppInfo a : apps) allPkgs.add(a.getPackageName());
            dockConfig.initPages(allPkgs);
        }
        if (currentPage >= dockConfig.getPages().size()) currentPage = 0;
        applySettings();        if (apps != null && !apps.isEmpty() && !drawerOpen) {
            String sig = dockSignature();
            if (!sig.equals(lastDockSig)) {
                setupDock();
                lastDockSig = sig;
            }
        }
        startClockHandler();
        if (shakeDetector != null
                && !GestureConfig.ACT_NONE.equals(gestures.getAction(GestureConfig.SHAKE))) {
            shakeDetector.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        if (shakeDetector != null) shakeDetector.stop();
        ModernSheet.dismissAll();
        hidePullHint();
        setDockHover(false);
        showDockBadge(false);
        showEdgeHints(false, false, false);
        resetDrawerHover();
        stopClockHandler();
        appWidgetHost.stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PackageReceiver.setListener(null);
        if (pkgReceiver != null) {
            try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
            pkgReceiver = null;
        }
        cleanupImageViews(dockLayout);
        cleanupGridView(gridView);
        iconCache.evictAll();
        widgetPreviewCache.evictAll();
        stopClockHandler();
        if (appsLoadThread != null) {
            appsLoadThread.interrupt();
            appsLoadThread = null;
        }
        pageHandler.removeCallbacksAndMessages(null);
        if (iconLoadExecutor != null) {
            iconLoadExecutor.shutdownNow();
            iconLoadExecutor = null;
        }
        // Null out references to help GC
        apps = null;
        appMap = null;
        filtered = null;
        adapter = null;
        settings = null;
        dockConfig = null;
        workspaceApps = null;
        widgetConfig = null;
        gestures = null;
        if (shakeDetector != null) {
            shakeDetector.stop();
            shakeDetector = null;
        }
        appWidgetHost = null;
        appWidgetManager = null;
        clockTimeText = null;
        clockDateText = null;
        clockWidgetContainer = null;
    }

    private void cleanupImageViews(ViewGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ImageView) {
                ((ImageView) child).setImageDrawable(null);
            } else if (child instanceof ViewGroup) {
                cleanupImageViews((ViewGroup) child);
            }
        }
    }

    private void cleanupGridView(RecyclerView rv) {
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child instanceof ViewGroup) {
                cleanupImageViews((ViewGroup) child);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_COMPLETE) {
            // Aggressive cleanup: clear all caches and force GC
            iconCache.evictAll();
            widgetPreviewCache.evictAll();
            Runtime.getRuntime().gc();
        } else if (level >= TRIM_MEMORY_MODERATE) {
            iconCache.evictAll();
            widgetPreviewCache.evictAll();
            rebindVisibleIcons();
        } else if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // Keep hot icons, drop half to avoid a re-decode storm on return
            try { iconCache.trimTo(iconCache.maxSize() / 2); } catch (Exception ignored) { iconCache.evictAll(); }
            widgetPreviewCache.evictAll();
            rebindVisibleIcons();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        iconCache.evictAll();
        widgetPreviewCache.evictAll();
        rebindVisibleIcons();
    }

    private void rebindVisibleIcons() {
        if (dockContainer.getVisibility() == View.VISIBLE) rebindDockIcons();
        if (gridView != null && adapter != null) adapter.notifyDataSetChanged();
    }

    private void setupClockHandler() {
        clockHandler = new Handler(Looper.getMainLooper());
        clockTick = () -> {
            if (clockTimeText != null && clockDateText != null) {
                Date now = new Date();
                clockTimeText.setText(tf.format(now));
                clockDateText.setText(df.format(now));
            }
            long delay = 60000 - (System.currentTimeMillis() % 60000);
            clockHandler.postDelayed(clockTick, delay);
        };
    }

    private void startClockHandler() { clockHandler.post(clockTick); }
    private void stopClockHandler() { clockHandler.removeCallbacks(clockTick); clockHandler.removeCallbacksAndMessages(null); }

    private void applySettings() {
        int cols = settings.getGridColumns();
        GridLayoutManager layoutManager = (GridLayoutManager) gridView.getLayoutManager();
        if (layoutManager != null && layoutManager.getSpanCount() != cols) layoutManager.setSpanCount(cols);
        int newPx = settings.getIconSizePx(this);
        String newStyle = settings.getStyleKey(SettingsManager.SURF_DRAWER);
        if (newPx != cachedGridIconPx || !newStyle.equals(lastGridStyle)
                || adapter.getItemCount() != filtered.size()) {
            cachedGridIconPx = newPx;
            lastGridStyle = newStyle;
            adapter.submitList(new ArrayList<>(filtered));
            // DiffUtil só enxerga nome/pacote: mudança visual exige rebind total
            adapter.notifyDataSetChanged();
        }
        int drawerColor = settings.getDrawerFinalColor();
        if (drawerOpen) {
            getWindow().setBackgroundDrawable(new ColorDrawable(drawerColor));
            appDrawer.setBackgroundColor(Color.TRANSPARENT);
        } else {
            appDrawer.setBackgroundColor(drawerColor);
        }
        int wsPx = settings.getWsIconPx(this);
        String wsStyle = settings.getStyleKey(SettingsManager.SURF_WS);
        boolean wsChanged = wsPx != lastWsPx || !wsStyle.equals(lastWsStyle);
        lastWsPx = wsPx;
        lastWsStyle = wsStyle;
        if (wsChanged && apps != null && !apps.isEmpty()) {
            clearWorkspaceShortcutViews();
            loadWorkspaceApps();
            loadWorkspaceFolders();
        }
        boolean showDock = settings.getShowDock() && !drawerOpen;
        dockContainer.setVisibility(showDock ? View.VISIBLE : View.GONE);
        if (showDock && dockContainer.getAlpha() < 1f && !animatingDock) dockContainer.setAlpha(1f);
        searchView.setVisibility(settings.getShowSearch() ? View.VISIBLE : View.GONE);
        widgetPages.setWidgetCornerRadius(settings.getWidgetRadius());
    }

    private void handlePackageChange(String action, String pkg, boolean replacing) {
        try {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action) && !replacing) {
                workspaceApps.remove(pkg);
                workspaceApps.purgeAppTarget(pkg);
                dockConfig.removePackageEverywhere(pkg);
                dockConfig.purgeAppTarget(pkg);
                folders.purgePackage(pkg);
                try { AppCustom.get().purgePackage(pkg); } catch (Exception ignored) { }
                refreshFolderViews();
                iconCache.removeAllFor(pkg);
            } else {
                iconCache.removeAllFor(pkg);
            }
            settings.setPkgRefresh();
        } catch (Exception e) {
            Log.w(TAG, "package sync failed", e);
        }
        if (activityResumed && !isFinishing() && !isDestroyed()) {
            runOnUiThread(this::doPackageRefresh);
        }
    }

    private void doPackageRefresh() {
        if (isFinishing() || isDestroyed()) return;
        dockConfig.reload();
        workspaceApps.reload();
        loadApps();
    }

    private void refreshAppsIfChanged() {
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
                String self = getPackageName();
                java.util.HashSet<String> pkgs = new java.util.HashSet<>(ris.size());
                for (ResolveInfo ri : ris) {
                    String p = ri.activityInfo.packageName;
                    if (!p.equals(self)) pkgs.add(p);
                }
                java.util.HashSet<String> cur = appMap != null
                        ? new java.util.HashSet<>(appMap.keySet()) : new java.util.HashSet<>();
                if (apps == null || !pkgs.equals(cur)) {
                    runOnUiThread(this::doPackageRefresh);
                }
            } catch (Exception ignored) { }
        }).start();
    }

    private void loadApps() {
        if (appsLoadThread != null) {
            appsLoadThread.interrupt();
        }
        appsLoadThread = new Thread(() -> {
            List<AppInfo> list = new ArrayList<>();
            java.util.HashMap<String, android.content.pm.ResolveInfo> rmap = new java.util.HashMap<>(256);
            String self = getPackageName();
            PackageManager pm = getPackageManager();
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
                for (ResolveInfo ri : ris) {
                    if (Thread.currentThread().isInterrupted()) return;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(self)) continue;
                    try {
                        String name = ri.loadLabel(pm).toString();
                        list.add(new AppInfo(pkg, name));
                        rmap.put(pkg, ri);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to load app: " + pkg, e);
                    }
                }
                Collections.sort(list, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            } catch (Exception e) {
                Log.e(TAG, "Failed to query installed apps", e);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                apps = list;
                resolveCache.clear();
                resolveCache.putAll(rmap);
                appMap = new java.util.HashMap<>(list.size());
                lowerAppKeys.clear();
                for (AppInfo a : list) {
                    appMap.put(a.getPackageName(), a);
                    lowerAppKeys.add((AppCustom.get().label(a.getPackageName(), a.getAppName()) + "\n" + a.getPackageName()).toLowerCase(Locale.getDefault()));
                }
                filtered.clear();
                refilter();
                adapter.submitList(new ArrayList<>(filtered));
                if (dockConfig.getPages().isEmpty()) {
                    List<String> allPkgs = new ArrayList<>();
                    for (AppInfo a : list) allPkgs.add(a.getPackageName());
                    dockConfig.initPages(allPkgs);
                }
                setupDock();
                clearWorkspaceShortcutViews();
                loadWorkspaceApps();
                loadWorkspaceFolders();
            });
        });
        appsLoadThread.start();
    }

    private void clearWorkspaceShortcutViews() {
        for (int p = 0; p < widgetPages.getPageCount(); p++) {
            FrameLayout page = widgetPages.getPageLayout(p);
            if (page == null) continue;
            for (int i = page.getChildCount() - 1; i >= 0; i--) {
                if (page.getChildAt(i).getTag() instanceof String) page.removeViewAt(i);
            }
        }
    }

    private int getDecodeSize() {
        int dock = (int) (dockConfig.getIconSizeDp() * cachedDensity);
        int s = Math.max(cachedGridIconPx > 0 ? cachedGridIconPx : 96, dock);
        return Math.min(Math.max(s, 48), 144);
    }

    private Bitmap decodeIcon(String pkg, int targetPx) {
        return decodeIcon(pkg, targetPx, SettingsManager.SURF_DRAWER);
    }

    private Bitmap decodeIcon(String pkg, int targetPx, String surface) {
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.ResolveInfo cached = resolveCache.get(pkg);
            Bitmap out = null;
            if (cached != null) out = BitmapUtils.loadIconScaled(cached, pm, targetPx);
            if (out == null) {
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setPackage(pkg);
                List<ResolveInfo> ris = pm.queryIntentActivities(intent, 0);
                if (ris.isEmpty()) return null;
                out = BitmapUtils.loadIconScaled(ris.get(0), pm, targetPx);
            }
            return IconStyle.prepareAuto(pkg, out, settings, surface);
        } catch (Exception e) {
            return null;
        }
    }

    /** Chave do cache: pacote + máscara da superfície (mesma máscara = mesma chave). */
    private String iconKey(String pkg, String surface) {
        return pkg + "|" + settings.maskKey(surface);
    }

    private void bindIcon(ImageView target, String pkg) {
        bindIcon(target, pkg, SettingsManager.SURF_DRAWER);
    }

    private void bindIcon(ImageView target, String pkg, String surface) {
        if (target == null || pkg == null) return;
        if (surface == null) surface = SettingsManager.SURF_DRAWER;
        final String surf = surface;
        final String key = iconKey(pkg, surf);
        Drawable cached = iconCache.get(key);
        if (cached != null) {
            target.setImageDrawable(cached);
            return;
        }
        // Show placeholder while loading
        if (target.getDrawable() == null) {
            target.setImageResource(R.drawable.ic_placeholder);
        }
        target.setTag(pkg);
        final int size = getDecodeSize();
        iconLoadExecutor.execute(() -> {
            if (Thread.currentThread().isInterrupted()) return;
            Bitmap decoded = decodeIcon(pkg, size, surf);
            if (decoded == null || isFinishing() || isDestroyed()) return;
            Drawable drawable = BitmapCache.bitmapToDrawable(getResources(), decoded);
            iconCache.put(key, drawable);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                int vw = target.getWidth() > 0 ? target.getWidth() : size;
                IconStyle.applyPlateAuto(target, settings, vw, pkg, surf);
                if (pkg.equals(target.getTag())) {
                    target.setImageDrawable(drawable);
                }
            });
        });
    }

    private void rebindDockIcons() {
        if (dockLayout == null) return;
        for (int i = 0; i < dockLayout.getChildCount(); i++) {
            ImageView iv = dockLayout.getChildAt(i).findViewById(R.id.dock_icon);
            if (iv != null && iv.getTag() instanceof String) {
                bindIcon(iv, (String) iv.getTag(), SettingsManager.SURF_DOCK);
            }
        }
    }

    private String dockSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(dockConfig.getIconCount()).append('|')
          .append(dockConfig.getIconSizeDp()).append('|')
          .append(dockConfig.getMarginDp()).append('|')
          .append(dockConfig.getPosition()).append('|')
          .append(dockConfig.getBgColor()).append('|')
          .append(dockConfig.getHideNames()).append('|')
          .append(dockConfig.getPageCount()).append('|')
          .append(settings.getStyleKey(SettingsManager.SURF_DOCK)).append('|')
          .append(currentPage).append('|');
        List<String> pagePkgs = currentPage >= 0 && currentPage < dockConfig.getPageCount()
                ? dockConfig.getPage(currentPage) : Collections.emptyList();
        for (String p : pagePkgs) sb.append(p).append(',');
        return sb.toString();
    }

    private void setupDock() {
        if (apps == null || apps.isEmpty()) return;
        String sig = dockSignature();
        if (sig.equals(lastDockSig) && dockLayout.getChildCount() > 0) return;
        cleanupImageViews(dockLayout);
        dockLayout.removeAllViews();

        float density = cachedDensity > 0 ? cachedDensity : getResources().getDisplayMetrics().density;
        int iconSizePx = (int) (dockConfig.getIconSizeDp() * density);
        boolean vertical = dockConfig.getPosition().equals("left") || dockConfig.getPosition().equals("right");
        String pos = dockConfig.getPosition();

        // page indicator
        updatePageDots(currentPage, dockConfig.getPageCount());

        // dock container layout params
        int pad = (int)(8 * density);
        int marginDp25 = (int)(24 * density);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                vertical ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                vertical ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT);
        if (vertical) cp.setMargins(0, marginDp25, 0, marginDp25);
        else cp.setMargins(marginDp25, 0, marginDp25, 0);
        switch (pos) {
            case "top": cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL; break;
            case "left": cp.gravity = android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL; break;
            case "right": cp.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL; break;
            default: cp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL; break;
        }
        dockContainer.setLayoutParams(cp);

        // calculate margin: icons have priority, shrink margin if needed
        List<String> pagePkgs = dockConfig.getPage(currentPage);
        int n = Math.min(pagePkgs.size(), dockConfig.getIconCount());
        float screenDim = vertical ? getResources().getDisplayMetrics().heightPixels : getResources().getDisplayMetrics().widthPixels;
        int desiredMarginPx = (int) (dockConfig.getMarginDp() * density / 2);
        int marginPx;
        if (n <= 1) {
            marginPx = Math.min(desiredMarginPx, (int) ((screenDim - iconSizePx) / 2f));
        } else {
            float maxMarginPx = (screenDim + (n - 1) * 4 * density - n * iconSizePx) / (2f * n);
            marginPx = (int) Math.max(0, Math.min(desiredMarginPx, maxMarginPx));
        }

        // dock items layout
        dockLayout.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        dockLayout.setGravity(android.view.Gravity.CENTER);
        if (vertical) dockLayout.setPadding(pad, marginPx, pad, marginPx);
        else dockLayout.setPadding(marginPx, pad, marginPx, pad);
        dockLayout.setBackgroundColor(dockConfig.getBgColor());

        final List<AppInfo> dockApps = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String pkg = pagePkgs.get(i);
            AppInfo app = findApp(pkg);
            if (app == null) continue;
            dockApps.add(app);

            View v = getLayoutInflater().inflate(R.layout.item_dock, dockLayout, false);
            ImageView iv = v.findViewById(R.id.dock_icon);
            TextView tv = v.findViewById(R.id.dock_name);
            bindIcon(iv, app.getPackageName(), SettingsManager.SURF_DOCK);
            tv.setText(AppCustom.get().label(app.getPackageName(), app.getAppName()));
            tv.setTextSize(settings.getLabelSize(SettingsManager.SURF_DOCK));

            ViewGroup.LayoutParams lp = iv.getLayoutParams();
            lp.width = iconSizePx;
            lp.height = iconSizePx;
            iv.setLayoutParams(lp);
            IconStyle.applyPlateAuto(iv, settings, iconSizePx, app.getPackageName(),
                SettingsManager.SURF_DOCK);

            if (dockConfig.getHideNames()) tv.setVisibility(View.GONE);

            if (i > 0) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                if (vertical) mlp.topMargin = marginPx * 2 - (int)(4 * density);
                else mlp.leftMargin = marginPx * 2 - (int)(4 * density);
            }

            dockLayout.addView(v);
        }

        // unified gesture handler for dock (items + empty area)
        final float[] startCoords = new float[2];
        final GestureDetector unifiedGd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private int hitItem = -1;

            @Override
            public boolean onDown(MotionEvent e) {
                startCoords[0] = e.getX();
                startCoords[1] = e.getY();
                hitItem = findDockItemAt(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (hitItem >= 0 && hitItem < dockApps.size()) launch(dockApps.get(hitItem));
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (hitItem >= 0 && hitItem < dockApps.size()) {
                    String a = dockConfig.getGesture(dockApps.get(hitItem).getPackageName(), "double_tap");
                    dockAction(a);
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                float threshold = 60;
                // horizontal fling anywhere → page navigation
                if (Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > threshold) {
                    String dir = vx < 0 ? "swipe_left" : "swipe_right";
                    if (hitItem >= 0 && hitItem < dockApps.size()) {
                        String ga = dockConfig.getGesture(
                            dockApps.get(hitItem).getPackageName(), dir);
                        if (ga != null && !"none".equals(ga)) {
                            performIconAction(ga);
                            return true;
                        }
                    }
                    if (vx < 0) changePage(currentPage + 1, 1);
                    else changePage(currentPage - 1, -1);
                    return true;
                }
                // vertical fling → only on item → swipe_up/swipe_down action
                if (Math.abs(vy) > Math.abs(vx) && Math.abs(vy) > threshold && hitItem >= 0 && hitItem < dockApps.size()) {
                    String g = vy < 0 ? "swipe_up" : "swipe_down";
                    String a = dockConfig.getGesture(dockApps.get(hitItem).getPackageName(), g);
                    dockAction(a);
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                int[] parentLoc = new int[2];
                ((View) indicator.getParent()).getLocationOnScreen(parentLoc);
                float px = e.getRawX() - parentLoc[0] - indicator.getWidth() / 2f;
                float py = e.getRawY() - parentLoc[1] - indicator.getHeight() / 2f;
                indicator.setX(px);
                indicator.setY(py);
                if (hitItem >= 0 && hitItem < dockApps.size()) {
                    final int idx = hitItem;
                    final AppInfo app = dockApps.get(idx);
                    indicator.setFillColor(0xFF00AAFF);
                    indicator.start(() -> showDockItemMenu(idx, app));
                }
            }
        });
        unifiedGd.setIsLongpressEnabled(true);
        dockLayout.setOnTouchListener((v, e) -> { unifiedGd.onTouchEvent(e); return true; });
        lastDockSig = sig;
    }

    private void changePage(int newPage, int direction) {
        if (newPage < 0 || newPage >= dockConfig.getPageCount()) return;
        if (animatingDock) return;

        int animMs = settings.getDockAnimPercent() * 10;
        if (direction == 0 || animMs == 0) {
            currentPage = newPage;
            setupDock();
            return;
        }

        animatingDock = true;
        boolean vertical = dockConfig.getPosition().equals("left") || dockConfig.getPosition().equals("right");
        float offset = vertical ? dockLayout.getHeight() : dockLayout.getWidth();
        if (offset <= 0) {
            currentPage = newPage;
            setupDock();
            animatingDock = false;
            return;
        }

        int count = dockLayout.getChildCount();
        if (count == 0) {
            currentPage = newPage;
            setupDock();
            animatingDock = false;
            return;
        }

        int[] remaining = {count};
        dockLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        for (int i = 0; i < count; i++) {
            View c = dockLayout.getChildAt(i);
            float tx = vertical ? 0 : -direction * offset;
            float ty = vertical ? -direction * offset : 0;
            c.animate().translationX(tx).translationY(ty).setDuration(animMs)
                .setInterpolator(accelerateInterpolator)
                .withEndAction(() -> {
                    remaining[0]--;
                    if (remaining[0] <= 0) {
                        for (int j = 0; j < dockLayout.getChildCount(); j++) {
                            dockLayout.getChildAt(j).setTranslationX(0);
                            dockLayout.getChildAt(j).setTranslationY(0);
                        }
                        currentPage = newPage;
                        setupDock();
                        int[] inRemaining = {dockLayout.getChildCount()};
                        if (inRemaining[0] <= 0) {
                            dockLayout.setLayerType(View.LAYER_TYPE_NONE, null);
                            animatingDock = false;
                            return;
                        }
                        float inOff = direction * offset;
                        for (int k = 0; k < dockLayout.getChildCount(); k++) {
                            View v = dockLayout.getChildAt(k);
                            v.setTranslationX(vertical ? 0 : inOff);
                            v.setTranslationY(vertical ? inOff : 0);
                            v.animate().translationX(0).translationY(0).setDuration(animMs)
                                .setInterpolator(decelerateInterpolator)
                                .withEndAction(() -> {
                                    inRemaining[0]--;
                                    if (inRemaining[0] <= 0) {
                                        dockLayout.setLayerType(View.LAYER_TYPE_NONE, null);
                                        animatingDock = false;
                                    }
                                }).start();
                        }
                    }
                }).start();
        }
    }

    private void showDockItemMenu(int index, AppInfo app) {
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        items.add(new ModernSheet.Item("\uD83D\uDDD1️", getString(R.string.remove)));
        items.add(new ModernSheet.Item("\uD83D\uDD04", getString(R.string.replace)));
        items.add(new ModernSheet.Item("\uD83D\uDCCC", getString(R.string.add_to_dock)));
        items.add(new ModernSheet.Item("\uD83D\uDC46", getString(R.string.gestures)));
        items.add(new ModernSheet.Item("ℹ️", getString(R.string.info)));
        ModernSheet.show(this, app.getAppName(), items, w -> {
                switch (w) {
                    case 0:
                        dockConfig.removeFromPage(currentPage, index);
                        showToast(getString(R.string.removed));
                        setupDock();
                        break;
                    case 1:
                        dockConfig.removeFromPage(currentPage, index);
                        showAppPicker(selected -> {
                            dockConfig.addToPage(currentPage, selected.getPackageName());
                            setupDock();
                            showToast(getString(R.string.added));
                        });
                        break;
                    case 2:
                        showAppPicker(selected -> {
                            addAppToDock(selected);
                        });
                        break;
                    case 3:
                        showDockGestureDialog(app);
                        break;
                    case 4:
                        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:" + app.getPackageName())));
                        break;
                }
            });
    }

    private void showDockGestureDialog(AppInfo app) {
        final String[] gestures = {"swipe_up", "swipe_down", "swipe_left", "swipe_right", "double_tap"};
        final String[] labels = {getString(R.string.gesture_swipe_up), getString(R.string.gesture_swipe_down), getString(R.string.gesture_swipe_left), getString(R.string.gesture_swipe_right), getString(R.string.gesture_double_tap)};
        final String[] icons = {"⬆️", "⬇️", "⬅️", "➡️", "\uD83D\uDC46"};
        final String[] actions = {"none", "open_drawer", "open_settings", "open_wallpaper", "open_widgets"};
        final String[] actionLabels = {getString(R.string.action_none), getString(R.string.action_open_drawer), getString(R.string.action_open_settings), getString(R.string.action_open_wallpaper), getString(R.string.action_open_widgets)};

        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < gestures.length; i++) {
            String cur = dockConfig.getGesture(app.getPackageName(), gestures[i]);
            items.add(new ModernSheet.Item(icons[i], labels[i] + ": " + iconActionLabel(cur)));
        }

        ModernSheet.show(this, app.getAppName(), items, w -> {
                showDockGestureActionPicker(app, gestures[w], labels[w], actions, actionLabels);
            });
    }

    private void showDockGestureActionPicker(AppInfo app, String gesture, String gestureLabel,
            final String[] actions, final String[] actionLabels) {
        String cur = dockConfig.getGesture(app.getPackageName(), gesture);
        int sel = 0;
        boolean curIsApp = com.launcher.larla.util.GestureConfig.isAppAction(cur);
        for (int i = 0; i < actions.length && !curIsApp; i++) {
            if (actions[i].equals(cur)) { sel = i; break; }
        }
        if (curIsApp) sel = actions.length;
        final String[] icons = {"\uD83D\uDEAB", "\uD83D\uDCC2", "⚙️", "\uD83D\uDDBC\uFE0F", "\uD83E\uDDE9"};
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < actions.length; i++) items.add(new ModernSheet.Item(icons[i], actionLabels[i]));
        items.add(new ModernSheet.Item("\uD83D\uDCF1", getString(R.string.action_open_app)));
        ModernSheet.show(this, app.getAppName() + " — " + gestureLabel, items, sel, w -> {
                if (w >= actions.length) {
                    showAppPicker(selected ->
                        dockConfig.setGesture(app.getPackageName(), gesture,
                            "app:" + selected.getPackageName()));
                } else {
                    dockConfig.setGesture(app.getPackageName(), gesture, actions[w]);
                }
            });
    }

    private void showWorkspaceGestureDialog(AppInfo app) {
        final String[] gestures = {"swipe_up", "swipe_down", "swipe_left", "swipe_right", "double_tap"};
        final String[] labels = {getString(R.string.gesture_swipe_up), getString(R.string.gesture_swipe_down), getString(R.string.gesture_swipe_left), getString(R.string.gesture_swipe_right), getString(R.string.gesture_double_tap)};
        final String[] icons = {"⬆️", "⬇️", "⬅️", "➡️", "\uD83D\uDC46"};
        final String[] actions = {"none", "open_drawer", "open_settings", "open_wallpaper", "open_widgets"};
        final String[] actionLabels = {getString(R.string.action_none), getString(R.string.action_open_drawer), getString(R.string.action_open_settings), getString(R.string.action_open_wallpaper), getString(R.string.action_open_widgets)};

        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < gestures.length; i++) {
            String cur = workspaceApps.getGesture(app.getPackageName(), gestures[i]);
            items.add(new ModernSheet.Item(icons[i], labels[i] + ": " + iconActionLabel(cur)));
        }

        ModernSheet.show(this, app.getAppName(), items, w -> {
                showWorkspaceGestureActionPicker(app, gestures[w], labels[w], actions, actionLabels);
            });
    }

    private void showWorkspaceGestureActionPicker(AppInfo app, String gesture, String gestureLabel,
            final String[] actions, final String[] actionLabels) {
        String cur = workspaceApps.getGesture(app.getPackageName(), gesture);
        int sel = 0;
        boolean curIsApp = com.launcher.larla.util.GestureConfig.isAppAction(cur);
        for (int i = 0; i < actions.length && !curIsApp; i++) {
            if (actions[i].equals(cur)) { sel = i; break; }
        }
        if (curIsApp) sel = actions.length;
        final String[] icons = {"\uD83D\uDEAB", "\uD83D\uDCC2", "⚙️", "\uD83D\uDDBC\uFE0F", "\uD83E\uDDE9"};
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < actions.length; i++) items.add(new ModernSheet.Item(icons[i], actionLabels[i]));
        items.add(new ModernSheet.Item("\uD83D\uDCF1", getString(R.string.action_open_app)));
        ModernSheet.show(this, app.getAppName() + " \u2014 " + gestureLabel, items, sel, w -> {
                if (w >= actions.length) {
                    showAppPicker(selected ->
                        workspaceApps.setGesture(app.getPackageName(), gesture,
                            "app:" + selected.getPackageName()));
                } else {
                    workspaceApps.setGesture(app.getPackageName(), gesture, actions[w]);
                }
            });
    }

    @SuppressWarnings("deprecation")
    private void startWorkspaceDrag(View itemView, AppInfo app) {
        if (app == null) return;
        dragPkg = app.getPackageName();
        ClipData data = ClipData.newPlainText("pkg", dragPkg);
        View.DragShadowBuilder shadow = new LiftedShadowBuilder(itemView);
        if (Build.VERSION.SDK_INT >= 24) itemView.startDragAndDrop(data, shadow, app, 0);
        else itemView.startDrag(data, shadow, app, 0);
    }

    private String dragPkg;
    private boolean dockHover;
    private boolean dragCloseBuzzed;
    private LinearLayout pullHint;
    private ImageView pullHintIcon;
    private TextView pullHintArrow;
    private android.animation.ObjectAnimator pullHintAnim;
    private TextView dropBadge;
    private TextView edgeLeftHint;
    private TextView edgeRightHint;
    private Drawable dockBgBackup;
    private final int[] tmpRootLoc = new int[2];
    private final int[] tmpPageLoc = new int[2];
    private final int[] tmpLoc = new int[2];

    /** Sombra do arrasto levemente ampliada (efeito "levantar", estilo Nova). */
    private static class LiftedShadowBuilder extends View.DragShadowBuilder {
        private Bitmap snapshot;
        private int sw, sh;

        LiftedShadowBuilder(View v) {
            super(v);
            try {
                Bitmap src = Bitmap.createBitmap(
                    Math.max(1, v.getWidth()), Math.max(1, v.getHeight()),
                    Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(src);
                v.draw(c);
                float scale = 1.1f;
                sw = Math.max(1, (int) (src.getWidth() * scale));
                sh = Math.max(1, (int) (src.getHeight() * scale));
                snapshot = Bitmap.createScaledBitmap(src, sw, sh, true);
                if (snapshot != src) src.recycle();
            } catch (Exception | OutOfMemoryError e) {
                snapshot = null;
            }
        }

        @Override
        public void onProvideShadowMetrics(Point outSize, Point outTouch) {
            View v = getView();
            if (snapshot != null) {
                outSize.set(sw, sh);
                outTouch.set(sw / 2, sh / 2);
            } else if (v != null) {
                outSize.set(v.getWidth(), v.getHeight());
                outTouch.set(v.getWidth() / 2, v.getHeight() / 2);
            }
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            if (snapshot != null && !snapshot.isRecycled()) {
                canvas.drawBitmap(snapshot, 0, 0, null);
            } else if (getView() != null) {
                super.onDrawShadow(canvas);
            }
        }
    }

    /** Dica "puxe para baixo" na base da gaveta enquanto segura o ícone. */
    private void showPullHint(AppInfo app) {
        try {
            if (!(dragRoot instanceof FrameLayout) || !drawerOpen || app == null) return;
            hidePullHint();
            float density = getResources().getDisplayMetrics().density;
            pullHint = new LinearLayout(this);
            pullHint.setOrientation(LinearLayout.HORIZONTAL);
            pullHint.setGravity(Gravity.CENTER);
            pullHint.setAlpha(0f);
            int pad = (int) (10 * density);
            pullHint.setPadding((int) (20 * density), (int) (8 * density),
                (int) (20 * density), (int) (10 * density) + systemBottomInset());
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColor(0xCC1A1A1A);
            bg.setStroke((int) (1.5f * density), 0x8800D4FF);
            pullHint.setBackground(bg);

            pullHintArrow = new TextView(this);
            pullHintArrow.setText("⬇");
            pullHintArrow.setTextColor(0xFF00D4FF);
            pullHintArrow.setTextSize(24);
            pullHintArrow.setGravity(Gravity.CENTER);
            pullHintArrow.setPadding(0, 0, (int) (10 * density), 0);
            pullHint.addView(pullHintArrow);

            pullHintIcon = new ImageView(this);
            int sz = (int) (40 * density);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
            ilp.rightMargin = (int) (10 * density);
            pullHintIcon.setLayoutParams(ilp);
            pullHintIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            pullHintIcon.setAlpha(0.9f);
            Drawable d = iconCache.get(app.getPackageName());
            if (d != null) pullHintIcon.setImageDrawable(d);
            else bindIcon(pullHintIcon, app.getPackageName());
            pullHint.addView(pullHintIcon);

            TextView lbl = new TextView(this);
            lbl.setText(getString(R.string.pull_to_workspace_hint));
            lbl.setTextColor(Color.WHITE);
            lbl.setTextSize(15);
            lbl.setGravity(Gravity.CENTER_VERTICAL);
            lbl.setSingleLine(true);
            pullHint.addView(lbl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout root = (FrameLayout) dragRoot;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM;
            pullHint.setLayoutParams(lp);
            root.addView(pullHint);
            pullHint.animate().alpha(1f).setDuration(150).start();
            pullHintAnim = android.animation.ObjectAnimator.ofFloat(
                pullHintArrow, "translationY", 0f, 12 * density, 0f);
            pullHintAnim.setDuration(650);
            pullHintAnim.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            pullHintAnim.start();
        } catch (Exception e) {
            Log.w(TAG, "pull hint failed", e);
        }
    }

    private void hidePullHint() {
        try {
            if (pullHintAnim != null) {
                pullHintAnim.cancel();
                pullHintAnim = null;
            }
            if (pullHint != null) {
                if (pullHint.getParent() instanceof ViewGroup) {
                    final LinearLayout gone = pullHint;
                    gone.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                        try {
                            if (gone.getParent() instanceof ViewGroup) {
                                ((ViewGroup) gone.getParent()).removeView(gone);
                            }
                        } catch (Exception ignored) { }
                    }).start();
                }
                pullHint = null;
                pullHintIcon = null;
                pullHintArrow = null;
            }
        } catch (Exception ignored) { }
    }

    private void buzz(View v) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            } else {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        } catch (Exception ignored) { }
    }

    private AppInfo dragAppOf(DragEvent e) {
        Object st = e.getLocalState();
        if (st instanceof AppInfo) return (AppInfo) st;
        if (e.getClipData() != null && e.getClipData().getItemCount() > 0) {
            CharSequence t = e.getClipData().getItemAt(0).getText();
            if (t != null) return findApp(t.toString());
        }
        return null;
    }

    private View dragRoot;

    private void setupDropTargets() {
        dragRoot = findViewById(android.R.id.content);
        // Soltar sobre a busca não deve colar o nome do pacote (paste nativo).
        try {
            View searchSrc = findSearchText(searchView);
            if (searchSrc != null) {
                searchSrc.setOnDragListener((sv, se) -> {
                    if (se.getAction() == DragEvent.ACTION_DROP) {
                        showDragHintOnce();
                        return true;
                    }
                    return se.getClipDescription() != null
                        && se.getClipDescription().hasMimeType(
                            ClipDescription.MIMETYPE_TEXT_PLAIN);
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "search drop guard failed", e);
        }
        dragRoot.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    dragCloseBuzzed = false;
                    resetDrawerHover();
                    setDockHover(false);
                    showDockBadge(false);
                    showEdgeHints(false, false, false);
                    return e.getClipDescription() != null
                        && e.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                case DragEvent.ACTION_DRAG_ENTERED:
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION: {
                    if (drawerOpen && v != null && v.getHeight() > 0) {
                        float edgePx = 140 * getResources().getDisplayMetrics().density;
                        if (e.getY() > v.getHeight() - edgePx) {
                            closeDrawer();
                            if (!dragCloseBuzzed && dragRoot != null) {
                                dragCloseBuzzed = true;
                                buzz(dragRoot);
                            }
                        }
                    } else if (!drawerOpen && v != null && v.getWidth() > 0) {
                        edgePageSwitch(e.getX(), v.getWidth());
                    }
                    if (drawerOpen) updateDrawerHover(e);
                    else resetDrawerHover();
                    updateDragFeedback(e);
                    return true;
                }
                case DragEvent.ACTION_DROP: {
                    AppInfo app = dragAppOf(e);
                    if (app == null || dragRoot == null) return false;
                    hidePullHint();
                    int[] rootLoc = new int[2];
                    dragRoot.getLocationOnScreen(rootLoc);
                    float sx = rootLoc[0] + e.getX(), sy = rootLoc[1] + e.getY();
                    resetDrawerHover();
                    setDockHover(false);
                    showDockBadge(false);
                    showEdgeHints(false, false, false);
                    if (drawerOpen) {
                        // Na gaveta: mesclar em pasta/célula sob o dedo.
                        DrawerEntry target = drawerEntryUnder(sx, sy);
                        if (target != null) {
                            return mergeDrawerDrop(target, app.getPackageName());
                        }
                        showDragHintOnce();
                        return false;
                    }
                    if (isViewAt(dockContainer, sx, sy)) {
                        addAppToDock(app);
                        buzz(dragRoot);
                        return true;
                    }
                    if (!isViewAt(widgetPages, sx, sy)) return false;
                    int[] pg = new int[2];
                    widgetPages.getLocationOnScreen(pg);
                    int wsPx = settings.getWsIconPx(this);
                    if (wsPx <= 0) wsPx = cachedGridIconPx > 0 ? cachedGridIconPx : 96;
                    placeWorkspaceShortcut(app,
                        Math.max(0, (int) (sx - pg[0]) - wsPx / 2),
                        Math.max(0, (int) (sy - pg[1]) - wsPx / 2));
                    buzz(dragRoot);
                    return true;
                }
                case DragEvent.ACTION_DRAG_ENDED:
                    // Soltar fora de um alvo = cancelar em silêncio (menu agora
                    // vem de segurar-parado, não do fim do arrasto).
                    hidePullHint();
                    resetDrawerHover();
                    setDockHover(false);
                    showDockBadge(false);
                    showEdgeHints(false, false, false);
                    dragPkg = null;
                    return true;
            }
            return false;
        });
    }

    /** Localiza o campo de texto interno do SearchView. */
    private View findSearchText(View root) {
        try {
            if (root instanceof android.widget.EditText) return root;
            if (root instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) root;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View found = findSearchText(g.getChildAt(i));
                    if (found != null) return found;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Troca de página nas bordas durante o arrasto (estilo Nova). */
    private long lastEdgePageSwitch;
    private void edgePageSwitch(float x, int width) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastEdgePageSwitch < 900) return;
            float density = getResources().getDisplayMetrics().density;
            int edge = (int) (28 * density);
            int cur = widgetPages.getCurrentPage();
            int count = widgetPages.getPageCount();
            if (x < edge && cur > 0) {
                lastEdgePageSwitch = now;
                widgetPages.goToPage(cur - 1);
                if (dragRoot != null) buzz(dragRoot);
            } else if (x > width - edge && cur < count - 1) {
                lastEdgePageSwitch = now;
                widgetPages.goToPage(cur + 1);
                if (dragRoot != null) buzz(dragRoot);
            }
        } catch (Exception e) {
            Log.w(TAG, "edge switch failed", e);
        }
    }

    /** Dica exibida uma única vez ao soltar sem alvo na gaveta. */
    private void showDragHintOnce() {
        try {
            android.content.SharedPreferences p =
                getSharedPreferences("polish", MODE_PRIVATE);
            if (p.getBoolean("drag_hint", false)) return;
            p.edit().putBoolean("drag_hint", true).apply();
            showToast(getString(R.string.drag_to_workspace_hint));
        } catch (Exception e) {
            Log.w(TAG, "hint failed", e);
        }
    }

    private View hoverCell;
    private Drawable hoverFgBackup;

    /** Célula da grade sob o ponto (hit-test manual, sem listener nas células). */
    private View drawerCellViewUnder(float screenX, float screenY) {
        try {
            if (gridView == null) return null;
            int[] loc = new int[2];
            gridView.getLocationOnScreen(loc);
            return gridView.findChildViewUnder(screenX - loc[0], screenY - loc[1]);
        } catch (Exception e) {
            return null;
        }
    }

    private DrawerEntry drawerEntryUnder(float screenX, float screenY) {
        try {
            View child = drawerCellViewUnder(screenX, screenY);
            if (child == null || gridView == null || adapter == null) return null;
            RecyclerView.ViewHolder vh = gridView.getChildViewHolder(child);
            int pos = vh.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return null;
            java.util.List<DrawerEntry> cur = adapter.differ.getCurrentList();
            if (pos < 0 || pos >= cur.size()) return null;
            return cur.get(pos);
        } catch (Exception e) {
            Log.w(TAG, "cell hit-test failed", e);
            return null;
        }
    }

    /** Realça a célula que pode receber o empilhamento em pasta. */
    private void updateDrawerHover(DragEvent e) {
        try {
            resetDrawerHover();
            if (!drawerOpen || dragRoot == null) return;
            AppInfo di = dragAppOf(e);
            String dp = di != null ? di.getPackageName() : null;
            if (dp == null) return;
            dragRoot.getLocationOnScreen(tmpRootLoc);
            float sx = tmpRootLoc[0] + e.getX(), sy = tmpRootLoc[1] + e.getY();
            DrawerEntry target = drawerEntryUnder(sx, sy);
            if (target == null || !canStackOn(target, dp)) return;
            View cell = drawerCellViewUnder(sx, sy);
            if (cell != null) {
                cell.setScaleX(1.08f);
                cell.setScaleY(1.08f);
                if (Build.VERSION.SDK_INT >= 23) {
                    hoverFgBackup = cell.getForeground();
                    float density = getResources().getDisplayMetrics().density;
                    GradientDrawable ring = new GradientDrawable();
                    ring.setShape(GradientDrawable.RECTANGLE);
                    ring.setCornerRadius(14 * density);
                    ring.setStroke((int) (3 * density), 0xFF00D4FF);
                    cell.setForeground(ring);
                }
                hoverCell = cell;
            }
        } catch (Exception ex) {
            Log.w(TAG, "drawer hover failed", ex);
        }
    }

    private void resetDrawerHover() {
        try {
            if (hoverCell != null) {
                hoverCell.setScaleX(1f);
                hoverCell.setScaleY(1f);
                if (Build.VERSION.SDK_INT >= 23) {
                    hoverCell.setForeground(hoverFgBackup);
                }
                hoverCell = null;
                hoverFgBackup = null;
            }
        } catch (Exception ignored) {
            hoverCell = null;
            hoverFgBackup = null;
        }
    }

    /** Brilho na dock enquanto o arrasto paira sobre ela. */
    private void setDockHover(boolean hover) {        try {
            if (hover == dockHover) return;
            dockHover = hover;
            if (dockContainer == null) return;
            if (hover) {
                dockBgBackup = dockContainer.getBackground();
                float density = getResources().getDisplayMetrics().density;
                GradientDrawable glow = new GradientDrawable();
                glow.setShape(GradientDrawable.RECTANGLE);
                glow.setCornerRadius(24 * density);
                glow.setColor(0x3300D4FF);
                glow.setStroke((int) (2 * density), 0x8800D4FF);
                dockContainer.setBackground(glow);
            } else if (dockBgBackup != null) {
                dockContainer.setBackground(dockBgBackup);
                dockBgBackup = null;
            } else {
                dockContainer.setBackground(null);
            }
        } catch (Exception e) {
            Log.w(TAG, "dock hover failed", e);
        }
    }

    /** Fantasma translúcido no ponto de soltura + hover da dock + setas de página. */
    private void updateDragFeedback(DragEvent e) {
        try {
            if (dragRoot == null || drawerOpen || dragPkg == null) {
                setDockHover(false);
                showDockBadge(false);
                showEdgeHints(false, false, false);
                return;
            }
            dragRoot.getLocationOnScreen(tmpRootLoc);
            float sx = tmpRootLoc[0] + e.getX(), sy = tmpRootLoc[1] + e.getY();
            boolean overDock = isViewAt(dockContainer, sx, sy);
            if (overDock && !dockHover) buzz(dragRoot);
            setDockHover(overDock);
            showDockBadge(overDock);
            updateEdgeHints(e.getX());
            if (overDock || !isViewAt(widgetPages, sx, sy)) {
                showDockBadge(false);
                showEdgeHints(false, false, false);
                return;
            }
            widgetPages.getLocationOnScreen(tmpPageLoc);
            int wsPx = settings.getWsIconPx(this);
            if (wsPx <= 0) wsPx = cachedGridIconPx > 0 ? cachedGridIconPx : 96;
            int gx = Math.max(0, (int) (sx - tmpPageLoc[0]) - wsPx / 2);
            int gy = Math.max(0, (int) (sy - tmpPageLoc[1]) - wsPx / 2);
            // Ghost no workspace removido (feedback visual apenas na gaveta/dock).
            // showDropGhost(dragPkg, wsPx, gx, gy);
        } catch (Exception ex) {
            Log.w(TAG, "drag feedback failed", ex);
        }
    }

    private int wsIconEstimate() {
        int wsPx = settings.getWsIconPx(this);
        if (wsPx <= 0) wsPx = cachedGridIconPx > 0 ? cachedGridIconPx : 96;
        return wsPx;
    }

    /** Selo "+" flutuante sobre a dock como alvo de soltura. */
    private void showDockBadge(boolean show) {
        try {
            if (!(dragRoot instanceof FrameLayout)) return;
            FrameLayout root = (FrameLayout) dragRoot;
            if (!show) {
                if (dropBadge != null && dropBadge.getParent() instanceof ViewGroup) {
                    ((ViewGroup) dropBadge.getParent()).removeView(dropBadge);
                }
                dropBadge = null;
                return;
            }
            if (dockContainer == null) return;
            if (dropBadge == null) {
                float density = getResources().getDisplayMetrics().density;
                dropBadge = new TextView(this);
                dropBadge.setText("+");
                dropBadge.setTextColor(Color.WHITE);
                dropBadge.setTextSize(24);
                dropBadge.setGravity(Gravity.CENTER);
                int sz = (int) (44 * density);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(0xCC00D4FF);
                dropBadge.setBackground(bg);
                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(sz, sz);
                dropBadge.setLayoutParams(blp);
            }
            dockContainer.getLocationOnScreen(tmpLoc);
            root.getLocationOnScreen(tmpRootLoc);
            FrameLayout.LayoutParams blp =
                (FrameLayout.LayoutParams) dropBadge.getLayoutParams();
            blp.leftMargin = tmpLoc[0] - tmpRootLoc[0]
                + dockContainer.getWidth() / 2 - blp.width / 2;
            blp.topMargin = tmpLoc[1] - tmpRootLoc[1]
                + dockContainer.getHeight() / 2 - blp.height / 2;
            dropBadge.setLayoutParams(blp);
            if (dropBadge.getParent() == null) root.addView(dropBadge);
        } catch (Exception e) {
            Log.w(TAG, "dock badge failed", e);
        }
    }

    /** Setas nas bordas indicam troca de página durante o arrasto. */
    private void updateEdgeHints(float x) {
        try {
            boolean multi = widgetPages.getPageCount() > 1 && dragRoot instanceof FrameLayout;
            if (!multi) {
                showEdgeHints(false, false, false);
                return;
            }
            float density = getResources().getDisplayMetrics().density;
            int edge = (int) (28 * density);
            int w = dragRoot.getWidth();
            int cur = widgetPages.getCurrentPage();
            int count = widgetPages.getPageCount();
            showEdgeHints(true, x < edge && cur > 0, x > w - edge && cur < count - 1);
        } catch (Exception e) {
            Log.w(TAG, "edge hints failed", e);
        }
    }

    private TextView makeEdgeArrow(String glyph, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextColor(0xFF00D4FF);
        tv.setTextSize(44);
        tv.setGravity(Gravity.CENTER);
        tv.setAlpha(0f);
        float density = getResources().getDisplayMetrics().density;
        int w = (int) (56 * density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            w, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = gravity;
        tv.setLayoutParams(lp);
        return tv;
    }

    private void showEdgeHints(boolean show, boolean leftActive, boolean rightActive) {
        try {
            if (!(dragRoot instanceof FrameLayout)) return;
            FrameLayout root = (FrameLayout) dragRoot;
            if (!show) {
                if (edgeLeftHint != null) edgeLeftHint.animate().alpha(0f).setDuration(150).start();
                if (edgeRightHint != null) edgeRightHint.animate().alpha(0f).setDuration(150).start();
                return;
            }
            if (edgeLeftHint == null) {
                edgeLeftHint = makeEdgeArrow("❮", Gravity.START | Gravity.CENTER_VERTICAL);
                root.addView(edgeLeftHint);
            }
            if (edgeRightHint == null) {
                edgeRightHint = makeEdgeArrow("❯", Gravity.END | Gravity.CENTER_VERTICAL);
                root.addView(edgeRightHint);
            }
            edgeLeftHint.animate().alpha(leftActive ? 0.95f : 0.3f).setDuration(120).start();
            edgeRightHint.animate().alpha(rightActive ? 0.95f : 0.3f).setDuration(120).start();
            edgeLeftHint.setScaleX(leftActive ? 1.25f : 1f);
            edgeLeftHint.setScaleY(leftActive ? 1.25f : 1f);
            edgeRightHint.setScaleX(rightActive ? 1.25f : 1f);
            edgeRightHint.setScaleY(rightActive ? 1.25f : 1f);
        } catch (Exception e) {
            Log.w(TAG, "edge hints failed", e);
        }
    }

    private boolean isViewAt(View v, float screenX, float screenY) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return screenX >= loc[0] && screenX <= loc[0] + v.getWidth()
            && screenY >= loc[1] && screenY <= loc[1] + v.getHeight();
    }

    private void addWorkspaceApp(AppInfo app) {
        placeWorkspaceShortcut(app, 50, 100);
    }

    private void placeWorkspaceShortcut(AppInfo app, int x, int y) {
        if (workspaceApps.has(app.getPackageName())) {
            showToast(getString(R.string.shortcut_exists));
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int iconSize = settings.getWsIconPx(this);

        LinearLayout shortcut = new LinearLayout(this);
        shortcut.setOrientation(LinearLayout.VERTICAL);
        shortcut.setGravity(Gravity.CENTER);
        int pad = (int)(6 * density);
        shortcut.setPadding(pad, pad, pad, pad);

        ImageView iv = new ImageView(this);
        bindIcon(iv, app.getPackageName(), SettingsManager.SURF_WS);
        int is = iconSize > 0 ? iconSize : (int)(48 * density);
        iv.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        IconStyle.applyPlateAuto(iv, settings, is, app.getPackageName(), SettingsManager.SURF_WS);
        shortcut.addView(iv);

        TextView tv = new TextView(this);
        tv.setText(AppCustom.get().label(app.getPackageName(), app.getAppName()));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(settings.getLabelSize(SettingsManager.SURF_WS));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(0, (int)(2 * density), 0, 0);
        shortcut.addView(tv);

        int ws = ViewGroup.LayoutParams.WRAP_CONTENT;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ws, ws);
        lp.leftMargin = Math.max(0, x);
        lp.topMargin = Math.max(0, y);

        shortcut.setTranslationX(0);
        shortcut.setTranslationY(0);
        shortcut.setPadding(pad, pad, pad, pad);

        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setLayoutParams(lp);
        wrapper.setTag(app.getPackageName());
        wrapper.addView(shortcut, new FrameLayout.LayoutParams(ws, ws));

        FrameLayout page = widgetPages.getCurrentPageLayout();
        int pageIdx = widgetPages.getCurrentPage();
        // Desvia de atalho existente (estima tamanho p/ checar sobreposição).
        int estW = (iconSize > 0 ? iconSize : (int)(48 * density)) + pad * 2;
        int estH = estW + (int)(20 * density);
        int[] free = findFreeSpot(page, lp.leftMargin, lp.topMargin, estW, estH);
        lp.leftMargin = free[0];
        lp.topMargin = free[1];
        wrapper.setLayoutParams(lp);
        page.addView(wrapper);
        widgetPages.applyRounding(wrapper);

        workspaceApps.add(app.getPackageName(), pageIdx, lp.leftMargin, lp.topMargin);
        snapShortcutInPage(wrapper, page, app.getPackageName(), pageIdx);
        showToast("Adicionado");
    }

    /** Trava o atalho dentro da página (cantos estilo Nova, sem ficar p/ fora).
     *  Respeita status bar, barra de navegação e o topo da dock. */
    private void snapShortcutInPage(final View wrapper, final FrameLayout page,
            final String pkg, final int pageIdx) {
        if (wrapper == null || page == null || pkg == null) return;
        wrapper.post(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;
                float density = getResources().getDisplayMetrics().density;
                int m = (int) (4 * density);
                int pw = page.getWidth(), ph = page.getHeight();
                int w = wrapper.getWidth(), h = wrapper.getHeight();
                if (pw <= 0 || ph <= 0 || w <= 0 || h <= 0) return;
                int topBound = m + systemTopInset();
                int bottomBound = ph - m - systemBottomInset();
                if (dockContainer != null && dockContainer.getVisibility() == View.VISIBLE
                        && settings.getShowDock() && !drawerOpen) {
                    int[] pl = new int[2], dl = new int[2];
                    page.getLocationOnScreen(pl);
                    dockContainer.getLocationOnScreen(dl);
                    bottomBound = Math.min(bottomBound, dl[1] - pl[1] - m);
                }
                FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) wrapper.getLayoutParams();
                int nx = Math.max(m, Math.min(lp.leftMargin, pw - w - m));
                int ny = Math.max(topBound, Math.min(lp.topMargin, bottomBound - h));
                if (ny < topBound) ny = topBound;
                if (nx != lp.leftMargin || ny != lp.topMargin) {
                    lp.leftMargin = nx;
                    lp.topMargin = ny;
                    wrapper.setLayoutParams(lp);
                    workspaceApps.move(pkg, pageIdx, nx, ny);
                }
            } catch (Exception e) {
                Log.w(TAG, "snap failed", e);
            }
        });
    }

    private int systemTopInset() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsets wi = widgetPages.getRootWindowInsets();
                if (wi != null) {
                    return wi.getInsets(
                        android.view.WindowInsets.Type.statusBars()).top;
                }
            } else {
                int res = getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
                if (res > 0) return getResources().getDimensionPixelSize(res);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    private int systemBottomInset() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsets wi = widgetPages.getRootWindowInsets();
                if (wi != null) {
                    return wi.getInsets(
                        android.view.WindowInsets.Type.navigationBars()).bottom;
                }
            }
        } catch (Exception ignored) { }
        return 0;
    }

    /** Procura ponto livre próximo (evita empilhar sobre atalho existente). */
    private int[] findFreeSpot(FrameLayout page, int wantX, int wantY, int w, int h) {
        try {
            float density = getResources().getDisplayMetrics().density;
            int m = (int) (4 * density);
            int pw = page.getWidth(), ph = page.getHeight();
            if (pw <= 0 || ph <= 0) return new int[]{ wantX, wantY };
            int maxX = Math.max(m, pw - w - m);
            int maxY = Math.max(m, ph - h - m);
            java.util.ArrayList<Rect> taken = new java.util.ArrayList<>();
            for (int i = 0; i < page.getChildCount(); i++) {
                View c = page.getChildAt(i);
                if (c.getVisibility() != View.VISIBLE || c.getWidth() <= 0) continue;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) c.getLayoutParams();
                taken.add(new Rect(lp.leftMargin, lp.topMargin,
                    lp.leftMargin + c.getWidth(), lp.topMargin + c.getHeight()));
            }
            int step = Math.max((int) (16 * density), 8);
            int maxR = Math.max(pw, ph);
            for (int r = 0; r <= maxR; r += step) {
                int[] dxs = r == 0 ? new int[]{ 0 }
                    : new int[]{ -r, 0, r };
                int[] dys = r == 0 ? new int[]{ 0 }
                    : new int[]{ -r, 0, r };
                for (int dx : dxs) {
                    for (int dy : dys) {
                        if (r > 0 && Math.abs(dx) != r && Math.abs(dy) != r) continue;
                        int cx = Math.max(m, Math.min(wantX + dx, maxX));
                        int cy = Math.max(m, Math.min(wantY + dy, maxY));
                        Rect cand = new Rect(cx, cy, cx + w, cy + h);
                        boolean free = true;
                        for (Rect t : taken) {
                            if (Rect.intersects(cand, t)) {
                                free = false;
                                break;
                            }
                        }
                        if (free) return new int[]{ cx, cy };
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "free spot failed", e);
        }
        return new int[]{ wantX, wantY };
    }

    private void loadWorkspaceApps() {
        int savedPage = widgetPages.getCurrentPage();
        for (WorkspaceAppConfig.ShortcutEntry e : workspaceApps.getShortcuts()) {
            AppInfo app = findApp(e.pkg);
            if (app == null) continue;
            float density = getResources().getDisplayMetrics().density;
            int iconSize = settings.getWsIconPx(this);

            LinearLayout shortcut = new LinearLayout(this);
            shortcut.setOrientation(LinearLayout.VERTICAL);
            shortcut.setGravity(Gravity.CENTER);
            int pad = (int)(6 * density);
            shortcut.setPadding(pad, pad, pad, pad);

            ImageView iv = new ImageView(this);
            bindIcon(iv, app.getPackageName(), SettingsManager.SURF_WS);
            int is = iconSize > 0 ? iconSize : (int)(48 * density);
            iv.setLayoutParams(new LinearLayout.LayoutParams(is, is));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            IconStyle.applyPlateAuto(iv, settings, is, app.getPackageName(), SettingsManager.SURF_WS);
            shortcut.addView(iv);

            TextView tv = new TextView(this);
            tv.setText(AppCustom.get().label(app.getPackageName(), app.getAppName()));
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(settings.getLabelSize(SettingsManager.SURF_WS));
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(0, (int)(2 * density), 0, 0);
            shortcut.addView(tv);

            int ws = ViewGroup.LayoutParams.WRAP_CONTENT;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ws, ws);
            lp.leftMargin = e.x;
            lp.topMargin = e.y;

            shortcut.setTranslationX(0);
            shortcut.setTranslationY(0);
            shortcut.setPadding(pad, pad, pad, pad);

            FrameLayout wrapper = new FrameLayout(this);
            wrapper.setLayoutParams(lp);
            wrapper.setTag(e.pkg);
            wrapper.addView(shortcut, new FrameLayout.LayoutParams(ws, ws));

            widgetPages.ensurePagesExist(e.page);
            widgetPages.goToPage(e.page);
            FrameLayout loadPage = widgetPages.getCurrentPageLayout();
            loadPage.addView(wrapper);
            widgetPages.applyRounding(wrapper);
            snapShortcutInPage(wrapper, loadPage, e.pkg, e.page);
        }
        widgetPages.goToPage(savedPage);
    }

    // ==================== PASTAS ====================

    private FrameLayout folderWindowOverlay;
    private String folderWindowId;
    private GridView folderWindowGrid;
    private BaseAdapter folderWindowAdapter;
    private final List<AppInfo> folderWindowMembers = new ArrayList<>();
    private TextView folderWindowTitle;

    private static String folderTag(String id) { return "folder:" + id; }

    private View findWorkspaceItemView(String tag) {
        for (int p = 0; p < widgetPages.getPageCount(); p++) {
            FrameLayout page = widgetPages.getPageLayout(p);
            if (page == null) continue;
            for (int i = 0; i < page.getChildCount(); i++) {
                View c = page.getChildAt(i);
                if (tag.equals(c.getTag())) return c;
            }
        }
        return null;
    }

    private int findWorkspaceItemPage(String tag) {
        for (int p = 0; p < widgetPages.getPageCount(); p++) {
            FrameLayout page = widgetPages.getPageLayout(p);
            if (page == null) continue;
            for (int i = 0; i < page.getChildCount(); i++) {
                if (tag.equals(page.getChildAt(i).getTag())) return p;
            }
        }
        return -1;
    }

    private void removeWorkspaceItemView(String tag) {
        View v = findWorkspaceItemView(tag);
        if (v != null && v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }

    private int bindFolderMinis(FolderConfig.Folder f, ImageView[] minis, int miniPx) {
        return bindFolderMinis(f, minis, miniPx, SettingsManager.SURF_DRAWER);
    }

    private int bindFolderMinis(FolderConfig.Folder f, ImageView[] minis, int miniPx, String surface) {
        int n = 0;
        for (String pkg : f.pkgs) {
            if (n >= minis.length) break;
            AppInfo app = findApp(pkg);
            if (app == null) continue;
            ImageView iv = minis[n++];
            iv.setVisibility(View.VISIBLE);
            bindIcon(iv, pkg, surface);
            IconStyle.applyPlateAuto(iv, settings, miniPx, pkg, surface);
        }
        for (int i = n; i < minis.length; i++) minis[i].setVisibility(View.INVISIBLE);
        return n;
    }

    private GradientDrawable folderCardBg(FolderConfig.Folder f, float density) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(f.bgColor);
        gd.setCornerRadius(f.cornerRadiusDp * density);
        return gd;
    }

    private View buildFolderView(FolderConfig.Folder f) {
        float density = getResources().getDisplayMetrics().density;
        int iconSize = settings.getWsIconPx(this);
        int is = iconSize > 0 ? iconSize : (int) (48 * density);
        int pad = (int) (6 * density);
        int inner = (int) (6 * density);
        int miniPx = Math.max((int) (20 * density), (is - inner * 2) / 2);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(pad, pad, pad, pad);

        FrameLayout card = new FrameLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        card.setBackground(folderCardBg(f, density));
        card.setPadding(inner, inner, inner, inner);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setWeightSum(2);
        rows.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView[] minis = new ImageView[4];
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(2);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            for (int c = 0; c < 2; c++) {
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                int m = (int) (2 * density);
                lp.setMargins(m, m, m, m);
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                minis[r * 2 + c] = iv;
                row.addView(iv);
            }
            rows.addView(row);
        }
        card.addView(rows);
        bindFolderMinis(f, minis, miniPx, SettingsManager.SURF_WS);
        col.addView(card);

        TextView tv = new TextView(this);
        tv.setText(f.name);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(settings.getLabelSize(SettingsManager.SURF_WS));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setPadding(0, (int) (2 * density), 0, 0);
        tv.setVisibility(f.showName ? View.VISIBLE : View.GONE);
        col.addView(tv);

        FrameLayout wrapper = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = f.x;
        lp.topMargin = f.y;
        wrapper.setLayoutParams(lp);
        wrapper.setTag(folderTag(f.id));
        wrapper.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        widgetPages.applyRounding(wrapper);
        return wrapper;
    }

    private void loadWorkspaceFolders() {
        if (apps == null) return;
        int savedPage = widgetPages.getCurrentPage();
        for (FolderConfig.Folder f : folders.byLocation(FolderConfig.LOC_WORKSPACE)) {
            widgetPages.ensurePagesExist(f.page);
            widgetPages.goToPage(f.page);
            widgetPages.getCurrentPageLayout().addView(buildFolderView(f));
        }
        widgetPages.goToPage(savedPage);
    }

    private void refreshFolderIcon(String id) {
        FolderConfig.Folder f = folders.find(id);
        if (f == null || !FolderConfig.LOC_WORKSPACE.equals(f.location)) return;
        removeWorkspaceItemView(folderTag(id));
        widgetPages.ensurePagesExist(f.page);
        widgetPages.getPageLayout(f.page).addView(buildFolderView(f));
        refreshFolderWindow();
    }

    private void refreshFolderViews() {
        clearWorkspaceShortcutViews();
        loadWorkspaceApps();
        loadWorkspaceFolders();
    }

    private void closeFolderWindow() {
        if (folderWindowOverlay != null) {
            ViewGroup parent = (ViewGroup) folderWindowOverlay.getParent();
            if (parent != null) parent.removeView(folderWindowOverlay);
            folderWindowOverlay = null;
        }
        folderWindowId = null;
        folderWindowGrid = null;
        folderWindowAdapter = null;
        folderWindowMembers.clear();
        folderWindowTitle = null;
    }

    private void openFolderWindow(String id) {
        FolderConfig.Folder f = folders.find(id);
        if (f == null) return;
        if (drawerOpen) closeDrawer();
        closeFolderWindow();
        folderWindowId = id;
        float density = getResources().getDisplayMetrics().density;

        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0xCC000000);
        overlay.setOnClickListener(v -> closeFolderWindow());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_sheet);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = (int) (24 * density);
        cardLp.setMargins(m, m, m, m);
        cardLp.gravity = Gravity.CENTER;
        card.setLayoutParams(cardLp);
        int pad = (int) (16 * density);
        card.setPadding(pad, pad, pad, pad);
        card.setOnClickListener(v -> { });
        overlay.addView(card);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(header);

        folderWindowTitle = new TextView(this);
        folderWindowTitle.setTextColor(Color.WHITE);
        folderWindowTitle.setTextSize(18);
        folderWindowTitle.setTypeface(null, Typeface.BOLD);
        folderWindowTitle.setSingleLine(true);
        folderWindowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        folderWindowTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(folderWindowTitle);

        TextView dots = new TextView(this);
        dots.setText("\u22EE");
        dots.setTextSize(20);
        dots.setTextColor(Color.WHITE);
        dots.setPadding(pad, (int) (4 * density), pad, (int) (4 * density));
        dots.setOnClickListener(v -> showFolderMenuSheet(id));
        header.addView(dots);

        folderWindowGrid = new GridView(this);
        folderWindowGrid.setNumColumns(Math.max(2, f.columns));
        folderWindowGrid.setVerticalSpacing((int) (4 * density));
        folderWindowGrid.setHorizontalSpacing((int) (4 * density));
        folderWindowGrid.setClipToPadding(false);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (300 * density));
        gridLp.topMargin = (int) (8 * density);
        folderWindowGrid.setLayoutParams(gridLp);
        card.addView(folderWindowGrid);

        folderWindowAdapter = new BaseAdapter() {
            @Override public int getCount() { return folderWindowMembers.size(); }
            @Override public Object getItem(int p) { return folderWindowMembers.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int p, View cv, ViewGroup par) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_app, par, false);
                AppInfo a = folderWindowMembers.get(p);
                ImageView piv = (ImageView) cv.findViewById(R.id.app_icon);
                bindIcon(piv, a.getPackageName(), SettingsManager.SURF_DRAWER);
                int pipW = piv.getLayoutParams() != null ? piv.getLayoutParams().width : 0;
                IconStyle.applyPlateAuto(piv, settings, pipW, a.getPackageName(), SettingsManager.SURF_DRAWER);
                ((TextView) cv.findViewById(R.id.app_name)).setText(AppCustom.get().label(a.getPackageName(), a.getAppName()));
                return cv;
            }
        };
        folderWindowGrid.setAdapter(folderWindowAdapter);
        folderWindowGrid.setOnItemClickListener((par, v, pos, lid) -> {
            if (pos >= 0 && pos < folderWindowMembers.size()) {
                launch(folderWindowMembers.get(pos));
            }
        });
        folderWindowGrid.setOnItemLongClickListener((par, v, pos, lid) -> {
            if (pos >= 0 && pos < folderWindowMembers.size()) {
                showFolderMemberMenu(id, folderWindowMembers.get(pos));
                return true;
            }
            return false;
        });

        android.widget.Button addBtn = new android.widget.Button(this);
        addBtn.setText("\u2795 " + getString(R.string.folder_add_apps));
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundResource(R.drawable.bg_button);
        addBtn.setAllCaps(false);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = (int) (8 * density);
        addBtn.setLayoutParams(addLp);
        addBtn.setOnClickListener(v -> showAppPicker(selected -> {
            if (folders.addApp(id, selected.getPackageName())) {
                refreshFolderIcon(id);
                refreshFolderWindow();
                showToast(getString(R.string.added));
            } else {
                showToast(getString(R.string.folder_already_has));
            }
        }));
        card.addView(addBtn);

        folderWindowOverlay = overlay;
        ((ViewGroup) findViewById(android.R.id.content)).addView(overlay);
        refreshFolderWindow();
    }

    private void refreshFolderWindow() {
        if (folderWindowOverlay == null || folderWindowId == null) return;
        FolderConfig.Folder f = folders.find(folderWindowId);
        if (f == null) { closeFolderWindow(); return; }
        folderWindowTitle.setText(f.name.isEmpty() ? getString(R.string.folder) : f.name);
        folderWindowMembers.clear();
        for (String pkg : f.pkgs) {
            AppInfo app = findApp(pkg);
            if (app != null) folderWindowMembers.add(app);
        }
        folderWindowGrid.setNumColumns(Math.max(2, f.columns));
        folderWindowAdapter.notifyDataSetChanged();
        if (folderWindowMembers.isEmpty()) {
            folderWindowGrid.setVisibility(View.GONE);
        } else {
            folderWindowGrid.setVisibility(View.VISIBLE);
        }
    }

    private void showFolderMemberMenu(String folderId, AppInfo app) {
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        items.add(new ModernSheet.Item("\uD83D\uDE80", app.getAppName()));
        items.add(new ModernSheet.Item("\uD83D\uDDD1️", getString(R.string.folder_remove_from)));
        items.add(new ModernSheet.Item("ℹ️", getString(R.string.info)));
        ModernSheet.show(this, app.getAppName(), items, w -> {
            switch (w) {
                case 0:
                    launch(app);
                    break;
                case 1:
                    folders.removeApp(folderId, app.getPackageName());
                    refreshFolderIcon(folderId);
                    refreshFolderWindow();
                    refreshDrawerList();
                    break;
                case 2:
                    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + app.getPackageName())));
                    break;
            }
        });
    }

    private void showFolderMenuSheet(String id) {
        FolderConfig.Folder f = folders.find(id);
        if (f == null) return;
        String title = f.name.isEmpty() ? getString(R.string.folder) : f.name;
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        items.add(new ModernSheet.Item("\uD83D\uDCC2", getString(R.string.folder)));
        items.add(new ModernSheet.Item("➕", getString(R.string.folder_add_apps)));
        items.add(new ModernSheet.Item("✏️", getString(R.string.rename)));
        items.add(new ModernSheet.Item("⚙️", getString(R.string.folder_settings)));
        items.add(new ModernSheet.Item("\uD83D\uDDD1️", getString(R.string.dissolve)));
        ModernSheet.show(this, title, items, w -> {
            switch (w) {
                case 0:
                    openFolderWindow(id);
                    break;
                case 1:
                    showAppPicker(selected -> {
                        if (folders.addApp(id, selected.getPackageName())) {
                            refreshFolderIcon(id);
                            refreshFolderWindow();
                            refreshDrawerList();
                            showToast(getString(R.string.added));
                        } else {
                            showToast(getString(R.string.folder_already_has));
                        }
                    });
                    break;
                case 2:
                    renameFolderFlow(id);
                    break;
                case 3:
                    showFolderSettings(id);
                    break;
                case 4:
                    dissolveFolder(id, true);
                    break;
            }
        });
    }

    private void renameFolderFlow(String id) {
        FolderConfig.Folder f = folders.find(id);
        if (f == null) return;
        android.widget.EditText edit = new android.widget.EditText(this);
        edit.setText(f.name);
        edit.setHint(getString(R.string.folder_name_hint));
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0x88FFFFFF);
        edit.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        edit.setPadding(pad, pad, pad, pad);
        edit.setSelection(edit.getText().length());
        ModernSheet.showCustom(this, getString(R.string.rename), edit,
            getString(R.string.ok), getString(R.string.cancel), () -> {
                folders.rename(id, edit.getText().toString().trim());
                refreshFolderIcon(id);
                refreshFolderWindow();
                refreshDrawerList();
            }, null);
    }

    private void showFolderSettings(String id) {
        FolderConfig.Folder f = folders.find(id);
        if (f == null) return;
        float density = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * density);
        root.setPadding(pad, (int) (4 * density), pad, (int) (4 * density));

        // icon size
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sizeLb = new TextView(this);
        sizeLb.setText(getString(R.string.folder_icon_size));
        sizeLb.setTextColor(Color.WHITE);
        sizeLb.setTextSize(15);
        sizeLb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sizeRow.addView(sizeLb);
        final TextView sizeVal = new TextView(this);
        sizeVal.setTextColor(0xFF00D4FF);
        sizeVal.setTextSize(14);
        sizeRow.addView(sizeVal);
        root.addView(sizeRow);
        android.widget.SeekBar sizeBar = new android.widget.SeekBar(this);
        sizeBar.setMax(100);
        sizeBar.setProgress(f.iconSizePct - 50);
        sizeVal.setText(f.iconSizePct + "%");
        sizeBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) return;
                f.iconSizePct = Math.max(50, Math.min(150, p + 50));
                sizeVal.setText(f.iconSizePct + "%");
                folders.saveNow();
                refreshFolderIcon(id);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) { }
        });
        root.addView(sizeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // show name
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
        TextView nameLb = new TextView(this);
        nameLb.setText(getString(R.string.folder_show_name));
        nameLb.setTextColor(Color.WHITE);
        nameLb.setTextSize(15);
        nameLb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameRow.addView(nameLb);
        android.widget.Switch nameSw = new android.widget.Switch(this);
        nameSw.setChecked(f.showName);
        nameSw.setOnCheckedChangeListener((b, v) -> {
            f.showName = v;
            folders.saveNow();
            refreshFolderIcon(id);
        });
        nameRow.addView(nameSw);
        root.addView(nameRow);

        // alpha/transparency slider
        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setOrientation(LinearLayout.HORIZONTAL);
        alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        alphaRow.setPadding(0, (int) (8 * density), 0, 0);
        TextView alphaLb = new TextView(this);
        alphaLb.setText("Transparência");
        alphaLb.setTextColor(Color.WHITE);
        alphaLb.setTextSize(15);
        alphaLb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        alphaRow.addView(alphaLb);
        final TextView alphaVal = new TextView(this);
        alphaVal.setTextColor(0xFF00D4FF);
        alphaVal.setTextSize(14);
        alphaRow.addView(alphaVal);
        root.addView(alphaRow);
        android.widget.SeekBar alphaBar = new android.widget.SeekBar(this);
        alphaBar.setMax(255);
        int curAlpha = (f.bgColor >> 24) & 0xFF;
        alphaBar.setProgress(curAlpha);
        alphaVal.setText(curAlpha == 0 ? "Total" : (curAlpha == 255 ? "Opaco" : curAlpha + ""));
        alphaBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) return;
                int newAlpha = p;
                int rgb = f.bgColor & 0x00FFFFFF;
                f.bgColor = (newAlpha << 24) | rgb;
                folders.saveNow();
                alphaVal.setText(newAlpha == 0 ? "Total" : (newAlpha == 255 ? "Opaco" : newAlpha + ""));
                refreshFolderIcon(id);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) { }
        });
        root.addView(alphaBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // colors
        TextView colorLb = new TextView(this);
        colorLb.setText(getString(R.string.folder_bg));
        colorLb.setTextColor(Color.WHITE);
        colorLb.setTextSize(15);
        colorLb.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
        root.addView(colorLb);
        final int[] colors = {0x33000000, 0x00000000, 0xAA000000, 0xAA1976D2, 0xAAD32F2F, 0xAA388E3C};
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        final android.widget.ImageView[] swatches = new android.widget.ImageView[colors.length];
        for (int i = 0; i < colors.length; i++) {
            final int c = colors[i];
            android.widget.ImageView sw = new android.widget.ImageView(this);
            int sz = (int) (36 * density);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(sz, sz);
            int mg = (int) (6 * density);
            slp.setMargins(mg, mg, mg, mg);
            sw.setLayoutParams(slp);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(c);
            gd.setStroke((int) (2 * density), c == f.bgColor ? 0xFF00D4FF : 0x44FFFFFF);
            sw.setBackground(gd);
            sw.setOnClickListener(v -> {
                f.bgColor = c;
                folders.saveNow();
                for (int j = 0; j < colors.length; j++) {
                    android.graphics.drawable.GradientDrawable g2 =
                            new android.graphics.drawable.GradientDrawable();
                    g2.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    g2.setColor(colors[j]);
                    g2.setStroke((int) (2 * density),
                            colors[j] == c ? 0xFF00D4FF : 0x44FFFFFF);
                    swatches[j].setBackground(g2);
                }
                refreshFolderIcon(id);
            });
            swatches[i] = sw;
            colorRow.addView(sw);
        }
        root.addView(colorRow);

        // radius stepper
        LinearLayout radRow = new LinearLayout(this);
        radRow.setOrientation(LinearLayout.HORIZONTAL);
        radRow.setGravity(Gravity.CENTER_VERTICAL);
        radRow.setPadding(0, (int) (8 * density), 0, 0);
        TextView radLb = new TextView(this);
        radLb.setText(getString(R.string.folder_radius));
        radLb.setTextColor(Color.WHITE);
        radLb.setTextSize(15);
        radLb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        radRow.addView(radLb);
        TextView radMinus = stepBtn("−");
        final TextView radVal = new TextView(this);
        radVal.setTextColor(0xFF00D4FF);
        radVal.setTextSize(14);
        radVal.setGravity(Gravity.CENTER);
        radVal.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (64 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView radPlus = stepBtn("+");
        radMinus.setOnClickListener(v -> {
            f.cornerRadiusDp = Math.max(0, f.cornerRadiusDp - 4);
            folders.saveNow();
            radVal.setText(f.cornerRadiusDp + "dp");
            refreshFolderIcon(id);
        });
        radPlus.setOnClickListener(v -> {
            f.cornerRadiusDp = Math.min(48, f.cornerRadiusDp + 4);
            folders.saveNow();
            radVal.setText(f.cornerRadiusDp + "dp");
            refreshFolderIcon(id);
        });
        radVal.setText(f.cornerRadiusDp + "dp");
        radRow.addView(radMinus);
        radRow.addView(radVal);
        radRow.addView(radPlus);
        root.addView(radRow);

        // columns stepper
        LinearLayout colRow = new LinearLayout(this);
        colRow.setOrientation(LinearLayout.HORIZONTAL);
        colRow.setGravity(Gravity.CENTER_VERTICAL);
        colRow.setPadding(0, (int) (8 * density), 0, 0);
        TextView colLb = new TextView(this);
        colLb.setText(getString(R.string.folder_columns));
        colLb.setTextColor(Color.WHITE);
        colLb.setTextSize(15);
        colLb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        colRow.addView(colLb);
        TextView colMinus = stepBtn("−");
        final TextView colVal = new TextView(this);
        colVal.setTextColor(0xFF00D4FF);
        colVal.setTextSize(14);
        colVal.setGravity(Gravity.CENTER);
        colVal.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (64 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView colPlus = stepBtn("+");
        colMinus.setOnClickListener(v -> {
            f.columns = Math.max(2, f.columns - 1);
            folders.saveNow();
            colVal.setText(String.valueOf(f.columns));
            refreshFolderWindow();
        });
        colPlus.setOnClickListener(v -> {
            f.columns = Math.min(6, f.columns + 1);
            folders.saveNow();
            colVal.setText(String.valueOf(f.columns));
            refreshFolderWindow();
        });
        colVal.setText(String.valueOf(f.columns));
        colRow.addView(colMinus);
        colRow.addView(colVal);
        colRow.addView(colPlus);
        root.addView(colRow);

        ModernSheet.showCustom(this, getString(R.string.folder_settings), root,
            getString(R.string.ok), null, null, null);
    }

    private TextView stepBtn(String s) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(R.drawable.bg_button);
        float density = getResources().getDisplayMetrics().density;
        b.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (40 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private void dissolveFolder(String id, boolean toast) {
        if (folders.find(id) == null) return;
        folders.dissolve(id);
        removeWorkspaceItemView(folderTag(id));
        if (id.equals(folderWindowId)) closeFolderWindow();
        refreshDrawerList();
        if (toast) showToast(getString(R.string.folder_dissolved));
    }

    private void createWorkspaceFolder(int page, int x, int y, List<String> pkgs, String name) {
        if (pkgs == null || pkgs.isEmpty()) return;
        FolderConfig.Folder f = folders.create(
                name != null ? name : getString(R.string.folder),
                FolderConfig.LOC_WORKSPACE, page, x, y, pkgs);
        widgetPages.ensurePagesExist(page);
        widgetPages.getPageLayout(page).addView(buildFolderView(f));
        showToast(getString(R.string.folder_created));
    }

    private List<String> draggedPkgs(Object draggedTag) {
        List<String> out = new ArrayList<>();
        if (draggedTag instanceof String) {
            String s = (String) draggedTag;
            if (WidgetPageLayout.isFolderTag(s)) {
                FolderConfig.Folder f = folders.find(WidgetPageLayout.folderIdOf(s));
                if (f != null) out.addAll(f.pkgs);
            } else if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private void mergeDropOnWorkspaceFolder(String folderId, Object draggedTag) {
        FolderConfig.Folder target = folders.find(folderId);
        if (target == null) return;
        List<String> incoming = draggedPkgs(draggedTag);
        if (draggedTag instanceof String
                && WidgetPageLayout.folderIdOf(draggedTag) != null
                && WidgetPageLayout.folderIdOf(draggedTag).equals(folderId)) {
            return;
        }
        boolean added = false;
        for (String pkg : incoming) {
            if (findApp(pkg) == null) continue;
            if (folders.addApp(folderId, pkg)) added = true;
        }
        if (draggedTag instanceof String) {
            String s = (String) draggedTag;
            if (WidgetPageLayout.isFolderTag(s)) {
                dissolveFolder(WidgetPageLayout.folderIdOf(s), false);
            } else if (workspaceApps.has(s)) {
                workspaceApps.remove(s);
                removeWorkspaceItemView(s);
            }
        }
        refreshFolderIcon(folderId);
        refreshFolderWindow();
        if (added) showToast(getString(R.string.added));
    }

    private void mergeDropOnWorkspaceShortcut(String targetPkg, Object draggedTag) {
        if (!(draggedTag instanceof String)) return;
        String ds = (String) draggedTag;
        if (ds.equals(targetPkg)) return;
        View targetView = findWorkspaceItemView(targetPkg);
        int page = widgetPages.getCurrentPage();
        int x = 50, y = 100;
        if (targetView != null && targetView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) targetView.getLayoutParams();
            x = lp.leftMargin;
            y = lp.topMargin;
            int tp = findWorkspaceItemPage(targetPkg);
            if (tp >= 0) page = tp;
        }
        List<String> members = new ArrayList<>();
        members.add(targetPkg);
        members.addAll(draggedPkgs(ds));
        if (WidgetPageLayout.isFolderTag(ds)) {
            dissolveFolder(WidgetPageLayout.folderIdOf(ds), false);
        } else if (workspaceApps.has(ds)) {
            workspaceApps.remove(ds);
            removeWorkspaceItemView(ds);
        }
        workspaceApps.remove(targetPkg);
        removeWorkspaceItemView(targetPkg);
        createWorkspaceFolder(page, x, y, members, null);
    }

    private void showAddToFolderSheet(AppInfo app, String defaultLoc) {
        if (app == null) return;
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        final List<FolderConfig.Folder> all = new ArrayList<>(folders.getFolders());
        for (FolderConfig.Folder f : all) {
            String label = (f.name.isEmpty() ? getString(R.string.folder) : f.name)
                    + " (" + f.pkgs.size() + ")";
            items.add(new ModernSheet.Item("\uD83D\uDCC1", label));
        }
        items.add(new ModernSheet.Item("➕", getString(R.string.new_folder)));
        ModernSheet.show(this, getString(R.string.add_to_folder), items, w -> {
            if (w < 0 || w >= items.size()) return;
            if (w < all.size()) {
                FolderConfig.Folder f = all.get(w);
                if (folders.addApp(f.id, app.getPackageName())) {
                    refreshFolderIcon(f.id);
                    refreshFolderWindow();
                    refreshDrawerList();
                    showToast(getString(R.string.added));
                } else {
                    showToast(getString(R.string.folder_already_has));
                }
                return;
            }
            android.widget.EditText edit = new android.widget.EditText(this);
            edit.setText(getString(R.string.folder));
            edit.setHint(getString(R.string.folder_name_hint));
            edit.setTextColor(Color.WHITE);
            edit.setHintTextColor(0x88FFFFFF);
            edit.setSingleLine(true);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            edit.setPadding(pad, pad, pad, pad);
            edit.setSelection(edit.getText().length());
            ModernSheet.showCustom(this, getString(R.string.new_folder), edit,
                getString(R.string.ok), getString(R.string.cancel), () -> {
                    String name = edit.getText().toString().trim();
                    if (name.isEmpty()) name = getString(R.string.folder);
                    List<String> one = new ArrayList<>();
                    one.add(app.getPackageName());
                    if (FolderConfig.LOC_WORKSPACE.equals(defaultLoc)) {
                        createWorkspaceFolder(widgetPages.getCurrentPage(), 50, 100, one, name);
                    } else {
                        folders.create(name, FolderConfig.LOC_DRAWER, 0, 0, 0, one);
                        refreshDrawerList();
                        showToast(getString(R.string.folder_created));
                    }
                }, null);
        });
    }

    private void refreshDrawerList() {
        refilter();
        if (adapter != null) adapter.submitList(new ArrayList<>(filtered));
    }

    private void bindFolderCell(AppRecyclerAdapter.ViewHolder holder, FolderConfig.Folder f) {
        float density = getResources().getDisplayMetrics().density;
        int px = cachedGridIconPx > 0 ? cachedGridIconPx : 96;
        ViewGroup.LayoutParams clp = holder.folderCard.getLayoutParams();
        if (clp.width != px) {
            clp.width = px;
            clp.height = px;
            holder.folderCard.setLayoutParams(clp);
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(f.bgColor);
        gd.setCornerRadius(f.cornerRadiusDp * density);
        holder.folderCard.setBackground(gd);
        int miniPx = Math.max((int) (20 * density), (px - (int) (12 * density)) / 2);
        bindFolderMinis(f, holder.minis, miniPx, SettingsManager.SURF_DRAWER);
        String name = f.name.isEmpty() ? getString(R.string.folder) : f.name;
        holder.folderName.setText(name);
        holder.folderName.setTextSize(settings.getLabelSize(SettingsManager.SURF_DRAWER));
        holder.folderName.setVisibility(f.showName ? View.VISIBLE : View.GONE);
    }

    private String draggedPkg(DragEvent e) {
        if (e.getClipData() != null && e.getClipData().getItemCount() > 0) {
            CharSequence t = e.getClipData().getItemAt(0).getText();
            if (t != null) return t.toString();
        }
        return null;
    }

    private boolean canStackOn(DrawerEntry target, String draggedPkg) {
        if (target == null || draggedPkg == null) return false;
        if (target.isFolder()) {
            return !target.folder.pkgs.contains(draggedPkg);
        }
        return !draggedPkg.equals(target.app.getPackageName());
    }

    private boolean mergeDrawerDrop(DrawerEntry target, String draggedPkg) {
        if (target == null || draggedPkg == null) return false;
        AppInfo app = findApp(draggedPkg);
        if (app == null) return false;
        if (target.isFolder()) {
            if (folders.addApp(target.folder.id, draggedPkg)) {
                refreshDrawerList();
                refreshFolderWindow();
                showToast(getString(R.string.added));
            } else {
                showToast(getString(R.string.folder_already_has));
            }
            return true;
        }
        if (draggedPkg.equals(target.app.getPackageName())) return false;
        List<String> members = new ArrayList<>();
        members.add(target.app.getPackageName());
        members.add(draggedPkg);
        folders.create(getString(R.string.folder), FolderConfig.LOC_DRAWER, 0, 0, 0, members);
        refreshDrawerList();
        showToast(getString(R.string.folder_created));
        return true;
    }

    private void showDrawerFolderMenu(String id) {
        FolderConfig.Folder f = folders.find(id);
        if (f == null) return;
        String title = f.name.isEmpty() ? getString(R.string.folder) : f.name;
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        items.add(new ModernSheet.Item("\uD83D\uDCC2", getString(R.string.folder)));
        items.add(new ModernSheet.Item("✏️", getString(R.string.rename)));
        items.add(new ModernSheet.Item("➕", getString(R.string.folder_add_apps)));
        items.add(new ModernSheet.Item("⚙️", getString(R.string.folder_settings)));
        items.add(new ModernSheet.Item("\uD83D\uDDD1️", getString(R.string.dissolve)));
        ModernSheet.show(this, title, items, w -> {
            switch (w) {
                case 0:
                    openFolderWindow(id);
                    break;
                case 1:
                    renameFolderFlow(id);
                    break;
                case 2:
                    showAppPicker(selected -> {
                        if (folders.addApp(id, selected.getPackageName())) {
                            refreshDrawerList();
                            refreshFolderWindow();
                            showToast(getString(R.string.added));
                        } else {
                            showToast(getString(R.string.folder_already_has));
                        }
                    });
                    break;
                case 3:
                    showFolderSettings(id);
                    break;
                case 4:
                    dissolveFolder(id, true);
                    break;
            }
        });
    }

    private void addAppToDock(AppInfo app) {
        List<String> pagePkgs = dockConfig.getPage(currentPage);
        if (pagePkgs.contains(app.getPackageName())) {
            showToast(getString(R.string.app_on_dock));
            return;
        }
        if (pagePkgs.size() >= dockConfig.getIconCount()) {
            ModernSheet.showConfirm(this, getString(R.string.dock_page_full),
                getString(R.string.dock_full), getString(R.string.yes),
                getString(R.string.cancel), () -> {
                    dockConfig.removeFromPage(currentPage, pagePkgs.size() - 1);
                    dockConfig.addToPage(currentPage, app.getPackageName());
                    setupDock();
                    showToast(getString(R.string.added));
                }, null);
            return;
        }
        dockConfig.addToPage(currentPage, app.getPackageName());
        setupDock();
        showToast(getString(R.string.added));
    }

    private void showAppPicker(AppPickerCallback callback) {
        final float density = getResources().getDisplayMetrics().density;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0xFF121212);

        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * density)));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int)(12 * density), 0, (int)(12 * density), 0);
        header.setBackgroundColor(0xFF1A1A1A);

        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(20);
        backBtn.setTextColor(0xFF00D4FF);
        backBtn.setOnClickListener(v -> ((ViewGroup) overlay.getParent()).removeView(overlay));
        header.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(getString(R.string.choose_app));
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding((int)(12 * density), 0, 0, 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        overlay.addView(header);

        SearchView pickerSearch = new SearchView(this);
        pickerSearch.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(40 * density)));
        pickerSearch.setQueryHint(getString(R.string.search_hint));
        pickerSearch.setIconifiedByDefault(false);
        pickerSearch.setBackgroundColor(0xFF222222);        final java.util.ArrayList<AppInfo> pickerData = new java.util.ArrayList<>();
        fillPickerData(pickerData);
        BaseAdapter pickerAdapter = new BaseAdapter() {
            @Override public int getCount() { return pickerData.size(); }
            @Override public Object getItem(int p) { return pickerData.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int p, View cv, ViewGroup par) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_app, par, false);
                AppInfo a = pickerData.get(p);
                ImageView piv = (ImageView) cv.findViewById(R.id.app_icon);
                bindIcon(piv, a.getPackageName(), SettingsManager.SURF_DRAWER);
                int pipW = piv.getLayoutParams() != null ? piv.getLayoutParams().width : 0;
                IconStyle.applyPlateAuto(piv, settings, pipW, a.getPackageName(), SettingsManager.SURF_DRAWER);
                ((TextView) cv.findViewById(R.id.app_name)).setText(AppCustom.get().label(a.getPackageName(), a.getAppName()));
                return cv;
            }
        };

        GridView pickerGrid = new GridView(this);
        pickerGrid.setAdapter(pickerAdapter);
        pickerGrid.setNumColumns(4);
        pickerGrid.setVerticalSpacing((int)(4 * density));
        pickerGrid.setHorizontalSpacing((int)(4 * density));
        pickerGrid.setPadding((int)(8 * density), 0, (int)(8 * density), (int)(8 * density));
        pickerGrid.setClipToPadding(false);
        pickerGrid.setOnItemClickListener((par, v, pos, id) -> {
            if (pos >= 0 && pos < pickerData.size()) {
                callback.onAppSelected(pickerData.get(pos));
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
        });

        pickerSearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) {
                pickerData.clear();
                if (q.isEmpty()) {
                    fillPickerData(pickerData);
                } else {
                    String lq = q.toLowerCase(Locale.getDefault());
                    for (AppInfo a : apps) {
                        if (pickerMatches(a, lq))
                            pickerData.add(a);
                    }
                }
                pickerAdapter.notifyDataSetChanged();
                return true;
            }
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(pickerSearch);
        content.addView(pickerGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentLp.topMargin = (int)(48 * density);
        overlay.addView(content, contentLp);

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(overlay);
    }

    private AppInfo findApp(String pkg) {
        if (appMap == null || pkg == null) return null;
        return appMap.get(pkg);
    }

    private boolean pickerMatches(AppInfo a, String lq) {
        if (a == null || AppCustom.get().isHidden(a.getPackageName())) return false;
        if (lq == null || lq.isEmpty()) return true;
        String label = AppCustom.get().label(a.getPackageName(), a.getAppName())
                .toLowerCase(Locale.getDefault());
        return label.contains(lq)
                || a.getPackageName().toLowerCase(Locale.getDefault()).contains(lq);
    }

    private void fillPickerData(java.util.ArrayList<AppInfo> data) {
        data.clear();
        if (apps == null) return;
        for (AppInfo a : apps) {
            if (pickerMatches(a, "")) data.add(a);
        }
    }

    private int findDockItemAt(float x, float y) {
        for (int i = 0; i < dockLayout.getChildCount(); i++) {
            View child = dockLayout.getChildAt(i);
            if (x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom())
                return i;
        }
        return -1;
    }

    private void updatePageDots(int activePage, int totalPages) {
        pageHandler.removeCallbacks(hideDots);
        int existing = pageIndicator.getChildCount();
        float density = getResources().getDisplayMetrics().density;
        int dotSize = (int)(8 * density);
        int margin = (int)(3 * density);

        if (existing != totalPages) {
            pageIndicator.removeAllViews();
            for (int i = 0; i < totalPages; i++) {
                View dot = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
                lp.setMargins(margin, 0, margin, 0);
                dot.setLayoutParams(lp);
                pageIndicator.addView(dot);
            }
        }

        for (int i = 0; i < totalPages; i++) {
            View dot = pageIndicator.getChildAt(i);
            dot.setBackground(i == activePage ? dotActive : dotInactive);
        }

        pageIndicator.setAlpha(1f);
        pageIndicator.setVisibility(View.VISIBLE);
        pageHandler.postDelayed(hideDots, 1000);
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void dockAction(String action) {
        performIconAction(action);
    }

    /** Executa ação de gesto por ícone (dock + atalhos): nenhum, padrões ou abrir app. */
    private void performIconAction(String action) {
        if (action == null || "none".equals(action)) return;
        try {
            switch (action) {
                case "open_drawer": openDrawer(); break;
                case "open_settings": openSettings(); break;
                case "open_wallpaper": startActivity(Intent.createChooser(
                        new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.choose_wallpaper))); break;
                case "open_widgets": startActivity(new Intent("android.appwidget.action.ACTION_APPWIDGET_PICKER")); break;
                default: {
                    String pkg = com.launcher.larla.util.GestureConfig.appOf(action);
                    if (pkg == null) return;
                    Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (li != null) startActivity(li);
                    else showToast(getString(R.string.app_not_available));
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "icon action failed", e);
        }
    }

    private String iconActionLabel(String action) {
        if (action == null || "none".equals(action)) return getString(R.string.action_none);
        switch (action) {
            case "open_drawer": return getString(R.string.action_open_drawer);
            case "open_settings": return getString(R.string.action_open_settings);
            case "open_wallpaper": return getString(R.string.action_open_wallpaper);
            case "open_widgets": return getString(R.string.action_open_widgets);
            default: {
                String pkg = com.launcher.larla.util.GestureConfig.appOf(action);
                if (pkg == null) return getString(R.string.action_none);
                String label = pkg;
                try {
                    label = getPackageManager().getApplicationInfo(pkg, 0)
                        .loadLabel(getPackageManager()).toString();
                } catch (Exception ignored) { }
                return getString(R.string.action_open_app_named, label);
            }
        }
    }

    private void performGlobalGesture(String gestureKey) {
        if (gestures == null || isFinishing() || isDestroyed()) return;
        performGlobalAction(gestures.getAction(gestureKey));
    }

    private void performGlobalAction(String action) {
        if (action == null || GestureConfig.ACT_NONE.equals(action)) return;
        try {
            switch (action) {
                case GestureConfig.ACT_DRAWER: openDrawer(); break;
                case GestureConfig.ACT_SETTINGS: openSettings(); break;
                case GestureConfig.ACT_WALLPAPER:
                    startActivity(Intent.createChooser(
                            new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.choose_wallpaper)));
                    break;
                case GestureConfig.ACT_WIDGETS:
                    startActivity(new Intent("android.appwidget.action.ACTION_APPWIDGET_PICKER"));
                    break;
                case GestureConfig.ACT_NEXT_PAGE:
                    if (widgetPages != null && currentPage < widgetPages.getPageCount() - 1) {
                        widgetPages.goToPage(currentPage + 1);
                    }
                    break;
                case GestureConfig.ACT_PREV_PAGE:
                    if (widgetPages != null && currentPage > 0) {
                        widgetPages.goToPage(currentPage - 1);
                    }
                    break;
                case GestureConfig.ACT_TOGGLE_DOCK:
                    settings.setShowDock(!settings.getShowDock());
                    applySettings();
                    break;
                default:
                    String pkg = GestureConfig.appOf(action);
                    if (pkg != null) {
                        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (li != null) startActivity(li);
                    }
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "global action failed", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (gestures == null) return;
        if (gestures.isHomeOnlyHome() && widgetPages != null
                && widgetPages.getCurrentPage() != 0) {
            widgetPages.goToPage(0);
            return;
        }
        performGlobalGesture(GestureConfig.HOME_PRESS);
    }

    private void refilter() {
        filtered.clear();
        if (apps == null) return;
        AppCustom custom = AppCustom.get();
        if (filter.isEmpty()) {
            for (AppInfo a : apps) {
                if (!custom.isHidden(a.getPackageName())) filtered.add(DrawerEntry.of(a));
            }
            if (folders != null) {
                for (FolderConfig.Folder f : folders.byLocation(FolderConfig.LOC_DRAWER)) {
                    filtered.add(DrawerEntry.of(f));
                }
            }
            return;
        }
        for (int i = 0; i < apps.size() && i < lowerAppKeys.size(); i++) {
            if (lowerAppKeys.get(i).contains(filter)) {
                AppInfo a = apps.get(i);
                if (!custom.isHidden(a.getPackageName())) filtered.add(DrawerEntry.of(a));
            }
        }
        if (folders != null) {
            for (FolderConfig.Folder f : folders.byLocation(FolderConfig.LOC_DRAWER)) {
                String name = f.name.isEmpty() ? getString(R.string.folder) : f.name;
                if (name.toLowerCase(Locale.getDefault()).contains(filter)) {
                    filtered.add(DrawerEntry.of(f));
                    continue;
                }
                for (String pkg : f.pkgs) {
                    AppInfo app = findApp(pkg);
                    if (app == null) continue;
                    String label = AppCustom.get().label(pkg, app.getAppName())
                            .toLowerCase(Locale.getDefault());
                    if (label.contains(filter)
                            || pkg.toLowerCase(Locale.getDefault()).contains(filter)) {
                        filtered.add(DrawerEntry.of(f));
                        break;
                    }
                }
            }
        }
    }

    private void setupGestures() {
        GestureDetector gDrawer = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 != null && e2 != null && e2.getY() - e1.getY() > 80 && Math.abs(vy) > 80) {
                    closeDrawer(); return true;
                }
                return false;
            }
        });
        gDrawer.setIsLongpressEnabled(false);
        appDrawer.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                gDrawer.onTouchEvent(e);
            }
            return true;
        });
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        int drawerMs = settings.getDrawerAnimMs();
        int fadeMs = drawerMs * 3 / 4;
        int drawerColor = settings.getDrawerFinalColor();
        getWindow().setBackgroundDrawable(new ColorDrawable(drawerColor));
        appDrawer.setBackgroundColor(Color.TRANSPARENT);
        appDrawer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dockContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        widgetPages.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        appDrawer.setAlpha(0f);
        appDrawer.setTranslationY(100f);
        appDrawer.setVisibility(View.VISIBLE);
        appDrawer.animate().alpha(1f).translationY(0f).setDuration(drawerMs)
            .withEndAction(() -> appDrawer.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        dockContainer.animate().alpha(0f).setDuration(fadeMs).withEndAction(() -> { dockContainer.setVisibility(View.GONE); dockContainer.setLayerType(View.LAYER_TYPE_NONE, null); }).start();
        widgetPages.animate().alpha(0f).setDuration(fadeMs).withEndAction(() -> { widgetPages.setVisibility(View.GONE); widgetPages.setLayerType(View.LAYER_TYPE_NONE, null); }).start();
        // Reset empty state visibility
        emptyStateView.setVisibility(View.GONE);
        gridView.setVisibility(View.VISIBLE);
        // Staggered animation for grid items
        gridView.post(() -> staggerGridItems());
    }

    private void staggerGridItems() {
        int n = gridView.getChildCount();
        if (n <= 0) return;
        int itemMs = settings.getDrawerAnimMs();
        gridView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        for (int i = 0; i < n; i++) {
            View child = gridView.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(40f);
            int delay = itemMs <= 0 ? 0 : Math.min(i * 25 * itemMs / 200, itemMs + 100);
            boolean last = (i == n - 1);
            child.animate().alpha(1f).translationY(0f)
                .setDuration(itemMs)
                .setStartDelay(delay)
                .setInterpolator(decelerateInterpolator)
                .withEndAction(last ? () -> gridView.setLayerType(View.LAYER_TYPE_NONE, null) : null)
                .start();
        }
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        hidePullHint();
        resetDrawerHover();
        int drawerMs = settings.getDrawerAnimMs();
        int fadeMs = drawerMs * 3 / 4;
        gridView.setLayerType(View.LAYER_TYPE_NONE, null);
        appDrawer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dockContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        widgetPages.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        appDrawer.animate().alpha(0f).translationY(100f).setDuration(drawerMs)
            .withEndAction(() -> { appDrawer.setVisibility(View.GONE); appDrawer.setTranslationY(0f); appDrawer.setLayerType(View.LAYER_TYPE_NONE, null); appDrawer.setBackgroundColor(settings.getDrawerFinalColor()); getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); }).start();
        dockContainer.setAlpha(0f);
        dockContainer.setVisibility(View.VISIBLE);
        dockContainer.animate().alpha(1f).setDuration(fadeMs)
            .withEndAction(() -> dockContainer.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        widgetPages.setAlpha(0f);
        widgetPages.setVisibility(View.VISIBLE);
        widgetPages.animate().alpha(1f).setDuration(fadeMs)
            .withEndAction(() -> widgetPages.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        searchView.setQuery("", false);
        searchView.clearFocus();
        updatePageDots(currentPage, dockConfig.getPageCount());
    }

    private void launch(AppInfo app) {
        Intent i = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (i != null) startActivity(i);
    }

    private void showMenu(AppInfo app) {
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        items.add(new ModernSheet.Item("\uD83D\uDCCC", getString(R.string.shortcut_title)));
        items.add(new ModernSheet.Item("ℹ️", getString(R.string.info)));
        items.add(new ModernSheet.Item("\uD83D\uDDD1️", getString(R.string.uninstall)));
        items.add(new ModernSheet.Item("\uD83D\uDCC1", getString(R.string.add_to_folder)));
        items.add(new ModernSheet.Item("✏️", getString(R.string.rename)));
        items.add(new ModernSheet.Item("\uD83C\uDFA8", getString(R.string.edit_icon)));
        items.add(new ModernSheet.Item("\uD83D\uDC41️", getString(R.string.hide)));
        items.add(new ModernSheet.Item("\uD83D\uDCE4", getString(R.string.share)));
        items.add(new ModernSheet.Item("⭐", getString(R.string.rate_app)));
        final String appLabel = AppCustom.get().label(app.getPackageName(), app.getAppName());
        final String pkg = app.getPackageName();
        ModernSheet.show(this, appLabel, items, w -> {
                switch (w) {
                    case 0:
                        java.util.List<ModernSheet.Item> sub = new java.util.ArrayList<>();
                        sub.add(new ModernSheet.Item("\uD83D\uDCF1", getString(R.string.add_to_workspace)));
                        sub.add(new ModernSheet.Item("\uD83D\uDCCC", getString(R.string.add_to_dock)));
                        ModernSheet.show(this, getString(R.string.shortcut_title), sub, w2 -> {
                            if (w2 == 0) addWorkspaceApp(app);
                            else addAppToDock(app);
                        });
                        break;
                    case 1:
                        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:" + pkg)));
                        break;
                    case 2:
                        startActivity(new Intent(Intent.ACTION_DELETE,
                            android.net.Uri.parse("package:" + pkg)));
                        break;
                    case 3:
                        showAddToFolderSheet(app, FolderConfig.LOC_DRAWER);
                        break;
                    case 4:
                        renameAppFlow(app);
                        break;
                    case 5:
                        editAppIconSheet(app);
                        break;
                    case 6:
                        AppCustom.get().setHidden(pkg, true);
                        refreshAppVisual(pkg);
                        showToast(getString(R.string.app_hidden));
                        break;
                    case 7:
                        shareApp(app);
                        break;
                    case 8:
                        rateApp(pkg);
                        break;
                }
            });
    }

    private void renameAppFlow(AppInfo app) {
        final String pkg = app.getPackageName();
        android.widget.EditText edit = new android.widget.EditText(this);
        edit.setText(AppCustom.get().label(pkg, app.getAppName()));
        edit.setHint(getString(R.string.rename));
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0x88FFFFFF);
        edit.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        edit.setPadding(pad, pad, pad, pad);
        edit.setSelection(edit.getText().length());
        ModernSheet.showCustom(this, getString(R.string.rename), edit,
            getString(R.string.ok), getString(R.string.cancel), () -> {
                AppCustom.get().setLabel(pkg, edit.getText().toString().trim());
                refreshAppVisual(pkg);
            }, null);
    }

    private void shareApp(AppInfo app) {
        try {
            String label = AppCustom.get().label(app.getPackageName(), app.getAppName());
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT, label + "\nhttps://play.google.com/store/apps/details?id=" + app.getPackageName());
            startActivity(Intent.createChooser(s, getString(R.string.share)));
        } catch (Exception e) {
            Log.w(TAG, "share failed", e);
        }
    }

    private void rateApp(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e1) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (Exception e2) {
                Log.w(TAG, "rate failed", e2);
            }
        }
    }

    /** Atualiza chave de busca + grade + dock + workspace para um pkg. */
    private void refreshAppVisual(String pkg) {
        try {
            if (apps != null && lowerAppKeys.size() == apps.size()) {
                for (int i = 0; i < apps.size(); i++) {
                    AppInfo a = apps.get(i);
                    if (a.getPackageName().equals(pkg)) {
                        lowerAppKeys.set(i, (AppCustom.get().label(pkg, a.getAppName())
                            + "\n" + pkg).toLowerCase(Locale.getDefault()));
                        break;
                    }
                }
            }
            refilter();
            if (adapter != null) {
                adapter.submitList(new ArrayList<>(filtered));
                // Personalizações (nome/ícone/oculto) vivem fora do AppInfo, então o
                // DiffUtil não detecta a mudança: força rebind das células visíveis.
                adapter.notifyDataSetChanged();
            }
            lastDockSig = "";
            setupDock();
            clearWorkspaceShortcutViews();
            loadWorkspaceApps();
            loadWorkspaceFolders();
            refreshFolderViews();
        } catch (Exception e) {
            Log.w(TAG, "refresh visual failed", e);
        }
    }

    private void editAppIconSheet(AppInfo app) {
        final String pkg = app.getPackageName();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * density);
        root.setPadding(pad, (int) (4 * density), pad, (int) (4 * density));

        int sizePx = (int) (72 * density);
        LinearLayout prevRow = new LinearLayout(this);
        prevRow.setOrientation(LinearLayout.HORIZONTAL);
        prevRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView preview = new ImageView(this);
        bindIcon(preview, pkg);
        preview.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int curShape = AppCustom.get().shapeOf(pkg, settings.getIconShape());
        int curColor = AppCustom.get().colorOf(pkg, IconStyle.plateColor(pkg));
        int[] corners = curShape == com.launcher.larla.util.IconShape.CUSTOM ? settings.getCorners() : null;
        IconStyle.applyPlate(preview, curShape, sizePx, curColor, corners,
            settings.getGlyphPct(), settings.getPlatePct());
        prevRow.addView(preview);
        TextView lbl = new TextView(this);
        lbl.setText(AppCustom.get().label(pkg, app.getAppName()));
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(15);
        lbl.setPadding((int) (12 * density), 0, 0, 0);
        prevRow.addView(lbl);
        root.addView(prevRow);

        final int[] shapeHolder = new int[]{ curShape };
        final int[] colorHolder = new int[]{ curColor };
        final int[] RESET = new int[]{ 0 };

        Runnable repaint = () -> {
            int sh = shapeHolder[0];
            int[] cn = sh == com.launcher.larla.util.IconShape.CUSTOM ? settings.getCorners() : null;
            IconStyle.applyPlate(preview, sh, sizePx,
                colorHolder[0] == 0 ? IconStyle.plateColor(pkg) : colorHolder[0],
                cn, settings.getGlyphPct(), settings.getPlatePct());
        };

        android.widget.Button btnShape = new android.widget.Button(this);
        btnShape.setText(getString(R.string.edit_icon) + ": " + getString(R.string.icon_shape));
        btnShape.setOnClickListener(v -> {
            int cur = shapeHolder[0];
            ModernSheet.showGrid(this, getString(R.string.icon_shape),
                com.launcher.larla.util.IconShape.COUNT, cur,
                (idx, chk) -> {
                    LinearLayout outer = new LinearLayout(this);
                    outer.setOrientation(LinearLayout.VERTICAL);
                    outer.setGravity(Gravity.CENTER);
                    outer.setMinimumHeight((int) (72 * density));
                    int box = (int) (56 * density);
                    android.widget.FrameLayout f = new android.widget.FrameLayout(this);
                    f.setBackground(new IconStyle.ShapePlateDrawable(idx, 0xFF8A8A8A));
                    outer.addView(f, new LinearLayout.LayoutParams(box, box));
                    if (chk) {
                        TextView tick = new TextView(this);
                        tick.setText("✓");
                        tick.setTextColor(0xFF00D4FF);
                        tick.setGravity(Gravity.CENTER);
                        outer.addView(tick);
                    }
                    return outer;
                }, w -> {
                    shapeHolder[0] = w;
                    repaint.run();
                });
        });
        root.addView(btnShape);

        TextView colorLbl = new TextView(this);
        colorLbl.setText(getString(R.string.plate_color));
        colorLbl.setTextColor(0xFFBDBDBD);
        colorLbl.setPadding(0, (int) (12 * density), 0, (int) (4 * density));
        root.addView(colorLbl);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] presets = new int[]{ 0, 0xFF1A1A1A, 0xFF2A2A2A, 0xFF00D4FF, 0xFF4CAF50,
            0xFFFFC107, 0xFFFF5722, 0xFF9C27B0, 0xFFFFFFFF };
        String[] presetNames = new String[]{ getString(R.string.auto), "#1A1A1A", "#2A2A2A",
            "#00D4FF", "#4CAF50", "#FFC107", "#FF5722", "#9C27B0", "#FFFFFF" };
        for (int i = 0; i < presets.length; i++) {
            final int c = presets[i];
            android.widget.Button b = new android.widget.Button(this);
            b.setText(presetNames[i].length() > 7 ? presetNames[i].substring(0, 7) : presetNames[i]);
            b.setTextSize(9);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                (int) (40 * density), 1f);
            blp.rightMargin = (int) (4 * density);
            b.setLayoutParams(blp);
            if (c != 0) {
                android.graphics.drawable.GradientDrawable gd =
                    new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                gd.setCornerRadius(8 * density);
                gd.setColor(c);
                b.setBackground(gd);
                b.setTextColor(c == 0xFFFFFFFF || c == 0xFFFFC107 ? Color.BLACK : Color.WHITE);
            }
            b.setOnClickListener(v -> {
                colorHolder[0] = c;
                repaint.run();
            });
            colorRow.addView(b);
            if ((i + 1) % 3 == 0 && i + 1 < presets.length) {
                root.addView(colorRow);
                colorRow = new LinearLayout(this);
                colorRow.setOrientation(LinearLayout.HORIZONTAL);
            }
        }
        root.addView(colorRow);

        android.widget.Button btnReset = new android.widget.Button(this);
        btnReset.setText(getString(R.string.reset_icon));
        btnReset.setOnClickListener(v -> {
            RESET[0] = 1;
            ModernSheet.dismissAll();
            AppCustom.get().resetIcon(pkg);
            iconCache.removeAllFor(pkg);
            refreshAppVisual(pkg);
        });
        root.addView(btnReset);

        ModernSheet.showCustom(this, getString(R.string.edit_icon), root,
            getString(R.string.ok), getString(R.string.cancel), () -> {
                if (RESET[0] == 1) return;
                AppCustom custom = AppCustom.get();
                if (shapeHolder[0] == settings.getIconShape()) custom.setShape(pkg, -1);
                else custom.setShape(pkg, shapeHolder[0]);
                if (colorHolder[0] == 0) custom.setColor(pkg, 0);
                else custom.setColor(pkg, colorHolder[0]);
                iconCache.removeAllFor(pkg);
                refreshAppVisual(pkg);
            }, null);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void showHomeContextMenu() {
        boolean canAdd = widgetPages.getPageCount() < WidgetPageLayout.MAX_PAGES;
        boolean canRemove = widgetPages.getPageCount() > 1 && widgetPages.isPageEmpty(widgetPages.getCurrentPage());
        showWorkspaceContextMenu(widgetPages.getCurrentPage(), canAdd, canRemove);
    }

    private void showWorkspaceContextMenu(int page, boolean canAddPage, boolean canRemovePage) {
        java.util.List<ModernSheet.Item> items = new java.util.ArrayList<>();
        java.util.List<Integer> actions = new java.util.ArrayList<>();

        items.add(new ModernSheet.Item("\uD83D\uDDBC\uFE0F", getString(R.string.wallpaper))); actions.add(0);
        items.add(new ModernSheet.Item("\uD83E\uDDE9", getString(R.string.widget_add))); actions.add(1);
        items.add(new ModernSheet.Item("\uD83D\uDD50", widgetConfig.hasClock() ? getString(R.string.remove_clock) : getString(R.string.add_clock))); actions.add(2);
        items.add(new ModernSheet.Item("⚙️", getString(R.string.action_open_settings))); actions.add(3);
        items.add(new ModernSheet.Item("\uD83D\uDCF1", getString(R.string.add_shortcut_to_workspace))); actions.add(6);
        items.add(new ModernSheet.Item("\uD83D\uDCCC", getString(R.string.add_shortcut_to_dock))); actions.add(7);
        if (canAddPage) { items.add(new ModernSheet.Item("➕", getString(R.string.add_page))); actions.add(4); }
        if (canRemovePage) { items.add(new ModernSheet.Item("➖", getString(R.string.remove_page))); actions.add(5); }

        ModernSheet.show(this, getString(R.string.customize), items, idx -> {
                switch (actions.get(idx)) {
                    case 0: startActivity(Intent.createChooser(
                            new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.choose_wallpaper))); break;
                    case 1: openWidgetPicker(); break;
                    case 2:
                        if (widgetConfig.hasClock()) removeClockWidget();
                        else addClockWidget();
                        break;
                    case 3: openSettings(); break;
                    case 4:
                        int newPage = widgetPages.addPage();
                        if (newPage >= 0) {
                            widgetPages.goToPage(newPage);
                            widgetConfig.setPageCount(widgetPages.getPageCount());
                            workspaceApps.setPageCount(widgetPages.getPageCount());
                            widgetConfig.save();
                            workspaceApps.reload();
                            showToast(getString(R.string.page_added));
                        } else {
                            showToast(getString(R.string.max_pages_reached));
                        }
                        break;
                    case 5:
                        ModernSheet.showConfirm(this, getString(R.string.remove_page),
                            getString(R.string.remove_page_confirm), getString(R.string.yes),
                            getString(R.string.cancel), () -> {
                                widgetPages.removePage(page);
                                widgetConfig.setPageCount(widgetPages.getPageCount());
                                workspaceApps.setPageCount(widgetPages.getPageCount());
                                widgetConfig.save();
                                workspaceApps.reload();
                                showToast(getString(R.string.page_removed));
                            }, null);
                        break;
                    case 6:
                        showAppPicker(app -> addWorkspaceApp(app));
                        break;
                    case 7:
                        showAppPicker(app -> addAppToDock(app));
                        break;
                }
            });
    }

    private void openWidgetPicker() {
        final FrameLayout pickerOverlay = new FrameLayout(this);
        pickerOverlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pickerOverlay.setBackgroundColor(0xFF121212);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * getResources().getDisplayMetrics().density)));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int)(12 * getResources().getDisplayMetrics().density), 0,
                (int)(12 * getResources().getDisplayMetrics().density), 0);
        header.setBackgroundColor(0xFF1A1A1A);

        TextView backBtn = new TextView(this);
        backBtn.setText("←");
        backBtn.setTextSize(20);
        backBtn.setTextColor(0xFF00D4FF);
        backBtn.setOnClickListener(v -> ((ViewGroup) pickerOverlay.getParent()).removeView(pickerOverlay));
        header.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(getString(R.string.widget_add));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding((int)(8 * getResources().getDisplayMetrics().density), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pickerOverlay.addView(header);

        // grid
        final List<AppWidgetProviderInfo> widgets = appWidgetManager.getInstalledProviders();
        GridView grid = new GridView(this);
        grid.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        grid.setNumColumns(2);
        grid.setColumnWidth((int)(160 * getResources().getDisplayMetrics().density));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing((int)(12 * getResources().getDisplayMetrics().density));
        grid.setHorizontalSpacing((int)(12 * getResources().getDisplayMetrics().density));
        grid.setPadding((int)(12 * getResources().getDisplayMetrics().density),
                (int)(12 * getResources().getDisplayMetrics().density),
                (int)(12 * getResources().getDisplayMetrics().density),
                (int)(12 * getResources().getDisplayMetrics().density));
        grid.setClipToPadding(false);

        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return widgets.size(); }
            @Override public Object getItem(int p) { return widgets.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                if (convert == null) {
                    convert = getLayoutInflater().inflate(R.layout.item_widget_picker, parent, false);
                }
                AppWidgetProviderInfo info = widgets.get(pos);
                ((TextView) convert.findViewById(R.id.widget_name)).setText(info.loadLabel(getPackageManager()));
                ((TextView) convert.findViewById(R.id.widget_size)).setText(info.minWidth + " x " + info.minHeight + " cells");
                ImageView icon = convert.findViewById(R.id.widget_icon);
                String cacheKey = info.provider.flattenToString();
                Drawable preview = widgetPreviewCache.get(cacheKey);
                if (preview == null) {
                    preview = info.loadPreviewImage(LauncherActivity.this, 0);
                    if (preview == null) preview = info.loadIcon(LauncherActivity.this, 0);
                    if (preview != null) widgetPreviewCache.put(cacheKey, preview);
                }
                icon.setImageDrawable(preview);
                return convert;
            }
        });

        final float[] lastTouch = new float[2];
        grid.setOnTouchListener((v, event) -> {
            if (pickerDragForwarding) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE:
                        widgetPages.continueDrag(event.getRawX(), event.getRawY());
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        widgetPages.endDrag(event.getRawX(), event.getRawY());
                        pickerDragForwarding = false;
                        pickerOverlay.post(() -> {
                            ViewGroup p = (ViewGroup) pickerOverlay.getParent();
                            if (p != null) p.removeView(pickerOverlay);
                        });
                        return true;
                }
                return true;
            }
            lastTouch[0] = event.getRawX();
            lastTouch[1] = event.getRawY();
            return false;
        });

        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            AppWidgetProviderInfo info = widgets.get(position);
            pendingProvider = info.provider;
            pendingTouchX = lastTouch[0];
            pendingTouchY = lastTouch[1];

            int[] parentLoc = new int[2];
            ((View) indicator.getParent()).getLocationOnScreen(parentLoc);
            float px = pendingTouchX - parentLoc[0] - indicator.getWidth() / 2f;
            float py = pendingTouchY - parentLoc[1] - indicator.getHeight() / 2f;
            indicator.setX(px);
            indicator.setY(py);
            indicator.setFillColor(0xFF00CC00);
            indicator.start(() -> {
                int aid = appWidgetHost.allocateAppWidgetId();
                if (appWidgetManager.bindAppWidgetIdIfAllowed(aid, pendingProvider)) {
                    if (info.configure != null) {
                        pendingWidgetId = aid;
                        ((ViewGroup) pickerOverlay.getParent()).removeView(pickerOverlay);
                        Intent configIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                        configIntent.setComponent(info.configure);
                        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, aid);
                        startActivityForResult(configIntent, REQUEST_CONFIGURE_WIDGET);
                    } else {
                        pendingWidgetId = -1;
                        initiateDragFromPicker(aid, pendingTouchX, pendingTouchY);
                        pickerOverlay.setAlpha(0f);
                        pickerDragForwarding = true;
                    }
                } else {
                    pendingWidgetId = aid;
                    ((ViewGroup) pickerOverlay.getParent()).removeView(pickerOverlay);
                    promptGrantPermission(aid, pendingProvider);
                }
            });
            return true;
        });

        pickerOverlay.addView(grid);
        ((ViewGroup) findViewById(android.R.id.content)).addView(pickerOverlay);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (drawerOpen) closeDrawer();
        else super.onBackPressed();
    }

    private static class DrawerEntry {
        final AppInfo app;
        final FolderConfig.Folder folder;

        private DrawerEntry(AppInfo app, FolderConfig.Folder folder) {
            this.app = app;
            this.folder = folder;
        }

        static DrawerEntry of(AppInfo app) { return new DrawerEntry(app, null); }
        static DrawerEntry of(FolderConfig.Folder folder) { return new DrawerEntry(null, folder); }

        boolean isFolder() { return folder != null; }

        String stableId() {
            return isFolder() ? "f:" + folder.id : "a:" + app.getPackageName();
        }
    }

    private class AppRecyclerAdapter extends RecyclerView.Adapter<AppRecyclerAdapter.ViewHolder> {
        private static final int TYPE_APP = 0;
        private static final int TYPE_FOLDER = 1;

        private final AsyncListDiffer<DrawerEntry> differ =
                new AsyncListDiffer<>(this, new AppDiffCallback());
        private final OnItemClickListener clickListener;

        interface OnItemClickListener { void onItemClick(AppInfo app); }

        AppRecyclerAdapter(OnItemClickListener click) {
            this.clickListener = click;
            setHasStableIds(true);
        }

        public void submitList(List<DrawerEntry> list) { differ.submitList(list); }

        @Override
        public int getItemCount() { return differ.getCurrentList().size(); }

        @Override
        public long getItemId(int position) {
            return differ.getCurrentList().get(position).stableId().hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return differ.getCurrentList().get(position).isFolder() ? TYPE_FOLDER : TYPE_APP;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(
                    viewType == TYPE_FOLDER ? R.layout.item_folder : R.layout.item_app,
                    parent, false);
            return new ViewHolder(v, viewType);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            DrawerEntry entry = differ.getCurrentList().get(position);
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            if (entry.isFolder()) {
                bindFolderCell(holder, entry.folder);
            } else {
                AppInfo app = entry.app;
                bindIcon(holder.icon, app.getPackageName(), SettingsManager.SURF_DRAWER);
                holder.name.setText(AppCustom.get().label(app.getPackageName(), app.getAppName()));
                int px = cachedGridIconPx > 0 ? cachedGridIconPx : 96;
                ViewGroup.LayoutParams lp = holder.icon.getLayoutParams();
                if (lp.width != px) { lp.width = px; lp.height = px; holder.icon.setLayoutParams(lp); }
                IconStyle.applyPlateAuto(holder.icon, settings, px, app.getPackageName(), SettingsManager.SURF_DRAWER);
                holder.name.setTextSize(settings.getLabelSize(SettingsManager.SURF_DRAWER));
            }
            holder.bind(entry);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            FrameLayout folderCard;
            ImageView[] minis;
            TextView folderName;
            final int viewType;
            // Segurar-parado = menu; segurar+mover = arrastar p/ workspace/dock.
            float pressX, pressY;
            boolean pressArmed, pressDragging;
            AppInfo pressApp;
            final Runnable armPress;

            ViewHolder(View v, int viewType) {
                super(v);
                this.viewType = viewType;
                armPress = () -> {
                    pressArmed = true;
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    // Garante que a rolagem da grade não roube o gesto de arrastar.
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (pressApp != null) showPullHint(pressApp);
                };
                if (viewType == TYPE_FOLDER) {
                    folderCard = v.findViewById(R.id.folder_card);
                    folderName = v.findViewById(R.id.folder_name);
                    minis = new ImageView[]{
                        v.findViewById(R.id.fmini0), v.findViewById(R.id.fmini1),
                        v.findViewById(R.id.fmini2), v.findViewById(R.id.fmini3)};
                } else {
                    icon = v.findViewById(R.id.app_icon);
                    name = v.findViewById(R.id.app_name);
                }
                v.setOnClickListener(vw -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    DrawerEntry entry = differ.getCurrentList().get(pos);
                    if (entry.isFolder()) openFolderWindow(entry.folder.id);
                    else clickListener.onItemClick(entry.app);
                });
                v.setOnLongClickListener(vw -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;
                    DrawerEntry entry = differ.getCurrentList().get(pos);
                    if (entry.isFolder()) {
                        showDrawerFolderMenu(entry.folder.id);
                        return true;
                    }
                    return false;
                });
                v.setOnTouchListener((vw, ev) -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;
                    DrawerEntry entry = differ.getCurrentList().get(pos);
                    if (entry.isFolder() || entry.app == null) return false;
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            pressX = ev.getRawX();
                            pressY = ev.getRawY();
                            pressArmed = false;
                            pressDragging = false;
                            pressApp = entry.app;
                            vw.removeCallbacks(armPress);
                            vw.postDelayed(armPress,
                                (long) ViewConfiguration.getLongPressTimeout());
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (pressApp == null || pressDragging) break;
                            float dx = ev.getRawX() - pressX;
                            float dy = ev.getRawY() - pressY;
                            int slop = ViewConfiguration.get(vw.getContext()).getScaledTouchSlop();
                            if (dx * dx + dy * dy > (float) slop * slop) {
                                vw.removeCallbacks(armPress);
                                if (pressArmed) {
                                    // Mantém a dica visível durante o arrasto na
                                    // gaveta; ela some ao chegar na área de trabalho.
                                    pressDragging = true;
                                    startWorkspaceDrag(vw, pressApp);
                                } else {
                                    // Rolagem da grade: desarma o toque.
                                    pressApp = null;
                                }
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            vw.removeCallbacks(armPress);
                            hidePullHint();
                            boolean held = pressArmed || pressDragging;
                            if (pressArmed && !pressDragging && pressApp != null) {
                                showMenu(pressApp);
                            }
                            pressArmed = false;
                            pressDragging = false;
                            pressApp = null;
                            // Segurou: consome o UP p/ não disparar o clique.
                            if (held) return true;
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            vw.removeCallbacks(armPress);
                            hidePullHint();
                            pressArmed = false;
                            pressDragging = false;
                            pressApp = null;
                            break;
                    }
                    return false;
                });
            }
            void bind(DrawerEntry entry) {
                // already set in onBind
            }
        }

        private static class AppDiffCallback extends DiffUtil.ItemCallback<DrawerEntry> {
            @Override
            public boolean areItemsTheSame(DrawerEntry oldItem, DrawerEntry newItem) {
                return oldItem.stableId().equals(newItem.stableId());
            }
            @Override
            public boolean areContentsTheSame(DrawerEntry oldItem, DrawerEntry newItem) {
                if (oldItem.isFolder() != newItem.isFolder()) return false;
                if (!oldItem.isFolder()) {
                    return oldItem.app.getAppName().equals(newItem.app.getAppName());
                }
                FolderConfig.Folder a = oldItem.folder, b = newItem.folder;
                return a.name.equals(b.name) && a.pkgs.equals(b.pkgs)
                    && a.bgColor == b.bgColor && a.cornerRadiusDp == b.cornerRadiusDp
                    && a.showName == b.showName && a.columns == b.columns
                    && a.iconSizePct == b.iconSizePct;
            }
        }
    }

    private int[] widgetGridSize(int minWidth, int minHeight) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;
        int screenW = getResources().getConfiguration().screenWidthDp;
        int screenH = getResources().getConfiguration().screenHeightDp;

        int sbDp = (int)(getBarDimension("status_bar_height") / density);
        int nbDp = (int)(getBarDimension("navigation_bar_height") / density);
        int topAreaDp = 80;
        int dockDp = 56;

        int availH = screenH - sbDp - nbDp - topAreaDp - dockDp;
        if (availH < screenH / 3) availH = screenH * 2 / 3;

        int cols = 4, rows = 6;
        int cellW = screenW / cols;
        int cellH = availH / rows;

        int spanW = Math.max(1, Math.min(4, (minWidth + cellW - 1) / cellW));
        int spanH = Math.max(1, Math.min(4, (minHeight + cellH - 1) / cellH));

        float scale = Math.max(1f, screenW / 360f);
        int stdW = (int)((56 * spanW - 16) * scale);
        int stdH = (int)((56 * spanH - 16) * scale);

        return new int[]{ (int)(stdW * density), (int)(stdH * density) };
    }

    private void updateWidgetOptions(int appWidgetId, int widthPx, int heightPx) {
        try {
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, pxToDp(widthPx));
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, pxToDp(widthPx));
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, pxToDp(heightPx));
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, pxToDp(heightPx));
            appWidgetManager.updateAppWidgetOptions(appWidgetId, options);
        } catch (Exception e) {
            Log.w(TAG, "Failed to update widget options for id=" + appWidgetId, e);
        }
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private int getBarDimension(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        if (id == 0) return 0;
        try { return getResources().getDimensionPixelSize(id); } catch (Exception e) { return 0; }
    }

    private void loadWidgets() {
        int savedPage = widgetPages.getCurrentPage();
        for (WidgetConfig.WidgetEntry w : widgetConfig.getWidgets()) {
            if (w.id == WidgetConfig.CLOCK_ID) continue;
            try {
                widgetPages.ensurePagesExist(w.page);
                widgetPages.goToPage(w.page);
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(w.id);
                if (info == null) { widgetConfig.removeWidget(w.id); continue; }
                AppWidgetHostView hostView = appWidgetHost.createView(this, w.id, info);

                int[] size;
                if (w.width > 0 && w.height > 0) {
                    size = new int[]{ w.width, w.height };
                } else {
                    size = widgetGridSize(info.minWidth, info.minHeight);
                    widgetConfig.resizeWidget(w.id, size[0], size[1]);
                }
                hostView.setMinimumWidth(size[0]);
                hostView.setMinimumHeight(size[1]);

                widgetPages.addWidgetToCurrentPage(hostView, w.x, w.y);
                updateWidgetOptions(w.id, size[0], size[1]);
                if (w.opacity < 100) {
                    View parent = (View) hostView.getParent();
                    if (parent != null) parent.setAlpha(w.opacity / 100f);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load widget id=" + w.id, e);
                widgetConfig.removeWidget(w.id);
            }
        }
        widgetPages.goToPage(savedPage);
    }

    private void loadClockWidget() {
        if (!widgetConfig.hasClock()) return;
        WidgetConfig.WidgetEntry entry = widgetConfig.findWidget(WidgetConfig.CLOCK_ID);
        if (entry == null) return;
        widgetPages.ensurePagesExist(entry.page);
        widgetPages.goToPage(entry.page);
        createClockWidget(entry.x, entry.y, entry.width, entry.height);
        if (entry.opacity < 100 && clockWidgetContainer != null) {
            View parent = (View) clockWidgetContainer.getParent();
            if (parent != null) parent.setAlpha(entry.opacity / 100f);
        }
    }

    private void createClockWidget(int x, int y) {
        createClockWidget(x, y, -1, -1);
    }

    private void createClockWidget(int x, int y, int w, int h) {
        float density = getResources().getDisplayMetrics().density;

        clockTimeText = new TextView(this);
        clockTimeText.setTextSize(72);
        clockTimeText.setTextColor(Color.WHITE);
        clockTimeText.setTypeface(null, Typeface.BOLD);
        clockTimeText.setShadowLayer(4 * density, 2, 2, 0x80000000);
        clockTimeText.setGravity(Gravity.CENTER);

        clockDateText = new TextView(this);
        clockDateText.setTextSize(20);
        clockDateText.setTextColor(0xCCFFFFFF);
        clockDateText.setShadowLayer(3 * density, 1, 1, 0x80000000);
        clockDateText.setGravity(Gravity.CENTER);

        Date now = new Date();
        clockTimeText.setText(tf.format(now));
        clockDateText.setText(df.format(now));

        clockWidgetContainer = new LinearLayout(this);
        clockWidgetContainer.setOrientation(LinearLayout.VERTICAL);
        clockWidgetContainer.setGravity(Gravity.CENTER);
        clockWidgetContainer.addView(clockTimeText);
        clockWidgetContainer.addView(clockDateText);
        clockWidgetContainer.setTag(WidgetConfig.CLOCK_ID);

        widgetPages.addWidgetToCurrentPage(clockWidgetContainer, x, y);

        if (w > 0 && h > 0) {
            View parent = (View) clockWidgetContainer.getParent();
            if (parent != null) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) parent.getLayoutParams();
                lp.width = w;
                lp.height = h;
                parent.setLayoutParams(lp);
            }
        }
    }

    private void addClockWidget() {
        if (widgetConfig.hasClock()) {
            showToast(getString(R.string.clock_exists));
            return;
        }
        int x = 50, y = 50;
        createClockWidget(x, y);
        widgetConfig.addClock(x, y);
        View parent = (View) clockWidgetContainer.getParent();
        if (parent != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) parent.getLayoutParams();
            widgetConfig.resizeWidget(WidgetConfig.CLOCK_ID, lp.width, lp.height);
        }
    }

    private void removeClockWidget() {
        if (clockWidgetContainer == null) return;
        FrameLayout p = (FrameLayout) clockWidgetContainer.getParent();
        if (p != null) p.removeView(clockWidgetContainer);
        removeClockWidgetRefs();
        widgetConfig.removeClock();
    }

    private void removeClockWidgetRefs() {
        clockWidgetContainer = null;
        clockTimeText = null;
        clockDateText = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WIDGET) {
            if (resultCode == RESULT_OK && pendingProvider != null && pendingWidgetId >= 0) {
                boolean bound = appWidgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, pendingProvider);
                if (bound) {
                    placeWidgetDirect(pendingWidgetId, pendingTouchX, pendingTouchY);
                    pendingWidgetId = -1;
                    pendingProvider = null;
                } else {
                    promptGrantPermission(pendingWidgetId, pendingProvider);
                }
            } else {
                if (pendingWidgetId >= 0) {
                    appWidgetHost.deleteAppWidgetId(pendingWidgetId);
                }
                pendingWidgetId = -1;
                pendingProvider = null;
            }
        } else if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK && pendingWidgetId >= 0) {
                placeWidgetDirect(pendingWidgetId, pendingTouchX, pendingTouchY);
                pendingWidgetId = -1;
                pendingProvider = null;
            } else {
                if (pendingWidgetId >= 0) {
                    appWidgetHost.deleteAppWidgetId(pendingWidgetId);
                }
                pendingWidgetId = -1;
                pendingProvider = null;
            }
        }
    }

    private void promptGrantPermission(int appWidgetId, ComponentName provider) {
        ModernSheet.showConfirm(this, getString(R.string.widget_add),
            getString(R.string.widget_permission_message), getString(R.string.try_again),
            getString(R.string.cancel), () -> {
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
                startActivityForResult(intent, REQUEST_PICK_WIDGET);
            }, () -> {
                appWidgetHost.deleteAppWidgetId(appWidgetId);
                pendingWidgetId = -1;
                pendingProvider = null;
            });
    }

    private void initiateDragFromPicker(int appWidgetId, float rawX, float rawY) {
        try {
            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
            AppWidgetHostView hostView = appWidgetHost.createView(this, appWidgetId, info);

            final int[] size = widgetGridSize(info.minWidth, info.minHeight);
            hostView.setMinimumWidth(size[0]);
            hostView.setMinimumHeight(size[1]);

            int[] loc = new int[2];
            widgetPages.getLocationOnScreen(loc);
            int pageX = Math.max(0, (int)(rawX - loc[0] - size[0] / 2f));
            int pageY = Math.max(0, (int)(rawY - loc[1] - size[1] / 2f));

            widgetPages.addWidgetToCurrentPage(hostView, pageX, pageY);
            widgetConfig.addWidget(appWidgetId,
                    widgetPages.getCurrentPage(), pageX, pageY);
            updateWidgetOptions(appWidgetId, size[0], size[1]);

            FrameLayout pageLayout = widgetPages.getCurrentPageLayout();
            View wrapper = pageLayout.getChildAt(pageLayout.getChildCount() - 1);
            widgetPages.startDragOn(wrapper, rawX, rawY, pageX, pageY);
        } catch (Exception e) {
            showToast(getString(R.string.widgets_not_supported));
        }
    }

    private void placeWidgetDirect(int appWidgetId, float rawX, float rawY) {
        try {
            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
            AppWidgetHostView hostView = appWidgetHost.createView(this, appWidgetId, info);

            final int[] size = widgetGridSize(info.minWidth, info.minHeight);
            hostView.setMinimumWidth(size[0]);
            hostView.setMinimumHeight(size[1]);

            int[] loc = new int[2];
            widgetPages.getLocationOnScreen(loc);
            int pageX = Math.max(0, (int)(rawX - loc[0] - size[0] / 2f));
            int pageY = Math.max(0, (int)(rawY - loc[1] - size[1] / 2f));

            widgetPages.addWidgetToCurrentPage(hostView, pageX, pageY);
            widgetConfig.addWidget(appWidgetId,
                    widgetPages.getCurrentPage(), pageX, pageY);
            updateWidgetOptions(appWidgetId, size[0], size[1]);
        } catch (Exception e) {
            showToast(getString(R.string.widgets_not_supported));
        }
    }
}

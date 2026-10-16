package com.launcher.llara.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.launcher.llara.R;
import com.launcher.llara.util.BitmapUtils;
import com.launcher.llara.util.IconStyle;
import com.launcher.llara.util.SettingsManager;
import com.launcher.llara.view.ModernSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class IconEditorActivity extends Activity {

    private static final String TAG = "IconEditor";
    private static final String[] SHAPE_NAMES = {"Quadrado", "Arredondado", "Círculo", "Gota",
            "Esquilo", "Flor", "Trevo", "Ondulado", "Sol", "Cápsula",
            "Diamante", "Triângulo", "Pentágono", "Folha esq.", "Folha dir.",
            "Personalizado"};

    private SettingsManager settings;
    private final Random random = new Random();

    private String surface = SettingsManager.SURF_DRAWER;
    private int shape, wsDp, labelSp;
    private int glyphPct = 70, platePct = 100;
    private int[] corners = {30, 30, 30, 30};
    private Drawable previewIcon;
    private Bitmap lastMasked;
    private Bitmap previewOrig;
    private String previewName = "";
    private String previewPkg;
    private int dragCorner = -1;
    private float dragStartY;
    private int dragStartPct;

    private ImageView previewIv;
    private TextView previewTv;
    private TextView lblShape, lblWs, lblLabel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_icon_editor);

        settings = new SettingsManager(this);
        surface = SettingsManager.SURF_DRAWER;
        loadSurface(surface);

        previewIv = findViewById(R.id.preview_icon);
        previewTv = findViewById(R.id.preview_label);
        lblShape = findViewById(R.id.lbl_shape);
        lblWs = findViewById(R.id.lbl_ws);
        lblLabel = findViewById(R.id.lbl_label);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        android.view.View stage = findViewById(R.id.preview_stage);
        final float cornerGrab = 64 * getResources().getDisplayMetrics().density;
        stage.setOnTouchListener((v, e) -> {
            float[] rect = iconRectInStage();
            if (rect == null) return false;
            float ix = rect[0], iy = rect[1], iw = rect[2], ih = rect[3];
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: {
                    // Canto do ícone mais próximo do dedo.
                    float[] cxs = {ix, ix + iw, ix + iw, ix};
                    float[] cys = {iy, iy, iy + ih, iy + ih};
                    int best = -1;
                    float bestD2 = cornerGrab * cornerGrab;
                    for (int i = 0; i < 4; i++) {
                        float ddx = e.getX() - cxs[i];
                        float ddy = e.getY() - cys[i];
                        float d2 = ddx * ddx + ddy * ddy;
                        if (d2 < bestD2) { bestD2 = d2; best = i; }
                    }
                    if (best < 0) return false;
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    dragCorner = best;
                    dragStartY = e.getY();
                    dragStartPct = corners[dragCorner];
                    highlightCornerPill(dragCorner, true);
                    if (shape != com.launcher.llara.util.IconShape.CUSTOM) {
                        shape = com.launcher.llara.util.IconShape.CUSTOM;
                    }
                    return true;
                }
                case android.view.MotionEvent.ACTION_MOVE:
                    if (dragCorner < 0) return false;
                    int h = v.getHeight() > 0 ? v.getHeight() : 1;
                    int pct = Math.max(0, Math.min(100,
                            (int) (dragStartPct + (dragStartY - e.getY()) / h * 100)));
                    corners[dragCorner] = pct;
                    refresh();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    highlightCornerPill(dragCorner, false);
                    dragCorner = -1;
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return false;
        });

        pickRandomApp();
        setupTopics();
        setupControls();
        refresh();
    }

    private void loadSurface(String s) {
        shape = settings.getIconShape(s);
        labelSp = settings.getLabelSize(s);
        corners = settings.getCorners(s);
        glyphPct = settings.getGlyphPct(s);
        platePct = settings.getPlatePct(s);
        wsDp = settings.getWsIconDp();
    }

    private void saveSurface(String s) {
        settings.setIconShape(s, shape);
        settings.setCorners(s, corners[0], corners[1], corners[2], corners[3]);
        settings.setLabelSize(s, labelSp);
        settings.setGlyphPct(s, glyphPct);
        settings.setPlatePct(s, platePct);
        settings.setWsIconDp(wsDp);
    }

    private void setupTopics() {
        android.widget.Switch sw = findViewById(R.id.switch_unify);
        sw.setChecked(settings.getUnifyIcons());
        sw.setOnCheckedChangeListener((b, v) -> {
            saveSurface(surface);
            settings.setUnifyIcons(v);
            if (v && !SettingsManager.SURF_DRAWER.equals(surface)) {
                surface = SettingsManager.SURF_DRAWER;
                loadSurface(surface);
            }
            com.launcher.llara.util.BitmapCache.getInstance().evictAll();
            updateTopics();
            refresh();
        });
        findViewById(R.id.btn_topic_drawer).setOnClickListener(v -> switchSurface(SettingsManager.SURF_DRAWER));
        findViewById(R.id.btn_topic_dock).setOnClickListener(v -> switchSurface(SettingsManager.SURF_DOCK));
        findViewById(R.id.btn_topic_ws).setOnClickListener(v -> switchSurface(SettingsManager.SURF_WS));
        updateTopics();
    }

    private void switchSurface(String s) {
        if (s.equals(surface)) return;
        saveSurface(surface);
        surface = s;
        loadSurface(surface);
        updateTopics();
        refresh();
    }

    private void updateTopics() {
        boolean unify = settings.getUnifyIcons();
        android.view.View tabs = findViewById(R.id.topic_tabs);
        if (tabs != null) tabs.setVisibility(unify ? android.view.View.GONE : android.view.View.VISIBLE);
        android.view.View sub = findViewById(R.id.lbl_unify_sub);
        if (sub != null) sub.setVisibility(unify ? android.view.View.VISIBLE : android.view.View.GONE);
        android.view.View note = findViewById(R.id.lbl_topic_note);
        if (note != null) note.setVisibility(unify ? android.view.View.VISIBLE : android.view.View.GONE);
        android.view.View wsRow = findViewById(R.id.row_ws_size);
        if (wsRow != null) wsRow.setVisibility(
            SettingsManager.SURF_WS.equals(surface) ? android.view.View.VISIBLE : android.view.View.GONE);
        paintTopicTab(R.id.btn_topic_drawer, SettingsManager.SURF_DRAWER.equals(surface));
        paintTopicTab(R.id.btn_topic_dock, SettingsManager.SURF_DOCK.equals(surface));
        paintTopicTab(R.id.btn_topic_ws, SettingsManager.SURF_WS.equals(surface));
    }

    private void paintTopicTab(int id, boolean active) {
        android.view.View v = findViewById(id);
        if (!(v instanceof android.widget.Button)) return;
        android.widget.Button b = (android.widget.Button) v;
        b.setTextColor(active ? 0xFF00D4FF : 0xFFFFFFFF);
        b.setTypeface(null, active ? android.graphics.Typeface.BOLD
            : android.graphics.Typeface.NORMAL);
    }

    private void pickRandomApp() {
        try {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
            List<ResolveInfo> mine = new ArrayList<>();
            String self = getPackageName();
            for (ResolveInfo ri : ris) {
                if (!ri.activityInfo.packageName.equals(self)) mine.add(ri);
            }
            if (mine.isEmpty()) return;
            ResolveInfo ri = mine.get(random.nextInt(mine.size()));
            int target = (int) (96 * getResources().getDisplayMetrics().density);
            Bitmap raw = BitmapUtils.loadIconScaled(ri, pm, target);
            previewPkg = ri.activityInfo.packageName;
            if (previewOrig != null && !previewOrig.isRecycled()) previewOrig.recycle();
            previewOrig = raw;
            if (raw != null) IconStyle.rememberEdge(previewPkg, raw);
            previewIcon = remaskPreview();
            previewName = ri.loadLabel(pm).toString();
        } catch (Exception e) {
            android.util.Log.w(TAG, "random app failed", e);
        }
    }

    private void setupControls() {
        findViewById(R.id.btn_shape).setOnClickListener(v -> {
            float density = getResources().getDisplayMetrics().density;
            int box = (int) (64 * density);
            ModernSheet.showGrid(this, "Formato", com.launcher.llara.util.IconShape.COUNT, shape,
                (idx, chk) -> {
                    android.widget.LinearLayout outer = new android.widget.LinearLayout(this);
                    outer.setOrientation(android.widget.LinearLayout.VERTICAL);
                    outer.setGravity(android.view.Gravity.CENTER);
                    outer.setMinimumHeight((int) (72 * density));
                    android.widget.FrameLayout f = new android.widget.FrameLayout(this);
                    com.launcher.llara.util.IconStyle.ShapePlateDrawable bg =
                            new com.launcher.llara.util.IconStyle.ShapePlateDrawable(idx, 0xFF8A8A8A);
                    f.setBackground(bg);
                    if (android.os.Build.VERSION.SDK_INT >= 23) {
                        f.setForeground(getSelectableBg());
                    }
                    outer.addView(f, new android.widget.LinearLayout.LayoutParams(box, box));
                    if (chk) {
                        TextView tick = new TextView(this);
                        tick.setText("✓");
                        tick.setTextColor(0xFF00D4FF);
                        tick.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
                        tick.setTypeface(null, android.graphics.Typeface.BOLD);
                        tick.setGravity(android.view.Gravity.CENTER);
                        android.widget.LinearLayout.LayoutParams tlp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        tlp.topMargin = (int) (2 * density);
                        outer.addView(tick, tlp);
                    }
                    return outer;
                }, w -> {
                    shape = w;
                    refresh();
                });
        });

        Button wsMinus = findViewById(R.id.btn_ws_minus);
        Button wsPlus = findViewById(R.id.btn_ws_plus);
        wsMinus.setOnClickListener(v -> {
            if (wsDp > 24) wsDp -= 4;
            else if (wsDp == 24) wsDp = 0;
            refresh();
        });
        wsPlus.setOnClickListener(v -> {
            if (wsDp == 0) wsDp = 24;
            else if (wsDp < 96) wsDp += 4;
            refresh();
        });

        Button lbMinus = findViewById(R.id.btn_label_minus);
        Button lbPlus = findViewById(R.id.btn_label_plus);
        lbMinus.setOnClickListener(v -> {
            if (labelSp > 8) { labelSp--; refresh(); }
        });
        lbPlus.setOnClickListener(v -> {
            if (labelSp < 16) { labelSp++; refresh(); }
        });

        Button glMinus = findViewById(R.id.btn_glyph_minus);
        Button glPlus = findViewById(R.id.btn_glyph_plus);
        glMinus.setOnClickListener(v -> {
            if (glyphPct > 40) { glyphPct -= 5; refresh(); }
        });
        glPlus.setOnClickListener(v -> {
            if (glyphPct < 100) { glyphPct += 5; refresh(); }
        });

        Button plMinus = findViewById(R.id.btn_plate_minus);
        Button plPlus = findViewById(R.id.btn_plate_plus);
        plMinus.setOnClickListener(v -> {
            if (platePct > 60) { platePct -= 5; refresh(); }
        });
        plPlus.setOnClickListener(v -> {
            if (platePct < 100) { platePct += 5; refresh(); }
        });

        findViewById(R.id.btn_shuffle).setOnClickListener(v -> {
            pickRandomApp();
            refresh();
        });
        findViewById(R.id.btn_save).setOnClickListener(v -> saveAndFinish());
    }

    private android.graphics.drawable.Drawable getSelectableBg() {
        try {
            android.util.TypedValue t = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, t, true);
            return getResources().getDrawable(t.resourceId, getTheme());
        } catch (Exception e) {
            return null;
        }
    }

    private Drawable remaskPreview() {
        if (previewOrig == null || previewOrig.isRecycled()) return previewIcon;
        try {
            Bitmap copy = previewOrig.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) return previewIcon;
            int[] cn = shape == com.launcher.llara.util.IconShape.CUSTOM ? corners : null;
            Bitmap masked = com.launcher.llara.util.IconShape.mask(copy, shape, cn);
            if (lastMasked != null && !lastMasked.isRecycled() && lastMasked != masked) {
                lastMasked.recycle();
            }
            lastMasked = (masked != null && !masked.isRecycled()) ? masked : null;
            return new BitmapDrawable(getResources(), masked);
        } catch (Exception e) {
            return previewIcon;
        }
    }

    /** Retângulo do ícone do preview em coordenadas do stage: [x, y, w, h]. */
    private float[] iconRectInStage() {
        try {
            android.view.View stage = findViewById(R.id.preview_stage);
            if (stage == null || previewIv == null) return null;
            int[] sLoc = new int[2];
            int[] iLoc = new int[2];
            stage.getLocationOnScreen(sLoc);
            previewIv.getLocationOnScreen(iLoc);
            float x = iLoc[0] - sLoc[0];
            float y = iLoc[1] - sLoc[1];
            float w = previewIv.getWidth();
            float h = previewIv.getHeight();
            if (w <= 0 || h <= 0) return null;
            return new float[]{x, y, w, h};
        } catch (Exception e) {
            return null;
        }
    }

    /** Acende a pílula do canto arrastado como feedback (sem bolinhas). */
    private void highlightCornerPill(int corner, boolean on) {
        try {
            int id;
            switch (corner) {
                case 0: id = R.id.pill_tl; break;
                case 1: id = R.id.pill_tr; break;
                case 2: id = R.id.pill_br; break;
                case 3: id = R.id.pill_bl; break;
                default: return;
            }
            android.view.View pill = findViewById(id);
            if (pill == null) return;
            if (on) {
                float density = getResources().getDisplayMetrics().density;
                android.graphics.drawable.GradientDrawable gd =
                    new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                gd.setCornerRadius(8 * density);
                gd.setColor(0xFF00D4FF);
                pill.setBackground(gd);
                ((TextView) pill).setTextColor(0xFF121212);
            } else {
                pill.setBackgroundResource(R.drawable.bg_pill);
                ((TextView) pill).setTextColor(0xFF00D4FF);
            }
        } catch (Exception ignored) { }
    }

    private void refresh() {
        boolean custom = shape == com.launcher.llara.util.IconShape.CUSTOM;
        lblShape.setText(custom ? "Personalizado"
                : SHAPE_NAMES[Math.max(0, Math.min(SHAPE_NAMES.length - 1, shape))]);
        lblWs.setText(wsDp <= 0 ? "Auto" : wsDp + "dp");
        lblLabel.setText(labelSp + "sp");
        ((TextView) findViewById(R.id.lbl_glyph)).setText(glyphPct + "%");
        ((TextView) findViewById(R.id.lbl_plate)).setText(platePct + "%");

        android.view.View pills = findViewById(R.id.corner_pills);
        if (pills != null) {
            pills.setVisibility(custom ? android.view.View.VISIBLE : android.view.View.GONE);
            if (custom) {
                ((TextView) findViewById(R.id.pill_tl)).setText(String.valueOf(corners[0]));
                ((TextView) findViewById(R.id.pill_tr)).setText(String.valueOf(corners[1]));
                ((TextView) findViewById(R.id.pill_br)).setText(String.valueOf(corners[2]));
                ((TextView) findViewById(R.id.pill_bl)).setText(String.valueOf(corners[3]));
            }
        }

        int px = wsDp > 0
                ? (int) (wsDp * getResources().getDisplayMetrics().density)
                : settings.getIconSizePx(this);
        android.view.ViewGroup.LayoutParams lp = previewIv.getLayoutParams();
        if (lp.width != px) { lp.width = px; lp.height = px; previewIv.setLayoutParams(lp); }
        previewIcon = remaskPreview();
        if (previewIcon != null) previewIv.setImageDrawable(previewIcon);
        int[] cn = custom ? corners : null;
        IconStyle.applyPlate(previewIv, shape, px, IconStyle.plateColor(previewPkg), cn,
                glyphPct, platePct);
        previewTv.setText(previewName);
        previewTv.setTextSize(labelSp);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ModernSheet.dismissAll();
        if (isFinishing()) saveValues();
    }

    private void saveValues() {
        String before = settings.getStyleKey(surface);
        saveSurface(surface);
        if (!before.equals(settings.getStyleKey(surface))) {
            com.launcher.llara.util.BitmapCache.getInstance().evictAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (previewOrig != null && !previewOrig.isRecycled()) previewOrig.recycle();
        previewOrig = null;
        if (lastMasked != null && !lastMasked.isRecycled()) lastMasked.recycle();
        lastMasked = null;
    }

    private void saveAndFinish() {
        saveValues();
        if (!isFinishing()) finish();
    }
}

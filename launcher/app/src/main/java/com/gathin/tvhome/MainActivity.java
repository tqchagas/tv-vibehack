package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.io.IOException;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private TvInputManager tvInputs;
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputAdded(String id) { adapter.reload(); }
        @Override public void onInputRemoved(String id) { adapter.reload(); }
        @Override public void onInputUpdated(String id) { adapter.reload(); }
        @Override public void onTvInputInfoUpdated(TvInputInfo info) { adapter.reload(); }
        @Override public void onInputStateChanged(String id, int state) { adapter.reload(); }
    };

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private Bitmap installerWallpaperBitmap;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnWifi;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;
    private boolean artMode;
    private int consumedArtKey = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        selectInstallerWallpaperFrom(getIntent());

        root = findViewById(R.id.root);
        root.setFocusableInTouchMode(true);
        applyWallpaper();
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnWallpaper = findViewById(R.id.btnWallpaper);
        btnWallpaper.setOnClickListener(v -> showWallpaperPicker());
        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getRepeatCount() == 0) setArtMode(true, true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnWallpaper) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnWallpaper.setOnKeyListener(pillKeys);
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);
        tvInputs = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tvInputs != null) tvInputs.registerCallback(inputCallback, handler);
        adapter.reload();
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the tile we left from, or the first one.
        grid.post(() -> {
            View focused = getCurrentFocus();
            if (focused != null && grid.findContainingViewHolder(focused) != null) return;
            focusTile(lastFocused);
        });
    }

    @Override
    protected void onPause() {
        if (artMode) setArtMode(false, false);
        consumedArtKey = -1;
        super.onPause();
        handler.removeCallbacks(minuteTick);
        if (tvInputs != null) tvInputs.unregisterCallback(inputCallback);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (selectInstallerWallpaperFrom(intent)) applyWallpaper();
    }

    private boolean selectInstallerWallpaperFrom(Intent intent) {
        if (intent == null || !intent.getBooleanExtra("select_installer_wallpaper", false)
                || installerWallpaperResource() == 0) return false;
        getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", INSTALLER_WALLPAPER).apply();
        intent.removeExtra("select_installer_wallpaper");
        return true;
    }

    @Override
    public void onBackPressed() {
        if (artMode) {
            setArtMode(false, true);
            focusTile(lastFocused);
        }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // transparent inset around the selection outline
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(9f * density * AppAdapter.FOCUS_SCALE);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        // All rows stay in place; scroll only when a focused tile leaves the viewport.
        Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
        grid.requestChildRectangleOnScreen(item, want, false);
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            heroName.setY(y);
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                setArtMode(true, true);
                return true;
            }
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    @Override
    public boolean onTileHorizontalKey(int position, int keyCode) {
        int column = position % COLUMNS;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (column == 0) return true;
            focusTile(position - 1);
        } else {
            if (column == COLUMNS - 1 || position + 1 >= adapter.getItemCount()) return true;
            focusTile(position + 1);
        }
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        // Wait for the target row to attach. A scroll listener can miss the
        // SCROLL_STATE_IDLE transition when the requested item is already in view.
        RecyclerView.OnChildAttachStateChangeListener attachListener = new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View view) {
                if (grid.getChildAdapterPosition(view) != pos) return;
                grid.removeOnChildAttachStateChangeListener(this);
                view.findViewById(R.id.card).requestFocus();
            }

            @Override public void onChildViewDetachedFromWindow(@NonNull View view) {}
        };
        grid.addOnChildAttachStateChangeListener(attachListener);
        grid.scrollToPosition(pos);
        grid.post(() -> {
            RecyclerView.ViewHolder attached = grid.findViewHolderForAdapterPosition(pos);
            if (attached == null) return;
            grid.removeOnChildAttachStateChangeListener(attachListener);
            attached.itemView.findViewById(R.id.card).requestFocus();
        });
    }

    /** A short transition only: the artwork has no animation or frame loop. */
    private void setArtMode(boolean show, boolean animate) {
        artMode = show;
        grid.stopScroll();
        View topBar = findViewById(R.id.topBar);
        View shade = findViewById(R.id.wallpaperShade);
        grid.animate().cancel();
        topBar.animate().cancel();
        shade.animate().cancel();
        grid.setDescendantFocusability(show ? ViewGroup.FOCUS_BLOCK_DESCENDANTS
                : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        ((ViewGroup) topBar).setDescendantFocusability(show ? ViewGroup.FOCUS_BLOCK_DESCENDANTS
                : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        grid.setImportantForAccessibility(show ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        topBar.setImportantForAccessibility(show ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (show) {
            root.requestFocus();
            hideGlass();
        }
        // Leave a small strip of the dock visible at the bottom as a return affordance.
        float offset = show ? Math.max(0, root.getHeight() - grid.getTop()
                - ((View) grid.getParent()).getTop() - 64 * density) : 0;
        if (animate) {
            grid.animate().translationY(offset).setDuration(220).start();
            topBar.animate().alpha(show ? 0f : 1f).setDuration(180).start();
            shade.animate().alpha(show ? 0f : 1f).setDuration(180).start();
        } else {
            grid.setTranslationY(offset);
            topBar.setAlpha(show ? 0f : 1f);
            shade.setAlpha(show ? 0f : 1f);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            String shortcutPackage = shortcutPackageForKey(key, event.getScanCode());
            if (shortcutPackage != null) {
                setArtMode(false, true);
                openShortcut(shortcutPackage);
                return true;
            }
            // Channel +/- buttons should work from the launcher too. Opening the tuner
            // keeps them useful even when the home screen currently has focus.
            if (key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                setArtMode(false, true);
                openLiveTv();
                return true;
            }
        }
        // Many TV remotes send KEYCODE_TV for their dedicated Live TV button.
        // The launcher must handle it explicitly; otherwise it is swallowed here
        // instead of reaching TCL's TV app (which may restore the last HDMI input).
        if ((key == KeyEvent.KEYCODE_TV || key == KeyEvent.KEYCODE_TV_INPUT
                || key == KeyEvent.KEYCODE_GUIDE || event.getScanCode() == 365)
                && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            setArtMode(false, true);
            openLiveTv();
            return true;
        }
        if (key == consumedArtKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) consumedArtKey = -1;
            return true;
        }
        if (artMode && (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_BACK)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) {
                consumedArtKey = key;
                setArtMode(false, true);
                if (key == KeyEvent.KEYCODE_DPAD_UP) findViewById(R.id.btnWallpaper).requestFocus();
                else focusTile(lastFocused);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** TCL exposes several dedicated remote buttons as vendor scan codes on this C6K. */
    private String shortcutPackageForKey(int key, int scanCode) {
        // Observed on the physical IR receiver; the translated Android key codes are
        // vendor-specific and differ between firmware builds, while scan codes remain stable.
        if (scanCode == 566) return "com.netflix.ninja";
        if (scanCode == 752) return "com.amazon.amazonvideo.livingroom";
        if (scanCode == 569) return "com.google.android.youtube.tv";
        switch (key) {
            case KeyEvent.KEYCODE_TV_SATELLITE_CS: return "com.google.android.youtube.tv";
            case KeyEvent.KEYCODE_F1: return "com.netflix.ninja";
            case KeyEvent.KEYCODE_F2: return "com.amazon.amazonvideo.livingroom";
            case KeyEvent.KEYCODE_F3: return "com.google.android.youtube.tv";
            case KeyEvent.KEYCODE_F4: return "com.globo.globotv";
            case KeyEvent.KEYCODE_F5: return "com.tcl.channelplus";
            case KeyEvent.KEYCODE_F6: return "com.disney.disneyplus";
            default: return null;
        }
    }

    private void openShortcut(String packageName) {
        Intent intent;
        if ("com.tcl.channelplus".equals(packageName)) {
            // TCL Channel is a deep-link app on this firmware, without a launcher activity.
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("app://com.tcl.iptv/tune"));
            intent.setPackage(packageName);
        } else {
            intent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        }
        if (intent == null) {
            Toast.makeText(this, getString(R.string.shortcut_app_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.shortcut_app_unavailable), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        Calendar now = Calendar.getInstance();
        String date = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.ENGLISH).format(now.getTime());
        ((TextView) findViewById(R.id.currentDate)).setText(date);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    private static final int[] WALLPAPERS = {0, R.drawable.wallpaper_wave, R.drawable.wallpaper_van_gogh};
    private static final int INSTALLER_WALLPAPER = WALLPAPERS.length;
    private static final int WALLPAPER_MAX_WIDTH = 1920;
    private static final int WALLPAPER_MAX_HEIGHT = 1080;

    private int installerWallpaperResource() {
        return getResources().getIdentifier("installer_wallpaper", "drawable", getPackageName());
    }

    private int wallpaperChoice() {
        int choice = getPreferences(MODE_PRIVATE).getInt("wallpaper", 1);
        if (choice > 0 && choice < WALLPAPERS.length) return choice;
        if (choice == INSTALLER_WALLPAPER && installerWallpaperResource() != 0) return choice;
        getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", 1).apply();
        return 1;
    }

    private void applyWallpaper() {
        int choice = wallpaperChoice();
        ImageView wallpaper = findViewById(R.id.wallpaper);
        if (choice == INSTALLER_WALLPAPER) {
            try {
                if (installerWallpaperBitmap == null) installerWallpaperBitmap = decodeInstallerWallpaper();
                wallpaper.setImageBitmap(installerWallpaperBitmap);
            } catch (IOException | RuntimeException e) {
                wallpaper.setImageResource(WALLPAPERS[1]);
            }
        } else wallpaper.setImageResource(WALLPAPERS[choice]);
        wallpaper.setVisibility(choice == 0 ? View.GONE : View.VISIBLE);
        View shade = findViewById(R.id.wallpaperShade);
        shade.setVisibility(choice == 0 ? View.GONE : View.VISIBLE);
    }

    private Bitmap decodeInstallerWallpaper() throws IOException {
        int resource = installerWallpaperResource();
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source = ImageDecoder.createSource(getResources(), resource);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                float scale = Math.min(1f, Math.min(WALLPAPER_MAX_WIDTH / (float) width,
                        WALLPAPER_MAX_HEIGHT / (float) height));
                decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), resource, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > WALLPAPER_MAX_WIDTH
                || bounds.outHeight / sample > WALLPAPER_MAX_HEIGHT) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resource, options);
        if (bitmap == null) throw new IOException("Could not decode installer wallpaper");
        return bitmap;
    }

    private void showWallpaperPicker() {
        String[] bundled = getResources().getStringArray(R.array.wallpaper_choices);
        boolean hasInstallerWallpaper = installerWallpaperResource() != 0;
        String[] choices = new String[bundled.length + (hasInstallerWallpaper ? 1 : 0)];
        System.arraycopy(bundled, 0, choices, 0, bundled.length);
        if (hasInstallerWallpaper) choices[bundled.length] = getString(R.string.installed_custom_wallpaper);
        int selected = wallpaperChoice() - 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_title)
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", which + 1).apply();
                    applyWallpaper();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLiveTv() {
        if (!LiveTv.open(this)) {
            Toast.makeText(this, R.string.no_live_tuner, Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Leave room for the source name and translated connection status side by side.
        int width = Math.max(anchor.getWidth(), (int) (260 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? "" : l.toString().trim();
                if (label.isEmpty()) label = getString(R.string.unnamed_input);
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        String tunerInputId = LiveTv.findTunerInputId(this);
        Intent liveTv = tunerInputId == null && !liveTvPkg.isEmpty()
                ? getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg) : null;
        if (tunerInputId != null || liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1,
                    () -> {
                        if (tunerInputId != null) {
                            LiveTv.open(this);
                        } else {
                            open(liveTv);
                        }
                    });
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}

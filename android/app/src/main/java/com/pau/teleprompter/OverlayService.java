package com.pau.teleprompter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private boolean isMinimized = false;
    private static final String CHANNEL_ID = "tp_overlay";

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        // Foreground notification
        Notification notification = buildNotification();
        startForeground(1, notification);

        String text = intent.getStringExtra("text");
        int size = intent.getIntExtra("size", 1);
        int opacity = intent.getIntExtra("opacity", 70);

        if (overlayView != null) {
            windowManager.removeView(overlayView);
        }

        createOverlay(text, size, opacity);
        return START_NOT_STICKY;
    }

    private void createOverlay(String text, int sizeIndex, int opacity) {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Tamaños de texto: S=16, M=20, L=26, XL=32
        float[] textSizes = {16f, 20f, 26f, 32f};
        float textSize = textSizes[Math.min(sizeIndex, 3)];

        // Color de fondo con opacidad
        int bgAlpha = (int)(opacity * 2.55f);
        int bgColor = Color.argb(bgAlpha, 0, 0, 0);

        // Root: FrameLayout
        FrameLayout root = new FrameLayout(this);

        // ScrollView con el contenido
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(bgColor);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(24);
        contentLayout.setPadding(pad, dpToPx(20), pad, dpToPx(20));

        // Spacer top
        Space topSpacer = new Space(this);
        topSpacer.setMinimumHeight(dpToPx(120));
        contentLayout.addView(topSpacer);

        // Parse y renderizar texto
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            // Corte
            if (trimmed.equals("-- CORTE --") || trimmed.equals("--CORTE--") || trimmed.equalsIgnoreCase("corte")) {
                LinearLayout corteRow = new LinearLayout(this);
                corteRow.setOrientation(LinearLayout.HORIZONTAL);
                corteRow.setGravity(Gravity.CENTER_VERTICAL);
                corteRow.setPadding(0, dpToPx(6), 0, dpToPx(6));

                View line1 = new View(this);
                line1.setBackgroundColor(Color.argb(77, 255, 255, 255)); // white/30
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, dpToPx(1), 1f);
                corteRow.addView(line1, lineParams);

                TextView corteLabel = new TextView(this);
                corteLabel.setText("CORTE");
                corteLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                corteLabel.setTextColor(Color.argb(128, 255, 255, 255)); // white/50
                corteLabel.setLetterSpacing(0.15f);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                labelParams.setMargins(dpToPx(8), 0, dpToPx(8), 0);
                corteRow.addView(corteLabel, labelParams);

                View line2 = new View(this);
                line2.setBackgroundColor(Color.argb(77, 255, 255, 255));
                corteRow.addView(line2, new LinearLayout.LayoutParams(0, dpToPx(1), 1f));

                LinearLayout.LayoutParams corteParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                corteParams.bottomMargin = dpToPx(16);
                contentLayout.addView(corteRow, corteParams);
                continue;
            }

            // Bloque con label
            String blockLabel = null;
            String blockText = trimmed;
            String[] labels = {"HOOK", "DICE", "CTA", "INTRO", "CIERRE", "PREGUNTA", "DATO", "EJEMPLO", "TRANSICION"};
            for (String lbl : labels) {
                if (trimmed.toUpperCase().startsWith(lbl + ":") || trimmed.toUpperCase().startsWith(lbl + " ")) {
                    blockLabel = lbl;
                    blockText = trimmed.substring(lbl.length()).replaceFirst("^:\\s*", "").replaceFirst("^\\s+", "");
                    break;
                }
            }

            if (trimmed.isEmpty()) {
                Space spacer = new Space(this);
                spacer.setMinimumHeight(dpToPx(8));
                contentLayout.addView(spacer);
                continue;
            }

            LinearLayout blockLayout = new LinearLayout(this);
            blockLayout.setOrientation(LinearLayout.VERTICAL);

            if (blockLabel != null) {
                TextView labelView = new TextView(this);
                labelView.setText(blockLabel);
                labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                labelView.setTextColor(Color.argb(128, 255, 255, 255)); // white/50
                labelView.setTypeface(Typeface.DEFAULT_BOLD);
                labelView.setAllCaps(true);
                blockLayout.addView(labelView);
            }

            TextView textView = new TextView(this);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
            textView.setTextColor(Color.WHITE);
            textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textView.setLineSpacing(0, 1.5f);

            // Parse bold **text**
            SpannableString spannable = parseBold(blockText);
            textView.setText(spannable);

            blockLayout.addView(textView);

            // Nota dirección: (texto entre paréntesis)
            // Handled inline - skip for simplicity

            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blockParams.bottomMargin = dpToPx(16);
            contentLayout.addView(blockLayout, blockParams);
        }

        // Spacer bottom
        Space bottomSpacer = new Space(this);
        bottomSpacer.setMinimumHeight(dpToPx(300));
        contentLayout.addView(bottomSpacer);

        scrollView.addView(contentLayout);

        // Bottom bar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(Color.argb(217, 0, 0, 0)); // 85%
        int barPad = dpToPx(8);
        bottomBar.setPadding(barPad, barPad, barPad, barPad);

        // Scroll arriba
        bottomBar.addView(createBarButton("▲", v -> scrollView.smoothScrollBy(0, -dpToPx(200))));
        bottomBar.addView(createBarSpacer());
        // Scroll abajo
        bottomBar.addView(createBarButton("▼", v -> scrollView.smoothScrollBy(0, dpToPx(200))));
        bottomBar.addView(createBarSpacer());
        // Minimizar
        bottomBar.addView(createBarButton("━", v -> toggleMinimize(scrollView, root)));
        bottomBar.addView(createBarSpacer());
        // Cerrar
        bottomBar.addView(createBarButton("✕", v -> {
            stopSelf();
        }));

        // Add to root
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        scrollParams.bottomMargin = dpToPx(60);
        root.addView(scrollView, scrollParams);

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(60));
        barParams.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, barParams);

        // Window params - overlay tipo TYPE_APPLICATION_OVERLAY
        // FLAG_NOT_TOUCH_MODAL: permite tocar fuera del overlay
        // FLAG_LAYOUT_IN_SCREEN: usa toda la pantalla
        // FLAG_SECURE: NO aparece en screen recording/screenshots
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        overlayView = root;
        windowManager.addView(overlayView, params);
    }

    private View createBarButton(String label, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btn.setTextColor(Color.argb(179, 255, 255, 255)); // white/70
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundColor(Color.argb(26, 255, 255, 255)); // white/10
        btn.setMinimumWidth(dpToPx(44));
        btn.setMinimumHeight(dpToPx(44));
        btn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        btn.setOnClickListener(listener);

        // Make clickable through overlay
        btn.setClickable(true);
        btn.setFocusable(false);

        return btn;
    }

    private View createBarSpacer() {
        Space s = new Space(this);
        s.setMinimumWidth(dpToPx(12));
        return s;
    }

    private void toggleMinimize(ScrollView scrollView, FrameLayout root) {
        isMinimized = !isMinimized;
        scrollView.setVisibility(isMinimized ? View.GONE : View.VISIBLE);

        WindowManager.LayoutParams params = (WindowManager.LayoutParams) root.getLayoutParams();
        if (isMinimized) {
            params.height = dpToPx(60);
            params.gravity = Gravity.BOTTOM | Gravity.START;
        } else {
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.TOP | Gravity.START;
        }
        windowManager.updateViewLayout(root, params);
    }

    private SpannableString parseBold(String text) {
        StringBuilder clean = new StringBuilder();
        java.util.List<int[]> boldRanges = new java.util.ArrayList<>();

        int i = 0;
        while (i < text.length()) {
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > 0) {
                    int start = clean.length();
                    clean.append(text, i + 2, end);
                    boldRanges.add(new int[]{start, clean.length()});
                    i = end + 2;
                    continue;
                }
            }
            clean.append(text.charAt(i));
            i++;
        }

        SpannableString spannable = new SpannableString(clean.toString());
        for (int[] range : boldRanges) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Teleprompter Overlay",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controla el teleprompter overlay");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Close action
        Intent closeIntent = new Intent(this, OverlayService.class);
        closeIntent.setAction("STOP");
        PendingIntent closePi = PendingIntent.getService(this, 1, closeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Teleprompter activo")
                .setContentText("Toca para abrir la app")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cerrar", closePi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception e) {}
        }
    }
}

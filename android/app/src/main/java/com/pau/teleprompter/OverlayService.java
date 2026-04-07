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
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private boolean isMinimized = false;
    private ScrollView scrollView;
    private FrameLayout root;
    private TextView toggleBtn;
    private static final String CHANNEL_ID = "tp_overlay";
    private static final int BAR_HEIGHT_DP = 44;

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

        String action = intent.getAction();
        if ("STOP".equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

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

        // Screen height for 25%
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenHeight = dm.heightPixels;
        int overlayHeight = screenHeight / 4; // 25% de pantalla

        float[] textSizes = {14f, 18f, 22f, 28f};
        float textSize = textSizes[Math.min(sizeIndex, 3)];

        int bgAlpha = (int)(opacity * 2.55f);
        int bgColor = Color.argb(bgAlpha, 0, 0, 0);

        // Root
        root = new FrameLayout(this);

        // ScrollView
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(bgColor);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        contentLayout.setPadding(pad, dpToPx(12), pad, dpToPx(8));

        // Parse y renderizar texto
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("-- CORTE --") || trimmed.equals("--CORTE--") || trimmed.equalsIgnoreCase("corte")) {
                LinearLayout corteRow = new LinearLayout(this);
                corteRow.setOrientation(LinearLayout.HORIZONTAL);
                corteRow.setGravity(Gravity.CENTER_VERTICAL);
                corteRow.setPadding(0, dpToPx(4), 0, dpToPx(4));

                View line1 = new View(this);
                line1.setBackgroundColor(Color.argb(77, 255, 255, 255));
                corteRow.addView(line1, new LinearLayout.LayoutParams(0, dpToPx(1), 1f));

                TextView corteLabel = new TextView(this);
                corteLabel.setText("CORTE");
                corteLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                corteLabel.setTextColor(Color.argb(128, 255, 255, 255));
                corteLabel.setLetterSpacing(0.15f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(dpToPx(8), 0, dpToPx(8), 0);
                corteRow.addView(corteLabel, lp);

                View line2 = new View(this);
                line2.setBackgroundColor(Color.argb(77, 255, 255, 255));
                corteRow.addView(line2, new LinearLayout.LayoutParams(0, dpToPx(1), 1f));

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.bottomMargin = dpToPx(12);
                contentLayout.addView(corteRow, cp);
                continue;
            }

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
                spacer.setMinimumHeight(dpToPx(6));
                contentLayout.addView(spacer);
                continue;
            }

            LinearLayout blockLayout = new LinearLayout(this);
            blockLayout.setOrientation(LinearLayout.VERTICAL);

            if (blockLabel != null) {
                TextView labelView = new TextView(this);
                labelView.setText(blockLabel);
                labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                labelView.setTextColor(Color.argb(128, 255, 255, 255));
                labelView.setTypeface(Typeface.DEFAULT_BOLD);
                labelView.setAllCaps(true);
                blockLayout.addView(labelView);
            }

            TextView textView = new TextView(this);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
            textView.setTextColor(Color.WHITE);
            textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textView.setLineSpacing(0, 1.4f);
            textView.setText(parseBold(blockText));
            blockLayout.addView(textView);

            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.bottomMargin = dpToPx(12);
            contentLayout.addView(blockLayout, bp);
        }

        scrollView.addView(contentLayout);

        // Bottom bar - solo toggle + X
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(Color.argb(230, 0, 0, 0)); // 90%
        int barPad = dpToPx(4);
        bottomBar.setPadding(dpToPx(16), barPad, dpToPx(16), barPad);

        // Toggle (flecha arriba = abierto)
        toggleBtn = (TextView) createBarButton("\u25B2", v -> toggleMinimize());
        bottomBar.addView(toggleBtn);
        bottomBar.addView(createBarSpacer());
        // Cerrar
        bottomBar.addView(createBarButton("\u2715", v -> stopSelf()));

        // Layout: scroll arriba, barra abajo
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        scrollParams.bottomMargin = dpToPx(BAR_HEIGHT_DP);
        root.addView(scrollView, scrollParams);

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(BAR_HEIGHT_DP));
        barParams.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, barParams);

        // Overlay: TOP, 25% pantalla, FLAG_SECURE
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btn.setTextColor(Color.argb(179, 255, 255, 255));
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundColor(Color.argb(26, 255, 255, 255));
        btn.setMinimumWidth(dpToPx(36));
        btn.setMinimumHeight(dpToPx(36));
        btn.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        btn.setOnClickListener(listener);
        btn.setClickable(true);
        btn.setFocusable(false);
        return btn;
    }

    private View createBarSpacer() {
        Space s = new Space(this);
        s.setMinimumWidth(dpToPx(12));
        return s;
    }

    private void toggleMinimize() {
        isMinimized = !isMinimized;
        scrollView.setVisibility(isMinimized ? View.GONE : View.VISIBLE);

        WindowManager.LayoutParams params = (WindowManager.LayoutParams) root.getLayoutParams();
        if (isMinimized) {
            params.height = dpToPx(BAR_HEIGHT_DP);
            toggleBtn.setText("\u25BC"); // flecha abajo = cerrado, clic para abrir
        } else {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            params.height = dm.heightPixels / 4;
            toggleBtn.setText("\u25B2"); // flecha arriba = abierto, clic para cerrar
        }
        // Siempre arriba
        params.gravity = Gravity.TOP | Gravity.START;
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

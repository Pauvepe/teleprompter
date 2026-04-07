package com.pau.teleprompter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLDecoder;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_CODE = 1234;
    private EditText textInput;
    private SeekBar sizeSeekBar;
    private SeekBar opacitySeekBar;
    private TextView sizeLabel;
    private TextView opacityLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Status bar transparente
        getWindow().setStatusBarColor(0xFF111111);
        getWindow().setNavigationBarColor(0xFF111111);

        textInput = findViewById(R.id.text_input);
        sizeSeekBar = findViewById(R.id.size_seekbar);
        opacitySeekBar = findViewById(R.id.opacity_seekbar);
        sizeLabel = findViewById(R.id.size_label);
        opacityLabel = findViewById(R.id.opacity_label);
        Button btnStart = findViewById(R.id.btn_start);

        // Cargar texto guardado
        String saved = getSharedPreferences("tp", MODE_PRIVATE).getString("text", "");
        if (!saved.isEmpty()) textInput.setText(saved);

        // Size seekbar (0-3: S,M,L,XL)
        sizeSeekBar.setMax(3);
        sizeSeekBar.setProgress(1);
        sizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            String[] labels = {"S", "M", "L", "XL"};
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sizeLabel.setText(labels[progress]);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Opacity seekbar (30-100)
        opacitySeekBar.setMax(70);
        opacitySeekBar.setProgress(40); // = 70%
        opacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityLabel.setText((progress + 30) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnStart.setOnClickListener(v -> startOverlay());

        // Deep link: crm-teleprompter://open?data=BASE64_GUION
        handleDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (!"crm-teleprompter".equals(uri.getScheme())) return;

        String dataB64 = uri.getQueryParameter("data");
        if (dataB64 == null || dataB64.isEmpty()) return;

        try {
            // Decode base64 → JSON array of guion blocks → plain text
            byte[] decoded = android.util.Base64.decode(dataB64, android.util.Base64.DEFAULT);
            String json = new String(decoded, "UTF-8");
            // Parse JSON array manually (avoid dependency on org.json for simple case)
            String plainText = guionJsonToText(json);

            textInput.setText(plainText);
            getSharedPreferences("tp", MODE_PRIVATE).edit().putString("text", plainText).apply();

            // Auto-launch overlay
            startOverlay();
        } catch (Exception e) {
            Toast.makeText(this, "Error al leer guion", Toast.LENGTH_SHORT).show();
        }
    }

    private String guionJsonToText(String json) {
        // Simple parser for guion JSON array
        // Handles: ["HOOK: text", "-- CORTE --", {"tipo":"dice","texto":"text","nota_direccion":"note"}]
        StringBuilder sb = new StringBuilder();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.get(i);
                if (item instanceof String) {
                    sb.append((String) item).append("\n");
                } else if (item instanceof org.json.JSONObject) {
                    org.json.JSONObject obj = (org.json.JSONObject) item;
                    String tipo = obj.optString("tipo", "");
                    String texto = obj.optString("texto", "");
                    if ("corte".equalsIgnoreCase(tipo)) {
                        sb.append("-- CORTE --\n");
                    } else {
                        if (!tipo.isEmpty()) sb.append(tipo.toUpperCase()).append(": ");
                        sb.append(texto).append("\n");
                    }
                    String nota = obj.optString("nota_direccion", "");
                    if (!nota.isEmpty()) sb.append("(").append(nota).append(")\n");
                }
            }
        } catch (Exception e) {
            // If not valid JSON array, use as plain text
            sb.append(json);
        }
        return sb.toString().trim();
    }

    private void startOverlay() {
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Escribe algo primero", Toast.LENGTH_SHORT).show();
            return;
        }

        // Guardar texto
        getSharedPreferences("tp", MODE_PRIVATE).edit().putString("text", text).apply();

        // Verificar permiso de overlay
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE);
            return;
        }

        launchOverlay(text);
    }

    private void launchOverlay(String text) {
        int size = sizeSeekBar.getProgress();
        int opacity = opacitySeekBar.getProgress() + 30;

        Intent intent = new Intent(this, OverlayService.class);
        intent.putExtra("text", text);
        intent.putExtra("size", size);
        intent.putExtra("opacity", opacity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        // Minimizar la app
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Settings.canDrawOverlays(this)) {
                String text = textInput.getText().toString().trim();
                if (!text.isEmpty()) launchOverlay(text);
            } else {
                Toast.makeText(this, "Necesitas dar permiso de overlay", Toast.LENGTH_LONG).show();
            }
        }
    }
}

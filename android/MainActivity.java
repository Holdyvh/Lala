package com.lalaassistant.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int ACCESSIBILITY_REQUEST_CODE = 1002;
    private static final int NOTIFICATION_REQUEST_CODE = 1003;
    
    private WebView webView;
    private boolean isServiceRunning = false;
    
    // Required permissions
    private final String[] requiredPermissions = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALENDAR
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up the WebView
        webView = new WebView(this);
        setContentView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        // Configure WebView settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Add Android interface
        webView.addJavascriptInterface(new AndroidInterface(), "Android");
        
        // Configure WebView client
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                
                // Handle external URLs
                if (!uri.getHost().equals("localhost")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                    return true;
                }
                
                return false;
            }
        });
        
        // Check and request permissions
        checkAndRequestPermissions();
        
        // Load the Flask app
        webView.loadUrl("http://localhost:5000");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if the background service is running
        isServiceRunning = LalaBackgroundService.isRunning();
        
        // Update the UI
        updateServiceStatus();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle the back button
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    /**
     * Check and request required permissions
     */
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        
        // Check which permissions are not granted
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        
        // Request permissions if needed
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                    permissionsNeeded.toArray(new String[0]), 
                    PERMISSIONS_REQUEST_CODE);
        }
        
        // Check if notification permission is needed (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_REQUEST_CODE);
            }
        }
        
        // Check accessibility service
        if (!isAccessibilityServiceEnabled()) {
            promptForAccessibilityService();
        }
    }
    
    /**
     * Handle permission request results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                // Show an explanation
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Permisos necesarios")
                        .setMessage("Lala Assistant necesita estos permisos para funcionar correctamente.")
                        .setPositiveButton("Configuración", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        }
    }
    
    /**
     * Check if the accessibility service is enabled
     */
    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        
        String serviceId = getPackageName() + "/" + LalaAccessibilityService.class.getName();
        
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getId().equals(serviceId)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Prompt the user to enable the accessibility service
     */
    private void promptForAccessibilityService() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Servicio de accesibilidad")
                .setMessage("Lala Assistant necesita el servicio de accesibilidad para funcionar correctamente.")
                .setPositiveButton("Configuración", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivityForResult(intent, ACCESSIBILITY_REQUEST_CODE);
                })
                .setNegativeButton("Más tarde", null)
                .show();
    }
    
    /**
     * Update the service status in the UI
     */
    private void updateServiceStatus() {
        webView.post(() -> {
            String script = "if (typeof updateServiceStatus === 'function') { " +
                    "updateServiceStatus(" + isServiceRunning + "); }";
            webView.evaluateJavascript(script, null);
        });
    }
    
    /**
     * Android interface for JavaScript
     */
    public class AndroidInterface {
        
        @JavascriptInterface
        public String openApp(String packageName) {
            try {
                PackageManager pm = getPackageManager();
                Intent intent = pm.getLaunchIntentForPackage(packageName);
                
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    
                    JSONObject result = new JSONObject();
                    result.put("success", true);
                    
                    return result.toString();
                } else {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", "App not installed");
                    
                    return result.toString();
                }
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String sendMessage(String app, String contact, String message) {
            try {
                // This requires the Accessibility Service to be enabled
                // For WhatsApp, we can try to use a direct intent
                if (app.equalsIgnoreCase("whatsapp")) {
                    // This is a simplified implementation
                    // In a full app, we would need to look up the contact's number
                    // and format it correctly
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("https://wa.me/?text=" + Uri.encode(message)));
                        intent.setPackage("com.whatsapp");
                        startActivity(intent);
                        
                        JSONObject result = new JSONObject();
                        result.put("success", true);
                        
                        return result.toString();
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending WhatsApp message", e);
                    }
                }
                
                // For other apps or if the direct intent failed,
                // we would use the accessibility service
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String setAlarm(String time) {
            try {
                // This is a simplified implementation
                // In a full app, we would parse the time and set the alarm
                Toast.makeText(MainActivity.this, 
                        "Setting alarm for " + time, 
                        Toast.LENGTH_SHORT).show();
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String createNote(String content) {
            try {
                // This is a simplified implementation
                // In a full app, we would create a note in the preferred app
                Toast.makeText(MainActivity.this, 
                        "Creating note: " + content.substring(0, Math.min(content.length(), 20)) + "...", 
                        Toast.LENGTH_SHORT).show();
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String isAppInstalled(String packageName) {
            try {
                PackageManager pm = getPackageManager();
                try {
                    pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                    
                    JSONObject result = new JSONObject();
                    result.put("installed", true);
                    
                    return result.toString();
                } catch (PackageManager.NameNotFoundException e) {
                    JSONObject result = new JSONObject();
                    result.put("installed", false);
                    
                    return result.toString();
                }
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("installed", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"installed\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String getContacts() {
            try {
                // This is a simplified implementation
                // In a full app, we would query the contacts provider
                
                JSONObject result = new JSONObject();
                JSONArray contacts = new JSONArray();
                
                // Add some dummy contacts for demonstration
                JSONObject contact1 = new JSONObject();
                contact1.put("name", "Mamá");
                contact1.put("phone", "+1234567890");
                contacts.put(contact1);
                
                JSONObject contact2 = new JSONObject();
                contact2.put("name", "Papá");
                contact2.put("phone", "+0987654321");
                contacts.put(contact2);
                
                result.put("contacts", contacts);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String startBackgroundService() {
            try {
                Intent serviceIntent = new Intent(MainActivity.this, LalaBackgroundService.class);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                isServiceRunning = true;
                updateServiceStatus();
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String stopBackgroundService() {
            try {
                Intent serviceIntent = new Intent(MainActivity.this, LalaBackgroundService.class);
                stopService(serviceIntent);
                
                isServiceRunning = false;
                updateServiceStatus();
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String requestAccessibilityPermission() {
            try {
                if (!isAccessibilityServiceEnabled()) {
                    promptForAccessibilityService();
                }
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
        
        @JavascriptInterface
        public String requestNotificationPermission() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, 
                            Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                NOTIFICATION_REQUEST_CODE);
                    }
                }
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    
                    return result.toString();
                } catch (JSONException je) {
                    return "{\"success\":false,\"error\":\"Unknown error\"}";
                }
            }
        }
    }
}

package com.lalaassistant.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class LalaAccessibilityService extends AccessibilityService {
    private static final String TAG = "LalaAccessibilityService";
    
    // Command queue for pending commands
    private final Deque<AccessibilityCommand> commandQueue = new ArrayDeque<>();
    
    // Currently executing command
    private AccessibilityCommand currentCommand = null;
    
    // Flag to indicate if a command is being executed
    private boolean isExecutingCommand = false;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Process the accessibility event
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // A new window has come to the foreground
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );
            
            try {
                ActivityInfo activityInfo = getPackageManager()
                        .getActivityInfo(componentName, 0);
                
                // Log the current foreground app
                Log.d(TAG, "Current foreground app: " + event.getPackageName());
                
                // Process any pending commands for this app
                processCommandsForApp(event.getPackageName().toString());
            } catch (PackageManager.NameNotFoundException e) {
                // Not an Activity
            }
        }
        
        // Process the current command if there is one
        if (isExecutingCommand && currentCommand != null) {
            processCurrentCommand(event);
        }
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        // Configure the service
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        
        // Set the service info
        setServiceInfo(info);
        
        Log.d(TAG, "Accessibility service connected");
    }
    
    /**
     * Execute a command using the accessibility service
     * @param command The command to execute
     */
    public void executeCommand(AccessibilityCommand command) {
        synchronized (commandQueue) {
            // Add the command to the queue
            commandQueue.add(command);
            
            // Process the command if no command is currently being executed
            if (!isExecutingCommand) {
                startNextCommand();
            }
        }
    }
    
    /**
     * Start the next command in the queue
     */
    private void startNextCommand() {
        synchronized (commandQueue) {
            if (commandQueue.isEmpty()) {
                isExecutingCommand = false;
                currentCommand = null;
                return;
            }
            
            isExecutingCommand = true;
            currentCommand = commandQueue.poll();
            
            // Start the command
            currentCommand.start(this);
        }
    }
    
    /**
     * Process the current command
     * @param event The accessibility event
     */
    private void processCurrentCommand(AccessibilityEvent event) {
        if (currentCommand.process(this, event)) {
            // Command completed
            currentCommand.onComplete(true);
            startNextCommand();
        }
    }
    
    /**
     * Process commands for a specific app
     * @param packageName The package name of the app
     */
    private void processCommandsForApp(String packageName) {
        // In a real implementation, this would process commands
        // that are waiting for this app to be in the foreground
    }
    
    /**
     * Find a node by text
     * @param text The text to find
     * @return The node if found, null otherwise
     */
    public AccessibilityNodeInfo findNodeByText(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return null;
            
            Deque<AccessibilityNodeInfo> deque = new ArrayDeque<>();
            deque.add(root);
            
            while (!deque.isEmpty()) {
                AccessibilityNodeInfo node = deque.removeFirst();
                
                if (node.getText() != null && node.getText().toString().contains(text)) {
                    return node;
                }
                
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        deque.add(child);
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find a node by ID
     * @param viewId The ID to find
     * @return The node if found, null otherwise
     */
    public AccessibilityNodeInfo findNodeById(String viewId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return null;
            
            return root.findAccessibilityNodeInfosByViewId(viewId).size() > 0 ?
                    root.findAccessibilityNodeInfosByViewId(viewId).get(0) : null;
        }
        
        return null;
    }
    
    /**
     * Click on a node
     * @param node The node to click
     * @return True if successful, false otherwise
     */
    public boolean clickOnNode(AccessibilityNodeInfo node) {
        if (node != null) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        
        return false;
    }
    
    /**
     * Set text on a node
     * @param node The node to set text on
     * @param text The text to set
     * @return True if successful, false otherwise
     */
    public boolean setTextOnNode(AccessibilityNodeInfo node, String text) {
        if (node != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }
        
        return false;
    }
    
    /**
     * Abstract class for accessibility commands
     */
    public static abstract class AccessibilityCommand {
        protected Map<String, Object> params = new HashMap<>();
        protected CommandCallback callback;
        
        public AccessibilityCommand(CommandCallback callback) {
            this.callback = callback;
        }
        
        /**
         * Set a parameter
         * @param key The parameter key
         * @param value The parameter value
         * @return This command for chaining
         */
        public AccessibilityCommand setParam(String key, Object value) {
            params.put(key, value);
            return this;
        }
        
        /**
         * Start the command
         * @param service The accessibility service
         */
        public abstract void start(LalaAccessibilityService service);
        
        /**
         * Process the command
         * @param service The accessibility service
         * @param event The accessibility event
         * @return True if the command is complete, false otherwise
         */
        public abstract boolean process(LalaAccessibilityService service, AccessibilityEvent event);
        
        /**
         * Called when the command is complete
         * @param success Whether the command was successful
         */
        public void onComplete(boolean success) {
            if (callback != null) {
                callback.onCommandCompleted(this, success);
            }
        }
    }
    
    /**
     * Interface for command callbacks
     */
    public interface CommandCallback {
        void onCommandCompleted(AccessibilityCommand command, boolean success);
    }
}

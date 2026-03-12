// AutoClickerService.java
package com.xiaodi.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class AutoClickerService extends AccessibilityService {
    private WindowManager windowManager;
    private View floatingView;
    private FrameLayout touchOverlay;
    private boolean isClicking = false;
    private boolean isRecording = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable clickRunnable;
    private int clickSpeed = 10;
    private float targetX = -1, targetY = -1;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}
    
    @Override
    public void onInterrupt() { stopClicking(); }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        showFloatingWindow();
    }
    
    private void showFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_control, null);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100; params.y = 200;
        
        setupDrag(floatingView, params);
        setupControls(floatingView);
        windowManager.addView(floatingView, params);
    }
    
    private void setupDrag(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.setTag(new float[]{params.x, params.y, event.getRawX(), event.getRawY()});
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float[] tag = (float[]) view.getTag();
                    params.x = (int) (tag[0] + event.getRawX() - tag[2]);
                    params.y = (int) (tag[1] + event.getRawY() - tag[3]);
                    windowManager.updateViewLayout(floatingView, params);
                    return true;
            }
            return false;
        });
    }
    
    private void setupControls(View view) {
        Button btnRecord = view.findViewById(R.id.btn_record);
        Button btnStart = view.findViewById(R.id.btn_start);
        Button btnStop = view.findViewById(R.id.btn_stop);
        SeekBar seekBar = view.findViewById(R.id.seekbar_speed);
        TextView tvSpeed = view.findViewById(R.id.tv_speed);
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                clickSpeed = p + 1;
                tvSpeed.setText(clickSpeed + " 次/秒");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        
        btnRecord.setOnClickListener(v -> {
            if (isRecording) {
                hideTouchOverlay();
                btnRecord.setText("录制位置");
                isRecording = false;
            } else {
                showTouchOverlay();
                btnRecord.setText("点击屏幕确定");
                isRecording = true;
                Toast.makeText(this, "点击屏幕选择位置", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnStart.setOnClickListener(v -> {
            if (targetX < 0) {
                Toast.makeText(this, "先录制位置", Toast.LENGTH_SHORT).show();
                return;
            }
            startClicking();
        });
        
        btnStop.setOnClickListener(v -> stopClicking());
    }
    
    private void showTouchOverlay() {
        touchOverlay = new FrameLayout(this);
        touchOverlay.setBackgroundColor(0x44000000);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        touchOverlay.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                targetX = e.getRawX(); targetY = e.getRawY();
                Toast.makeText(this, "位置: (" + (int)targetX + ", " + (int)targetY + ")", Toast.LENGTH_SHORT).show();
                hideTouchOverlay();
                Button btn = floatingView.findViewById(R.id.btn_record);
                btn.setText("重新录制");
                isRecording = false;
            }
            return true;
        });
        windowManager.addView(touchOverlay, params);
    }
    
    private void hideTouchOverlay() {
        if (touchOverlay != null) {
            windowManager.removeView(touchOverlay);
            touchOverlay = null;
        }
    }
    
    private void startClicking() {
        if (isClicking) return;
        isClicking = true;
        Toast.makeText(this, "开始: " + clickSpeed + "次/秒", Toast.LENGTH_SHORT).show();
        long interval = 1000 / clickSpeed;
        clickRunnable = new Runnable() {
            @Override
            public void run() {
                if (isClicking) {
                    performClick(targetX, targetY);
                    handler.postDelayed(this, interval);
                }
            }
        };
        handler.post(clickRunnable);
    }
    
    private void stopClicking() {
        isClicking = false;
        if (clickRunnable != null) handler.removeCallbacks(clickRunnable);
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
    }
    
    private void performClick(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        dispatchGesture(builder.build(), null, null);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopClicking();
        hideTouchOverlay();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}

package com.example.addition;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.constraint.solver.widgets.WidgetContainer;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class OverlayService extends Service {

    CountDownTimer timer;

    private ImageView overlayedButton;
    private TextView timerDisp;
    private WindowManager wm;

    private boolean dragged = false;
    private SharedPreferences sp;
    private boolean canNyoom = false;

    private WindowManager.LayoutParams paramsImg;
    private WindowManager.LayoutParams paramsText;

    private long lastLoggedTimerCheck;
    private int lastEntryMinuteVal;



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate(){
        super.onCreate();

        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);

        lastEntryMinuteVal = sp.getInt("minutes", 20);
        timerDisp = new TextView(this);
        timerSet();

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        overlayedButton = new ImageView(this);
        overlayedButton.setImageResource(R.drawable.running);

        paramsImg = new WindowManager.LayoutParams(
                80,
                80,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        paramsImg.gravity = Gravity.LEFT | Gravity.TOP;
        paramsImg.x = 20;
        paramsImg.y = 250;

        paramsText = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        paramsText.gravity = Gravity.LEFT | Gravity.TOP;
        paramsText.x = 10;
        paramsText.y = 250 + paramsImg.height;

        wm.addView(overlayedButton, paramsImg);
        wm.addView(timerDisp, paramsText);

        View.OnTouchListener touchListener = new View.OnTouchListener() {

            private WindowManager.LayoutParams paramsI = paramsImg;
            private WindowManager.LayoutParams paramsT = paramsText;
            private int initialIX;
            private int initialIY;
            private int initialTX;
            private int initialTY;
            private int initialLX;
            private int initialLY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        initialIX = paramsI.x;
                        initialIY = paramsI.y;
                        initialTX = paramsT.x;
                        initialTY = paramsT.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        paramsI.x = initialIX + (int) (event.getRawX() - initialTouchX);
                        paramsI.y = initialIY + (int) (event.getRawY() - initialTouchY);
                        paramsT.x = initialTX + (int) (event.getRawX() - initialTouchX);
                        paramsT.y = initialTY + (int) (event.getRawY() - initialTouchY);
                        dragged = true;
                        wm.updateViewLayout(overlayedButton, paramsI);
                        wm.updateViewLayout(timerDisp, paramsT);
                        break;
                    case MotionEvent.ACTION_UP:
                        dragged = false;
                        break;
                }
                return false;
            }
        };

        try{ overlayedButton.setOnTouchListener(touchListener); }catch(Exception e){}
        try{ timerDisp.setOnTouchListener(touchListener); }catch(Exception e){}

        View.OnClickListener clickListener = new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if(!dragged){
                    if(canNyoom){
                        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                        lastLoggedTimerCheck = sp.getLong("lastEntry", 0);
                        lastEntryMinuteVal = sp.getInt("minutes", 20);
                        canNyoom = false;
                        //nyoom here
                        Intent bgIntent = new Intent(OverlayService.this, BackgroundReceiver.class);
                        bgIntent.setAction("com.example.addition.BACKGROUND_ADDITION");
                        bgIntent.putExtra("9999", 0);
                        PendingIntent bgPendingIntent = PendingIntent.getBroadcast(OverlayService.this, 0, bgIntent, 0);

                        AlarmManager alarmManager = (AlarmManager)OverlayService.this.getSystemService(Context.ALARM_SERVICE);
                        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, bgPendingIntent);
                        //timerDisp = new TextView(OverlayService.this);
                        CountDownTimer waitForLog = new CountDownTimer(11000, 100) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                                long checkChange = sp.getLong("lastEntry", 0);
                                if(checkChange != lastLoggedTimerCheck) {
                                    timerSet();
                                    this.cancel();
                                }
                            }
                            @Override
                            public void onFinish() {
                                Toast.makeText(OverlayService.this, "Error", Toast.LENGTH_LONG).show();
                            }
                        }.start();
                    }
                }
                dragged = false;
            }
        };

        overlayedButton.setOnClickListener(clickListener);
        timerDisp.setOnClickListener(clickListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayedButton != null) {
            wm.removeView(overlayedButton);
            wm.removeView(timerDisp);
            overlayedButton = null;
            timer.cancel();
            timerDisp = null;
        }
    }

    public void timerSet(){
        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);

        int minutes = sp.getInt("minutes", 20);
        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        cal.add(Calendar.MILLISECOND, -(1000*60*minutes));
        long sessionStart = cal.getTimeInMillis();
        long lastLog = sp.getLong("lastEntry", 0);

        final long waitTime = lastLog - sessionStart;

        Log.w("OverlayService", "timer");

        timer = new CountDownTimer(waitTime, 1000){
            public void onTick(long millisUntilFinished) {
                long waitTimeInSeconds = millisUntilFinished/1000;
                long minutesRemain = waitTimeInSeconds/60;
                long secondsRemain = waitTimeInSeconds%60;
                timerDisp.setText(String.format(Locale.CANADA, "%02d", minutesRemain) + ":" + String.format(Locale.CANADA, "%02d", secondsRemain));
                sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                if(lastEntryMinuteVal != sp.getInt("minutes", 20)){
                    this.cancel();
                    timerSet();
                }
            }

            public void onFinish() {
                timerDisp.setText("Ready");
                canNyoom = true;
            }
        }.start();
    }

}

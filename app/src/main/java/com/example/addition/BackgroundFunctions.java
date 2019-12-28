package com.example.addition;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

public class BackgroundFunctions extends BroadcastReceiver {

    private SharedPreferences sp;
    public static final String TAG = "BackgroundFunctions";
    private final String CHANNEL_ID = "com.example.addition.ANDROID_MAIN";

    Context context;
    @Override
    public void onReceive(Context context, Intent intent){
        int function = intent.getIntExtra("function",0);
        this.context = context;

        if(function == 0){ //reset time
            NotificationManager nMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nMgr.cancelAll();
            scheduleNoti(3);
        }

    }

    public void notifyScreen(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    public void scheduleNoti(int notiShouldShow) {
        Log.w(TAG, "noti scheduled");
        sp = context.getSharedPreferences("timeData", Context.MODE_PRIVATE);
        int boostMinutes = sp.getInt("minutes", 20);
        //action for noti:
        Intent bgIntent = new Intent(context, BackgroundReceiver.class);
        bgIntent.setAction("com.example.addition.BACKGROUND_ADDITION");
        bgIntent.putExtra("9999", 0);
        PendingIntent bgPendingIntent = PendingIntent.getBroadcast(context, 0, bgIntent, 0);

        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.MILLISECOND, -(1000*60*boostMinutes));
        long sessionStart = cal.getTimeInMillis();
        long lastLog = endTime;
        sp.edit().putLong("lastEntry", lastLog).apply();

        long waitTime = lastLog - sessionStart;

        long futureInMillis = SystemClock.elapsedRealtime() + waitTime;

        //
        Intent resetTimeIntent = new Intent(context, BackgroundFunctions.class);
        resetTimeIntent.setAction("com.example.addition.BACKGROUND_fUNCTIONS");
        resetTimeIntent.putExtra("function", 0);
        PendingIntent resetTimePendInt = PendingIntent.getBroadcast(context, 0, resetTimeIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        //

        //building noti:
        NotificationCompat.Builder notiBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.running)
                .setContentTitle("NYOOOOOOOOOOOOOM")
                .setContentText("NYOOM " + Integer.toString(boostMinutes) + " minutes?")
                .setShowWhen(false)
                //.setStyle(new NotificationCompat.BigTextStyle()
                //.bigText("Zoom " + Integer.toString(boostMinutes) + "?"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(bgPendingIntent)
                .addAction(R.drawable.walking, "Reset", resetTimePendInt)
                .setAutoCancel(true);

        Notification notification = notiBuilder.build();

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction("com.example.addition.SCHEDULE_NOTIFICATION");
        intent.putExtra("futureNotification", 0);
        intent.putExtra("notification", notification);
        intent.putExtra("should_show", notiShouldShow);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        sp.edit().putInt("notiShouldShow", notiShouldShow).apply();


        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent);

        sp.edit().putBoolean("notificationScheduled", true).apply();
        notifyScreen("Notification reset for " + boostMinutes + " minutes from now.");
    }

}

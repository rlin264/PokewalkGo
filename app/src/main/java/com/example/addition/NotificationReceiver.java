package com.example.addition;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

public class NotificationReceiver extends BroadcastReceiver {

    private final String TAG = "NotificationReceiver";
    private final String CHANNEL_ID = "com.example.addition.ANDROID_MAIN";

    SharedPreferences sp;

    @Override
    public void onReceive(Context context, Intent intent) {
        sp = context.getSharedPreferences("timeData", Context.MODE_PRIVATE);

        if(sp.getInt("notiShouldShow", 0) != intent.getIntExtra("should_show", -1)){
            return;
        }

        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("notificationScheduled", false);
        editor.apply();

        boolean stillAutoRunning = sp.getBoolean("autoRunning", false);

        NotificationManager notificationManager = createManager(context);

        Notification notification = intent.getParcelableExtra("notification");
        int notificationId = intent.getIntExtra("futureNotification", 0);
        if(!stillAutoRunning) notificationManager.notify(notificationId, notification);
        else{
            Intent bgIntent = new Intent(context, BackgroundReceiver.class);
            bgIntent.setAction("com.example.addition.BACKGROUND_ADDITION");
            bgIntent.putExtra("9999", 0);
            PendingIntent bgPendingIntent = PendingIntent.getBroadcast(context, 0, bgIntent, 0);

            AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, bgPendingIntent);
        }
    }

    public NotificationManager createManager(Context context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence channelName = "Popup Notifications";
            String description = "Notifies user";
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setDescription(description);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);
            return notificationManager;
        }
        return null;
    }

}

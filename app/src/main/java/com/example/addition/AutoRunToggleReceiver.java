package com.example.addition;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

public class AutoRunToggleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent){
        Log.w("AutoRunToggleReceiver", "received");
        SharedPreferences sp = context.getSharedPreferences("timeData", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("autoRunning", false);

        NotificationManager nMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nMgr.cancelAll();

        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        long currentTime = cal.getTimeInMillis();
        long elapsedMillis = currentTime - sp.getLong("autoRunSessionStart", 0);
        long elapsedSeconds = elapsedMillis/1000;
        long elapsedMinutes = elapsedSeconds/60;
        if(elapsedSeconds < 60)
            notifyScreen(context, "Autoran for " + Long.toString(elapsedSeconds) + " seconds.");
        else
            notifyScreen(context, "Autoran for " + Long.toString(elapsedMinutes) + " minutes.");
        editor.putLong("autoRunSessionStart", 0);
        editor.apply();
    }

    public void notifyScreen(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

}

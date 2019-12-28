package com.example.addition;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import android.content.SharedPreferences;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SessionInsertRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class BackgroundReceiver extends BroadcastReceiver implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, OnDataPointListener {

    public final String TAG = "BackgroundReceiver";
    public Context context;
    private SharedPreferences sp;

    public GoogleSignInOptions gso;
    public GoogleSignInClient mGoogleSignInClient;
    public GoogleSignInAccount appGoogleSignInAccount;

    private GoogleApiClient mGoogleApiClient;
    private boolean hasOAuthPerm = false;

    //private NotificationChannel notificationChannel;
    private final String CHANNEL_ID = "com.example.addition.ANDROID_MAIN";


    @Override
    public void onReceive(Context context, Intent intent){
        this.context = context;
        sp = context.getSharedPreferences("timeData", Context.MODE_PRIVATE);
        Bundle extras = intent.getExtras();

        if(extras != null){
            gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(context, gso);
            GoogleSignInAccount initAcc = GoogleSignIn.getLastSignedInAccount(context);
            updateUI(initAcc);

            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Fitness.SESSIONS_API)
                    .addApi(Fitness.HISTORY_API)
                    .useDefaultAccount()
                    .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint){
        Log.w(TAG, "connected to fit api");

        int minutes = sp.getInt("minutes", 20);
        boolean sessionsConflict = false;
        if(hasOAuthPerm){
            sessionsConflict = checkSessionsConflict(minutes);
            if(sessionsConflict){
                showUserWait(minutes);
            }
            else if(minutes > 0){
                int intensity = sp.getInt("intensityValue", 0);
                insertNewData(minutes, intensity);
            }
        }
    }
    @Override
    public void onDataPoint(DataPoint dp){}
    @Override
    public void onConnectionSuspended(int cause){}
    @Override
    public void onConnectionFailed(ConnectionResult res){
        if(res.getErrorCode() == FitnessStatusCodes.NEEDS_OAUTH_PERMISSIONS || res.getErrorCode() == 4){
            notifyScreen("Failed to connect to google fit. Access the app and sign in.");
        }
        else{
            notifyScreen("Error=" + Integer.toString(res.getErrorCode()));
        }
    }

    private void updateUI(GoogleSignInAccount gsa){
        if(gsa != null){
            appGoogleSignInAccount = gsa;
            FitnessOptions fo = FitnessOptions.builder()
                    .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
                    .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
                    .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_WRITE)
                    .build();
            if(!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(context), fo)){
                notifyScreen("Log into the app");
            }else{
                Log.w(TAG, "has permissions");
                hasOAuthPerm = true;
            }
        }
    }

    public boolean checkSessionsConflict(int minutes){
        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        long currentTime = cal.getTimeInMillis();
        cal.add(Calendar.MILLISECOND, -(1000*60*minutes));
        long sessionStart = cal.getTimeInMillis();
        long lastLog = sp.getLong("lastEntry", sessionStart - 1);
        if(lastLog > sessionStart) return true;
        return false;
    }

    public void showUserWait(int minutes){
        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        long currentTime = cal.getTimeInMillis();
        cal.add(Calendar.MILLISECOND, -(1000*60*minutes));
        long sessionStart = cal.getTimeInMillis();
        long lastLog = sp.getLong("lastEntry", 0);

        long waitTime = lastLog - sessionStart;
        long waitTimeSeconds = waitTime/1000;
        if(waitTimeSeconds < 60) notifyScreen("Wait " + Long.toString(waitTimeSeconds) + " more seconds.");
        else{
            notifyScreen("Wait " + Long.toString(waitTimeSeconds/60) + " more minutes.");
        }
    }

    public void insertNewData(int minutes, int intensity){
        //create time interval
        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.MILLISECOND, -(1000*60*minutes));
        Random r = new Random();
        long startTime = cal.getTimeInMillis() + r.nextInt(7001);
        Log.w(TAG, "start: " + Long.toString(startTime) + " - end: " + Long.toString(endTime));

        //initialize session
        Session mSession = new Session.Builder()
                .setName("Running")
                .setIdentifier(context.getString(R.string.app_name))
                .setDescription("Running session details")
                .setStartTime(startTime, TimeUnit.MILLISECONDS)
                .setEndTime(endTime, TimeUnit.MILLISECONDS)
                .setActivity(FitnessActivities.RUNNING)
                .build();

        int[] stepsByIntensity = new int[]{150, 190, 230};
        float[] distanceByIntensity = new float[]{0.4f, 0.6f, 0.8f};
        int steps = (stepsByIntensity[intensity] + r.nextInt(7))*minutes;
        float distance = minutes * distanceByIntensity[intensity];
        Log.w(TAG, "steps: " + Integer.toString(steps) + " distance: " + Float.toString(distance));

        //create steps data to insert
        DataSource dataSourceSteps = new DataSource.Builder()
                .setAppPackageName(context.getPackageName())
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName(TAG + " - step count")
                .setType(DataSource.TYPE_RAW)
                .build();

        DataSet dataSetSteps = DataSet.create(dataSourceSteps);

        DataPoint dataPointSteps = dataSetSteps.createDataPoint().setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
        dataPointSteps.getValue(Field.FIELD_STEPS).setInt(steps);
        dataSetSteps.add(dataPointSteps);

        //create distance data to insert
        DataSource dataSourceDistance = new DataSource.Builder()
                .setAppPackageName(context.getPackageName())
                .setDataType(DataType.TYPE_DISTANCE_DELTA)
                .setStreamName(TAG + " - distance (km)")
                .setType(DataSource.TYPE_RAW)
                .build();

        DataSet dataSetDistance = DataSet.create(dataSourceDistance);

        DataPoint dataPointDistance = dataSetDistance.createDataPoint().setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
        dataPointDistance.getValue(Field.FIELD_DISTANCE).setFloat(distance);
        dataSetDistance.add(dataPointDistance);


        SessionInsertRequest insertReq = new SessionInsertRequest.Builder()
                .setSession(mSession)
                .addDataSet(dataSetSteps)
                .addDataSet(dataSetDistance)
                .build();

        Task<Void> response = Fitness.getSessionsClient(context, GoogleSignIn.getLastSignedInAccount(context)).insertSession(insertReq)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        notifyScreen("Successful Log");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        notifyScreen("Failure: " + e.toString());
                        Log.w(TAG, e.toString());
                    }
                });
        sp = context.getSharedPreferences("timeData", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong("lastEntry", endTime);
        editor.putInt("minutes", minutes);
        editor.apply();
        notifyScreen(Integer.toString(minutes) + " minutes logged.");

        boolean hasNotiScheduled = sp.getBoolean("notificationScheduled", false);
        if(!hasNotiScheduled) nextNotification();

        //Fitness.getHistoryClient(this, appGoogleSignInAccount).insertData(dataSetSteps);
        //Fitness.getHistoryClient(this, appGoogleSignInAccount).insertData(dataSetDistance);
    }

    public void notifyScreen(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    /*public void createNotiChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence channelName = "Popup Notifications";
            String description = "Notifies user when they can NYOOM";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            notificationChannel = new NotificationChannel(CHANNEL_ID, channelName, importance);
            notificationChannel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }*/

    public void nextNotification() {
        sp = context.getSharedPreferences("timeData", Context.MODE_PRIVATE);
        if(!sp.getBoolean("notificationToggle", false)) return;
        int boostMinutes = sp.getInt("minutes", 20);

        //action for noti:

        Intent bgIntent = new Intent(context, BackgroundReceiver.class);
        bgIntent.setAction("com.example.addition.BACKGROUND_ADDITION");
        bgIntent.putExtra("9999", 0);
        PendingIntent bgPendingIntent = PendingIntent.getBroadcast(context, 0, bgIntent, 0);

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
        intent.putExtra("should_show", 3);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        sp.edit().putInt("notiShouldShow", 3).apply();

        long futureInMillis = SystemClock.elapsedRealtime() + (boostMinutes) * 60 * 1000;

        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent);

        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("notificationScheduled", true);
        editor.apply();
    }

}

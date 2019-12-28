package com.example.addition;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.util.*;
import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.ApiException;
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
import com.google.android.gms.tasks.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.common.api.GoogleApiClient;

import android.content.SharedPreferences;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, OnDataPointListener {
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_GOOGLE_FIT = 9002;
    private static final int RC_OAUTH = 1001;
    public static int OVERLAY_PERMISSION_REQ_CODE = 1002;
    public static final String TAG = "MainActivity";

    public GoogleSignInOptions gso;
    public GoogleSignInClient mGoogleSignInClient;
    public GoogleSignInAccount appGoogleSignInAccount;

    private GoogleApiClient mGoogleApiClient;
    private boolean hasOAuthPerm = false;

    private SharedPreferences sp;

    private final String CHANNEL_ID = "com.example.addition.ANDROID_MAIN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);

        //devReset();

        sp.edit().putBoolean("notificationScheduled", false).apply();
        createNotiChannel();

        ImageView walkingIV = findViewById(R.id.walkingPNG);
        walkingIV.setImageResource(R.drawable.walking);

        ImageView runningIV = findViewById(R.id.runningPNG);
        runningIV.setImageResource(R.drawable.running);

        connectApp(); //sign into google and connect to Google fit

        setupTextboxes(); //code for textboxes, reacting to input

        setupSwitches();

    }

    public void devReset(){ //for dev purposes, resetting wait time
        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        cal.add(Calendar.MILLISECOND, -(1000*60*21));
        long sessionStart = cal.getTimeInMillis();
        sp.edit().putLong("lastEntry", sessionStart).apply();
    }

    public void connectApp(){
        gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        //try to sign in first if user signed in before
        GoogleSignInAccount initAcc = GoogleSignIn.getLastSignedInAccount(this);
        updateUI(initAcc);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.SESSIONS_API)
                .addApi(Fitness.HISTORY_API)
                .useDefaultAccount()
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    public void setupTextboxes(){
        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
        int boostMinutes = sp.getInt("minutes", 20);
        int autoRunMinutes = sp.getInt("autoRunLength", 20);

        EditText etrun = findViewById(R.id.userInputMinutes);
        etrun.setText(String.format(Locale.CANADA, "%d", boostMinutes));

        EditText etauto = findViewById(R.id.autoRunMinutes);
        etauto.setText(String.format(Locale.CANADA, "%d", autoRunMinutes));

        etrun.addTextChangedListener(new TextWatcher() {
            String beforeText;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {if(s.toString().length() > 0) beforeText = s.toString();}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if(s.toString().length() > 0) {
                    sp.edit().putInt("minutes", Integer.parseInt(s.toString())).apply();
                    if(Integer.parseInt(s.toString()) > Integer.parseInt(beforeText)){
                        sp.edit().putInt("notiShouldShow", 2).apply();
                        if(sp.getBoolean("notificationToggle", false))scheduleNotification(2);
                    }
                }
                else sp.edit().putInt("minutes", 20).apply();
            }
        });

        etauto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if(s.toString().length() > 0) sp.edit().putInt("autoRunLength", Integer.parseInt(s.toString())).apply();
                else sp.edit().putInt("autoRunLength", 20).apply();
            }
        });
    }

    public void setupSwitches(){
        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
        boolean setting_autorun = sp.getBoolean("autoRunning", false);
        boolean setting_overlay = sp.getBoolean("overlayOn", false);
        boolean setting_notification_toggle = sp.getBoolean("notificationToggle", true);

        if(setting_autorun && setting_overlay){
            Toast.makeText(this, "If you ever see this tell Chiu he fucked up..", Toast.LENGTH_SHORT).show();
            setting_autorun = false;
            setting_overlay = false;
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean("autoRunning", false);
            editor.putBoolean("overlayOn", false);
            editor.apply();
        }
        //setting switch functions:

        Switch autoRunToggle = findViewById(R.id.autoRunSwitch);
        autoRunToggle.setChecked(setting_autorun);
        autoRunToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();

                if(isChecked){
                    if(sp.getBoolean("overlayOn", false)){
                        Switch permSwitch = findViewById(R.id.OverlaySwitch);
                        permSwitch.setChecked(false);
                        editor.putBoolean("overlayOn", false);
                    }
                    if(!sp.getBoolean("notificationToggle", true)) {
                        Switch notiToggleSwitch = findViewById(R.id.NotificationToggle);
                        notiToggleSwitch.setChecked(true);
                        editor.putBoolean("notificationToggle", true);
                    }
                    editor.putBoolean("autoRunning", true);
                    Intent autoRunIntent = new Intent(MainActivity.super.getBaseContext(), AutoRunToggleReceiver.class);
                    autoRunIntent.setAction("com.example.addition.AUTORUN_OFF");
                    PendingIntent autoRunPendIntent = PendingIntent.getBroadcast(MainActivity.super.getBaseContext(), 0, autoRunIntent, 0);

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.super.getBaseContext(), CHANNEL_ID)
                            .setSmallIcon(R.drawable.running)
                            .setContentTitle("NYOOOOMING")
                            .setContentText("Autorun is on. Tap to turn off.")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(autoRunPendIntent)
                            .setOngoing(true);
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.super.getBaseContext());
                    notificationManager.notify(9991, builder.build());
                }
                else {
                    NotificationManager nMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nMgr.cancelAll();
                    editor.putBoolean("autoRunning", false);
                }
                editor.apply();
            }
        });

        Switch overlayToggle = findViewById(R.id.OverlaySwitch);
        overlayToggle.setChecked(setting_overlay);
        overlayToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                if(isChecked){
                    checkOverlayPerm();
                    if(sp.getBoolean("autoRunning", false)){
                        Switch autoSwitch = findViewById(R.id.autoRunSwitch);
                        autoSwitch.setChecked(false);
                        editor.putBoolean("autoRunning", false);
                    }
                    if(sp.getBoolean("notificationToggle", true)) {
                        Switch notiToggleSwitch = findViewById(R.id.NotificationToggle);
                        notiToggleSwitch.setChecked(false);
                        editor.putBoolean("notificationToggle", false);
                    }
                    editor.putBoolean("overlayOn", true);
                    //activate overlay

                    Log.w(TAG, "overlay on");
                    startService(new Intent(MainActivity.this, OverlayService.class));
                }
                else {
                    //deactivate overlay
                    Log.w(TAG, "overlay off");
                    stopService(new Intent(MainActivity.this, OverlayService.class));
                    editor.putBoolean("overlayOn", false);
                }
                editor.apply();
            }
        });

        Switch notificationToggle = findViewById(R.id.NotificationToggle);
        notificationToggle.setChecked(setting_notification_toggle);
        notificationToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                if(isChecked) {
                    editor.putBoolean("notificationToggle", true);
                    if(sp.getBoolean("overlayOn", false)){
                        Switch permSwitch = findViewById(R.id.OverlaySwitch);
                        permSwitch.setChecked(false);
                        editor.putBoolean("overlayOn", false);
                    }
                }
                else {
                    editor.putBoolean("notificationToggle", false);
                    if(sp.getBoolean("autoRunning", false)){
                        Switch autoSwitch = findViewById(R.id.autoRunSwitch);
                        autoSwitch.setChecked(false);
                        editor.putBoolean("autoRunning", false);
                    }
                }
                editor.apply();
            }
        });

        NotificationManager nMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nMgr.cancelAll();

        if(setting_autorun){
            Intent autoRunIntent = new Intent(this, AutoRunToggleReceiver.class);
            autoRunIntent.setAction("com.example.addition.AUTORUN_OFF");
            PendingIntent autoRunPendIntent = PendingIntent.getBroadcast(this, 0, autoRunIntent, 0);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.running)
                    .setContentTitle("NYOOOOMING")
                    .setContentText("Autorun is on. Tap to turn off.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(autoRunPendIntent)
                    .setOngoing(true);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(9991, builder.build());
        }
        else if(setting_overlay){
            Log.w(TAG, "overlay on");
            startService(new Intent(this, OverlayService.class));
        } //shouldnt need anything. all actions will be handled when the switch is toggled on and off
    }

    public void checkOverlayPerm(){
        if(Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(MainActivity.this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            }else{
                Log.w(TAG, "has overlay perms");
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint){
        Log.w(TAG, "connected to fit api");

        findViewById(R.id.userInputMinutes).setEnabled(true);
        findViewById(R.id.autoRunMinutes).setEnabled(true);
        findViewById(R.id.autoRunSwitch).setEnabled(true);
        findViewById(R.id.OverlaySwitch).setEnabled(true);

        Button insertNewDataBTN = findViewById(R.id.dataInsertButton);
        insertNewDataBTN.setEnabled(true);
        insertNewDataBTN.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                EditText userInputText = findViewById(R.id.userInputMinutes);
                int minutes = 0;
                try{
                    minutes = Integer.parseInt(userInputText.getText().toString());
                }catch(Exception e){Log.w(TAG, e.toString());}

                //check if new session will conflict
                boolean sessionsConflict;
                if(hasOAuthPerm){
                    sessionsConflict = checkSessionsConflict(minutes);
                    if(sessionsConflict){
                        showUserWait(minutes);
                    }
                    else if(minutes > 0){
                        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                        int intensity = sp.getInt("intensityValue", 0);
                        insertNewData(minutes, intensity);
                    }
                }
            }
        });
    }
    @Override
    public void onDataPoint(DataPoint dp){}
    @Override
    public void onConnectionSuspended(int cause){}
    @Override
    public void onConnectionFailed(ConnectionResult res){
        Log.w(TAG, "failed to connect to fit api");
        notifyScreen("Failed to connect to account. Login to google and allow access to Fit. You may need to restart the app.");
        if(res.getErrorCode() == FitnessStatusCodes.NEEDS_OAUTH_PERMISSIONS || res.getErrorCode() == 4){
            try{
                Log.w(TAG, "attempting");
                res.startResolutionForResult(this, RC_OAUTH);
            }catch(IntentSender.SendIntentException e){Log.w(TAG, e.toString());}
        }
        else{
            notifyScreen("Error=" + Integer.toString(res.getErrorCode()));
        }
    }

    private void signIn(){
        Intent siIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(siIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
// Check which request we're responding to
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
        else if(requestCode == RC_GOOGLE_FIT){
            if(resultCode == RESULT_OK){
                hasOAuthPerm = true;
            }
        }
        else if(requestCode == RC_OAUTH){
            if(resultCode == RESULT_OK){
                if(!mGoogleApiClient.isConnected()) mGoogleApiClient.connect();
            }
        }
        else if(requestCode == OVERLAY_PERMISSION_REQ_CODE){
            if(!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "cannot draw overlay");
                Switch overlaySwitch = findViewById(R.id.OverlaySwitch);
                overlaySwitch.setChecked(false);
            }
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask){
        try{
            GoogleSignInAccount acc = completedTask.getResult(ApiException.class);
            updateUI(acc);
        }catch(ApiException ae){
            Log.w(TAG, "signInResult:failed code=" + ae.getStatusCode());
            updateUI(null);
        }
    }

    private void updateUI(GoogleSignInAccount gsa){
        if(gsa != null){
            appGoogleSignInAccount = gsa;
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            Log.w(TAG, "Sign in success");
            FitnessOptions fo = FitnessOptions.builder()
                    .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
                    .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
                    .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_WRITE)
                    .build();
            if(!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fo)){
                GoogleSignIn.requestPermissions(this, RC_GOOGLE_FIT, GoogleSignIn.getLastSignedInAccount(this), fo);
            }else{
                Log.w(TAG, "has permissions");
                hasOAuthPerm = true;
            }
            sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
            int intensitySetting = sp.getInt("intensityValue", 0);
            SeekBar sb = findViewById(R.id.intensityBar);
            sb.setEnabled(true);
            sb.setProgress(intensitySetting);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                int progressChangedValue = 0;
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                    progressChangedValue = progress;
                }
                public void onStartTrackingTouch(SeekBar seekBar){}
                public void onStopTrackingTouch(SeekBar seekBar){
                    sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putInt("intensityValue", progressChangedValue);
                    editor.apply();
                    Log.w(TAG, Integer.toString(progressChangedValue));
                }
            });
        }
        else {
            //button actions for sign in (button only appears when auto sign in doesn't work)
            findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    //startActivity(new Intent(MainActivity.this,StandingsActivity.class));
                    signIn();
                }
            });
        }
    }

    public boolean checkSessionsConflict(int minutes){
        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        cal.add(Calendar.MILLISECOND, -(1000*60*minutes));
        long sessionStart = cal.getTimeInMillis();
        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
        long lastLog = sp.getLong("lastEntry", sessionStart - 1);
        return lastLog > sessionStart;
    }

    public void showUserWait(int minutes){
        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        cal.add(Calendar.MILLISECOND, -(1000*60*minutes));
        long sessionStart = cal.getTimeInMillis();
        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
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
                .setIdentifier(getString(R.string.app_name))
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
                .setAppPackageName(this.getPackageName())
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
                .setAppPackageName(this.getPackageName())
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

        Task<Void> response = Fitness.getSessionsClient(this, GoogleSignIn.getLastSignedInAccount(this)).insertSession(insertReq)
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

        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong("lastEntry", endTime);
        editor.putInt("minutes", minutes);
        if(sp.getBoolean("autoRunning", false)) editor.putLong("autoRunSessionStart", endTime);

        notifyScreen(Integer.toString(minutes) + " minutes logged.");

        if(sp.getBoolean("autoRunning", false)){
            EditText autoRunMinEdit = findViewById(R.id.autoRunMinutes);
            int autoRunMinutes = Integer.parseInt(autoRunMinEdit.getText().toString());

            Intent autoRunShutDownIntent = new Intent(this, AutoRunToggleReceiver.class);
            autoRunShutDownIntent.setAction("com.example.addition.AUTORUN_OFF");
            PendingIntent shutDownPendIntent = PendingIntent.getBroadcast(this, 0, autoRunShutDownIntent, 0);

            long futureInMillis = SystemClock.elapsedRealtime() + (autoRunMinutes * 60000);
            AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, shutDownPendIntent);
            editor.putInt("autoRunLength", autoRunMinutes);
        }
        editor.apply();
        boolean hasCurrentScheduledNotification = sp.getBoolean("notificationScheduled", false);
        if(!hasCurrentScheduledNotification && sp.getBoolean("notificationToggle", false)) scheduleNotification(1);
        //Fitness.getHistoryClient(this, appGoogleSignInAccount).insertData(dataSetSteps);
        //Fitness.getHistoryClient(this, appGoogleSignInAccount).insertData(dataSetDistance);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void createNotiChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence channelName = "Popup Notifications";
            String description = "Notifies user when they can NYOOM";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, channelName, importance);
            notificationChannel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);
        }else{
            CharSequence channelName = "Popup Notifications";
            String description = "Notifies user when they can NYOOM";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
        }
    }

    public void notifyScreen(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    public void scheduleNotification(int notiShouldShow){
        Log.w(TAG, "notification scheduled");
        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
        int boostMinutes = sp.getInt("minutes", 20);
        //action for noti:

        Intent bgIntent = new Intent(this, BackgroundReceiver.class);
        bgIntent.setAction("com.example.addition.BACKGROUND_ADDITION");
        bgIntent.putExtra("9999", 0);
        PendingIntent bgPendingIntent = PendingIntent.getBroadcast(this, 0, bgIntent, 0);

        Calendar cal = Calendar.getInstance();
        Date current = new Date();
        cal.setTime(current);
        cal.add(Calendar.MILLISECOND, -(1000*60*boostMinutes));
        long sessionStart = cal.getTimeInMillis();
        sp = getSharedPreferences("timeData", Context.MODE_PRIVATE);
        long lastLog = sp.getLong("lastEntry", 0);

        long waitTime = lastLog - sessionStart;

        long futureInMillis = SystemClock.elapsedRealtime() + waitTime;

        //
        Intent resetTimeIntent = new Intent(this, BackgroundFunctions.class);
        resetTimeIntent.setAction("com.example.addition.BACKGROUND_fUNCTIONS");
        resetTimeIntent.putExtra("function", 0);
        PendingIntent resetTimePendInt = PendingIntent.getBroadcast(this, 0, resetTimeIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        //

        //building noti:
        NotificationCompat.Builder notiBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
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

        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.setAction("com.example.addition.SCHEDULE_NOTIFICATION");
        intent.putExtra("futureNotification", 0);
        intent.putExtra("notification", notification);
        intent.putExtra("should_show", notiShouldShow);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        sp.edit().putInt("notiShouldShow", notiShouldShow).apply();


        AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent);

        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("notificationScheduled", true);
        editor.apply();
        //send noti:
        //NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        //NotificationManager notificationManager = getSystemService(NotificationManager.class);
        //notificationManager.createNotificationChannel(notificationChannel);
        //notificationManager.notify(9990, notiBuilder.build());
        //end test
    }
}

package liu.zach.com.gpslocation;

import android.content.Intent;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView address, infoTv;
    private Location lastLocation;
    private AddressResultReceiver resultReceiver;

    private TextToSpeech mTTS;

    private float startSpeed, endSpeed;
    private float startBearing, endBearing;
    // 保存考量区间的速度值和朝向
    private Deque<Float> speedQueue = new LinkedList<>();
    private Deque<Float> bearingQueue = new LinkedList<>();
    private Deque<Location> locationQueue = new LinkedList<>();

    private long currentRealTime;
    private String addressOutput;
    private Bundle mBundle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        address = findViewById(R.id.address);
        infoTv = findViewById(R.id.info);
        resultReceiver = new AddressResultReceiver(null);

        initSpeech();

        GPSUtils.getInstance(MainActivity.this).getLngAndLat(new GPSUtils.OnLocationResultListener() {
            @Override
            public void onLocationResult(final Location location) {
                if (location != null) {
                    Log.e(TAG, "init location: " + location.toString());

                    lastLocation = location;
                    if (location.getSpeed() != 0) {
                        speedQueue.offer(getRealSpeed(location.getSpeed()));
                        bearingQueue.offer(location.getAccuracy());
                    }
                    locationQueue.offer(location);
                    startIntentService();
                }
            }

            @Override
            public void OnLocationChange(Location location, int count) {
                if (location != null) {
                    Log.e(TAG, "OnLocationChange: " + location.toString());

                    startIntentService();
                    lastLocation = location;

                    Location curLocation = locationQueue.peekFirst();
                    currentRealTime = SystemClock.elapsedRealtimeNanos();
                    while (curLocation != null && elapsed2ms(currentRealTime, curLocation) > 3000) {  // 考察3s内的数据
                        if (!locationQueue.isEmpty()) {
                            locationQueue.removeFirst();
                        }
                        if (!speedQueue.isEmpty()) {
                            speedQueue.removeFirst();
                        }
                        if (!bearingQueue.isEmpty()) {
                            bearingQueue.removeFirst();
                        }
                        curLocation = locationQueue.peekFirst();
                    }

                    if (!locationQueue.isEmpty()) {
                        checkRapidSpeedState(location);
                        checkBearingState(location);
                    }

                    locationQueue.offer(location);
                    speedQueue.offer(getRealSpeed(location.getSpeed()));
                    bearingQueue.offer(location.getBearing());

                    StringBuilder queueSb = new StringBuilder();
                    queueSb.append("speedQ: ").append(printQueue(speedQueue)).append("\n").append("bearingQ: ").
                            append(printQueue(bearingQueue)).append("\n\n").
                            append("satellite count: ").append(count);
                    infoTv.setText(queueSb.toString());

                    displayAddressOutput(addressOutput);
                }
            }
        });
    }

    private void initSpeech() {
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    TTSPlay("语音初始化成功");
                    int result = mTTS.setLanguage(Locale.CHINESE);
                    if (result == TextToSpeech.LANG_MISSING_DATA  || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(MainActivity.this, "TTS暂时不支持这种语音的朗读！", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        mBundle = new Bundle();
        mBundle.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
    }

    private void TTSPlay(String text) {
        if (mTTS != null) {
            mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, mBundle, null);
        }
    }


    private long elapsed2ms(long currentRealTimeNano, Location location) {
        long duration = TimeUnit.NANOSECONDS.toMillis(currentRealTimeNano - location.getElapsedRealtimeNanos());
        Log.e(TAG, "elaspsed duration: " + duration);
        return duration;
    }

    private static final Float SPEED_CHANGE_THRESHOLD = 18.03f;
    private static final Float BEARING_CHANGE_THRESHOLD = 80f;

    /**
     * 判断是否急加速或急减速
     */
    private void checkRapidSpeedState(Location location) {
        if (location.getSpeed() == 0) {
            return;
        }

        Log.i(TAG, "speedQueue: " + printQueue(speedQueue));

        if (!speedQueue.isEmpty()) {
            startSpeed = speedQueue.peekFirst();
            endSpeed = speedQueue.peekLast();
            if (Math.abs(endSpeed - startSpeed) >= SPEED_CHANGE_THRESHOLD) {
                if (startSpeed > endSpeed) {
                    TTSPlay("急减速");
                } else {
                    TTSPlay("急加速");
                }
            }
        }
    }

    /**
     * 判断是否急转弯
     */
    private void checkBearingState(Location location) {
        if (location.getBearing() == 0 || location.getSpeed() == 0) {
            return;
        }

        Log.i(TAG, "bearingQueue: " + printQueue(bearingQueue));

        if (!bearingQueue.isEmpty()) {
            startBearing = bearingQueue.peekFirst();
            endBearing = bearingQueue.peekLast();

            if (startBearing == 0 || endBearing == 0) { // 可能为静止状态，忽略
                return;
            }

            float abs = Math.abs(endBearing - startBearing);
            if (abs >= 300f) { // 在0度附近偏移，视为无效
                return;
            }

            if (abs >= BEARING_CHANGE_THRESHOLD) {
                TTSPlay("急转弯");
            }
        }
    }

    /**
     * m/s -> km/h
     */
    private float getRealSpeed(float speed) {
        return speed * 3.6f;
    }

    private String printQueue(Deque<Float> q) {
        StringBuilder sb = new StringBuilder();

        for (Float val : q) {
            sb.append(val).append(" -> ");
        }
        return sb.toString();
    }

    protected void startIntentService() {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, resultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, lastLocation);
        startService(intent);
    }


    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultData == null) {
                return;
            }

            addressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            if (addressOutput == null) {
                addressOutput = "";
            }

            Log.i(TAG, "current Address: " + addressOutput);

            if (resultCode == Constants.SUCCESS_RESULT) {
//                Log.i(TAG, "address found");
            }

        }
    }

    private void displayAddressOutput(final String addressText) {
        Log.i(TAG, "lastLocation info: " + lastLocation.toString());

        runOnUiThread(new Runnable() {

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void run() {
                try {
                    CharSequence addrInfo = TextUtils.concat(
//                            "latitude: " + lastLocation.getLatitude() + ", longitude: " + lastLocation.getLongitude() + "\n",
//                            "address: " + addressText + "\n",
//                            "altitude: " + lastLocation.getAltitude() + "\n",
//                            "Accuracy: " + lastLocation.getAccuracy() + "\n",
//                            "VerticalAccuracyMeters: " + lastLocation.getVerticalAccuracyMeters() + "\n\n",

                            "speed:" + getRealSpeed(lastLocation.getSpeed()) + " km/h" + "\n",
                            "SpeedAccuracyMetersPerSecond: " + lastLocation.getSpeedAccuracyMetersPerSecond() + "\n\n",

                            "bearing: " + lastLocation.getBearing() + "\n",
                            "BearingAccuracyDegrees: " + lastLocation.getBearingAccuracyDegrees() + "\n"

//                            "getTime: " + lastLocation.getTime() + "\n",
//                            "ElapsedRealtimeNanos: " + lastLocation.getElapsedRealtimeNanos()
                    );

                    address.setText(addrInfo);
                } catch (Error e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GPSUtils.getInstance(MainActivity.this).removeListener();

        if (mTTS != null) {
            mTTS.shutdown();
        }
    }
}

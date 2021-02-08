package liu.zach.com.gpslocation;

import android.content.Intent;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.speech.tts.TextToSpeech;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView address, infoTv;
    private Location lastLocation;
    private AddressResultReceiver resultReceiver;

    private TextToSpeech mTTS;

    private List<Float> speedList = new LinkedList<>();
    private List<Float> bearingList = new LinkedList<>();

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
                        speedList.add(getRealSpeed(location.getSpeed()));
                        bearingList.add(location.getBearing());
                    }
                    startIntentService();
                }
            }

            @Override
            public void OnLocationChange(Location location, int count) {
                if (location != null) {
                    Log.e(TAG, "OnLocationChange: " + location.toString());

                    startIntentService();
                    lastLocation = location;


                    if (speedList.size() >= 2) {
                        speedList.remove(0);
                    }
                    if (bearingList.size() >= 2) {
                        bearingList.remove(0);
                    }
                    speedList.add(getRealSpeed(location.getSpeed()));
                    bearingList.add(location.getBearing());

                    checkRapidSpeedState(location);
                    checkBearingState(location);

                    StringBuilder queueSb = new StringBuilder();
                    queueSb.append("speedList: ").append(printList(speedList)).append("\n").append("bearingList: ").
                            append(printList(bearingList)).append("\n\n").
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
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
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

    private static final Float SPEED_CHANGE_THRESHOLD = 1.67f;
    private static final Float BEARING_CHANGE_THRESHOLD = 60f;
    private float ACCEL_CRASH_VALUE = 5.6f;
    private float ACCEL_INTENSE_VALUE = 3.34f;
    private float ACCEL_THREAD_HOLD = 1.67f;

    private int decelCnt = 0, nrlCnt = 0, accelCnt = 0;

    private long lastAccAlarmTick = System.currentTimeMillis();

    /**
     * 判断是否急加速或急减速
     */
    private void checkRapidSpeedState(Location location) {
        if (location.getSpeed() == 0 || speedList.size() < 2) {
            return;
        }

        Log.i(TAG, "speedList: " + printList(speedList));
        float realAcc = (speedList.get(1) - speedList.get(0)) / 3.6f;
        float acc = Math.abs(realAcc);

        if (acc > ACCEL_CRASH_VALUE) {
            decelCnt = 0;
            TTSPlay("发生剧烈碰撞");
            return;
        }

        if (acc > ACCEL_INTENSE_VALUE) {
            decelCnt = 0;
            accelCnt = 0;
            if (System.currentTimeMillis() - lastAccAlarmTick < 1000) {
                Log.e(TAG, "ACC_THREAD 两次报警时间太短");
                return;
            }
            lastAccAlarmTick = System.currentTimeMillis();
            if (realAcc < 0) {
                Log.e(TAG, "Accer 急减速0");
                TTSPlay("急减速1");
            }
        } else if (acc > ACCEL_THREAD_HOLD) {
            if (realAcc > 0) {
                if (System.currentTimeMillis() - lastAccAlarmTick < 1000) {
                    Log.e(TAG, "ACC_THREAD 两次报警时间太短");
                    return;
                }
                nrlCnt = 2;
                decelCnt = 0;
                accelCnt++;
                if (accelCnt > 1) {
                    lastAccAlarmTick = System.currentTimeMillis();
                    Log.e(TAG, "Accer 急加速");
                    TTSPlay("急加速");
                    accelCnt = 0;
                }
            } else {
                if (System.currentTimeMillis() - lastAccAlarmTick < 2000) {
                    Log.e(TAG, "ACC_THREAD 两次报警时间太短");
                    return;
                }
                nrlCnt = 2;
                accelCnt = 0;
                decelCnt++;
                if (decelCnt > 1) {
                    lastAccAlarmTick = System.currentTimeMillis();
                    Log.e(TAG, "Accer 急减速1");
                    TTSPlay("急减速2");
                    decelCnt = 0;
                }
            }
        } else {
            if (nrlCnt > 0) {
                nrlCnt--;
                if (nrlCnt == 0) {
                    decelCnt = 0;
                    accelCnt = 0;
                }
            }
        }

    }

    /**
     * 判断是否急转弯
     */
    private void checkBearingState(Location location) {
        if (location.getBearing() == 0 || bearingList.size() < 2) {
            return;
        }

        Log.i(TAG, "bearingList: " + printList(bearingList));

        float startBearing = bearingList.get(0);
        float endBearing = bearingList.get(1);
        if (startBearing == 0 || endBearing == 0) { // 可能为静止状态，忽略
            return;
        }

        double rst = Math.abs(endBearing - startBearing);
        if (rst >= 300f) { // 在0度附近偏移，视为无效
            return;
        }

        if (rst > BEARING_CHANGE_THRESHOLD && getRealSpeed(location.getSpeed()) > 10) {
            TTSPlay("急转弯");
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

    private String printList(List<Float> list) {
        StringBuilder sb = new StringBuilder();
        for (Float val : list) {
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

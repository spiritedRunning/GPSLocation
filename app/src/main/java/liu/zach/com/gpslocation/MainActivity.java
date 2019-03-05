package liu.zach.com.gpslocation;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView address;
    protected Location lastLocation;
    private AddressResultReceiver resultReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        address = findViewById(R.id.address);
        resultReceiver = new AddressResultReceiver(null);

        GPSUtils.getInstance(MainActivity.this).getLngAndLat(new GPSUtils.OnLocationResultListener() {
            @Override
            public void onLocationResult(final Location location) {
                if (location != null) {
                    Log.e(TAG, "init latitude: " + location.getLatitude() + ", longitude: " + location.getLongitude());

                    lastLocation = location;
                    startIntentService();
                }
            }

            @Override
            public void OnLocationChange(Location location) {
                if (location != null) {
                    Log.e(TAG, "change latitude: " + location.getLatitude() + ", longitude: " + location.getLongitude());

                    lastLocation = location;
                    startIntentService();
                }
            }
        });


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

            // Display the address string
            // or an error message sent from the intent service.
            String addressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            if (addressOutput == null) {
                addressOutput = "";
            }
            displayAddressOutput(addressOutput);

            if (resultCode == Constants.SUCCESS_RESULT) {
                Log.i(TAG, "address found");
            }

        }
    }

    private void displayAddressOutput(final String addressText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CharSequence addrInfo = TextUtils.concat("latitude: " +
                                lastLocation.getLatitude() + ", longitude: " + lastLocation.getLongitude() + "\n",
                                addressText);
                address.setText(addrInfo);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GPSUtils.getInstance(MainActivity.this).removeListener();
    }
}

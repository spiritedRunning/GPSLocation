package liu.zach.com.gpslocation;

import android.app.IntentService;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 根据经纬度获取实际地址
 * <p>
 * Created by zhen.liu on 01,March,2019.
 */
public class FetchAddressIntentService extends IntentService {
    private static final String TAG = "FetchAddressIntentServi";

    protected ResultReceiver receiver;

    private Intent mIntent;

    private ExecutorService executorService = Executors.newFixedThreadPool(1);


    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public FetchAddressIntentService() {
        super("Location Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        mIntent = intent;

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                Geocoder geocoder = new Geocoder(FetchAddressIntentService.this, Locale.getDefault());
                String errorMessage = "";

                // Get the location passed to this service through an extra.
                Location location = mIntent.getParcelableExtra(Constants.LOCATION_DATA_EXTRA);
                receiver = mIntent.getParcelableExtra(Constants.RECEIVER);

                List<Address> addresses = null;

                try {
                    addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(),
                            // In this sample, get just a single address.
                            1);
                } catch (IOException ioException) {
                    // Catch network or other I/O problems.
                    errorMessage = getString(R.string.service_not_available);
                    Log.e(TAG, errorMessage, ioException);
                } catch (IllegalArgumentException illegalArgumentException) {
                    // Catch invalid latitude or longitude values.
                    errorMessage = getString(R.string.invalid_lat_long_used);
                    Log.e(TAG, errorMessage + ". " +
                                    "Latitude = " + location.getLatitude() + ", Longitude = " + location.getLongitude(),
                            illegalArgumentException);
                }

                // Handle case where no address was found.
                if (addresses == null || addresses.size() == 0) {
                    if (errorMessage.isEmpty()) {
                        errorMessage = getString(R.string.no_address_found);
//                Log.e(TAG, errorMessage);
                    }
                    deliverResultToReceiver(Constants.FAILURE_RESULT, errorMessage);
                } else {
                    Address address = addresses.get(0);
                    ArrayList<String> addressFragments = new ArrayList<String>();

                    // Fetch the address lines using getAddressLine,
                    // join them, and send them to the thread.
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                        addressFragments.add(address.getAddressLine(i));
                    }
//                    Log.i(TAG, getString(R.string.address_found));
                    deliverResultToReceiver(Constants.SUCCESS_RESULT,
                            TextUtils.join(System.getProperty("line.separator"), addressFragments));
                }
            }
        });
    }

    private void deliverResultToReceiver(int resultCode, String message) {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.RESULT_DATA_KEY, message);
        receiver.send(resultCode, bundle);
    }
}


package liu.zach.com.gpslocation;

import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import java.util.Iterator;
import java.util.List;

public class GPSUtils {
    private static final String TAG = "GPSUtils";

    private static GPSUtils instance;
    private Context mContext;
    private LocationManager locationManager;

    private GPSUtils(Context context) {
        this.mContext = context;
    }

    public static GPSUtils getInstance(Context context) {
        if (instance == null) {
            instance = new GPSUtils(context);
        }
        return instance;
    }

    /**
     * 获取经纬度
     */
    /*  注意事项:
     * 0 在测试GPS定位时最好在较为宽广的空间,否则影响定位
     * 1 利用mLocationManager.getLastKnownLocation(GPSProvider)获取Location时常为null.
     *   因为设备定位是需要一定时间的,所以把定位逻辑放在LocationManager的requestLocationUpdates()方法
     *
     * 2 LocationManager.requestLocationUpdates
     *   (String provider, long minTime, float minDistance, LocationListener listener)
     *   第一个参数:位置信息的provider,比如GPS
     *   第二个参数:更新位置信息的时间间隔,单位毫秒
     *   第三个参数:更新位置信息的距离间隔,单位米
     *   第四个参数:位置信息变化时的回调
     *
     * 3 LocationListener中最重要的回调方法onLocationChanged()
     *   当minTime和minDistance同时满足时会调用该方法.文档说明:
     *   The minDistance parameter can also be used to control the
     *   frequency of location updates. If it is greater than 0 then the
     *   location provider will only send your application an update when
     *   the location has changed by at least minDistance meters, AND
     *   at least minTime milliseconds have passed.
     *   比如间隔时间(minTime)到了3秒并且移动的距离(minDistance)大于了5米
     *   那么就会调用该方法.
     *
     * 4 在Activity的onDestroy()时取消地理位置的更新.
     */
    public void getLngAndLat(OnLocationResultListener onLocationResultListener) {
        mOnLocationListener = onLocationResultListener;

        String locationProvider = null;
        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);

        if (providers.contains(LocationManager.GPS_PROVIDER)) { // 使用GPS
            Log.i(TAG, "using GPS_PROVIDER");
            locationProvider = LocationManager.GPS_PROVIDER;
        } else if (providers.contains(LocationManager.NETWORK_PROVIDER)) { // 使用wifi
            Log.i(TAG, "using NETWORK_PROVIDER");
            locationProvider = LocationManager.NETWORK_PROVIDER;
        } else {
            Intent i = new Intent();
            i.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            mContext.startActivity(i);
        }

        Location location = locationManager.getLastKnownLocation(locationProvider);
        if (location != null) {
            if (mOnLocationListener != null) {
                mOnLocationListener.onLocationResult(location);
            }

        }
        //监视地理位置变化
        locationManager.requestLocationUpdates(locationProvider, 0, 0, locationListener);
        locationManager.addGpsStatusListener(gpsStatusListener);
    }

    GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
        public void onGpsStatusChanged(int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_FIRST_FIX: // 第一次定位
                    Log.i(TAG, "GPS_EVENT_FIRST_FIX");
                    break;

                case GpsStatus.GPS_EVENT_SATELLITE_STATUS: // 卫星状态改变
                    GpsStatus gpsStatus = locationManager.getGpsStatus(null);
                    int maxSatellites = gpsStatus.getMaxSatellites();
                    Iterator<GpsSatellite> iters = gpsStatus.getSatellites().iterator();
                    int count = 0;
                    while (iters.hasNext() && count <= maxSatellites) {
                        GpsSatellite s = iters.next();
                        count++;
                    }
                    Log.i(TAG, "Satellite Number:" + count);
                    break;

                case GpsStatus.GPS_EVENT_STARTED: // 定位启动
                    Log.i(TAG, "GPS_EVENT_STARTED");
                    break;

                case GpsStatus.GPS_EVENT_STOPPED: // 定位结束
                    Log.i(TAG, "GPS_EVENT_STOPPED");
                    break;
            }
        }
    };

    public LocationListener locationListener = new LocationListener() {

        // Provider的状态在可用、暂时不可用和无服务三个状态直接切换时触发此函数
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        // Provider被enable时触发此函数，比如GPS被打开
        @Override
        public void onProviderEnabled(String provider) {

        }

        // Provider被disable时触发此函数，比如GPS被关闭
        @Override
        public void onProviderDisabled(String provider) {

        }

        //当坐标改变时触发此函数，如果Provider传进相同的坐标，它就不会被触发
        @Override
        public void onLocationChanged(Location location) {
            if (mOnLocationListener != null) {
                mOnLocationListener.OnLocationChange(location);
            }
        }
    };

    public void removeListener() {
        locationManager.removeUpdates(locationListener);
    }

    private OnLocationResultListener mOnLocationListener;

    public interface OnLocationResultListener {
        void onLocationResult(Location location);

        void OnLocationChange(Location location);
    }
}
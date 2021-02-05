package liu.zach.com.gpslocation;


import android.app.Application;
import android.content.Context;

public class IApplication extends Application {
    private static IApplication application;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;

    }

    public synchronized static Context getContext() {
        if (application != null) {
            return application.getApplicationContext();
        }
        return null;
    }
}

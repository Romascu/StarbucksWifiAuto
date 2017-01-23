
package org.crocodile.sbautologin;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.crocodile.sbautologin.db.*;
import org.crocodile.sbautologin.model.HistoryItem;

public class NetStatusBroadcastReceiver extends BroadcastReceiver
{
    private static final String TAG = "SbAutoLogin";
    private Context             context;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        this.context = context;

        final String action = intent.getAction();
        Log.d(TAG, "Broadcast received. Action=" + action);

        SharedPreferences settings = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        if(!settings.getBoolean(Constants.PREF_KEY_ACTIVE, true))
        {
            Log.i(TAG, "Disabled. Ignoring broadcast.");
            return;
        }

        NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if(ni == null || !ni.isConnected())
        {
            Log.d(TAG, "Not connected");
            return;
        }

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo winfo = wifi.getConnectionInfo();
        String ssid = winfo.getSSID();

        if(Constants.STARBUCKS_SSID.equals(ssid) || ("\"" + Constants.STARBUCKS_SSID + "\"").equals(ssid))
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            Log.d(TAG, "Starbucks SSDID detected. SSID=" + ssid);
            Starbucks s = new Starbucks();
            HistoryItem h = new HistoryItem();
            h.setDate(new Date());
            try
            {
                boolean status = s.login(getTestURL(context, settings));
                h.setSuccess(true);
                if(status)
                {
                    if(prefs.getBoolean(Constants.PREF_KEY_NOTIFY_WHEN_SUCCESS, true))
                    {
                        createNotification(context.getString(R.string.notify_message_success));
                    }
                    h.setMessage("Logged in");
                } else
                {
                    if(prefs.getBoolean(Constants.PREF_KEY_NOTIFY_WHEN_ALREADY_LOGGED_IN, false))
                    {
                        createNotification(context.getString(R.string.notify_message_already_logged));
                    }
                    h.setMessage("Already logged in");
                }
            } catch(Exception e)
            {
                if(prefs.getBoolean(Constants.PREF_KEY_NOTIFY_WHEN_ERROR, true))
                {
                    createNotification(context.getString(R.string.notify_message_error));
                }
                Log.e(TAG, "Login failed", e);
                h.setSuccess(false);
                h.setMessage("Login failed: " + e.getMessage());
            }
            DBAccesser db = new DBAccesser(context);
            db.addHistoryItem(h);
        } else
        {
            Log.d(TAG, "Unknown SSID " + ssid);
        }
    }

    private String getTestURL(Context context, SharedPreferences settings)
    {
        String default_url = context.getString(R.string.defaulturl);
        String s = settings.getString(Constants.PREF_KEY_URL, default_url);        
        // Google is no longer blocked, replace it with default URL
        if(s == null || "http://www.google.com/".equalsIgnoreCase(s))
        {
            s = default_url;
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(Constants.PREF_KEY_URL, s);
            editor.commit();
        }
        s = s.trim();
        try
        {
            new URL(s);
            return s;
        } catch(MalformedURLException mex)
        {
            return default_url;
        }
    }

    private void createNotification(String message)
    {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new Notification(R.drawable.icon, context.getString(R.string.notify_title),
                System.currentTimeMillis());
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(context, context.getString(R.string.notify_title), message, contentIntent);
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(0, notification);
    }

}

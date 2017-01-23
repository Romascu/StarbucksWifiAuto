
package org.crocodile.sbautologin;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.content.Intent;
import android.os.*;

import org.crocodile.sbautologin.db.DBAccesser;
import org.crocodile.sbautologin.model.HistoryItem;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.RelativeLayout.LayoutParams;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

public class MainActivity extends Activity
{
    private static final String TAG       = "SbAutoLogin";
    private boolean             update    = true;
    private Object              monitor   = new Object();
    private boolean             needRetry = false;

    private InterstitialAd mInterstitialAd;
    AdView mAdView;

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch(item.getItemId())
        {
        case R.id.clear_hist_menu_item:
            mInterstitialAd.show();
            clearHistory();
            return true;
        case R.id.settings:
            mInterstitialAd.show();
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void clearHistory()
    {
        Log.d(TAG, "Clearing history");
        DBAccesser db = new DBAccesser(MainActivity.this);
        db.removeHistoryItems();
        synchronized(monitor)
        {
            monitor.notify();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if(android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        setContentView(R.layout.main);

        ToggleButton activeToggle = (ToggleButton) findViewById(R.id.active);
        SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, 0);
        
        activeToggle.setChecked(settings.getBoolean(Constants.PREF_KEY_ACTIVE, true));
        activeToggle.setOnClickListener(new OnClickListener() {
            public void onClick(View buttonView)
            {
                SharedPreferences settings = getSharedPreferences(Constants.PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(Constants.PREF_KEY_ACTIVE, ((ToggleButton) buttonView).isChecked());
                editor.commit();
                mInterstitialAd.show();
            }
        });

        // addTestData();

        mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)        // All emulators
                .addTestDevice("E9DEB34031182776A4E765DCEF19F10D")  // My phone
                .build();
        mAdView.loadAd(adRequest);


        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-6887589184636373/6896242043");
        AdRequest adRequestInterstial = new AdRequest.Builder()
                .addTestDevice("E9DEB34031182776A4E765DCEF19F10D")
                .build();
        mInterstitialAd.loadAd(adRequestInterstial);

//listner for adClosed
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                AdRequest adRequest = new AdRequest.Builder()
                        .addTestDevice("E9DEB34031182776A4E765DCEF19F10D")
                        .build();
                mInterstitialAd.loadAd(adRequest);
            }
        });

    }

    @Override
    public void onResume()
    {
        super.onResume();

        update = true;
        startUpdateThread();

        if(mAdView!=null){  // Check if Adview is not null in case of fist time load.
            mAdView.resume();}

    }

    @Override
    public void onPause()
    {
        mAdView.pause();
        super.onPause();

        synchronized(monitor)
        {
            update = false;
            monitor.notify();
        }
    }

    public void startUpdateThread()
    {
        new Thread(new Runnable() {
            public void run()
            {
                DBAccesser db = new DBAccesser(MainActivity.this);
                handler.sendEmptyMessage(0);
                long maxId = db.getMaxId();
                while(true)
                {
                    int newMaxId = db.getMaxId();
                    if(maxId < newMaxId || newMaxId == 0)
                    {
                        handler.sendEmptyMessage(0);
                        maxId = newMaxId;
                    } else if(needRetry)
                    {
                        needRetry = false;
                        handler.sendEmptyMessage(0);
                    }

                    try
                    {
                        synchronized(monitor)
                        {
                            monitor.wait(Constants.REFRESH_INTERVAL_MS);
                            if(!update)
                                break;
                        }
                    } catch(InterruptedException e)
                    {
                    }
                }
            }
        }).start();
    }

    public Handler handler = new Handler() {
                               @Override
                               public void handleMessage(Message msg)
                               {
                                   showHistory();
                               }
                           };

    private void showHistory()
    {
        DBAccesser db = new DBAccesser(this);
        ArrayList<HistoryItem> hist;
        try
        {
            hist = db.getHistoryItems(Constants.HIST_LEN);
        } catch(Exception e)
        {
            needRetry = true;
            return;
        }

        TableLayout histtable = (TableLayout) findViewById(R.id.histTable);
        histtable.setStretchAllColumns(true);
        histtable.setShrinkAllColumns(true);
        histtable.removeAllViews();
        if(hist.isEmpty())
        {
            TextView nohist = new TextView(this);
            nohist.setText(R.string.nohist);
            nohist.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            histtable.addView(nohist);
        } else
        {

            int i = 0;
            for(HistoryItem h : hist)
            {
                i++;
                RelativeLayout row = new RelativeLayout(this);
                row.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

                ImageView icon = new ImageView(this);
                icon.setImageResource(h.isSuccess() ? android.R.drawable.presence_online
                        : android.R.drawable.presence_busy);
                LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                icon.setLayoutParams(params);
                icon.setId(1019 + i * 3);
                row.addView(icon);

                TextView dateCell = new TextView(this);
                CharSequence ds = DateUtils.formatSameDayTime(h.getDate().getTime(), new Date().getTime(),
                        DateFormat.SHORT, DateFormat.SHORT);
                dateCell.setText(ds);
                params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.RIGHT_OF, 1019 + i * 3);
                dateCell.setLayoutParams(params);
                dateCell.setId(1020 + i * 3);
                row.addView(dateCell);

                TextView msgCell = new TextView(this);
                msgCell.setText(h.getMessage());
                params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.RIGHT_OF, 1020 + i * 3);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                msgCell.setLayoutParams(params);
                msgCell.setPadding(10, 0, 0, 0);
                row.addView(msgCell);

                histtable.addView(row);
            }
        }
    }

    @SuppressWarnings("unused")
    private void addTestData()
    {
        HistoryItem h = new HistoryItem();

        DBAccesser db = new DBAccesser(this);
        db.removeHistoryItems();
        for(int i = 0; i < 2; i++)
        {
            h.setDate(new Date());
            h.setSuccess(i % 2 == 0);
            h.setMessage("Attempt longgggg ggggggggggg gggggggggggggggggggggggg gggggggggggggggggggggggggggggggggg" + i);
            db.addHistoryItem(h);
        }
    }

}
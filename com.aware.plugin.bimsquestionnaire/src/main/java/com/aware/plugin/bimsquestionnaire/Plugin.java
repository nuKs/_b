package com.aware.plugin.bimsquestionnaire;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

// import android.os.SystemClock;
import android.util.Log;

import android.os.Binder;
import android.os.IBinder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Plugin extends Aware_Plugin {

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public Plugin getService() {
            // Return this instance of Plugin so clients can call public methods
            return Plugin.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //This allows plugin data to be synced on demand from broadcast Aware#ACTION_AWARE_SYNC_DATA
        AUTHORITY = Provider.getAuthority(this);

        TAG = "AWARE::" + getResources().getString(R.string.app_name);

        /**
         * Plugins share their current status, i.e., context using this method.
         * This method is called automatically when triggering
         * {@link Aware#ACTION_AWARE_CURRENT_CONTEXT}
         **/
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
            }
        };

        /*
        // @note not working ?
        //To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Provider.bimsquestionnaire_Data.CONTENT_URI };
        */
    }


    /**
     * Allow callback to other applications when data is stored in provider
     */
    private static AWARESensorObserver awareSensor;

    public static void setSensorObserver(AWARESensorObserver observer) {
        awareSensor = observer;
    }

    public static AWARESensorObserver getSensorObserver() {
        return awareSensor;
    }

    public interface AWARESensorObserver {
        void onDataChanged(ContentValues data);
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_bimsquestionnaire, true);

            //Enable our plugin's sync-adapter to upload the data to the server if part of a study
            if (Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE).length() >= 0 && !Aware.isSyncEnabled(this, Provider.getAuthority(this)) && Aware.isStudy(this) && getApplicationContext().getPackageName().equalsIgnoreCase("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
                Log.i(TAG, "sync! :)");
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Provider.getAuthority(this), 1);
                ContentResolver.addPeriodicSync(
                        Aware.getAWAREAccount(this),
                        Provider.getAuthority(this),
                        Bundle.EMPTY,
                        Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60
                );
            }

            //Initialise AWARE instance in plugin
            Aware.startAWARE(this);
        }

        return START_STICKY;
    }

    public void storeQuestionnaire(String questionnaireId, Map<String, Double> content) {
        String deviceId = Aware.getSetting(this, Aware_Preferences.DEVICE_ID);
        long timestamp = System.currentTimeMillis();

        Set<ContentValues> context_datas = new HashSet<ContentValues>(content.size());
        for (Map.Entry<String, Double> entry : content.entrySet()) {
            ContentValues context_data = new ContentValues();
            context_data.put(Provider.bimsquestionnaire_Data.TIMESTAMP, timestamp);
            context_data.put(Provider.bimsquestionnaire_Data.DEVICE_ID, deviceId);
            context_data.put(Provider.bimsquestionnaire_Data.QUESTIONNAIRE_ID, questionnaireId);
            context_data.put(Provider.bimsquestionnaire_Data.QUESTION_ID, entry.getKey());
            context_data.put(Provider.bimsquestionnaire_Data.VALUE, entry.getValue());
            context_datas.add(context_data);
            Log.i(TAG, String.format("Storing questionnaire %s data %s=%s", questionnaireId, entry.getKey(), entry.getValue()));
        }

        try {
            getApplicationContext().getContentResolver().bulkInsert(Provider.bimsquestionnaire_Data.CONTENT_URI, context_datas.toArray(new ContentValues[context_datas.size()]));
        } catch (SQLiteException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        } catch (SQLException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //Turn off the sync-adapter if part of a study
        if (Aware.isStudy(this) && (getApplicationContext().getPackageName().equalsIgnoreCase("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone))) {
            ContentResolver.removePeriodicSync(
                    Aware.getAWAREAccount(this),
                    Provider.getAuthority(this),
                    Bundle.EMPTY
            );
        }

        Aware.setSetting(this, Settings.STATUS_PLUGIN_bimsquestionnaire, false);

        //Stop AWARE instance in plugin
        Aware.stopAWARE(this);
    }

}
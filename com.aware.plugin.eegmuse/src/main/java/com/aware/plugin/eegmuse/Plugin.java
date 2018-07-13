package com.aware.plugin.eegmuse;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;

// import com.aware.Accelerometer;
import com.aware.Aware;
import com.aware.Aware_Preferences;
// import com.aware.Screen;
import com.aware.utils.Aware_Plugin;
import com.choosemuse.libmuse.*;

// import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
// import java.util.List;
import android.os.Binder;
import android.os.IBinder;

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

    /**
     * Tag used for logging purposes.
     */
    //private final String TAG = "TestLibMuseAndroid";

    /**
     * The MuseManager is how you detect Muse headbands and receive notifications
     * when the list of  available headbands changes.
     */
    private MuseManagerAndroid manager;

    /**
     * A Muse refers to a Muse headband.  Use this to connect/disconnect from the
     * headband, register listeners to receive EEG data and get headband
     * configuration and version information.
     */
    // private Muse muse;

    /**
     * The ConnectionListener will be notified whenever there is a change in
     * the connection state of a headband, for example when the headband connects
     * or disconnects.
     * <p>
     * Note that ConnectionListener is an inner class at the bottom of this file
     * that extends MuseConnectionListener.
     */
    // private ConnectionListener connectionListener;

    /**
     * The DataListener is how you will receive EEG (and other) data from the
     * headband.
     * <p>
     * Note that DataListener is an inner class at the bottom of this file
     * that extends MuseDataListener.
     */
    private DataListener dataListener;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "HHELLO Muse?");

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
        @note not working ?
        //To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Provider.Example_Data.CONTENT_URI };
        */

        //Add permissions you need (Android M+).
        //By default, AWARE asks access to the #Manifest.permission.WRITE_EXTERNAL_STORAGE

        //REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);


        /** MUSE STUFFS **/

        // Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
        // simplify the connection process.  This requires access to the COARSE_LOCATION
        // or FINE_LOCATION permissions.  Make sure we have these permissions before
        // proceeding.
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH_ADMIN);

        // We need to set the context on MuseManagerAndroid before we can do anything.
        // This must come before other LibMuse API calls as it also loads the library.
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        Log.i(TAG, "LibMuse version=" + LibmuseVersion.instance().getString());

        WeakReference<Plugin> weakPlugin = new WeakReference<Plugin>(this);

        // Register a listener to receive connection state changes.
        // connectionListener = new ConnectionListener(weakPlugin);
        // Register a listener to receive data from a Muse.
        dataListener = new DataListener(weakPlugin);

        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        // manager.setMuseListener(new MuseL(weakPlugin));
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
            Aware.setSetting(this, Settings.STATUS_PLUGIN_EEGMUSE, true);

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

    // start/stop record with autoconnnect to first available muse
    /*
    public void startRecording() {
        manager.startListening();
    }
    public void stopRecording() {
        manager.stopListening();
    }
    */
    // start/stop record without autoconnnect
    public void startRecording(Muse muse) {
        muse.registerDataListener(dataListener, MuseDataPacketType.EEG);
    }
    public void stopRecording(Muse muse) {
        muse.unregisterDataListener(dataListener, MuseDataPacketType.EEG);
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

        Aware.setSetting(this, Settings.STATUS_PLUGIN_EEGMUSE, false);

        //Stop AWARE instance in plugin
        Aware.stopAWARE(this);
    }

    //--------------------------------------
    // Listener translators
    //
    // Each of these classes extend from the appropriate listener and contain a weak reference
    // to the activity.  Each class simply forwards the messages it receives back to the Activity.
    /*
    class MuseL extends MuseListener {
        final WeakReference<Plugin> activityRef;

        MuseL(final WeakReference<Plugin> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void museListChanged() {
            activityRef.get().museListChanged();
        }
    }

    private void museListChanged() {
        final List<Muse> list = manager.getMuses();

        if (list.size() >= 1) {
            // Stop listening to list change
            // manager.stopListening();

            // Retrieve the first muse available
            final Muse m = list.get(0);

            // Log
            Log.i(TAG, "muse found:" + m.getName() + " - " + m.getMacAddress());

            // Unregister all prior listeners and register our data listener to
            // receive the MuseDataPacketTypes we are interested in.  If you do
            // not register a listener for a particular data type, you will not
            // receive data packets of that type.
            m.unregisterAllListeners();
            m.registerConnectionListener(connectionListener);
            m.registerDataListener(dataListener, MuseDataPacketType.EEG);
            // m.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE);
            // m.registerDataListener(dataListener, MuseDataPacketType.ACCELEROMETER);
            // m.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
            // m.registerDataListener(dataListener, MuseDataPacketType.DRL_REF);
            // m.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION);

            // Connect
            // m.runAsynchronously();
            new Thread(new Runnable() {
                public void run() {
                    m.connect();
                    while (true) {
                        // This should be called relatively frequently: max 250ms, ideally 20ms.
                        // cf. docs
                        m.execute();
                        SystemClock.sleep(100);
                    }
                    // @todo stop on disconnect event
                    // @todo thread safety
                }
            }).start();
        }
    }

    class ConnectionListener extends MuseConnectionListener {
        final WeakReference<Plugin> activityRef;

        ConnectionListener(final WeakReference<Plugin> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {
            activityRef.get().receiveMuseConnectionPacket(p, muse);
        }
    }

    private void receiveMuseConnectionPacket(MuseConnectionPacket p, Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = p.getPreviousConnectionState() + " -> " + current;
        Log.i(TAG, status);
    }
    */

    class DataListener extends MuseDataListener {
        final WeakReference<Plugin> activityRef;

        DataListener(final WeakReference<Plugin> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
            activityRef.get().receiveMuseDataPacket(p, muse);
        }

        @Override
        public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
            activityRef.get().receiveMuseArtifactPacket(p, muse);
        }
    }

    private void receiveMuseArtifactPacket(MuseArtifactPacket p, Muse muse) {
    }

    private void receiveMuseDataPacket(MuseDataPacket p, Muse muse) {
        String deviceId = Aware.getSetting(this, Aware_Preferences.DEVICE_ID);


        ContentValues context_data = new ContentValues();
        context_data.put(Provider.EEGMuse_Data.TIMESTAMP, p.timestamp());
        context_data.put(Provider.EEGMuse_Data.DEVICE_ID, deviceId);
        context_data.put(Provider.EEGMuse_Data.EEG1, p.getEegChannelValue(Eeg.EEG1));
        context_data.put(Provider.EEGMuse_Data.EEG2, p.getEegChannelValue(Eeg.EEG2));
        context_data.put(Provider.EEGMuse_Data.EEG3, p.getEegChannelValue(Eeg.EEG3));
        context_data.put(Provider.EEGMuse_Data.EEG4, p.getEegChannelValue(Eeg.EEG4));

        try {
            getApplicationContext().getContentResolver().insert(Provider.EEGMuse_Data.CONTENT_URI, context_data);
        } catch (SQLiteException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        } catch (SQLException e) {
            if (Aware.DEBUG) Log.d(TAG, e.getMessage());
        }

        // if (awareSensor != null) awareSensor.onSmartTagChanged(context_data);
    }
}
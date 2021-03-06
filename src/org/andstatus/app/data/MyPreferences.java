/**
 * Copyright (C) 2010-2012 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.data;

import org.andstatus.app.TimelineActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.util.MyLog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is central point of accessing SharedPreferences and other global objects, used by AndStatus
 * Noninstantiable class 
 * @author yvolk
 */
public class MyPreferences {
    private static final String TAG = MyPreferences.class.getSimpleName();
    private static volatile boolean initialized = false;
    /**
     * Single context object for which we will request SharedPreferences
     */
    private static Context context;
    /**
     * Name of the object that initialized the class
     */
    private static String initializedBy;
    /**
     * When preferences, loaded into this class, were changed
     */
    private static volatile long preferencesChangeTime = 0;
    private static MyDatabase db;

    /**
     * IDs used for testing purposes to identify instances of reference types.
     */
    private static final AtomicInteger prevInstanceId = new AtomicInteger(0);
    
    /**
     * This is sort of button to start verification of credentials
     */
    public static final String KEY_VERIFY_CREDENTIALS = "verify_credentials";

    public static final String KEY_HISTORY_SIZE = "history_size";
    public static final String KEY_HISTORY_TIME = "history_time";
    /**
     * Period of automatic updates in seconds
     */
    public static final String KEY_FETCH_PERIOD = "fetch_frequency";
    public static final String KEY_AUTOMATIC_UPDATES = "automatic_updates";
    public static final String KEY_RINGTONE_PREFERENCE = "notification_ringtone";
    public static final String KEY_CONTACT_DEVELOPER = "contact_developer";
    public static final String KEY_REPORT_BUG = "report_bug";
    public static final String KEY_CHANGE_LOG = "change_log";
    public static final String KEY_ABOUT_APPLICATION = "about_application";

    /**
     * System time when shared preferences were changed
     */
    public static final String KEY_PREFERENCES_CHANGE_TIME = "preferences_change_time";
    /**
     * Minimum logging level for the whole application (i.e. for any tag)
     */
    public static final String KEY_MIN_LOG_LEVEL = "min_log_level";
    /**
     * Use this dir: http://developer.android.com/reference/android/content/Context.html#getExternalFilesDir(java.lang.String)
     * (for API 8)
     */
    public static final String KEY_USE_EXTERNAL_STORAGE = "use_external_storage";
    /**
     * New value for #KEY_USE_EXTERNAL_STORAGE to e confirmed/processed
     */
    public static final String KEY_USE_EXTERNAL_STORAGE_NEW = "use_external_storage_new";
    /**
     * Is the timeline combined in {@link TimelineActivity} 
     */
    public static final String KEY_TIMELINE_IS_COMBINED = "timeline_is_combined";
    /**
     * Version code of last opened application (int) 
     */
    public static final String KEY_VERSION_CODE_LAST = "version_code_last";
    
    /**
     * System time when shared preferences were examined and took into account
     * by some receiver. We use this for the Service to track time when it
     * recreated alarms last time...
     */
    public static final String KEY_PREFERENCES_EXAMINE_TIME = "preferences_examine_time";

    private MyPreferences(){
        throw new AssertionError();
    }
    
    /**
     * This method should be called before any other operations in the application (as early as possible)
     * If the class was initialized but preferences changed later, all preferences are reloaded ("refresh")
     * @param context_in
     * @param object - object that initialized the class 
     * @return System time when preferences were last changed
     */
    public static long initialize(Context context_in, Object object ) {
        boolean justInitialized = false;
        String initializerName;
        if (object instanceof String) {
            initializerName = (String) object;
        } else {
            initializerName = object.getClass().getSimpleName();

        }
        if (initialized) {
            synchronized(TAG) {
                if (initialized) {
                    long preferencesChangeTime_last = getPreferencesChangeTime();
                    if (preferencesChangeTime != preferencesChangeTime_last) {
                        MyLog.v(TAG, "Preferences changed " + (java.lang.System.currentTimeMillis() - preferencesChangeTime_last)/1000 +  " seconds ago, refreshing...");
                        forget();
                    }
                }
            }
        }
        if (!initialized) {
            synchronized(TAG) {
                if (!initialized) {
                    initializedBy = initializerName;
                    Log.v(TAG, "Starting initialization by " + initializedBy);
                    if (context_in != null) {
                        // Maybe we should use context_in.getApplicationContext() ??
                        context = context_in.getApplicationContext();
                    
                        /* This may be useful to know from where the class was initialized
                        StackTraceElement[] elements = Thread.currentThread().getStackTrace(); 
                        for(int i=0; i<elements.length; i++) { 
                            Log.v(TAG, elements[i].toString()); 
                        }
                        */
                    
                        if ( context == null) {
                            Log.v(TAG, "getApplicationContext is null, trying the context_in itself: " + context_in.getClass().getName());
                            context = context_in;
                        }
                    }
                    if ( context != null) {
                        initialized = true;
                        preferencesChangeTime = getPreferencesChangeTime();

                        MyAccount.initialize();
                    }
                    justInitialized = initialized;
                    if (initialized) {
                        MyLog.v(TAG, "Initialized by " + initializedBy + " context: " + context.getClass().getName());
                    } else {
                        Log.e(TAG, "Failed to initialize by " + initializedBy);
                    }
                }
            }
        } 
        if (initialized && !justInitialized) {
            MyLog.v(TAG, "Already initialized by " + initializedBy +  " (called by: " + initializerName + ")");
        }
        return preferencesChangeTime;
    }

    public static Context initializeAndGetContext(Context context_in, java.lang.Object object ) {
        initialize(context_in, object);
        return getContext();
    }
    
    /**
     * @return Unique for this process integer, numbers are given in the order starting from 1
     */
    public static int nextInstanceId() {
        return prevInstanceId.incrementAndGet();
    }
    
    /**
     * Forget everything in order to reread from the sources if it will be needed
     * e.g. after configuration changes
     */
    public static void forget() {
        initialized = false;
        MyAccount.forget();
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing database " + e.getMessage());
            } finally {
                db = null;
            }
        }
        MyLog.forget();
        context = null;
        initializedBy = null;
    }
    
    public static boolean isInitialized() {
        return (initialized);
    }
    
    /**
     * @return DefaultSharedPreferences for this application
     */
    public static SharedPreferences getDefaultSharedPreferences() {
        if (context == null) {
            Log.e(TAG, "getDefaultSharedPreferences - Was not initialized yet");
            /* TODO: */
            StackTraceElement[] elements = Thread.currentThread().getStackTrace(); 
            for(int i=0; i<elements.length; i++) { 
                Log.v(TAG, elements[i].toString()); 
            }
            return null;
        } else {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    public static void setDefaultValues(int resId, boolean readAgain) {
        if (context == null) {
            Log.e(TAG, "setDefaultValues - Was not initialized yet");
        } else {
            PreferenceManager.setDefaultValues(context, resId, readAgain);
        }
    }
    
    public static SharedPreferences getSharedPreferences(String name, int mode) {
        if (context == null) {
            Log.e(TAG, "getSharedPreferences - Was not initialized yet");
            return null;
        } else {
            return context.getSharedPreferences(name, mode);
        }
    }

    /**
     *  Remember when last changes to the preferences were made
     */
    public static void setPreferencesChangedNow() {
        if (initialized) {
            getDefaultSharedPreferences()
            .edit()
            .putLong(KEY_PREFERENCES_CHANGE_TIME,
                    java.lang.System.currentTimeMillis()).commit();
        }
    }

    /**
     * @return System time when AndStatus preferences were last time changed. 
     * We take into account here time when accounts were added/removed...
     */
    public static long getPreferencesChangeTime() {
        long preferencesChangeTime = 0;
        if (initialized) {
            preferencesChangeTime = getDefaultSharedPreferences().getLong(KEY_PREFERENCES_CHANGE_TIME, 0);            
        }
        return preferencesChangeTime;
    }

    public static Context getContext() {
        return context;
    }

    public static MyDatabase getDatabase() {
        if (db == null) {
            if (context == null) {
                Log.e(TAG, "getDatabase - Was not initialized yet");
            } else {
                db = new MyDatabase(context);
            }
        }
        return db;
    }
    
    /**
     * Standard directory in which to place databases
     */
    public static String DIRECTORY_DATABASES = "databases";
    
    /**
     * This function works just like {@link android.content.Context#getExternalFilesDir
     * Context.getExternalFilesDir}, but it takes {@link #KEY_USE_EXTERNAL_STORAGE} into account,
     * so it returns directory either on internal or external storage.
     * 
     * @param type The type of files directory to return.  May be null for
     * the root of the files directory or one of
     * the following Environment constants for a subdirectory:
     * {@link android.os.Environment#DIRECTORY_PICTURES Environment.DIRECTORY_...} (since API 8),
     * {@link #DIRECTORY_DATABASES}
     * @param forcedUseExternalStorage if not null, use this value instead of stored in preferences as {@link #KEY_USE_EXTERNAL_STORAGE}
     * 
     * @return directory, already created for you OR null in case of error
     * @see <a href="http://developer.android.com/guide/topics/data/data-storage.html#filesExternal">filesExternal</a>
     */
    public static File getDataFilesDir(String type, Boolean forcedUseExternalStorage) {
        File baseDir = null;
        String pathToAppend = "";
        File dir = null;
        String textToLog = null;
        boolean useExternalStorage = false;
        if (context == null) {
            textToLog = "getDataFilesDir - Was not initialized yet";
        } else {
            if (forcedUseExternalStorage == null) {
                useExternalStorage = getDefaultSharedPreferences().getBoolean(KEY_USE_EXTERNAL_STORAGE, false); 
            } else {
                useExternalStorage = forcedUseExternalStorage;
            }
            if (useExternalStorage) {
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {    
                    // We can read and write the media
                    baseDir = Environment.getExternalStorageDirectory();
                    pathToAppend = "Android/data/" + context.getPackageName() + "/files";
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    textToLog = "getDataFilesDir - We can only read External storage";
                } else {    
                    textToLog = "getDataFilesDir - error accessing External storage";
                }                
            } else {
                baseDir = Environment.getDataDirectory();
                pathToAppend = "data/" + context.getPackageName();
            }
            if (baseDir != null) {
                if (type != null) {
                    pathToAppend = pathToAppend + "/" + type;
                }
                dir = new File(baseDir, pathToAppend);
                if (!dir.exists()) {
                    dir.mkdirs();
                    if (!dir.exists()) {
                        textToLog = "getDataFilesDir - Could not create '" + dir.getPath() + "'";
                        dir = null;
                    }
                }
            }
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                MyLog.v(TAG, (useExternalStorage ? "External" : "Internal") + " path: '" 
                + ( (dir == null) ? "(null)" : dir.getPath()) + "'");
            }
        }
        if (textToLog != null) {
            Log.i(TAG, textToLog);
        }
        
        return dir;
    }

    /**
     * Extends {@link android.content.ContextWrapper#getDatabasePath(java.lang.String)}
     * @param name The name of the database for which you would like to get its path.
     * @param forcedUseExternalStorage if not null, use this value instead of stored in preferences as {@link #KEY_USE_EXTERNAL_STORAGE}
     * @return
     */
    public static File getDatabasePath(String name, Boolean forcedUseExternalStorage) {
        File dbDir = MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DATABASES, forcedUseExternalStorage);
        File dbAbsolutePath = null;
        if (dbDir != null) {
            dbAbsolutePath = new File(dbDir.getPath() + "/" + name);
        }
        return dbAbsolutePath;
    }
    
    /**
     * Simple check that allows to prevent data access errors
     */
    public static boolean isDataAvailable() {
        return (getDataFilesDir(null, null) != null);
    }

    /**
     * Load the theme according to the preferences.
     */
    public static void loadTheme(String TAG, android.content.Context context) {
        boolean light = getDefaultSharedPreferences().getBoolean("appearance_light_theme", false);
        StringBuilder themeName = new StringBuilder();
        String name = getDefaultSharedPreferences().getString("theme", "AndStatus");
        if (name.indexOf("Theme.") > -1) {
            name = name.substring(name.indexOf("Theme."));
        }
        themeName.append("Theme.");
        if (light) {
            themeName.append("Light.");
        }
        themeName.append(name);
        int themeId = (int) context.getResources().getIdentifier(themeName.toString(), "style",
                "org.andstatus.app");
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "loadTheme; theme=\"" + themeName.toString() + "\"; id=" + Integer.toHexString(themeId));
        }
        context.setTheme(themeId);
    }
    
}

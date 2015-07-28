package app.com.example.android.sunshine.data;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.test.AndroidTestCase;

import app.com.example.android.sunshine.data.WeatherContract.LocationEntry;
import app.com.example.android.sunshine.data.WeatherContract.WeatherEntry;

public class TestProvider extends AndroidTestCase
{
    public static final String LOG_TAG = TestProvider.class.getSimpleName();

    /*
       This helper function deletes all records from both database tables using the ContentProvider.
       It also queries the ContentProvider to make sure that the database has been successfully
       deleted, so it cannot be used until the Query and Delete functions have been written
       in the ContentProvider.

       Students: Replace the calls to deleteAllRecordsFromDB with this one after you have written
       the delete functionality in the ContentProvider.
     */
    public void deleteAllRecordsFromProvider() {
        mContext.getContentResolver().delete(
                WeatherEntry.CONTENT_URI,
                null,
                null
        );
        mContext.getContentResolver().delete(
                LocationEntry.CONTENT_URI,
                null,
                null
        );

        Cursor cursor = mContext.getContentResolver().query(
                WeatherEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );
        assertEquals("Error: Records not deleted from Weather table during delete", 0, cursor.getCount());
        cursor.close();

        cursor = mContext.getContentResolver().query(
                LocationEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );
        assertEquals("Error: Records not deleted from Location table during delete", 0, cursor.getCount());
        cursor.close();
    }

    /*
       This helper function deletes all records from both database tables using the database
       functions only.  This is designed to be used to reset the state of the database until the
       delete functionality is available in the ContentProvider.
     */
    public void deleteAllRecordsFromDB() {
        WeatherDbHelper dbHelper = new WeatherDbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        db.delete(WeatherEntry.TABLE_NAME, null, null);
        db.delete(LocationEntry.TABLE_NAME, null, null);
        db.close();
    }

    /*
        Student: Refactor this function to use the deleteAllRecordsFromProvider functionality once
        you have implemented delete functionality there.
     */
    public void deleteAllRecords() {
        deleteAllRecordsFromDB();
    }

    // Since we want each test to start with a clean slate, run deleteAllRecords
    // in setUp (called by the test runner before each test).
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteAllRecords();
    }

    public void testProviderRegistry()
    {
        PackageManager packageManager = this.getContext().getPackageManager();

        // We define the component name based on the package name from the context and the
        // WeatherProvider class.
        ComponentName componentName = new ComponentName(this.getContext().getPackageName(),
                WeatherProvider.class.getName());
        try
        {
            ProviderInfo providerInfo = packageManager.getProviderInfo(componentName, 0);
            assertEquals("ERROR: WeatherProvider registered with authority: " +
                            providerInfo.authority + " instead of authority: " +
                            WeatherContract.CONTENT_AUTHORITY, providerInfo.authority,
                    WeatherContract.CONTENT_AUTHORITY);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            assertTrue("ERROR: WeatherProvider is not registered at " + this.getContext()
                            .getPackageName(), false);
        }
    }

    public void testGetType()
    {
        // content://com.example.android.sunshine.app/weather/
        String type = this.getContext().getContentResolver().getType(WeatherEntry.CONTENT_URI);

        // vnd.android.cursor.dir/com.example.android.sunshine.app/weather
        assertEquals("ERROR: the WeatherEntry CONTENT_URI should return WeatherEntry.CONTENT_TYPE",
                WeatherEntry.CONTENT_TYPE, type);

        String testLocation = "94074";
        // content://com.example.android.sunshine.app/weather/94074
        type = this.getContext().getContentResolver()
                .getType(WeatherEntry.buildWeatherLocation(testLocation));

        // vnd.android.cursor.dir/com.example.android.sunshine.app/weather
        assertEquals("ERROR: the WeatherEntry CONTENT_URI with location should return WeatherEntry.CONTENT_TYPE",
                WeatherEntry.CONTENT_TYPE, type);

        long testDate = 1419120000L; // December 21st, 2014
        // content://com.example.android.sunshine.app/weather/94074/20140612
        type = this.getContext().getContentResolver()
                .getType(WeatherEntry.buildWeatherLocationWithDate(testLocation, testDate));

        // vnd.android.cursor.item/com.example.android.sunshine.app/weather/1419120000
        assertEquals("ERROR: the WeatherEntry CONTENT_URI with location and date should return " +
                        "WeatherEntry.CONTENT_ITEM_TYPE", WeatherEntry.CONTENT_ITEM_TYPE, type);

        // content://com.example.android.sunshine.app/location/
        type = this.getContext().getContentResolver().getType(LocationEntry.CONTENT_URI);

        // vnd.android.cursor.dir/com.example.android.sunshine.app/location
        assertEquals("ERROR: the LocationEntry CONTENT_URI should return " +
                        "LocationEntry.CONTENT_TYPE", LocationEntry.CONTENT_TYPE, type);
    }

    public void testBasicWeatherQuery()
    {
        ContentValues locationValues = TestUtilities.createNorthPoleLocationValues();
        long locationRowId = TestUtilities.insertNorthPoleLocationValues(this.getContext());

        ContentValues weatherValues = TestUtilities.createWeatherValues(locationRowId);
        TestUtilities.insertWeatherValues(this.getContext(), locationRowId);

        Cursor weatherCursor = this.getContext().getContentResolver().query(
                WeatherEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        TestUtilities.validateCursor("testBasicWeatherQuery", weatherCursor, weatherValues);
    }

    public void testBasicLocationQueries()
    {
        ContentValues testValues = TestUtilities.createNorthPoleLocationValues();
        long locationRowId = TestUtilities.insertNorthPoleLocationValues(this.getContext());

        Cursor locationCursor = this.getContext().getContentResolver().query(
                LocationEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        TestUtilities.validateCursor("testBasicLocationQueries", locationCursor, testValues);

        if(Build.VERSION.SDK_INT >= 19)
        {
            assertEquals("ERROR: Location Query did not properly set NotificationUri",
                    locationCursor.getNotificationUri(), LocationEntry.CONTENT_URI);
        }
    }

    public void testInsertReadProvider()
    {
        //------------------------------------------------------------------------------------------
        ContentValues locationValues = TestUtilities.createNorthPoleLocationValues();
        TestUtilities.TestContentObserver testContentObserver
                = TestUtilities.getTestContentObserver();

        getContext().getContentResolver()
                .registerContentObserver(LocationEntry.CONTENT_URI, true, testContentObserver);
        Uri locationUri = getContext().getContentResolver()
                .insert(LocationEntry.CONTENT_URI, locationValues);
        testContentObserver.waitForNotificationOrFail();
        getContext().getContentResolver().unregisterContentObserver(testContentObserver);

        long locationRowId = ContentUris.parseId(locationUri);

        assertTrue(locationRowId != -1);

        Cursor cursor = getContext().getContentResolver().query(
                LocationEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        TestUtilities.validateCursor("testInsertReadProvider. ERROR: LocationEntry is not valid.",
                cursor, locationValues);

        //------------------------------------------------------------------------------------------
        ContentValues weatherValues = TestUtilities.createWeatherValues(locationRowId);
        testContentObserver = TestUtilities.getTestContentObserver();

        getContext().getContentResolver()
                .registerContentObserver(WeatherEntry.CONTENT_URI, true, testContentObserver);
        Uri weatherInsertUri = getContext().getContentResolver()
                .insert(WeatherEntry.CONTENT_URI, weatherValues);
        testContentObserver.waitForNotificationOrFail();
        getContext().getContentResolver().unregisterContentObserver(testContentObserver);

        assertTrue(weatherInsertUri != null);

        Cursor weatherCursor = getContext().getContentResolver().query(
                WeatherEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        TestUtilities.validateCursor("testInsertReadProvider. Error validating WeatherEntry insert.",
                weatherCursor, weatherValues);

        //------------------------------------------------------------------------------------------
        weatherValues.putAll(locationValues);
        //------------------------------------------------------------------------------------------
        weatherCursor = getContext().getContentResolver().query(
                WeatherEntry.buildWeatherLocation(TestUtilities.TEST_LOCATION),
                null,
                null,
                null,
                null
        );
        TestUtilities.validateCursor
                ("testInsertReadProvider. ERROR: joint of weather and location data is not valid.",
                        weatherCursor, weatherValues);

        //------------------------------------------------------------------------------------------
        weatherCursor = getContext().getContentResolver().query(
                WeatherEntry.buildWeatherLocationWithStartDate
                        (TestUtilities.TEST_LOCATION, TestUtilities.TEST_DATE),
                null,
                null,
                null,
                null
        );
        TestUtilities.validateCursor
                ("testInsertReadProvider. ERROR: joint of weather and location data with start " +
                                "date is not valid.", weatherCursor, weatherValues);

        //------------------------------------------------------------------------------------------
        weatherCursor = getContext().getContentResolver().query(
                WeatherEntry.buildWeatherLocationWithDate
                        (TestUtilities.TEST_LOCATION, TestUtilities.TEST_DATE),
                null,
                null,
                null,
                null
        );
        TestUtilities.validateCursor("testInsertReadProvider.  ERROR: joint of weather and " +
                        "location data for a specific date is not valid.",
                weatherCursor, weatherValues);
    }

    /*
        This test uses the provider to insert and then update the data. Uncomment this test to
        see if your update location is functioning correctly.
     */
//    public void testUpdateLocation() {
//        // Create a new map of values, where column names are the keys
//        ContentValues values = TestUtilities.createNorthPoleLocationValues();
//
//        Uri locationUri = mContext.getContentResolver().
//                insert(LocationEntry.CONTENT_URI, values);
//        long locationRowId = ContentUris.parseId(locationUri);
//
//        // Verify we got a row back.
//        assertTrue(locationRowId != -1);
//        Log.d(LOG_TAG, "New row id: " + locationRowId);
//
//        ContentValues updatedValues = new ContentValues(values);
//        updatedValues.put(LocationEntry._ID, locationRowId);
//        updatedValues.put(LocationEntry.COLUMN_CITY_NAME, "Santa's Village");
//
//        // Create a cursor with observer to make sure that the content provider is notifying
//        // the observers as expected
//        Cursor locationCursor = mContext.getContentResolver().query(LocationEntry.CONTENT_URI, null, null, null, null);
//
//        TestUtilities.TestContentObserver tco = TestUtilities.getTestContentObserver();
//        locationCursor.registerContentObserver(tco);
//
//        int count = mContext.getContentResolver().update(
//                LocationEntry.CONTENT_URI, updatedValues, LocationEntry._ID + "= ?",
//                new String[] { Long.toString(locationRowId)});
//        assertEquals(count, 1);
//
//        // Test to make sure our observer is called.  If not, we throw an assertion.
//        //
//        // Students: If your code is failing here, it means that your content provider
//        // isn't calling getContext().getContentResolver().notifyChange(uri, null);
//        tco.waitForNotificationOrFail();
//
//        locationCursor.unregisterContentObserver(tco);
//        locationCursor.close();
//
//        // A cursor is your primary interface to the query results.
//        Cursor cursor = mContext.getContentResolver().query(
//                LocationEntry.CONTENT_URI,
//                null,   // projection
//                LocationEntry._ID + " = " + locationRowId,
//                null,   // Values for the "where" clause
//                null    // sort order
//        );
//
//        TestUtilities.validateCursor("testUpdateLocation.  Error validating location entry update.",
//                cursor, updatedValues);
//
//        cursor.close();
//    }


    // Make sure we can still delete after adding/updating stuff
    //
    // Student: Uncomment this test after you have completed writing the delete functionality
    // in your provider.  It relies on insertions with testInsertReadProvider, so insert and
    // query functionality must also be complete before this test can be used.
//    public void testDeleteRecords() {
//        testInsertReadProvider();
//
//        // Register a content observer for our location delete.
//        TestUtilities.TestContentObserver locationObserver = TestUtilities.getTestContentObserver();
//        mContext.getContentResolver().registerContentObserver(LocationEntry.CONTENT_URI, true, locationObserver);
//
//        // Register a content observer for our weather delete.
//        TestUtilities.TestContentObserver weatherObserver = TestUtilities.getTestContentObserver();
//        mContext.getContentResolver().registerContentObserver(WeatherEntry.CONTENT_URI, true, weatherObserver);
//
//        deleteAllRecordsFromProvider();
//
//        // Students: If either of these fail, you most-likely are not calling the
//        // getContext().getContentResolver().notifyChange(uri, null); in the ContentProvider
//        // delete.  (only if the insertReadProvider is succeeding)
//        locationObserver.waitForNotificationOrFail();
//        weatherObserver.waitForNotificationOrFail();
//
//        mContext.getContentResolver().unregisterContentObserver(locationObserver);
//        mContext.getContentResolver().unregisterContentObserver(weatherObserver);
//    }


    static private final int BULK_INSERT_RECORDS_TO_INSERT = 10;
    static ContentValues[] createBulkInsertWeatherValues(long locationRowId) {
        long currentTestDate = 1L;//TestUtilities.TEST_DATE;
        long millisecondsInADay = 1000*60*60*24;
        ContentValues[] returnContentValues = new ContentValues[BULK_INSERT_RECORDS_TO_INSERT];

        for ( int i = 0; i < BULK_INSERT_RECORDS_TO_INSERT; i++, currentTestDate+= millisecondsInADay ) {
            ContentValues weatherValues = new ContentValues();
            weatherValues.put(WeatherEntry.COLUMN_LOCATION_KEY, locationRowId);
            weatherValues.put(WeatherEntry.COLUMN_DATE, currentTestDate);
            weatherValues.put(WeatherEntry.COLUMN_WIND_DIRECTION, 1.1);
            weatherValues.put(WeatherEntry.COLUMN_HUMIDITY, 1.2 + 0.01 * (float) i);
            weatherValues.put(WeatherEntry.COLUMN_PRESSURE, 1.3 - 0.01 * (float) i);
            weatherValues.put(WeatherEntry.COLUMN_MAX_TEMPERATURE, 75 + i);
            weatherValues.put(WeatherEntry.COLUMN_MIN_TEMPERATURE, 65 - i);
            weatherValues.put(WeatherEntry.COLUMN_DESCRIPTION, "Asteroids");
            weatherValues.put(WeatherEntry.COLUMN_WIND_SPEED, 5.5 + 0.2 * (float) i);
            weatherValues.put(WeatherEntry.COLUMN_WEATHER_ID, 321);
            returnContentValues[i] = weatherValues;
        }
        return returnContentValues;
    }

    // Student: Uncomment this test after you have completed writing the BulkInsert functionality
    // in your provider.  Note that this test will work with the built-in (default) provider
    // implementation, which just inserts records one-at-a-time, so really do implement the
    // BulkInsert ContentProvider function.
//    public void testBulkInsert() {
//        // first, let's create a location value
//        ContentValues testValues = TestUtilities.createNorthPoleLocationValues();
//        Uri locationUri = mContext.getContentResolver().insert(LocationEntry.CONTENT_URI, testValues);
//        long locationRowId = ContentUris.parseId(locationUri);
//
//        // Verify we got a row back.
//        assertTrue(locationRowId != -1);
//
//        // Data's inserted.  IN THEORY.  Now pull some out to stare at it and verify it made
//        // the round trip.
//
//        // A cursor is your primary interface to the query results.
//        Cursor cursor = mContext.getContentResolver().query(
//                LocationEntry.CONTENT_URI,
//                null, // leaving "columns" null just returns all the columns.
//                null, // cols for "where" clause
//                null, // values for "where" clause
//                null  // sort order
//        );
//
//        TestUtilities.validateCursor("testBulkInsert. Error validating LocationEntry.",
//                cursor, testValues);
//
//        // Now we can bulkInsert some weather.  In fact, we only implement BulkInsert for weather
//        // entries.  With ContentProviders, you really only have to implement the features you
//        // use, after all.
//        ContentValues[] bulkInsertContentValues = createBulkInsertWeatherValues(locationRowId);
//
//        // Register a content observer for our bulk insert.
//        TestUtilities.TestContentObserver weatherObserver = TestUtilities.getTestContentObserver();
//        mContext.getContentResolver().registerContentObserver(WeatherEntry.CONTENT_URI, true, weatherObserver);
//
//        int insertCount = mContext.getContentResolver().bulkInsert(WeatherEntry.CONTENT_URI, bulkInsertContentValues);
//
//        // Students:  If this fails, it means that you most-likely are not calling the
//        // getContext().getContentResolver().notifyChange(uri, null); in your BulkInsert
//        // ContentProvider method.
//        weatherObserver.waitForNotificationOrFail();
//        mContext.getContentResolver().unregisterContentObserver(weatherObserver);
//
//        assertEquals(insertCount, BULK_INSERT_RECORDS_TO_INSERT);
//
//        // A cursor is your primary interface to the query results.
//        cursor = mContext.getContentResolver().query(
//                WeatherEntry.CONTENT_URI,
//                null, // leaving "columns" null just returns all the columns.
//                null, // cols for "where" clause
//                null, // values for "where" clause
//                WeatherEntry.COLUMN_DATE + " ASC"  // sort order == by DATE ASCENDING
//        );
//
//        // we should have as many records in the database as we've inserted
//        assertEquals(cursor.getCount(), BULK_INSERT_RECORDS_TO_INSERT);
//
//        // and let's make sure they match the ones we created
//        cursor.moveToFirst();
//        for ( int i = 0; i < BULK_INSERT_RECORDS_TO_INSERT; i++, cursor.moveToNext() ) {
//            TestUtilities.validateCurrentRecord("testBulkInsert.  Error validating WeatherEntry " + i,
//                    cursor, bulkInsertContentValues[i]);
//        }
//        cursor.close();
//    }
}

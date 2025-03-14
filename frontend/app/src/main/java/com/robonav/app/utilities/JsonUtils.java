package com.robonav.app.utilities;


import static com.robonav.app.utilities.FragmentUtils.showMessage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.robonav.app.models.Robot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class JsonUtils {

    private static String getTokenFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE);
        return prefs.getString("JWT_TOKEN", null); // Default to empty string if not found
    }

    // Reads JSON file from the assets folder
    public static String loadJSONFromAsset(Context context, String fileName) {
        String json = null;
        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return json;
    }

    public static void saveJSONToFile(Context context, String fileName, String jsonData) throws IOException {
        // Open a file in the app's internal storage
        try (FileOutputStream fileOutputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            // Write the JSON string to the file
            fileOutputStream.write(jsonData.getBytes());
        }
        // Close the file output stream
    }
    // Loads JSON data from a file in the app's internal storage
    public static String loadJSONFromFile(Context context, String fileName) throws IOException {
        FileInputStream fileInputStream = context.openFileInput(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }
        reader.close();
        return jsonBuilder.toString();
    }






    public static CompletableFuture<List<String>> loadRobotNames(Context context) {
        // Initialize the CompletableFuture to return the result
        CompletableFuture<List<String>> future = new CompletableFuture<>();

        List<String> robotNames = new ArrayList<>();
        String robotUrl = ConfigManager.getBaseUrl() + "/api/protected/robot/robots";
        RequestQueue queue = Volley.newRequestQueue(context);

        JsonArrayRequest robotRequest = new JsonArrayRequest(Request.Method.GET, robotUrl, null,
                response -> {
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            Robot robot = new Robot(response.getJSONObject(i));
                            robotNames.add(robot.getName());
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    // Complete the future when robot names are loaded
                    future.complete(robotNames);
                },
                error -> {
                    error.printStackTrace();
                    // If there's an error, complete the future exceptionally
                    future.completeExceptionally(error);
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                // Correct the header map type
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + getTokenFromPrefs(context));
                return headers;
            }
        };

        queue.add(robotRequest);

        return future;  // Return the CompletableFuture
    }












    // Helper method to convert JSON array of tasks to List<String>
    public static List<String> jsonArrayToList(JSONArray jsonArray) throws JSONException {
        List<String> taskList = new ArrayList<>();
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                taskList.add(jsonArray.getString(i));
            }
        }
        return taskList;
    }

    public static List<Robot> loadRobotsWithLocations(Context context) {
        List<Robot> robotsWithLocations = new ArrayList<>();
        String robotJson = loadJSONFromAsset(context, "robots.json");
        try {
            JSONArray robots = new JSONArray(robotJson);
            for (int i = 0; i < robots.length(); i++) {
                Robot robot = new Robot(robots.getJSONObject(i));
                if (!robot.getLocationName().isEmpty()) {
                    robotsWithLocations.add(robot);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            //showMessage("Error loading robots: " + e.getMessage(),context);
        }
        return robotsWithLocations;
    }



    public static List<Robot> loadAllRobots(Context context) {
        List<Robot> robots = new ArrayList<>();
        try {
            String robotsJson = JsonUtils.loadJSONFromAsset(context, "robots.json");
            JSONArray robotsArray = new JSONArray(robotsJson);
            for (int i = 0; i < robotsArray.length(); i++) {
                robots.add(new Robot(robotsArray.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            //showMessage("Error loading robots: " + e.getMessage(),context);
        }
        return robots;
    }

    public static List<String> loadLocationNames(Context context) {
        List<String> locationNames = new ArrayList<>();
        try {
            String locationJson = JsonUtils.loadJSONFromAsset(context, "locations.json");
            JSONArray locationsArray = new JSONArray(locationJson);
            for (int i = 0; i < locationsArray.length(); i++) {
                JSONObject location = locationsArray.getJSONObject(i);
                locationNames.add(location.getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            //showMessage("Error loading locations: " + e.getMessage(),context);
        }
        return locationNames;
    }
    public static CompletableFuture<List<JSONObject>> loadLocationDetails(Context context, String robotId) {
        CompletableFuture<List<JSONObject>> future = new CompletableFuture<>();
        List<JSONObject> locationDetails = new ArrayList<>();
        String locationUrl = ConfigManager.getBaseUrl() + "/api/protected/robot/" + robotId + "/location";
        RequestQueue queue = Volley.newRequestQueue(context);

        JsonArrayRequest locationRequest = new JsonArrayRequest(Request.Method.GET, locationUrl, null,
                response -> {
                    try {
                        // Iterate through the JSONArray directly
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject location = response.getJSONObject(i);

                            JSONObject locationInfo = new JSONObject();
                            locationInfo.put("location_name", location.getString("location_name"));
                            locationInfo.put("location_coordinates", location.getString("location_coordinates"));

                            locationDetails.add(locationInfo);
                        }

                        Log.d("LocationDetails", locationDetails.toString());
                        future.complete(locationDetails);

                    } catch (JSONException e) {
                        Log.e("LocationDetailsError", "Error parsing JSON", e);
                        future.completeExceptionally(e);
                    } catch (Exception e) {
                        Log.e("LocationDetailsError", "Error occurred", e);
                        future.completeExceptionally(e);
                    }
                },
                error -> {
                    Log.e("LocationDetailsError", "Network error", error);
                    future.completeExceptionally(error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                String token = getTokenFromPrefs(context);
                Log.d("AuthToken", "Token: " + token);
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        queue.add(locationRequest);

        return future;
    }













    public static String getCoordinatesForLocation(String locationName, Context context) {
        try {
            String locationJson = JsonUtils.loadJSONFromAsset(context, "locations.json");
            JSONArray locationsArray = new JSONArray(locationJson);
            for (int i = 0; i < locationsArray.length(); i++) {
                JSONObject location = locationsArray.getJSONObject(i);
                if (location.getString("name").equals(locationName)) {
                    return location.getString("coordinates");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }
    public static List<String> getAllLocations(Context context) {
        List<String> locations = new ArrayList<>();
        try {
            String locationJson = JsonUtils.loadJSONFromAsset(context, "locations.json");
            JSONArray locationsArray = new JSONArray(locationJson);
            for (int i = 0; i < locationsArray.length(); i++) {
                JSONObject location = locationsArray.getJSONObject(i);
                String name = location.getString("name");
                String coordinates = location.getString("coordinates");
                locations.add(name + " (Coordinates: " + coordinates + ")");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showMessage("Error retrieving locations: " + e.getMessage(),context);
        }
        return locations;
    }

    public static List<String> getAvailableMapFiles() {
        // Simulate a list of map files
        List<String> mapFiles = new ArrayList<>();
        mapFiles.add("map_file_1.json");
        mapFiles.add("map_file_2.json");
        mapFiles.add("map_file_3.json");
        return mapFiles;

        // You can replace this with actual file browsing logic if needed
    }
}
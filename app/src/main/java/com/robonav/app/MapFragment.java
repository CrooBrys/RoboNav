package com.robonav.app;

import static com.robonav.app.JsonUtils.saveJSONToFile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment {

    private Spinner actionSpinner;
    private View dynamicContentContainer;
    private GoogleMap googleMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        actionSpinner = view.findViewById(R.id.action_spinner);
        dynamicContentContainer = view.findViewById(R.id.dynamic_content_container);

        // Initialize the map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_view);
        if (mapFragment != null) {
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(@NonNull GoogleMap map) {
                    googleMap = map;
                }
            });
        }

        setupDropdownMenu();
        return view;
    }

    private void setupDropdownMenu() {
        String[] actions = {
                "Check Robot Position",
                "Set Initial Robot Position",
                "Save Current Location",
                "Remove All Locations",
                "Get Location by Name",
                "Get All Locations",
                "Get Current Map",
                "Swap Map"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, actions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(adapter);

        actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                handleActionSelection(actions[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Optional: Clear dynamic content if nothing is selected
                clearDynamicContent();
            }
        });
    }

    private void handleActionSelection(String action) {
        clearDynamicContent();
        Spinner robotDropdown;
        switch (action) {

            case "Check Robot Position":
                inflateContent(R.layout.dynamic_check_position);

                // Initialize components
                robotDropdown = dynamicContentContainer.findViewById(R.id.robot_dropdown);
                TextView locationNameTextView = dynamicContentContainer.findViewById(R.id.robot_location_name);
                TextView locationCoordinatesTextView = dynamicContentContainer.findViewById(R.id.robot_location_coordinates);
                Button btnCheckPosition = dynamicContentContainer.findViewById(R.id.btn_check_position);

                // Load robot names into the dropdown
                List<Robot> allRobots = loadAllRobots();
                List<String> robotNames = new ArrayList<>();
                for (Robot robot : allRobots) {
                    robotNames.add(robot.getName());
                }

                ArrayAdapter<String> robotAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, robotNames);
                robotAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                robotDropdown.setAdapter(robotAdapter);

                // Dropdown selection listener to display robot location
                robotDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String selectedRobotName = robotDropdown.getSelectedItem().toString();
                        Robot selectedRobot = findRobotByName(selectedRobotName, allRobots);

                        if (selectedRobot != null) {
                            // Display location details
                            locationNameTextView.setText("Location Name: " + selectedRobot.getLocationName());
                            locationCoordinatesTextView.setText("Coordinates: " + selectedRobot.getLocationCoordinates());
                        } else {
                            locationNameTextView.setText("Location Name: Not found");
                            locationCoordinatesTextView.setText("Coordinates: Not found");
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        locationNameTextView.setText("Location Name: ");
                        locationCoordinatesTextView.setText("Coordinates: ");
                    }
                });

                // Button to confirm checking position
                btnCheckPosition.setOnClickListener(v -> {
                    String selectedRobotName = robotDropdown.getSelectedItem().toString();
                    Robot selectedRobot = findRobotByName(selectedRobotName, allRobots);

                    if (selectedRobot != null) {
                        appendOutput("Checked Position for " + selectedRobot.getName() +
                                "\nLocation Name: " + selectedRobot.getLocationName() +
                                "\nCoordinates: " + selectedRobot.getLocationCoordinates());
                    } else {
                        showMessage("Please select a robot with a valid location.");
                    }
                });
                break;

            case "Set Initial Robot Position":
                inflateContent(R.layout.dynamic_set_position);

                // Initialize components
                robotDropdown = dynamicContentContainer.findViewById(R.id.robot_dropdown);
                Spinner preExistingLocationDropdown = dynamicContentContainer.findViewById(R.id.pre_existing_location_dropdown);
                EditText inputCoordinates = dynamicContentContainer.findViewById(R.id.input_coordinates);
                Button btnSetPosition = dynamicContentContainer.findViewById(R.id.btn_set_initial_position);

                // Load robots into dropdown
                robotNames = loadRobotNames();
                robotAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, robotNames);
                robotAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                robotDropdown.setAdapter(robotAdapter);

                // Load pre-existing locations into dropdown
                List<String> locationNames = loadLocationNames();
                ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, locationNames);
                locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                preExistingLocationDropdown.setAdapter(locationAdapter);

                // Set coordinates when a pre-existing location is selected
                preExistingLocationDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String selectedLocation = preExistingLocationDropdown.getSelectedItem().toString();
                        String coordinates = getCoordinatesForLocation(selectedLocation);
                        inputCoordinates.setText(coordinates); // Automatically fill coordinates
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        inputCoordinates.setText(""); // Clear coordinates if nothing is selected
                    }
                });

                // Handle Set Initial Position button click
                btnSetPosition.setOnClickListener(v -> {
                    String selectedRobot = robotDropdown.getSelectedItem() != null ? robotDropdown.getSelectedItem().toString() : "";
                    String coordinates = inputCoordinates.getText().toString();

                    if (coordinates.isEmpty() || selectedRobot.isEmpty()) {
                        showMessage("Please select a robot and provide coordinates.");
                        return;
                    }

                    handleSetPosition(selectedRobot, coordinates);
                });
                break;
            case "Save Current Location":
                inflateContent(R.layout.dynamic_save_location);

                // Initialize components
                robotDropdown = dynamicContentContainer.findViewById(R.id.robot_dropdown);
                TextView robotLocationName = dynamicContentContainer.findViewById(R.id.robot_location_name);
                Button btnSaveLocation = dynamicContentContainer.findViewById(R.id.btn_save_location);

                // Load robots with non-empty location fields
                List<Robot> robotsWithLocations = loadRobotsWithLocations();
                robotNames = new ArrayList<>();
                for (Robot robot : robotsWithLocations) {
                    robotNames.add(robot.getName());
                }

                // Populate the dropdown
                robotAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, robotNames);
                robotAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                robotDropdown.setAdapter(robotAdapter);

                // Handle robot selection
                robotDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String selectedRobot = robotDropdown.getSelectedItem().toString();
                        Robot selectedRobotObj = findRobotByName(selectedRobot, robotsWithLocations);

                        if (selectedRobotObj != null) {
                            // Update the location name dynamically
                            String location = selectedRobotObj.getLocationName();
                            if (!location.isEmpty()) {
                                robotLocationName.setText("Location Name: " + location);
                            } else {
                                robotLocationName.setText("No location found for this robot.");
                            }
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        robotLocationName.setText("");
                    }
                });

                // Handle Save Location button click
                btnSaveLocation.setOnClickListener(v -> {
                    String selectedRobot = robotDropdown.getSelectedItem() != null ? robotDropdown.getSelectedItem().toString() : "";

                    if (selectedRobot.isEmpty()) {
                        showMessage("Please select a robot.");
                        return;
                    }

                    Robot selectedRobotObj = findRobotByName(selectedRobot, robotsWithLocations);
                    if (selectedRobotObj != null && !selectedRobotObj.getLocationName().isEmpty()) {
                        saveLocation(selectedRobotObj);
                    } else {
                        showMessage("No valid location found for the selected robot.");
                    }
                });
                break;
            case "Get All Locations":
                inflateContent(R.layout.dynamic_get_all_locations);

                // Initialize components
                Button btnShowAllLocations = dynamicContentContainer.findViewById(R.id.btn_show_all_locations);
                TextView allLocationsOutput = dynamicContentContainer.findViewById(R.id.all_locations_output);

                // Handle button click
                btnShowAllLocations.setOnClickListener(v -> {
                    List<String> allLocations = getAllLocations();
                    if (allLocations.isEmpty()) {
                        allLocationsOutput.setText("No locations found.");
                    } else {
                        StringBuilder output = new StringBuilder("All Locations:\n");
                        for (String location : allLocations) {
                            output.append(location).append("\n");
                        }
                        allLocationsOutput.setText(output.toString());
                    }
                });
                break;
            case "Remove All Locations":
                inflateContent(R.layout.dynamic_remove_all_locations);

                // Initialize button
                Button btnRemoveAllLocations = dynamicContentContainer.findViewById(R.id.btn_remove_all_locations);

                // Handle button click
                btnRemoveAllLocations.setOnClickListener(v -> {
                    try {
                        // Load existing robots.json
                        String robotsJson = JsonUtils.loadJSONFromAsset(requireContext(), "robots.json");
                        JSONArray robots = new JSONArray(robotsJson);

                        // Update each robot's location to an empty string
                        for (int i = 0; i < robots.length(); i++) {
                            JSONObject robot = robots.getJSONObject(i);
                            robot.put("location", "");
                        }

                        // Save the updated robots.json
                        JsonUtils.saveJSONToFile(requireContext(), "robots.json", robots.toString());

                        // Notify user
                        showMessage("All robot locations have been cleared.");
                    } catch (Exception e) {
                        e.printStackTrace();
                        showMessage("Error clearing locations: " + e.getMessage());
                    }
                });
                break;

            case "Get Location by Name":
                inflateContent(R.layout.dynamic_get_location_by_name);

                // Initialize components
                Spinner locationDropdown = dynamicContentContainer.findViewById(R.id.location_dropdown);
                Button btnGetCoordinates = dynamicContentContainer.findViewById(R.id.btn_get_coordinates);
                TextView coordinatesOutput = dynamicContentContainer.findViewById(R.id.coordinates_output);

                // Load location names into dropdown
                locationNames = loadLocationNames();
                locationAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, locationNames);
                locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                locationDropdown.setAdapter(locationAdapter);

                // Handle Get Coordinates button click
                btnGetCoordinates.setOnClickListener(v -> {
                    String selectedLocation = locationDropdown.getSelectedItem() != null ? locationDropdown.getSelectedItem().toString() : "";

                    if (selectedLocation.isEmpty()) {
                        showMessage("Please select a location.");
                        return;
                    }

                    // Retrieve and display coordinates for the selected location
                    String coordinates = getCoordinatesForLocation(selectedLocation);
                    if (!coordinates.isEmpty()) {
                        coordinatesOutput.setText("Coordinates: " + coordinates);
                    } else {
                        coordinatesOutput.setText("Coordinates not found for the selected location.");
                    }
                });
                break;
            case "Get Current Map":
                inflateContent(R.layout.dynamic_get_current_map);

                // Initialize components
                Button btnRetrieveMapFile = dynamicContentContainer.findViewById(R.id.btn_retrieve_map_file);
                TextView mapFileStatus = dynamicContentContainer.findViewById(R.id.map_file_status);

                // Simulate map file retrieval
                btnRetrieveMapFile.setOnClickListener(v -> {
                    String simulatedMapFileName = "current_map.json"; // Simulated map file name
                    String simulatedMapDetails = "Map Size: 5MB, Updated: 2024-11-28"; // Simulated metadata

                    // Update the output TextView with a simulated response
                    mapFileStatus.setText("Map file retrieved successfully:\n" +
                            "File Name: " + simulatedMapFileName + "\n" +
                            simulatedMapDetails);
                });
                break;
            case "Swap Map":
                inflateContent(R.layout.dynamic_swap_map);

                // Initialize components
                Spinner mapFileDropdown = dynamicContentContainer.findViewById(R.id.map_file_dropdown);
                Button btnSwapMap = dynamicContentContainer.findViewById(R.id.btn_swap_map);
                TextView swapMapStatus = dynamicContentContainer.findViewById(R.id.swap_map_status);

                // Simulate available map files (you could replace this with actual file browsing logic)
                List<String> availableMapFiles = getAvailableMapFiles();
                ArrayAdapter<String> mapFileAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, availableMapFiles);
                mapFileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mapFileDropdown.setAdapter(mapFileAdapter);

                // Handle Swap Map button click
                btnSwapMap.setOnClickListener(v -> {
                    String selectedMapFile = mapFileDropdown.getSelectedItem() != null ? mapFileDropdown.getSelectedItem().toString() : "";

                    if (selectedMapFile.isEmpty()) {
                        swapMapStatus.setText("No map file selected. Please choose a map file.");
                        return;
                    }

                    // Simulate swapping map files
                    swapMapStatus.setText("Map file swapped successfully to: " + selectedMapFile);
                });
                break;
            default:
                break;
        }

    }

    private void handleSetPosition(String selectedRobot, String coordinates) {
        String[] latLng = coordinates.split(",");
        if (latLng.length != 2) {
            showMessage("Invalid coordinates. Please enter in 'Latitude, Longitude' format.");
            return;
        }

        try {
            double latitude = Double.parseDouble(latLng[0].trim());
            double longitude = Double.parseDouble(latLng[1].trim());
            LatLng position = new LatLng(latitude, longitude);

            if (googleMap != null) {
                googleMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(selectedRobot + "'s Checkpoint")
                        .snippet("Initial Position Set"));
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 10));
            }

            appendOutput("Initial position set for " + selectedRobot + " at coordinates: " + coordinates);

        } catch (NumberFormatException e) {
            showMessage("Invalid coordinates. Please enter numeric values for Latitude and Longitude.");
        }
    }

    private List<String> loadRobotNames() {
        List<String> robotNames = new ArrayList<>();
        String robotJson = JsonUtils.loadJSONFromAsset(requireContext(), "robots.json");
        try {
            JSONArray robots = new JSONArray(robotJson);
            for (int i = 0; i < robots.length(); i++) {
                Robot robot = new Robot(robots.getJSONObject(i));
                robotNames.add(robot.getName());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showMessage("Error loading robots: " + e.getMessage());
        }
        return robotNames;
    }

    private void clearDynamicContent() {
        ((ViewGroup) dynamicContentContainer).removeAllViews();
    }

    private void inflateContent(int layoutResId) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View view = inflater.inflate(layoutResId, (ViewGroup) dynamicContentContainer, false);
        ((ViewGroup) dynamicContentContainer).addView(view);
    }

    private void showMessage(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void appendOutput(String message) {
        // Assuming an output box exists in your layout
        View rootView = getView();
        if (rootView != null) {
            TextView outputBox = rootView.findViewById(R.id.output_text_view);
            String currentOutput = outputBox.getText().toString();
            if (currentOutput.isEmpty()) {
                outputBox.setText(currentOutput + message);
            }
            else {
                outputBox.setText(currentOutput + "\n\n" + message );

            }
        }
    }
    private List<Robot> loadRobotsWithLocations() {
        List<Robot> robotsWithLocations = new ArrayList<>();
        String robotJson = JsonUtils.loadJSONFromAsset(requireContext(), "robots.json");
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
            showMessage("Error loading robots: " + e.getMessage());
        }
        return robotsWithLocations;
    }
    private Robot findRobotByName(String name, List<Robot> robots) {
        for (Robot robot : robots) {
            if (robot.getName().equals(name)) {
                return robot;
            }
        }
        return null;
    }
    private void saveLocation(Robot robot) {
        try {
            // Step 1: Load existing locations.json
            String locationJson;
            File file = new File(requireContext().getFilesDir(), "locations.json");
            JSONArray locations;

            if (file.exists()) {
                locationJson = JsonUtils.loadJSONFromFile(requireContext(), "locations.json");
                locations = new JSONArray(locationJson);
            } else {
                locations = new JSONArray();
            }

            // Step 2: Create new location object
            JSONObject newLocation = new JSONObject();
            newLocation.put("name", "Checkpoint for " + robot.getName());
            newLocation.put("coordinates", robot.getLocationCoordinates());
            newLocation.put("robots", new JSONArray().put(robot.getId()));

            // Step 3: Add the new location to the array
            locations.put(newLocation);

            // Step 4: Save updated JSON array back to locations.json
            JsonUtils.saveJSONToFile(requireContext(), "locations.json", locations.toString());

            // Step 5: Notify user and update UI
            showMessage("Location saved successfully.");
            appendOutput("Saved location for " + robot.getName() + " at coordinates: " + robot.getLocationCoordinates());
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error saving location: " + e.getMessage());
        }
    }
    private List<Robot> loadAllRobots() {
        List<Robot> robots = new ArrayList<>();
        try {
            String robotsJson = JsonUtils.loadJSONFromAsset(requireContext(), "robots.json");
            JSONArray robotsArray = new JSONArray(robotsJson);
            for (int i = 0; i < robotsArray.length(); i++) {
                robots.add(new Robot(robotsArray.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error loading robots: " + e.getMessage());
        }
        return robots;
    }
    private List<String> loadLocationNames() {
        List<String> locationNames = new ArrayList<>();
        try {
            String locationJson = JsonUtils.loadJSONFromAsset(requireContext(), "locations.json");
            JSONArray locationsArray = new JSONArray(locationJson);
            for (int i = 0; i < locationsArray.length(); i++) {
                JSONObject location = locationsArray.getJSONObject(i);
                locationNames.add(location.getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showMessage("Error loading locations: " + e.getMessage());
        }
        return locationNames;
    }

    private String getCoordinatesForLocation(String locationName) {
        try {
            String locationJson = JsonUtils.loadJSONFromAsset(requireContext(), "locations.json");
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
    private List<String> getAllLocations() {
        List<String> locations = new ArrayList<>();
        try {
            String locationJson = JsonUtils.loadJSONFromAsset(requireContext(), "locations.json");
            JSONArray locationsArray = new JSONArray(locationJson);
            for (int i = 0; i < locationsArray.length(); i++) {
                JSONObject location = locationsArray.getJSONObject(i);
                String name = location.getString("name");
                String coordinates = location.getString("coordinates");
                locations.add(name + " (Coordinates: " + coordinates + ")");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showMessage("Error retrieving locations: " + e.getMessage());
        }
        return locations;
    }

    private List<String> getAvailableMapFiles() {
        // Simulate a list of map files
        List<String> mapFiles = new ArrayList<>();
        mapFiles.add("map_file_1.json");
        mapFiles.add("map_file_2.json");
        mapFiles.add("map_file_3.json");
        return mapFiles;

        // You can replace this with actual file browsing logic if needed
    }
}

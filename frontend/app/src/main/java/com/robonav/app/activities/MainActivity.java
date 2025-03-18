package com.robonav.app.activities;

import com.robonav.app.utilities.ConfigManager;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;  // Import Log
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.robonav.app.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity"; // Log tag
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Toast currentToast; // Store the latest toast reference

    private static final String LOGIN_URL = ConfigManager.getBaseUrl() + "/api/open/users/login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Check token validity before initializing the UI
        if (isTokenValid()) {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: MainActivity started");

        // Initialize the views
        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        Button loginButton = findViewById(R.id.loginButton);
        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);
        TextView signUpText = findViewById(R.id.signUpText);

        // Underline the "Forgot Password?" and "Sign Up" text
        forgotPasswordText.setPaintFlags(forgotPasswordText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        signUpText.setPaintFlags(signUpText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Pre-fill username if provided
        Intent intent = getIntent();
        String prefilledUsername = intent.getStringExtra("username");
        if (prefilledUsername != null && !prefilledUsername.isEmpty()) {
            usernameEditText.setText(prefilledUsername);
            Log.d(TAG, "Prefilled username: " + prefilledUsername);
        }

        // Set up click listeners
        forgotPasswordText.setOnClickListener(v -> {
            Log.d(TAG, "Forgot Password clicked");
            Intent forgotPasswordIntent = new Intent(MainActivity.this, ForgotPasswordActivity.class);
            startActivity(forgotPasswordIntent);
        });

        signUpText.setOnClickListener(v -> {
            Log.d(TAG, "Sign Up clicked");
            Intent signUpIntent = new Intent(MainActivity.this, SignUpActivity.class);
            startActivity(signUpIntent);
        });

        loginButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            Log.d(TAG, "Login button clicked. Username: " + username);

            if (!areInputsValid(username, password)) return;

            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Logging in...");
            progressDialog.show();

            // Create JSON request body
            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("username", username);
                jsonBody.put("password", password);
                Log.d(TAG, "JSON request body: " + jsonBody.toString());
            } catch (JSONException e) {
                Log.e(TAG, "JSON creation error", e);
            }

            // Send JSON request
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, LOGIN_URL, jsonBody,
                    response -> {
                        progressDialog.dismiss();
                        Log.d(TAG, "Server Response: " + response.toString());
                        try {
                            if (response.has("token")) {
                                String token = response.getString("token");

                                // Save token (SharedPreferences for later use)
                                getSharedPreferences("APP_PREFS", MODE_PRIVATE)
                                        .edit()
                                        .putString("JWT_TOKEN", token)
                                        .apply();

                                showToast(response.getString("message"));

                                // Navigate to HomeActivity
                                Intent homeIntent = new Intent(MainActivity.this, HomeActivity.class);
                                homeIntent.putExtra("username", username);
                                startActivity(homeIntent);
                                finish();
                            } else {
                                Log.e(TAG, "Token missing in response");
                                showToast("Login failed. Try again.");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing response", e);
                            showToast("Error parsing response");
                        }
                    }, error -> {
                progressDialog.dismiss();
                Log.e(TAG, "Volley error: " + error.toString());

                // Default error message
                String errorMessage = "An error occurred. Please try again.";

                // Check if the error has a network response
                if (error.networkResponse != null) {
                    try {
                        String responseBody = new String(error.networkResponse.data, "UTF-8");
                        JSONObject errorResponse = new JSONObject(responseBody);
                        Log.e(TAG, "Error response from server: " + responseBody);

                        if (errorResponse.has("message")) {
                            errorMessage = errorResponse.getString("message");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                }

                showToast(errorMessage);
            }) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    Log.d(TAG, "Request Headers: " + headers.toString());
                    return headers;
                }
            };

            RequestQueue queue = Volley.newRequestQueue(this);
            queue.add(request);
        });
    }

    private boolean isTokenValid() {
        SharedPreferences prefs = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        if (token == null || token.isEmpty()) {
            return false; // No token stored
        }

        // Token format validation
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false; // Invalid JWT format
        }

        // Decode and check expiration
        try {
            String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT));
            JSONObject payloadJson = new JSONObject(payload);
            long exp = payloadJson.optLong("exp", 0);
            long currentTime = System.currentTimeMillis() / 1000;
            if (exp <= currentTime) {
                prefs.edit().remove("JWT_TOKEN").apply();
            }
            return exp > currentTime; // Token is valid
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Validate username and password
    private boolean areInputsValid(String username, String password) {
        Log.d(TAG, "Validating inputs: " + username + " / " + password);

        if (username.isEmpty() || password.isEmpty()) {
            showToast("Please fill in both fields");
            return false;
        }

        if (!isValidUsername(username)) {
            showToast("Username must be between 4-20 alphanumeric characters.");
            return false;
        }

        if (!isValidPassword(password)) {
            showToast("Invalid password format.");
            return false;
        }

        return true;
    }

    private boolean isValidUsername(String username) {
        return username.matches("^[a-zA-Z0-9]{4,20}$");
    }

    private boolean isValidPassword(String password) {
        return password.matches("^[A-Za-z0-9@#!$%^&*()_+={}\\[\\]:;\"'<>,.?/`~|-]{6,20}$");
    }

    // Toast helper method to prevent toast queue buildup
    private void showToast(String message) {
        Log.d(TAG, "Showing toast: " + message);
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
    }
}

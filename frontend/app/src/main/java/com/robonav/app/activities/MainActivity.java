package com.robonav.app.activities;

import com.robonav.app.utilities.ConfigManager;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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

    private EditText usernameEditText;
    private EditText passwordEditText;
    private Toast currentToast; // Store the latest toast reference

    private static final String LOGIN_URL = ConfigManager.getBaseUrl() + "/api/open/users/login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        }

        // OnClickListener for "Forgot Password"
        forgotPasswordText.setOnClickListener(v -> {
            Intent forgotPasswordIntent = new Intent(MainActivity.this, ForgotPasswordActivity.class);
            startActivity(forgotPasswordIntent);
        });

        // OnClickListener for "Sign Up"
        signUpText.setOnClickListener(v -> {
            Intent signUpIntent = new Intent(MainActivity.this, SignUpActivity.class);
            startActivity(signUpIntent);
        });

        // Set onClickListener for the login button
        loginButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (!areInputsValid(username, password)) return;

            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Logging in...");
            progressDialog.show();

            // Create JSON request body
            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("username", username);
                jsonBody.put("password", password);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // Send JSON request
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, LOGIN_URL, jsonBody,
                    response -> {
                        progressDialog.dismiss();
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
                            }
                        } catch (JSONException e) {
                            showToast("Error parsing response");
                        }
                    }, error -> {
                progressDialog.dismiss();

                // Default error message
                String errorMessage = "An error occurred. Please try again.";

                // Check if the error has a network response
                if (error.networkResponse != null) {
                    try {
                        // Get the error response body
                        String responseBody = new String(error.networkResponse.data, "UTF-8");

                        // Parse the error response body into a JSONObject
                        JSONObject errorResponse = new JSONObject(responseBody);

                        // Check if there's a "message" field in the error response
                        if (errorResponse.has("message")) {
                            errorMessage = errorResponse.getString("message");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // Show the extracted or default error message
                showToast(errorMessage);
            }) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json"); // Ensure JSON request
                    return headers;
                }
            };

            RequestQueue queue = Volley.newRequestQueue(this);
            queue.add(request);
        });

    }

    // Validate username and password
    private boolean areInputsValid(String username, String password) {

        // Check for empty fields
        if (username.isEmpty() || password.isEmpty()) {
            showToast("Please fill in both fields");
            return false;
        }

        // Validate username (4–20 alphanumeric characters)
        if (!isValidUsername(username)) {
            showToast("Username must be between 4-20 alphanumeric characters.");
            return false;
        }

        // Validate password length
        if (!isValidPassword(password)) {
            // Check if the password is too short or too long, contains spaces, or invalid characters
            if (password.length() < 6 || password.length() > 20) {
                showToast("Password must be between 6 and 20 characters.");
            } else if (password.contains(" ")) {
                showToast("Password cannot contain spaces.");
            } else {
                showToast("Password contains invalid characters. Only letters, numbers, and special characters (@, #, !, $, %, ^, &, *, etc.) are allowed.");
            }
            return false;
        }

        return true; // All validations passed
    }

    private boolean isValidUsername(String username) {
        return username.matches("^[a-zA-Z0-9]{4,20}$");
    }

    private boolean isValidPassword(String password) {
        // Check if password length is between 6 and 20, contains no spaces, or invalid characters
        return password.matches("^[A-Za-z0-9@#!$%^&*()_+={}\\[\\]:;\"'<>,.?/`~|-]{6,20}$");
    }

    // Toast helper method to prevent toast queue buildup
    private void showToast(String message) {
        if (currentToast != null) {
            currentToast.cancel();  // Cancel the previous toast if it exists
        }
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
    }
}
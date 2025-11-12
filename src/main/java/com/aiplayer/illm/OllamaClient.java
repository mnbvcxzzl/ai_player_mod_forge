package com.aiplayer.illm;

import com.aiplayer.AIPlayerMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class OllamaClient {
    private static final Gson GSON = new Gson();
    private static final String DEFAULT_MODEL = "llama3";
    private static final int TIMEOUT_MS = 15000;

    public static CompletableFuture<String> generateResponse(String prompt, String model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Используем 127.0.0.1 напрямую — обход проблемы с localhost/IPv6
                URL url = new URL("http://127.0.0.1:11434/api/generate");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);

                JsonObject request = new JsonObject();
                request.addProperty("model", model != null ? model : DEFAULT_MODEL);
                request.addProperty("prompt", prompt);
                request.addProperty("stream", false);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(GSON.toJson(request).getBytes());
                    os.flush();
                }

                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        JsonObject response = GSON.fromJson(reader, JsonObject.class);
                        return response.has("response") ? response.get("response").getAsString().trim() : null;
                    }
                } else {
                    BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream()));
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        error.append(line);
                    }
                    AIPlayerMod.LOGGER.error("Ollama error {}: {}", conn.getResponseCode(), error.toString());
                }
            } catch (Exception e) {
                AIPlayerMod.LOGGER.error("Ollama request failed", e);
            }
            return null;
        });
    }
}
package com.aiplayer.illm;

import com.aiplayer.AIPlayerMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class OllamaClient {
    private static final Gson GSON = new Gson();
    private static final String MODEL = "qwen2.5:7b-instruct";

    public static CompletableFuture<String> generateResponse(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("http://127.0.0.1:11434/api/generate");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);

                JsonObject request = new JsonObject();
                request.addProperty("model", MODEL);
                request.addProperty("prompt", prompt);
                request.addProperty("stream", false);

                JsonObject options = new JsonObject();
                options.addProperty("temperature", 0.6);
                options.addProperty("num_predict", 70);
                request.add("options", options);

                JsonArray stop = new JsonArray();
                stop.add("\n\n");
                stop.add("\n");
                request.add("stop", stop);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(GSON.toJson(request).getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject json = GSON.fromJson(response, JsonObject.class);
                    return json.get("response").getAsString().trim();
                } else {
                    AIPlayerMod.LOGGER.error("Ollama error: {}", conn.getResponseCode());
                }
            } catch (Exception e) {
                AIPlayerMod.LOGGER.error("Ollama connection failed", e);
            }
            return null;
        });
    }
}
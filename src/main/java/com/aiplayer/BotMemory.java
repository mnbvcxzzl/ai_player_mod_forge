// src/main/java/com/aiplayer/BotMemory.java
package com.aiplayer;

import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BotMemory {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AIPlayerEntity bot;
    private BotLife life;
    private final Path savePath;

    // В BotMemory.java
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public BotMemory(AIPlayerEntity bot) throws IOException {
        this.bot = bot;
        this.savePath = bot.level().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("aiplayer").resolve(bot.getUUID() + ".json");
        Files.createDirectories(savePath.getParent());
        load();
    }

    private void load() throws IOException {
        if (Files.exists(savePath)) {
            try (Reader reader = Files.newBufferedReader(savePath)) {
                this.life = AIPlayerMod.GSON.fromJson(reader, BotLife.class);
                if (this.life == null) this.life = new BotLife();
            } catch (Exception e) {
                AIPlayerMod.LOGGER.error("Failed to load bot memory, creating new", e);
                this.life = new BotLife();
            }
        } else {
            this.life = new BotLife();
        }
        if (this.life.name == null || this.life.name.equals("AI_Bot")) {
            this.life.name = bot.getCustomName().getString();
        }
        if (this.life.owner == null) {
            this.life.owner = bot.getOwnerUUID();
        }
        addAction("родился в мире");
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(savePath)) {
            AIPlayerMod.GSON.toJson(this.life, writer);
        } catch (IOException e) {
            AIPlayerMod.LOGGER.error("Failed to save bot memory", e);
        }
    }

    public BotLife getLife() { return life; }
    public BotMemory getMemory() { return this; }

    public void addAction(String action) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        life.actions.add(timestamp + ": " + action);
        save();
    }

    public void addDialogue(String playerName, String playerMsg, String botMsg) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        life.dialogue.add(new DialogueEntry(playerName, playerMsg, botMsg, timestamp));
        save();
    }

    public String getActionSummary() {
        if (life.actions.isEmpty()) return "";
        String last = life.actions.get(life.actions.size() - 1);
        int colon = last.indexOf(": ");
        return colon > 0 ? last.substring(colon + 2) : last;
    }

    public String getDialogueHistory(int limit) {
        int size = life.dialogue.size();
        int start = Math.max(0, size - limit);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < size; i++) {
            DialogueEntry e = life.dialogue.get(i);
            sb.append(e.playerName).append(": ").append(e.playerMsg).append("\n")
              .append(life.name).append(": ").append(e.botMsg).append("\n");
        }
        return sb.toString();
    }

    public static class BotLife {
        @SerializedName("name")
        public String name = "AI_Bot";

        @SerializedName("owner")
        public UUID owner;

        // ← ВОТ ЭТО ПОЛЕ ДОЛЖНО БЫТЬ!
        @SerializedName("actions")
        public List<String> actions = new ArrayList<>();

        @SerializedName("dialogue")
        public List<DialogueEntry> dialogue = new ArrayList<>();

        // ← МЕТОД ДОЛЖЕН БЫТЬ ВНУТРИ BotLife
        public String getLastAction() {
            if (actions.isEmpty()) return "ничего не делал";
            String last = actions.get(actions.size() - 1);
            int colon = last.indexOf(": ");
            return colon > 0 ? last.substring(colon + 2) : last;
        }
    }

    public static class DialogueEntry {
        public String playerName;
        public String playerMsg;
        public String botMsg;
        public String timestamp;

        public DialogueEntry() {}

        public DialogueEntry(String playerName, String playerMsg, String botMsg, String timestamp) {
            this.playerName = playerName;
            this.playerMsg = playerMsg;
            this.botMsg = botMsg;
            this.timestamp = timestamp;
        }
    }
}
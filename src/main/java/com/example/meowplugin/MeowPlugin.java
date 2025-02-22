package com.example.meowplugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MeowPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig(); // 生成默认配置
        reloadConfig();      // 加载配置
        getCommand("meow").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 权限检查
        if (getConfig().getBoolean("restrict-to-op") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "about":
                sendAbout(sender);
                return true;
            case "help":
                sendHelp(sender);
                return true;
            case "reload":
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "配置已重载");
                return true;
            default:
                String query = String.join(" ", args);
                processQuery(sender, query, 0);
                return true;
        }
    }

    private void processQuery(CommandSender sender, String query, int retryCount) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "控制台不能使用此命令");
            return;
        }

        new Thread(() -> {
            try {
                FileConfiguration config = getConfig();
                String apiUrl = config.getString("api-url");
                String apiKey = config.getString("api-key");
                String model = config.getString("model");
                int maxTokens = config.getInt("max-tokens");
                double temperature = config.getDouble("temperature");
                String systemPrompt = config.getString("system-prompt");

                // 构建请求体
                String jsonInputString = String.format(
                        "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":%d,\"temperature\":%.1f}",
                        model,
                        systemPrompt.replace("\"", "\\\""),
                        query.replace("\"", "\\\""),
                        maxTokens,
                        temperature
                );

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);  // 5秒连接超时
                conn.setReadTimeout(20000);     // 20秒读取超时

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonInputString.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) response.append(line);
                        sendFormattedResponse(sender, query, parseApiResponse(response.toString()));
                    }
                } else {
                    sendErrorMessage(sender, "API错误: " + conn.getResponseCode());
                }
            } catch (SocketTimeoutException e) {
                if (retryCount < 3) {
                    sendFormattedResponse(sender, query, ChatColor.RED + "请求超时，正在重试...");
                    processQuery(sender, query, retryCount + 1);
                } else {
                    sendFormattedResponse(sender, query, ChatColor.RED + "请求失败，请稍后再试");
                }
            } catch (Exception e) {
                sendErrorMessage(sender, "错误: " + e.getMessage());
            }
        }).start();
    }

    private String parseApiResponse(String response) {
        try {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (Exception e) {
            return "解析响应失败";
        }
    }

    private void sendFormattedResponse(CommandSender sender, String query, String answer) {
        getServer().getScheduler().runTask(this, () -> {
            String msg = String.format(
                    ChatColor.YELLOW + "[Meow] " +
                    ChatColor.AQUA + "@%s" +
                    ChatColor.GRAY + "(%s)" +
                    ChatColor.WHITE + ": " +
                    "%s", // 颜色由调用方控制
                    sender.getName(),
                    query,
                    answer
            );

            // 是否广播
            if (getConfig().getBoolean("broadcast-reply")) {
                getServer().getOnlinePlayers().forEach(p -> p.sendMessage(msg));
            } else {
                sender.sendMessage(msg);
            }
        });
    }

    private void sendAbout(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "[Meow] " +
                ChatColor.AQUA + "Meow" + ChatColor.LIGHT_PURPLE + " v1.0");
    }

    private void sendHelp(CommandSender sender) {
        String[] help = {
                ChatColor.YELLOW + "[Meow] " + ChatColor.AQUA + "帮助:",
                ChatColor.AQUA + "/meow <问题>" + ChatColor.WHITE + " - 提问",
                ChatColor.AQUA + "/meow reload" + ChatColor.WHITE + " - 重载配置",
                ChatColor.AQUA + "/meow about" + ChatColor.WHITE + " - 插件信息"
        };
        sender.sendMessage(help);
    }

    private void sendErrorMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.YELLOW + "[Meow] " +
                ChatColor.RED + "[Error] " + ChatColor.RED + message);
    }
}
package com.example.meowplugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MeowPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig(); // 生成默认配置
        reloadConfig();      // 加载配置
        getCommand("meow").setExecutor(this);
    //    getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(this, this);
    }
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (message.regionMatches(true, 0, "星风", 0, 2)) {


            // 异步环境下提取参数
            String content = message.substring(2).trim();
            String[] args = content.split(" +");
            String command = "meow" + (content.isEmpty() ? "" : " " + String.join(" ", args));

            // 将命令执行移至主线程
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    // 执行命令
                    Bukkit.dispatchCommand(player, command);

                    // 构建日志信息
                    String logMessage = String.format(
                            "[星风触发] 玩家：%s | 时间：%s | 原消息：%s | 执行命令：%s",
                            player.getName(),
                            new SimpleDateFormat("HH:mm:ss").format(new Date()),
                            message,
                            command
                    );

                    // 主线程日志记录
                    getLogger().info(logMessage);

                } catch (Exception e) {
                    getLogger().severe("执行星风指令时发生错误: " + e.getMessage());
                }
            });
        }
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
                        sendFormattedResponse(sender,parseApiResponse(response.toString()));
                    }
                } else {
                    sendErrorMessage(sender, "API错误: " + conn.getResponseCode());
                }
            } catch (SocketTimeoutException e) {
                if (retryCount < 3) {
                    sendFormattedResponse(sender, ChatColor.RED + "请求超时，正在重试...");
                    processQuery(sender, query, retryCount + 1);
                } else {
                    sendFormattedResponse(sender, ChatColor.RED + "请求失败，请稍后再试");
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

    private void sendFormattedResponse(CommandSender sender, String answer) {
        getServer().getScheduler().runTask(this, () -> {
            String msg = String.format(
                    ChatColor.RED + "🤖Deepseek V1" +
                            ChatColor.GRAY+" | "+ChatColor.WHITE+"星风💬："+
                    ChatColor.AQUA + "@%s" +
                    ChatColor.WHITE+ "%s", // 颜色由调用方控制
                    sender.getName(),
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

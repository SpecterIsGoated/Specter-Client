package com.jfant.specter.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jfant.specter.SpecterClientAddon;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class Webhook {
   public static final int RED = 15158332;
   public static final int ORANGE = 15105570;
   public static final int YELLOW = 15844367;
   public static final int GREEN = 3066993;
   public static final int BLUE = 5793266;
   private static final int QUEUE_LIMIT = 32;
   private static final long GAP_MS = 1200L;
   private static final long REPEAT_MS = 5000L;
   private static final int MAX_TRIES = 3;
   private static final long MAX_WAIT_MS = 30000L;
   private static final Duration TIMEOUT = Duration.ofSeconds(8L);
   private static final String USERNAME = "Specter Client";
   private static final Object LOCK = new Object();
   private static final Map<String, Long> RECENT = new HashMap<>();
   private static ThreadPoolExecutor worker;
   private static HttpClient client;
   private static long lastPost;
   private static long lastFullLog;

   private Webhook() {
   }

   public static boolean valid(String url) {
      if (url == null) {
         return false;
      } else {
         String text = url.trim();
         if (text.isEmpty()) {
            return false;
         } else {
            try {
               URI uri = new URI(text);
               String host = uri.getHost();
               if (host != null && "https".equalsIgnoreCase(uri.getScheme())) {
                  host = host.toLowerCase(Locale.ROOT);
                  return host.equals("discord.com") || host.endsWith(".discord.com") || host.equals("discordapp.com") || host.endsWith(".discordapp.com");
               } else {
                  return false;
               }
            } catch (URISyntaxException var4) {
               return false;
            }
         }
      }
   }

   public static void send(String url, String ping, String source, Webhook.Embed embed) {
      if (valid(url) && embed != null) {
         String address = url.trim();
         String body = embed.toJson(source, ping == null ? "" : ping.trim());
         String key = Integer.toHexString(address.hashCode()) + embed.signature().hashCode();
         long now = System.currentTimeMillis();
         synchronized (LOCK) {
            RECENT.values().removeIf(at -> now - at > 5000L);
            if (RECENT.putIfAbsent(key, now) != null) {
               return;
            }
         }

         try {
            executor().execute(() -> post(address, body, source));
         } catch (RejectedExecutionException var11) {
            if (now - lastFullLog > 60000L) {
               lastFullLog = now;
               SpecterClientAddon.LOG.warn("[Webhook] {}: too many alerts waiting, dropping the newest", source);
            }
         }
      }
   }

   private static synchronized ThreadPoolExecutor executor() {
      if (worker == null) {
         worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread thread = new Thread(runnable, "specter-webhook");
            thread.setDaemon(true);
            return thread;
         });
         client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
      }

      return worker;
   }

   private static void post(String url, String body, String source) {
      try {
         for (int attempt = 1; attempt <= 3; attempt++) {
            long wait = lastPost + 1200L - System.currentTimeMillis();
            if (wait > 0L) {
               Thread.sleep(wait);
            }

            HttpResponse<String> response;
            try {
               HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                  .timeout(TIMEOUT)
                  .header("Content-Type", "application/json; charset=utf-8")
                  .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                  .build();
               lastPost = System.currentTimeMillis();
               response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException var10) {
               SpecterClientAddon.LOG.warn("[Webhook] {}: could not send ({})", source, var10.getClass().getSimpleName());
               return;
            }

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
               return;
            }

            if (status != 429 || attempt == 3) {
               SpecterClientAddon.LOG.warn("[Webhook] {}: Discord answered HTTP {}", source, status);
               return;
            }

            long retry = retryAfterMs(response.body());
            if (retry > 30000L) {
               SpecterClientAddon.LOG.warn("[Webhook] {}: rate limited for {} s, dropping the alert", source, retry / 1000L);
               return;
            }

            Thread.sleep(retry + 100L);
            lastPost = System.currentTimeMillis();
         }
      } catch (InterruptedException var11) {
         Thread.currentThread().interrupt();
      } catch (RuntimeException var12) {
         SpecterClientAddon.LOG.warn("[Webhook] {}: could not send ({})", source, var12.getClass().getSimpleName());
      }
   }

   private static long retryAfterMs(String body) {
      try {
         JsonObject json = JsonParser.parseString(body).getAsJsonObject();
         double seconds = json.has("retry_after") ? json.get("retry_after").getAsDouble() : 1.0;
         return Double.isFinite(seconds) ? (long)(Math.max(0.25, seconds) * 1000.0) : 1000L;
      } catch (RuntimeException var4) {
         return 1000L;
      }
   }

   public static String coordinates(int x, int y, int z) {
      return "`X: " + x + "  Y: " + y + "  Z: " + z + "`";
   }

   public static final class Embed {
      private static final int TITLE_MAX = 256;
      private static final int DESCRIPTION_MAX = 4096;
      private static final int NAME_MAX = 256;
      private static final int VALUE_MAX = 1024;
      private static final int FIELDS_MAX = 25;
      private final String title;
      private final String description;
      private final int color;
      private final JsonArray fields = new JsonArray();

      public Embed(String title, String description, int color) {
         this.title = clip(title, 256);
         this.description = clip(description, 4096);
         this.color = color & 16777215;
      }

      public Webhook.Embed field(String name, String value, boolean inline) {
         if (this.fields.size() < 25 && value != null && !value.isBlank()) {
            JsonObject field = new JsonObject();
            field.addProperty("name", clip(name, 256));
            field.addProperty("value", clip(value, 1024));
            field.addProperty("inline", inline);
            this.fields.add(field);
            return this;
         } else {
            return this;
         }
      }

      public Webhook.Embed context() {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null) {
            ServerData server = mc.getCurrentServer();
            this.field("Account", "`" + mc.player.getGameProfile().name() + "`", true);
            this.field("Server", "`" + (server != null ? server.ip : "singleplayer") + "`", true);
            this.field("Dimension", "`" + mc.level.dimension().identifier() + "`", true);
            this.field("Your location", Webhook.coordinates(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()), false);
            return this;
         } else {
            return this;
         }
      }

      String signature() {
         return this.title + "|" + this.description + "|" + this.fields;
      }

      String toJson(String source, String ping) {
         JsonObject embed = new JsonObject();
         if (!this.title.isBlank()) {
            embed.addProperty("title", this.title);
         }

         if (!this.description.isBlank()) {
            embed.addProperty("description", this.description);
         }

         embed.addProperty("color", this.color);
         if (this.fields.size() > 0) {
            embed.add("fields", this.fields);
         }

         embed.addProperty("timestamp", Instant.now().toString());
         JsonObject footer = new JsonObject();
         footer.addProperty("text", "Specter Client • " + source);
         embed.add("footer", footer);
         JsonArray embeds = new JsonArray();
         embeds.add(embed);
         JsonObject payload = new JsonObject();
         payload.addProperty("username", "Specter Client");
         payload.add("embeds", embeds);
         JsonObject mentions = new JsonObject();
         JsonArray parse = new JsonArray();
         JsonArray users = new JsonArray();
         if (ping.matches("\\d{15,25}")) {
            payload.addProperty("content", "<@" + ping + ">");
            users.add(ping);
         }

         mentions.add("parse", parse);
         if (users.size() > 0) {
            mentions.add("users", users);
         }

         payload.add("allowed_mentions", mentions);
         return payload.toString();
      }

      private static String clip(String text, int max) {
         if (text == null) {
            return "";
         } else {
            return text.length() <= max ? text : text.substring(0, max - 3) + "...";
         }
      }
   }
}

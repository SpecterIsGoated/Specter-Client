package com.jfant.specter.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NeteaseLyrics {
   private static final String SEARCH = "https://music.163.com/api/cloudsearch/pc?type=1&limit=10&offset=0&s=";
   private static final String LYRIC = "https://music.163.com/api/song/lyric/v1?cp=false&lv=0&kv=0&tv=0&rv=0&yv=0&ytv=0&yrv=0&id=";
   private static final String AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36";
   private static final Duration TIMEOUT = Duration.ofSeconds(10L);
   private static final double LENGTH_TOLERANCE_SECONDS = 3.0;
   private static final long GAP_MS = 1200L;
   private static final Pattern LINE = Pattern.compile("^\\[(\\d+),(\\d+)]");
   private static final Pattern WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)(.*?)(?=\\(\\d+,\\d+,\\d+\\)|$)");
   private static final Pattern BRACKETS = Pattern.compile("\\s*[(\\[][^)\\]]*[)\\]]");
   private static final Pattern MARKS = Pattern.compile("\\p{M}+");
   private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
   private static final Pattern CREDITS = Pattern.compile("作词|作曲|编曲|制作人|词\\s*[:：]|曲\\s*[:：]");

   private NeteaseLyrics() {
   }

   static NeteaseLyrics.Found find(HttpClient http, LyricsFinder.Query query) throws IOException, InterruptedException {
      JsonObject song = match(http, query);
      if (song == null) {
         return null;
      } else {
         JsonObject lyric = json(
            get(http, "https://music.163.com/api/song/lyric/v1?cp=false&lv=0&kv=0&tv=0&rv=0&yv=0&ytv=0&yrv=0&id=" + song.get("id").getAsLong())
         );
         if (lyric != null && lyric.has("yrc") && lyric.get("yrc").isJsonObject()) {
            JsonElement text = lyric.getAsJsonObject("yrc").get("lyric");
            if (text != null && text.isJsonPrimitive()) {
               String lrc = toEnhancedLrc(text.getAsString());
               return lrc.isEmpty() ? null : new NeteaseLyrics.Found(song.get("name").getAsString(), artists(song), song.get("dt").getAsLong() / 1000.0, lrc);
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private static JsonObject match(HttpClient http, LyricsFinder.Query query) throws IOException, InterruptedException {
      String terms = URLEncoder.encode(plainTitle(query.title()) + " " + query.artist(), StandardCharsets.UTF_8);
      JsonObject body = json(get(http, "https://music.163.com/api/cloudsearch/pc?type=1&limit=10&offset=0&s=" + terms));
      if (body != null && body.has("result") && body.get("result").isJsonObject()) {
         JsonObject result = body.getAsJsonObject("result");
         if (result.has("songs") && result.get("songs").isJsonArray()) {
            String title = squash(plainTitle(query.title()));
            String artist = squash(query.artist());
            JsonObject best = null;
            int bestScore = 0;

            for (JsonElement element : result.getAsJsonArray("songs")) {
               if (element.isJsonObject()) {
                  JsonObject song = element.getAsJsonObject();
                  if (song.has("name") && song.has("id") && song.has("dt") && song.has("ar")) {
                     String name = squash(plainTitle(song.get("name").getAsString()));
                     boolean sameTitle = name.equals(title);
                     if ((sameTitle || name.startsWith(title) || title.startsWith(name)) && !name.isEmpty() && sharesArtist(song.getAsJsonArray("ar"), artist)) {
                        double seconds = song.get("dt").getAsLong() / 1000.0;
                        if (query.seconds() <= 0 || !(Math.abs(seconds - query.seconds()) > 3.0)) {
                           int score = sameTitle ? 2 : 1;
                           if (score > bestScore) {
                              best = song;
                              bestScore = score;
                           }
                        }
                     }
                  }
               }
            }

            return best;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean sharesArtist(JsonArray artists, String wanted) {
      for (JsonElement element : artists) {
         if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
            String name = squash(element.getAsJsonObject().get("name").getAsString());
            if (!name.isEmpty() && (wanted.contains(name) || name.contains(wanted))) {
               return true;
            }
         }
      }

      return false;
   }

   private static String artists(JsonObject song) {
      List<String> names = new ArrayList<>();

      for (JsonElement element : song.getAsJsonArray("ar")) {
         if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
            names.add(element.getAsJsonObject().get("name").getAsString());
         }
      }

      return String.join(", ", names);
   }

   static String toEnhancedLrc(String yrc) {
      record Sung(long start, long end, String text) {
      }

      List<Sung> lines = new ArrayList<>();

      for (String raw : yrc.split("\r\n|\r|\n")) {
         Matcher line = LINE.matcher(raw);
         if (line.find()) {
            long start = Long.parseLong(line.group(1));
            long length = Long.parseLong(line.group(2));
            StringBuilder out = new StringBuilder();
            StringBuilder plain = new StringBuilder();
            Matcher word = WORD.matcher(raw.substring(line.end()));

            while (word.find()) {
               String text = word.group(3);
               if (!text.isEmpty()) {
                  out.append('<').append(stamp(Long.parseLong(word.group(1)))).append('>').append(text);
                  plain.append(text);
               }
            }

            String words = plain.toString().strip();
            if (!words.isEmpty() && !CREDITS.matcher(words).find()) {
               lines.add(new Sung(start, start + length, out.toString().strip()));
            }
         }
      }

      StringBuilder lrc = new StringBuilder();

      for (int i = 0; i < lines.size(); i++) {
         Sung sung = lines.get(i);
         lrc.append('[').append(stamp(sung.start())).append(']').append(sung.text()).append('\n');
         long next = i + 1 < lines.size() ? lines.get(i + 1).start() : 9223372036854775807L;
         if (next - sung.end() > 1200L) {
            lrc.append('[').append(stamp(sung.end() + 300L)).append("]\n");
         }
      }

      return lrc.toString();
   }

   private static String stamp(long ms) {
      long minutes = ms / 60000L;
      long seconds = ms / 1000L % 60L;
      long hundredths = ms / 10L % 100L;
      return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths);
   }

   private static String plainTitle(String title) {
      String plain = BRACKETS.matcher(title).replaceAll("");
      int dash = plain.indexOf(" - ");
      return (dash > 0 ? plain.substring(0, dash) : plain).strip();
   }

   private static String squash(String text) {
      String decomposed = MARKS.matcher(Normalizer.normalize(text, Form.NFKD)).replaceAll("");
      return NOT_WORD.matcher(decomposed.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
   }

   private static String get(HttpClient http, String url) throws IOException, InterruptedException {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
         .timeout(TIMEOUT)
         .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36")
         .header("Referer", "https://music.163.com/")
         .GET()
         .build();
      HttpResponse<String> response = http.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
         throw new IOException("NetEase answered HTTP " + response.statusCode());
      } else {
         return response.body();
      }
   }

   private static JsonObject json(String body) {
      try {
         JsonElement parsed = JsonParser.parseString(body);
         return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
      } catch (JsonParseException var2) {
         return null;
      }
   }

   record Found(String title, String artist, double seconds, String lrc) {
   }
}

package com.jfant.specter.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

public final class LyricsFinder {
   private static final URI SITE = URI.create("https://lrclib.net/");
   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8L);
   private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12L);
   private static final long REQUEST_GAP_MS = 300L;
   private static final long FOUND_TTL_MS = Duration.ofDays(30L).toMillis();
   private static final long MISSING_TTL_MS = Duration.ofHours(12L).toMillis();
   private static final long RETRY_MS = 60000L;
   private static final int MEMORY_ENTRIES = 64;
   private static final int DISK_FILES = 300;
   private static final double LENGTH_TOLERANCE_SECONDS = 3.0;
   private static final Pattern MARKS = Pattern.compile("\\p{M}+");
   private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
   private static final Pattern EXTRAS = Pattern.compile(
      "\\s*[(\\[][^)\\]]*\\b(feat|ft|featuring|with|remaster(ed)?|explicit|clean|mono|stereo|bonus|deluxe|edition|single version|album version)\\b[^)\\]]*[)\\]]|\\s+[-–—]\\s+[^-–—]*\\b(remaster(ed)?|explicit|clean|mono|stereo|bonus track|deluxe|edition|single version|album version)\\b.*$|\\s+(feat\\.?|ft\\.|featuring)\\s+.*$",
      2
   );
   private static final Pattern ARTIST_SPLIT = Pattern.compile(
      "\\s*(?:,|;|/|&|\\+|\\bx\\b|\\band\\b|\\bwith\\b|\\bfeat\\b\\.?|\\bft\\b\\.?|\\bfeaturing\\b)\\s*", 2
   );
   private static final Pattern FIRST_ARTIST = Pattern.compile("\\s*[,;]\\s*|\\s+(?:&|x|feat\\.?|ft\\.?|featuring|with)\\s+", 2);
   private final File folder = new File(new File(SpecterCore.FOLDER, "specter"), "lyrics");
   private final Map<LyricsFinder.Query, LyricsFinder.Memo> memory = new LinkedHashMap<LyricsFinder.Query, LyricsFinder.Memo>(128, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<LyricsFinder.Query, LyricsFinder.Memo> eldest) {
         return this.size() > 64;
      }
   };
   private final Set<LyricsFinder.Query> pending = new HashSet<>();
   private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "specter-lyrics");
      thread.setDaemon(true);
      return thread;
   });
   private volatile LyricsFinder.Query wanted;
   private volatile boolean closed;
   private HttpClient http;
   private long nextRequestAt;
   private int writes;
   private final BooleanSupplier wordTimed;

   public LyricsFinder() {
      this(() -> false);
   }

   public LyricsFinder(BooleanSupplier wordTimed) {
      this.wordTimed = wordTimed;
   }

   public void forget() {
      synchronized (this.memory) {
         this.memory.clear();
      }
   }

   public Lyrics lookup(LyricsFinder.Query query) {
      if (query != null && query.searchable()) {
         this.wanted = query;
         long now = System.currentTimeMillis();
         synchronized (this.memory) {
            LyricsFinder.Memo memo = this.memory.get(query);
            if (memo != null && now < memo.freshUntil()) {
               return memo.lyrics();
            } else {
               if (!this.closed && this.pending.add(query)) {
                  try {
                     this.worker.execute(() -> this.resolve(query));
                  } catch (RuntimeException var8) {
                     this.pending.remove(query);
                  }
               }

               return memo != null ? memo.lyrics() : Lyrics.LOADING;
            }
         }
      } else {
         return Lyrics.NOT_FOUND;
      }
   }

   public void close() {
      this.closed = true;
      this.worker.shutdownNow();
      synchronized (this.memory) {
         this.pending.clear();
      }
   }

   private void resolve(LyricsFinder.Query query) {
      try {
         try {
            if (this.closed || !query.equals(this.wanted)) {
               return;
            }

            boolean words = this.wordTimed.getAsBoolean();
            String key = (words ? "words-" : "") + query.key();
            JsonObject stored = this.readDisk(key);
            if (stored != null) {
               long fetchedAt = stored.has("fetchedAt") ? stored.get("fetchedAt").getAsLong() : 0L;
               LyricsFinder.Candidate found = stored.has("track") ? LyricsFinder.Candidate.of(stored.get("track")) : null;
               long freshUntil = fetchedAt + (found != null ? FOUND_TTL_MS : MISSING_TTL_MS);
               if (System.currentTimeMillis() < freshUntil) {
                  this.remember(query, lyricsOf(found), freshUntil);
                  return;
               }
            }

            LyricsFinder.Fetched fetched = words ? this.fetchWordTimed(query) : null;
            if (fetched == null) {
               fetched = this.fetch(query);
            }

            long now = System.currentTimeMillis();
            Lyrics lyrics = lyricsOf(fetched.best());
            if (!fetched.complete()) {
               this.remember(query, lyrics, now + 60000L);
               return;
            }

            this.remember(query, lyrics, now + (fetched.best() != null ? FOUND_TTL_MS : MISSING_TTL_MS));
            this.writeDisk(key, fetched.best(), now);
         } catch (InterruptedException var30) {
            Thread.currentThread().interrupt();
         } catch (RuntimeException | IOException var31) {
            SpecterClientAddon.LOG.debug("Lyrics lookup for \"{}\" failed: {}", query.title(), var31.toString());
            this.remember(query, Lyrics.UNAVAILABLE, System.currentTimeMillis() + 60000L);
         }
      } finally {
         synchronized (this.memory) {
            this.pending.remove(query);
         }
      }
   }

   private void remember(LyricsFinder.Query query, Lyrics lyrics, long freshUntil) {
      synchronized (this.memory) {
         this.memory.put(query, new LyricsFinder.Memo(lyrics, freshUntil));
      }
   }

   private static Lyrics lyricsOf(LyricsFinder.Candidate candidate) {
      if (candidate == null) {
         return Lyrics.NOT_FOUND;
      } else {
         if (candidate.hasSynced()) {
            Lrc lrc = Lrc.parse(candidate.synced());
            if (!lrc.isEmpty()) {
               return new Lyrics(Lyrics.Status.SYNCED, lrc);
            }
         }

         if (candidate.hasPlain()) {
            return Lyrics.UNSYNCED;
         } else {
            return candidate.instrumental() ? Lyrics.INSTRUMENTAL : Lyrics.NOT_FOUND;
         }
      }
   }

   private LyricsFinder.Fetched fetchWordTimed(LyricsFinder.Query query) throws InterruptedException {
      if (this.http == null) {
         this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(Redirect.NORMAL).build();
      }

      try {
         NeteaseLyrics.Found found = NeteaseLyrics.find(this.http, query);
         if (found == null) {
            return null;
         } else {
            SpecterClientAddon.LOG.info("Lyrics: word-timed lyrics for \"{}\" from NetEase", query.title());
            return new LyricsFinder.Fetched(new LyricsFinder.Candidate(found.title(), found.artist(), found.seconds(), false, "", found.lrc()), true);
         }
      } catch (RuntimeException | IOException var3) {
         SpecterClientAddon.LOG.debug("NetEase lookup for \"{}\" failed: {}", query.title(), var3.toString());
         return null;
      }
   }

   private LyricsFinder.Fetched fetch(LyricsFinder.Query query) throws IOException, InterruptedException {
      LyricsFinder.Candidate exact = this.get(query);
      if (exact == null || !exact.hasSynced() && (!exact.instrumental() || exact.hasPlain())) {
         LyricsFinder.Candidate fallback = exact != null && exact.usable() ? exact : null;

         try {
            for (String[] terms : searchTerms(query)) {
               LyricsFinder.Candidate best = best(this.search(terms[0], terms[1]), query);
               if (best != null) {
                  if (best.hasSynced()) {
                     return new LyricsFinder.Fetched(best, true);
                  }

                  if (fallback == null) {
                     fallback = best;
                  }
               }
            }
         } catch (IOException var7) {
            if (fallback == null) {
               throw var7;
            }

            return new LyricsFinder.Fetched(fallback, false);
         }

         return new LyricsFinder.Fetched(fallback, true);
      } else {
         return new LyricsFinder.Fetched(exact, true);
      }
   }

   private LyricsFinder.Candidate get(LyricsFinder.Query query) throws IOException, InterruptedException {
      HttpResponse<String> response = this.send(
         "api/get?"
            + params("track_name", query.title(), "artist_name", query.artist(), "duration", query.seconds() > 0 ? Integer.toString(query.seconds()) : "")
      );
      if (response.statusCode() == 404) {
         return null;
      } else if (response.statusCode() != 200) {
         throw new IOException("lrclib answered HTTP " + response.statusCode());
      } else {
         return LyricsFinder.Candidate.of(json(response.body()));
      }
   }

   private List<LyricsFinder.Candidate> search(String title, String artist) throws IOException, InterruptedException {
      HttpResponse<String> response = this.send("api/search?" + params("track_name", title, "artist_name", artist));
      if (response.statusCode() == 404) {
         return List.of();
      } else if (response.statusCode() != 200) {
         throw new IOException("lrclib answered HTTP " + response.statusCode());
      } else {
         JsonElement parsed = json(response.body());
         List<LyricsFinder.Candidate> out = new ArrayList<>();
         if (parsed != null && parsed.isJsonArray()) {
            for (JsonElement item : parsed.getAsJsonArray()) {
               LyricsFinder.Candidate candidate = LyricsFinder.Candidate.of(item);
               if (candidate != null) {
                  out.add(candidate);
               }
            }
         }

         return out;
      }
   }

   private HttpResponse<String> send(String pathAndQuery) throws IOException, InterruptedException {
      if (this.http == null) {
         this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(Redirect.NORMAL).build();
      }

      long wait = this.nextRequestAt - System.currentTimeMillis();
      if (wait > 0L) {
         Thread.sleep(wait);
      }

      HttpRequest request = HttpRequest.newBuilder(SITE.resolve(pathAndQuery))
         .timeout(REQUEST_TIMEOUT)
         .header("User-Agent", "Specter Client (private addon)")
         .header("Accept", "application/json")
         .GET()
         .build();

      HttpResponse var5;
      try {
         var5 = this.http.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
      } finally {
         this.nextRequestAt = System.currentTimeMillis() + 300L;
      }

      return var5;
   }

   private static String params(String... namesAndValues) {
      StringBuilder out = new StringBuilder();

      for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
         if (!namesAndValues[i + 1].isEmpty()) {
            if (out.length() > 0) {
               out.append('&');
            }

            out.append(namesAndValues[i]).append('=').append(URLEncoder.encode(namesAndValues[i + 1], StandardCharsets.UTF_8));
         }
      }

      return out.toString();
   }

   private static JsonElement json(String body) throws IOException {
      try {
         return JsonParser.parseString(body);
      } catch (JsonParseException var2) {
         throw new IOException("lrclib sent malformed JSON", var2);
      }
   }

   private static List<String[]> searchTerms(LyricsFinder.Query query) {
      List<String[]> terms = new ArrayList<>(2);
      terms.add(new String[]{query.title(), query.artist()});
      String title = baseTitle(query.title());
      String artist = FIRST_ARTIST.split(query.artist(), 2)[0].strip();
      if (!title.equals(query.title()) || !artist.equals(query.artist())) {
         terms.add(new String[]{title, artist});
      }

      return terms;
   }

   private static LyricsFinder.Candidate best(List<LyricsFinder.Candidate> candidates, LyricsFinder.Query query) {
      LyricsFinder.Candidate best = null;
      int bestScore = -1;
      double bestDrift = 1.7976931348623157E308;

      for (LyricsFinder.Candidate candidate : candidates) {
         int score = score(candidate, query);
         if (score >= 0) {
            double drift = query.seconds() > 0 && candidate.seconds() > 0.0 ? Math.abs(candidate.seconds() - query.seconds()) : 3.0;
            if (score > bestScore || score == bestScore && drift < bestDrift) {
               best = candidate;
               bestScore = score;
               bestDrift = drift;
            }
         }
      }

      return best;
   }

   private static int score(LyricsFinder.Candidate candidate, LyricsFinder.Query query) {
      if (!candidate.usable()) {
         return -1;
      } else {
         int length = 0;
         if (query.seconds() > 0 && candidate.seconds() > 0.0) {
            double drift = Math.abs(candidate.seconds() - query.seconds());
            if (drift > 3.0) {
               return -1;
            }

            length = (int)Math.round(20.0 * (1.0 - drift / 6.0));
         }

         int title = titleScore(query.title(), candidate.title());
         if (title <= 0) {
            return -1;
         } else {
            int artist = artistScore(query.artist(), candidate.artist());
            return artist <= 0 ? -1 : title + artist + length + (candidate.hasSynced() ? 25 : (candidate.hasPlain() ? 5 : 8));
         }
      }
   }

   private static int titleScore(String wanted, String offered) {
      String a = fold(wanted);
      String b = fold(offered);
      if (a.isEmpty() || b.isEmpty()) {
         return 0;
      } else if (a.equals(b)) {
         return 40;
      } else {
         String baseA = fold(baseTitle(wanted));
         String baseB = fold(baseTitle(offered));
         if (!baseA.isEmpty() && baseA.equals(baseB)) {
            return 30;
         } else {
            return !containsWords(baseA, baseB) && !containsWords(baseB, baseA) ? 0 : 12;
         }
      }
   }

   private static int artistScore(String wanted, String offered) {
      String a = fold(wanted);
      String b = fold(offered);
      if (a.isEmpty() || b.isEmpty()) {
         return 0;
      } else if (a.equals(b)) {
         return 30;
      } else {
         List<String> wantedNames = artists(wanted);
         List<String> offeredNames = artists(offered);

         for (String name : wantedNames) {
            if (offeredNames.contains(name)) {
               return 20;
            }
         }

         String first = wantedNames.isEmpty() ? a : wantedNames.getFirst();
         return !containsWords(b, first) && !containsWords(a, b) ? 0 : 10;
      }
   }

   private static List<String> artists(String credit) {
      List<String> names = new ArrayList<>();

      for (String part : ARTIST_SPLIT.split(credit)) {
         String name = fold(part);
         if (!name.isEmpty() && !names.contains(name)) {
            names.add(name);
         }
      }

      return names;
   }

   private static String baseTitle(String title) {
      String base = EXTRAS.matcher(title).replaceAll("").strip();
      return base.isEmpty() ? title.strip() : base;
   }

   private static String fold(String text) {
      if (text != null && !text.isEmpty()) {
         String lower = text.toLowerCase(Locale.ROOT).replace("&", " and ").replace("ß", "ss").replace("ø", "o").replace("ł", "l");
         String plain = MARKS.matcher(Normalizer.normalize(lower, Form.NFD)).replaceAll("");
         return NOT_WORD.matcher(plain.replaceAll("['’`´]", "")).replaceAll(" ").strip();
      } else {
         return "";
      }
   }

   private static boolean containsWords(String haystack, String needle) {
      return needle.length() >= 3 && haystack.length() >= needle.length() && (" " + haystack + " ").contains(" " + needle + " ");
   }

   private JsonObject readDisk(String key) {
      File file = new File(this.folder, key + ".json");
      if (!file.isFile()) {
         return null;
      } else {
         try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
         } catch (RuntimeException | IOException var4) {
            return null;
         }
      }
   }

   private void writeDisk(String key, LyricsFinder.Candidate found, long fetchedAt) {
      JsonObject object = new JsonObject();
      object.addProperty("fetchedAt", fetchedAt);
      if (found != null) {
         object.add("track", found.toJson());
      }

      try {
         Files.createDirectories(this.folder.toPath());
         File file = new File(this.folder, key + ".json");
         File temp = new File(this.folder, key + ".tmp");
         Files.writeString(temp.toPath(), object.toString(), StandardCharsets.UTF_8);
         Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
         if (++this.writes % 25 == 1) {
            this.prune();
         }
      } catch (IOException var8) {
         SpecterClientAddon.LOG.debug("Could not cache lyrics: {}", var8.toString());
      }
   }

   private void prune() {
      File[] files = this.folder.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null && files.length > 300) {
         Arrays.sort(files, Comparator.comparingLong(File::lastModified));

         for (int i = 0; i < files.length - 300; i++) {
            files[i].delete();
         }
      }
   }

   private record Candidate(String title, String artist, double seconds, boolean instrumental, String plain, String synced) {
      boolean hasSynced() {
         return !this.synced.isBlank();
      }

      boolean hasPlain() {
         return !this.plain.isBlank();
      }

      boolean usable() {
         return this.instrumental || this.hasSynced() || this.hasPlain();
      }

      static LyricsFinder.Candidate of(JsonElement element) {
         if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement instrumental = object.get("instrumental");
            return new LyricsFinder.Candidate(
               text(object, "trackName"),
               text(object, "artistName"),
               number(object, "duration"),
               instrumental != null && instrumental.isJsonPrimitive() && instrumental.getAsJsonPrimitive().isBoolean() && instrumental.getAsBoolean(),
               text(object, "plainLyrics"),
               text(object, "syncedLyrics")
            );
         } else {
            return null;
         }
      }

      JsonObject toJson() {
         JsonObject object = new JsonObject();
         object.addProperty("trackName", this.title);
         object.addProperty("artistName", this.artist);
         object.addProperty("duration", this.seconds);
         object.addProperty("instrumental", this.instrumental);
         object.addProperty("plainLyrics", this.plain);
         object.addProperty("syncedLyrics", this.synced);
         return object;
      }

      private static String text(JsonObject object, String name) {
         JsonElement value = object.get(name);
         return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
      }

      private static double number(JsonObject object, String name) {
         JsonElement value = object.get(name);
         return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? Math.max(0.0, value.getAsDouble()) : 0.0;
      }
   }

   private record Fetched(LyricsFinder.Candidate best, boolean complete) {
   }

   private record Memo(Lyrics lyrics, long freshUntil) {
   }

   public record Query(String title, String artist, int seconds) {
      public Query(String title, String artist, int seconds) {
         title = title == null ? "" : title.strip();
         artist = artist == null ? "" : artist.strip();
         seconds = seconds >= 0 && seconds <= 3600 ? seconds : 0;
         this.title = title;
         this.artist = artist;
         this.seconds = seconds;
      }

      boolean searchable() {
         return !this.title.isEmpty() && !this.artist.isEmpty();
      }

      String key() {
         String identity = squash(this.artist) + "\n" + squash(this.title) + "\n" + this.seconds;

         try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)), 0, 16);
         } catch (NoSuchAlgorithmException var3) {
            throw new IllegalStateException(var3);
         }
      }

      private static String squash(String text) {
         return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
      }
   }
}

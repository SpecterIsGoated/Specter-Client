package com.jfant.specter.phone.apps.gram;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.phone.core.PhoneConfig;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.jetbrains.annotations.Nullable;

/**
 * DonutGram's connection to the Specter server: signing in, the feed, posting, likes, profiles, the leaderboard.
 *
 * <p>Signing in never sends a password or the Minecraft session token. The game signs a one-time challenge with the
 * profile key Mojang gives every account (the key used for signed chat); the server checks Mojang's signature on
 * that key, which proves the account. DonutGram never shows the Minecraft name: everyone sees an anonymous handle
 * (made by the server, changeable once a day). The DonutGram session that comes back only works for DonutGram and is kept
 * (with its expiry) in {@code specter/phone/donutgram-session.json}.
 *
 * <p>All calls are asynchronous; results are delivered on the game thread.
 */
public final class GramService {
   public static final String SERVER = "https://specterplus-license.specterclient.workers.dev";
   private static final Path SESSION_FILE = PhoneConfig.DIR.resolve("donutgram-session.json");
   private static final GramService INSTANCE = new GramService();

   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
   @Nullable
   private String token;
   private long tokenExpires;
   private String tokenUuid;
   @Nullable
   private JsonObject me;
   @Nullable
   private CompletableFuture<JsonObject> signingIn;

   public static final class GramException extends RuntimeException {
      public final int status;
      public final String code;
      @Nullable
      public final JsonObject body;

      GramException(int status, String code, String message, @Nullable JsonObject body) {
         super(message);
         this.status = status;
         this.code = code;
         this.body = body;
      }
   }

   public static GramService get() {
      return INSTANCE;
   }

   private GramService() {
      JsonObject saved = PhoneConfig.readJson(SESSION_FILE);
      if (saved.has("token") && saved.has("exp") && saved.has("uuid")) {
         this.token = saved.get("token").getAsString();
         this.tokenExpires = saved.get("exp").getAsLong();
         this.tokenUuid = saved.get("uuid").getAsString();
         if (saved.has("me") && saved.get("me").isJsonObject()) this.me = saved.getAsJsonObject("me");
      }
   }

   /** Keeps the session and the account details (handle, Discord link) across restarts. */
   private void saveSession() {
      if (this.token == null) return;
      JsonObject saved = new JsonObject();
      saved.addProperty("token", this.token);
      saved.addProperty("exp", this.tokenExpires);
      saved.addProperty("uuid", this.tokenUuid);
      if (this.me != null) saved.add("me", this.me);
      PhoneConfig.writeAtomic(SESSION_FILE, saved.toString());
   }

   private void setMe(JsonObject me) {
      this.me = me;
      this.saveSession();
   }

   // ============================================================================================== session

   private static String myUuid() {
      UUID id = Minecraft.getInstance().getUser().getProfileId();
      return id == null ? "" : id.toString().replace("-", "");
   }

   /** Signed in, for the account the game is using now, and not about to expire. */
   public boolean signedIn() {
      return this.token != null && this.tokenExpires - 60 > System.currentTimeMillis() / 1000 && myUuid().equals(this.tokenUuid);
   }

   /** The signed-in account's details from the server (name, Specter+ status, likes, rank), or null. */
   @Nullable
   public JsonObject me() {
      return this.signedIn() ? this.me : null;
   }

   public void signOut() {
      this.token = null;
      this.me = null;
      PhoneConfig.writeAtomic(SESSION_FILE, "{}");
   }

   /** Signs in (or returns the sign-in already running). */
   public CompletableFuture<JsonObject> signIn() {
      if (this.signingIn != null && !this.signingIn.isDone()) return this.signingIn;
      Minecraft mc = Minecraft.getInstance();
      String uuid = myUuid();
      String name = mc.getUser().getName();
      CompletableFuture<JsonObject> f = this.call("POST", "/v1/challenge", "{}", false).thenCompose(ch -> {
         String challenge = ch.get("challenge").getAsString();
         return keyPair().thenApply(kp -> {
            JsonObject body = new JsonObject();
            body.addProperty("uuid", uuid);
            body.addProperty("username", name);
            body.addProperty("challenge", challenge);
            body.add("proof", proof(kp, "donutgram-login:" + challenge + ":" + uuid));
            return body;
         });
      }).thenCompose(body -> this.call("POST", "/v1/gram/login", body.toString(), false)).thenApply(res -> {
         this.token = res.get("token").getAsString();
         this.tokenExpires = res.get("expiresAt").getAsLong();
         this.tokenUuid = uuid;
         this.setMe(res.getAsJsonObject("me"));
         return this.me;
      });
      this.signingIn = onMain(f);
      return this.signingIn;
   }

   /** The game's profile key pair. No Chat Reports hides the manager; then the real one is read from the game. */
   private static CompletableFuture<ProfileKeyPair> keyPair() {
      Minecraft mc = Minecraft.getInstance();
      ProfileKeyPairManager manager = mc.getProfileKeyPairManager();
      if (manager == ProfileKeyPairManager.EMPTY_KEY_MANAGER) {
         try {
            for (Field f : Minecraft.class.getDeclaredFields()) {
               if (f.getType() == ProfileKeyPairManager.class) {
                  f.setAccessible(true);
                  Object real = f.get(mc);
                  if (real instanceof ProfileKeyPairManager m && m != ProfileKeyPairManager.EMPTY_KEY_MANAGER) manager = m;
               }
            }
         } catch (ReflectiveOperationException | RuntimeException e) {
            SpecterClientAddon.LOG.warn("DonutGram: could not reach the profile key manager", e);
         }
      }
      return manager.prepareKeyPair().thenApply((Optional<ProfileKeyPair> kp) -> kp.orElseThrow(() -> new GramException(0, "no_key",
         "Your account has no profile key (offline mode, or Microsoft hasn't issued one). DonutGram needs a signed-in Minecraft account.", null)));
   }

   private static JsonObject proof(ProfileKeyPair kp, String message) {
      try {
         ProfilePublicKey.Data data = kp.publicKey().data();
         Signature sig = Signature.getInstance("SHA256withRSA");
         sig.initSign(kp.privateKey());
         sig.update(message.getBytes(StandardCharsets.UTF_8));
         JsonObject p = new JsonObject();
         Base64.Encoder b64 = Base64.getEncoder();
         p.addProperty("key", b64.encodeToString(data.key().getEncoded()));
         p.addProperty("expiresAt", data.expiresAt().toEpochMilli());
         p.addProperty("keySignature", b64.encodeToString(data.keySignature()));
         p.addProperty("signature", b64.encodeToString(sig.sign()));
         return p;
      } catch (Exception e) {
         throw new GramException(0, "sign_failed", "Couldn't sign in with your Minecraft account.", null);
      }
   }

   /** Runs {@code action} signed in, signing in first when needed. */
   private <T> CompletableFuture<T> authed(java.util.function.Supplier<CompletableFuture<T>> action) {
      if (this.signedIn()) return action.get();
      return this.signIn().thenCompose(m -> action.get());
   }

   // ============================================================================================== calls

   public CompletableFuture<JsonObject> feed(long before) {
      String path = "/v1/gram/feed" + (before > 0 ? "?before=" + before : "");
      return onMain(this.call("GET", path, null, this.signedIn()));
   }

   public CompletableFuture<JsonObject> refreshMe() {
      return onMain(this.authed(() -> this.call("GET", "/v1/gram/me", null, true)).thenApply(res -> {
         this.setMe(res.getAsJsonObject("me"));
         return res;
      }));
   }

   public CompletableFuture<JsonObject> like(String postId, boolean like) {
      JsonObject body = new JsonObject();
      body.addProperty("like", like);
      return onMain(this.authed(() -> this.call("POST", "/v1/gram/posts/" + postId + "/like", body.toString(), true)));
   }

   public CompletableFuture<JsonObject> report(String postId, String reason) {
      JsonObject body = new JsonObject();
      body.addProperty("reason", reason);
      return onMain(this.authed(() -> this.call("POST", "/v1/gram/posts/" + postId + "/report", body.toString(), true)));
   }

   public CompletableFuture<JsonObject> delete(String postId) {
      return onMain(this.authed(() -> this.call("DELETE", "/v1/gram/posts/" + postId, null, true)));
   }

   public CompletableFuture<JsonObject> user(String name) {
      return onMain(this.call("GET", "/v1/gram/users/" + URLEncoder.encode(name, StandardCharsets.UTF_8), null, this.signedIn()));
   }

   public CompletableFuture<JsonObject> leaderboard() {
      return onMain(this.call("GET", "/v1/gram/leaderboard", null, this.signedIn()));
   }

   /** Picks a new anonymous handle (the server allows one change a day). */
   public CompletableFuture<JsonObject> changeHandle(String handle) {
      JsonObject body = new JsonObject();
      body.addProperty("handle", handle);
      return onMain(this.authed(() -> this.call("POST", "/v1/gram/handle", body.toString(), true)).thenApply(res -> {
         if (res.has("me")) this.setMe(res.getAsJsonObject("me"));
         return res;
      }));
   }

   /** A one-time code to type into /donutgram in the Specter Discord (links the Discord account; needed to post). */
   public CompletableFuture<JsonObject> linkCode() {
      return onMain(this.authed(() -> this.call("POST", "/v1/gram/link", "{}", true)));
   }

   public CompletableFuture<JsonObject> unlinkDiscord() {
      return onMain(this.authed(() -> this.call("POST", "/v1/gram/unlink", "{}", true)).thenApply(res -> {
         if (res.has("me")) this.setMe(res.getAsJsonObject("me"));
         return res;
      }));
   }

   /** Is a Discord account linked (from the last answer)? */
   public boolean discordLinked() {
      JsonObject m = this.me();
      return m != null && m.has("discord") && m.getAsJsonObject("discord").get("linked").getAsBoolean();
   }

   public CompletableFuture<JsonObject> markRead() {
      return onMain(this.authed(() -> this.call("POST", "/v1/gram/notifications/read", "{}", true)));
   }

   /** Posts an image (JPEG or PNG bytes) with a caption. */
   public CompletableFuture<JsonObject> upload(byte[] image, String mime, String caption) {
      return onMain(this.authed(() -> {
         HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SERVER + "/v1/gram/posts"))
            .timeout(Duration.ofSeconds(40))
            .header("content-type", mime)
            .header("authorization", "Bearer " + this.token)
            .header("x-specter-client", "specter/" + SpecterCore.VERSION)
            .header("x-gram-caption", URLEncoder.encode(caption, StandardCharsets.UTF_8).replace("+", "%20"))
            .POST(HttpRequest.BodyPublishers.ofByteArray(image));
         return this.send(b.build());
      }));
   }

   /** Raw image bytes of a post (not moved to the game thread: the caller decodes off-thread). */
   public CompletableFuture<byte[]> image(String postId) {
      HttpRequest req = HttpRequest.newBuilder(URI.create(SERVER + "/v1/gram/image/" + postId)).timeout(Duration.ofSeconds(20)).GET().build();
      return this.http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
         if (r.statusCode() != 200) throw new GramException(r.statusCode(), "image", "Image unavailable", null);
         return r.body();
      });
   }

   private CompletableFuture<JsonObject> call(String method, String path, @Nullable String json, boolean auth) {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SERVER + path)).timeout(Duration.ofSeconds(15))
         .header("x-specter-client", "specter/" + SpecterCore.VERSION);
      if (auth && this.token != null) b.header("authorization", "Bearer " + this.token);
      if (json != null) b.header("content-type", "application/json");
      b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
      return this.send(b.build());
   }

   private CompletableFuture<JsonObject> send(HttpRequest req) {
      return this.http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
         JsonObject body = null;
         try {
            JsonElement e = JsonParser.parseString(r.body());
            if (e.isJsonObject()) body = e.getAsJsonObject();
         } catch (RuntimeException ignored) {
         }
         if (r.statusCode() == 401 && req.headers().firstValue("authorization").isPresent()) this.token = null;
         if (r.statusCode() / 100 != 2) {
            String msg = body != null && body.has("message") ? body.get("message").getAsString() : "DonutGram is unreachable right now (" + r.statusCode() + ").";
            if (r.statusCode() == 404 && "Not found.".equals(msg)) msg = "DonutGram isn't switched on on the Specter server yet.";
            String code = body != null && body.has("error") ? body.get("error").getAsString() : "http_" + r.statusCode();
            throw new GramException(r.statusCode(), code, msg, body);
         }
         return body == null ? new JsonObject() : body;
      });
   }

   /** Completes on the game thread. */
   private static <T> CompletableFuture<T> onMain(CompletableFuture<T> f) {
      CompletableFuture<T> out = new CompletableFuture<>();
      f.whenComplete((v, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null) out.completeExceptionally(e);
         else out.complete(v);
      }));
      return out;
   }

   /** A readable message from any failure. */
   /** The server's error code (e.g. "timed_out"), or null. */
   public static String code(Throwable t) {
      Throwable c = t;
      while ((c instanceof CompletionException || c instanceof java.util.concurrent.ExecutionException) && c.getCause() != null) c = c.getCause();
      return c instanceof GramException g ? g.code : null;
   }

   public static String message(Throwable t) {
      Throwable c = t;
      while ((c instanceof CompletionException || c instanceof java.util.concurrent.ExecutionException) && c.getCause() != null) c = c.getCause();
      if (c instanceof GramException g) return g.getMessage();
      if (c instanceof java.net.http.HttpTimeoutException) return "DonutGram took too long to answer. Try again.";
      if (c instanceof java.io.IOException) return "Can't reach DonutGram. Check your connection.";
      return "Something went wrong (" + c.getClass().getSimpleName() + ").";
   }

   /** Unread notifications from the last {@link #refreshMe} answer newer than {@code afterId}. */
   public static JsonArray notifications(JsonObject meAnswer) {
      return meAnswer.has("notifications") ? meAnswer.getAsJsonArray("notifications") : new JsonArray();
   }
}

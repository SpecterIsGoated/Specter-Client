package com.jfant.specter.phone.apps.gram;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.api.AppSetting;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.api.PhoneSound;
import com.jfant.specter.phone.core.PhoneSystemUi;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.gfx.Colors;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import com.jfant.specter.phone.ui.TextInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * DonutGram: a feed of screenshots posted from Donut Phone, gone after 24 hours. Like, report, see who's on top this
 * month (the top three win Specter+ or Donut money), and post your own: take a photo of what you see right now (the
 * phone and HUD hide for the shot). Camera only: there is no way to pick a file, so only what you see in game gets posted.
 * A short wait between posts (5 minutes, set on the server).
 */
public final class DonutGramApp extends PhoneApplication {
   private enum Tab {
      FEED,
      TOP,
      POST,
      ME
   }

   /** A post as the server shows it to everyone: the owner's anonymous handle, never their Minecraft name or account. */
   private record Post(String id, String handle, boolean mine, String caption, int width, int height, long createdAt, long expiresAt, int likes, boolean liked,
                       boolean plus) {
      static Post of(JsonObject o) {
         return new Post(o.get("id").getAsString(), o.has("handle") ? o.get("handle").getAsString() : "anonymous", o.has("mine") && o.get("mine").getAsBoolean(),
            o.has("caption") ? o.get("caption").getAsString() : "", o.get("width").getAsInt(), o.get("height").getAsInt(), o.get("createdAt").getAsLong(),
            o.get("expiresAt").getAsLong(), o.get("likes").getAsInt(), o.has("liked") && o.get("liked").getAsBoolean(), o.has("plus") && o.get("plus").getAsBoolean());
      }

      Post withLikes(int n, boolean l) {
         return new Post(this.id, this.handle, this.mine, this.caption, this.width, this.height, this.createdAt, this.expiresAt, n, l, this.plus);
      }
   }

   private static final AppSetting<Boolean> NOTIFY_LIKES = AppSetting.toggle("notify-likes", "Notify me about likes", "A notification when someone likes your post.", true);

   private Tab tab = Tab.FEED;
   private final List<Post> feed = new ArrayList<>();
   private Long next;
   private boolean loading;
   private String error;
   private long loadedAt;
   @Nullable
   private Post photo;
   @Nullable
   private String userName;
   @Nullable
   private JsonObject userPage;
   @Nullable
   private JsonObject board;
   @Nullable
   private JsonObject meAnswer;
   private String status;
   private boolean busy;
   // composing
   @Nullable
   private GramImages.Prepared prepared;
   private boolean preparing;
   private boolean uploading;
   /** Front camera: photos are selfies. */
   private boolean selfie;
   private int pollTicks;

   @Override
   public List<AppSetting<?>> settings() {
      return List.of(NOTIFY_LIKES);
   }

   @Override
   protected void onOpen() {
      if (System.currentTimeMillis() - this.loadedAt > 60_000) this.reload();
      // sign in by itself (no button): the game signs with its profile key, nothing to type
      if (!GramService.get().signedIn()) {
         if (!this.busy) this.signIn(false);
      } else {
         this.loadMe();
      }
   }

   @Override
   protected void onDestroy() {
      GramImages.releaseAll();
   }

   @Override
   protected void onRoute(String route) {
      switch (route) {
         case "me" -> this.show(Tab.ME);
         case "top" -> this.show(Tab.TOP);
         case "post" -> this.show(Tab.POST);
         case "feed" -> this.show(Tab.FEED);
         default -> {
         }
      }
   }

   // ============================================================================================== data

   private void reload() {
      this.feed.clear();
      this.next = null;
      this.loadMore();
   }

   private void loadMore() {
      if (this.loading) return;
      this.loading = true;
      this.error = null;
      GramService.get().feed(this.next == null ? 0 : this.next).whenComplete((res, err) -> {
         this.loading = false;
         if (err != null) {
            this.error = GramService.message(err);
            return;
         }
         this.loadedAt = System.currentTimeMillis();
         for (JsonElement e : res.getAsJsonArray("posts")) {
            Post p = Post.of(e.getAsJsonObject());
            if (this.feed.stream().noneMatch(x -> x.id.equals(p.id))) this.feed.add(p);
         }
         this.next = res.has("next") && !res.get("next").isJsonNull() ? res.get("next").getAsLong() : null;
      });
   }

   private void show(Tab t) {
      this.tab = t;
      this.photo = null;
      this.userName = null;
      if (t == Tab.TOP) this.loadBoard();
      if (t == Tab.ME) this.loadMe();
   }

   private void loadBoard() {
      GramService.get().leaderboard().whenComplete((res, err) -> {
         if (err == null) this.board = res;
         else this.status = GramService.message(err);
      });
   }

   private void loadMe() {
      if (!GramService.get().signedIn()) return;
      GramService.get().refreshMe().whenComplete((res, err) -> {
         if (err == null) {
            this.meAnswer = res;
            if (GramService.notifications(res).size() > 0) GramService.get().markRead();
         } else {
            this.status = GramService.message(err);
         }
      });
   }

   private void signIn() {
      this.signIn(true);
   }

   private void signIn(boolean announce) {
      this.busy = true;
      this.status = null;
      GramService.get().signIn().whenComplete((me, err) -> {
         this.busy = false;
         if (err != null) {
            this.status = GramService.message(err);
            if (announce) this.phone().sound(PhoneSound.ERROR);
         } else {
            if (announce) this.phone().toast("Signed in");
            this.loadMe();
            this.reload();
         }
      });
   }

   private void toggleLike(Post p) {
      if (p.mine) {
         this.phone().toast("You can't like your own post");
         return;
      }
      boolean want = !p.liked;
      this.replace(p.withLikes(p.likes + (want ? 1 : -1), want));
      if (want) this.phone().sound(PhoneSound.LIKE);
      GramService.get().like(p.id, want).whenComplete((res, err) -> {
         if (err != null) {
            this.replace(p);
            this.phone().toast(GramService.message(err));
         } else {
            this.replace(p.withLikes(res.get("likes").getAsInt(), res.get("liked").getAsBoolean()));
         }
      });
   }

   private void replace(Post p) {
      for (int i = 0; i < this.feed.size(); i++) if (this.feed.get(i).id.equals(p.id)) this.feed.set(i, p);
      if (this.photo != null && this.photo.id.equals(p.id)) this.photo = p;
   }

   @Override
   public void backgroundTick() {
      // check for likes and rewards every three minutes while signed in and playing
      if (!GramService.get().signedIn() || Minecraft.getInstance().player == null) return;
      if (++this.pollTicks < 20 * 180) return;
      this.pollTicks = 0;
      GramService.get().refreshMe().whenComplete((res, err) -> {
         if (err != null) return;
         this.meAnswer = res;
         long last = this.phone().storage().has("lastNote") ? this.phone().storage().get("lastNote").getAsLong() : 0;
         long top = last;
         for (JsonElement e : GramService.notifications(res)) {
            JsonObject n = e.getAsJsonObject();
            long id = n.get("id").getAsLong();
            if (id <= last || n.get("read").getAsBoolean()) continue;
            top = Math.max(top, id);
            String kind = n.get("kind").getAsString();
            if (kind.equals("like") && !NOTIFY_LIKES.get(this.phone())) continue;
            this.phone().notify(kind.equals("reward") ? "DonutGram reward" : "DonutGram", n.get("text").getAsString(), "me");
         }
         if (top > last) {
            this.phone().storage().addProperty("lastNote", top);
            this.phone().saveStorage();
         }
      });
   }

   @Override
   public boolean back() {
      if (this.photo != null) {
         this.photo = null;
         return true;
      }
      if (this.userName != null) {
         this.userName = null;
         return true;
      }
      if (this.tab != Tab.FEED) {
         this.show(Tab.FEED);
         return true;
      }
      return false;
   }

   // ============================================================================================== screens

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      if (this.photo != null) {
         this.renderPhoto(c, ui, this.photo);
         return;
      }
      if (this.userName != null) {
         this.renderUser(c, ui);
         return;
      }
      switch (this.tab) {
         case FEED -> this.renderFeed(c, ui);
         case TOP -> this.renderTop(c, ui);
         case POST -> this.renderPost(c, ui);
         case ME -> this.renderMe(c, ui);
      }
      this.tabBar(c, ui);
      this.renderMenu(c, ui);
   }

   private static final float TAB_H = 30;

   private float contentBottom(PhoneCanvas c) {
      return c.height - PhoneCanvas.SAFE_BOTTOM - TAB_H;
   }

   private void tabBar(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float y = this.contentBottom(c);
      c.rect(0, y, c.width, TAB_H + PhoneCanvas.SAFE_BOTTOM, t.background());
      ui.divider(0, y, c.width);
      Glyph[] glyphs = {Glyph.HOME, Glyph.TROPHY, Glyph.PLUS, Glyph.USER};
      Tab[] tabs = Tab.values();
      float w = c.width / 4;
      for (int i = 0; i < 4; i++) {
         Tab tb = tabs[i];
         boolean sel = tb == this.tab;
         float cx = w * i + w / 2;
         if (tb == Tab.POST) {
            c.round(cx - 14, y + 5, 28, 20, 8, sel ? t.accent() : Colors.alpha(t.accent(), 0.85F));
            c.glyphAt(Glyph.PLUS, cx, y + 15, 11, 0xFFFFFFFF);
         } else {
            c.glyphAt(glyphs[i], cx, y + 15, 13, sel ? t.text() : t.textFaint());
         }
         ui.onClick(w * i, y, w, TAB_H, () -> this.show(tb));
      }
   }

   private void renderFeed(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("DonutGram", null, Glyph.REFRESH, this::reload);
      float bottom = this.contentBottom(c);
      if (this.feed.isEmpty()) {
         if (this.loading) ui.spinner(c.width / 2, top + 80, 9, t.accent());
         else if (this.error != null) {
            ui.empty(Glyph.GLOBE, "Can't load DonutGram", this.error, top + 40);
            ui.button(40, top + 140, c.width - 80, 24, "Try again", PhoneUi.Style.PRIMARY, this::reload);
         } else {
            ui.empty(Glyph.CAMERA, "No posts right now", "Posts disappear after 24 hours. Be the first: tap + to share what you see.", top + 40);
         }
         return;
      }
      ScrollView sv = ui.scroll("feed");
      float w = c.width - 16;
      List<Float> heights = new ArrayList<>();
      float total = 0;
      for (Post p : this.feed) {
         float h = this.cardHeight(c, p, w);
         heights.add(h);
         total += h + 10;
      }
      sv.begin(ui, 0, top, c.width, bottom - top, total + 30);
      float y = top + 4;
      for (int i = 0; i < this.feed.size(); i++) {
         float h = heights.get(i);
         // only draw cards near the view
         if (y + h > top + sv.offset() - 40 && y < top + sv.offset() + (bottom - top) + 40) this.card(c, ui, this.feed.get(i), 8, y, w);
         y += h + 10;
      }
      if (this.next != null) {
         if (sv.atBottom(200)) this.loadMore();
         ui.spinner(c.width / 2, y + 10, 6, t.textFaint());
      }
      sv.end(ui);
   }

   private float imageHeight(Post p, float w) {
      return Math.min(w * 1.1F, w * p.height / Math.max(1F, p.width));
   }

   private float cardHeight(PhoneCanvas c, Post p, float w) {
      float h = 34 + this.imageHeight(p, w) + 26;
      if (!p.caption.isEmpty()) h += c.wrap(p.caption, w - 16, PhoneTheme.SMALL, 4).size() * PhoneCanvas.line(PhoneTheme.SMALL) + 4;
      return h;
   }

   private void card(PhoneCanvas c, PhoneUi ui, Post p, float x, float y, float w) {
      PhoneTheme t = c.theme();
      float h = this.cardHeight(c, p, w);
      c.round(x, y, w, h, 14, t.surface());
      ui.initialAvatar(p.handle, x + 18, y + 17, 9);
      c.text(c.fit(p.handle, w - 110, PhoneTheme.SMALL), x + 32, y + 9, PhoneTheme.SMALL, t.text());
      float nw = c.width(c.fit(p.handle, w - 110, PhoneTheme.SMALL), PhoneTheme.SMALL);
      if (p.plus) {
         c.round(x + 35 + nw, y + 8.5F, 24, 9, 4.5F, 0xFFDC2649);
         c.textCentered("PLUS", x + 47 + nw, y + 13 - PhoneTheme.CAPTION * 0.42F, PhoneTheme.CAPTION * 0.85F, 0xFFFFFFFF);
      }
      c.text(PhoneSystemUi.ago(p.createdAt * 1000) + " ago  ·  " + expiresIn(p.expiresAt), x + 32, y + 20, PhoneTheme.CAPTION, t.textFaint());
      String handle = p.handle;
      ui.onClick(x, y, w * 0.6F, 34, () -> this.openUser(handle));
      ui.iconButton(x + w - 14, y + 17, 10, Glyph.MORE, t.textDim(), () -> this.menu(p));
      float iy = y + 34, ih = this.imageHeight(p, w);
      GramImages.Loaded img = GramImages.post(p.id);
      if (img != null) {
         c.imageCover(img.texture(), img.width(), img.height(), x, iy, w, ih, 0xFFFFFFFF);
      } else {
         c.rect(x, iy, w, ih, t.surfaceHigh());
         if (GramImages.failed(p.id)) {
            c.textCentered("Couldn't load - tap to retry", x + w / 2, iy + ih / 2 - 3, PhoneTheme.CAPTION, t.textDim());
         } else {
            ui.spinner(x + w / 2, iy + ih / 2, 7, t.textFaint());
         }
      }
      ui.onClick(x, iy, w, ih, () -> {
         if (GramImages.failed(p.id)) GramImages.retry(p.id);
         else this.photo = p;
      });
      float ay = iy + ih + 4;
      float pop = ui.ease("heart:" + p.id, p.liked ? 1 : 0, 14);
      int heart = Colors.lerp(t.textDim(), 0xFFFF3B6B, pop);
      c.glyph(p.liked ? Glyph.HEART : Glyph.HEART_OUTLINE, x + 8, ay + 2, 14, heart);
      c.textMid(p.likes + (p.likes == 1 ? " like" : " likes"), x + 28, ay + 9, PhoneTheme.SMALL, t.text());
      ui.onClick(x + 4, ay, 70, 18, () -> this.toggleLike(p));
      if (!p.caption.isEmpty()) {
         List<String> lines = c.wrap(p.caption, w - 16, PhoneTheme.SMALL, 4);
         for (int i = 0; i < lines.size(); i++) c.text(lines.get(i), x + 8, ay + 22 + i * PhoneCanvas.line(PhoneTheme.SMALL), PhoneTheme.SMALL, t.textDim());
      }
   }

   private static String expiresIn(long expiresAt) {
      long s = Math.max(0, expiresAt - System.currentTimeMillis() / 1000);
      return s >= 3600 ? s / 3600 + "h left" : Math.max(1, s / 60) + "m left";
   }

   private String menuFor;

   private void menu(Post p) {
      this.menuFor = p.id;
   }

   private void openUser(String handle) {
      this.userName = handle;
      this.userPage = null;
      GramService.get().user(handle).whenComplete((res, err) -> {
         if (err == null) this.userPage = res;
         else this.status = GramService.message(err);
      });
   }

   private void renderPhoto(PhoneCanvas c, PhoneUi ui, Post p) {
      c.rect(0, 0, c.width, c.height, 0xFF000000);
      GramImages.Loaded img = GramImages.post(p.id);
      float maxH = c.height - 120;
      float w = c.width, h = Math.min(maxH, w * p.height / Math.max(1F, p.width));
      if (h >= maxH) w = h * p.width / Math.max(1F, p.height);
      float x = (c.width - w) / 2, y = (c.height - h) / 2;
      if (img != null) c.image(img.texture(), x, y, w, h);
      else ui.spinner(c.width / 2, c.height / 2, 8, 0xFFFFFFFF);
      ui.iconButton(18, PhoneCanvas.SAFE_TOP + 14, 11, Glyph.BACK, 0xFFFFFFFF, this::back);
      c.text(p.handle, 34, PhoneCanvas.SAFE_TOP + 9, PhoneTheme.SMALL, 0xFFFFFFFF);
      float by = c.height - PhoneCanvas.SAFE_BOTTOM - 34;
      c.glyph(p.liked ? Glyph.HEART : Glyph.HEART_OUTLINE, 14, by, 16, p.liked ? 0xFFFF3B6B : 0xFFFFFFFF);
      c.textMid(String.valueOf(p.likes), 36, by + 8, PhoneTheme.SMALL, 0xFFFFFFFF);
      ui.onClick(10, by - 4, 60, 24, () -> this.toggleLike(p));
   }

   private void renderTop(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("Top this month", null, null, null);
      float bottom = this.contentBottom(c);
      if (this.board == null) {
         ui.spinner(c.width / 2, top + 60, 8, t.accent());
         return;
      }
      JsonObject rewards = this.board.getAsJsonObject("rewards");
      int topN = rewards.get("topN").getAsInt();
      int days = rewards.get("plusDays").getAsInt();
      long money = rewards.get("money").getAsLong();
      float x = 10, w = c.width - 20, y = top + 2;
      c.round(x, y, w, 58, 14, 0xFFF59E0B, 0xFFDB2777);
      c.glyphAt(Glyph.TROPHY, x + 24, y + 29, 22, 0xFFFFFFFF);
      c.text("Top " + topN + " on " + this.board.get("month").getAsString(), x + 44, y + 10, PhoneTheme.HEADLINE, 0xFFFFFFFF);
      c.paragraph(days + " days of Specter+ - or " + String.format("%,d", money) + " Donut money if you already have it.", x + 44, y + 24, w - 52, PhoneTheme.CAPTION, 0xEEFFFFFF, 3);
      y += 66;
      JsonArray entries = this.board.getAsJsonArray("entries");
      if (entries.isEmpty()) {
         ui.empty(Glyph.HEART, "Nobody yet", "Likes on posts made this month count. Get posting!", y + 10);
         return;
      }
      ScrollView sv = ui.scroll("board");
      sv.begin(ui, 0, y, c.width, bottom - y, entries.size() * 34 + 10);
      ui.card(x, y, w, entries.size() * 34);
      int[] medals = {0xFFFBBF24, 0xFFCBD5E1, 0xFFD97706};
      for (int i = 0; i < entries.size(); i++) {
         JsonObject e = entries.get(i).getAsJsonObject();
         float ry = y + i * 34;
         int rank = e.get("rank").getAsInt();
         String name = e.has("handle") ? e.get("handle").getAsString() : "anonymous";
         boolean mineRow = e.has("mine") && e.get("mine").getAsBoolean();
         if (i > 0) ui.divider(x + 10, ry, w - 10);
         if (mineRow) c.rect(x, ry, w, 34, t.accentSoft());
         if (rank <= 3) c.circle(x + 16, ry + 17, 8, medals[rank - 1]);
         c.textCentered(String.valueOf(rank), x + 16, ry + 17 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, rank <= 3 ? 0xFF1F1300 : t.textDim());
         c.text(c.fit(name, w - 110, PhoneTheme.BODY), x + 32, ry + 8, PhoneTheme.BODY, t.text());
         c.text(e.get("posts").getAsInt() + " posts", x + 32, ry + 20, PhoneTheme.CAPTION, t.textFaint());
         c.textRight(e.get("likes").getAsInt() + " likes", x + w - 10, ry + 17 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, t.text());
         ui.onClick(x, ry, w, 34, () -> this.openUser(name));
      }
      sv.end(ui);
   }

   private void renderPost(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("New post", null, null, null);
      float x = 10, w = c.width - 20, y = top + 2;
      if (!GramService.get().signedIn()) {
         if (this.busy) {
            ui.spinner(c.width / 2, y + 60, 9, t.accent());
            c.textCentered("Signing in with your Minecraft account...", c.width / 2, y + 80, PhoneTheme.SMALL, t.textDim());
            return;
         }
         ui.empty(Glyph.LOCK, "Sign in to post", "DonutGram checks your Minecraft account with the game's profile key - no password, nothing else leaves your game.", y + 20);
         ui.button(x + 20, y + 150, w - 40, 26, this.busy ? "Signing in..." : "Sign in", Glyph.USER, PhoneUi.Style.PRIMARY, this.busy ? null : this::signIn);
         if (this.status != null) c.paragraph(this.status, x + 10, y + 184, w - 20, PhoneTheme.CAPTION, t.danger(), 4);
         return;
      }
      if (!GramService.get().discordLinked()) {
         this.renderLink(c, ui, x, y, w, true);
         return;
      }
      JsonObject me = GramService.get().me();
      // timed out by a helper: say why and until when instead of the composer
      JsonObject timeout = me != null && me.has("timeout") && me.get("timeout").isJsonObject() ? me.getAsJsonObject("timeout") : null;
      long timedOutFor = timeout != null ? timeout.get("until").getAsLong() - System.currentTimeMillis() / 1000 : 0;
      if (timedOutFor > 0) {
         String reason = timeout.has("reason") && !timeout.get("reason").isJsonNull() ? timeout.get("reason").getAsString() : "";
         ui.empty(Glyph.LOCK, "You're timed out", "A DonutGram helper timed you out. You can post, like and report again in " + timeLeft(timedOutFor) + "."
            + (reason.isEmpty() ? "" : " Reason: " + reason), y + 20);
         return;
      }
      long next = me != null && me.has("nextUploadAt") ? me.get("nextUploadAt").getAsLong() : 0;
      long wait = next - System.currentTimeMillis() / 1000;
      float ph = w * 9 / 16;
      if (this.prepared != null) {
         c.imageCover(this.prepared.preview(), this.prepared.width(), this.prepared.height(), x, y, w, ph, 0xFFFFFFFF);
         c.cornerMask(x, y, w, ph, 12, t.background());
      } else {
         c.round(x, y, w, ph, 12, t.surface());
         if (this.preparing) ui.spinner(x + w / 2, y + ph / 2, 8, t.accent());
         else {
            c.glyphAt(Glyph.IMAGE, x + w / 2, y + ph / 2 - 6, 22, t.textFaint());
            c.textCentered(this.selfie ? "Selfie: the camera turns round to face you" : "Take a photo of what you see", x + w / 2, y + ph / 2 + 12, PhoneTheme.CAPTION, t.textDim());
         }
      }
      y += ph + 8;
      ui.button(x, y, w - 30, 24, this.prepared != null ? "Retake" : this.selfie ? "Selfie" : "Camera", Glyph.CAMERA, PhoneUi.Style.SECONDARY, this.preparing ? null : this::takePhoto);
      // flip between the back camera (what you see) and the front one (a selfie)
      ui.iconButton(x + w - 12, y + 12, 13, Glyph.REFRESH, this.selfie ? t.accent() : t.textDim(), this.preparing ? null : () -> {
         this.selfie = !this.selfie;
         this.phone().toast(this.selfie ? "Selfie camera" : "Back camera");
      });
      y += 32;
      TextInput caption = ui.field("caption");
      caption.placeholder = "Write a caption...";
      caption.maxLength = 200;
      ui.textField(caption, x, y, w, 26);
      c.textRight(caption.get().length() + "/200", x + w - 4, y + 30, PhoneTheme.CAPTION, t.textFaint());
      y += 42;
      boolean limited = wait > 0;
      String label = this.uploading ? "Posting..." : limited ? "Next post in " + Math.max(1, (wait + 59) / 60) + " min" : "Share";
      ui.button(x, y, w, 28, label, Glyph.SEND, PhoneUi.Style.PRIMARY, this.prepared == null || this.uploading || limited ? null : () -> this.upload(caption));
      y += 36;
      c.paragraph("Posted with your Discord name - never your Minecraft name. Public for 24 hours and in the Specter Discord. Camera hides F3, nametags, chat and the scoreboard, but what's on screen can still give your spot away.",
         x + 2, y, w - 4, PhoneTheme.CAPTION, t.textFaint(), 6);
      if (this.status != null) c.paragraph(this.status, x + 2, y + 50, w - 4, PhoneTheme.CAPTION, t.danger(), 3);
   }

   private boolean editingHandle;
   private String linkCode;
   private long linkExpires;
   private boolean linkBusy;

   /** "Link your Discord" (needed to post): get a code, type /donutgram code:XXXXXX in the Specter Discord, done. */
   private float renderLink(PhoneCanvas c, PhoneUi ui, float x, float y, float w, boolean big) {
      PhoneTheme t = c.theme();
      float y0 = y;
      if (big) {
         c.circle(c.width / 2, y + 26, 22, 0xFF5865F2);
         c.glyphAt(Glyph.CHAT, c.width / 2, y + 26, 20, 0xFFFFFFFF);
         c.textCentered("Link your Discord to post", c.width / 2, y + 58, PhoneTheme.HEADLINE, t.text());
         y += 74;
         y += c.paragraph("Posts show your Discord name and link your Discord profile - never your Minecraft name.", x + 6, y, w - 12, PhoneTheme.SMALL,
            t.textDim(), 4) + 8;
      }
      long left = this.linkExpires - System.currentTimeMillis() / 1000;
      if (this.linkCode != null && left > 0) {
         ui.card(x, y, w, 96);
         c.textCentered("In the Specter Discord, type:", x + w / 2, y + 10, PhoneTheme.CAPTION, t.textDim());
         String cmd = "/donutgram code:" + this.linkCode;
         c.textCentered(this.linkCode, x + w / 2, y + 26, PhoneTheme.LARGE, t.accent());
         c.textCentered(cmd, x + w / 2, y + 46, PhoneTheme.SMALL, t.text());
         c.textCentered("Expires in " + (left / 60) + ":" + String.format("%02d", left % 60), x + w / 2, y + 60, PhoneTheme.CAPTION, t.textFaint());
         float half = (w - 22) / 2;
         ui.button(x + 8, y + 70, half, 20, "Copy", Glyph.COPY, PhoneUi.Style.SECONDARY, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(cmd);
            this.phone().toast("Copied - paste it in Discord");
         });
         ui.button(x + 14 + half, y + 70, half, 20, "I did it", Glyph.CHECK, PhoneUi.Style.PRIMARY, this.linkBusy ? null : this::checkLinked);
         y += 104;
      } else {
         ui.button(x, y, w, 26, this.linkBusy ? "Getting a code..." : "Link Discord", Glyph.CHAT, PhoneUi.Style.PRIMARY, this.linkBusy ? null : this::newLinkCode);
         y += 32;
      }
      return y - y0;
   }

   private void newLinkCode() {
      this.linkBusy = true;
      GramService.get().linkCode().whenComplete((res, err) -> {
         this.linkBusy = false;
         if (err != null) {
            this.phone().toast(GramService.message(err));
            return;
         }
         this.linkCode = res.get("code").getAsString();
         this.linkExpires = res.get("expiresAt").getAsLong();
      });
   }

   private void checkLinked() {
      this.linkBusy = true;
      GramService.get().refreshMe().whenComplete((res, err) -> {
         this.linkBusy = false;
         if (err != null) {
            this.phone().toast(GramService.message(err));
            return;
         }
         this.meAnswer = res;
         if (GramService.get().discordLinked()) {
            this.linkCode = null;
            this.phone().sound(PhoneSound.SUCCESS);
            this.phone().toast("Discord linked - you can post now");
         } else {
            this.phone().toast("Not linked yet - run the command in the Specter Discord first");
         }
      });
   }

   private void saveHandle(String handle) {
      GramService.get().changeHandle(handle).whenComplete((res, err) -> {
         if (err != null) {
            this.phone().toast(GramService.message(err));
            return;
         }
         this.editingHandle = false;
         if (this.meAnswer != null) this.meAnswer.add("me", res.getAsJsonObject("me"));
         this.phone().toast("You're now " + res.getAsJsonObject("me").get("handle").getAsString());
         this.reload();
      });
   }

   private void takePhoto() {
      this.preparing = true;
      this.status = null;
      PhoneOS.get().capture(this.selfie, image -> GramImages.fromCapture(image).whenComplete((prep, err) -> {
         this.preparing = false;
         if (err != null) this.status = GramService.message(err);
         else this.prepared = prep;
      }));
   }

   private void upload(TextInput caption) {
      GramImages.Prepared p = this.prepared;
      if (p == null) return;
      this.uploading = true;
      this.status = null;
      GramService.get().upload(p.bytes(), p.mime(), caption.get().trim()).whenComplete((res, err) -> {
         this.uploading = false;
         if (err != null) {
            this.status = GramService.message(err);
            this.phone().sound(PhoneSound.ERROR);
            if ("timed_out".equals(GramService.code(err))) GramService.get().refreshMe();
            return;
         }
         this.phone().sound(PhoneSound.SUCCESS);
         this.phone().toast("Posted!");
         this.prepared = null;
         caption.set("");
         JsonObject me = GramService.get().me();
         if (me != null && res.has("nextUploadAt")) me.addProperty("nextUploadAt", res.get("nextUploadAt").getAsLong());
         this.show(Tab.FEED);
         this.reload();
      });
   }

   private static String timeLeft(long seconds) {
      long m = Math.max(1, (seconds + 59) / 60);
      if (m < 60) return m + " min";
      long h = m / 60;
      if (h < 48) return h + " h" + (m % 60 != 0 ? " " + m % 60 + " min" : "");
      return (m + 1439) / 1440 + " days";
   }

   private void renderMe(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("Profile", null, null, null);
      float x = 10, w = c.width - 20, y = top + 2;
      if (!GramService.get().signedIn()) {
         if (this.busy) {
            ui.spinner(c.width / 2, y + 60, 9, t.accent());
            c.textCentered("Signing in with your Minecraft account...", c.width / 2, y + 80, PhoneTheme.SMALL, t.textDim());
            return;
         }
         ui.empty(Glyph.USER, "Not signed in", "Sign in to see your likes, rank and rewards.", y + 20);
         ui.button(x + 20, y + 130, w - 40, 26, this.busy ? "Signing in..." : "Sign in", Glyph.USER, PhoneUi.Style.PRIMARY, this.busy ? null : this::signIn);
         if (this.status != null) c.paragraph(this.status, x + 10, y + 164, w - 20, PhoneTheme.CAPTION, t.danger(), 4);
         return;
      }
      JsonObject me = this.meAnswer != null ? this.meAnswer.getAsJsonObject("me") : GramService.get().me();
      if (me == null) {
         ui.spinner(c.width / 2, y + 60, 8, t.accent());
         return;
      }
      float bottom = this.contentBottom(c);
      ScrollView sv = ui.scroll("me");
      sv.begin(ui, 0, y, c.width, bottom - y, 560);
      boolean linked = me.has("discord") && me.getAsJsonObject("discord").get("linked").getAsBoolean();
      String handle = linked ? me.getAsJsonObject("discord").get("name").getAsString() : me.get("handle").getAsString();
      ui.initialAvatar(handle, c.width / 2, y + 26, 20);
      c.textCentered(handle, c.width / 2, y + 52, PhoneTheme.TITLE, t.text());
      long changeAt = me.has("handleChangeAt") ? me.get("handleChangeAt").getAsLong() : 0;
      boolean canChange = changeAt <= System.currentTimeMillis() / 1000;
      float lw = c.width("Change", PhoneTheme.CAPTION) + 12;
      if (!linked && canChange && !this.editingHandle) {
         ui.button(c.width / 2 + c.width(handle, PhoneTheme.TITLE) / 2 + 6, y + 51, lw, 12, "Change", PhoneUi.Style.GHOST, () -> {
            this.editingHandle = true;
            ui.field("handle").set(handle);
         });
      }
      boolean plus = me.get("plus").getAsBoolean();
      String badge = plus ? "Specter+ member" : "Specter";
      float bw = c.width(badge, PhoneTheme.CAPTION) + 14;
      c.round(c.width / 2 - bw / 2, y + 66, bw, 12, 6, plus ? 0xFFDC2649 : t.surfaceHigh());
      c.textCentered(badge, c.width / 2, y + 72 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, plus ? 0xFFFFFFFF : t.textDim());
      y += 86;
      if (this.editingHandle) {
         TextInput field = ui.field("handle");
         field.maxLength = 20;
         field.allowed = cp -> cp < 128 && (Character.isLetterOrDigit(cp) || cp == '_');
         field.placeholder = "New handle";
         Runnable save = () -> this.saveHandle(field.get().trim());
         field.onSubmit = save;
         ui.textField(field, x, y, w - 64, 24);
         ui.button(x + w - 58, y, 58, 24, "Save", PhoneUi.Style.PRIMARY, save);
         y += 30;
      }
      c.textCentered("Your Minecraft name is never shown on DonutGram.", c.width / 2, y, PhoneTheme.CAPTION, t.textFaint());
      y += 14;
      if (linked) {
         ui.card(x, y, w, 30);
         c.circle(x + 16, y + 15, 8, 0xFF5865F2);
         c.glyphAt(Glyph.CHAT, x + 16, y + 15, 9, 0xFFFFFFFF);
         c.textMid(c.fit("Discord: " + handle, w - 90, PhoneTheme.SMALL), x + 30, y + 15, PhoneTheme.SMALL, t.text());
         ui.button(x + w - 62, y + 6, 56, 18, "Unlink", PhoneUi.Style.GHOST, () -> GramService.get().unlinkDiscord().whenComplete((r, e) -> {
            if (e != null) this.phone().toast(GramService.message(e));
            else {
               if (this.meAnswer != null) this.meAnswer.add("me", r.getAsJsonObject("me"));
               this.phone().toast("Discord unlinked");
            }
         }));
         y += 38;
      } else {
         y += this.renderLink(c, ui, x, y, w, false) + 4;
      }
      float sw = (w - 12) / 3;
      String[][] stats = {
         {String.valueOf(me.get("likesThisMonth").getAsInt()), "likes"},
         {me.has("rank") && !me.get("rank").isJsonNull() ? "#" + me.get("rank").getAsInt() : "-", "rank"},
         {String.valueOf(me.get("postsActive").getAsInt()), "live posts"}
      };
      for (int i = 0; i < 3; i++) {
         float sx = x + i * (sw + 6);
         ui.card(sx, y, sw, 40);
         c.textCentered(stats[i][0], sx + sw / 2, y + 9, PhoneTheme.HEADLINE, t.text());
         c.textCentered(stats[i][1], sx + sw / 2, y + 25, PhoneTheme.CAPTION, t.textDim());
      }
      y += 48;
      if (this.meAnswer != null) {
         JsonArray rewards = this.meAnswer.getAsJsonArray("rewards");
         if (!rewards.isEmpty()) {
            ui.section("Rewards", x + 4, y);
            y += 10;
            for (JsonElement e : rewards) {
               JsonObject r = e.getAsJsonObject();
               boolean isPlus = r.get("kind").getAsString().equals("plus");
               ui.card(x, y, w, 44);
               c.text("#" + r.get("rank").getAsInt() + " in " + r.get("month").getAsString(), x + 10, y + 7, PhoneTheme.SMALL, t.text());
               if (isPlus && r.has("key") && !r.get("key").isJsonNull()) {
                  String key = r.get("key").getAsString();
                  c.text(key, x + 10, y + 20, PhoneTheme.CAPTION, t.accent());
                  c.text("Paste it into Specter+ to activate.", x + 10, y + 31, PhoneTheme.CAPTION, t.textFaint());
                  ui.iconButton(x + w - 16, y + 22, 11, Glyph.COPY, t.accent(), () -> {
                     Minecraft.getInstance().keyboardHandler.setClipboard(key);
                     this.phone().toast("Key copied");
                  });
               } else {
                  String amount = r.has("amount") && !r.get("amount").isJsonNull() ? String.format("%,d", r.get("amount").getAsLong()) : "";
                  c.text(amount + " Donut money", x + 10, y + 20, PhoneTheme.CAPTION, t.text());
                  c.text(r.get("status").getAsString().equals("paid") ? "Paid" : "On its way", x + 10, y + 31, PhoneTheme.CAPTION, t.textFaint());
               }
               y += 48;
            }
         }
         JsonArray notes = GramService.notifications(this.meAnswer);
         if (!notes.isEmpty()) {
            ui.section("Activity", x + 4, y);
            y += 10;
            int n = Math.min(8, notes.size());
            ui.card(x, y, w, n * 26);
            for (int i = 0; i < n; i++) {
               JsonObject o = notes.get(i).getAsJsonObject();
               float ry = y + i * 26;
               if (i > 0) ui.divider(x + 10, ry, w - 10);
               String kind = o.get("kind").getAsString();
               c.glyph(kind.equals("reward") ? Glyph.TROPHY : kind.equals("timeout") ? Glyph.LOCK : Glyph.HEART, x + 8, ry + 7, 11,
                  kind.equals("reward") ? 0xFFFBBF24 : kind.equals("timeout") ? t.textDim() : 0xFFFF3B6B);
               c.textMid(c.fit(o.get("text").getAsString(), w - 70, PhoneTheme.SMALL), x + 26, ry + 13, PhoneTheme.SMALL, t.text());
               c.textRight(PhoneSystemUi.ago(o.get("createdAt").getAsLong() * 1000), x + w - 8, ry + 13 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, t.textFaint());
            }
            y += n * 26 + 8;
         }
      }
      ui.button(x, y + 4, w, 24, "Sign out", PhoneUi.Style.GHOST, () -> {
         GramService.get().signOut();
         this.meAnswer = null;
      });
      sv.end(ui);
   }

   private void renderUser(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header(this.userName, this::back, null, null);
      if (this.userPage == null) {
         ui.spinner(c.width / 2, top + 60, 8, t.accent());
         return;
      }
      JsonObject u = this.userPage.getAsJsonObject("user");
      float x = 10, w = c.width - 20, y = top + 4;
      c.text((u.get("plus").getAsBoolean() ? "Specter+  ·  " : "") + u.get("likesThisMonth").getAsInt() + " likes this month"
         + (u.has("rank") && !u.get("rank").isJsonNull() ? "  ·  #" + u.get("rank").getAsInt() : ""), x + 4, y, PhoneTheme.SMALL, t.textDim());
      y += 16;
      JsonArray posts = this.userPage.getAsJsonArray("posts");
      if (posts.isEmpty()) {
         ui.empty(Glyph.IMAGE, "No live posts", "Their posts from the last 24 hours show here.", y + 10);
         return;
      }
      float cell = (w - 6) / 2, ch = cell;
      ScrollView sv = ui.scroll("user:" + this.userName);
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      sv.begin(ui, 0, y, c.width, bottom - y, ((posts.size() + 1) / 2) * (ch + 6) + 6);
      for (int i = 0; i < posts.size(); i++) {
         Post p = Post.of(posts.get(i).getAsJsonObject());
         float px = x + (i % 2) * (cell + 6), py = y + (i / 2) * (ch + 6);
         GramImages.Loaded img = GramImages.post(p.id);
         if (img != null) c.imageCover(img.texture(), img.width(), img.height(), px, py, cell, ch, 0xFFFFFFFF);
         else c.rect(px, py, cell, ch, t.surfaceHigh());
         c.round(px + 4, py + ch - 16, 34, 12, 6, 0xAA000000);
         c.glyph(Glyph.HEART, px + 7, py + ch - 14.5F, 8, 0xFFFF3B6B);
         c.text(String.valueOf(p.likes), px + 17, py + ch - 10 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, 0xFFFFFFFF);
         ui.onClick(px, py, cell, ch, () -> this.photo = p);
      }
      sv.end(ui);
   }

   /** The "..." menu on a post: delete your own, report someone else's. Drawn over the feed when open. */
   private void renderMenu(PhoneCanvas c, PhoneUi ui) {
      if (this.menuFor == null) return;
      PhoneTheme t = c.theme();
      Post p = this.feed.stream().filter(x -> x.id.equals(this.menuFor)).findFirst().orElse(null);
      if (p == null) {
         this.menuFor = null;
         return;
      }
      c.gui().nextStratum();
      c.rect(0, 0, c.width, c.height, 0x99000000);
      ui.onClick(0, 0, c.width, c.height, () -> this.menuFor = null);
      float w = c.width - 30, h = 96, x = 15, y = c.height - PhoneCanvas.SAFE_BOTTOM - TAB_H - h - 10;
      c.round(x, y, w, h, 16, t.surface());
      boolean mine = p.mine;
      c.textCentered(p.handle, x + w / 2, y + 10, PhoneTheme.SMALL, t.textDim());
      if (mine) {
         ui.button(x + 10, y + 26, w - 20, 26, "Delete post", Glyph.TRASH, PhoneUi.Style.DANGER, () -> {
            this.menuFor = null;
            GramService.get().delete(p.id).whenComplete((r, e) -> {
               if (e != null) this.phone().toast(GramService.message(e));
               else {
                  this.feed.removeIf(x2 -> x2.id.equals(p.id));
                  this.phone().toast("Deleted");
               }
            });
         });
      } else {
         ui.button(x + 10, y + 26, w - 20, 26, "Report post", Glyph.FLAG, PhoneUi.Style.DANGER, () -> {
            this.menuFor = null;
            GramService.get().report(p.id, "reported in game").whenComplete((r, e) -> {
               if (e != null) this.phone().toast(GramService.message(e));
               else {
                  this.feed.removeIf(x2 -> x2.id.equals(p.id));
                  this.phone().toast("Thanks - it's hidden for you and the team will look");
               }
            });
         });
      }
      ui.button(x + 10, y + 58, w - 20, 26, "Cancel", PhoneUi.Style.SECONDARY, () -> this.menuFor = null);
   }
}

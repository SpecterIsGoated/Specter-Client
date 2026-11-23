package com.jfant.specter.phone.apps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.api.PhoneSound;
import com.jfant.specter.phone.apps.gram.GramService;
import com.jfant.specter.phone.core.PhoneApplicationManager;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Your profile: the Minecraft account you're playing on, Specter and Specter+ (membership is only shown as confirmed
 * when the Specter server said so), DonutGram numbers, statistics from your other apps, and achievements. Everything
 * that only lives on this computer is labelled that way.
 */
public final class ProfileApp extends PhoneApplication {
   private boolean checking;
   private String status;
   private float contentH = 600;

   private record Achievement(String id, String title, String how, Glyph glyph, int color) {
   }

   private static final List<Achievement> ACHIEVEMENTS = List.of(
      new Achievement("first_post", "First post", "Share a photo on DonutGram", Glyph.CAMERA, 0xFFFF5CA8),
      new Achievement("liked_10", "Fan favourite", "Get 10 likes in one month", Glyph.HEART, 0xFFF43F5E),
      new Achievement("snake_20", "Long boi", "Reach length 20 in Snake", Glyph.PLAY, 0xFF22C55E),
      new Achievement("mines_60", "Bomb squad", "Clear Minesweeper in under 60 s", Glyph.FLAG, 0xFF64748B),
      new Achievement("ttt_3", "Unbeaten", "Win 3 Tic-Tac-Toe games in a row", Glyph.GRID, 0xFFEC4899),
      new Achievement("react_250", "Lightning", "React in under 250 ms", Glyph.SPARK, 0xFFFBBF24),
      new Achievement("places_5", "Cartographer", "Save 5 waypoints in Maps", Glyph.PIN, 0xFF10B981),
      new Achievement("travel_3", "Globetrotter", "Visit 3 DonutTravel destinations", Glyph.PLANE, 0xFF0EA5E9)
   );

   @Override
   protected void onOpen() {
      if (GramService.get().signedIn() && !this.checking) {
         this.checking = true;
         GramService.get().refreshMe().whenComplete((r, e) -> this.checking = false);
      }
   }

   @Nullable
   private JsonObject storageOf(String appId) {
      PhoneApplicationManager.Running r = PhoneOS.get().apps().launch(appId);
      return r == null ? null : r.context().storage();
   }

   private long best(String game) {
      JsonObject s = this.storageOf("games");
      if (s == null || !s.has("best")) return 0;
      JsonObject b = s.getAsJsonObject("best");
      return b.has(game) ? b.get(game).getAsLong() : 0;
   }

   private int placesSaved() {
      JsonObject s = this.storageOf("maps");
      if (s == null || !s.has("places") || !s.get("places").isJsonObject()) return 0;
      int n = 0;
      for (var e : s.getAsJsonObject("places").entrySet()) if (e.getValue().isJsonArray()) n += e.getValue().getAsJsonArray().size();
      return n;
   }

   private int destinationsVisited() {
      JsonObject s = this.storageOf("travel");
      return s != null && s.has("visited") && s.get("visited").isJsonArray() ? s.getAsJsonArray("visited").size() : 0;
   }

   /** Checks every achievement against your data and keeps the ones earned (they stay earned). */
   private List<String> unlocked() {
      JsonObject mine = this.phone().storage();
      if (!mine.has("unlocked")) mine.add("unlocked", new JsonArray());
      JsonArray arr = mine.getAsJsonArray("unlocked");
      List<String> have = new ArrayList<>();
      for (JsonElement e : arr) have.add(e.getAsString());
      JsonObject me = GramService.get().me();
      List<String> now = new ArrayList<>();
      if (me != null && (me.get("postsActive").getAsInt() > 0)) now.add("first_post");
      if (me != null && me.get("likesThisMonth").getAsInt() >= 10) now.add("liked_10");
      if (this.best("snake") >= 20) now.add("snake_20");
      long mines = this.best("minesweeper");
      if (mines > 0 && mines < 60) now.add("mines_60");
      if (this.best("tictactoe") >= 3) now.add("ttt_3");
      long react = this.best("reaction");
      if (react > 0 && react < 250) now.add("react_250");
      if (this.placesSaved() >= 5) now.add("places_5");
      if (this.destinationsVisited() >= 3) now.add("travel_3");
      boolean changed = false;
      for (String id : now) {
         if (!have.contains(id)) {
            have.add(id);
            arr.add(id);
            changed = true;
            Achievement a = ACHIEVEMENTS.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
            if (a != null) this.phone().notify("Achievement unlocked", a.title + " - " + a.how, null);
         }
      }
      if (changed) this.phone().saveStorage();
      return have;
   }

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      PhoneTheme t = c.theme();
      Minecraft mc = Minecraft.getInstance();
      float top = ui.header("Profile", null, null, null);
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      ScrollView sv = ui.scroll("profile");
      sv.begin(ui, 0, top, c.width, bottom - top, this.contentH);
      float x = 10, w = c.width - 20, y = top + 4, y0 = y;
      // account
      UUID uuid = mc.getUser().getProfileId();
      String name = mc.getUser().getName();
      c.round(x, y, w, 70, 16, t.accent(), com.jfant.specter.phone.gfx.Colors.shade(t.accent(), 0.5F));
      ui.avatar(name, uuid, x + 34, y + 35, 22);
      c.text(c.fit(name, w - 80, PhoneTheme.TITLE), x + 66, y + 18, PhoneTheme.TITLE, 0xFFFFFFFF);
      c.text("Minecraft account", x + 66, y + 36, PhoneTheme.CAPTION, 0xCCFFFFFF);
      String where = mc.getCurrentServer() != null ? "Playing on " + mc.getCurrentServer().name : mc.isLocalServer() ? "Singleplayer" : "";
      c.text(c.fit(where, w - 80, PhoneTheme.CAPTION), x + 66, y + 48, PhoneTheme.CAPTION, 0xCCFFFFFF);
      y += 80;

      // Specter / Specter+
      ui.section("Specter", x + 4, y);
      y += 10;
      ui.card(x, y, w, 62);
      c.textMid("Specter", x + 10, y + 15, PhoneTheme.BODY, t.text());
      c.textRight(String.valueOf(SpecterCore.VERSION), x + w - 10, y + 15 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, t.textDim());
      ui.divider(x + 10, y + 31, w - 10);
      c.textMid("Specter+", x + 10, y + 46, PhoneTheme.BODY, t.text());
      JsonObject me = GramService.get().me();
      if (me != null) {
         boolean plus = me.get("plus").getAsBoolean();
         String plan = me.has("plan") && !me.get("plan").isJsonNull() ? me.get("plan").getAsString() : "";
         String v = plus ? "Member" + (plan.isEmpty() ? "" : " (" + plan + ")") : "Not a member";
         c.textRight(v, x + w - 10, y + 40, PhoneTheme.SMALL, plus ? 0xFFF43F5E : t.textDim());
         c.textRight("confirmed by the Specter server", x + w - 10, y + 50, PhoneTheme.CAPTION * 0.9F, t.textFaint());
      } else {
         String label = this.checking ? "Checking..." : "Check";
         float lw = c.width(label, PhoneTheme.SMALL) + 16;
         ui.button(x + w - lw - 8, y + 37, lw, 18, label, PhoneUi.Style.GHOST, this.checking ? null : () -> {
            this.checking = true;
            this.status = null;
            GramService.get().signIn().whenComplete((r, e) -> {
               this.checking = false;
               if (e != null) {
                  this.status = GramService.message(e);
                  this.phone().sound(PhoneSound.ERROR);
               }
            });
         });
      }
      y += 66;
      if (FabricLoader.getInstance().isModLoaded("specterplus")) {
         c.text("Specter+ mod is installed on this computer.", x + 4, y, PhoneTheme.CAPTION, t.textFaint());
         y += 10;
      }
      if (this.status != null) {
         y += c.paragraph(this.status, x + 4, y, w - 8, PhoneTheme.CAPTION, t.danger(), 3);
      }
      y += 6;

      // DonutGram
      if (me != null) {
         ui.section("DonutGram", x + 4, y);
         y += 10;
         float sw = (w - 12) / 3;
         String[][] stats = {
            {String.valueOf(me.get("likesThisMonth").getAsInt()), "likes"},
            {me.has("rank") && !me.get("rank").isJsonNull() ? "#" + me.get("rank").getAsInt() : "-", "this month"},
            {String.valueOf(me.get("postsActive").getAsInt()), "live posts"}
         };
         for (int i = 0; i < 3; i++) {
            float sx = x + i * (sw + 6);
            ui.card(sx, y, sw, 40);
            c.textCentered(stats[i][0], sx + sw / 2, y + 9, PhoneTheme.HEADLINE, t.text());
            c.textCentered(stats[i][1], sx + sw / 2, y + 25, PhoneTheme.CAPTION, t.textDim());
         }
         y += 48;
      }

      // statistics (this computer)
      ui.section("Statistics  ·  this computer", x + 4, y);
      y += 10;
      long minutes = ManagementFactory.getRuntimeMXBean().getUptime() / 60000;
      String[][] rows = {
         {"Playing this session", minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min"},
         {"Waypoints saved", String.valueOf(this.placesSaved())},
         {"Destinations visited", String.valueOf(this.destinationsVisited())},
         {"Best Snake", this.best("snake") > 0 ? String.valueOf(this.best("snake")) : "-"},
         {"Best reaction", this.best("reaction") > 0 ? this.best("reaction") + " ms" : "-"},
         {"Notifications", String.valueOf(PhoneOS.get().notifications().all().size())}
      };
      ui.card(x, y, w, rows.length * 24);
      for (int i = 0; i < rows.length; i++) {
         float ry = y + i * 24;
         if (i > 0) ui.divider(x + 10, ry, w - 10);
         c.textMid(rows[i][0], x + 10, ry + 12, PhoneTheme.SMALL, t.text());
         c.textRight(rows[i][1], x + w - 10, ry + 12 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, t.textDim());
      }
      y += rows.length * 24 + 10;

      // achievements
      List<String> have = this.unlocked();
      ui.section("Achievements  ·  " + have.size() + "/" + ACHIEVEMENTS.size(), x + 4, y);
      y += 10;
      float cell = (w - 6) / 2;
      for (int i = 0; i < ACHIEVEMENTS.size(); i++) {
         Achievement a = ACHIEVEMENTS.get(i);
         boolean got = have.contains(a.id);
         float ax = x + (i % 2) * (cell + 6), ay = y + (i / 2) * 50;
         ui.card(ax, ay, cell, 44);
         c.circle(ax + 18, ay + 22, 11, got ? com.jfant.specter.phone.gfx.Colors.alpha(a.color, 0.25F) : t.surfaceHigh());
         c.glyphAt(got ? a.glyph : Glyph.LOCK, ax + 18, ay + 22, 11, got ? a.color : t.textFaint());
         c.text(c.fit(a.title, cell - 40, PhoneTheme.SMALL), ax + 34, ay + 10, PhoneTheme.SMALL, got ? t.text() : t.textDim());
         List<String> how = c.wrap(a.how, cell - 40, PhoneTheme.CAPTION * 0.9F, 2);
         for (int k = 0; k < how.size(); k++) c.text(how.get(k), ax + 34, ay + 22 + k * 7.5F, PhoneTheme.CAPTION * 0.9F, t.textFaint());
      }
      y += ((ACHIEVEMENTS.size() + 1) / 2) * 50 + 6;

      // account settings
      ui.section("Account", x + 4, y);
      y += 10;
      if (GramService.get().signedIn()) {
         ui.button(x, y, w, 24, "Sign out of DonutGram", PhoneUi.Style.SECONDARY, () -> {
            GramService.get().signOut();
            this.phone().toast("Signed out");
         });
         y += 30;
      }
      ui.button(x, y, w, 24, "Phone settings", Glyph.GEAR, PhoneUi.Style.GHOST, () -> this.phone().openApp("settings"));
      y += 30;
      this.contentH = y - y0 + 10;
      sv.end(ui);
   }
}

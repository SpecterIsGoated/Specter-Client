package com.jfant.specter.phone.apps.maps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.ReceiveMessageEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Donut Maps: a live map of the loaded world around you, saved places per server (categories, favourites,
 * distances), navigation with a beam and an on-screen arrow, sharing places through chat, and places other Donut
 * Maps users shared with you. Teleport is only offered when the server actually lets you use /tp.
 */
public final class MapsApp extends PhoneApplication {
   // DonutSMP's own wording (from real chat logs)
   private static final String NAME = "(\\.?[A-Za-z0-9_]{1,16})";
   private static final Pattern TPA = Pattern.compile("^" + NAME + " sent you a teleport request$");
   private static final Pattern TPAHERE = Pattern.compile("^" + NAME + " sent you a teleport here request$");
   private static final Pattern SETHOME = Pattern.compile("^sethome(?:\\s+(\\S+))?\\s*$", Pattern.CASE_INSENSITIVE);
   private static final Pattern DELHOME = Pattern.compile("^delhome\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
   private static final long REQUEST_MILLIS = 90_000;
   private static final Pattern SHARED = Pattern.compile("\\[DonutMaps] (.{1,40}?) @ (-?\\d{1,8}) (-?\\d{1,4}) (-?\\d{1,8})(?: ([a-z0-9_]{1,32}))?");
   private static final String[] TABS = {"Map", "Homes", "Waypoints", "Shared"};

   private int tab;
   @Nullable
   private Waypoint viewing;
   private boolean adding;
   private boolean settingHome;
   private Waypoint.Category addCategory = Waypoint.Category.BASE;
   private String addDim;
   private int zoom = 1;
   private final RadarMap radar = new RadarMap();
   private boolean subscribed;
   // a /sethome or /delhome waiting for the server to say it worked
   private String pendingHome;
   private int pendingX;
   private int pendingY;
   private int pendingZ;
   private String pendingDim;
   private long pendingAt;
   private String pendingDelete;
   private long pendingDeleteAt;
   // homes the server suggests after "/home " (its autofill), refreshed while the Homes tab is open
   private List<String> serverHomes = List.of();
   private long homesAskedAt;

   // ============================================================================================== data

   /** Which world the places belong to: the server address, or the singleplayer world's name. */
   public static String worldKey() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getSingleplayerServer() != null) return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
      if (mc.getCurrentServer() != null) return "mp:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
      return "unknown";
   }

   private JsonArray array(String key) {
      JsonObject root = this.phone().storage();
      if (!root.has(key) || !root.get(key).isJsonObject()) root.add(key, new JsonObject());
      JsonObject byWorld = root.getAsJsonObject(key);
      String world = worldKey();
      if (!byWorld.has(world)) byWorld.add(world, new JsonArray());
      return byWorld.getAsJsonArray(world);
   }

   private List<Waypoint> load(String key) {
      List<Waypoint> list = new ArrayList<>();
      for (JsonElement e : this.array(key)) {
         try {
            list.add(Waypoint.fromJson(e.getAsJsonObject()));
         } catch (RuntimeException ignored) {
         }
      }
      return list;
   }

   private void save(String key, List<Waypoint> list) {
      JsonArray arr = this.array(key);
      while (!arr.isEmpty()) arr.remove(0);
      for (Waypoint w : list) arr.add(w.toJson());
      this.phone().saveStorage();
   }

   private List<Waypoint> places() {
      return this.load("places");
   }

   private String dimension() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? "overworld" : mc.level.dimension().identifier().getPath();
   }

   @Override
   protected void onCreate() {
      SpecterCore.EVENT_BUS.subscribe(this);
      this.subscribed = true;
   }

   @Override
   protected void onDestroy() {
      if (this.subscribed) SpecterCore.EVENT_BUS.unsubscribe(this);
      this.subscribed = false;
      this.radar.release();
   }

   @Override
   protected void onClose() {
      this.radar.release();
   }

   /** /sethome and /delhome: remembered until the server confirms (then the home is saved or removed). */
   @EventHandler
   private void onCommand(com.jfant.specter.phone.core.PhoneCommandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      Matcher set = SETHOME.matcher(event.command.trim());
      if (set.matches()) {
         this.pendingHome = set.group(1) != null ? set.group(1) : "1";
         this.pendingX = mc.player.getBlockX();
         this.pendingY = mc.player.getBlockY();
         this.pendingZ = mc.player.getBlockZ();
         this.pendingDim = this.dimension();
         this.pendingAt = System.currentTimeMillis();
         return;
      }
      Matcher del = DELHOME.matcher(event.command.trim());
      if (del.matches()) {
         this.pendingDelete = del.group(1);
         this.pendingDeleteAt = System.currentTimeMillis();
      }
   }

   private void homeSet() {
      if (this.pendingHome == null || System.currentTimeMillis() - this.pendingAt > 10_000) return;
      String key = this.pendingHome;
      this.pendingHome = null;
      List<Waypoint> places = this.places();
      places.removeIf(p -> key.equalsIgnoreCase(p.home));
      Waypoint w = new Waypoint(UUID.randomUUID().toString(), "Home " + key, this.pendingX, this.pendingY, this.pendingZ, this.pendingDim, Waypoint.Category.HOME,
         false, System.currentTimeMillis(), null);
      w.home = key;
      places.add(w);
      this.save("places", places);
      this.phone().notify("Home " + key + " saved", w.x + ", " + w.y + ", " + w.z + "  \u00b7  teleport with /home " + key + " from Maps", "homes");
   }

   private void homeDeleted() {
      if (this.pendingDelete == null || System.currentTimeMillis() - this.pendingDeleteAt > 10_000) return;
      String key = this.pendingDelete;
      this.pendingDelete = null;
      List<Waypoint> places = this.places();
      if (places.removeIf(p -> key.equalsIgnoreCase(p.home))) this.save("places", places);
   }

   /** Accept / Deny buttons, only offered when the server actually has those commands. */
   private List<com.jfant.specter.phone.api.NotificationAction> requestButtons(String who) {
      List<com.jfant.specter.phone.api.NotificationAction> out = new ArrayList<>();
      if (canRun("tpaccept")) out.add(com.jfant.specter.phone.api.NotificationAction.primary("Accept", "tpaccept " + who));
      if (canRun("tpdeny")) out.add(com.jfant.specter.phone.api.NotificationAction.secondary("Deny", "tpdeny " + who));
      return out;
   }

   /** Places shared in chat by other Donut Maps users land in the Shared tab; teleport requests become notifications. */
   @EventHandler
   private void onChat(ReceiveMessageEvent event) {
      String text = event.getMessage().getString().trim();
      if (text.endsWith("Home set.")) {
         this.homeSet();
         return;
      }
      if (text.equals("Home deleted") || text.endsWith("Home deleted.")) {
         this.homeDeleted();
         return;
      }
      Matcher tpa = TPA.matcher(text);
      if (tpa.matches()) {
         String who = tpa.group(1);
         this.phone().notify("Teleport request", who + " wants to teleport to you", null, this.requestButtons(who), REQUEST_MILLIS);
         return;
      }
      Matcher here = TPAHERE.matcher(text);
      if (here.matches()) {
         String who = here.group(1);
         this.phone().notify("Teleport here request", who + " wants you to teleport to them", null, this.requestButtons(who), REQUEST_MILLIS);
         return;
      }
      Matcher m = SHARED.matcher(text);
      if (!m.find()) return;
      String before = text.substring(0, m.start()).trim();
      String from = before.isEmpty() ? "chat" : before.length() > 24 ? before.substring(0, 24) : before;
      List<Waypoint> shared = this.load("shared");
      Waypoint w = new Waypoint(UUID.randomUUID().toString(), m.group(1).trim(), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
         Integer.parseInt(m.group(4)), m.group(5) != null ? m.group(5) : "overworld", Waypoint.Category.OTHER, false, System.currentTimeMillis(), from);
      shared.removeIf(o -> o.x == w.x && o.y == w.y && o.z == w.z && o.name.equals(w.name));
      shared.add(0, w);
      while (shared.size() > 40) shared.remove(shared.size() - 1);
      this.save("shared", shared);
      this.phone().notify("Place shared with you", w.name + " (" + w.x + ", " + w.y + ", " + w.z + ")", "shared");
   }

   @Override
   protected void onRoute(String route) {
      if ("shared".equals(route)) this.tab = 3;
      else if ("places".equals(route)) this.tab = 2;
      else if ("homes".equals(route)) this.tab = 1;
      else if ("map".equals(route)) this.tab = 0;
      else if ("add".equals(route)) this.startAdd();
      else if ("sethome".equals(route)) this.settingHome = true;
   }

   @Override
   public void tick() {
      if (this.tab == 0 && this.viewing == null && !this.adding) {
         this.radar.setScale(this.zoom);
         this.radar.tick();
      }
   }

   @Override
   public boolean back() {
      if (this.adding || this.settingHome) {
         this.adding = false;
         this.settingHome = false;
         return true;
      }
      if (this.viewing != null) {
         this.viewing = null;
         return true;
      }
      return false;
   }

   // ============================================================================================== screens

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      if (this.settingHome) {
         this.renderSetHome(c, ui);
         return;
      }
      if (this.adding) {
         this.renderAdd(c, ui);
         return;
      }
      if (this.viewing != null) {
         this.renderDetail(c, ui, this.viewing);
         return;
      }
      float top = ui.header("Maps", null, Glyph.PLUS, this::startAdd);
      ui.segmented(10, top, c.width - 20, 20, TABS, this.tab, i -> this.tab = i);
      top += 26;
      switch (this.tab) {
         case 0 -> this.renderMap(c, ui, top);
         case 1 -> this.renderHomes(c, ui, top);
         case 2 -> this.renderList(c, ui, top, this.places(), "places", "No waypoints yet", "Tap + or Save Waypoint on the Map tab. Homes you set show up here too.");
         default -> this.renderList(c, ui, top, this.load("shared"), "shared", "Nothing shared yet",
            "When someone shares a place from Donut Maps in chat, it shows up here.");
      }
   }

   /** Opens the Save Waypoint form with your position filled in (you can type other coordinates). */
   private void startAdd() {
      this.adding = true;
      this.addCategory = Waypoint.Category.BASE;
      this.addDim = this.dimension();
      this.fillPending = true;
   }

   private boolean fillPending;

   private void fillHere() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      PhoneUi ui = this.uiOf();
      if (ui == null) return;
      ui.field("x").set(String.valueOf(mc.player.getBlockX()));
      ui.field("y").set(String.valueOf(mc.player.getBlockY()));
      ui.field("z").set(String.valueOf(mc.player.getBlockZ()));
   }

   private PhoneUi lastUi;

   private PhoneUi uiOf() {
      return this.lastUi;
   }

   /** The next home number you haven't set (as far as Maps knows). */
   private int nextHomeNumber() {
      java.util.Set<String> used = new java.util.HashSet<>();
      for (Waypoint p : this.places()) if (p.home != null) used.add(p.home);
      for (int i = 1; i <= 99; i++) if (!used.contains(String.valueOf(i))) return i;
      return 1;
   }

   private void renderMap(PhoneCanvas c, PhoneUi ui, float top) {
      PhoneTheme t = c.theme();
      Minecraft mc = Minecraft.getInstance();
      float size = c.width - 20, x = 10, y = top;
      c.round(x, y, size, size, 14, 0xFF101418);
      c.clip(x, y, size, size);
      net.minecraft.resources.Identifier tex = this.radar.texture();
      if (tex != null) c.image(tex, x, y, size, size);
      else ui.spinner(x + size / 2, y + size / 2, 8, t.accent());
      if (mc.player != null && tex != null) {
         int scale = this.radar.scale();
         float ppb = size / (RadarMap.SIZE * scale);
         double ox = this.radar.originX(), oz = this.radar.originZ();
         // places
         for (Waypoint w : this.places()) {
            if (!w.dim.equals(this.dimension())) continue;
            float px = x + size / 2 + (float)(w.x - ox) * ppb, py = y + size / 2 + (float)(w.z - oz) * ppb;
            boolean inside = px > x + 6 && px < x + size - 6 && py > y + 6 && py < y + size - 6;
            px = Math.clamp(px, x + 6, x + size - 6);
            py = Math.clamp(py, y + 6, y + size - 6);
            c.circle(px, py, inside ? 5.5F : 3.5F, 0xFFFFFFFF);
            c.circle(px, py, inside ? 4.3F : 2.6F, w.category.color);
            Waypoint ww = w;
            ui.onClick(px - 7, py - 7, 14, 14, () -> this.viewing = ww);
         }
         // other players in view distance
         if (mc.level != null) {
            for (AbstractClientPlayer p : mc.level.players()) {
               if (p == mc.player) continue;
               float px = x + size / 2 + (float)(p.getX() - ox) * ppb, py = y + size / 2 + (float)(p.getZ() - oz) * ppb;
               if (px < x || px > x + size || py < y || py > y + size) continue;
               c.circle(px, py, 2.6F, 0xFFFFFFFF);
               c.circle(px, py, 1.8F, 0xFFF43F5E);
            }
         }
         // you
         float px = x + size / 2 + (float)(mc.player.getX() - ox) * ppb, py = y + size / 2 + (float)(mc.player.getZ() - oz) * ppb;
         double yaw = Math.toRadians(mc.player.getYRot());
         c.circle(px, py, 6, 0x553B82F6);
         c.circle(px, py, 4, 0xFFFFFFFF);
         c.circle(px, py, 3, 0xFF3B82F6);
         c.circle(px - (float)Math.sin(yaw) * 6.5F, py + (float)Math.cos(yaw) * 6.5F, 1.5F, 0xFFFFFFFF);
      }
      c.unclip();
      c.round(x + 6, y + 6, 14, 14, 7, 0xAA000000);
      c.textCentered("N", x + 13, y + 13 - PhoneTheme.CAPTION / 2, PhoneTheme.CAPTION, 0xFFFFFFFF);
      ui.segmented(x + size - 66, y + 6, 60, 15, new String[]{"1x", "2x"}, this.zoom == 1 ? 0 : 1, i -> this.zoom = i == 0 ? 1 : 2);
      y += size + 8;
      if (mc.player != null) {
         ui.card(x, y, size, 58);
         c.text("You're in", x + 10, y + 6, PhoneTheme.CAPTION, t.textDim());
         c.text(dimName(this.dimension()), x + 10, y + 16, PhoneTheme.SMALL, t.text());
         float half = (size - 22) / 2;
         ui.button(x + 8, y + 31, half, 21, "Set Home", Glyph.HOME, PhoneUi.Style.PRIMARY, canRun("sethome") ? () -> {
            this.settingHome = true;
            ui.field("setHomeNumber").set(String.valueOf(this.nextHomeNumber()));
         } : null);
         ui.button(x + 14 + half, y + 31, half, 21, "Save Waypoint", Glyph.PIN, PhoneUi.Style.SECONDARY, this::startAdd);
         y += 64;
      }
      Waypoint nav = MapsNavigation.get().target();
      if (nav != null) {
         ui.card(x, y, size, 30);
         c.text("Navigating to", x + 10, y + 6, PhoneTheme.CAPTION, t.textDim());
         c.text(c.fit(nav.name, size - 90, PhoneTheme.SMALL), x + 10, y + 16, PhoneTheme.SMALL, t.text());
         ui.button(x + size - 60, y + 5, 54, 20, "Stop", PhoneUi.Style.SECONDARY, () -> MapsNavigation.get().stop());
      }
   }

   /**
    * Homes: teleport to any home number with /home (1-99 - how many you have depends on your rank), your homes the
    * server suggests (chat autofill), and the ones Maps saw you set with /sethome (with where they are).
    */
   private void renderHomes(PhoneCanvas c, PhoneUi ui, float top) {
      PhoneTheme t = c.theme();
      float x = 10, w = c.width - 20, y = top;
      if (!canRun("home")) {
         ui.empty(Glyph.HOME, "No /home here", "This server doesn't give you a /home command.", y + 40);
         return;
      }
      if (System.currentTimeMillis() - this.homesAskedAt > 30_000) {
         this.homesAskedAt = System.currentTimeMillis();
         com.jfant.specter.phone.core.ServerSuggestions.complete("home ").thenAccept(list -> this.serverHomes = list);
      }
      ui.card(x, y, w, 64);
      c.text("Go to home", x + 10, y + 8, PhoneTheme.SMALL, t.textDim());
      TextInput number = ui.field("homeNumber");
      number.placeholder = "1-99";
      number.maxLength = 2;
      number.allowed = Character::isDigit;
      if (number.isEmpty() && !ui.typing()) number.set("1");
      ui.iconButton(x + 22, y + 38, 10, Glyph.MINUS, t.accent(), () -> number.set(String.valueOf(Math.max(1, this.homeNumber(number) - 1))));
      ui.textField(number, x + 36, y + 26, 40, 24);
      ui.iconButton(x + 90, y + 38, 10, Glyph.PLUS, t.accent(), () -> number.set(String.valueOf(Math.min(99, this.homeNumber(number) + 1))));
      Runnable go = () -> this.goHome(String.valueOf(this.homeNumber(number)));
      number.onSubmit = go;
      ui.button(x + 106, y + 26, w - 114, 24, "Teleport", Glyph.HOME, PhoneUi.Style.PRIMARY, go);
      y += 72;
      // the homes we know: saved (with coordinates) and the server's suggestions
      List<Waypoint> saved = new ArrayList<>();
      for (Waypoint p : this.places()) if (p.home != null) saved.add(p);
      List<String> keys = new ArrayList<>();
      for (Waypoint p : saved) if (!keys.contains(p.home.toLowerCase(Locale.ROOT))) keys.add(p.home.toLowerCase(Locale.ROOT));
      for (String s : this.serverHomes) {
         String k = s.toLowerCase(Locale.ROOT);
         if (!k.isBlank() && !keys.contains(k)) keys.add(k);
      }
      keys.sort(Comparator.comparingInt((String k) -> k.matches("\\d+") ? Integer.parseInt(k) : 1000).thenComparing(k -> k));
      ui.section("Your homes", x + 4, y);
      y += 10;
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      if (keys.isEmpty()) {
         c.paragraph("Homes you set with /sethome (1-99) show up here with where they are. Use the number box above for homes set before.",
            x + 4, y + 2, w - 8, PhoneTheme.CAPTION, t.textFaint(), 4);
         return;
      }
      ScrollView sv = ui.scroll("homes");
      float rowH = 34;
      sv.begin(ui, 0, y, c.width, bottom - y, keys.size() * rowH + 8);
      ui.card(x, y, w, keys.size() * rowH);
      for (int i = 0; i < keys.size(); i++) {
         String k = keys.get(i);
         Waypoint known = saved.stream().filter(p -> p.home.equalsIgnoreCase(k)).findFirst().orElse(null);
         float ry = y + i * rowH;
         if (i > 0) ui.divider(x + 36, ry, w - 36);
         c.circle(x + 18, ry + rowH / 2, 10, Colors.alpha(Waypoint.Category.HOME.color, 0.2F));
         c.glyphAt(Glyph.HOME, x + 18, ry + rowH / 2, 10, Waypoint.Category.HOME.color);
         c.text("Home " + k, x + 36, ry + 7, PhoneTheme.BODY, t.text());
         String sub = known != null ? this.distanceText(known) + "  \u00b7  " + this.direction(known) : "set before Maps saw it";
         c.text(c.fit(sub, w - 110, PhoneTheme.CAPTION), x + 36, ry + 20, PhoneTheme.CAPTION, t.textDim());
         if (known != null) ui.onClick(x, ry, w - 70, rowH, () -> this.viewing = known);
         ui.button(x + w - 62, ry + 8, 54, 18, "Go", Glyph.HOME, PhoneUi.Style.SECONDARY, () -> this.goHome(k));
      }
      sv.end(ui);
   }

   private int homeNumber(TextInput field) {
      try {
         return Math.clamp(Integer.parseInt(field.get().trim()), 1, 99);
      } catch (NumberFormatException e) {
         return 1;
      }
   }

   private void goHome(String key) {
      if (this.phone().runCommand("home " + key)) {
         this.phone().toast("/home " + key);
         this.phone().closePhone();
      }
   }

   private void renderList(PhoneCanvas c, PhoneUi ui, float top, List<Waypoint> list, String key, String emptyTitle, String emptyText) {
      PhoneTheme t = c.theme();
      Minecraft mc = Minecraft.getInstance();
      list.sort(Comparator.comparing((Waypoint w) -> !w.favorite).thenComparingDouble(this::distance));
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      if (list.isEmpty()) {
         ui.empty(Glyph.PIN, emptyTitle, emptyText, top + 50);
         return;
      }
      ScrollView sv = ui.scroll(key);
      float rowH = 40;
      sv.begin(ui, 0, top, c.width, bottom - top, list.size() * rowH + 10);
      float x = 10, w = c.width - 20;
      ui.card(x, top, w, list.size() * rowH);
      for (int i = 0; i < list.size(); i++) {
         Waypoint p = list.get(i);
         float y = top + i * rowH;
         if (i > 0) ui.divider(x + 40, y, w - 40);
         boolean hover = ui.hovered(x, y, w, rowH);
         if (hover) c.rect(x, y, w, rowH, Colors.alpha(t.accentSoft(), 0.5F));
         c.circle(x + 20, y + rowH / 2, 12, Colors.alpha(p.category.color, 0.2F));
         c.glyphAt(p.category.glyph, x + 20, y + rowH / 2, 11, p.category.color);
         c.text(c.fit(p.name, w - 120, PhoneTheme.BODY), x + 40, y + 9, PhoneTheme.BODY, t.text());
         String sub = p.from != null ? "from " + p.from : p.home != null ? "/home " + p.home : p.category.title;
         c.text(c.fit(sub, w - 100, PhoneTheme.CAPTION), x + 40, y + 23, PhoneTheme.CAPTION, t.textDim());
         c.textRight(this.distanceText(p), x + w - 10, y + 10, PhoneTheme.SMALL, t.text());
         c.textRight(this.direction(p), x + w - 10, y + 23, PhoneTheme.CAPTION, t.textFaint());
         if (p.favorite) c.glyph(Glyph.STAR, x + w - 66, y + 9, 8, 0xFFFBBF24);
         ui.onClick(x, y, w, rowH, () -> this.viewing = p);
      }
      sv.end(ui);
   }

   private double distance(Waypoint w) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return 0;
      if (!w.dim.equals(this.dimension())) return 1.0E9;
      double dx = w.x + 0.5 - mc.player.getX(), dy = w.y - mc.player.getY(), dz = w.z + 0.5 - mc.player.getZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private String distanceText(Waypoint w) {
      if (!w.dim.equals(this.dimension())) return dimName(w.dim);
      double d = this.distance(w);
      return d >= 1000 ? String.format("%.1f km", d / 1000) : Math.round(d) + " m";
   }

   private String direction(Waypoint w) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !w.dim.equals(this.dimension())) return "";
      double dx = w.x + 0.5 - mc.player.getX(), dz = w.z + 0.5 - mc.player.getZ();
      double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
      String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
      return dirs[(int)Math.round(deg / 45) % 8];
   }

   private static String dimName(String dim) {
      return switch (dim) {
         case "overworld" -> "Overworld";
         case "the_nether" -> "Nether";
         case "the_end" -> "The End";
         default -> dim;
      };
   }

   /** Did the server send us this command (servers only send the commands you're allowed to use)? */
   static boolean canRun(String command) {
      Minecraft mc = Minecraft.getInstance();
      return mc.getConnection() != null && mc.getConnection().getCommands().getRoot().getChild(command) != null;
   }

   private void renderDetail(PhoneCanvas c, PhoneUi ui, Waypoint w) {
      PhoneTheme t = c.theme();
      boolean shared = w.from != null;
      float top = ui.header(shared ? "Shared waypoint" : w.home != null ? "Home " + w.home : "Waypoint", this::back, null, null);
      float x = 10, cw = c.width - 20, y = top + 4;
      c.round(x, y, cw, 70, 14, Colors.shade(w.category.color, 0.7F), Colors.shade(w.category.color, 0.35F));
      c.circle(x + 30, y + 35, 18, 0x33FFFFFF);
      c.glyphAt(w.category.glyph, x + 30, y + 35, 18, 0xFFFFFFFF);
      c.text(c.fit(w.name, cw - 70, PhoneTheme.TITLE), x + 56, y + 18, PhoneTheme.TITLE, 0xFFFFFFFF);
      c.text(w.x + ", " + w.y + ", " + w.z + "  ·  " + dimName(w.dim), x + 56, y + 38, PhoneTheme.SMALL, 0xDDFFFFFF);
      y += 78;
      c.text(this.distanceText(w) + "  " + this.direction(w), x + 4, y, PhoneTheme.BODY, t.text());
      if (shared) c.text("Shared by " + w.from + "  ·  " + PhoneSystemUi.ago(w.created) + " ago", x + 4, y + 14, PhoneTheme.CAPTION, t.textDim());
      y += 30;
      boolean sameWorld = w.dim.equals(this.dimension());
      Waypoint nav = MapsNavigation.get().target();
      boolean navigating = nav != null && nav.id.equals(w.id);
      ui.button(x, y, cw, 26, navigating ? "Stop navigation" : "Navigate", Glyph.NAV, navigating ? PhoneUi.Style.SECONDARY : PhoneUi.Style.PRIMARY,
         !sameWorld ? null : () -> {
            if (navigating) {
               MapsNavigation.get().stop();
            } else {
               MapsNavigation.get().start(w, worldKey(), arrived -> {
                  this.phone().notify("You've arrived", arrived.name, null);
                  this.phone().sound(PhoneSound.SUCCESS);
               });
               this.phone().toast("Navigating - follow the beam");
               this.phone().closePhone();
            }
         });
      y += 32;
      if (!sameWorld) {
         c.textCentered("This place is in " + dimName(w.dim) + ".", c.width / 2, y, PhoneTheme.CAPTION, t.textDim());
         y += 14;
      }
      float half = (cw - 6) / 2;
      ui.button(x, y, half, 24, "Share", Glyph.SHARE, PhoneUi.Style.SECONDARY, () -> this.phone().openChat(w.shareText()));
      ui.button(x + half + 6, y, half, 24, "Copy", Glyph.COPY, PhoneUi.Style.SECONDARY, () -> {
         Minecraft.getInstance().keyboardHandler.setClipboard(w.x + " " + w.y + " " + w.z);
         this.phone().toast("Coordinates copied");
      });
      y += 30;
      if (w.home != null && canRun("home")) {
         ui.button(x, y, cw, 24, "Teleport (/home " + w.home + ")", Glyph.HOME, PhoneUi.Style.SECONDARY, () -> {
            this.phone().runCommand("home " + w.home);
            this.phone().closePhone();
         });
         y += 30;
      }
      if (shared) {
         ui.button(x, y, cw, 24, "Save to my waypoints", Glyph.PLUS, PhoneUi.Style.GHOST, () -> {
            List<Waypoint> places = this.places();
            places.add(new Waypoint(UUID.randomUUID().toString(), w.name, w.x, w.y, w.z, w.dim, Waypoint.Category.OTHER, false, System.currentTimeMillis(), null));
            this.save("places", places);
            this.phone().toast("Saved");
         });
         y += 30;
      } else {
         ui.button(x, y, half, 24, w.favorite ? "Unfavourite" : "Favourite", w.favorite ? Glyph.STAR : Glyph.STAR_OUTLINE, PhoneUi.Style.GHOST, () -> {
            List<Waypoint> places = this.places();
            for (Waypoint p : places) if (p.id.equals(w.id)) p.favorite = !p.favorite;
            w.favorite = !w.favorite;
            this.save("places", places);
         });
         ui.button(x + half + 6, y, half, 24, "Delete", Glyph.TRASH, PhoneUi.Style.GHOST, () -> {
            List<Waypoint> places = this.places();
            places.removeIf(p -> p.id.equals(w.id));
            this.save("places", places);
            if (MapsNavigation.get().target() != null && MapsNavigation.get().target().id.equals(w.id)) MapsNavigation.get().stop();
            this.viewing = null;
         });
      }
   }

   /** Save Waypoint: name, X / Y / Z (your position to start - type any coordinates), dimension and category. */
   private void renderAdd(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      this.lastUi = ui;
      if (this.fillPending) {
         this.fillPending = false;
         this.fillHere();
      }
      float top = ui.header("Save Waypoint", this::back, null, null);
      float x = 10, w = c.width - 20, y = top + 4;
      TextInput name = ui.field("name");
      name.placeholder = "Name (e.g. Spawner farm)";
      name.maxLength = 40;
      ui.textField(name, x, y, w, 24);
      y += 30;
      // coordinates
      String[] axes = {"x", "y", "z"};
      float cw = (w - 12) / 3;
      for (int i = 0; i < 3; i++) {
         TextInput f = ui.field(axes[i]);
         f.maxLength = 9;
         f.placeholder = axes[i].toUpperCase(Locale.ROOT);
         f.allowed = cp -> Character.isDigit(cp) || cp == '-';
         float fx = x + i * (cw + 6);
         c.text(axes[i].toUpperCase(Locale.ROOT), fx + 4, y, PhoneTheme.CAPTION, t.textFaint());
         ui.textField(f, fx, y + 8, cw, 22);
      }
      y += 34;
      ui.button(x, y, w, 18, "Use my position", Glyph.CROSSHAIR, PhoneUi.Style.GHOST, this::fillHere);
      y += 24;
      // dimension
      String[] dims = {"overworld", "the_nether", "the_end"};
      String[] dimNames = {"Overworld", "Nether", "End"};
      int dimIdx = Math.max(0, java.util.Arrays.asList(dims).indexOf(this.addDim == null ? this.dimension() : this.addDim));
      ui.segmented(x, y, w, 18, dimNames, dimIdx, i -> this.addDim = dims[i]);
      y += 26;
      // category
      Waypoint.Category[] cats = Waypoint.Category.values();
      float cell = (w - 6 * 3) / 4;
      for (int i = 0; i < cats.length; i++) {
         Waypoint.Category cat = cats[i];
         float cx = x + (i % 4) * (cell + 6), cy = y + (i / 4) * 40;
         boolean sel = cat == this.addCategory;
         c.round(cx, cy, cell, 35, 10, sel ? Colors.alpha(cat.color, 0.35F) : t.surface());
         if (sel) c.stroke(cx, cy, cell, 35, 10, 1.2F, cat.color);
         c.glyphAt(cat.glyph, cx + cell / 2, cy + 13, 11, cat.color);
         c.textCentered(cat.title, cx + cell / 2, cy + 24, PhoneTheme.CAPTION, t.text());
         ui.onClick(cx, cy, cell, 35, () -> this.addCategory = cat);
      }
      y += ((cats.length + 3) / 4) * 40 + 4;
      Runnable save = () -> {
         Integer px = parseCoord(ui.field("x").get()), py = parseCoord(ui.field("y").get()), pz = parseCoord(ui.field("z").get());
         if (px == null || py == null || pz == null) {
            this.phone().toast("Type whole numbers for X, Y and Z");
            return;
         }
         String n = name.get().trim();
         if (n.isEmpty()) n = this.addCategory.title + " " + px + ", " + pz;
         String dim = this.addDim == null ? this.dimension() : this.addDim;
         List<Waypoint> places = this.places();
         places.add(new Waypoint(UUID.randomUUID().toString(), n, px, py, pz, dim, this.addCategory, false, System.currentTimeMillis(), null));
         this.save("places", places);
         name.set("");
         this.adding = false;
         this.tab = 2;
         this.phone().toast("Saved " + n);
      };
      name.onSubmit = save;
      ui.button(x, y, w, 26, "Save Waypoint", Glyph.CHECK, PhoneUi.Style.PRIMARY, save);
   }

   private static Integer parseCoord(String s) {
      try {
         int v = Integer.parseInt(s.trim());
         return Math.abs(v) <= 30_000_000 ? v : null;
      } catch (NumberFormatException e) {
         return null;
      }
   }

   /** Set Home: pick 1-99 and it runs /sethome N; Maps saves the spot once the server says "Home set." */
   private void renderSetHome(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      Minecraft mc = Minecraft.getInstance();
      float top = ui.header("Set Home", this::back, null, null);
      float x = 10, w = c.width - 20, y = top + 8;
      if (mc.player != null) {
         c.text("Here: " + mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ() + "  \u00b7  " + dimName(this.dimension()),
            x + 4, y, PhoneTheme.SMALL, t.textDim());
      }
      y += 18;
      ui.card(x, y, w, 64);
      c.text("Home number", x + 10, y + 8, PhoneTheme.SMALL, t.textDim());
      TextInput number = ui.field("setHomeNumber");
      number.placeholder = "1-99";
      number.maxLength = 2;
      number.allowed = Character::isDigit;
      if (number.isEmpty() && !ui.typing()) number.set(String.valueOf(this.nextHomeNumber()));
      ui.iconButton(x + 22, y + 38, 10, Glyph.MINUS, t.accent(), () -> number.set(String.valueOf(Math.max(1, this.homeNumber(number) - 1))));
      ui.textField(number, x + 36, y + 26, 40, 24);
      ui.iconButton(x + 90, y + 38, 10, Glyph.PLUS, t.accent(), () -> number.set(String.valueOf(Math.min(99, this.homeNumber(number) + 1))));
      y += 72;
      boolean taken = false;
      String key = String.valueOf(this.homeNumber(number));
      for (Waypoint p : this.places()) if (key.equals(p.home)) taken = true;
      if (taken) {
         c.paragraph("You already have home " + key + " saved - setting it again moves it here.", x + 4, y, w - 8, PhoneTheme.CAPTION, t.warning(), 2);
         y += 16;
      }
      Runnable set = () -> {
         String k = String.valueOf(this.homeNumber(number));
         if (this.phone().runCommand("sethome " + k)) {
            this.phone().toast("Setting home " + k + "...");
            this.settingHome = false;
            this.tab = 1;
         } else {
            this.phone().toast("Not connected to a server");
         }
      };
      number.onSubmit = set;
      ui.button(x, y, w, 28, "Set Home " + key, Glyph.HOME, PhoneUi.Style.PRIMARY, set);
      y += 36;
      c.paragraph("Runs /sethome " + key + ". Teleport back any time from the Homes tab.", x + 4, y, w - 8, PhoneTheme.CAPTION, t.textFaint(), 3);
   }
}

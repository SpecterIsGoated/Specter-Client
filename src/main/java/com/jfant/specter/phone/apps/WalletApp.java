package com.jfant.specter.phone.apps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.game.ReceiveMessageEvent;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.api.PhoneSound;
import com.jfant.specter.phone.core.PhoneSystemUi;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.ui.Glyph;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.jfant.specter.phone.ui.ScrollView;
import com.jfant.specter.phone.ui.TextInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * Donut Wallet: pay a player (pick someone online, type an amount like 5M or 1.5B, confirm - it runs /pay), see your
 * balance from the server's sidebar, and a history of money in and out. Payments to you and bounties on you also
 * arrive as notifications. Everything is read from the server's own chat messages; nothing is sent anywhere else.
 */
public final class WalletApp extends PhoneApplication {
   // DonutSMP's own wording (from real chat logs)
   private static final String NAME = "(\\.?[A-Za-z0-9_]{1,16})";
   private static final String AMOUNT = "\\$ ?([0-9][0-9.,]*[KMBTkmbt]?)";
   private static final Pattern RECEIVED = Pattern.compile("^" + NAME + " was paid " + AMOUNT + " by " + NAME + "$");
   private static final Pattern SENT = Pattern.compile("^You paid " + NAME + " " + AMOUNT + "$");
   private static final Pattern BOUNTY = Pattern.compile("^" + NAME + " added " + AMOUNT + " to " + NAME + "'s bounty$");
   private static final int KEEP = 60;

   private enum Step {
      HOME,
      PICK,
      AMOUNT,
      CONFIRM
   }

   private Step step = Step.HOME;
   private String payee;
   // the chat box's autofill for "/pay <name>": finds players who aren't in the tab list
   private String typedQuery = "";
   private long typedAt;
   private String askedQuery;
   private List<String> serverNames = List.of();
   private boolean asking;
   private long amount;
   private boolean subscribed;

   @Override
   protected void onCreate() {
      SpecterCore.EVENT_BUS.subscribe(this);
      this.subscribed = true;
   }

   @Override
   protected void onDestroy() {
      if (this.subscribed) SpecterCore.EVENT_BUS.unsubscribe(this);
      this.subscribed = false;
   }

   @Override
   protected void onRoute(String route) {
      this.step = "pay".equals(route) ? Step.PICK : Step.HOME;
   }

   @Override
   public boolean back() {
      switch (this.step) {
         case PICK -> this.step = Step.HOME;
         case AMOUNT -> this.step = Step.PICK;
         case CONFIRM -> this.step = Step.AMOUNT;
         default -> {
            return false;
         }
      }
      return true;
   }

   // ============================================================================================== chat

   private static boolean isMe(String name) {
      String me = Minecraft.getInstance().getUser().getName();
      return strip(name).equalsIgnoreCase(strip(me));
   }

   private static String strip(String name) {
      return name.startsWith(".") ? name.substring(1) : name;
   }

   @EventHandler
   private void onChat(ReceiveMessageEvent event) {
      String text = event.getMessage().getString().trim();
      Matcher m = RECEIVED.matcher(text);
      if (m.matches() && isMe(m.group(1))) {
         this.record("in", m.group(3), m.group(2));
         this.phone().notify("Payment received", m.group(3) + " paid you $" + m.group(2), "history");
         return;
      }
      m = SENT.matcher(text);
      if (m.matches()) {
         this.record("out", m.group(1), m.group(2));
         return;
      }
      m = BOUNTY.matcher(text);
      if (m.matches() && isMe(m.group(3))) {
         this.record("bounty", m.group(1), m.group(2));
         this.phone().notify("Bounty on you", m.group(1) + " added $" + m.group(2) + " to your bounty", "history");
      }
   }

   @EventHandler
   private void onLeave(GameLeftEvent event) {
      this.step = Step.HOME;
   }

   private JsonArray history() {
      JsonObject s = this.phone().storage();
      if (!s.has("history") || !s.get("history").isJsonArray()) s.add("history", new JsonArray());
      return s.getAsJsonArray("history");
   }

   private void record(String kind, String who, String amount) {
      JsonArray h = this.history();
      JsonObject o = new JsonObject();
      o.addProperty("kind", kind);
      o.addProperty("who", who);
      o.addProperty("amount", amount);
      o.addProperty("time", System.currentTimeMillis());
      JsonArray fresh = new JsonArray();
      fresh.add(o);
      for (int i = 0; i < h.size() && fresh.size() < KEEP; i++) fresh.add(h.get(i));
      this.phone().storage().add("history", fresh);
      this.phone().saveStorage();
   }

   // ============================================================================================== money

   /** "5m" -> 5,000,000; "1.5B" -> 1,500,000,000; plain digits as they are. -1 if it isn't an amount. */
   static long parseAmount(String text) {
      String s = text.trim().replace(",", "").replace("$", "").toUpperCase(Locale.ROOT);
      if (s.isEmpty()) return -1;
      double mult = 1;
      char last = s.charAt(s.length() - 1);
      if (last == 'K' || last == 'M' || last == 'B' || last == 'T') {
         mult = last == 'K' ? 1e3 : last == 'M' ? 1e6 : last == 'B' ? 1e9 : 1e12;
         s = s.substring(0, s.length() - 1);
      }
      try {
         double v = Double.parseDouble(s) * mult;
         if (!(v >= 1) || v > 1e15) return -1;
         return Math.round(v);
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   /** 5000000 -> "5M", 1500 -> "1.5K". */
   static String compact(long v) {
      String[] units = {"", "K", "M", "B", "T"};
      double d = v;
      int u = 0;
      while (d >= 1000 && u < units.length - 1) {
         d /= 1000;
         u++;
      }
      String num = d >= 100 || d == Math.floor(d) ? String.valueOf((long)Math.floor(d)) : String.format(Locale.ROOT, "%.1f", Math.floor(d * 10) / 10);
      return num + units[u];
   }

   /** Your money from the server's sidebar (DonutSMP shows it there), or null if no line shows a $ amount. */
   @Nullable
   private static String sidebarBalance() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return null;
      Scoreboard sb = mc.level.getScoreboard();
      Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
      if (obj == null) return null;
      for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
         if (e.isHidden()) continue;
         PlayerTeam team = sb.getPlayersTeam(e.owner());
         String line = PlayerTeam.formatNameForTeam(team, e.ownerName()).getString();
         if (e.display() != null) line += " " + e.display().getString();
         Matcher m = Pattern.compile("\\$\\s?([0-9][0-9.,]*[KMBTkmbt]?)").matcher(line);
         if (m.find()) return m.group(1);
      }
      return null;
   }

   // ============================================================================================== screens

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      switch (this.step) {
         case PICK -> this.renderPick(c, ui);
         case AMOUNT -> this.renderAmount(c, ui);
         case CONFIRM -> this.renderConfirm(c, ui);
         default -> this.renderHome(c, ui);
      }
   }

   private void renderHome(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("Wallet", null, null, null);
      float x = 10, w = c.width - 20, y = top + 2;
      c.round(x, y, w, 76, 16, 0xFF16A34A, 0xFF065F46);
      c.text("Balance", x + 14, y + 12, PhoneTheme.SMALL, 0xCCFFFFFF);
      String bal = sidebarBalance();
      if (bal != null) {
         c.text("$" + bal, x + 14, y + 26, PhoneTheme.LARGE * 1.5F, 0xFFFFFFFF);
         c.text("from the server's sidebar", x + 14, y + 60, PhoneTheme.CAPTION, 0xAAFFFFFF);
      } else {
         c.text("Not shown", x + 14, y + 28, PhoneTheme.TITLE, 0xFFFFFFFF);
         if (MapsAppAccess.canRun("bal")) {
            ui.button(x + 14, y + 50, 70, 16, "Check /bal", PhoneUi.Style.SECONDARY, () -> {
               this.phone().runCommand("bal");
               this.phone().toast("Your balance is in chat");
            });
         }
      }
      y += 84;
      boolean canPay = MapsAppAccess.canRun("pay");
      ui.button(x, y, w, 28, "Pay someone", Glyph.SEND, PhoneUi.Style.PRIMARY, canPay ? () -> this.step = Step.PICK : null);
      if (!canPay) c.textCentered("This server has no /pay for you.", c.width / 2, y + 32, PhoneTheme.CAPTION, t.textFaint());
      y += canPay ? 36 : 46;
      ui.section("Activity", x + 4, y);
      y += 10;
      JsonArray h = this.history();
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      if (h.isEmpty()) {
         ui.empty(Glyph.STAR, "Nothing yet", "Money you send and receive, and bounties on you, show up here.", y + 4);
         return;
      }
      ScrollView sv = ui.scroll("history");
      float rowH = 32;
      sv.begin(ui, 0, y, c.width, bottom - y, h.size() * rowH + 8);
      ui.card(x, y, w, h.size() * rowH);
      for (int i = 0; i < h.size(); i++) {
         JsonObject o = h.get(i).getAsJsonObject();
         String kind = o.get("kind").getAsString();
         float ry = y + i * rowH;
         if (i > 0) ui.divider(x + 34, ry, w - 34);
         int color = kind.equals("in") ? 0xFF22C55E : kind.equals("out") ? 0xFFF43F5E : 0xFFF59E0B;
         Glyph g = kind.equals("in") ? Glyph.DOWN : kind.equals("out") ? Glyph.UP : Glyph.CROSSHAIR;
         c.circle(x + 17, ry + rowH / 2, 9, com.jfant.specter.phone.gfx.Colors.alpha(color, 0.18F));
         c.glyphAt(g, x + 17, ry + rowH / 2, 9, color);
         String who = o.get("who").getAsString();
         String what = kind.equals("in") ? "From " + who : kind.equals("out") ? "To " + who : who + " - bounty";
         c.text(c.fit(what, w - 110, PhoneTheme.SMALL), x + 32, ry + 8, PhoneTheme.SMALL, t.text());
         c.text(PhoneSystemUi.ago(o.get("time").getAsLong()), x + 32, ry + 19, PhoneTheme.CAPTION, t.textFaint());
         String amt = (kind.equals("out") ? "-$" : kind.equals("in") ? "+$" : "$") + o.get("amount").getAsString();
         c.textRight(amt, x + w - 10, ry + rowH / 2 - PhoneTheme.SMALL / 2, PhoneTheme.SMALL, color);
      }
      sv.end(ui);
   }

   private void renderPick(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("Pay who?", this::back, null, null);
      float x = 10, w = c.width - 20, y = top + 2;
      TextInput search = ui.field("search");
      search.placeholder = "Search or type a name";
      search.maxLength = 17;
      ui.textField(search, x, y, w, 24);
      y += 32;
      String q = search.get().trim();
      Minecraft mc = Minecraft.getInstance();
      // ask the server (like the chat box does) once typing pauses
      if (!q.equals(this.typedQuery)) {
         this.typedQuery = q;
         this.typedAt = System.currentTimeMillis();
      }
      if (!q.isEmpty() && !q.equals(this.askedQuery) && System.currentTimeMillis() - this.typedAt > 220) {
         String asked = q;
         this.askedQuery = q;
         this.asking = true;
         com.jfant.specter.phone.core.ServerSuggestions.complete("pay " + q).thenAccept(list -> {
            if (asked.equals(this.askedQuery)) {
               this.serverNames = list;
               this.asking = false;
            }
         });
      }
      if (q.isEmpty()) {
         this.serverNames = List.of();
         this.askedQuery = null;
      }
      // names: the server's suggestions first, then matching players from the tab list
      List<String> names = new ArrayList<>();
      java.util.Set<String> seen = new java.util.HashSet<>();
      String meName = mc.getUser().getName().toLowerCase(Locale.ROOT);
      for (String n : this.serverNames) {
         if (!n.matches("\\.?[A-Za-z0-9_]{1,16}") || n.toLowerCase(Locale.ROOT).equals(meName)) continue;
         if (seen.add(n.toLowerCase(Locale.ROOT))) names.add(n);
      }
      List<String> tab = new ArrayList<>();
      if (mc.getConnection() != null) {
         for (PlayerInfo p : mc.getConnection().getListedOnlinePlayers()) {
            String n = p.getProfile().name();
            if (n.equalsIgnoreCase(mc.getUser().getName())) continue;
            if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) tab.add(n);
         }
      }
      tab.sort(Comparator.comparing(n -> n.toLowerCase(Locale.ROOT)));
      for (String n : tab) if (seen.add(n.toLowerCase(Locale.ROOT))) names.add(n);
      if (!q.isEmpty() && q.matches("\\.?[A-Za-z0-9_]{1,16}") && !seen.contains(q.toLowerCase(Locale.ROOT))) {
         ui.button(x, y, w, 24, "Pay \"" + q + "\"", Glyph.USER, PhoneUi.Style.SECONDARY, () -> this.choose(q));
         y += 30;
      }
      float bottom = c.height - PhoneCanvas.SAFE_BOTTOM;
      if (this.asking && !q.isEmpty()) {
         ui.spinner(x + 12, y + 8, 4, t.textFaint());
         c.text("Searching the server...", x + 22, y + 5, PhoneTheme.CAPTION, t.textFaint());
         y += 16;
      }
      if (names.isEmpty()) {
         c.textCentered(q.isEmpty() ? "Type a name - the server fills it in like chat does" : this.asking ? "" : "No matches", c.width / 2, y + 20, PhoneTheme.SMALL, t.textDim());
         return;
      }
      ScrollView sv = ui.scroll("players");
      float rowH = 30;
      int max = Math.min(names.size(), 200);
      sv.begin(ui, 0, y, c.width, bottom - y, max * rowH + 8);
      for (int i = 0; i < max; i++) {
         String n = names.get(i);
         float ry = y + i * rowH;
         if (ry - sv.offset() > bottom + 30 || ry - sv.offset() < y - 40) continue;
         boolean hover = ui.hovered(x, ry, w, rowH - 2);
         c.round(x, ry, w, rowH - 2, 10, hover ? t.surfaceHigh() : t.surface());
         ui.avatar(n, null, x + 15, ry + (rowH - 2) / 2, 8);
         c.textMid(c.fit(n, w - 60, PhoneTheme.BODY), x + 30, ry + (rowH - 2) / 2, PhoneTheme.BODY, t.text());
         ui.onClick(x, ry, w, rowH - 2, () -> this.choose(n));
      }
      sv.end(ui);
   }

   private void choose(String name) {
      this.payee = name;
      this.amount = 0;
      this.step = Step.AMOUNT;
   }

   private void renderAmount(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("Pay " + this.payee, this::back, null, null);
      float x = 10, w = c.width - 20, y = top + 6;
      ui.avatar(this.payee, null, c.width / 2, y + 18, 16);
      y += 42;
      TextInput field = ui.field("amount");
      field.placeholder = "Amount, e.g. 5M";
      field.maxLength = 16;
      field.allowed = cp -> Character.isDigit(cp) || cp == '.' || cp == ',' || "kmbtKMBT".indexOf(cp) >= 0;
      long v = parseAmount(field.get());
      c.textCentered(v > 0 ? "$" + compact(v) : "$0", c.width / 2, y, PhoneTheme.DISPLAY * 0.9F, v > 0 ? t.text() : t.textFaint());
      y += 28;
      c.textCentered(v > 0 ? String.format(Locale.ROOT, "%,d", v) : "Type an amount below", c.width / 2, y, PhoneTheme.SMALL, t.textDim());
      y += 16;
      ui.textField(field, x, y, w, 26);
      y += 34;
      String[] quick = {"1K", "10K", "100K", "1M", "10M"};
      float qw = (w - 4 * 5) / quick.length;
      for (int i = 0; i < quick.length; i++) {
         String q = quick[i];
         ui.button(x + i * (qw + 5), y, qw, 20, q, PhoneUi.Style.SECONDARY, () -> field.set(q));
      }
      y += 30;
      Runnable next = () -> {
         long a = parseAmount(field.get());
         if (a <= 0) {
            this.phone().toast("Type an amount first");
            return;
         }
         this.amount = a;
         this.step = Step.CONFIRM;
      };
      field.onSubmit = next;
      ui.button(x, y, w, 28, "Continue", Glyph.CHEVRON, PhoneUi.Style.PRIMARY, v > 0 ? next : null);
   }

   private void renderConfirm(PhoneCanvas c, PhoneUi ui) {
      PhoneTheme t = c.theme();
      float top = ui.header("Confirm", this::back, null, null);
      float x = 10, w = c.width - 20, y = top + 20;
      ui.card(x, y, w, 120);
      ui.avatar(this.payee, null, c.width / 2, y + 26, 16);
      c.textCentered("Pay " + this.payee, c.width / 2, y + 50, PhoneTheme.HEADLINE, t.text());
      c.textCentered("$" + compact(this.amount), c.width / 2, y + 66, PhoneTheme.LARGE * 1.3F, 0xFF22C55E);
      c.textCentered(String.format(Locale.ROOT, "%,d", this.amount), c.width / 2, y + 96, PhoneTheme.SMALL, t.textDim());
      y += 132;
      c.paragraph("This runs /pay " + this.payee + " " + this.amount + " on the server. Payments can't be undone, so check the name.", x + 4, y, w - 8,
         PhoneTheme.CAPTION, t.textFaint(), 3);
      y += 30;
      ui.button(x, y, w, 28, "Pay $" + compact(this.amount), Glyph.SEND, PhoneUi.Style.PRIMARY, () -> {
         if (this.phone().runCommand("pay " + this.payee + " " + this.amount)) {
            this.phone().sound(PhoneSound.SUCCESS);
            this.phone().toast("Sent - the server confirms in chat");
         } else {
            this.phone().toast("Not connected to a server");
         }
         ui.field("amount").set("");
         this.step = Step.HOME;
      });
      ui.button(x, y + 34, w, 24, "Cancel", PhoneUi.Style.SECONDARY, () -> this.step = Step.HOME);
   }

   /** The same "does the server let me use this command" check Maps uses. */
   private static final class MapsAppAccess {
      static boolean canRun(String command) {
         Minecraft mc = Minecraft.getInstance();
         return mc.getConnection() != null && mc.getConnection().getCommands().getRoot().getChild(command) != null;
      }
   }
}

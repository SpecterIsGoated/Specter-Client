package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import com.jfant.specter.render.PurpleCard;
import com.jfant.specter.staff.StaffTeams;
import com.jfant.specter.webhook.Webhook;
import com.jfant.specter.webhook.WebhookSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import org.joml.Matrix3x2fStack;

public class StaffWatch extends Module {
   private static final int SCAN_TICKS = 10;
   private static final long QUIET_MS = 4000L;
   private static final long EMPTY_LOG_MS = 15000L;
   private static final long OFF_TAB_GRACE_MS = 3000L;
   private static final long JOINED_MS = 5000L;
   private static final int MAX_LINES = 14;
   private static final double NEAR_FAR = 96.0;
   private static final int OFF_TAB = -22016;
   private static final int NEAR_CLOSE = -43691;
   private static final int LEFT_FACE = -2130706433;
   private static final String TITLE_TEXT = "Staff Watch";
   private static final int PAD = 7;
   private static final int DIVIDER_Y = 11;
   private static final int HEADER = 15;
   private static final int LINE = 12;
   private static final int FACE = 8;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAlerts = this.settings.createGroup("Alerts");
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");
   private final Setting<Boolean> developers = this.sgGeneral
      .add(new BoolSetting.Builder().name("developers").description("Show the owner and developers.").defaultValue(true).build());
   private final Setting<Boolean> staffShown = this.sgGeneral
      .add(new BoolSetting.Builder().name("staff").description("Show admins, mods and helpers, senior ones included.").defaultValue(true).build());
   private final Setting<Integer> keepLeft = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("keep-left")
            .description("Seconds someone who left stays on the card, greyed out. 0 drops them straight away.")
            .defaultValue(30)
            .min(0)
            .sliderRange(0, 300)
            .build()
      );
   private final Setting<Boolean> serverNames = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("server-names")
            .description("Write names the way the server's tab list does, rank badge included.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> hideWhenClear = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-when-clear").description("Hide the card while there is nobody to show.").defaultValue(false).build());
   private final Setting<Integer> width = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("width")
            .description("Width of the card. It stays this wide whoever is on, so it never shifts around.")
            .defaultValue(180)
            .min(150)
            .sliderRange(150, 300)
            .build()
      );
   private final Setting<Boolean> sound = this.sgAlerts
      .add(
         new BoolSetting.Builder()
            .name("sound")
            .description("Play a sound when a developer or staff member joins, leaves or comes near. Only you hear it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> chatMessage = this.sgAlerts
      .add(new BoolSetting.Builder().name("chat-message").description("Also say it in chat. Only you see it.").defaultValue(false).build());
   private final Setting<Boolean> alertNear = this.sgAlerts
      .add(
         new BoolSetting.Builder()
            .name("alert-near")
            .description("Alert when a developer or staff member comes into render distance.")
            .defaultValue(true)
            .build()
      );
   private final WebhookSettings webhook = new WebhookSettings(this.sgWebhook, "Staff Watch");
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.TopRight, 4, 105);
   private final Setting<Double> scale = this.sgPosition
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .build()
      );
   private final Map<String, StaffWatch.Person> people = new LinkedHashMap<>();
   private final Map<String, StaffTeams.Member> found = new LinkedHashMap<>();
   private final Map<String, AbstractClientPlayer> loaded = new HashMap<>();
   private final Set<Integer> logged = new HashSet<>();
   private boolean described;
   private long joinedAt;
   private final List<StaffWatch.Person> sorted = new ArrayList<>();
   private final List<Object> lines = new ArrayList<>();
   private ClientLevel lastWorld;
   private long quietUntil;
   private int ticks;
   private static final Comparator<StaffWatch.Person> ORDER = Comparator.<StaffWatch.Person>comparingInt(p -> p.rank.ordinal())
      .thenComparingInt(p -> p.leftAt == 0L ? 0 : 1)
      .thenComparingLong(p -> p.leftAt == 0L ? 0L : -p.leftAt)
      .thenComparingInt(p -> p.order)
      .thenComparing(p -> p.key);

   public StaffWatch() {
      super(
         SpecterClientAddon.HUD,
         "staff-watch",
         "Shows which developers and staff are on your server, read from the tab, and alerts when they join, leave or come near."
      );
   }

   @Override
   public void onActivate() {
      this.reset();
      this.described = false;
      this.joinedAt = System.currentTimeMillis();
   }

   @Override
   public void onDeactivate() {
      this.people.clear();
      this.sorted.clear();
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.onlineCount());
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.reset();
      this.logged.clear();
      this.described = false;
      this.joinedAt = System.currentTimeMillis();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.getConnection() != null) {
         long now = System.currentTimeMillis();
         if (this.mc.level != this.lastWorld) {
            this.reset();
         }

         boolean quiet = now < this.quietUntil;
         if (this.ticks++ % 10 == 0) {
            this.scan(now, quiet);
            this.logEmpty(now);
         }

         this.trackNear(quiet);
      }
   }

   private void reset() {
      this.people.clear();
      this.sorted.clear();
      this.lastWorld = this.mc.level;
      this.quietUntil = System.currentTimeMillis() + 4000L;
      this.ticks = 0;
   }

   private void scan(long now, boolean quiet) {
      StaffTeams.scan(this.mc.level, this.mc.getConnection(), this.mc.player.getGameProfile().name(), this.found);
      Iterator<Entry<String, StaffTeams.Member>> it = this.found.entrySet().iterator();

      while (it.hasNext()) {
         Entry<String, StaffTeams.Member> f = it.next();
         String key = f.getKey();
         StaffTeams.Member seen = f.getValue();
         this.logLook(seen);
         if (!this.shown(seen.order())) {
            it.remove();
         } else {
            StaffWatch.Person p = this.people.get(key);
            if (p == null) {
               p = new StaffWatch.Person(key);
               this.people.put(key, p);
            }

            boolean arrived = p.leftAt != 0L || p.name == null;
            p.name = seen.name();
            p.order = seen.order();
            p.rank = seen.rank();
            if (seen.entry() != null) {
               p.skin = seen.entry().getSkin();
               p.mode = seen.entry().getGameMode();
               if (seen.entry().getTabListDisplayName() != null) {
                  p.shown = seen.entry().getTabListDisplayName();
               }
            }

            if (arrived) {
               p.leftAt = 0L;
               p.offTabSince = 0L;
               p.joinedAt = quiet ? 0L : now;
               if (!quiet) {
                  this.alert(StaffWatch.Alert.JOIN, seen.name() + " (" + seen.rank().single + ") joined the server.", SoundEvents.ARROW_HIT_PLAYER, 1.0F);
               }
            }

            if (seen.entry() != null) {
               p.offTabSince = 0L;
            } else if (p.offTabSince == 0L) {
               p.offTabSince = now;
            }
         }
      }

      long keepMs = this.keepLeft.get().intValue() * 1000L;
      Iterator<StaffWatch.Person> itx = this.people.values().iterator();

      while (itx.hasNext()) {
         StaffWatch.Person px = itx.next();
         if (!this.found.containsKey(px.key)) {
            if (px.leftAt == 0L) {
               if (!quiet && this.shown(px.order)) {
                  this.alert(StaffWatch.Alert.LEFT, px.name + " left the server.", SoundEvents.ARROW_HIT_PLAYER, 0.6F);
               }

               if (!quiet && this.shown(px.order) && keepMs != 0L) {
                  px.leftAt = now;
                  px.near = false;
               } else {
                  itx.remove();
               }
            } else if (now - px.leftAt > keepMs || !this.shown(px.order)) {
               itx.remove();
            }
         }
      }
   }

   private boolean shown(int order) {
      return order <= 2 ? this.developers.get() : this.staffShown.get();
   }

   private void logLook(StaffTeams.Member seen) {
      PlayerInfo entry = seen.entry();
      int key = seen.source() == StaffTeams.Source.TEAM ? seen.order() : 100 + seen.source().ordinal() * 10 + seen.order();
      if (entry != null && !this.logged.contains(key)) {
         this.logged.add(key);
         SpecterClientAddon.LOG
            .info(
               "Staff Watch: tab order {} ({}) by {} (sort number {}) shows as {}",
               new Object[]{
                  String.format(Locale.ROOT, "%02d", seen.order()),
                  seen.name(),
                  seen.source().name().toLowerCase(Locale.ROOT),
                  entry.getTabListOrder(),
                  entry.getTabListDisplayName() == null ? "a plain name" : entry.getTabListDisplayName().toString()
               }
            );
      }
   }

   private void logEmpty(long now) {
      if (!this.described && now - this.joinedAt >= 15000L && this.found.isEmpty()) {
         this.described = true;
         SpecterClientAddon.LOG.info("Staff Watch: no developers or staff found - {}", StaffTeams.describe(this.mc.level, this.mc.getConnection()));
      }
   }

   private void trackNear(boolean quiet) {
      if (!this.people.isEmpty()) {
         this.loaded.clear();

         for (AbstractClientPlayer e : this.mc.level.players()) {
            if (e != this.mc.player) {
               this.loaded.put(e.getGameProfile().name().toLowerCase(Locale.ROOT), e);
            }
         }

         for (StaffWatch.Person p : this.people.values()) {
            AbstractClientPlayer ex = p.leftAt == 0L ? this.loaded.get(p.key) : null;
            if (ex == null) {
               p.near = false;
            } else {
               p.distance = this.mc.player.distanceTo(ex);
               if (!p.near && !quiet && this.alertNear.get()) {
                  this.alert(
                     StaffWatch.Alert.NEAR, p.name + " is near you - " + Math.round(p.distance) + " blocks away.", SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F
                  );
               }

               p.near = true;
            }
         }
      }
   }

   private void alert(StaffWatch.Alert kind, String message, SoundEvent event, float pitch) {
      if (this.sound.get()) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, 1.0F));
      }

      if (this.chatMessage.get()) {
         this.info(message, new Object[0]);
      }

      if (this.webhook.enabled()) {
         this.webhook.send(new Webhook.Embed(kind.title, message, kind.color).context());
      }
   }

   private int onlineCount() {
      int n = 0;

      for (StaffWatch.Person p : this.people.values()) {
         if (p.leftAt == 0L) {
            n++;
         }
      }

      return n;
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (!this.mc.gui.hud.isHidden() && !this.mc.debugEntries.isOverlayVisible()) {
            this.sorted.clear();
            this.sorted.addAll(this.people.values());
            this.sorted.sort(ORDER);
            if (!this.sorted.isEmpty() || !this.hideWhenClear.get()) {
               long now = System.currentTimeMillis();
               GuiGraphicsExtractor ctx = event.graphics;
               Font tr = this.mc.font;
               this.lines.clear();
               int[] counts = new int[StaffTeams.Rank.values().length];

               for (StaffWatch.Person p : this.sorted) {
                  if (p.leftAt == 0L) {
                     counts[p.rank.ordinal()]++;
                  }
               }

               StaffTeams.Rank heading = null;
               int listed = 0;

               for (StaffWatch.Person px : this.sorted) {
                  if (this.lines.size() >= 13) {
                     break;
                  }

                  if (px.rank != heading) {
                     heading = px.rank;
                     this.lines.add(heading);
                  }

                  this.lines.add(px);
                  listed++;
               }

               int more = this.sorted.size() - listed;
               int online = this.onlineCount();
               String count = online == 0 ? "Clear" : online + " online";
               int countColor = online == 0 ? -4149795 : -1;
               int lineCount = Math.max(1, this.lines.size() + (more > 0 ? 1 : 0));
               int boxWidth = this.width.get();
               int innerWidth = boxWidth - 14;
               int boxHeight = 15 + lineCount * 12 - 4 + 14;
               float sc = this.scale.get().floatValue();
               this.spot.place(boxWidth * sc, boxHeight * sc);
               float x = this.spot.x();
               float y = this.spot.y();
               Matrix3x2fStack matrices = ctx.pose();
               matrices.pushMatrix();
               matrices.translate(Math.round(x), Math.round(y));
               matrices.scale(sc, sc);
               PurpleCard.card(ctx, 0, 0, boxWidth, boxHeight);
               matrices.translate(7.0F, 7.0F);
               CardText.draw(ctx, "Staff Watch", 0.0F, 0.0F, -1065473, true);
               CardText.draw(ctx, count, (float)(innerWidth - CardText.width(count)), 0.0F, countColor, true);
               ctx.fill(0, 11, innerWidth, 12, 1440926975);
               int rowY = 15;
               if (this.lines.isEmpty()) {
                  CardText.draw(ctx, "No staff on this server", 0.0F, (float)rowY, -4149795, true);
               }

               for (Object line : this.lines) {
                  if (line instanceof StaffTeams.Rank r) {
                     this.drawHeading(ctx, tr, r, counts[r.ordinal()], rowY);
                  } else {
                     this.drawRow(ctx, tr, (StaffWatch.Person)line, rowY, innerWidth, now);
                  }

                  rowY += 12;
               }

               if (more > 0) {
                  CardText.draw(ctx, "+" + more + " more", 0.0F, (float)rowY, -4149795, true);
               }

               matrices.popMatrix();
            }
         }
      }
   }

   private void drawHeading(GuiGraphicsExtractor ctx, Font tr, StaffTeams.Rank rank, int online, int y) {
      CardText.draw(ctx, rank.plural, 0.0F, (float)y, -1911561, true);
      CardText.draw(ctx, Integer.toString(online), (float)(CardText.width(rank.plural) + 4), (float)y, -4149795, true);
   }

   private void drawRow(GuiGraphicsExtractor ctx, Font tr, StaffWatch.Person p, int y, int innerWidth, long now) {
      boolean left = p.leftAt != 0L;
      PlayerSkin skin = p.skin != null ? p.skin : DefaultPlayerSkin.getDefaultSkin();
      PlayerFaceExtractor.extractRenderState(ctx, skin, 0, y, 8, left ? -2130706433 : -1);
      String status = status(p, now);
      int statusWidth = status.isEmpty() ? 0 : CardText.width(status) + 6;
      int nameRoom = innerWidth - 8 - 4 - statusWidth;
      CardText.draw(ctx, clip(tr, this.nameText(p), nameRoom), 12.0F, (float)y, left ? -4149795 : -1, true);
      if (!status.isEmpty()) {
         CardText.draw(ctx, status, (float)(innerWidth - CardText.width(status)), (float)y, statusColor(p, now), true);
      }
   }

   private Component nameText(StaffWatch.Person p) {
      return (Component)(this.serverNames.get() && p.shown != null && p.leftAt == 0L ? p.shown : Component.literal(p.name));
   }

   private static FormattedCharSequence clip(Font tr, Component text, int maxWidth) {
      return CardText.clip(text, maxWidth);
   }

   private static boolean offTab(StaffWatch.Person p, long now) {
      return p.leftAt == 0L && p.offTabSince != 0L && now - p.offTabSince >= 3000L;
   }

   private static String status(StaffWatch.Person p, long now) {
      if (p.leftAt != 0L) {
         return "Left " + ago(now - p.leftAt);
      } else if (p.near) {
         return Math.round(p.distance) + " blocks";
      } else if (offTab(p, now)) {
         return "Off tab";
      } else if (p.mode == GameType.SPECTATOR) {
         return "Spectator";
      } else if (p.mode == GameType.CREATIVE) {
         return "Creative";
      } else {
         return p.joinedAt != 0L && now - p.joinedAt < 5000L ? "Joined" : "";
      }
   }

   private static int statusColor(StaffWatch.Person p, long now) {
      if (p.leftAt != 0L) {
         return -4149795;
      } else if (p.near) {
         return PurpleCard.lerp(-43691, -1911561, (float)Math.min(1.0, p.distance / 96.0));
      } else {
         return !offTab(p, now) && p.mode != GameType.SPECTATOR ? -1065473 : -22016;
      }
   }

   private static String ago(long ms) {
      long seconds = ms / 1000L;
      return seconds < 60L ? seconds + "s" : seconds / 60L + "m";
   }

   private static enum Alert {
      JOIN("Staff joined", 5793266),
      LEFT("Staff left", 15844367),
      NEAR("Staff nearby", 15158332);

      final String title;
      final int color;

      private Alert(String title, int color) {
         this.title = title;
         this.color = color;
      }
   }

   private static final class Person {
      final String key;
      String name;
      StaffTeams.Rank rank;
      Component shown;
      PlayerSkin skin;
      GameType mode;
      int order;
      long joinedAt;
      long offTabSince;
      long leftAt;
      boolean near;
      double distance;

      Person(String key) {
         this.key = key;
      }
   }
}

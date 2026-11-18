package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.region.DonutGoliaths;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import com.jfant.specter.render.PurpleCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Matrix3x2fStack;

public class MediaBaseFinder extends Module {
   private static final String MEDIA_BADGE = "\ud83d\udcf9";
   private static final String MEDIA_TEAM = "09-";
   private static final int SETTLE_TICKS = 60;
   private static final int SCAN_TICKS = 10;
   private static final int MAX_LISTED = 8;
   private static final long RTP_WINDOW_MS = 60000L;
   private static final int OFF_MAP = -2147483648;
   private static final long BIG_IN_MS = 400L;
   private static final long BIG_STAY_MS = 3000L;
   private static final long BIG_OUT_MS = 800L;
   private static final int BIG_RGB = 16733695;
   private static final int PAD = 7;
   private static final int DIVIDER_Y = 11;
   private static final int HEADER = 15;
   private static final int LINE = 12;
   private static final int FACE = 8;
   private static final int CHECKING = -19648;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgCard = this.settings.createGroup("Card");
   private final Setting<Boolean> chat = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("chat-message")
            .description("Say in chat which media are on the goliath you land on, and when another one comes onto it. Only you see it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> reportEmpty = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("report-empty")
            .description("Also say so in chat when a goliath has no media on it.")
            .defaultValue(false)
            .visible(this.chat::get)
            .build()
      );
   private final Setting<Boolean> bigText = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("big-text")
            .description("After an /rtp, show the number of media bases in big letters in the middle of the screen.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> sound = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sound")
            .description("Play a sound with the big text. Only you hear it.")
            .defaultValue(true)
            .visible(this.bigText::get)
            .build()
      );
   private final Setting<Boolean> card = this.sgCard
      .add(new BoolSetting.Builder().name("card").description("Show a card listing the media on your goliath.").defaultValue(true).build());
   private final Setting<Boolean> hideWhenClear = this.sgCard
      .add(
         new BoolSetting.Builder()
            .name("hide-when-clear")
            .description("Hide the card while no media are on your goliath.")
            .defaultValue(false)
            .visible(this.card::get)
            .build()
      );
   private final Setting<Integer> width = this.sgCard
      .add(
         new IntSetting.Builder()
            .name("width")
            .description("Width of the card.")
            .defaultValue(170)
            .min(130)
            .sliderRange(130, 300)
            .visible(this.card::get)
            .build()
      );
   private final CardSpot spot = new CardSpot(this, this.sgCard, Corner.TopRight, 4, 200);
   private final Setting<Double> scale = this.sgCard
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .visible(this.card::get)
            .build()
      );
   private final Queue<UUID> modeUpdates = new ConcurrentLinkedQueue<>();
   private final Set<String> proven = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   private Set<String> here = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
   private String goliath = "-";
   private int settle;
   private int ticks;
   private boolean reported;
   private boolean afterRtp;
   private volatile long rtpAt;
   private ClientLevel worldSeen;
   private LocalPlayer selfSeen;
   private String brandSeen;
   private int goliathSeen = -2147483648;
   private String bigTitle;
   private String bigBelow;
   private long bigStart;

   public MediaBaseFinder() {
      super(SpecterClientAddon.BASES, "media-base-finder", "Tells you which DonutSMP media are on the goliath you land on, where their base most likely is.");
   }

   @Override
   public void onActivate() {
      this.forgetPlace();
      this.bigTitle = null;
      this.rtpAt = 0L;
      this.arrive();
   }

   @Override
   public void onDeactivate() {
      this.forgetPlace();
      this.modeUpdates.clear();
      this.proven.clear();
      this.here = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      this.bigTitle = null;
   }

   @Override
   public String getInfoString() {
      return this.settle > 0 ? null : Integer.toString(this.here.size());
   }

   private void arrive() {
      this.modeUpdates.clear();
      this.proven.clear();
      this.here = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      this.settle = 60;
      this.reported = false;
      this.goliath = this.label();
      long sent = this.rtpAt;
      this.afterRtp = sent != 0L && System.currentTimeMillis() - sent < 60000L;
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      String command = event.packet instanceof ServerboundChatCommandPacket plain
         ? plain.command()
         : (event.packet instanceof ServerboundChatCommandSignedPacket signed ? signed.command() : null);
      if (command != null) {
         String text = command.trim().toLowerCase(Locale.ROOT);
         if (text.equals("rtp") || text.startsWith("rtp ")) {
            this.rtpAt = System.currentTimeMillis();
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      this.collect(event.packet);
   }

   private void collect(Packet<?> packet) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> inner : bundle.subPackets()) {
            this.collect(inner);
         }
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket list) {
         if (!list.actions().contains(Action.UPDATE_GAME_MODE)) {
            return;
         }

         if (list.actions().contains(Action.ADD_PLAYER)) {
            return;
         }

         for (Entry entry : list.entries()) {
            if (entry != null && entry.profileId() != null) {
               this.modeUpdates.offer(entry.profileId());
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      ClientPacketListener handler = this.mc.getConnection();
      if (handler != null && this.mc.player != null && this.mc.level != null) {
         if (this.moved(handler)) {
            this.arrive();
         }

         this.drainModeUpdates(handler);
         if (this.settle > 0) {
            this.settle--;
         } else if (!this.reported || ++this.ticks % 10 == 0) {
            Set<String> found = this.scan(handler);
            this.goliath = this.label();
            if (!this.reported) {
               this.reported = true;
               this.here = found;
               this.report(found, handler);
            } else {
               List<String> arrived = new ArrayList<>();

               for (String name : found) {
                  if (!this.here.contains(name)) {
                     arrived.add(name);
                  }
               }

               this.here = found;
               if (!arrived.isEmpty()) {
                  this.announce(arrived, found.size(), false);
               }
            }
         }
      } else {
         this.forgetPlace();
      }
   }

   private boolean moved(ClientPacketListener handler) {
      ClientLevel world = this.mc.level;
      LocalPlayer self = this.mc.player;
      String brand = handler.serverBrand();
      int now = this.goliathNumber();
      boolean moved = this.worldSeen != null
         && (
            world != this.worldSeen
               || self != this.selfSeen && !this.selfSeen.isDeadOrDying()
               || now != this.goliathSeen
               || brand != null && this.brandSeen != null && !brand.equals(this.brandSeen)
         );
      this.worldSeen = world;
      this.selfSeen = self;
      this.brandSeen = brand;
      this.goliathSeen = now;
      return moved;
   }

   private void forgetPlace() {
      this.worldSeen = null;
      this.selfSeen = null;
      this.brandSeen = null;
      this.goliathSeen = -2147483648;
   }

   private boolean inOverworld() {
      return this.mc.player != null && this.mc.level != null && this.mc.level.dimension() == Level.OVERWORLD;
   }

   private int goliathNumber() {
      return this.inOverworld() ? DonutGoliaths.number(this.mc.player.getX(), this.mc.player.getZ()) : -2147483648;
   }

   private DonutGoliaths.Goliath goliathHere() {
      return this.inOverworld() ? DonutGoliaths.at(this.mc.player.getX(), this.mc.player.getZ()) : null;
   }

   private String label() {
      if (!this.inOverworld()) {
         return "-";
      } else {
         DonutGoliaths.Goliath at = this.goliathHere();
         return at == null ? "Off the map" : at.label();
      }
   }

   private static boolean isMedia(PlayerTeam team) {
      return team.getName().startsWith("09-") || team.getPlayerPrefix().getString().contains("\ud83d\udcf9");
   }

   private Set<String> scan(ClientPacketListener handler) {
      Set<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      String self = this.mc.player.getGameProfile().name();

      for (PlayerTeam team : this.mc.level.getScoreboard().getPlayerTeams()) {
         if (isMedia(team)) {
            for (String member : team.getPlayers()) {
               if (!member.equalsIgnoreCase(self) && (handler.getPlayerInfo(member) == null || this.proven.contains(member))) {
                  found.add(member);
               }
            }
         }
      }

      return found;
   }

   private void drainModeUpdates(ClientPacketListener handler) {
      UUID id;
      while ((id = this.modeUpdates.poll()) != null) {
         PlayerInfo entry = handler.getPlayerInfo(id);
         if (entry != null && entry.getTeam() != null && isMedia(entry.getTeam())) {
            String name = entry.getProfile().name();
            if (this.proven.add(name)) {
               SpecterClientAddon.LOG.info("Media Base Finder: a game mode update places {} on {}", name, this.goliath);
            }
         }
      }
   }

   private void report(Set<String> found, ClientPacketListener handler) {
      this.rtpAt = 0L;
      int teams = 0;
      int mediaTeams = 0;
      int mediaMembers = 0;

      for (PlayerTeam team : this.mc.level.getScoreboard().getPlayerTeams()) {
         teams++;
         if (isMedia(team)) {
            mediaTeams++;
            mediaMembers += team.getPlayers().size();
         }
      }

      SpecterClientAddon.LOG
         .info(
            "Media Base Finder: {}: {} media {} ({} teams, {} media teams with {} members, {} placed by game mode, after rtp {})",
            new Object[]{this.goliath, found.size(), found, teams, mediaTeams, mediaMembers, this.proven.size(), this.afterRtp}
         );
      if (found.isEmpty()) {
         if (this.chat.get() && this.reportEmpty.get()) {
            this.info("%s", new Object[]{"No media on " + this.goliath + "."});
         }
      } else {
         this.announce(new ArrayList<>(found), found.size(), true);
      }
   }

   private void announce(List<String> names, int total, boolean summary) {
      String list = names(names);
      String area = this.area();
      String bases = total + (total == 1 ? " media base" : " media bases") + (area == null ? "" : " within " + area + " blocks");
      if (summary) {
         if (this.chat.get()) {
            this.info("%s", new Object[]{bases + " on " + this.goliath + ": " + list});
         }
      } else {
         if (this.chat.get()) {
            this.info("%s", new Object[]{list + " joined " + this.goliath + ", now " + bases + "."});
         }

         SpecterClientAddon.LOG.info("Media Base Finder: {} joined {}, now {}", new Object[]{list, this.goliath, total});
      }

      if (summary && this.afterRtp && this.bigText.get()) {
         this.bigTitle = total + (total == 1 ? " Media Base" : " Media Bases");
         this.bigBelow = area == null ? "on this goliath" : "within " + area + " blocks";
         this.bigStart = 0L;
         if (this.sound.get()) {
            this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4F, 1.0F));
         }
      }
   }

   private String area() {
      DonutGoliaths.Goliath at = this.goliathHere();
      if (at == null) {
         return null;
      } else {
         String w = blocks(at.width());
         String l = blocks(at.length());
         return w.equals(l) ? w : w + " x " + l;
      }
   }

   private static String blocks(int value) {
      return value % 1000 == 0 ? value / 1000 + "k" : String.format(Locale.ROOT, "%.1fk", value / 1000.0);
   }

   private static String names(List<String> names) {
      StringBuilder out = new StringBuilder();
      int shown = Math.min(names.size(), 8);

      for (int i = 0; i < shown; i++) {
         out.append(i == 0 ? "" : ", ").append(names.get(i));
      }

      if (names.size() > shown) {
         out.append(" +").append(names.size() - shown);
      }

      return out.toString();
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         this.drawBigText(event.graphics);
         if (this.card.get() && !this.mc.gui.hud.isHidden() && !this.mc.debugEntries.isOverlayVisible()) {
            if (!this.hideWhenClear.get() || this.settle <= 0 && !this.here.isEmpty()) {
               this.drawCard(event.graphics);
            }
         }
      }
   }

   private void drawCard(GuiGraphicsExtractor ctx) {
      List<String> rows = new ArrayList<>(this.here);
      int more = Math.max(0, rows.size() - 8);
      if (more > 0) {
         rows = rows.subList(0, 8);
      }

      boolean checking = this.settle > 0;
      int lineCount = !checking && !rows.isEmpty() ? rows.size() + (more > 0 ? 1 : 0) : 1;
      int boxWidth = this.width.get();
      int innerWidth = boxWidth - 14;
      int boxHeight = 15 + lineCount * 12 - 4 + 14;
      float sc = this.scale.get().floatValue();
      this.spot.place(boxWidth * sc, boxHeight * sc);
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(Math.round(this.spot.x()), Math.round(this.spot.y()));
      matrices.scale(sc, sc);
      PurpleCard.card(ctx, 0, 0, boxWidth, boxHeight);
      matrices.translate(7.0F, 7.0F);
      String title = "Media Bases";
      CardText.draw(ctx, title, 0.0F, 0.0F, -1065473, true);
      String place = CardText.fit(this.goliath, innerWidth - CardText.width(title) - 8);
      CardText.draw(ctx, place, (float)(innerWidth - CardText.width(place)), 0.0F, -4149795, true);
      ctx.fill(0, 11, innerWidth, 12, 1440926975);
      int rowY = 15;
      if (checking) {
         CardText.draw(ctx, "Checking...", 0.0F, (float)rowY, -19648, true);
      } else if (rows.isEmpty()) {
         CardText.draw(ctx, "None on this goliath", 0.0F, (float)rowY, -4149795, true);
      } else {
         ClientPacketListener handler = this.mc.getConnection();

         for (String name : rows) {
            PlayerInfo entry = handler == null ? null : handler.getPlayerInfo(name);
            PlayerSkin skin = entry != null ? entry.getSkin() : DefaultPlayerSkin.getDefaultSkin();
            PlayerFaceExtractor.extractRenderState(ctx, skin, 0, rowY, 8, -1);
            CardText.draw(ctx, CardText.fit(name, innerWidth - 8 - 4), 12.0F, (float)rowY, -1, true);
            rowY += 12;
         }

         if (more > 0) {
            CardText.draw(ctx, "+" + more + " more", 0.0F, (float)rowY, -4149795, true);
         }
      }

      matrices.popMatrix();
   }

   private void drawBigText(GuiGraphicsExtractor ctx) {
      if (this.bigTitle != null) {
         long now = System.currentTimeMillis();
         if (this.bigStart == 0L) {
            if (this.mc.gui.screen() != null) {
               return;
            }

            this.bigStart = now;
         }

         long t = now - this.bigStart;
         long total = 4200L;
         if (t >= total) {
            this.bigTitle = null;
         } else {
            float fade = t < 400L ? (float)t / 400.0F : Math.min(1.0F, (float)(total - t) / 800.0F);
            int alpha = Math.round(fade * 255.0F);
            if (alpha > 8) {
               int color = alpha << 24 | 16733695;
               float centerX = ctx.guiWidth() / 2.0F;
               float centerY = ctx.guiHeight() / 2.0F;
               Matrix3x2fStack m = ctx.pose();
               m.pushMatrix();
               m.translate(centerX, centerY - 44.0F);
               m.scale(3.0F, 3.0F);
               CardText.draw(ctx, this.bigTitle, -CardText.width(this.bigTitle) / 2.0F, 0.0F, color, true);
               m.popMatrix();
               m.pushMatrix();
               m.translate(centerX, centerY - 8.0F);
               m.scale(1.5F, 1.5F);
               CardText.draw(ctx, this.bigBelow, -CardText.width(this.bigBelow) / 2.0F, 0.0F, color, true);
               m.popMatrix();
            }
         }
      }
   }
}

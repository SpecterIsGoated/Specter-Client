package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.ReceiveMessageEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
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
import com.jfant.specter.staff.GoliathStaff;
import com.jfant.specter.staff.LeaveMessages;
import com.jfant.specter.staff.StaffTeams;
import com.jfant.specter.staff.UnseenActivity;
import com.jfant.specter.staff.WatchingAlert;
import com.jfant.specter.webhook.Webhook;
import com.jfant.specter.webhook.WebhookSettings;
import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayDeque;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import org.joml.Matrix3x2fStack;

public class AntiVanish extends Module {
   private static final int SCAN_TICKS = 10;
   private static final long QUIET_MS = 4000L;
   private static final long HIDDEN_GRACE_MS = 3000L;
   private static final long UNLISTED_MS = 5000L;
   private static final long JUDGE_MS = 2000L;
   private static final long PROBE_MS = 20000L;
   private static final long NAME_GAP_MS = 1500L;
   private static final long NAME_ROUND_MS = 30000L;
   private static final long NAME_KEEP_MS = 75000L;
   private static final long QUIET_LEAVE_KEEP_MS = 60000L;
   private static final long BODY_RECENT_MS = 60000L;
   private static final long EVIDENCE_MS = 15000L;
   private static final long VERDICT_COOLDOWN_MS = 12000L;
   private static final long REARM_MS = 5000L;
   private static final long UNSEEN_SHOW_MS = 20000L;
   private static final int UNSEEN_ROWS = 3;
   private static final int MAX_SUSPECTS = 8;
   private static final long GOLIATH_ALERT_GAP_MS = 10000L;
   private static final long GOLIATH_KEEP_MS = 120000L;
   private static final int GOLIATH_ROWS = 5;
   private static final int WARN = -22016;
   private static final int ALARM = -43691;
   private static final int PAD = 7;
   private static final int DIVIDER_Y = 11;
   private static final int HEADER = 15;
   private static final int LINE = 12;
   private static final int FACE = 8;
   private static final int WIDTH = 190;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAlerts = this.settings.createGroup("Alerts");
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");
   private final Setting<Boolean> developers = this.sgGeneral
      .add(new BoolSetting.Builder().name("developers").description("Watch the owner and developers.").defaultValue(true).build());
   private final Setting<Boolean> staffShown = this.sgGeneral
      .add(new BoolSetting.Builder().name("staff").description("Watch admins, mods and helpers, senior ones included.").defaultValue(true).build());
   private final Setting<Integer> sensorRange = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sensor-range")
            .description("How far away (in blocks) invisible staff, and chests, doors and hits with nobody in sight, count.")
            .defaultValue(64)
            .min(8)
            .sliderRange(8, 128)
            .build()
      );
   private final Setting<Boolean> unseenActivity = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("unseen-activity")
            .description("Notice a chest, door, gate, button or lever used, or something hit, near you with no player in sight to do it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> targetableCheck = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("targetable-check")
            .description(
               "While staff are off the tab, every 20 seconds ask the server to complete a player name, as pressing Tab in chat does, and flag staff it still offers."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> knownStaffCheck = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("known-staff-check")
            .description(
               "Ask the server to complete /msg for each known staff name it does not show you, one every 1.5 seconds, as typing it would. One it offers is online but hidden - catches staff who join already vanished."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> goliathWatch = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("your-goliath")
            .description(
               "Tell you when staff come onto or leave YOUR goliath (not just the network), from the server's team changes and tab refreshes, and list them on the card."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> bigAlert = this.sgAlerts
      .add(
         new BoolSetting.Builder()
            .name("big-alert")
            .description("When staff are watching you, show their head and \"Name Is Watching You\" in the middle of the screen, with two loud sounds.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> goliathBigAlert = this.sgAlerts
      .add(
         new BoolSetting.Builder()
            .name("goliath-big-alert")
            .description("Also show the big \"Is Watching You\" alert the moment staff come onto your goliath (at most every 10 seconds per person).")
            .defaultValue(true)
            .visible(this.goliathWatch::get)
            .build()
      );
   private final Setting<Boolean> sound = this.sgAlerts
      .add(
         new BoolSetting.Builder()
            .name("sound")
            .description(
               "Play a sound when staff vanish, switch to spectator, turn up invisible or something happens near you with nobody in sight. Only you hear it."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> chatMessage = this.sgAlerts
      .add(new BoolSetting.Builder().name("chat-message").description("Also say it in chat. Only you see it.").defaultValue(true).build());
   private final WebhookSettings webhook = new WebhookSettings(this.sgWebhook, "Anti Vanish");
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.TopLeft, 4, 120);
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
   private final Setting<Boolean> hideWhenClear = this.sgPosition
      .add(
         new BoolSetting.Builder()
            .name("hide-when-clear")
            .description("Hide the card while nobody is hidden and nothing has happened near you.")
            .defaultValue(true)
            .build()
      );
   private final Map<String, AntiVanish.Suspect> suspects = new LinkedHashMap<>();
   private final Map<String, StaffTeams.Member> found = new LinkedHashMap<>();
   private final Map<String, AbstractClientPlayer> loaded = new HashMap<>();
   private final List<AntiVanish.Suspect> shownSuspects = new ArrayList<>();
   private final List<AntiVanish.Suspect> watchers = new ArrayList<>();
   private final LeaveMessages leaves = new LeaveMessages();
   private final UnseenActivity unseen = new UnseenActivity();
   private final List<AntiVanish.Unseen> unseenRows = new ArrayList<>();
   private final List<AntiVanish.Unseen> unseenShown = new ArrayList<>();
   private long lastUnseen;
   private final ConcurrentLinkedQueue<UUID> unlisted = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<AntiVanish.EntitySound> entitySounds = new ConcurrentLinkedQueue<>();
   private final Set<Integer> probeIds = ConcurrentHashMap.newKeySet();
   private volatile List<String> probeNames;
   private int nextProbeId = 52000;
   private long lastProbe;
   private final ArrayDeque<String> nameQueue = new ArrayDeque<>();
   private final Map<Integer, String> nameProbes = new ConcurrentHashMap<>();
   private final ConcurrentLinkedQueue<AntiVanish.NameAnswer> nameAnswers = new ConcurrentLinkedQueue<>();
   private long lastNameProbe;
   private long nameRoundAt;
   private final WatchingAlert overlay = new WatchingAlert();
   private final GoliathStaff goliath = new GoliathStaff();
   private final Map<String, Long> goliathAlertAt = new HashMap<>();
   private final List<GoliathStaff.Here> goliathShown = new ArrayList<>();
   private ClientLevel lastWorld;
   private long quietUntil;
   private int ticks;

   public AntiVanish() {
      super(
         SpecterClientAddon.HUD,
         "anti-vanish",
         "Catches staff who vanish, go spectator or turn invisible, and shows \"Name Is Watching You\" when one of them is watching you."
      );
   }

   @Override
   public void onActivate() {
      this.leaves.reset();
      this.reset();
   }

   @Override
   public void onDeactivate() {
      this.suspects.clear();
      this.unseenRows.clear();
      this.unseen.reset();
      this.unlisted.clear();
      this.entitySounds.clear();
      this.probeIds.clear();
      this.probeNames = null;
      this.nameQueue.clear();
      this.nameProbes.clear();
      this.nameAnswers.clear();
      this.nameRoundAt = 0L;
      this.goliath.reset();
      this.goliathAlertAt.clear();
      this.overlay.stop();
   }

   @Override
   public String getInfoString() {
      int hidden = 0;

      for (AntiVanish.Suspect s : this.suspects.values()) {
         if (s.suspect(System.currentTimeMillis())) {
            hidden++;
         }
      }

      return hidden == 0 ? null : Integer.toString(hidden);
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.leaves.reset();
      this.reset();
   }

   private void reset() {
      this.suspects.clear();
      this.unseenRows.clear();
      this.unseen.reset();
      this.unlisted.clear();
      this.entitySounds.clear();
      this.probeIds.clear();
      this.probeNames = null;
      this.nameQueue.clear();
      this.nameProbes.clear();
      this.nameAnswers.clear();
      this.nameRoundAt = 0L;
      this.goliath.reset();
      this.goliathAlertAt.clear();
      this.lastUnseen = 0L;
      this.lastWorld = this.mc.level;
      this.quietUntil = System.currentTimeMillis() + 4000L;
      this.ticks = 0;
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      this.leaves.onMessage(event.getMessage(), System.currentTimeMillis());
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundCommandSuggestionsPacket named && this.nameProbes.containsKey(named.id())) {
         String asked = this.nameProbes.remove(named.id());
         String offered = null;

         for (Suggestion suggestion : named.toSuggestions().getList()) {
            if (suggestion.getText().trim().equalsIgnoreCase(asked)) {
               offered = suggestion.getText().trim();
            }
         }

         this.nameAnswers.offer(new AntiVanish.NameAnswer(asked, offered));
         event.cancel();
      } else if (event.packet instanceof ClientboundCommandSuggestionsPacket answer && this.probeIds.remove(answer.id())) {
         List<String> names = new ArrayList<>();

         for (Suggestion suggestionx : answer.toSuggestions().getList()) {
            if (!suggestionx.getText().isBlank()) {
               names.add(suggestionx.getText().trim());
            }
         }

         this.probeNames = names;
         event.cancel();
      } else {
         if (this.goliathWatch.get()) {
            this.goliath.onPacket(event.packet);
         }

         this.read(event.packet);
      }
   }

   private void read(Packet<?> packet) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> inner : bundle.subPackets()) {
            this.read(inner);
         }
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket list) {
         if (!list.actions().contains(Action.UPDATE_LISTED)) {
            return;
         }

         for (Entry entry : list.entries()) {
            if (!entry.listed() && entry.profileId() != null) {
               this.unlisted.offer(entry.profileId());
            }
         }
      } else if (packet instanceof ClientboundSoundEntityPacket sound) {
         String path = sound.getSound().value().location().getPath().toLowerCase(Locale.ROOT);
         if (UnseenActivity.suspiciousSound(path) && this.entitySounds.size() < 256) {
            this.entitySounds.offer(new AntiVanish.EntitySound(sound.getId(), path));
         }
      } else {
         if (!this.unseenActivity.get()) {
            return;
         }

         if (packet instanceof ClientboundSoundPacket soundx) {
            this.unseen.sound(soundx);
         } else if (packet instanceof ClientboundLevelParticlesPacket particle) {
            this.unseen.particle(particle);
         } else if (packet instanceof ClientboundExplodePacket blast) {
            this.unseen.explosion(blast);
         }
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (this.unseenActivity.get()) {
         this.unseen.sent(event.packet);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.getConnection() != null) {
         long now = System.currentTimeMillis();
         if (this.mc.level != this.lastWorld) {
            this.reset();
         }

         boolean quiet = now < this.quietUntil;
         this.drainUnlisted(now);
         if (this.ticks++ % 10 == 0) {
            this.scan(now, quiet);
         }

         if (this.goliathWatch.get()) {
            this.pollGoliath(now, quiet);
         }

         this.trackBodies(now, quiet);
         this.drainEntitySounds(now, quiet);
         if (this.unseenActivity.get()) {
            this.pollUnseen(now, quiet);
         } else {
            this.unseenRows.clear();
         }

         if (this.targetableCheck.get()) {
            this.probe(now, quiet);
         }

         if (this.knownStaffCheck.get()) {
            this.probeKnownStaff(now, quiet);
         } else {
            this.nameQueue.clear();
         }

         this.judgeWatching(now, quiet);
      }
   }

   private void drainUnlisted(long now) {
      ClientPacketListener handler = this.mc.getConnection();

      UUID id;
      while ((id = this.unlisted.poll()) != null) {
         PlayerInfo entry = handler.getPlayerInfo(id);
         if (entry != null) {
            AntiVanish.Suspect s = this.suspects.get(entry.getProfile().name().toLowerCase(Locale.ROOT));
            if (s != null) {
               s.unlistedAt = now;
            }
         }
      }
   }

   private void scan(long now, boolean quiet) {
      ClientPacketListener handler = this.mc.getConnection();
      StaffTeams.scan(this.mc.level, handler, this.mc.player.getGameProfile().name(), this.found);

      for (StaffTeams.Member m : this.found.values()) {
         if (this.watched(m.order())) {
            String key = m.name().toLowerCase(Locale.ROOT);
            AntiVanish.Suspect s = this.suspects.computeIfAbsent(key, AntiVanish.Suspect::new);
            boolean arrived = s.name == null || s.leftAt != 0L;
            s.name = m.name();
            s.rank = m.rank();
            PlayerInfo any = m.entry() != null ? m.entry() : handler.getPlayerInfo(m.name());
            GameType before = s.mode;
            if (any != null) {
               s.skin = any.getSkin();
               s.id = any.getProfile().id();
               s.mode = any.getGameMode();
            }

            if (arrived) {
               s.leftAt = 0L;
               s.judged = false;
               s.clearHidden();
               before = null;
            }

            if (!quiet && before != null && s.mode != null && before != s.mode) {
               this.modeChanged(s, before);
            }

            if (m.entry() != null) {
               if (s.hiddenSince != 0L && s.how != null && s.how != AntiVanish.Hide.QUIET_LEAVE) {
                  this.visibleAgain(s, quiet);
               }

               s.offTabSince = 0L;
            } else {
               if (s.offTabSince == 0L) {
                  s.offTabSince = now;
               }

               boolean unlistedNow = s.unlistedAt != 0L && now - s.unlistedAt < 5000L;
               if (now - s.offTabSince >= 3000L || unlistedNow) {
                  this.hide(s, this.bodyOf(key) != null ? AntiVanish.Hide.NEARBY : AntiVanish.Hide.OFF_TAB, now, quiet);
               }
            }
         }
      }

      Iterator<AntiVanish.Suspect> it = this.suspects.values().iterator();

      while (it.hasNext()) {
         AntiVanish.Suspect sx = it.next();
         if (this.found.containsKey(sx.key) && this.watched(this.found.get(sx.key).order())) {
            sx.probeOnly = false;
         } else if (sx.probeOnly) {
            if (now - sx.offeredAt > 75000L || quiet) {
               it.remove();
            }
         } else if (quiet || sx.name == null) {
            it.remove();
         } else if (sx.leftAt == 0L) {
            sx.leftAt = now;
            sx.judged = false;
         } else if (!sx.judged && now - sx.leftAt >= 2000L) {
            sx.judged = true;
            if (this.leaves.serverPrints() && !this.leaves.seenFor(sx.name, now)) {
               this.hide(sx, AntiVanish.Hide.QUIET_LEAVE, now, false);
            }
         } else if (sx.judged && (sx.how != AntiVanish.Hide.QUIET_LEAVE || now - sx.leftAt > 60000L)) {
            it.remove();
         }
      }
   }

   private void hide(AntiVanish.Suspect s, AntiVanish.Hide how, long now, boolean quiet) {
      if (s.how == null || s.how.ordinal() < how.ordinal()) {
         boolean first = s.how == null;
         s.how = how;
         if (s.hiddenSince == 0L) {
            s.hiddenSince = now;
         }

         if (!quiet) {
            String who = s.name + " (" + s.rank.single + ")";

            String line = switch (how) {
               case OFF_TAB -> who + " left the tab but is still on the server - vanished.";
               case QUIET_LEAVE -> who + " went off the server with no leave message - probably vanished.";
               case TARGETABLE -> who + " is off the tab but the server still offers their name - vanished.";
               case NEARBY -> who + " is off the tab but still loaded " + Math.round(s.distance) + " blocks from you.";
            };
            this.notify(
               AntiVanish.Alert.VANISH, (first ? "" : "Now: ") + line, SoundEvents.NOTE_BLOCK_BELL.value(), how == AntiVanish.Hide.OFF_TAB ? 0.8F : 1.1F
            );
         }
      }
   }

   private void visibleAgain(AntiVanish.Suspect s, boolean quiet) {
      s.clearHidden();
      if (!quiet && this.chatMessage.get()) {
         this.info(s.name + " is back on the tab.", new Object[0]);
      }
   }

   private void modeChanged(AntiVanish.Suspect s, GameType before) {
      if (s.mode == GameType.SPECTATOR) {
         this.notify(
            AntiVanish.Alert.SPECTATOR,
            s.name + " (" + s.rank.single + ") switched to spectator - they can watch without being seen.",
            SoundEvents.NOTE_BLOCK_BELL.value(),
            1.2F
         );
      } else if (this.chatMessage.get()) {
         this.info(s.name + " switched from " + modeName(before) + " to " + modeName(s.mode) + ".", new Object[0]);
      }
   }

   private static String modeName(GameType mode) {
      return mode == null ? "unknown" : mode.getLongDisplayName().getString().toLowerCase(Locale.ROOT);
   }

   private void pollGoliath(long now, boolean quiet) {
      String self = this.mc.player.getGameProfile().name();
      List<GoliathStaff.Change> changes = this.goliath.poll(now, this.mc.getConnection(), self, this::staffOrder, this::watched);
      if (!quiet) {
         for (GoliathStaff.Change c : changes) {
            String key = c.name().toLowerCase(Locale.ROOT);
            AntiVanish.Suspect s = this.suspects.get(key);
            String who = c.name() + (s != null && s.rank != null ? " (" + s.rank.single + ")" : "");
            if (c.arrived()) {
               String line = who + " " + c.how().text + (c.onTab() ? "." : " - and is off the tab.");
               this.notify(AntiVanish.Alert.GOLIATH, line, SoundEvents.NOTE_BLOCK_BELL.value(), 1.4F);
               Long last = this.goliathAlertAt.get(key);
               if (this.goliathBigAlert.get() && (last == null || now - last >= 10000L)) {
                  this.goliathAlertAt.put(key, now);
                  int others = 0;

                  for (GoliathStaff.Here h : this.goliath.list(now, 0L)) {
                     if (h.here() && !h.name.equalsIgnoreCase(c.name())) {
                        others++;
                     }
                  }

                  this.overlay.play(c.name(), this.skinOf(key, s), others, true);
               }
            } else {
               String line = who + (c.loggedOff() ? " logged off." : " left your goliath.");
               this.notify(AntiVanish.Alert.GOLIATH_LEFT, line, SoundEvents.NOTE_BLOCK_BELL.value(), 0.75F);
            }
         }
      }
   }

   private int staffOrder(String key) {
      StaffTeams.Member m = this.found.get(key);
      if (m != null) {
         return m.order();
      } else {
         Integer known = StaffTeams.knownOrder(key);
         return known != null ? known : -1;
      }
   }

   private PlayerSkin skinOf(String key, AntiVanish.Suspect s) {
      if (s != null && s.skin != null) {
         return s.skin;
      } else {
         StaffTeams.Member m = this.found.get(key);
         if (m != null && m.entry() != null) {
            return m.entry().getSkin();
         } else {
            PlayerInfo entry = this.mc.getConnection().getPlayerInfo(key);
            return entry != null ? entry.getSkin() : DefaultPlayerSkin.getDefaultSkin();
         }
      }
   }

   private boolean watched(int order) {
      return order <= 2 ? this.developers.get() : this.staffShown.get();
   }

   private void trackBodies(long now, boolean quiet) {
      this.loaded.clear();

      for (AbstractClientPlayer e : this.mc.level.players()) {
         if (e != this.mc.player && !e.isRemoved()) {
            this.loaded.put(e.getGameProfile().name().toLowerCase(Locale.ROOT), e);
         }
      }

      double range = this.sensorRange.get().intValue();

      for (AntiVanish.Suspect s : this.suspects.values()) {
         AbstractClientPlayer ex = s.leftAt == 0L ? this.loaded.get(s.key) : null;
         s.bodyNow = ex != null;
         if (ex == null) {
            s.invisible = false;
         } else {
            s.bodyAt = now;
            s.distance = this.mc.player.distanceTo(ex);
            s.id = ex.getUUID();
            s.skin = ex.getSkin();
            if (s.how == AntiVanish.Hide.OFF_TAB) {
               this.hide(s, AntiVanish.Hide.NEARBY, now, quiet);
            }

            boolean invisible = ex.isInvisible() && s.distance <= range;
            if (invisible && !s.invisible && !quiet) {
               this.notify(
                  AntiVanish.Alert.INVISIBLE,
                  s.name + " (" + s.rank.single + ") is invisible " + Math.round(s.distance) + " blocks from you.",
                  SoundEvents.NOTE_BLOCK_BELL.value(),
                  1.2F
               );
            }

            s.invisible = invisible;
         }
      }
   }

   private Player bodyOf(String key) {
      for (Player e : this.mc.level.players()) {
         if (e != this.mc.player && !e.isRemoved() && e.getGameProfile().name().toLowerCase(Locale.ROOT).equals(key)) {
            return e;
         }
      }

      return null;
   }

   private void drainEntitySounds(long now, boolean quiet) {
      double rangeSq = (double)this.sensorRange.get().intValue() * this.sensorRange.get().intValue();

      AntiVanish.EntitySound heard;
      while ((heard = this.entitySounds.poll()) != null) {
         if (this.mc.level.getEntity(heard.entityId()) instanceof Player player
            && player != this.mc.player
            && player.isInvisible()
            && !(player.distanceToSqr(this.mc.player) > rangeSq)) {
            AntiVanish.Suspect s = this.suspects.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
            if (s != null) {
               boolean fresh = now - s.invisibleSoundAt > 15000L;
               s.invisibleSoundAt = now;
               if (fresh && !quiet) {
                  this.notify(
                     AntiVanish.Alert.INVISIBLE,
                     "A sound came from " + s.name + ", who is invisible " + Math.round(player.distanceTo(this.mc.player)) + " blocks from you.",
                     SoundEvents.NOTE_BLOCK_BELL.value(),
                     1.2F
                  );
               }
            }
         }
      }
   }

   private void pollUnseen(long now, boolean quiet) {
      List<UnseenActivity.Signal> signals = this.unseen.poll(now, this.sensorRange.get().intValue());
      this.unseenRows.removeIf(row -> now - row.at() > 20000L);
      if (!quiet) {
         for (UnseenActivity.Signal signal : signals) {
            this.lastUnseen = now;
            this.unseenRows.add(new AntiVanish.Unseen(signal.what(), signal.where(), now));

            while (this.unseenRows.size() > 3) {
               this.unseenRows.remove(0);
            }

            this.notify(
               AntiVanish.Alert.UNSEEN, signal.what() + ", " + signal.where() + " away, with nobody in sight.", SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F
            );
         }
      }
   }

   private void probe(long now, boolean quiet) {
      List<String> names = this.probeNames;
      if (names != null) {
         this.probeNames = null;
         Set<String> offered = new HashSet<>();

         for (String name : names) {
            offered.add(name.toLowerCase(Locale.ROOT));
         }

         for (AntiVanish.Suspect s : this.suspects.values()) {
            if (s.leftAt == 0L && s.offTabSince != 0L && now - s.offTabSince >= 3000L && offered.contains(s.key)) {
               this.hide(s, AntiVanish.Hide.TARGETABLE, now, quiet);
            }
         }
      }

      if (!quiet && now - this.lastProbe >= 20000L) {
         boolean anyOffTab = false;

         for (AntiVanish.Suspect sx : this.suspects.values()) {
            anyOffTab |= sx.leftAt == 0L && sx.offTabSince != 0L;
         }

         if (anyOffTab) {
            this.lastProbe = now;
            int id = this.nextProbeId++;
            if (this.nextProbeId > 59999) {
               this.nextProbeId = 52000;
            }

            this.probeIds.add(id);

            while (this.probeIds.size() > 4) {
               this.probeIds.remove(this.probeIds.iterator().next());
            }

            this.mc.getConnection().send(new ServerboundCommandSuggestionPacket(id, "minecraft:msg "));
         }
      }
   }

   private void probeKnownStaff(long now, boolean quiet) {
      AntiVanish.NameAnswer answer;
      while ((answer = this.nameAnswers.poll()) != null) {
         if (answer.offered() != null) {
            String key = answer.asked().toLowerCase(Locale.ROOT);
            Integer knownOrder = StaffTeams.knownOrder(key);
            int order = knownOrder != null ? knownOrder : 8;
            if (this.watched(order) && (!this.found.containsKey(key) || !this.watched(this.found.get(key).order()))) {
               AntiVanish.Suspect s = this.suspects.get(key);
               if (s == null) {
                  s = new AntiVanish.Suspect(key);
                  s.name = answer.offered();
                  s.rank = StaffTeams.knownRank(key);
                  s.probeOnly = true;
                  this.suspects.put(key, s);
               }

               s.offeredAt = now;
               if (s.probeOnly) {
                  this.hide(s, AntiVanish.Hide.TARGETABLE, now, quiet);
               }
            }
         }
      }

      if (!quiet) {
         String self = this.mc.player.getGameProfile().name().toLowerCase(Locale.ROOT);
         if (this.nameQueue.isEmpty() && now - this.nameRoundAt >= 30000L) {
            this.nameRoundAt = now;
            this.nameProbes.clear();

            for (String key : StaffTeams.knownNames()) {
               if (!key.equals(self) && !this.found.containsKey(key) && this.watched(StaffTeams.knownOrder(key))) {
                  this.nameQueue.add(key);
               }
            }
         }

         if (!this.nameQueue.isEmpty() && now - this.lastNameProbe >= 1500L) {
            this.lastNameProbe = now;
            String name = this.nameQueue.poll();
            int id = this.nextProbeId++;
            if (this.nextProbeId > 59999) {
               this.nextProbeId = 52000;
            }

            this.nameProbes.put(id, name);
            this.mc.getConnection().send(new ServerboundCommandSuggestionPacket(id, "/msg " + name));
         }
      }
   }

   private void judgeWatching(long now, boolean quiet) {
      this.watchers.clear();

      for (AntiVanish.Suspect s : this.suspects.values()) {
         boolean watching = this.watching(s, now);
         if (watching) {
            s.watchingAt = now;
            if (s.armed && now - s.verdictAt >= 12000L) {
               this.watchers.add(s);
            }
         } else if (!s.armed && now - s.watchingAt >= 5000L) {
            s.armed = true;
         }
      }

      if (!this.watchers.isEmpty() && !quiet) {
         this.watchers.sort(Comparator.comparingDouble(sx -> sx.bodyNow ? sx.distance : 1.7976931348623157E308));

         for (AntiVanish.Suspect sx : this.watchers) {
            sx.armed = false;
            sx.verdictAt = now;
         }

         AntiVanish.Suspect first = this.watchers.get(0);
         String why = this.reason(first, now);
         String message = first.name + " (" + first.rank.single + ") is watching you - " + why + ".";
         if (this.watchers.size() > 1) {
            message = message + " " + (this.watchers.size() - 1) + " more too.";
         }

         if (this.bigAlert.get()) {
            this.overlay.play(first.name, first.skin, this.watchers.size() - 1, true);
         } else if (this.sound.get()) {
            this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F, 1.0F));
         }

         if (this.chatMessage.get()) {
            this.info("%s", new Object[]{message});
         }

         if (this.webhook.enabled()) {
            this.webhook.send(new Webhook.Embed(AntiVanish.Alert.WATCHING.title, message, AntiVanish.Alert.WATCHING.color).context());
         }
      }
   }

   private boolean watching(AntiVanish.Suspect s, long now) {
      boolean spectator = s.leftAt == 0L && s.mode == GameType.SPECTATOR;
      boolean strong = s.how == AntiVanish.Hide.NEARBY && s.bodyNow || s.invisible || now - s.invisibleSoundAt < 15000L || spectator && s.bodyNow;
      if (strong) {
         return true;
      } else {
         boolean hidden = s.how != null || spectator;
         return hidden && now - s.bodyAt < 60000L && this.lastUnseen != 0L && now - this.lastUnseen < 15000L;
      }
   }

   private String reason(AntiVanish.Suspect s, long now) {
      if (s.invisible) {
         return "invisible " + Math.round(s.distance) + " blocks away";
      } else if (now - s.invisibleSoundAt < 15000L) {
         return "a sound came from their invisible body";
      } else if (s.how == AntiVanish.Hide.NEARBY && s.bodyNow) {
         return "off the tab but loaded " + Math.round(s.distance) + " blocks away";
      } else {
         return s.mode == GameType.SPECTATOR && s.bodyNow
            ? "in spectator " + Math.round(s.distance) + " blocks away"
            : "hidden, seen near you lately, and something happened near you with nobody in sight";
      }
   }

   public void previewAlert(String name, PlayerSkin skin) {
      this.overlay.play(name, skin, 0, true);
   }

   private void notify(AntiVanish.Alert kind, String message, SoundEvent event, float pitch) {
      if (this.sound.get()) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, 1.0F));
      }

      if (this.chatMessage.get()) {
         this.info("%s", new Object[]{message});
      }

      if (this.webhook.enabled()) {
         this.webhook.send(new Webhook.Embed(kind.title, message, kind.color).context());
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (!this.mc.options.hideGui) {
            GuiGraphics ctx = event.drawContext;
            this.drawCard(ctx);
            this.overlay.render(ctx);
         }
      }
   }

   private void drawCard(GuiGraphics ctx) {
      if (!this.mc.debugEntries.isOverlayVisible()) {
         long now = System.currentTimeMillis();
         this.shownSuspects.clear();

         for (AntiVanish.Suspect s : this.suspects.values()) {
            if (s.suspect(now)) {
               this.shownSuspects.add(s);
            }
         }

         this.shownSuspects
            .sort(
               Comparator.<AntiVanish.Suspect>comparingInt(sx -> now - sx.watchingAt < 5000L ? 0 : 1)
                  .thenComparingInt(sx -> sx.rank.ordinal())
                  .thenComparing(sx -> sx.key)
            );
         int more = Math.max(0, this.shownSuspects.size() - 8);

         while (this.shownSuspects.size() > 8) {
            this.shownSuspects.remove(this.shownSuspects.size() - 1);
         }

         this.unseenShown.clear();

         for (AntiVanish.Unseen row : this.unseenRows) {
            if (now - row.at() <= 20000L) {
               this.unseenShown.add(row);
            }
         }

         this.goliathShown.clear();
         if (this.goliathWatch.get()) {
            this.goliathShown.addAll(this.goliath.list(now, 120000L));
         }

         while (this.goliathShown.size() > 5) {
            this.goliathShown.remove(this.goliathShown.size() - 1);
         }

         if (!this.shownSuspects.isEmpty() || !this.unseenShown.isEmpty() || !this.goliathShown.isEmpty() || !this.hideWhenClear.get()) {
            Font tr = this.mc.font;
            int suspectRows = Math.max(1, this.shownSuspects.size() + (more > 0 ? 1 : 0));
            int lineCount = suspectRows
               + (this.unseenShown.isEmpty() ? 0 : 1 + this.unseenShown.size())
               + (this.goliathShown.isEmpty() ? 0 : 1 + this.goliathShown.size());
            int innerWidth = 176;
            int boxHeight = 15 + lineCount * 12 - 4 + 14;
            float sc = this.scale.get().floatValue();
            this.spot.place(190.0F * sc, boxHeight * sc);
            Matrix3x2fStack matrices = ctx.pose();
            matrices.pushMatrix();
            matrices.translate(Math.round(this.spot.x()), Math.round(this.spot.y()));
            matrices.scale(sc, sc);
            PurpleCard.card(ctx, 0, 0, 190, boxHeight);
            matrices.translate(7.0F, 7.0F);
            boolean alarm = false;

            for (AntiVanish.Suspect sx : this.shownSuspects) {
               alarm |= now - sx.watchingAt < 5000L;
            }

            String count = alarm ? "Watched" : (this.shownSuspects.isEmpty() ? "Clear" : this.shownSuspects.size() + " hidden");
            int countColor = alarm ? pulse(-43691, now) : (this.shownSuspects.isEmpty() ? -4149795 : -22016);
            CardText.draw(ctx, "Anti Vanish", 0.0F, 0.0F, -1065473, true);
            CardText.draw(ctx, count, (float)(innerWidth - CardText.width(count)), 0.0F, countColor, true);
            ctx.fill(0, 11, innerWidth, 12, 1440926975);
            int rowY = 15;
            if (this.shownSuspects.isEmpty()) {
               CardText.draw(ctx, "No hidden staff", 0.0F, (float)rowY, -4149795, true);
            }

            for (AntiVanish.Suspect sx : this.shownSuspects) {
               this.drawSuspect(ctx, tr, sx, rowY, innerWidth, now);
               rowY += 12;
            }

            if (more > 0) {
               CardText.draw(ctx, "+" + more + " more", 0.0F, (float)rowY, -4149795, true);
            }

            if (!this.unseenShown.isEmpty()) {
               rowY = 15 + suspectRows * 12;
               CardText.draw(ctx, "Unseen activity", 0.0F, (float)rowY, -1911561, true);
               rowY += 12;

               for (AntiVanish.Unseen rowx : this.unseenShown) {
                  int alpha = (int)(255.0 * Math.min(1.0, (20000L - (now - rowx.at())) / 4000.0));
                  int color = Math.max(40, alpha) << 24 | 16755200;
                  String where = rowx.where();
                  int whereWidth = CardText.width(where) + 6;
                  CardText.draw(ctx, clip(tr, Component.literal(rowx.what()), innerWidth - whereWidth), 0.0F, (float)rowY, color, true);
                  CardText.draw(ctx, where, (float)(innerWidth - CardText.width(where)), (float)rowY, color, true);
                  rowY += 12;
               }
            }

            if (!this.goliathShown.isEmpty()) {
               rowY = 15 + (suspectRows + (this.unseenShown.isEmpty() ? 0 : 1 + this.unseenShown.size())) * 12;
               CardText.draw(ctx, "On your goliath", 0.0F, (float)rowY, -1911561, true);
               rowY += 12;

               for (GoliathStaff.Here h : this.goliathShown) {
                  String status = h.here() ? "here " + ago(now - h.since) : (h.loggedOff ? "logged off " : "left ") + ago(now - h.leftAt);
                  int color = h.here() ? pulse(-43691, now) : -4149795;
                  int statusWidth = CardText.width(status) + 6;
                  CardText.draw(ctx, clip(tr, Component.literal(h.name), innerWidth - statusWidth), 0.0F, (float)rowY, h.here() ? -1 : -4149795, true);
                  CardText.draw(ctx, status, (float)(innerWidth - CardText.width(status)), (float)rowY, color, true);
                  rowY += 12;
               }
            }

            matrices.popMatrix();
         }
      }
   }

   private void drawSuspect(GuiGraphics ctx, Font tr, AntiVanish.Suspect s, int y, int innerWidth, long now) {
      PlayerSkin skin = s.skin != null ? s.skin : DefaultPlayerSkin.getDefaultSkin();
      PlayerFaceRenderer.draw(ctx, skin, 0, y, 8, s.leftAt != 0L ? -2130706433 : -1);
      boolean watching = now - s.watchingAt < 5000L;
      String status = watching ? "Watching you" : status(s, now);
      int statusWidth = CardText.width(status) + 6;
      CardText.draw(ctx, clip(tr, Component.literal(s.name), innerWidth - 8 - 4 - statusWidth), 12.0F, (float)y, s.leftAt != 0L ? -4149795 : -1, true);
      CardText.draw(ctx, status, (float)(innerWidth - CardText.width(status)), (float)y, watching ? pulse(-43691, now) : pulse(-22016, now), true);
   }

   private static String status(AntiVanish.Suspect s, long now) {
      if (s.invisible) {
         return "Invisible " + Math.round(s.distance) + " blocks";
      } else if (s.how == AntiVanish.Hide.NEARBY && s.bodyNow) {
         return "Hidden " + Math.round(s.distance) + " blocks";
      } else if (s.how == AntiVanish.Hide.TARGETABLE) {
         return "Targetable";
      } else if (s.how == AntiVanish.Hide.QUIET_LEAVE) {
         return "Left quietly " + ago(now - s.leftAt);
      } else if (s.how != null) {
         return "Vanished?";
      } else if (s.mode == GameType.SPECTATOR) {
         return s.bodyNow ? "Spectator " + Math.round(s.distance) + " blocks" : "Spectator";
      } else {
         return "Invisible sound";
      }
   }

   private static int pulse(int color, long now) {
      double phase = now % 1600L / 1600.0 * 2.0 * 3.141592653589793;
      int alpha = (int)(170.0 + 85.0 * (0.5 + 0.5 * Math.sin(phase)));
      return alpha << 24 | color & 16777215;
   }

   private static FormattedCharSequence clip(Font tr, Component text, int maxWidth) {
      return CardText.clip(text, maxWidth);
   }

   private static String ago(long ms) {
      long seconds = ms / 1000L;
      return seconds < 60L ? seconds + "s" : seconds / 60L + "m";
   }

   private static enum Alert {
      VANISH("Staff vanished", 15105570),
      SPECTATOR("Staff in spectator", 15158332),
      INVISIBLE("Invisible staff nearby", 15158332),
      UNSEEN("Unseen activity nearby", 15105570),
      GOLIATH("Staff on your goliath", 15158332),
      GOLIATH_LEFT("Staff left your goliath", 15105570),
      WATCHING("Staff watching you", 15158332);

      final String title;
      final int color;

      private Alert(String title, int color) {
         this.title = title;
         this.color = color;
      }
   }

   private record EntitySound(int entityId, String path) {
   }

   private static enum Hide {
      OFF_TAB,
      QUIET_LEAVE,
      TARGETABLE,
      NEARBY;
   }

   private record NameAnswer(String asked, String offered) {
   }

   private static final class Suspect {
      final String key;
      String name;
      StaffTeams.Rank rank;
      UUID id;
      PlayerSkin skin;
      GameType mode;
      AntiVanish.Hide how;
      long hiddenSince;
      long offTabSince;
      long unlistedAt;
      long leftAt;
      boolean judged;
      boolean probeOnly;
      long offeredAt;
      boolean bodyNow;
      boolean invisible;
      long bodyAt;
      long invisibleSoundAt;
      double distance;
      long watchingAt = -4611686018427387904L;
      long verdictAt = -4611686018427387904L;
      boolean armed = true;

      Suspect(String key) {
         this.key = key;
      }

      void clearHidden() {
         this.how = null;
         this.hiddenSince = 0L;
         this.offTabSince = 0L;
         this.unlistedAt = 0L;
      }

      boolean suspect(long now) {
         if (this.how == AntiVanish.Hide.QUIET_LEAVE) {
            return now - this.leftAt <= 60000L;
         } else {
            return this.leftAt != 0L ? false : this.how != null || this.invisible || this.mode == GameType.SPECTATOR || now - this.invisibleSoundAt < 15000L;
         }
      }
   }

   private record Unseen(String what, String where, long at) {
   }
}

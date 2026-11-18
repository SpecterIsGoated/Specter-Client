package com.jfant.specter.bypass;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class AutoRtp {
   private static final double TELEPORT_DISTANCE_SQ = 256.0;
   private static final long REPLY_WINDOW_MS = 5000L;
   private static final long STOP_REPLY_WINDOW_MS = 2500L;
   private static final long RESUME_GRACE_MS = 1000L;
   private static final long RESPAWN_GRACE_MS = 3000L;
   private static final long OFFLINE_LIMIT_MS = 30000L;
   private static final long MENU_CLICK_DELAY_MS = 300L;
   private static final long MENU_FILL_MS = 2500L;
   private static final long MENU_CLAIM_MS = 4000L;
   private static final int MAX_MENU_CLICKS = 3;
   private static final int MENU_NAMES_SHOWN = 10;
   private static final long LONG_COOLDOWN_MS = 60000L;
   private static final long BAR_REFRESH_MS = 2000L;
   private static final long NONE = -9223372036854775808L;
   private final AutoRtp.Host host;
   private final AutoRtpLoop loop = new AutoRtpLoop();
   private Connection link;
   private ClientLevel level;
   private Vec3 anchor;
   private int startChunkX;
   private int startChunkZ;
   private boolean wasDead;
   private long offlineSince;
   private boolean lastTryLanded;
   private long commandSentAt = -9223372036854775808L;
   private boolean sendingCommand;
   private volatile long userActionAt = -9223372036854775808L;
   private int menuId = -1;
   private int ignoredMenuId = -1;
   private long menuSeenAt;
   private boolean menuClicked;
   private int menuClicks;
   private long lastMenuClickAt;
   private long noticedCooldownUntil;
   private String lastBar = "";
   private long lastBarAt;

   public AutoRtp(AutoRtp.Host host) {
      this.host = host;
   }

   public boolean running() {
      return this.loop.running();
   }

   public void start() {
      if (!this.loop.running()) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         ClientPacketListener connection = mc.getConnection();
         if (player != null && mc.level != null && connection != null && connection.getConnection().isConnected()) {
            long now = System.currentTimeMillis();
            AutoRtpLoop.Config config = this.config();
            this.loop.start(now, config);
            this.link = connection.getConnection();
            this.level = mc.level;
            this.anchor = position(player);
            this.startChunkX = player.getBlockX() >> 4;
            this.startChunkZ = player.getBlockZ() >> 4;
            this.wasDead = player.isDeadOrDying();
            this.offlineSince = 0L;
            this.lastTryLanded = false;
            this.noticedCooldownUntil = 0L;
            this.lastBar = "";
            this.resetMenu();
            StringBuilder line = new StringBuilder(
               "Auto RTP started: /"
                  + this.command()
                  + ", "
                  + seconds(config.scanMs())
                  + " scan per spot, at least "
                  + seconds(config.delayMs())
                  + " between RTPs"
            );
            if (config.maxRtps() > 0) {
               line.append(", max ").append(config.maxRtps());
            }

            long cooldown = this.loop.cooldownLeft(now);
            if (cooldown > 0L) {
               this.noticedCooldownUntil = now + cooldown;
               line.append(", server cooldown ").append(seconds(cooldown)).append(" left");
               if (cooldown >= 60000L) {
                  line.append(" (turn Player Bypass off and on to clear it)");
               }
            }

            this.host.say(line.append('.').toString());
         } else {
            this.host.warn("Join a server first, Auto RTP is off.");
            this.host.switchedOff();
         }
      }
   }

   public void stopByUser() {
      if (this.loop.running()) {
         this.stop("Auto RTP stopped after " + this.loop.landings() + rtpWord(this.loop.landings()) + ".");
      }
   }

   public void forgetCooldown() {
      this.loop.forgetCooldown();
   }

   private void stop(String why) {
      boolean was = this.loop.running();
      this.loop.stop();
      this.resetMenu();
      this.link = null;
      this.level = null;
      this.anchor = null;
      if (was && why != null) {
         this.host.say(why);
      }
   }

   private void stopWithRestartHint(String why) {
      this.stop(why + " Turn auto-rtp off and on to start again.");
   }

   public boolean playerSpotted(int blockX, int blockZ, boolean disconnecting) {
      if (this.loop.running() && !this.atStartSpot(blockX >> 4, blockZ >> 4)) {
         this.found("Player Activity", blockX, blockZ, disconnecting);
         return true;
      } else {
         return false;
      }
   }

   public void chunkArrived() {
      if (this.loop.running()) {
         this.loop.dataArrived(System.currentTimeMillis());
      }
   }

   private void found(String what, int blockX, int blockZ, boolean disconnecting) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      boolean teleporting = this.loop.phase() == AutoRtpLoop.Phase.TELEPORTING;
      boolean moved = player != null && this.anchor != null && (mc.level != this.level || position(player).distanceToSqr(this.anchor) > 256.0);
      boolean pending = this.loop.awaitingTeleport() && player != null && !moved;
      int rtps = this.loop.landings() + (teleporting && moved ? 1 : 0);
      boolean showPos = this.host.showCoordinates();
      this.stopWithRestartHint(what + " found" + (showPos ? " at " + blockX + ", " + blockZ : "") + " after " + rtps + rtpWord(rtps) + ". Auto RTP stopped.");
      if (pending && !disconnecting) {
         this.host
            .warn(
               "Your /"
                  + this.command()
                  + " is still pending: move a little to cancel it (most servers cancel a teleport when you move), or the server may still teleport you away."
            );
      }

      BypassToast.addOrUpdate(
         new ItemStack(Items.PLAYER_HEAD),
         what + " Found - Auto RTP",
         Component.literal(showPos ? "at " + blockX + ", " + blockZ : "after " + rtps + rtpWord(rtps)),
         this.host.sound()
      );
      if (this.host.sound()) {
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
      }
   }

   public void tick() {
      if (this.loop.running()) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         ClientPacketListener connection = mc.getConnection();
         long now = System.currentTimeMillis();
         if (this.link != null && this.link.isConnected() && (connection == null || connection.getConnection() == this.link)) {
            if (player != null && mc.level != null && connection != null && mc.gameMode != null) {
               this.offlineSince = 0L;
               AutoRtpLoop.Config config = this.config();
               if (player.isDeadOrDying()) {
                  if (!this.wasDead) {
                     this.wasDead = true;
                     this.loop.interrupt(now, config);
                     this.resetMenu();
                  }

                  this.loop.holdUntil(now + 3000L);
                  this.bar(now, this.label(config) + " - paused, you died", now - this.lastBarAt >= 2000L);
               } else {
                  if (this.wasDead) {
                     this.wasDead = false;
                     this.level = mc.level;
                     this.anchor = position(player);
                     this.loop.holdUntil(now + 3000L);
                  }

                  boolean levelChanged = mc.level != this.level;
                  if (levelChanged) {
                     this.level = mc.level;
                  }

                  if (levelChanged || this.anchor != null && position(player).distanceToSqr(this.anchor) > 256.0) {
                     switch (this.loop.phase()) {
                        case TELEPORTING:
                           this.land(player, now, config);
                           break;
                        case SCANNING:
                           this.anchor = position(player);
                           this.loop.relanded(now, config);
                           break;
                        default:
                           if (this.loop.landedLate(now, config)) {
                              this.anchor = position(player);
                              this.closeMenu(player);
                              this.resetMenu();
                           } else if (levelChanged) {
                              this.stopWithRestartHint("World changed, Auto RTP stopped.");
                              return;
                           }
                     }
                  }

                  if (!this.handleMenu(mc, player, now, config)) {
                     String pause = mc.gui.screen() != null && !this.ourMenuOpen(mc, player) ? "a menu is open" : null;
                     if (pause != null) {
                        this.loop.holdUntil(now + 1000L);
                     }

                     switch (this.loop.tick(now, config)) {
                        case SEND:
                           this.send(player, connection, now);
                           break;
                        case TIMED_OUT:
                           this.lastTryLanded = false;
                           this.closeMenu(player);
                           break;
                        case SCAN_CLEAR:
                           this.lastTryLanded = true;
                           break;
                        case LIMIT:
                           this.stopWithRestartHint("Max RTPs reached (" + config.maxRtps() + "), no base found. Auto RTP stopped.");
                           return;
                        case GAVE_UP:
                           this.closeMenu(player);
                           this.stopWithRestartHint("No teleport after 5 tries, check rtp-command and menu-item. Auto RTP stopped.");
                           return;
                     }

                     this.progress(now, pause, config);
                  }
               }
            } else {
               if (this.offlineSince == 0L) {
                  this.offlineSince = now;
               }

               if (now - this.offlineSince > 30000L) {
                  this.stopWithRestartHint("No world for 30s, Auto RTP stopped.");
               }
            }
         } else {
            this.stopWithRestartHint("Disconnected, Auto RTP stopped.");
         }
      }
   }

   private void send(LocalPlayer player, ClientPacketListener connection, long now) {
      this.anchor = position(player);
      this.resetMenu();
      this.commandSentAt = now;
      this.sendingCommand = true;

      try {
         connection.sendCommand(this.command());
      } finally {
         this.sendingCommand = false;
      }
   }

   private void land(LocalPlayer player, long now, AutoRtpLoop.Config config) {
      this.anchor = position(player);
      this.closeMenu(player);
      this.resetMenu();
      this.loop.landed(now, config);
   }

   public void onPacket(Packet<?> packet) {
      if (this.loop.running()) {
         if (packet instanceof ClientboundDamageEventPacket damage) {
            this.onDamage(damage);
         } else if (packet instanceof ClientboundSystemChatPacket chat) {
            this.onServerText(chat.content().getString(), !chat.overlay());
         } else if (packet instanceof ClientboundSetTitleTextPacket title) {
            this.onServerText(title.text().getString(), false);
         } else if (packet instanceof ClientboundSetSubtitleTextPacket subtitle) {
            this.onServerText(subtitle.text().getString(), false);
         }
      }
   }

   public void onPacketSent(Packet<?> packet) {
      if (!this.sendingCommand) {
         if (packet instanceof ServerboundUseItemOnPacket
            || packet instanceof ServerboundChatCommandPacket
            || packet instanceof ServerboundChatCommandSignedPacket
            || packet instanceof ServerboundInteractPacket interact && isInteraction(interact)) {
            this.userActionAt = System.currentTimeMillis();
         }
      }
   }

   private static boolean isInteraction(ServerboundInteractPacket packet) {
      final boolean[] interaction = new boolean[]{false};
      packet.dispatch(new Handler() {
         @Override
         public void onInteraction(InteractionHand hand) {
            interaction[0] = true;
         }

         @Override
         public void onInteraction(InteractionHand hand, Vec3 pos) {
            interaction[0] = true;
         }

         @Override
         public void onAttack() {
         }
      });
      return interaction[0];
   }

   private void onServerText(String raw, boolean chatLine) {
      long now = System.currentTimeMillis();
      boolean teleporting = this.loop.phase() == AutoRtpLoop.Phase.TELEPORTING;
      if (teleporting || this.loop.sentWithin(now, 5000L)) {
         if (!chatLine || !RtpReply.looksLikePlayerChat(raw, otherPlayerNames())) {
            RtpReply reply = RtpReply.parse(raw);
            boolean fresh = this.loop.sentWithin(now, 2500L);
            switch (reply.kind()) {
               case COOLDOWN:
                  this.loop.cooldown(now, reply.millis());
                  this.noticeCooldown(now, reply.millis());
                  break;
               case WARMUP:
                  this.loop.warmup(now, reply.millis(), this.config());
                  if (reply.nextUseMs() >= 0L) {
                     this.loop.nextUseIn(now, reply.nextUseMs());
                     this.noticeCooldown(now, reply.nextUseMs());
                  }
                  break;
               case CANCELLED:
                  this.loop.cancelled(now);
                  break;
               case UNKNOWN_COMMAND:
                  if (fresh) {
                     this.stopWithRestartHint("The server does not know /" + this.command() + ", check rtp-command. Auto RTP stopped.");
                  }
                  break;
               case NO_PERMISSION:
                  if (fresh) {
                     this.stopWithRestartHint("No permission for /" + this.command() + ". Auto RTP stopped.");
                  }
                  break;
               case COMBAT:
                  if (teleporting && fresh) {
                     this.stopWithRestartHint("In combat, Auto RTP stopped.");
                  }
            }
         }
      }
   }

   private void noticeCooldown(long now, long ms) {
      long until = now + ms;
      if (ms >= 60000L && until > this.noticedCooldownUntil + 5000L) {
         this.noticedCooldownUntil = until;
         this.host.say("Server cooldown " + seconds(ms) + ", Auto RTP waits.");
      }
   }

   private static List<String> otherPlayerNames() {
      Minecraft mc = Minecraft.getInstance();
      ClientPacketListener connection = mc.getConnection();
      if (connection == null) {
         return List.of();
      } else {
         LocalPlayer self = mc.player;
         UUID selfId = self == null ? null : self.getUUID();
         String selfName = self == null ? null : self.getGameProfile().name();
         List<String> names = new ArrayList<>();

         for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null && profile.name() != null && !Objects.equals(profile.id(), selfId) && !profile.name().equalsIgnoreCase(selfName)) {
               names.add(profile.name());
            }
         }

         return names;
      }
   }

   private void onDamage(ClientboundDamageEventPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && packet.entityId() == player.getId()) {
         Entity cause = packet.sourceCauseId() >= 0 ? mc.level.getEntity(packet.sourceCauseId()) : null;
         if (cause != player) {
            boolean byPlayer = cause instanceof Player
               || packet.sourceType().is(DamageTypes.PLAYER_ATTACK)
               || packet.sourceType().is(DamageTypes.PLAYER_EXPLOSION);
            if (byPlayer) {
               String who = cause instanceof Player attacker ? attacker.getGameProfile().name() : "a player";
               this.stopWithRestartHint("Hit by " + who + ", Auto RTP stopped (combat).");
            }
         }
      }
   }

   private boolean handleMenu(Minecraft mc, LocalPlayer player, long now, AutoRtpLoop.Config config) {
      if (this.loop.phase() == AutoRtpLoop.Phase.TELEPORTING && containerOpen(mc, player)) {
         AbstractContainerMenu menu = player.containerMenu;
         if (menu.containerId != this.menuId) {
            if (menu.containerId == this.ignoredMenuId) {
               return false;
            }

            if (!this.menuMayBeOurs(now)) {
               this.ignoredMenuId = menu.containerId;
               return false;
            }

            if (this.menuClicks >= 3) {
               this.stopWithRestartHint("The RTP menu keeps opening, Auto RTP stopped.");
               return true;
            }

            this.menuId = menu.containerId;
            this.menuSeenAt = now;
            this.menuClicked = false;
            this.loop.extend(now, config);
         }

         if (!this.menuClicked && now - this.menuSeenAt >= 300L) {
            List<String> names = new ArrayList<>();
            String wanted = this.host.menuItem().trim();
            int slot = findSlot(menu, wanted, names);
            boolean loading = now - this.menuSeenAt < 2500L;
            if (wanted.isEmpty()) {
               if (names.isEmpty() && loading) {
                  return false;
               } else {
                  this.stopWithRestartHint("The RTP command opened a menu, set menu-item to the item to click" + nameList(names) + ". Auto RTP stopped.");
                  return true;
               }
            } else if (slot >= 0) {
               mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
               this.menuClicked = true;
               this.menuClicks++;
               this.lastMenuClickAt = now;
               this.loop.extend(now, config);
               return false;
            } else if (loading) {
               return false;
            } else {
               this.stopWithRestartHint("No \"" + wanted + "\" in the RTP menu" + nameList(names) + ". Auto RTP stopped.");
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean menuMayBeOurs(long now) {
      return this.commandSentAt != -9223372036854775808L && this.userActionAt < this.commandSentAt
         ? now - this.commandSentAt <= 4000L || this.menuClicks > 0 && now - this.lastMenuClickAt <= 4000L
         : false;
   }

   private static int findSlot(AbstractContainerMenu menu, String wanted, List<String> names) {
      List<Slot> slots = new ArrayList<>();
      List<String> slotNames = new ArrayList<>();
      List<String> slotIds = new ArrayList<>();

      for (Slot slot : menu.slots) {
         if (slot.container instanceof Inventory) {
            break;
         }

         ItemStack stack = slot.getItem();
         if (!stack.isEmpty()) {
            String name = RtpReply.normalize(stack.getHoverName().getString());
            slots.add(slot);
            slotNames.add(name);
            slotIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            if (!name.isEmpty() && !names.contains(name)) {
               names.add(name);
            }
         }
      }

      for (String part : wanted.split(",")) {
         String text = RtpReply.normalize(part);
         if (!text.isEmpty()) {
            String id = text.replace(' ', '_');

            for (int i = 0; i < slots.size(); i++) {
               if (slotNames.get(i).contains(text) || slotIds.get(i).equals(id)) {
                  return slots.get(i).index;
               }
            }
         }
      }

      return -1;
   }

   private static String nameList(List<String> names) {
      if (names.isEmpty()) {
         return "";
      } else {
         List<String> shown = names.size() > 10 ? names.subList(0, 10) : names;
         return " (items: " + String.join(", ", shown) + (names.size() > shown.size() ? ", ..." : "") + ")";
      }
   }

   private static boolean containerOpen(Minecraft mc, LocalPlayer player) {
      return player.containerMenu != player.inventoryMenu && mc.gui.screen() instanceof AbstractContainerScreen;
   }

   private boolean ourMenuOpen(Minecraft mc, LocalPlayer player) {
      return this.menuId >= 0 && containerOpen(mc, player) && player.containerMenu.containerId == this.menuId;
   }

   private void closeMenu(LocalPlayer player) {
      if (this.menuId >= 0 && player.containerMenu != player.inventoryMenu && player.containerMenu.containerId == this.menuId) {
         player.closeContainer();
      }
   }

   private void resetMenu() {
      this.menuId = -1;
      this.ignoredMenuId = -1;
      this.menuClicked = false;
      this.menuClicks = 0;
   }

   private boolean atStartSpot(int chunkX, int chunkZ) {
      if (this.loop.landings() > 0) {
         return false;
      } else {
         int radius = Minecraft.getInstance().options.getEffectiveRenderDistance() + 1;
         return Math.abs(chunkX - this.startChunkX) <= radius && Math.abs(chunkZ - this.startChunkZ) <= radius;
      }
   }

   private void progress(long now, String pause, AutoRtpLoop.Config config) {
      String label = this.label(config);
      String text;
      if (pause != null) {
         text = label + " - paused, " + pause;
      } else {
         long next = this.loop.nextSendIn(now);

         text = switch (this.loop.phase()) {
            case TELEPORTING -> label + " - waiting for the teleport";
            case SCANNING -> label + " - scanning, " + seconds(this.loop.scanLeft(now)) + " left";
            case WAITING -> this.loop.cooldownLeft(now) > 0L
               ? label + " - server cooldown, next in " + seconds(next)
               : (
                  this.loop.landings() == 0 && this.loop.sent() == 0
                     ? label + " - starting" + (next >= 1000L ? " in " + seconds(next) : "")
                     : (
                        !this.lastTryLanded
                           ? label + " - no teleport, retry in " + seconds(next)
                           : (this.loop.settling(now) ? label + " - no base yet, chunks still loading" : label + " - no base, next in " + seconds(next))
                     )
               );
            default -> "";
         };
      }

      this.bar(now, text, this.loop.phase() != AutoRtpLoop.Phase.TELEPORTING && now - this.lastBarAt >= 2000L);
   }

   private String label(AutoRtpLoop.Config config) {
      return "Auto RTP #" + this.loop.sent() + (config.maxRtps() > 0 ? "/" + config.maxRtps() : "");
   }

   private void bar(long now, String text, boolean refresh) {
      if (!text.isEmpty() && (!text.equals(this.lastBar) || refresh)) {
         this.lastBar = text;
         this.lastBarAt = now;
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            player.displayClientMessage(Component.literal(text), true);
         }
      }
   }

   private AutoRtpLoop.Config config() {
      return new AutoRtpLoop.Config(this.host.scanMs(), this.host.delayMs(), this.host.timeoutMs(), this.host.maxRtps());
   }

   private String command() {
      String text = this.host.command().trim();

      while (text.startsWith("/")) {
         text = text.substring(1).trim();
      }

      return text.isEmpty() ? "rtp" : text;
   }

   private static Vec3 position(LocalPlayer player) {
      return new Vec3(player.getX(), player.getY(), player.getZ());
   }

   private static String seconds(long ms) {
      long s = (ms + 999L) / 1000L;
      return s >= 120L ? String.format(Locale.ROOT, "%dm %ds", s / 60L, s % 60L) : s + "s";
   }

   private static String rtpWord(int count) {
      return count == 1 ? " RTP" : " RTPs";
   }

   public interface Host {
      String command();

      long scanMs();

      long delayMs();

      long timeoutMs();

      int maxRtps();

      String menuItem();

      boolean sound();

      boolean showCoordinates();

      void say(String var1);

      void warn(String var1);

      void switchedOff();
   }
}

package com.jfant.specter.modules;

import com.jfant.specter.Scope;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.sound.SoundSpots;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class SoundDetector extends Module {
   private static final int QUEUE_LIMIT = 2048;
   private static final int PER_TICK = 256;
   private static final int REFRESH_TICKS = 10;
   private static final long YOUR_BLOCK_MS = 6000L;
   private static final long YOUR_CONTAINER_MS = 2500L;
   private static final long ALERT_GAP_MS = 3000L;
   private static final double YOUR_BLOCK_REACH = 2.0;
   private static final double YOUR_CONTAINER_REACH = 6.0;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAlerts = this.settings.createGroup("Alerts");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> containers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("containers")
            .description("Listen for chests, barrels, shulker boxes and ender chests being opened or closed.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> workBlocks = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("work-blocks")
            .description(
               "Listen for anvils, enchanting tables, brewing stands, smithing tables, grindstones, stonecutters, looms, cartography tables, respawn anchors, beacons and end portal frames."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> redstone = this.sgGeneral
      .add(new BoolSetting.Builder().name("redstone").description("Listen for pistons, dispensers, droppers, levers and buttons.").defaultValue(true).build());
   private final Setting<Boolean> doors = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("doors").description("Listen for doors, trapdoors and gates. Villagers set these off too.").defaultValue(false).build()
      );
   private final Setting<Boolean> music = this.sgGeneral
      .add(new BoolSetting.Builder().name("music").description("Listen for note blocks, jukeboxes starting, bells and goat horns.").defaultValue(true).build());
   private final Setting<Boolean> explosions = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("explosions")
            .description("Listen for explosions: TNT, creepers, crystals, anchors and beds.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> skipOwn = this.sgGeneral
      .add(new BoolSetting.Builder().name("skip-own").description("Leave out what you use yourself.").defaultValue(true).build());
   private final Setting<Boolean> chat = this.sgAlerts
      .add(new BoolSetting.Builder().name("chat").description("Say in chat when something is heard at a new spot. Only you see it.").defaultValue(true).build());
   private final Setting<Boolean> ping = this.sgAlerts
      .add(
         new BoolSetting.Builder()
            .name("ping")
            .description("Play a pling when something is heard at a new spot. Only you hear it.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> range = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("range")
            .description("How far away to show spots, in blocks.")
            .defaultValue(128)
            .range(16, 1024)
            .sliderRange(16, 512)
            .build()
      );
   private final Setting<Integer> maxShown = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("max-shown")
            .description("How many of the nearest spots to show.")
            .defaultValue(40)
            .range(1, 200)
            .sliderRange(5, 100)
            .build()
      );
   private final Setting<SettingColor> color = this.sgRender
      .add(new ColorSetting.Builder().name("color").description("The colour of the boxes and labels.").defaultValue(new SettingColor(60, 200, 255, 60)).build());
   private final Setting<Boolean> labels = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("labels")
            .description("Label each spot with what was heard, how many times and how long ago.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> beams = this.sgRender
      .add(new BoolSetting.Builder().name("beams").description("Draw a beam up from each spot.").defaultValue(false).build());
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw a line from you to each spot.").defaultValue(false).build());
   private final SoundSpots spots = new SoundSpots();
   private final ConcurrentLinkedQueue<SoundDetector.Heard> inbox = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<SoundDetector.Click> clicks = new ConcurrentLinkedQueue<>();
   private ClientLevel lastWorld;
   private long containerOpenAt;
   private long lastAlert;
   private int refreshIn;
   private List<SoundSpots.Spot> shown = List.of();
   private final Vector3d labelPos = new Vector3d();
   private final Color line = new Color();
   private static final Color LABEL_TEXT = new Color(235, 235, 235);

   public SoundDetector() {
      super(
         SpecterClientAddon.BASES,
         "sound-detector",
         "Marks where containers, work blocks, redstone, music and explosions were heard, and remembers those spots across joins. Sends nothing."
      );
   }

   @Override
   public void onActivate() {
      this.lastWorld = null;
      this.refreshIn = 0;
   }

   @Override
   public void onDeactivate() {
      this.stop();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.stop();
   }

   private void stop() {
      this.spots.flush();
      this.spots.select(null);
      this.lastWorld = null;
      this.forgetMoment();
   }

   private void forgetMoment() {
      this.inbox.clear();
      this.clicks.clear();
      this.containerOpenAt = 0L;
      this.shown = List.of();
   }

   @Override
   public String getInfoString() {
      return this.spots.size() == 0 ? null : String.valueOf(this.spots.size());
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      this.read(event.packet);
   }

   private void read(Packet<?> packet) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> inner : bundle.subPackets()) {
            this.read(inner);
         }
      } else if (packet instanceof ClientboundSoundPacket sound) {
         this.queue(
            BlockPos.containing(sound.getX(), sound.getY(), sound.getZ()), ofSound(sound.getSound().value().location().getPath().toLowerCase(Locale.ROOT))
         );
      } else if (packet instanceof ClientboundSoundEntityPacket sound) {
         if (sound.getSound().value().location().getPath().startsWith("item.goat_horn.sound.")) {
            int blower = sound.getId();
            this.mc.execute(() -> this.hornBlown(blower));
         }
      } else if (packet instanceof ClientboundLevelEventPacket world) {
         this.queue(world.getPos(), ofWorldEvent(world.getType()));
      } else if (packet instanceof ClientboundBlockEventPacket blockEvent) {
         this.queue(blockEvent.getPos(), ofBlockEvent(blockEvent.getBlock()));
      } else if (packet instanceof ClientboundExplodePacket blast) {
         this.queue(BlockPos.containing(blast.center()), SoundSpots.Kind.EXPLOSION);
      }
   }

   private void hornBlown(int entityId) {
      if (this.isActive() && this.mc.level != null && this.music.get()) {
         Entity blower = this.mc.level.getEntity(entityId);
         if (blower != null) {
            this.queue(blower.blockPosition(), SoundSpots.Kind.GOAT_HORN);
         } else {
            SpecterClientAddon.LOG.info("Sound Detector: goat horn heard, whoever blew it is not loaded");
            this.alert("Goat Horn heard within 256 blocks, too far away to place", System.currentTimeMillis());
         }
      }
   }

   private void queue(BlockPos pos, SoundSpots.Kind kind) {
      if (kind != null && this.inbox.size() < 2048) {
         this.inbox.add(new SoundDetector.Heard(pos.immutable(), kind));
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (event.packet instanceof ServerboundUseItemOnPacket use) {
         this.clicks.add(new SoundDetector.Click(use.hitResult().getBlockPos().immutable(), System.currentTimeMillis() + 6000L));
      }
   }

   private static SoundSpots.Kind ofSound(String path) {
      if (!path.endsWith(".open") && !path.endsWith(".close")) {
         if (path.startsWith("block.note_block.")) {
            return SoundSpots.Kind.NOTE_BLOCK;
         } else {
            return switch (path) {
               case "block.bell.use" -> SoundSpots.Kind.BELL;
               case "block.enchantment_table.use" -> SoundSpots.Kind.ENCHANTING_TABLE;
               case "block.respawn_anchor.charge", "block.respawn_anchor.set_spawn" -> SoundSpots.Kind.RESPAWN_ANCHOR;
               case "block.beacon.activate", "block.beacon.deactivate", "block.beacon.power_select" -> SoundSpots.Kind.BEACON;
               case "block.end_portal_frame.fill" -> SoundSpots.Kind.END_PORTAL_FRAME;
               case "ui.stonecutter.take_result" -> SoundSpots.Kind.STONECUTTER;
               case "ui.loom.take_result" -> SoundSpots.Kind.LOOM;
               case "ui.cartography_table.take_result" -> SoundSpots.Kind.CARTOGRAPHY_TABLE;
               case "block.piston.extend", "block.piston.contract" -> SoundSpots.Kind.PISTON;
               case "block.lever.click" -> SoundSpots.Kind.LEVER;
               default -> path.startsWith("block.") && path.contains("button.click") ? SoundSpots.Kind.BUTTON : null;
            };
         }
      } else if (path.startsWith("block.shulker_box.")) {
         return SoundSpots.Kind.SHULKER_BOX;
      } else if (path.startsWith("block.ender_chest.")) {
         return SoundSpots.Kind.ENDER_CHEST;
      } else if (path.startsWith("block.barrel.")) {
         return SoundSpots.Kind.BARREL;
      } else if (path.contains("chest")) {
         return SoundSpots.Kind.CHEST;
      } else if (path.contains("trapdoor")) {
         return SoundSpots.Kind.TRAPDOOR;
      } else if (path.contains("fence_gate")) {
         return SoundSpots.Kind.GATE;
      } else {
         return path.contains("door") ? SoundSpots.Kind.DOOR : null;
      }
   }

   private static SoundSpots.Kind ofWorldEvent(int id) {
      return switch (id) {
         case 1000, 1001 -> SoundSpots.Kind.DISPENSER;
         case 1010 -> SoundSpots.Kind.JUKEBOX;
         case 1030 -> SoundSpots.Kind.ANVIL;
         case 1035 -> SoundSpots.Kind.BREWING_STAND;
         case 1042 -> SoundSpots.Kind.GRINDSTONE;
         case 1044 -> SoundSpots.Kind.SMITHING_TABLE;
         default -> null;
      };
   }

   private static SoundSpots.Kind ofBlockEvent(Block block) {
      if (block == Blocks.ENDER_CHEST) {
         return SoundSpots.Kind.ENDER_CHEST;
      } else if (block == Blocks.NOTE_BLOCK) {
         return SoundSpots.Kind.NOTE_BLOCK;
      } else if (block == Blocks.BELL) {
         return SoundSpots.Kind.BELL;
      } else if (block instanceof ChestBlock) {
         return SoundSpots.Kind.CHEST;
      } else {
         return block instanceof ShulkerBoxBlock ? SoundSpots.Kind.SHULKER_BOX : null;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         if (this.mc.level != this.lastWorld) {
            this.spots.flush();
            this.spots.select(Scope.current(this.mc));
            this.lastWorld = this.mc.level;
            this.forgetMoment();
            this.refreshIn = 0;
         }

         this.spots.tickAutosave();
         long now = System.currentTimeMillis();
         if (this.mc.player.containerMenu != this.mc.player.inventoryMenu) {
            this.containerOpenAt = now;
         }

         this.clicks.removeIf(click -> click.until() <= now);
         boolean added = false;

         for (int handled = 0; handled < 256; handled++) {
            SoundDetector.Heard heard = this.inbox.poll();
            if (heard == null) {
               break;
            }

            if (this.hear(heard, now)) {
               added = true;
            }
         }

         if (added || --this.refreshIn <= 0) {
            this.refreshShown();
            this.refreshIn = 10;
         }
      }
   }

   private boolean hear(SoundDetector.Heard heard, long now) {
      if (!this.watching(heard.kind().group)) {
         return false;
      } else if (this.skipOwn.get() && this.yours(heard.pos(), now)) {
         return false;
      } else {
         BlockPos pos = this.oneChest(heard.pos(), heard.kind());
         if (!this.spots.heard(pos, heard.kind(), now)) {
            return false;
         } else {
            this.announce(pos, heard.kind(), now);
            return true;
         }
      }
   }

   private boolean watching(SoundSpots.Group group) {
      return switch (group) {
         case CONTAINERS -> this.containers.get();
         case WORK_BLOCKS -> this.workBlocks.get();
         case REDSTONE -> this.redstone.get();
         case DOORS -> this.doors.get();
         case MUSIC -> this.music.get();
         case EXPLOSIONS -> this.explosions.get();
      };
   }

   private boolean yours(BlockPos pos, long now) {
      for (SoundDetector.Click click : this.clicks) {
         if (click.until() > now && click.pos().closerThan(pos, 2.0)) {
            return true;
         }
      }

      return now - this.containerOpenAt < 2500L && this.mc.player.blockPosition().closerThan(pos, 6.0);
   }

   private BlockPos oneChest(BlockPos pos, SoundSpots.Kind kind) {
      if (kind == SoundSpots.Kind.CHEST && this.spots.kindAt(pos) != kind) {
         for (Direction side : Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (this.spots.kindAt(next) == kind) {
               return next;
            }
         }

         return pos;
      } else {
         return pos;
      }
   }

   private void announce(BlockPos pos, SoundSpots.Kind kind, long now) {
      String text = kind.title + " heard at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
      SpecterClientAddon.LOG.info("Sound Detector: {}", text);
      this.alert(text, now);
   }

   private void alert(String text, long now) {
      if (now - this.lastAlert >= 3000L) {
         this.lastAlert = now;
         if (this.chat.get()) {
            this.info("%s", new Object[]{text});
         }

         if (this.ping.get()) {
            this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 1.0F));
         }
      }
   }

   private void refreshShown() {
      Vec3 me = this.mc.player.position();
      double limit = (double)this.range.get().intValue() * this.range.get().intValue();
      List<SoundSpots.Spot> near = new ArrayList<>();

      for (SoundSpots.Spot spot : this.spots.all()) {
         if (this.watching(spot.kind.group) && distanceSq(spot, me) <= limit) {
            near.add(spot);
         }
      }

      near.sort(Comparator.comparingDouble(spotx -> distanceSq(spotx, me)));
      this.shown = (List<SoundSpots.Spot>)(near.size() > this.maxShown.get() ? new ArrayList<>(near.subList(0, this.maxShown.get())) : near);
   }

   private static double distanceSq(SoundSpots.Spot spot, Vec3 to) {
      double dx = spot.x + 0.5 - to.x;
      double dy = spot.y + 0.5 - to.y;
      double dz = spot.z + 0.5 - to.z;
      return dx * dx + dy * dy + dz * dz;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.shown.isEmpty()) {
         SettingColor c = this.color.get();
         this.line.set(c.r, c.g, c.b, 255);
         Vec3 from = RenderUtils.center;

         for (SoundSpots.Spot spot : this.shown) {
            double x = spot.x + 0.5;
            double y = spot.y;
            double z = spot.z + 0.5;
            event.renderer.box(spot.x, spot.y, spot.z, spot.x + 1, spot.y + 1, spot.z + 1, c, this.line, ShapeMode.Both, 0);
            if (this.beams.get()) {
               event.renderer.line(x, y + 1.0, z, x, y + 48.0, z, this.line);
            }

            if (this.tracers.get() && from != null) {
               event.renderer.line(from.x, from.y, from.z, x, y + 0.5, z, this.line);
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.labels.get() && !this.shown.isEmpty()) {
         TextRenderer text = TextRenderer.get();
         SettingColor c = this.color.get();
         this.line.set(c.r, c.g, c.b, 255);
         long now = System.currentTimeMillis();

         for (SoundSpots.Spot spot : this.shown) {
            this.labelPos.set(spot.x + 0.5, spot.y + 1.8, spot.z + 0.5);
            if (NametagUtils.to2D(this.labelPos, 1.3)) {
               String head = spot.kind.title;
               String second = (spot.count > 1 ? "x" + spot.count + " - " : "") + ago(now - spot.last);
               NametagUtils.begin(this.labelPos, event.graphics);
               text.begin(event.graphics);
               double h = text.getHeight(true);
               text.render(head, -text.getWidth(head, true) / 2.0, -h * 2.0, this.line, true);
               text.render(second, -text.getWidth(second, true) / 2.0, -h, LABEL_TEXT, true);
               text.end();
               NametagUtils.end(event.graphics);
            }
         }
      }
   }

   private static String ago(long ms) {
      long seconds = ms / 1000L;
      if (seconds < 60L) {
         return "just now";
      } else if (seconds < 3600L) {
         return seconds / 60L + "m ago";
      } else {
         return seconds < 86400L ? seconds / 3600L + "h ago" : seconds / 86400L + "d ago";
      }
   }

   private record Click(BlockPos pos, long until) {
   }

   private record Heard(BlockPos pos, SoundSpots.Kind kind) {
   }
}

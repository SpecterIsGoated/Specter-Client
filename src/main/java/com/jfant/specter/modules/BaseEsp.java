package com.jfant.specter.modules;

import com.jfant.specter.Scope;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.reveal.RevealStore;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class BaseEsp extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgEntities = this.settings.createGroup("Entities");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgStorage = this.settings.createGroup("Storage");
   private static final List<Block> STORAGE_BLOCKS = List.of(
      Blocks.CHEST,
      Blocks.TRAPPED_CHEST,
      Blocks.BARREL,
      Blocks.ENDER_CHEST,
      Blocks.SHULKER_BOX,
      Blocks.DYED_SHULKER_BOX.white(),
      Blocks.DYED_SHULKER_BOX.orange(),
      Blocks.DYED_SHULKER_BOX.magenta(),
      Blocks.DYED_SHULKER_BOX.lightBlue(),
      Blocks.DYED_SHULKER_BOX.yellow(),
      Blocks.DYED_SHULKER_BOX.lime(),
      Blocks.DYED_SHULKER_BOX.pink(),
      Blocks.DYED_SHULKER_BOX.gray(),
      Blocks.DYED_SHULKER_BOX.lightGray(),
      Blocks.DYED_SHULKER_BOX.cyan(),
      Blocks.DYED_SHULKER_BOX.purple(),
      Blocks.DYED_SHULKER_BOX.blue(),
      Blocks.DYED_SHULKER_BOX.brown(),
      Blocks.DYED_SHULKER_BOX.green(),
      Blocks.DYED_SHULKER_BOX.red(),
      Blocks.DYED_SHULKER_BOX.black(),
      Blocks.SPAWNER,
      Blocks.FURNACE,
      Blocks.SMOKER,
      Blocks.BLAST_FURNACE,
      Blocks.ENCHANTING_TABLE
   );
   private static final List<Block> REDSTONE_BLOCKS = List.of(
      Blocks.PISTON,
      Blocks.STICKY_PISTON,
      Blocks.OBSERVER,
      Blocks.REPEATER,
      Blocks.COMPARATOR,
      Blocks.REDSTONE_TORCH,
      Blocks.REDSTONE_WALL_TORCH,
      Blocks.REDSTONE_LAMP,
      Blocks.LEVER,
      Blocks.DAYLIGHT_DETECTOR,
      Blocks.TARGET,
      Blocks.DISPENSER,
      Blocks.DROPPER,
      Blocks.HOPPER,
      Blocks.CRAFTER
   );
   private final Setting<Boolean> storage = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("storage")
            .description(
               "Record chests, trapped chests, barrels, ender chests, shulker boxes, spawners, furnaces and enchanting tables - also the ones the server hides in fake stone, from the list of containers it sends with every chunk."
            )
            .defaultValue(true)
            .onChanged(v -> this.rebuildTracked())
            .build()
      );
   private final Setting<Boolean> redstone = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("redstone")
            .description(
               "Record pistons, observers, repeaters, comparators, redstone torches and lamps, levers, daylight detectors, targets, dispensers, droppers, hoppers and crafters."
            )
            .defaultValue(true)
            .onChanged(v -> this.rebuildTracked())
            .build()
      );
   private final Setting<Boolean> openedStorage = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("opened-storage")
            .description(
               "Also record storage the server hides in fake stone the moment someone opens it near you: the lid opening and the open and close sounds still come through, from up to about 64 blocks away."
            )
            .defaultValue(true)
            .visible(this.storage::get)
            .build()
      );
   private final Setting<Boolean> removeMined = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("remove-mined")
            .description("Drop a block from the map when the server shows it changing to something untracked (e.g. mined).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> itemFrames = this.sgEntities
      .add(new BoolSetting.Builder().name("item-frames").description("Record item frames and glow item frames.").defaultValue(true).build());
   private final Setting<Boolean> frameLabels = this.sgEntities
      .add(
         new BoolSetting.Builder()
            .name("frame-labels")
            .description("Write over each item frame what it holds and the owner id DonutSMP stamps on items. Kept after the frame leaves range.")
            .defaultValue(true)
            .visible(this.itemFrames::get)
            .build()
      );
   private final Setting<Integer> frameLabelDistance = this.sgEntities
      .add(
         new IntSetting.Builder()
            .name("frame-label-distance")
            .description("Only label item frames this close (blocks).")
            .defaultValue(64)
            .min(8)
            .sliderRange(8, 256)
            .visible(() -> this.itemFrames.get() && this.frameLabels.get())
            .build()
      );
   private final Setting<Boolean> armorStands = this.sgEntities
      .add(
         new BoolSetting.Builder()
            .name("armor-stands")
            .description("Record armor stands. Invisible ones are skipped - servers use those for floating text, not bases.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> villagers = this.sgEntities
      .add(
         new BoolSetting.Builder()
            .name("villagers")
            .description(
               "Outline villagers - trading halls and breeders mean a base. They walk around, so they are drawn live while loaded instead of remembered."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> villagerSideColor = this.sgEntities
      .add(
         new ColorSetting.Builder()
            .name("villager-side-color")
            .description("Fill colour of villager boxes.")
            .defaultValue(new SettingColor(255, 40, 60, 50))
            .visible(this.villagers::get)
            .build()
      );
   private final Setting<SettingColor> villagerLineColor = this.sgEntities
      .add(
         new ColorSetting.Builder()
            .name("villager-line-color")
            .description("Outline and tracer colour of villagers.")
            .defaultValue(new SettingColor(255, 70, 85, 255))
            .visible(this.villagers::get)
            .build()
      );
   private final Setting<Boolean> multicolorEsp = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("multicolor-esp")
            .description(
               "Give each kind of block's box its own colour, set under Colors: chests, barrels, shulkers, hoppers, redstone and so on. Villagers keep their own colour."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> multicolorTracers = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("multicolor-tracers")
            .description("Give each kind of block's tracer its own colour, set under Colors. Works with multicolor-esp on or off.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("Fill colour of the boxes. With multicolor-esp on only its see-through level is used, so each fill matches its outline.")
            .defaultValue(new SettingColor(150, 60, 255, 50))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("Outline colour while multicolor-esp is off, and tracer colour while multicolor-tracers is off.")
            .defaultValue(new SettingColor(190, 110, 255, 255))
            .visible(() -> !this.multicolorEsp.get() || !this.multicolorTracers.get())
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the boxes are drawn."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Boolean> tracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw a line from you to each revealed block.").defaultValue(false).build());
   private final Setting<Boolean> tracerYLimit = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("tracer-y-limit")
            .description("Only draw tracers to blocks at or below tracer-max-y. Boxes are still drawn for everything.")
            .defaultValue(false)
            .visible(this.tracers::get)
            .build()
      );
   private final Setting<Integer> tracerMaxY = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("tracer-max-y")
            .description("Tracers only go to blocks at or below this Y.")
            .defaultValue(15)
            .range(-64, 320)
            .sliderRange(-64, 320)
            .visible(() -> this.tracers.get() && this.tracerYLimit.get())
            .build()
      );
   private final Setting<Integer> tracerOpacity = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("tracer-opacity")
            .description("How see-through the tracers are, in percent: 100 is solid, 0 is invisible. Box outlines keep line-color's own opacity.")
            .defaultValue(100)
            .range(0, 100)
            .sliderRange(0, 100)
            .visible(this.tracers::get)
            .build()
      );
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Only draw blocks within this many blocks of you.")
            .defaultValue(256)
            .min(16)
            .sliderRange(32, 1024)
            .build()
      );
   private final Setting<Integer> maxRender = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("max-render")
            .description("Draw at most this many boxes, nearest first. The map keeps every block either way - this only bounds render cost.")
            .defaultValue(3000)
            .min(50)
            .sliderRange(200, 20000)
            .build()
      );
   private final Setting<SettingColor> chestColor = this.tint("chest-color", "Chests.", new SettingColor(255, 160, 0, 255));
   private final Setting<SettingColor> trappedChestColor = this.tint("trapped-chest-color", "Trapped chests.", new SettingColor(0, 0, 0, 255));
   private final Setting<SettingColor> barrelColor = this.tint("barrel-color", "Barrels.", new SettingColor(115, 75, 8, 255));
   private final Setting<SettingColor> shulkerColor = this.tint("shulker-color", "Shulker boxes, every colour.", new SettingColor(225, 159, 222, 255));
   private final Setting<SettingColor> enderChestColor = this.tint("ender-chest-color", "Ender chests.", new SettingColor(120, 0, 255, 255));
   private final Setting<SettingColor> hopperColor = this.tint("hopper-color", "Hoppers.", new SettingColor(140, 140, 140, 255));
   private final Setting<SettingColor> dispenserColor = this.tint("dispenser-color", "Dispensers, droppers and crafters.", new SettingColor(140, 140, 140, 255));
   private final Setting<SettingColor> redstoneColor = this.tint(
      "redstone-color",
      "Pistons, observers, repeaters, comparators, redstone torches and lamps, levers, daylight detectors and targets.",
      new SettingColor(255, 0, 0, 255)
   );
   private final Setting<SettingColor> entityColor = this.tint("entity-color", "Item frames and armor stands.", new SettingColor(40, 230, 70, 255));
   private final Setting<SettingColor> spawnerColor = this.tint("spawner-color", "Mob spawners.", new SettingColor(140, 70, 235, 255));
   private final Setting<SettingColor> furnaceColor = this.tint("furnace-color", "Furnaces, smokers and blast furnaces.", new SettingColor(90, 90, 90, 255));
   private final Setting<SettingColor> enchantingColor = this.tint("enchanting-color", "Enchanting tables.", new SettingColor(200, 30, 60, 255));
   private final List<Setting<SettingColor>> tintSettings = List.of(
      this.chestColor,
      this.trappedChestColor,
      this.barrelColor,
      this.shulkerColor,
      this.enderChestColor,
      this.hopperColor,
      this.dispenserColor,
      this.redstoneColor,
      this.entityColor,
      this.spawnerColor,
      this.furnaceColor,
      this.enchantingColor
   );
   private final Setting<Boolean> forgetFar = this.sgStorage
      .add(
         new BoolSetting.Builder()
            .name("forget-far")
            .description(
               "Forget blocks once you are further than render-distance away and their chunk has unloaded. They come back when you return and the server sends the chunk again. Keeps the map small and fast; with remember on, only what is near you gets saved."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> remember = this.sgStorage
      .add(
         new BoolSetting.Builder()
            .name("remember")
            .description("Save the map per server and dimension, and reload it when you come back.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> autosaveSeconds = this.sgStorage
      .add(
         new IntSetting.Builder()
            .name("autosave-seconds")
            .description("Write the map to disk this often, so a crash or force-quit never loses it. 0 disables.")
            .defaultValue(60)
            .min(0)
            .sliderRange(0, 600)
            .visible(this.remember::get)
            .build()
      );
   private final RevealStore store = new RevealStore();
   private final Color tracerColor = new Color();
   private final Color villagerTracerColor = new Color();
   private volatile Set<Block> trackedSet = Set.of();
   private long nextAutosaveMs;
   private static final BaseEsp.Tint[] TINTS = BaseEsp.Tint.values();
   private final Color[] tintSides = colors();
   private final Color[] tintLines = colors();
   private final Color[] tintTracers = colors();
   private ExecutorService scanner;
   private long[] snapshot = new long[0];
   private Block[] snapshotBlocks = new Block[0];
   private long[] frameSnapshot = new long[0];
   private long[] standSnapshot = new long[0];
   private String scope;
   private final Map<Long, BaseEsp.FrameInfo> frameInfo = new HashMap<>();
   private final Vector3d labelPos = new Vector3d();
   private static final Color LABEL_TITLE = new Color(255, 255, 255, 255);
   private static final Color LABEL_OWNER = new Color(255, 200, 90, 255);
   private int[] drawX = new int[0];
   private int[] drawY = new int[0];
   private int[] drawZ = new int[0];
   private boolean[] drawTall = new boolean[0];
   private byte[] drawTint = new byte[0];
   private int drawCount;
   private boolean drawListStale = true;
   private double builtX;
   private double builtY;
   private double builtZ;
   private long[] candidateKey = new long[0];
   private boolean[] candidateTall = new boolean[0];
   private byte[] candidateTint = new byte[0];
   private float[] candidateDist = new float[0];
   private int[] order = new int[0];
   private static final Map<SoundEvent, Block> OPEN_SOUNDS = Map.of(
      SoundEvents.CHEST_OPEN,
      Blocks.CHEST,
      SoundEvents.CHEST_CLOSE,
      Blocks.CHEST,
      SoundEvents.BARREL_OPEN,
      Blocks.BARREL,
      SoundEvents.BARREL_CLOSE,
      Blocks.BARREL,
      SoundEvents.SHULKER_BOX_OPEN,
      Blocks.SHULKER_BOX,
      SoundEvents.SHULKER_BOX_CLOSE,
      Blocks.SHULKER_BOX,
      SoundEvents.ENDER_CHEST_OPEN,
      Blocks.ENDER_CHEST,
      SoundEvents.ENDER_CHEST_CLOSE,
      Blocks.ENDER_CHEST
   );

   private Setting<SettingColor> tint(String name, String what, SettingColor color) {
      return this.sgColors
         .add(
            new ColorSetting.Builder()
               .name(name)
               .description(what + " Outline and tracer; the fill is the same colour, as see-through as side-color.")
               .defaultValue(color)
               .visible(() -> this.multicolorEsp.get() || this.multicolorTracers.get())
               .build()
         );
   }

   private static Color[] colors() {
      Color[] out = new Color[TINTS.length];

      for (int i = 0; i < out.length; i++) {
         out[i] = new Color();
      }

      return out;
   }

   private static BaseEsp.Tint tintOf(Block block) {
      if (block == Blocks.CHEST) {
         return BaseEsp.Tint.CHEST;
      } else if (block == Blocks.TRAPPED_CHEST) {
         return BaseEsp.Tint.TRAPPED_CHEST;
      } else if (block == Blocks.BARREL) {
         return BaseEsp.Tint.BARREL;
      } else if (block == Blocks.ENDER_CHEST) {
         return BaseEsp.Tint.ENDER_CHEST;
      } else if (block instanceof ShulkerBoxBlock) {
         return BaseEsp.Tint.SHULKER;
      } else if (block == Blocks.HOPPER) {
         return BaseEsp.Tint.HOPPER;
      } else if (block == Blocks.DISPENSER || block == Blocks.DROPPER || block == Blocks.CRAFTER) {
         return BaseEsp.Tint.DISPENSER;
      } else if (block == Blocks.SPAWNER) {
         return BaseEsp.Tint.SPAWNER;
      } else if (block == Blocks.FURNACE || block == Blocks.SMOKER || block == Blocks.BLAST_FURNACE) {
         return BaseEsp.Tint.FURNACE;
      } else {
         return block == Blocks.ENCHANTING_TABLE ? BaseEsp.Tint.ENCHANTING : BaseEsp.Tint.REDSTONE;
      }
   }

   public BaseEsp() {
      super(
         SpecterClientAddon.ESP,
         "base-esp",
         "Remembers every chest, redstone component, item frame and armor stand the server shows you and outlines them - even after the server hides them again. Undetectable."
      );
      this.rebuildTracked();
   }

   private void rebuildTracked() {
      Set<Block> set = new ReferenceOpenHashSet();
      if (this.storage.get()) {
         set.addAll(STORAGE_BLOCKS);
      }

      if (this.redstone.get()) {
         set.addAll(REDSTONE_BLOCKS);
      }

      this.trackedSet = set;
      this.store.retainBlocks(set);
   }

   @Override
   public void onActivate() {
      this.scope = null;
      this.store.clear();
      this.frameInfo.clear();
      this.refreshSnapshots();
      this.rebuildTracked();
      this.drawCount = 0;
      this.nextAutosaveMs = System.currentTimeMillis() + 1000L * this.autosaveSeconds.get().intValue();
      this.scanner = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "specter-base-esp");
         thread.setDaemon(true);
         return thread;
      });
   }

   @Override
   public void onDeactivate() {
      if (this.scanner != null) {
         this.scanner.shutdownNow();
         this.scanner = null;
      }

      this.saveIfRemembering();
      this.scope = null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.saveIfRemembering();
      this.scope = null;
      this.store.clear();
      this.frameInfo.clear();
      this.drawCount = 0;
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      LevelChunk chunk = event.chunk();
      if (this.scanner != null) {
         try {
            this.scanner.submit(() -> this.captureChunk(chunk));
         } catch (RejectedExecutionException var4) {
         }
      }
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (this.storage.get() || this.redstone.get()) {
         this.take(event.packet);
      }
   }

   private void take(Packet<?> packet) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> inner : bundle.subPackets()) {
            this.take(inner);
         }
      } else if (packet instanceof ClientboundLevelChunkWithLightPacket data) {
         data.chunkData().getBlockEntitiesTagsConsumer(data.x(), data.z()).accept((pos, type, nbt) -> this.recordEntity(pos, type));
      } else if (packet instanceof ClientboundBlockEntityDataPacket update) {
         this.recordEntity(update.getPos(), update.getType());
      } else if (packet instanceof ClientboundBlockEventPacket lid && this.openedStorage.get()) {
         Block block = storageOf(lid.getBlock());
         if (block != null) {
            this.recordOpened(lid.getPos(), block);
         }
      } else if (packet instanceof ClientboundSoundPacket sound && this.openedStorage.get()) {
         Block block = OPEN_SOUNDS.get(sound.getSound().value());
         if (block != null) {
            this.recordOpened(BlockPos.containing(sound.getX(), sound.getY(), sound.getZ()), block);
         }
      }
   }

   private static Block storageOf(Block block) {
      if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST) {
         return block;
      } else {
         return block instanceof ShulkerBoxBlock ? Blocks.SHULKER_BOX : null;
      }
   }

   private void recordOpened(BlockPos pos, Block block) {
      BlockPos at = pos.immutable();
      this.mc
         .execute(
            () -> {
               if (this.mc.level != null && this.trackedSet.contains(block)) {
                  Block shown = this.mc.level.getBlockState(at).getBlock();
                  boolean sameKind = shown == block
                     || block == Blocks.SHULKER_BOX && shown instanceof ShulkerBoxBlock
                     || block == Blocks.CHEST && shown == Blocks.TRAPPED_CHEST;
                  if (!sameKind) {
                     this.store.put(at.asLong(), block);
                  }
               }
            }
         );
   }

   private void recordEntity(BlockPos pos, BlockEntityType<?> type) {
      Block block = blockOf(type);
      if (block != null && this.trackedSet.contains(block)) {
         this.store.put(pos.asLong(), block);
      }
   }

   private static Block blockOf(BlockEntityType<?> type) {
      if (type == BlockEntityType.CHEST) {
         return Blocks.CHEST;
      } else if (type == BlockEntityType.TRAPPED_CHEST) {
         return Blocks.TRAPPED_CHEST;
      } else if (type == BlockEntityType.ENDER_CHEST) {
         return Blocks.ENDER_CHEST;
      } else if (type == BlockEntityType.SHULKER_BOX) {
         return Blocks.SHULKER_BOX;
      } else if (type == BlockEntityType.BARREL) {
         return Blocks.BARREL;
      } else if (type == BlockEntityType.HOPPER) {
         return Blocks.HOPPER;
      } else if (type == BlockEntityType.MOB_SPAWNER) {
         return Blocks.SPAWNER;
      } else if (type == BlockEntityType.FURNACE) {
         return Blocks.FURNACE;
      } else if (type == BlockEntityType.SMOKER) {
         return Blocks.SMOKER;
      } else if (type == BlockEntityType.BLAST_FURNACE) {
         return Blocks.BLAST_FURNACE;
      } else if (type == BlockEntityType.ENCHANTING_TABLE) {
         return Blocks.ENCHANTING_TABLE;
      } else if (type == BlockEntityType.DISPENSER) {
         return Blocks.DISPENSER;
      } else if (type == BlockEntityType.DROPPER) {
         return Blocks.DROPPER;
      } else {
         return type == BlockEntityType.CRAFTER ? Blocks.CRAFTER : null;
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      Block block = event.newState.getBlock();
      long pos = event.pos.asLong();
      if (this.trackedSet.contains(block)) {
         this.store.put(pos, block);
      } else if (this.removeMined.get()) {
         this.store.remove(pos);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         String current = Scope.current(this.mc);
         if (!current.equals(this.scope)) {
            this.saveIfRemembering();
            this.store.clear();
            this.frameInfo.clear();
            this.scope = current;
            this.loadIfRemembering();
         }

         if (this.remember.get() && this.autosaveSeconds.get() > 0 && System.currentTimeMillis() >= this.nextAutosaveMs) {
            this.nextAutosaveMs = System.currentTimeMillis() + 1000L * this.autosaveSeconds.get().intValue();
            if (this.store.total() > 0) {
               this.save();
            }
         }

         boolean frames = this.itemFrames.get();
         boolean stands = this.armorStands.get();
         if ((frames || stands) && this.mc.level.getGameTime() % 20L == 0L) {
            for (Entity entity : this.mc.level.entitiesForRendering()) {
               if (frames && entity instanceof ItemFrame frame) {
                  this.store.putMarker(RevealStore.Marker.ITEM_FRAME, entity.blockPosition().asLong());
                  if (this.frameLabels.get()) {
                     this.noteFrame(frame);
                  }
               } else if (stands && entity instanceof ArmorStand stand && !stand.isInvisible()) {
                  this.store.putMarker(RevealStore.Marker.ARMOR_STAND, entity.blockPosition().asLong());
               }
            }
         }

         if (this.forgetFar.get() && this.mc.level.getGameTime() % 40L == 0L) {
            this.forgetFar();
         }

         if (this.store.isDirty()) {
            this.refreshSnapshots();
         }

         if (this.drawListStale || this.mc.player.distanceToSqr(this.builtX, this.builtY, this.builtZ) > 16.0 || this.mc.level.getGameTime() % 10L == 0L) {
            this.buildDrawList();
         }
      }
   }

   private void forgetFar() {
      double px = this.mc.player.getX();
      double pz = this.mc.player.getZ();
      double radiusSq = (double)this.renderDistance.get().intValue() * this.renderDistance.get().intValue();
      ChunkSource chunks = this.mc.level.getChunkSource();
      this.store.removeIf(pos -> {
         int x = BlockPos.getX(pos);
         int z = BlockPos.getZ(pos);
         double dx = x + 0.5 - px;
         double dz = z + 0.5 - pz;
         return dx * dx + dz * dz > radiusSq && !chunks.hasChunk(x >> 4, z >> 4);
      });
      this.frameInfo.keySet().removeIf(pos -> {
         int x = BlockPos.getX(pos);
         int z = BlockPos.getZ(pos);
         double dx = x + 0.5 - px;
         double dz = z + 0.5 - pz;
         return dx * dx + dz * dz > radiusSq && !chunks.hasChunk(x >> 4, z >> 4);
      });
   }

   private void noteFrame(ItemFrame frame) {
      long pos = frame.blockPosition().asLong();
      ItemStack stack = frame.getItem();
      if (stack.isEmpty()) {
         this.frameInfo.remove(pos);
      } else {
         String owner = null;
         CustomData data = stack.get(DataComponents.CUSTOM_DATA);
         if (data != null && !data.isEmpty()) {
            CompoundTag bukkit = data.copyTag().getCompoundOrEmpty("PublicBukkitValues");
            owner = bukkit.getString("minecraft:gownerctid").orElse(null);
            if (owner == null) {
               for (String key : bukkit.keySet()) {
                  if (key.toLowerCase(Locale.ROOT).contains("owner")) {
                     owner = bukkit.getString(key).orElse(null);
                     if (owner != null) {
                        break;
                     }
                  }
               }
            }
         }

         this.frameInfo.put(pos, new BaseEsp.FrameInfo(stack.getHoverName().getString(), owner));
      }
   }

   @EventHandler
   private void onRenderFrameLabels(Render2DEvent event) {
      if (this.itemFrames.get() && this.frameLabels.get() && !this.frameInfo.isEmpty() && this.mc.player != null) {
         TextRenderer text = TextRenderer.get();
         double maxSq = (double)this.frameLabelDistance.get().intValue() * this.frameLabelDistance.get().intValue();

         for (Entry<Long, BaseEsp.FrameInfo> e : this.frameInfo.entrySet()) {
            long pos = e.getKey();
            double x = BlockPos.getX(pos) + 0.5;
            double y = BlockPos.getY(pos) + 0.75;
            double z = BlockPos.getZ(pos) + 0.5;
            if (!(this.mc.player.distanceToSqr(x, y, z) > maxSq)) {
               this.labelPos.set(x, y, z);
               if (NametagUtils.to2D(this.labelPos, 1.0)) {
                  BaseEsp.FrameInfo info = e.getValue();
                  NametagUtils.begin(this.labelPos);
                  text.begin();
                  double h = text.getHeight(true);
                  text.render(info.item(), -text.getWidth(info.item(), true) / 2.0, info.owner() == null ? -h : -h * 2.0, LABEL_TITLE, true);
                  if (info.owner() != null) {
                     String owner = "owner " + info.owner();
                     text.render(owner, -text.getWidth(owner, true) / 2.0, -h, LABEL_OWNER, true);
                  }

                  text.end();
                  NametagUtils.end();
               }
            }
         }
      }
   }

   private void captureChunk(LevelChunk chunk) {
      Set<Block> tracking = this.trackedSet;
      LevelChunkSection[] sections = chunk.getSections();
      int startX = chunk.getPos().getMinBlockX();
      int startZ = chunk.getPos().getMinBlockZ();

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (!section.hasOnlyAir() && section.maybeHas(state -> tracking.contains(state.getBlock()))) {
            int baseY = chunk.getSectionYFromSectionIndex(i) * 16;

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     Block block = section.getBlockState(x, y, z).getBlock();
                     if (tracking.contains(block)) {
                        this.store.put(BlockPos.asLong(startX + x, baseY + y, startZ + z), block);
                     }
                  }
               }
            }
         }
      }
   }

   private void refreshSnapshots() {
      RevealStore.Snapshot blocks = this.store.blockSnapshot();
      this.snapshot = blocks.positions();
      this.snapshotBlocks = blocks.blocks();
      this.frameSnapshot = this.store.markerSnapshot(RevealStore.Marker.ITEM_FRAME);
      this.standSnapshot = this.store.markerSnapshot(RevealStore.Marker.ARMOR_STAND);
      this.store.clearDirty();
      this.drawListStale = true;
   }

   private void buildDrawList() {
      this.drawListStale = false;
      if (this.mc.player == null) {
         this.drawCount = 0;
      } else {
         this.builtX = this.mc.player.getX();
         this.builtY = this.mc.player.getY();
         this.builtZ = this.mc.player.getZ();
         double eyeY = this.mc.player.getEyeY();
         double maxSq = (double)this.renderDistance.get().intValue() * this.renderDistance.get().intValue();
         int capacity = this.snapshot.length + this.frameSnapshot.length + this.standSnapshot.length;
         if (this.candidateKey.length < capacity) {
            this.candidateKey = new long[capacity];
            this.candidateTall = new boolean[capacity];
            this.candidateTint = new byte[capacity];
            this.candidateDist = new float[capacity];
            this.order = new int[capacity];
         }

         int n = 0;
         n = this.collect(this.snapshot, this.snapshotBlocks, BaseEsp.Tint.REDSTONE, false, eyeY, maxSq, n);
         if (this.itemFrames.get()) {
            n = this.collect(this.frameSnapshot, null, BaseEsp.Tint.ENTITY, false, eyeY, maxSq, n);
         }

         if (this.armorStands.get()) {
            n = this.collect(this.standSnapshot, null, BaseEsp.Tint.ENTITY, true, eyeY, maxSq, n);
         }

         int count = Math.min(n, this.maxRender.get());
         int i = 0;

         while (i < n) {
            this.order[i] = i++;
         }

         if (n > count) {
            float[] dist = this.candidateDist;
            IntArrays.quickSort(this.order, 0, n, (a, b) -> Float.compare(dist[a], dist[b]));
         }

         if (this.drawX.length < count) {
            this.drawX = new int[count];
            this.drawY = new int[count];
            this.drawZ = new int[count];
            this.drawTall = new boolean[count];
            this.drawTint = new byte[count];
         }

         for (int ix = 0; ix < count; ix++) {
            int c = this.order[ix];
            long key = this.candidateKey[c];
            this.drawX[ix] = BlockPos.getX(key);
            this.drawY[ix] = BlockPos.getY(key);
            this.drawZ[ix] = BlockPos.getZ(key);
            this.drawTall[ix] = this.candidateTall[c];
            this.drawTint[ix] = this.candidateTint[c];
         }

         this.drawCount = count;
      }
   }

   private int collect(long[] positions, Block[] kinds, BaseEsp.Tint tint, boolean tall, double eyeY, double maxSq, int n) {
      for (int i = 0; i < positions.length; i++) {
         long key = positions[i];
         double dx = BlockPos.getX(key) + 0.5 - this.builtX;
         double dy = BlockPos.getY(key) + (tall ? 1.0 : 0.5) - eyeY;
         double dz = BlockPos.getZ(key) + 0.5 - this.builtZ;
         double distSq = dx * dx + dy * dy + dz * dz;
         if (!(distSq > maxSq)) {
            this.candidateKey[n] = key;
            this.candidateTall[n] = tall;
            this.candidateTint[n] = (byte)(kinds != null && i < kinds.length ? tintOf(kinds[i]) : tint).ordinal();
            this.candidateDist[n] = (float)distSq;
            n++;
         }
      }

      return n;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.villagers.get()) {
         this.renderVillagers(event);
      }

      if (this.drawCount != 0) {
         SettingColor sides = this.sideColor.get();
         SettingColor lines = this.lineColor.get();
         ShapeMode mode = this.shapeMode.get();
         Vec3 from = RenderUtils.center;
         boolean drawTracers = this.tracers.get() && from != null;
         int tracerTopY = this.tracerYLimit.get() ? this.tracerMaxY.get() : 2147483647;
         this.tracerColor.set((Color)lines);
         this.tracerColor.a = lines.a * this.tracerOpacity.get() / 100;
         boolean multiBoxes = this.multicolorEsp.get();
         boolean multiTracers = this.multicolorTracers.get();
         if (multiBoxes || multiTracers) {
            for (int t = 0; t < TINTS.length; t++) {
               SettingColor color = this.tintSettings.get(t).get();
               this.tintLines[t].set((Color)color);
               this.tintSides[t].set((Color)color);
               this.tintSides[t].a = sides.a;
               this.tintTracers[t].set((Color)color);
               this.tintTracers[t].a = color.a * this.tracerOpacity.get() / 100;
            }
         }

         if (!multiTracers && this.tracerColor.a == 0) {
            drawTracers = false;
         }

         Camera camera = this.mc.gameRenderer.mainCamera();
         Vec3 eye = camera.position();
         double yaw = Math.toRadians(camera.yRot());
         double pitch = Math.toRadians(camera.xRot());
         double lookX = -Math.sin(yaw) * Math.cos(pitch);
         double lookY = -Math.sin(pitch);
         double lookZ = Math.cos(yaw) * Math.cos(pitch);

         for (int i = 0; i < this.drawCount; i++) {
            int x = this.drawX[i];
            int y = this.drawY[i];
            int z = this.drawZ[i];
            int height = this.drawTall[i] ? 2 : 1;
            double centerY = y + height / 2.0;
            int t = this.drawTint[i];
            Color tracer = multiTracers ? this.tintTracers[t] : this.tracerColor;
            if (drawTracers && y <= tracerTopY && tracer.a > 0) {
               event.renderer.line(from.x, from.y, from.z, x + 0.5, centerY, z + 0.5, tracer);
            }

            double ahead = (x + 0.5 - eye.x) * lookX + (centerY - eye.y) * lookY + (z + 0.5 - eye.z) * lookZ;
            if (!(ahead < -2.0)) {
               if (multiBoxes) {
                  event.renderer.box(x, y, z, x + 1, y + height, z + 1, this.tintSides[t], this.tintLines[t], mode, 0);
               } else {
                  event.renderer.box(x, y, z, x + 1, y + height, z + 1, sides, lines, mode, 0);
               }
            }
         }
      }
   }

   private void renderVillagers(Render3DEvent event) {
      if (this.mc.level != null && this.mc.player != null) {
         SettingColor sides = this.villagerSideColor.get();
         SettingColor lines = this.villagerLineColor.get();
         ShapeMode mode = this.shapeMode.get();
         Vec3 from = RenderUtils.center;
         boolean drawTracers = this.tracers.get() && from != null;
         int tracerTopY = this.tracerYLimit.get() ? this.tracerMaxY.get() : 2147483647;
         this.villagerTracerColor.set((Color)lines);
         this.villagerTracerColor.a = lines.a * this.tracerOpacity.get() / 100;
         if (this.villagerTracerColor.a == 0) {
            drawTracers = false;
         }

         double maxSq = (double)this.renderDistance.get().intValue() * this.renderDistance.get().intValue();

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof Villager && !(entity.distanceToSqr(this.mc.player) > maxSq)) {
               Vec3 pos = entity.getPosition(event.tickDelta);
               double half = entity.getBbWidth() / 2.0F;
               double height = entity.getBbHeight();
               event.renderer.box(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + height, pos.z + half, sides, lines, mode, 0);
               if (drawTracers && pos.y <= tracerTopY) {
                  event.renderer.line(from.x, from.y, from.z, pos.x, pos.y + height / 2.0, pos.z, this.villagerTracerColor);
               }
            }
         }
      }
   }

   public File file() {
      return this.scope == null ? null : new File(new File(new File(SpecterCore.FOLDER, "specter"), "reveals"), this.scope + ".csv");
   }

   public void saveIfRemembering() {
      if (this.remember.get()) {
         this.save();
      }
   }

   private void loadIfRemembering() {
      if (this.remember.get()) {
         this.load();
      }
   }

   public int save() {
      File file = this.file();
      if (file == null) {
         return -1;
      } else {
         try {
            this.store.save(file);
            return this.store.total();
         } catch (IOException var3) {
            this.error("Could not save the base map: %s", new Object[]{var3.getMessage()});
            return -1;
         }
      }
   }

   public int load() {
      File file = this.file();
      if (file == null) {
         return -1;
      } else {
         try {
            int loaded = this.store.load(file);
            this.store.retainBlocks(this.trackedSet);
            this.refreshSnapshots();
            return loaded;
         } catch (IOException var3) {
            this.error("Could not load the base map: %s", new Object[]{var3.getMessage()});
            return -1;
         }
      }
   }

   public void reset() {
      this.store.clear();
      this.frameInfo.clear();
      this.refreshSnapshots();
   }

   public RevealStore store() {
      return this.store;
   }

   public String scope() {
      return this.scope;
   }

   private record FrameInfo(String item, String owner) {
   }

   private static enum Tint {
      CHEST,
      TRAPPED_CHEST,
      BARREL,
      SHULKER,
      ENDER_CHEST,
      HOPPER,
      DISPENSER,
      REDSTONE,
      ENTITY,
      SPAWNER,
      FURNACE,
      ENCHANTING;
   }
}

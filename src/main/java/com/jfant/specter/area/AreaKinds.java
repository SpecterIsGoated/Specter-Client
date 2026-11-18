package com.jfant.specter.area;

import com.jfant.specter.scan.BlockKinds;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class AreaKinds {
   public static final AreaKinds.Kind[] KINDS = AreaKinds.Kind.values();
   public static final long LUSH = 1L;
   public static final long MANGROVE = 2L;
   public static final long DEEP_DARK = 4L;
   public static final long CHERRY = 8L;
   public static final long PALE = 16L;
   public static final long BADLANDS = 32L;
   public static final long DESERT = 64L;
   public static final long MUSHROOM = 128L;
   public static final long DARK_FOREST = 256L;
   public static final long SWAMP = 512L;
   public static final long JUNGLE = 1024L;
   public static final long SAVANNA = 2048L;
   public static final long COLD = 4096L;
   public static final long TAIGA = 8192L;
   public static final long OLD_TAIGA = 16384L;
   public static final long WINDSWEPT = 32768L;
   public static final long WARM_OCEAN = 65536L;
   public static final long SUNFLOWER = 131072L;
   public static final long BEE = 262144L;
   public static final long NETHER = 524288L;
   public static final long END = 1048576L;
   private static final Map<String, Long> BIOME_GROUPS = new HashMap<>();
   private static final Map<Holder<Biome>, Long> biomeCache = new IdentityHashMap<>();
   private static final Set<String> NATURAL = Set.of(
      "air",
      "cave_air",
      "void_air",
      "water",
      "lava",
      "bubble_column",
      "fire",
      "soul_fire",
      "stone",
      "granite",
      "diorite",
      "andesite",
      "deepslate",
      "tuff",
      "calcite",
      "dripstone_block",
      "pointed_dripstone",
      "bedrock",
      "dirt",
      "coarse_dirt",
      "grass_block",
      "clay",
      "gravel",
      "sand",
      "snow",
      "obsidian",
      "crying_obsidian",
      "magma_block",
      "amethyst_block",
      "budding_amethyst",
      "amethyst_cluster",
      "large_amethyst_bud",
      "medium_amethyst_bud",
      "small_amethyst_bud",
      "smooth_basalt",
      "raw_iron_block",
      "raw_copper_block",
      "glow_lichen",
      "bone_block",
      "short_grass",
      "tall_grass",
      "fern",
      "large_fern",
      "bush",
      "firefly_bush",
      "leaf_litter",
      "wildflowers",
      "sugar_cane",
      "vine",
      "kelp",
      "kelp_plant",
      "seagrass",
      "tall_seagrass",
      "dandelion",
      "poppy",
      "allium",
      "azure_bluet",
      "red_tulip",
      "orange_tulip",
      "white_tulip",
      "pink_tulip",
      "oxeye_daisy",
      "cornflower",
      "lily_of_the_valley",
      "lilac",
      "rose_bush",
      "peony",
      "brown_mushroom",
      "red_mushroom",
      "oak_log",
      "spruce_log",
      "birch_log",
      "pumpkin",
      "turtle_egg",
      "frogspawn",
      "end_portal_frame",
      "end_portal"
   );
   private static final Set<String> STRUCTURE = Set.of(
      "prismarine",
      "prismarine_bricks",
      "dark_prismarine",
      "prismarine_stairs",
      "prismarine_slab",
      "prismarine_wall",
      "prismarine_brick_stairs",
      "prismarine_brick_slab",
      "dark_prismarine_stairs",
      "dark_prismarine_slab",
      "sea_lantern",
      "sponge",
      "wet_sponge",
      "mossy_cobblestone",
      "mossy_cobblestone_stairs",
      "mossy_cobblestone_slab",
      "mossy_cobblestone_wall",
      "mossy_stone_bricks",
      "mossy_stone_brick_stairs",
      "mossy_stone_brick_slab",
      "mossy_stone_brick_wall",
      "cracked_stone_bricks",
      "chiseled_stone_bricks",
      "spawner",
      "trial_spawner",
      "vault",
      "decorated_pot",
      "suspicious_sand",
      "suspicious_gravel",
      "cobweb",
      "heavy_core",
      "mud_bricks",
      "cracked_deepslate_bricks",
      "cracked_deepslate_tiles",
      "chiseled_deepslate",
      "reinforced_deepslate",
      "chiseled_tuff",
      "chiseled_tuff_bricks"
   );
   private static final Map<String, Long> GATED = new HashMap<>();
   private static final byte NATURAL_CODE = -1;
   private static final byte GATED_CODE = 127;
   private static byte[] codes;
   private static long[] gates;
   private static final Long ALWAYS;

   private static void group(long bit, String... biomes) {
      for (String biome : biomes) {
         BIOME_GROUPS.merge(biome, bit, (a, b) -> a | b);
      }
   }

   public static long biomeBits(Holder<Biome> biome) {
      Long bits = biomeCache.get(biome);
      if (bits == null) {
         bits = biome.unwrapKey().map(key -> BIOME_GROUPS.getOrDefault(key.identifier().getPath(), 0L)).orElse(0L);
         biomeCache.put(biome, bits);
      }

      return bits;
   }

   private static void gate(long biomes, String... blocks) {
      for (String block : blocks) {
         GATED.put(block, biomes);
      }
   }

   private AreaKinds() {
   }

   public static void build() {
      int size = Block.BLOCK_STATE_REGISTRY.size();
      byte[] c = new byte[size];
      long[] g = new long[size];
      Arrays.fill(c, (byte)-1);

      for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
         int id = Block.getId(state);
         if (id >= 0 && id < size) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            AreaKinds.Kind kind = named(state);
            if (kind != null) {
               c[id] = (byte)kind.ordinal();
            } else {
               Long gate = gateOf(state, path);
               if (gate != null) {
                  c[id] = 127;
                  g[id] = gate;
               } else if (!naturalOrStructure(state, path)) {
                  c[id] = (byte)AreaKinds.Kind.BUILT.ordinal();
               }
            }
         }
      }

      codes = c;
      gates = g;
      biomeCache.clear();
   }

   public static boolean maybeFlag(BlockState state) {
      int id = Block.getId(state);
      byte[] c = codes;
      return id >= 0 && id < c.length && c[id] != -1;
   }

   public static byte flag(BlockState state, long biomes) {
      int id = Block.getId(state);
      byte[] c = codes;
      if (id >= 0 && id < c.length) {
         byte code = c[id];
         if (code == -1) {
            return -1;
         } else if (code == 127) {
            return (gates[id] & biomes) != 0L ? -1 : (byte)AreaKinds.Kind.MISPLACED.ordinal();
         } else {
            return code;
         }
      } else {
         return -1;
      }
   }

   private static Long gateOf(BlockState state, String path) {
      if (state.getBlock() instanceof LeavesBlock) {
         return state.getOptionalValue(LeavesBlock.PERSISTENT).orElse(false) ? null : GATED.getOrDefault(path, ALWAYS);
      } else {
         return GATED.get(path);
      }
   }

   private static boolean naturalOrStructure(BlockState state, String path) {
      if (state.getBlock() instanceof LeavesBlock) {
         return false;
      } else if (NATURAL.contains(path) || STRUCTURE.contains(path)) {
         return true;
      } else {
         return path.contains("sandstone") || path.startsWith("infested_") ? true : path.endsWith("_ore");
      }
   }

   private static AreaKinds.Kind named(BlockState state) {
      Block block = state.getBlock();
      if (block instanceof ShulkerBoxBlock) {
         return AreaKinds.Kind.SHULKER;
      } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
         return AreaKinds.Kind.CHEST;
      } else if (block == Blocks.BARREL) {
         return AreaKinds.Kind.BARREL;
      } else if (block == Blocks.HOPPER) {
         return AreaKinds.Kind.HOPPER;
      } else if (block == Blocks.DISPENSER || block == Blocks.DROPPER || block == Blocks.CRAFTER) {
         return AreaKinds.Kind.DISPENSER;
      } else if (block == Blocks.BEACON) {
         return AreaKinds.Kind.BEACON;
      } else if (block == Blocks.ENCHANTING_TABLE) {
         return AreaKinds.Kind.ENCHANTING;
      } else if (block instanceof AnvilBlock) {
         return AreaKinds.Kind.ANVIL;
      } else if (block == Blocks.BREWING_STAND) {
         return AreaKinds.Kind.BREWING;
      } else if (block == Blocks.IRON_BLOCK
         || block == Blocks.GOLD_BLOCK
         || block == Blocks.DIAMOND_BLOCK
         || block == Blocks.EMERALD_BLOCK
         || block == Blocks.NETHERITE_BLOCK) {
         return AreaKinds.Kind.MINERAL;
      } else if (block == Blocks.BELL) {
         return AreaKinds.Kind.BELL;
      } else if (block == Blocks.DIRT_PATH) {
         return AreaKinds.Kind.PATH;
      } else if (block instanceof BedBlock) {
         return AreaKinds.Kind.BED;
      } else if (block == Blocks.CRAFTING_TABLE) {
         return AreaKinds.Kind.CRAFTING;
      } else if (block == Blocks.FURNACE || block == Blocks.SMOKER || block == Blocks.BLAST_FURNACE) {
         return AreaKinds.Kind.FURNACE;
      } else if (block instanceof SignBlock) {
         return AreaKinds.Kind.SIGN;
      } else if (block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock) {
         return AreaKinds.Kind.STAINED_GLASS;
      } else if (block == Blocks.GLASS || block == Blocks.GLASS_PANE || block == Blocks.TINTED_GLASS) {
         return AreaKinds.Kind.GLASS;
      } else if (!state.is(BlockTags.WOOL) && !(block instanceof CarpetBlock)) {
         String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
         if (path.endsWith("_concrete") || path.endsWith("_glazed_terracotta")) {
            return AreaKinds.Kind.CONCRETE;
         } else {
            return BlockKinds.isRedstonePart(state) && block != Blocks.REDSTONE_WIRE ? AreaKinds.Kind.REDSTONE : null;
         }
      } else {
         return AreaKinds.Kind.WOOL;
      }
   }

   static {
      group(1L, "lush_caves");
      group(2L, "mangrove_swamp");
      group(4L, "deep_dark");
      group(8L, "cherry_grove");
      group(16L, "pale_garden");
      group(32L, "badlands", "eroded_badlands", "wooded_badlands");
      group(64L, "desert");
      group(128L, "mushroom_fields");
      group(256L, "dark_forest");
      group(512L, "swamp");
      group(1024L, "jungle", "sparse_jungle", "bamboo_jungle");
      group(2048L, "savanna", "savanna_plateau", "windswept_savanna");
      group(
         4096L,
         "snowy_plains",
         "ice_spikes",
         "snowy_taiga",
         "snowy_beach",
         "snowy_slopes",
         "frozen_peaks",
         "jagged_peaks",
         "grove",
         "frozen_river",
         "frozen_ocean",
         "deep_frozen_ocean"
      );
      group(8192L, "taiga");
      group(16384L, "old_growth_pine_taiga", "old_growth_spruce_taiga");
      group(32768L, "windswept_hills", "windswept_gravelly_hills", "windswept_forest");
      group(65536L, "warm_ocean");
      group(131072L, "sunflower_plains");
      group(262144L, "plains", "flower_forest", "meadow", "forest", "birch_forest", "old_growth_birch_forest");
      group(524288L, "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas");
      group(1048576L, "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens");
      gate(
         1L,
         "moss_block",
         "moss_carpet",
         "azalea",
         "flowering_azalea",
         "rooted_dirt",
         "hanging_roots",
         "spore_blossom",
         "big_dripleaf",
         "big_dripleaf_stem",
         "small_dripleaf",
         "cave_vines",
         "cave_vines_plant",
         "azalea_leaves",
         "flowering_azalea_leaves"
      );
      gate(2L, "mud", "mangrove_log", "mangrove_roots", "muddy_mangrove_roots", "mangrove_propagule", "mangrove_leaves");
      gate(4L, "sculk", "sculk_vein", "sculk_sensor", "sculk_shrieker", "sculk_catalyst");
      gate(8L, "cherry_log", "cherry_leaves", "pink_petals");
      gate(
         16L,
         "pale_oak_log",
         "pale_oak_leaves",
         "pale_moss_block",
         "pale_moss_carpet",
         "pale_hanging_moss",
         "creaking_heart",
         "open_eyeblossom",
         "closed_eyeblossom",
         "resin_clump"
      );
      gate(272L, "dark_oak_log", "dark_oak_leaves");
      gate(32L, "red_sand");
      gate(128L, "mycelium");
      gate(896L, "red_mushroom_block", "brown_mushroom_block", "mushroom_stem");
      gate(17408L, "podzol");
      gate(1024L, "jungle_log", "jungle_leaves", "cocoa", "melon", "bamboo", "bamboo_sapling");
      gate(2048L, "acacia_log", "acacia_leaves");
      gate(4096L, "packed_ice", "blue_ice", "snow_block", "powder_snow");
      gate(61440L, "ice");
      gate(96L, "cactus", "cactus_flower", "short_dry_grass", "tall_dry_grass");
      gate(27234L, "dead_bush");
      gate(514L, "lily_pad");
      gate(512L, "blue_orchid");
      gate(131072L, "sunflower");
      gate(28672L, "sweet_berry_bush");
      gate(393224L, "bee_nest");
      gate(65536L, "sea_pickle");

      for (String coral : List.of("tube", "brain", "bubble", "fire", "horn")) {
         gate(65536L, coral + "_coral", coral + "_coral_block", coral + "_coral_fan", coral + "_coral_wall_fan");
      }

      gate(
         524288L,
         "netherrack",
         "soul_sand",
         "soul_soil",
         "basalt",
         "blackstone",
         "gilded_blackstone",
         "glowstone",
         "crimson_nylium",
         "warped_nylium",
         "crimson_stem",
         "warped_stem",
         "nether_wart_block",
         "warped_wart_block",
         "shroomlight",
         "crimson_fungus",
         "warped_fungus",
         "crimson_roots",
         "warped_roots",
         "nether_sprouts",
         "weeping_vines",
         "weeping_vines_plant",
         "twisting_vines",
         "twisting_vines_plant",
         "nether_quartz_ore",
         "nether_gold_ore",
         "ancient_debris",
         "nether_bricks",
         "nether_brick_fence",
         "nether_brick_stairs",
         "nether_brick_slab",
         "nether_wart",
         "polished_blackstone_bricks",
         "cracked_polished_blackstone_bricks",
         "chiseled_polished_blackstone",
         "polished_blackstone",
         "polished_blackstone_brick_stairs",
         "polished_blackstone_brick_slab",
         "polished_blackstone_brick_wall",
         "blackstone_stairs",
         "blackstone_slab",
         "blackstone_wall",
         "dried_ghast"
      );
      gate(
         1048576L,
         "end_stone",
         "end_stone_bricks",
         "end_stone_brick_stairs",
         "end_stone_brick_slab",
         "end_stone_brick_wall",
         "purpur_block",
         "purpur_pillar",
         "purpur_stairs",
         "purpur_slab",
         "chorus_plant",
         "chorus_flower",
         "end_rod",
         "dragon_egg",
         "end_gateway",
         "dragon_head",
         "dragon_wall_head"
      );
      codes = new byte[0];
      gates = new long[0];
      ALWAYS = -1L;
      gate(96L, "terracotta");

      for (String color : List.of(
         "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
      )) {
         gate(96L, color + "_terracotta");
      }
   }

   public static enum Kind {
      SHULKER("shulker", "shulkers", 6.0, 0, false, true),
      CHEST("chest", "chests", 3.0, 0, true, true),
      BARREL("barrel", "barrels", 3.0, 0, true, true),
      HOPPER("hopper", "hoppers", 2.0, 30, false, true),
      DISPENSER("dispenser", "dispensers", 2.0, 20, false, true),
      BEACON("beacon", "beacons", 10.0, 3, false, true),
      ENCHANTING("enchanting table", "enchanting tables", 4.0, 2, false, true),
      ANVIL("anvil", "anvils", 3.0, 3, false, true),
      BREWING("brewing stand", "brewing stands", 3.0, 4, true, true),
      MINERAL("mineral block", "mineral blocks", 2.0, 10, false, false),
      CONCRETE("concrete", "concrete", 0.5, 30, false, false),
      REDSTONE("redstone part", "redstone parts", 0.5, 20, false, false),
      STAINED_GLASS("stained glass", "stained glass", 0.3, 20, false, false),
      GLASS("glass", "glass", 0.2, 20, true, false),
      WOOL("wool", "wool", 0.2, 20, true, false),
      BED("bed", "beds", 1.0, 6, true, false),
      CRAFTING("crafting table", "crafting tables", 1.0, 3, true, false),
      FURNACE("furnace", "furnaces", 1.0, 8, true, false),
      SIGN("sign", "signs", 1.0, 6, false, false),
      FRAME("item frame", "item frames", 2.0, 10, false, false),
      ARMOR_STAND("armor stand", "armor stands", 2.0, 6, false, false),
      BELL("bell", "bells", 0.0, 0, false, false),
      PATH("dirt path", "dirt paths", 0.0, 0, false, false),
      VILLAGER("villager", "villagers", 0.0, 0, false, false),
      BUILT("built block", "built blocks", 0.02, 250, true, false),
      MISPLACED("out-of-biome block", "out-of-biome blocks", 0.2, 25, false, false);

      public final String one;
      public final String many;
      public final double points;
      public final int cap;
      public final boolean village;
      public final boolean core;

      private Kind(String one, String many, double points, int cap, boolean village, boolean core) {
         this.one = one;
         this.many = many;
         this.points = points;
         this.cap = cap;
         this.village = village;
         this.core = core;
      }

      public boolean generic() {
         return this == BUILT || this == MISPLACED;
      }

      public double score(int count) {
         return (this.cap > 0 ? Math.min(count, this.cap) : count) * this.points;
      }

      public String describe(int count) {
         return count == 1 ? "1 " + this.one : count + " " + this.many;
      }
   }
}

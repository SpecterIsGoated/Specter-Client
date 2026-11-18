package com.jfant.specter.scan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class DripstoneScanner {
   private static final int MIN_OPEN_SIDES = 3;

   private DripstoneScanner() {
   }

   public static DripstoneScanner.Find[] scan(
      LevelChunk chunk, LevelChunk west, LevelChunk east, LevelChunk north, LevelChunk south, DripstoneScanner.Rule rule
   ) {
      DripstoneScanner.Find[] found = scan(chunk, west, east, north, south, rule.minStalactite(), rule.minStalagmite(), rule.minStone());
      int kept = 0;

      for (DripstoneScanner.Find find : found) {
         if (rule.keeps(find)) {
            found[kept++] = find;
         }
      }

      return kept == found.length ? found : Arrays.copyOf(found, kept);
   }

   public static DripstoneScanner.Find[] scan(
      LevelChunk chunk, LevelChunk west, LevelChunk east, LevelChunk north, LevelChunk south, int minStalactite, int minStalagmite, int minStone
   ) {
      LevelChunkSection[] sections = chunk.getSections();
      LevelChunkSection[] westSections = west != null ? west.getSections() : null;
      LevelChunkSection[] eastSections = east != null ? east.getSections() : null;
      LevelChunkSection[] northSections = north != null ? north.getSections() : null;
      LevelChunkSection[] southSections = south != null ? south.getSections() : null;
      int startX = chunk.getPos().getMinBlockX();
      int startZ = chunk.getPos().getMinBlockZ();
      int bottomY = chunk.getMinY();
      List<DripstoneScanner.Run> runs = new ArrayList<>();
      int[] run = new int[256];

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         int baseY = chunk.getSectionYFromSectionIndex(i) * 16;
         LevelChunkSection westSection = sectionAt(westSections, i);
         LevelChunkSection eastSection = sectionAt(eastSections, i);
         LevelChunkSection northSection = sectionAt(northSections, i);
         LevelChunkSection southSection = sectionAt(southSections, i);
         LevelChunkSection aboveSection = sectionAt(sections, i + 1);
         boolean candidate = !section.hasOnlyAir() && section.maybeHas(DripstoneScanner::isPointed)
            || section.maybeHas(BlockStateBase::isAir)
               && (
                  hasStillWater(section)
                     || hasStillWater(westSection)
                     || hasStillWater(eastSection)
                     || hasStillWater(northSection)
                     || hasStillWater(southSection)
                     || hasStillWater(aboveSection)
               );
         if (!candidate) {
            for (int column = 0; column < 256; column++) {
               if (run[column] > 0) {
                  runs.add(run(startX, startZ, column, baseY - 1, run[column]));
               }

               run[column] = 0;
            }
         } else {
            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     int column = z * 16 + x;
                     BlockState state = section.getBlockState(x, y, z);
                     if (!isPointed(state)
                        && (
                           !state.isAir()
                              || !isStillWaterAt(x > 0 ? section : westSection, x > 0 ? x - 1 : 15, y, z)
                                 && !isStillWaterAt(x < 15 ? section : eastSection, x < 15 ? x + 1 : 0, y, z)
                                 && !isStillWaterAt(z > 0 ? section : northSection, x, y, z > 0 ? z - 1 : 15)
                                 && !isStillWaterAt(z < 15 ? section : southSection, x, y, z < 15 ? z + 1 : 0)
                                 && !isStillWaterAt(y < 15 ? section : aboveSection, x, y < 15 ? y + 1 : 0, z)
                        )) {
                        if (run[column] > 0) {
                           runs.add(run(startX, startZ, column, baseY + y - 1, run[column]));
                           run[column] = 0;
                        }
                     } else {
                        run[column]++;
                     }
                  }
               }
            }
         }
      }

      int topY = bottomY + sections.length * 16 - 1;

      for (int column = 0; column < 256; column++) {
         if (run[column] > 0) {
            runs.add(run(startX, startZ, column, topY, run[column]));
         }
      }

      List<DripstoneScanner.Find> out = new ArrayList<>();
      if (minStone > 0) {
         findHangingStone(out, runs, sections, westSections, eastSections, northSections, southSections, bottomY, startX, startZ, minStone, minStalactite);
      }

      for (DripstoneScanner.Run r : runs) {
         if (!r.used) {
            closeRun(out, sections, bottomY, r, minStalactite, minStalagmite);
         }
      }

      return out.toArray(new DripstoneScanner.Find[0]);
   }

   private static DripstoneScanner.Run run(int startX, int startZ, int column, int top, int length) {
      return new DripstoneScanner.Run(startX + (column & 15), startZ + (column >> 4), top - length + 1, top);
   }

   private static void closeRun(
      List<DripstoneScanner.Find> out, LevelChunkSection[] sections, int bottomY, DripstoneScanner.Run r, int minStalactite, int minStalagmite
   ) {
      int x = r.x & 15;
      int z = r.z & 15;
      int top = r.top;
      int bottom = r.bottom;
      int length = top - bottom + 1;
      boolean ceiling = isSolid(stateAt(sections, bottomY, x, top + 1, z));
      boolean floor = isSolid(stateAt(sections, bottomY, x, bottom - 1, z));
      DripstoneScanner.Kind kind;
      if (ceiling && floor) {
         if (length < Math.min(minStalactite, minStalagmite)) {
            return;
         }

         kind = DripstoneScanner.Kind.COLUMN;
      } else if (ceiling) {
         if (length < minStalactite) {
            return;
         }

         kind = DripstoneScanner.Kind.STALACTITE;
      } else {
         if (!floor) {
            return;
         }

         if (length < minStalagmite) {
            return;
         }

         kind = DripstoneScanner.Kind.STALAGMITE;
      }

      out.add(new DripstoneScanner.Find(r.x, r.z, bottom, top, kind));
   }

   private static void findHangingStone(
      List<DripstoneScanner.Find> out,
      List<DripstoneScanner.Run> runs,
      LevelChunkSection[] sections,
      LevelChunkSection[] westSections,
      LevelChunkSection[] eastSections,
      LevelChunkSection[] northSections,
      LevelChunkSection[] southSections,
      int bottomY,
      int startX,
      int startZ,
      int minStone,
      int minStalactite
   ) {
      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (!section.hasOnlyAir() && section.maybeHas(state -> state.is(Blocks.DRIPSTONE_BLOCK))) {
            int baseY = bottomY + i * 16;

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     if (section.getBlockState(x, y, z).is(Blocks.DRIPSTONE_BLOCK)) {
                        int top = baseY + y - 1;
                        int tip = top;

                        while (true) {
                           BlockState state = stateAt(sections, bottomY, x, tip, z);
                           if (state == null
                              || !state.is(Blocks.STONE)
                              || openSides(sections, westSections, eastSections, northSections, southSections, bottomY, x, tip, z) < 3) {
                              int length = top - tip;
                              if (length != 0) {
                                 DripstoneScanner.Run below = runEndingAt(runs, startX + x, startZ + z, tip);
                                 if (below != null) {
                                    if (length >= minStone || top - below.bottom + 1 >= minStalactite) {
                                       below.used = true;
                                       out.add(new DripstoneScanner.Find(startX + x, startZ + z, below.bottom, top, DripstoneScanner.Kind.HANGING_STONE));
                                    }
                                 } else if (length >= minStone && isOpen(stateAt(sections, bottomY, x, tip, z))) {
                                    out.add(new DripstoneScanner.Find(startX + x, startZ + z, tip + 1, top, DripstoneScanner.Kind.HANGING_STONE));
                                 }
                              }
                              break;
                           }

                           tip--;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static DripstoneScanner.Run runEndingAt(List<DripstoneScanner.Run> runs, int x, int z, int top) {
      for (DripstoneScanner.Run r : runs) {
         if (r.top == top && r.x == x && r.z == z) {
            return r;
         }
      }

      return null;
   }

   public static boolean isPointed(BlockState state) {
      return state.is(Blocks.POINTED_DRIPSTONE);
   }

   private static int openSides(
      LevelChunkSection[] sections,
      LevelChunkSection[] westSections,
      LevelChunkSection[] eastSections,
      LevelChunkSection[] northSections,
      LevelChunkSection[] southSections,
      int bottomY,
      int x,
      int y,
      int z
   ) {
      int open = 0;
      if (isOpen(x > 0 ? stateAt(sections, bottomY, x - 1, y, z) : stateAt(westSections, bottomY, 15, y, z))) {
         open++;
      }

      if (isOpen(x < 15 ? stateAt(sections, bottomY, x + 1, y, z) : stateAt(eastSections, bottomY, 0, y, z))) {
         open++;
      }

      if (isOpen(z > 0 ? stateAt(sections, bottomY, x, y, z - 1) : stateAt(northSections, bottomY, x, y, 15))) {
         open++;
      }

      if (isOpen(z < 15 ? stateAt(sections, bottomY, x, y, z + 1) : stateAt(southSections, bottomY, x, y, 0))) {
         open++;
      }

      return open;
   }

   private static boolean isOpen(BlockState state) {
      return state != null && (state.isAir() || !state.getFluidState().isEmpty());
   }

   private static BlockState stateAt(LevelChunkSection[] sections, int bottomY, int x, int y, int z) {
      if (sections == null) {
         return null;
      } else {
         int index = y - bottomY >> 4;
         return y >= bottomY && index < sections.length ? sections[index].getBlockState(x, y & 15, z) : null;
      }
   }

   private static LevelChunkSection sectionAt(LevelChunkSection[] sections, int index) {
      return sections != null && index < sections.length ? sections[index] : null;
   }

   private static boolean isStillWaterAt(LevelChunkSection section, int x, int y, int z) {
      return section != null && isStillWater(section.getBlockState(x, y, z));
   }

   private static boolean hasStillWater(LevelChunkSection section) {
      return section != null && !section.hasOnlyAir() && section.maybeHas(DripstoneScanner::isStillWater);
   }

   private static boolean isStillWater(BlockState state) {
      return state.getFluidState().isSource() && state.getFluidState().is(FluidTags.WATER);
   }

   public static boolean isWater(BlockState state) {
      return state.getFluidState().is(FluidTags.WATER);
   }

   private static boolean isSolid(BlockState state) {
      return state != null && !state.isAir() && state.getFluidState().isEmpty();
   }

   public record Find(int x, int z, int bottomY, int topY, DripstoneScanner.Kind kind) {
      public int length() {
         return this.topY - this.bottomY + 1;
      }

      public long id() {
         return BlockPos.asLong(this.x, this.bottomY, this.z) * 31L + this.topY;
      }
   }

   public static enum Kind {
      STALACTITE("Stalactite"),
      STALAGMITE("Stalagmite"),
      COLUMN("Dripstone column"),
      HANGING_STONE("Hanging stone");

      public final String title;

      private Kind(String title) {
         this.title = title;
      }
   }

   public record Rule(int minStalactite, int minStalagmite, int minStone, int minY, int maxY) {
      public boolean keeps(DripstoneScanner.Find find) {
         return find.topY() <= this.maxY && find.bottomY() >= this.minY;
      }

      public DripstoneScanner.Rule atLeast(int length) {
         return new DripstoneScanner.Rule(
            Math.max(this.minStalactite, length),
            Math.max(this.minStalagmite, length),
            this.minStone == 0 ? 0 : Math.max(this.minStone, length),
            this.minY,
            this.maxY
         );
      }
   }

   private static final class Run {
      final int x;
      final int z;
      final int bottom;
      final int top;
      boolean used;

      Run(int x, int z, int bottom, int top) {
         this.x = x;
         this.z = z;
         this.bottom = bottom;
         this.top = top;
      }
   }
}

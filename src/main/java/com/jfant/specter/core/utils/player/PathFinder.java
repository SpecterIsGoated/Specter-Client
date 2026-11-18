package com.jfant.specter.core.utils.player;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.player.PlayerMoveEvent;
import java.util.ArrayList;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class PathFinder {
   private static final int PATH_AHEAD = 3;
   private static final int QUAD_1 = 1;
   private static final int QUAD_2 = 2;
   private static final int SOUTH = 0;
   private static final int NORTH = 180;
   private final ArrayList<PathFinder.PathBlock> path = new ArrayList<>(3);
   private Entity target;
   private PathFinder.PathBlock currentPathBlock;

   public PathFinder.PathBlock getNextPathBlock() {
      PathFinder.PathBlock nextBlock = new PathFinder.PathBlock(BlockPos.containing(this.getNextStraightPos()));
      if (this.isSolidFloor(nextBlock.blockPos) && this.isAirAbove(nextBlock.blockPos)) {
         return nextBlock;
      } else {
         if (!this.isSolidFloor(nextBlock.blockPos) && this.isAirAbove(nextBlock.blockPos)) {
            int drop = this.getDrop(nextBlock.blockPos);
            if (this.getDrop(nextBlock.blockPos) < 3) {
               nextBlock = new PathFinder.PathBlock(new BlockPos(nextBlock.blockPos.getX(), nextBlock.blockPos.getY() - drop, nextBlock.blockPos.getZ()));
            }
         }

         return nextBlock;
      }
   }

   public int getDrop(BlockPos pos) {
      int drop = 0;

      while (!this.isSolidFloor(pos) && drop < 3) {
         drop++;
         pos = new BlockPos(pos.getX(), pos.getY() - 1, pos.getZ());
      }

      return drop;
   }

   public boolean isAirAbove(BlockPos blockPos) {
      return !this.getBlockStateAtPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()).isAir()
         ? false
         : this.getBlockStateAtPos(blockPos.getX(), blockPos.getY() + 1, blockPos.getZ()).isAir();
   }

   public Vec3 getNextStraightPos() {
      Vec3 nextPos = new Vec3(SpecterCore.mc.player.getX(), SpecterCore.mc.player.getY(), SpecterCore.mc.player.getZ());

      for (double multiplier = 1.0; nextPos == SpecterCore.mc.player.position(); multiplier += 0.1) {
         nextPos = new Vec3(
            (int)(SpecterCore.mc.player.getX() + multiplier * Math.cos(Math.toRadians(SpecterCore.mc.player.getYRot()))),
            (int)SpecterCore.mc.player.getY(),
            (int)(SpecterCore.mc.player.getZ() + multiplier * Math.sin(Math.toRadians(SpecterCore.mc.player.getYRot())))
         );
      }

      return nextPos;
   }

   public int getYawToTarget() {
      if (this.target != null && SpecterCore.mc.player != null) {
         Vec3 tPos = this.target.position();
         Vec3 pPos = SpecterCore.mc.player.position();
         int direction = this.getDirection();
         double tan = (tPos.z - pPos.z) / (tPos.x - pPos.x);
         int yaw;
         if (direction == 1) {
            yaw = (int)(1.5707963267948966 - Math.atan(tan));
         } else {
            if (direction != 2) {
               return direction;
            }

            yaw = (int)(-1.5707963267948966 - Math.atan(tan));
         }

         return yaw;
      } else {
         return 2147483647;
      }
   }

   public int getDirection() {
      if (this.target != null && SpecterCore.mc.player != null) {
         Vec3 targetPos = this.target.position();
         Vec3 playerPos = SpecterCore.mc.player.position();
         if (targetPos.x == playerPos.x && targetPos.z > playerPos.z) {
            return 0;
         } else if (targetPos.x == playerPos.x && targetPos.z < playerPos.z) {
            return 180;
         } else if (targetPos.x < playerPos.x) {
            return 1;
         } else {
            return targetPos.x > playerPos.x ? 2 : 0;
         }
      } else {
         return 0;
      }
   }

   public BlockState getBlockStateAtPos(BlockPos pos) {
      return SpecterCore.mc.level != null ? SpecterCore.mc.level.getBlockState(pos) : null;
   }

   public BlockState getBlockStateAtPos(int x, int y, int z) {
      return SpecterCore.mc.level != null ? SpecterCore.mc.level.getBlockState(new BlockPos(x, y, z)) : null;
   }

   public Block getBlockAtPos(BlockPos pos) {
      return SpecterCore.mc.level != null ? SpecterCore.mc.level.getBlockState(pos).getBlock() : null;
   }

   public boolean isSolidFloor(BlockPos blockPos) {
      return this.isAir(this.getBlockAtPos(blockPos));
   }

   public boolean isAir(Block block) {
      return block == Blocks.AIR;
   }

   public boolean isWater(Block block) {
      return block == Blocks.WATER;
   }

   public void lookAtDestination(PathFinder.PathBlock pathBlock) {
      if (SpecterCore.mc.player != null) {
         SpecterCore.mc
            .player
            .lookAt(
               Anchor.EYES, new Vec3(pathBlock.blockPos.getX(), pathBlock.blockPos.getY() + SpecterCore.mc.player.getEyeHeight(), pathBlock.blockPos.getZ())
            );
      }
   }

   @EventHandler
   private void moveEventListener(PlayerMoveEvent event) {
      if (this.target != null && SpecterCore.mc.player != null) {
         if (!PlayerUtils.isWithin(this.target, 3.0)) {
            if (this.currentPathBlock == null) {
               this.currentPathBlock = this.getNextPathBlock();
            }

            if (SpecterCore.mc
                  .player
                  .position()
                  .distanceToSqr(new Vec3(this.currentPathBlock.blockPos.getX(), this.currentPathBlock.blockPos.getY(), this.currentPathBlock.blockPos.getZ()))
               < 0.01) {
               this.currentPathBlock = this.getNextPathBlock();
            }

            this.lookAtDestination(this.currentPathBlock);
            if (!SpecterCore.mc.options.keyUp.isDown()) {
               SpecterCore.mc.options.keyUp.setDown(true);
            }
         } else {
            if (SpecterCore.mc.options.keyUp.isDown()) {
               SpecterCore.mc.options.keyUp.setDown(false);
            }

            this.path.clear();
            this.currentPathBlock = null;
         }
      }
   }

   public void initiate(Entity entity) {
      this.target = entity;
      if (this.target != null) {
         this.currentPathBlock = this.getNextPathBlock();
      }

      SpecterCore.EVENT_BUS.subscribe(this);
   }

   public void disable() {
      this.target = null;
      this.path.clear();
      if (SpecterCore.mc.options.keyUp.isDown()) {
         SpecterCore.mc.options.keyUp.setDown(false);
      }

      SpecterCore.EVENT_BUS.unsubscribe(this);
   }

   public class PathBlock {
      public final Block block;
      public final BlockPos blockPos;
      public final BlockState blockState;
      public double yaw;

      public PathBlock(Block b, BlockPos pos, BlockState state) {
         this.block = b;
         this.blockPos = pos;
         this.blockState = state;
      }

      public PathBlock(Block b, BlockPos pos) {
         this.block = b;
         this.blockPos = pos;
         this.blockState = PathFinder.this.getBlockStateAtPos(this.blockPos);
      }

      public PathBlock(BlockPos pos) {
         this.blockPos = pos;
         this.block = PathFinder.this.getBlockAtPos(pos);
         this.blockState = PathFinder.this.getBlockStateAtPos(this.blockPos);
      }
   }
}

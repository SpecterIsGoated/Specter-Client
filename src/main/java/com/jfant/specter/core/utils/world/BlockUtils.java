package com.jfant.specter.core.utils.world;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.utils.PreInit;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.player.FindItemResult;
import com.jfant.specter.core.utils.player.InvUtils;
import com.jfant.specter.core.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockUtils {
   public static boolean breaking;
   private static boolean breakingThisTick;
   private static final ThreadLocal<MutableBlockPos> EXPOSED_POS = ThreadLocal.withInitial(MutableBlockPos::new);

   private BlockUtils() {
   }

   @PreInit
   public static void init() {
      SpecterCore.EVENT_BUS.subscribe(BlockUtils.class);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, int rotationPriority) {
      return place(blockPos, findItemResult, rotationPriority, true);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority) {
      return place(blockPos, findItemResult, rotate, rotationPriority, true);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority, boolean checkEntities) {
      return place(blockPos, findItemResult, rotate, rotationPriority, true, checkEntities);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, int rotationPriority, boolean checkEntities) {
      return place(blockPos, findItemResult, true, rotationPriority, true, checkEntities);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities) {
      return place(blockPos, findItemResult, rotate, rotationPriority, swingHand, checkEntities, true);
   }

   public static boolean place(
      BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities, boolean swapBack
   ) {
      if (findItemResult.isOffhand()) {
         return place(
            blockPos,
            InteractionHand.OFF_HAND,
            SpecterCore.mc.player.getInventory().getSelectedSlot(),
            rotate,
            rotationPriority,
            swingHand,
            checkEntities,
            swapBack
         );
      } else {
         return findItemResult.isHotbar()
            ? place(blockPos, InteractionHand.MAIN_HAND, findItemResult.slot(), rotate, rotationPriority, swingHand, checkEntities, swapBack)
            : false;
      }
   }

   public static boolean place(
      BlockPos blockPos, InteractionHand hand, int slot, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities, boolean swapBack
   ) {
      if (slot >= 0 && slot <= 8) {
         Block toPlace = Blocks.OBSIDIAN;
         ItemStack i = hand == InteractionHand.MAIN_HAND
            ? SpecterCore.mc.player.getInventory().getItem(slot)
            : SpecterCore.mc.player.getInventory().getItem(40);
         if (i.getItem() instanceof BlockItem blockItem) {
            toPlace = blockItem.getBlock();
         }

         if (!canPlaceBlock(blockPos, checkEntities, toPlace)) {
            return false;
         } else {
            Vec3 hitPos = Vec3.atCenterOf(blockPos);
            Direction side = getPlaceSide(blockPos);
            BlockPos neighbour;
            if (side == null) {
               side = Direction.UP;
               neighbour = blockPos;
            } else {
               neighbour = blockPos.relative(side);
               hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
            }

            BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
            if (rotate) {
               Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), rotationPriority, () -> {
                  InvUtils.swap(slot, swapBack);
                  interact(bhr, hand, swingHand);
                  if (swapBack) {
                     InvUtils.swapBack();
                  }
               });
            } else {
               InvUtils.swap(slot, swapBack);
               interact(bhr, hand, swingHand);
               if (swapBack) {
                  InvUtils.swapBack();
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static void interact(BlockHitResult blockHitResult, InteractionHand hand, boolean swing) {
      boolean wasSneaking = SpecterCore.mc.player.isShiftKeyDown();
      SpecterCore.mc.player.setShiftKeyDown(false);
      InteractionResult result = SpecterCore.mc.gameMode.useItemOn(SpecterCore.mc.player, hand, blockHitResult);
      if (result.consumesAction()) {
         if (swing) {
            SpecterCore.mc.player.swing(hand);
         } else {
            SpecterCore.mc.getConnection().send(new ServerboundSwingPacket(hand));
         }
      }

      SpecterCore.mc.player.setShiftKeyDown(wasSneaking);
   }

   public static boolean canPlaceBlock(BlockPos blockPos, boolean checkEntities, Block block) {
      if (blockPos == null) {
         return false;
      } else if (!Level.isInSpawnableBounds(blockPos)) {
         return false;
      } else {
         return !SpecterCore.mc.level.getBlockState(blockPos).canBeReplaced()
            ? false
            : !checkEntities || SpecterCore.mc.level.isUnobstructed(block.defaultBlockState(), blockPos, CollisionContext.empty());
      }
   }

   public static boolean canPlace(BlockPos blockPos, boolean checkEntities) {
      return canPlaceBlock(blockPos, checkEntities, Blocks.OBSIDIAN);
   }

   public static boolean canPlace(BlockPos blockPos) {
      return canPlace(blockPos, true);
   }

   public static Direction getPlaceSide(BlockPos blockPos) {
      Vec3 lookVec = blockPos.getCenter().subtract(SpecterCore.mc.player.getEyePosition());
      double bestRelevancy = -1.7976931348623157E308;
      Direction bestSide = null;

      for (Direction side : Direction.values()) {
         BlockPos neighbor = blockPos.relative(side);
         BlockState state = SpecterCore.mc.level.getBlockState(neighbor);
         if (!state.isAir() && !isClickable(state.getBlock()) && state.getFluidState().isEmpty()) {
            double relevancy = side.getAxis().choose(lookVec.x(), lookVec.y(), lookVec.z()) * side.getAxisDirection().getStep();
            if (relevancy > bestRelevancy) {
               bestRelevancy = relevancy;
               bestSide = side;
            }
         }
      }

      return bestSide;
   }

   public static Direction getClosestPlaceSide(BlockPos blockPos) {
      return getClosestPlaceSide(blockPos, SpecterCore.mc.player.getEyePosition());
   }

   public static Direction getClosestPlaceSide(BlockPos blockPos, Vec3 pos) {
      Direction closestSide = null;
      double closestDistance = 1.7976931348623157E308;

      for (Direction side : Direction.values()) {
         BlockPos neighbor = blockPos.relative(side);
         BlockState state = SpecterCore.mc.level.getBlockState(neighbor);
         if (!state.isAir() && !isClickable(state.getBlock()) && state.getFluidState().isEmpty()) {
            double distance = pos.distanceToSqr(neighbor.getX(), neighbor.getY(), neighbor.getZ());
            if (distance < closestDistance) {
               closestDistance = distance;
               closestSide = side;
            }
         }
      }

      return closestSide;
   }

   @EventHandler(
      priority = 300
   )
   private static void onTickPre(TickEvent.Pre event) {
      breakingThisTick = false;
   }

   @EventHandler(
      priority = -300
   )
   private static void onTickPost(TickEvent.Post event) {
      if (!breakingThisTick && breaking) {
         breaking = false;
         if (SpecterCore.mc.gameMode != null) {
            SpecterCore.mc.gameMode.stopDestroyBlock();
         }
      }
   }

   public static boolean breakBlock(BlockPos blockPos, boolean swing) {
      if (!canBreak(blockPos, SpecterCore.mc.level.getBlockState(blockPos))) {
         return false;
      } else {
         BlockPos pos = blockPos instanceof MutableBlockPos ? new BlockPos(blockPos) : blockPos;
         if (SpecterCore.mc.gameMode.isDestroying()) {
            SpecterCore.mc.gameMode.continueDestroyBlock(pos, getDirection(blockPos));
         } else {
            SpecterCore.mc.gameMode.startDestroyBlock(pos, getDirection(blockPos));
         }

         if (swing) {
            SpecterCore.mc.player.swing(InteractionHand.MAIN_HAND);
         } else {
            SpecterCore.mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
         }

         breaking = true;
         breakingThisTick = true;
         return true;
      }
   }

   public static boolean canBreak(BlockPos blockPos, BlockState state) {
      return !SpecterCore.mc.player.isCreative() && state.getDestroySpeed(SpecterCore.mc.level, blockPos) < 0.0F
         ? false
         : state.getShape(SpecterCore.mc.level, blockPos) != Shapes.empty();
   }

   public static boolean canBreak(BlockPos blockPos) {
      return canBreak(blockPos, SpecterCore.mc.level.getBlockState(blockPos));
   }

   public static boolean canInstaBreak(BlockPos blockPos, float breakSpeed) {
      return SpecterCore.mc.player.isCreative() || calcBlockBreakingDelta2(blockPos, breakSpeed) >= 1.0F;
   }

   public static boolean canInstaBreak(BlockPos blockPos) {
      BlockState state = SpecterCore.mc.level.getBlockState(blockPos);
      return canInstaBreak(blockPos, SpecterCore.mc.player.getDestroySpeed(state));
   }

   public static float calcBlockBreakingDelta2(BlockPos blockPos, float breakSpeed) {
      BlockState state = SpecterCore.mc.level.getBlockState(blockPos);
      float f = state.getDestroySpeed(SpecterCore.mc.level, blockPos);
      if (f == -1.0F) {
         return 0.0F;
      } else {
         int i = SpecterCore.mc.player.hasCorrectToolForDrops(state) ? 30 : 100;
         return breakSpeed / f / i;
      }
   }

   public static boolean isClickable(Block block) {
      return block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof LoomBlock
         || block instanceof CartographyTableBlock
         || block instanceof GrindstoneBlock
         || block instanceof StonecutterBlock
         || block instanceof ButtonBlock
         || block instanceof BasePressurePlateBlock
         || block instanceof BaseEntityBlock
         || block instanceof BedBlock
         || block instanceof FenceGateBlock
         || block instanceof DoorBlock
         || block instanceof NoteBlock
         || block instanceof TrapDoorBlock;
   }

   public static BlockUtils.MobSpawn isValidMobSpawn(BlockPos blockPos, BlockState blockState, int spawnLightLimit) {
      boolean snow = blockState.getBlock() instanceof SnowLayerBlock && blockState.getValue(SnowLayerBlock.LAYERS) == 1;
      if (!blockState.isAir() && !snow) {
         return BlockUtils.MobSpawn.Never;
      } else if (!isValidSpawnBlock(SpecterCore.mc.level.getBlockState(blockPos.below()))) {
         return BlockUtils.MobSpawn.Never;
      } else if (SpecterCore.mc.level.getBrightness(LightLayer.BLOCK, blockPos) > spawnLightLimit) {
         return BlockUtils.MobSpawn.Never;
      } else {
         return SpecterCore.mc.level.getBrightness(LightLayer.SKY, blockPos) > spawnLightLimit ? BlockUtils.MobSpawn.Potential : BlockUtils.MobSpawn.Always;
      }
   }

   public static boolean isValidSpawnBlock(BlockState blockState) {
      Block block = blockState.getBlock();
      if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block instanceof TransparentBlock || block instanceof ScaffoldingBlock) {
         return false;
      } else if (block != Blocks.SOUL_SAND && block != Blocks.MUD) {
         if (block instanceof SlabBlock && blockState.getValue(SlabBlock.TYPE) == SlabType.TOP) {
            return true;
         } else {
            return block instanceof StairBlock && blockState.getValue(StairBlock.HALF) == Half.TOP ? true : blockState.isSolidRender();
         }
      } else {
         return true;
      }
   }

   public static Direction getDirection(BlockPos pos) {
      double eyePos = SpecterCore.mc.player.getY() + SpecterCore.mc.player.getEyeHeight(SpecterCore.mc.player.getPose());
      VoxelShape outline = SpecterCore.mc.level.getBlockState(pos).getCollisionShape(SpecterCore.mc.level, pos);
      if (eyePos > pos.getY() + outline.max(Axis.Y) && SpecterCore.mc.level.getBlockState(pos.above()).canBeReplaced()) {
         return Direction.UP;
      } else if (eyePos < pos.getY() + outline.min(Axis.Y) && SpecterCore.mc.level.getBlockState(pos.below()).canBeReplaced()) {
         return Direction.DOWN;
      } else {
         BlockPos difference = pos.subtract(SpecterCore.mc.player.blockPosition());
         if (Math.abs(difference.getX()) > Math.abs(difference.getZ())) {
            return difference.getX() > 0 ? Direction.WEST : Direction.EAST;
         } else {
            return difference.getZ() > 0 ? Direction.NORTH : Direction.SOUTH;
         }
      }
   }

   public static boolean isExposed(BlockPos blockPos) {
      for (Direction direction : Direction.values()) {
         if (!SpecterCore.mc.level.getBlockState(EXPOSED_POS.get().setWithOffset(blockPos, direction)).isSolidRender()) {
            return true;
         }
      }

      return false;
   }

   public static double getBreakDelta(int slot, BlockState state) {
      float hardness = state.getDestroySpeed(null, null);
      return hardness == -1.0F
         ? 0.0
         : getBlockBreakingSpeed(slot, state)
            / hardness
            / (
               state.requiresCorrectToolForDrops() && !SpecterCore.mc.player.getInventory().getNonEquipmentItems().get(slot).isCorrectToolForDrops(state)
                  ? 100
                  : 30
            );
   }

   private static double getBlockBreakingSpeed(int slot, BlockState block) {
      double speed = SpecterCore.mc.player.getInventory().getNonEquipmentItems().get(slot).getDestroySpeed(block);
      if (speed > 1.0) {
         ItemStack tool = SpecterCore.mc.player.getInventory().getItem(slot);
         int efficiency = Utils.getEnchantmentLevel(tool, Enchantments.EFFICIENCY);
         if (efficiency > 0 && !tool.isEmpty()) {
            speed += efficiency * efficiency + 1;
         }
      }

      if (MobEffectUtil.hasDigSpeed(SpecterCore.mc.player)) {
         speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(SpecterCore.mc.player) + 1) * 0.2F;
      }

      if (SpecterCore.mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
         float k = switch (SpecterCore.mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
         speed *= k;
      }

      if (SpecterCore.mc.player.isEyeInFluid(FluidTags.WATER)) {
         speed *= SpecterCore.mc.player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
      }

      if (!SpecterCore.mc.player.onGround()) {
         speed /= 5.0;
      }

      return speed;
   }

   public static MutableBlockPos mutateAround(MutableBlockPos mutable, BlockPos origin, int xOffset, int yOffset, int zOffset) {
      return mutable.set(origin.getX() + xOffset, origin.getY() + yOffset, origin.getZ() + zOffset);
   }

   public static enum MobSpawn {
      Never,
      Potential,
      Always;
   }
}

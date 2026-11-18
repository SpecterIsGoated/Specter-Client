package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.player.FindItemResult;
import com.jfant.specter.core.utils.player.InvUtils;
import com.jfant.specter.core.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class NoFall extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBucket = this.settings.createGroup("Water Bucket");
   private final Setting<NoFall.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description(
                     "Packet: tell the server you are on the ground while you fall (easy to spot by a ground check). Water Bucket: place water under you just before landing and pick it up again."
                  ))
               .defaultValue(NoFall.Mode.Packet))
            .build()
      );
   private final Setting<Double> minFall = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-fall-distance")
            .description(
               "Blocks fallen before it steps in. Vanilla damage starts above 3, so anything up to about 3 loses nothing; lower values act earlier and more often."
            )
            .defaultValue(2.5)
            .range(0.0, 20.0)
            .sliderRange(0.0, 8.0)
            .decimalPlaces(1)
            .build()
      );
   private final Setting<Boolean> pauseOnMace = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-on-mace")
            .description("Do nothing while you hold a mace, whose smash attack gets its damage from how far you fall.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> placeHeight = this.sgBucket
      .add(
         new DoubleSetting.Builder()
            .name("place-height")
            .description(
               "Place the water when the ground is this many blocks below your feet. Your reach down from your eyes is about 2.9, so more than that cannot work; too low and a fast fall is already past it."
            )
            .defaultValue(2.6)
            .range(1.0, 2.9)
            .sliderRange(1.0, 2.9)
            .decimalPlaces(1)
            .visible(() -> this.mode.get() == NoFall.Mode.WaterBucket)
            .build()
      );
   private final Setting<Boolean> pickUp = this.sgBucket
      .add(
         new BoolSetting.Builder()
            .name("pick-up")
            .description("Scoop the water back into the bucket after landing.")
            .defaultValue(true)
            .visible(() -> this.mode.get() == NoFall.Mode.WaterBucket)
            .build()
      );
   private static final int PICK_UP_TIMEOUT = 40;
   private boolean placed;
   private int placedTicks;

   public NoFall() {
      super(
         SpecterClientAddon.BLATANT,
         "no-fall",
         "Stops fall damage: Packet claims you are on the ground while you fall (a ground check on the server sees through it), Water Bucket places and picks up water just before you land. Elytra flight is left alone."
      );
   }

   @Override
   public void onActivate() {
      this.placed = false;
      this.placedTicks = 0;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.placed = false;
      this.placedTicks = 0;
   }

   private boolean excused(LocalPlayer player) {
      if (player.getAbilities().instabuild || player.isSpectator() || player.isFallFlying() || player.isPassenger()) {
         return true;
      } else {
         return player.getAbilities().flying
            ? true
            : this.pauseOnMace.get() && (player.getMainHandItem().getItem() instanceof MaceItem || player.getOffhandItem().getItem() instanceof MaceItem);
      }
   }

   @EventHandler
   private void onSend(PacketEvent.Send event) {
      if (this.mode.get() == NoFall.Mode.Packet && event.packet instanceof ServerboundMovePlayerPacket) {
         LocalPlayer player = this.mc.player;
         if (player != null && !this.excused(player) && !player.isInWater() && !player.isInLava()) {
            if (player.fallDistance >= this.minFall.get() && player.getDeltaMovement().y < -0.1) {
               GroundSpoof.apply(event.packet);
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null && this.mc.level != null && this.mode.get() == NoFall.Mode.WaterBucket) {
         if (!this.excused(player)) {
            if (this.placed) {
               this.placedTicks++;
               if (this.pickUp.get() && this.placedTicks > 2 && player.getInBlockState().getBlock() == Blocks.WATER) {
                  FindItemResult empty = InvUtils.findInHotbar(Items.BUCKET);
                  if (empty.found()) {
                     this.use(empty);
                     this.placed = false;
                     return;
                  }
               }

               if (this.placedTicks > 40 || !this.pickUp.get() && player.onGround()) {
                  this.placed = false;
               }
            } else if (!(player.fallDistance < this.minFall.get()) && !(player.getDeltaMovement().y > -0.3) && !player.isInWater() && !player.isInLava()) {
               Vec3 feet = player.position();
               BlockHitResult hit = this.mc
                  .level
                  .clip(new ClipContext(feet, feet.subtract(0.0, this.placeHeight.get(), 0.0), Block.COLLIDER, Fluid.NONE, player));
               if (hit.getType() == Type.BLOCK) {
                  FindItemResult bucket = InvUtils.findInHotbar(Items.WATER_BUCKET);
                  if (bucket.found()) {
                     this.use(bucket);
                     this.placed = true;
                     this.placedTicks = 0;
                  }
               }
            }
         }
      }
   }

   private void use(FindItemResult item) {
      Rotations.rotate(this.mc.player.getYRot(), 90.0, 10, true, () -> {
         if (item.isOffhand()) {
            this.mc.gameMode.useItem(this.mc.player, InteractionHand.OFF_HAND);
         } else {
            InvUtils.swap(item.slot(), true);
            this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
         }
      });
   }

   @Override
   public String getInfoString() {
      return this.mode.get().toString();
   }

   public static enum Mode {
      Packet("Packet"),
      WaterBucket("Water Bucket");

      private final String title;

      private Mode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}

package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.build.BuildProfile;
import com.jfant.specter.build.HumanAim;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringListSetting;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.util.Leaving;
import com.jfant.specter.webhook.Webhook;
import com.jfant.specter.webhook.WebhookSettings;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class SpawnerProtect extends Module {
   private static final double OWN_BREAK = 6.0;
   private static final double SPAWN_BOX = 100.0;
   private static final double TRUSTED_NEAR = 8.0;
   private static final int FIND_RADIUS = 16;
   private static final double REACH = 4.0;
   private static final double CHEST_REACH = 3.4;
   private static final int CHEST_RADIUS = 4;
   private static final int SETTLE_TICKS = 40;
   private static final int TARGET_TICKS = 400;
   private static final int CHASE_TICKS = 200;
   private static final int OPEN_TICKS = 200;
   private static final int SHOP_TICKS = 200;
   private static final int PLACE_TICKS = 200;
   private static final int LEAVE_TICKS = 40;
   private static final int SHOP_STEP_TICKS = 30;
   private static final int SHOP_END_SLOT = 11;
   private static final int SHOP_ENDER_SLOT = 9;
   private static final int SHOP_CONFIRM_SLOT = 25;
   private static final double FACING = 35.0;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRescue = this.settings.createGroup("Rescue");
   private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");
   private final Setting<Boolean> detectBreaks = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("detect-block-updates")
            .description("Start the rescue when blocks near you keep getting broken. Off, it only sends player alerts.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> triggerRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("trigger-range")
            .description("How far away (in blocks, ignoring height) a broken block or a player still counts.")
            .defaultValue(64.0)
            .min(16.0)
            .sliderRange(16.0, 128.0)
            .build()
      );
   private final Setting<Integer> threshold = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("block-update-threshold")
            .description("How many different blocks broken within the window start the rescue.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> window = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("block-update-window")
            .description("Seconds the broken blocks have to fall within.")
            .defaultValue(60)
            .min(1)
            .sliderRange(1, 600)
            .build()
      );
   private final Setting<List<String>> whitelist = this.sgGeneral
      .add(
         new StringListSetting.Builder()
            .name("whitelist")
            .description("Players whose digging is ignored, and who are not announced. Case does not matter.")
            .build()
      );
   private final Setting<Boolean> trustFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("trust-friends").description("Treat players on your friends list like the whitelist.").defaultValue(true).build());
   private final Setting<Boolean> playerAlerts = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("player-alerts")
            .description("Send a Discord message when a player comes within trigger-range. It never starts the rescue.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> sneak = this.sgRescue
      .add(new BoolSetting.Builder().name("sneak").description("Crouch while collecting, so you cannot step off an edge.").defaultValue(true).build());
   private final Setting<Boolean> buyChest = this.sgRescue
      .add(
         new BoolSetting.Builder()
            .name("buy-ender-chest")
            .description("With no ender chest nearby or in your inventory, buy one from /shop (End, Ender Chest) - that spends in-game money.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> disconnectWhenDone = this.sgRescue
      .add(
         new BoolSetting.Builder()
            .name("disconnect-when-done")
            .description("Leave the server once the spawners are stored (or it cannot store them).")
            .defaultValue(true)
            .build()
      );
   private final WebhookSettings webhook = new WebhookSettings(this.sgWebhook, "Spawner Protect");
   private final Random random = new Random();
   private final Long2LongOpenHashMap breaks = new Long2LongOpenHashMap();
   private final Set<UUID> watched = new HashSet<>();
   private final LongOpenHashSet skipped = new LongOpenHashSet();
   private final Set<Integer> ignoredDrops = new HashSet<>();
   private SpawnerProtect.State state = SpawnerProtect.State.WAITING;
   private HumanAim aim;
   private BlockPos target;
   private BlockPos chest;
   private BlockPos placeSpot;
   private String endNote = "";
   private boolean endedWell;
   private int stateTicks;
   private int targetTicks;
   private int idleTicks;
   private int chaseTicks;
   private int clickGap;
   private int shopStep;
   private int shopTicks;
   private int placeTries;
   private Vec3 anchor;
   private int anchorTicks;
   private int stuck;
   private Boolean pauseOnLostFocus;

   public SpawnerProtect() {
      super(
         SpecterClientAddon.DONUT,
         "spawner-protect",
         "Collects your spawners into an ender chest when blocks near you start getting broken; a player arriving only sends a Discord message."
      );
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = super.toTag();
      tag.putBoolean("active", false);
      return tag;
   }

   @Override
   public void onActivate() {
      this.aim = new HumanAim(new BuildProfile.Aim(), this.random);
      if (this.pauseOnLostFocus == null) {
         this.pauseOnLostFocus = this.mc.options.pauseOnLostFocus;
      }

      this.mc.options.pauseOnLostFocus = false;
      this.resetAll();
   }

   @Override
   public void onDeactivate() {
      this.releaseAll();
      if (this.aim != null) {
         this.aim.stop();
      }

      if (this.pauseOnLostFocus != null && this.mc.options != null) {
         this.mc.options.pauseOnLostFocus = this.pauseOnLostFocus;
      }

      this.pauseOnLostFocus = null;
      this.state = SpawnerProtect.State.WAITING;
   }

   @Override
   public String getInfoString() {
      return this.state.title;
   }

   private void resetAll() {
      this.releaseAll();
      if (this.aim != null) {
         this.aim.stop();
      }

      this.state = SpawnerProtect.State.WAITING;
      this.breaks.clear();
      this.watched.clear();
      this.skipped.clear();
      this.ignoredDrops.clear();
      this.target = this.chest = this.placeSpot = null;
      this.stateTicks = this.targetTicks = this.idleTicks = this.chaseTicks = this.clickGap = 0;
      this.shopStep = this.shopTicks = this.placeTries = 0;
      this.anchor = null;
      this.anchorTicks = this.stuck = 0;
      this.endedWell = false;
      this.endNote = "";
   }

   private void enter(SpawnerProtect.State next) {
      this.state = next;
      this.stateTicks = 0;
      this.stopMoving();
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (this.state == SpawnerProtect.State.WAITING && this.detectBreaks.get() && this.mc.player != null && this.mc.level != null && !this.nearSpawn()) {
         if (!event.oldState.isAir() && event.newState.isAir()) {
            if (event.oldState.getFluidState().isEmpty() && !event.oldState.canBeReplaced() && !event.oldState.is(BlockTags.LEAVES)) {
               if (!(event.oldState.getBlock() instanceof ShulkerBoxBlock)) {
                  if (event.pos.getY() > this.mc.level.getMinY()) {
                     Vec3 center = Vec3.atCenterOf(event.pos);
                     if (!(this.mc.player.getEyePosition().distanceTo(center) < 6.0)) {
                        double flat = Math.hypot(this.mc.player.getX() - center.x, this.mc.player.getZ() - center.z);
                        if (!(flat > this.triggerRange.get())) {
                           for (Player other : this.mc.level.players()) {
                              if (other != this.mc.player && this.trusted(other) && other.getEyePosition().distanceTo(center) < 8.0) {
                                 return;
                              }
                           }

                           long now = System.currentTimeMillis();
                           long keep = this.window.get().intValue() * 1000L;
                           this.breaks.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > keep);
                           this.breaks.put(event.pos.asLong(), now);
                           if (this.breaks.size() >= this.threshold.get()) {
                              BlockPos at = event.pos.immutable();
                              Webhook.Embed embed = new Webhook.Embed(
                                    "Protection activated", "Blocks near you were broken - collecting your spawners now.", 15158332
                                 )
                                 .field("Trigger", "Block breaking", true)
                                 .field("Blocks broken", this.breaks.size() + " / " + this.threshold.get(), true)
                                 .field("Distance", Math.round(flat) + " blocks", true)
                                 .field("Block location", Webhook.coordinates(at.getX(), at.getY(), at.getZ()), false)
                                 .context();
                              this.start(
                                 embed,
                                 "Blocks are being broken near you (%d blocks away) - collecting your spawners. Switch Spawner Protect off to stop."
                                    .formatted(Math.round(flat))
                              );
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void start(Webhook.Embed embed, String chatLine) {
      this.warning("%s", new Object[]{chatLine});
      if (this.webhook.enabled()) {
         this.webhook.send(embed);
      }

      this.breaks.clear();
      this.skipped.clear();
      this.ignoredDrops.clear();
      this.target = null;
      this.idleTicks = 0;
      this.enter(SpawnerProtect.State.MINING);
   }

   private boolean trusted(Player player) {
      if (this.trustFriends.get() && Friends.get().isFriend(player)) {
         return true;
      } else {
         String name = player.getGameProfile().name();

         for (String listed : this.whitelist.get()) {
            if (listed.trim().equalsIgnoreCase(name)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean nearSpawn() {
      return Math.abs(this.mc.player.getX()) < 100.0 && Math.abs(this.mc.player.getZ()) < 100.0;
   }

   private void watchPlayers() {
      if (this.playerAlerts.get() && this.webhook.enabled()) {
         Set<UUID> present = new HashSet<>();

         for (Player player : this.mc.level.players()) {
            if (player != this.mc.player && !player.isSpectator() && !this.trusted(player)) {
               double distance = this.mc.player.distanceTo(player);
               if (!(distance > this.triggerRange.get())) {
                  present.add(player.getUUID());
                  if (!this.watched.contains(player.getUUID())) {
                     this.webhook
                        .send(
                           new Webhook.Embed("A player entered your range", "A new player is nearby. This only sends a notification.", 5793266)
                              .field("Player", "`" + player.getGameProfile().name() + "`", true)
                              .field("Distance", String.format(Locale.ROOT, "%.1f blocks", distance), true)
                              .field("Protection", this.state == SpawnerProtect.State.WAITING ? "Standby" : "In progress", true)
                              .field("Player location", Webhook.coordinates(player.getBlockX(), player.getBlockY(), player.getBlockZ()), false)
                              .context()
                        );
                  }
               }
            }
         }

         this.watched.clear();
         this.watched.addAll(present);
      } else {
         this.watched.clear();
      }
   }

   @EventHandler
   private void onFrame(Render3DEvent event) {
      if (this.aim != null && this.state != SpawnerProtect.State.WAITING && this.mc.player != null && this.mc.gui.screen() == null) {
         this.aim.frame(this.mc);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null
         && this.mc.level != null
         && this.mc.gameMode != null
         && this.mc.getConnection() != null
         && !(this.mc.gui.screen() instanceof DisconnectedScreen)) {
         if (this.nearSpawn()) {
            if (this.state != SpawnerProtect.State.WAITING) {
               this.info("Back at spawn - stopping the rescue.", new Object[0]);
               this.resetAll();
            }
         } else {
            this.watchPlayers();
            if (this.state != SpawnerProtect.State.WAITING) {
               this.stateTicks++;
               if (this.state != SpawnerProtect.State.CHEST
                  && this.state != SpawnerProtect.State.DEPOSIT
                  && this.state != SpawnerProtect.State.BUY
                  && this.state != SpawnerProtect.State.LEAVING) {
                  press(this.mc.options.keyShift, this.sneak.get());
               }

               switch (this.state) {
                  case WAITING:
                  default:
                     break;
                  case MINING:
                     this.tickMining(player);
                     break;
                  case CHEST:
                     this.tickChest(player);
                     break;
                  case DEPOSIT:
                     this.tickDeposit(player);
                     break;
                  case BUY:
                     this.tickBuy(player);
                     break;
                  case PLACE:
                     this.tickPlace(player);
                     break;
                  case LEAVING:
                     this.tickLeaving();
               }

               if (this.aim != null && this.state != SpawnerProtect.State.WAITING) {
                  this.aim.tick(this.mc);
               }
            }
         }
      } else {
         if (this.state != SpawnerProtect.State.WAITING) {
            this.resetAll();
         }
      }
   }

   private void tickMining(LocalPlayer player) {
      if (player.containerMenu != player.inventoryMenu) {
         player.closeContainer();
      }

      ItemEntity drop = this.nearestDrop(player);
      if (drop != null) {
         this.idleTicks = 0;
         this.target = null;
         this.mc.gameMode.stopDestroyBlock();
         Vec3 at = drop.position().add(0.0, 0.2, 0.0);
         this.aim.follow(player, player.getEyePosition(), at);
         this.walkToward(at, 0.2);
         if (++this.chaseTicks > 200) {
            this.ignoredDrops.add(drop.getId());
            this.chaseTicks = 0;
         }
      } else {
         this.chaseTicks = 0;
         int carrying = this.count(Items.SPAWNER);
         if (!roomForSpawner(player)) {
            if (carrying > 0) {
               this.goToChest();
            } else {
               this.leave("Your inventory is full, so there was no room for a spawner. Make room first.", false);
            }
         } else if (!this.holdSilkPickaxe(player)) {
            if (carrying > 0) {
               this.goToChest();
            } else {
               this.leave("There is no Silk Touch pickaxe in your hotbar, so nothing was broken.", false);
            }
         } else {
            if (this.target == null || !this.mc.level.getBlockState(this.target).is(Blocks.SPAWNER) || this.skipped.contains(this.target.asLong())) {
               this.target = this.findSpawner(player);
               this.targetTicks = 0;
               if (this.target == null) {
                  this.stopMoving();
                  if (++this.idleTicks >= 40) {
                     if (carrying > 0) {
                        this.goToChest();
                     } else {
                        this.leave("No spawners were found to collect.", false);
                     }
                  }

                  return;
               }
            }

            this.idleTicks = 0;
            if (++this.targetTicks > 400) {
               this.skipped.add(this.target.asLong());
               this.target = null;
               this.stopMoving();
            } else {
               Vec3 center = Vec3.atCenterOf(this.target);
               this.aim.follow(player, player.getEyePosition(), center);
               if (player.getEyePosition().distanceTo(center) > 4.0) {
                  this.mc.gameMode.stopDestroyBlock();
                  this.walkToward(center, 3.5);
               } else {
                  press(this.mc.options.keyUp, false);
                  if (this.mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(this.target)) {
                     this.mc.gameMode.continueDestroyBlock(this.target, hit.getDirection());
                     player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation(), false);
                  } else {
                     this.mc.gameMode.stopDestroyBlock();
                  }
               }
            }
         }
      }
   }

   private void goToChest() {
      this.mc.gameMode.stopDestroyBlock();
      this.placeTries = 0;
      this.chest = this.findBlock(Blocks.ENDER_CHEST, 4);
      if (this.chest != null) {
         this.enter(SpawnerProtect.State.CHEST);
      } else if (this.count(Items.ENDER_CHEST) > 0) {
         this.enter(SpawnerProtect.State.PLACE);
      } else if (this.buyChest.get()) {
         this.shopStep = 0;
         this.shopTicks = 0;
         this.enter(SpawnerProtect.State.BUY);
      } else {
         this.leave("There is no ender chest nearby or in your inventory; the spawners stay in your inventory.", false);
      }
   }

   private void tickChest(LocalPlayer player) {
      press(this.mc.options.keyShift, false);
      if (player.containerMenu instanceof ChestMenu) {
         this.enter(SpawnerProtect.State.DEPOSIT);
      } else if (this.stateTicks > 200) {
         this.leave("The ender chest would not open; the spawners stay in your inventory.", false);
      } else {
         if (this.chest == null || !this.mc.level.getBlockState(this.chest).is(Blocks.ENDER_CHEST)) {
            this.chest = this.findBlock(Blocks.ENDER_CHEST, 4);
            if (this.chest == null) {
               if (++this.placeTries > 3) {
                  this.leave("The ender chest is gone; the spawners stay in your inventory.", false);
               } else {
                  this.enter(SpawnerProtect.State.PLACE);
               }

               return;
            }
         }

         Vec3 at = Vec3.atCenterOf(this.chest);
         this.aim.follow(player, player.getEyePosition(), at);
         if (player.getEyePosition().distanceTo(at) > 3.4) {
            this.walkToward(at, 2.8);
         } else {
            press(this.mc.options.keyUp, false);
            if (this.stateTicks % 12 == 0
               && this.mc.hitResult instanceof BlockHitResult hit
               && hit.getType() == Type.BLOCK
               && hit.getBlockPos().equals(this.chest)) {
               this.mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
               player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
            }
         }
      }
   }

   private void tickDeposit(LocalPlayer player) {
      press(this.mc.options.keyShift, false);
      if (!(player.containerMenu instanceof ChestMenu handler)) {
         this.enter(SpawnerProtect.State.CHEST);
      } else if (this.stateTicks >= 15 && --this.clickGap <= 0) {
         int chestSlots = handler.slots.size() - 36;
         boolean room = false;

         for (int i = 0; i < chestSlots; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            if (stack.isEmpty() || stack.is(Items.SPAWNER) && stack.getCount() < stack.getMaxStackSize()) {
               room = true;
               break;
            }
         }

         for (int ix = 0; ix < 36; ix++) {
            if (handler.getSlot(chestSlots + ix).getItem().is(Items.SPAWNER)) {
               if (!room) {
                  player.closeContainer();
                  this.leave("The ender chest is full; the rest of the spawners stay in your inventory.", false);
                  return;
               }

               this.mc.gameMode.handleContainerInput(handler.containerId, chestSlots + ix, 0, ContainerInput.QUICK_MOVE, player);
               this.clickGap = 2 + this.random.nextInt(4);
               return;
            }
         }

         player.closeContainer();
         if (this.findSpawner(player) != null) {
            this.target = null;
            this.idleTicks = 0;
            this.enter(SpawnerProtect.State.MINING);
         } else {
            this.leave("All your spawners are stored in the ender chest.", true);
         }
      }
   }

   private void tickBuy(LocalPlayer player) {
      press(this.mc.options.keyShift, false);
      this.shopTicks++;
      if (this.stateTicks > 400) {
         if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
         }

         this.leave("Buying an ender chest timed out; the spawners stay in your inventory.", false);
      } else if (this.shopStep > 0 && this.mc.gui.screen() == null && this.shopTicks > 60) {
         this.shopStep = 0;
         this.shopTicks = 0;
      } else if (this.shopTicks >= 30) {
         switch (this.shopStep) {
            case 0:
               this.mc.getConnection().sendCommand("shop");
               this.shopStep = 1;
               this.shopTicks = 0;
               break;
            case 1:
               this.shopClick("SHOP", 11, player);
               break;
            case 2:
               this.shopClick("END", 9, player);
               break;
            case 3:
               this.shopClick("ENDER CHEST", 25, player);
               break;
            default:
               if (this.count(Items.ENDER_CHEST) > 0) {
                  player.closeContainer();
                  this.info("Bought an ender chest.", new Object[0]);
                  this.placeTries = 0;
                  this.enter(SpawnerProtect.State.PLACE);
               } else if (this.shopTicks > 100) {
                  player.closeContainer();
                  this.shopStep = 0;
                  this.shopTicks = 0;
               }
         }
      }
   }

   private void shopClick(String title, int slot, LocalPlayer player) {
      if (this.mc.gui.screen() != null && this.mc.gui.screen().getTitle().getString().toUpperCase(Locale.ROOT).contains(title)) {
         this.mc.gameMode.handleContainerInput(player.containerMenu.containerId, slot, 0, ContainerInput.PICKUP, player);
         this.shopStep++;
         this.shopTicks = 0;
      }
   }

   private void tickPlace(LocalPlayer player) {
      if (this.stateTicks > 200) {
         this.leave("Could not place an ender chest; the spawners stay in your inventory.", false);
      } else if (!this.holdItem(player, Items.ENDER_CHEST)) {
         if (this.count(Items.ENDER_CHEST) == 0) {
            this.goToChest();
         }
      } else {
         if (this.placeSpot == null) {
            this.placeSpot = this.freeSpotBeside(player);
            if (this.placeSpot == null) {
               this.leave("There is nowhere to put an ender chest here; the spawners stay in your inventory.", false);
               return;
            }
         }

         BlockPos support = this.placeSpot.below();
         this.aim.follow(player, player.getEyePosition(), Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
         if (this.mc.hitResult instanceof BlockHitResult hit
            && hit.getType() == Type.BLOCK
            && hit.getBlockPos().equals(support)
            && hit.getDirection() == Direction.UP
            && this.aim.arrived(this.mc)) {
            this.mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
            this.chest = this.placeSpot;
            this.placeSpot = null;
            this.placeTries++;
            this.enter(SpawnerProtect.State.CHEST);
         }
      }
   }

   private BlockPos freeSpotBeside(LocalPlayer player) {
      BlockPos feet = player.blockPosition();

      for (Direction side : Plane.HORIZONTAL) {
         BlockPos spot = feet.relative(side);
         BlockPos below = spot.below();
         if (this.mc.level.getBlockState(spot).canBeReplaced() && this.mc.level.getBlockState(below).isFaceSturdy(this.mc.level, below, Direction.UP)) {
            return spot;
         }
      }

      return null;
   }

   private void leave(String note, boolean ok) {
      this.endNote = note;
      this.endedWell = ok;
      this.enter(SpawnerProtect.State.LEAVING);
   }

   private void tickLeaving() {
      press(this.mc.options.keyShift, false);
      this.mc.gameMode.stopDestroyBlock();
      if (this.stateTicks == 1) {
         int left = this.count(Items.SPAWNER);
         boolean fine = this.endedWell && left == 0;
         this.info("%s", new Object[]{this.endNote.isEmpty() ? "Done." : this.endNote});
         if (this.webhook.enabled()) {
            this.webhook
               .send(
                  new Webhook.Embed(
                        left > 0 ? "Spawners remain in your inventory" : "Task completed",
                        (this.endNote.isEmpty() ? "The collection has ended." : this.endNote)
                           + (this.disconnectWhenDone.get() ? " Disconnecting in two seconds." : ""),
                        fine ? 3066993 : 15844367
                     )
                     .field("Left in inventory", left + " spawners", true)
                     .field("Next step", this.disconnectWhenDone.get() ? "Disconnect from server" : "Stay in game", true)
                     .context()
               );
         }
      }

      if (this.stateTicks > 40) {
         ClientPacketListener connection = this.mc.getConnection();
         boolean leaving = this.disconnectWhenDone.get() && connection != null;
         if (leaving) {
            Leaving.onPurpose("Spawner Protect");
         }

         if (leaving) {
            connection.getConnection().disconnect(Component.literal("[Spawner Protect] " + (this.endNote.isEmpty() ? "Done." : this.endNote)));
         }

         this.toggle();
      }
   }

   private BlockPos findSpawner(LocalPlayer player) {
      BlockPos found = null;
      double best = 1.7976931348623157E308;

      for (BlockPos pos : BlockPos.withinBoxByManhattanDistance(player.blockPosition(), 16, 16, 16)) {
         if (this.mc.level.getBlockState(pos).is(Blocks.SPAWNER) && !this.skipped.contains(pos.asLong())) {
            double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < best) {
               best = distance;
               found = pos.immutable();
            }
         }
      }

      return found;
   }

   private BlockPos findBlock(Block block, int radius) {
      BlockPos found = null;
      double best = 1.7976931348623157E308;
      BlockPos origin = this.mc.player.blockPosition();

      for (BlockPos pos : BlockPos.withinBoxByManhattanDistance(origin, radius, radius, radius)) {
         if (this.mc.level.getBlockState(pos).is(block)) {
            double distance = origin.distSqr(pos);
            if (distance < best) {
               best = distance;
               found = pos.immutable();
            }
         }
      }

      return found;
   }

   private ItemEntity nearestDrop(LocalPlayer player) {
      ItemEntity nearest = null;
      double best = 256.0;

      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (entity instanceof ItemEntity item && item.getItem().is(Items.SPAWNER) && !item.hasPickUpDelay() && !this.ignoredDrops.contains(item.getId())) {
            double distance = player.distanceToSqr(item);
            if (distance < best) {
               best = distance;
               nearest = item;
            }
         }
      }

      return nearest;
   }

   private static boolean roomForSpawner(LocalPlayer player) {
      Inventory inventory = player.getInventory();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.isEmpty() || stack.is(Items.SPAWNER) && stack.getCount() < stack.getMaxStackSize()) {
            return true;
         }
      }

      return false;
   }

   private static int countIn(Inventory inventory, Item item) {
      int total = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getItem(i);
         if (stack.is(item)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private int count(Item item) {
      return this.mc.player == null ? 0 : countIn(this.mc.player.getInventory(), item);
   }

   private static boolean silkPickaxe(ItemStack stack) {
      return !stack.isEmpty() && stack.is(ItemTags.PICKAXES) && Utils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH) > 0;
   }

   private boolean holdSilkPickaxe(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      if (silkPickaxe(inventory.getItem(inventory.getSelectedSlot()))) {
         return true;
      } else {
         for (int i = 0; i < 9; i++) {
            if (silkPickaxe(inventory.getItem(i))) {
               inventory.setSelectedSlot(i);
               return true;
            }
         }

         return false;
      }
   }

   private boolean holdItem(LocalPlayer player, Item item) {
      Inventory inventory = player.getInventory();
      if (inventory.getItem(inventory.getSelectedSlot()).is(item)) {
         return true;
      } else {
         for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).is(item)) {
               inventory.setSelectedSlot(i);
               return true;
            }
         }

         for (int ix = 9; ix < 36; ix++) {
            if (inventory.getItem(ix).is(item)) {
               if (this.stateTicks % 10 == 0) {
                  this.mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, ix, inventory.getSelectedSlot(), ContainerInput.SWAP, player);
               }

               return false;
            }
         }

         return false;
      }
   }

   private void walkToward(Vec3 at, double stopWithin) {
      LocalPlayer player = this.mc.player;
      double dx = at.x - player.getX();
      double dz = at.z - player.getZ();
      double yawTo = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
      double off = Math.abs(Mth.wrapDegrees(yawTo - player.getYRot()));
      boolean go = off < 35.0 && Math.hypot(dx, dz) > stopWithin;
      press(this.mc.options.keyUp, go);
      if (!go) {
         this.stuck = 0;
         this.anchor = null;
         press(this.mc.options.keyJump, false);
      } else {
         if (this.anchor == null || ++this.anchorTicks >= 20) {
            if (this.anchor != null && player.position().distanceTo(this.anchor) < 0.25) {
               this.stuck += 20;
            } else {
               this.stuck = 0;
            }

            this.anchor = player.position();
            this.anchorTicks = 0;
         }

         press(this.mc.options.keyJump, this.stuck >= 40 && this.stuck < 120 && player.tickCount % 8 < 3);
         if (this.stuck >= 120) {
            if (this.target != null) {
               this.skipped.add(this.target.asLong());
            }

            this.target = null;
            this.stuck = 0;
            this.anchor = null;
         }
      }
   }

   private void stopMoving() {
      press(this.mc.options.keyUp, false);
      press(this.mc.options.keyJump, false);
      if (this.mc.gameMode != null) {
         this.mc.gameMode.stopDestroyBlock();
      }

      this.anchor = null;
      this.stuck = 0;
   }

   private void releaseAll() {
      if (this.mc.options != null) {
         for (KeyMapping key : new KeyMapping[]{this.mc.options.keyUp, this.mc.options.keyJump, this.mc.options.keyShift}) {
            key.setDown(false);
         }

         if (this.mc.gameMode != null) {
            this.mc.gameMode.stopDestroyBlock();
         }
      }
   }

   private static void press(KeyMapping key, boolean down) {
      if (key.isDown() != down) {
         key.setDown(down);
         if (key.isDown() != down) {
            key.setDown(!down);
         }
      }
   }

   private static enum State {
      WAITING("Standby"),
      MINING("Collecting"),
      CHEST("Ender chest"),
      DEPOSIT("Storing"),
      BUY("Buying chest"),
      PLACE("Placing chest"),
      LEAVING("Leaving");

      final String title;

      private State(String title) {
         this.title = title;
      }
   }
}

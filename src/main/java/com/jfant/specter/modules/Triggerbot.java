package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.pvp.AttackTiming;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.Melee;
import com.jfant.specter.pvp.Pace;
import com.jfant.specter.pvp.PvpLock;
import com.jfant.specter.pvp.Targets;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public class Triggerbot extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<Integer> reaction = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("reaction")
            .description("Ticks before hitting a target that has just come under the crosshair (20 = one second).")
            .defaultValue(3)
            .range(0, 10)
            .sliderRange(0, 10)
            .build()
      );
   private final Setting<Integer> spread = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("random-spread")
            .description("Up to this many extra ticks on the reaction, and on the pause after the swing fills.")
            .defaultValue(2)
            .range(0, 4)
            .sliderRange(0, 4)
            .build()
      );
   private final Setting<Triggerbot.Crits> crits = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("crits"))
                  .description("Off: hit as soon as ready. Smart: on the way up from a jump, wait to land the hit as a crit. Only: hit only with a crit."))
               .defaultValue(Triggerbot.Crits.Smart))
            .build()
      );
   private final Setting<Integer> critWait = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("crit-wait")
            .description("With Smart crits, the longest a full swing is held for a crit before it goes anyway.")
            .defaultValue(8)
            .range(1, 20)
            .sliderRange(1, 20)
            .visible(() -> this.crits.get() == Triggerbot.Crits.Smart)
            .build()
      );
   private final Setting<Boolean> holdAttack = this.sgGeneral
      .add(new BoolSetting.Builder().name("hold-attack").description("Only while you hold the attack button.").defaultValue(false).build());
   private final Setting<Boolean> weaponsOnly = this.sgGeneral
      .add(new BoolSetting.Builder().name("weapons-only").description("Only with a sword, axe, mace or trident in hand.").defaultValue(true).build());
   private final Setting<Boolean> players = this.sgTargets
      .add(new BoolSetting.Builder().name("players").description("Hit players (never ones on your Meteor friends list).").defaultValue(true).build());
   private final Setting<Boolean> hostiles = this.sgTargets
      .add(new BoolSetting.Builder().name("hostiles").description("Hit hostile mobs.").defaultValue(false).build());
   private final Setting<Boolean> invisible = this.sgTargets
      .add(new BoolSetting.Builder().name("invisible").description("Hit invisible targets too.").defaultValue(false).build());
   private final Setting<Boolean> debug = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Say in chat (only you see it) the timing of each hit: reaction, how long the swing sat full, crit or not.")
            .defaultValue(false)
            .build()
      );
   private final AttackTiming timing = new AttackTiming();
   private long tick;
   private long seenAt;

   public Triggerbot() {
      super(
         SpecterClientAddon.PVP, "triggerbot", "Hits the player your crosshair is on when your swing is full, after a human reaction time. You do the aiming."
      );
   }

   @Override
   public void onActivate() {
      this.timing.lose();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.tick++;
      LocalPlayer player = this.mc.player;
      if (player != null && Hands.canAct()) {
         Entity target = this.mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
         if (target != null && Targets.fightable(target, this.players.get(), this.hostiles.get()) && (this.invisible.get() || !target.isInvisible())) {
            if (target.getId() != this.timing.target()) {
               this.seenAt = this.tick;
            }

            this.timing.see(target.getId(), this.tick, Pace.ticks(this.reaction.get(), this.spread.get()));
            if (!this.weaponsOnly.get() || Melee.isWeapon(player.getMainHandItem())) {
               if (!this.holdAttack.get() || Input.isPressed(this.mc.options.keyAttack)) {
                  if (!PvpLock.heldByOther(this) && !PvpLock.actedThisTick()) {
                     float cooldown = player.getAttackStrengthScale(0.5F);
                     if (this.timing.ready(this.tick, cooldown, Pace.ticks(0, Math.min(1, this.spread.get())))) {
                        boolean crit = Melee.wouldCrit(player, target);
                        switch ((Triggerbot.Crits)this.crits.get()) {
                           case Off:
                           default:
                              break;
                           case Smart:
                              if (!crit && Melee.critComing(player) && this.timing.chargedFor(this.tick) < this.critWait.get().intValue()) {
                                 return;
                              }
                              break;
                           case Only:
                              if (!crit) {
                                 return;
                              }
                        }

                        long charged = this.timing.chargedFor(this.tick);
                        if (Hands.attack(target)) {
                           this.timing.attacked();
                           if (this.debug.get()) {
                              this.info(
                                 "Hit %s: %d ticks after it came under the crosshair, swing full for %d, %s.",
                                 new Object[]{target.getName().getString(), this.tick - this.seenAt, charged, crit ? "crit" : "no crit"}
                              );
                           }
                        }
                     }
                  }
               }
            }
         } else {
            this.timing.lose();
         }
      } else {
         this.timing.lose();
      }
   }

   public static enum Crits {
      Off,
      Smart,
      Only;
   }
}

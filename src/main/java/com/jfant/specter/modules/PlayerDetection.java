package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.bypass.BypassToast;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.ModuleListSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringListSetting;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.entity.fakeplayer.FakePlayerEntity;
import com.jfant.specter.core.utils.player.PlayerUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.WireframeEntityRenderer;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.webhook.Webhook;
import com.jfant.specter.webhook.WebhookSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerDetection extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgSkeleton = this.settings.createGroup("Skeleton");
   private final SettingGroup sgDetection = this.settings.createGroup("Detection");
   private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");
   private final Setting<PlayerDetection.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Box draws the hitbox; Wireframe draws the player's model. Invisible players always get a box - their model is not drawn."))
               .defaultValue(PlayerDetection.Mode.Box))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Double> fillOpacity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fill-opacity")
            .description("The opacity of the shape fill.")
            .defaultValue(0.3)
            .range(0.0, 1.0)
            .sliderMax(1.0)
            .build()
      );
   private final Setting<Double> fadeDistance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fade-distance")
            .description("The distance from a player where the color begins to fade.")
            .defaultValue(3.0)
            .min(0.0)
            .sliderMax(12.0)
            .build()
      );
   private final Setting<SettingColor> playersColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("players-color")
            .description("The color of players. Friends use your friend color.")
            .defaultValue(new SettingColor(100, 200, 255, 255))
            .build()
      );
   private final Setting<Boolean> invisible = this.sgColors
      .add(new BoolSetting.Builder().name("invisible-players").description("Draw invisible players in their own color.").defaultValue(true).build());
   private final Setting<SettingColor> invisibleColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("invisible-color")
            .description("The color of invisible players.")
            .defaultValue(new SettingColor(20, 50, 200, 255))
            .visible(this.invisible::get)
            .build()
      );
   private final Setting<Boolean> skeleton = this.sgSkeleton
      .add(
         new BoolSetting.Builder()
            .name("skeleton")
            .description("Draw a stick-figure skeleton inside each player that moves with their arms, legs, head and body.")
            .defaultValue(false)
            .build()
      );
   private final Setting<SettingColor> skeletonColor = this.sgSkeleton
      .add(
         new ColorSetting.Builder()
            .name("skeleton-color")
            .description("Color of the skeleton lines.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(this.skeleton::get)
            .build()
      );
   private final Setting<Boolean> distanceColors = this.sgSkeleton
      .add(
         new BoolSetting.Builder()
            .name("distance-colors")
            .description("Color each skeleton by distance instead: green close, through yellow and orange, to red at 60 blocks.")
            .defaultValue(false)
            .visible(this.skeleton::get)
            .build()
      );
   private final Setting<Boolean> legs = this.sgSkeleton
      .add(new BoolSetting.Builder().name("legs").description("Include the hips and legs.").defaultValue(true).visible(this.skeleton::get).build());
   private final SettingGroup sgTracer = this.settings.createGroup("Tracer");
   private final Setting<Boolean> tracer = this.sgTracer
      .add(new BoolSetting.Builder().name("tracer").description("Draw a big line from your crosshair to each player the module has detected.").defaultValue(true).build());
   private final Setting<SettingColor> tracerColor = this.sgTracer
      .add(
         new ColorSetting.Builder()
            .name("tracer-color")
            .description("Color of the tracer.")
            .defaultValue(new SettingColor(0, 110, 255, 255))
            .visible(this.tracer::get)
            .build()
      );
   private final Setting<Integer> tracerWidth = this.sgTracer
      .add(
         new IntSetting.Builder()
            .name("tracer-width")
            .description("How thick the tracer looks on screen, in pixels. It gets wider with distance so it stays about the same size near or far.")
            .defaultValue(6)
            .min(1)
            .sliderRange(1, 20)
            .visible(this.tracer::get)
            .build()
      );
   private final Setting<PlayerDetection.Notify> notify = this.sgDetection
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("notification-mode"))
                  .description("How to tell you a player showed up: a line in chat, a pop-up, both, or neither."))
               .defaultValue(PlayerDetection.Notify.Both))
            .build()
      );
   private final Setting<Boolean> sound = this.sgDetection
      .add(new BoolSetting.Builder().name("sound").description("Play a sound when a player shows up. Only you hear it.").defaultValue(true).build());
   private final Setting<List<Module>> modulesToToggle = this.sgDetection
      .add(
         new ModuleListSetting.Builder()
            .name("modules-to-toggle")
            .description("Modules to switch (on to off, or off to on) the moment a player shows up - once for the visit, not again for every extra player.")
            .build()
      );
   private final Setting<List<String>> whitelist = this.sgDetection
      .add(new StringListSetting.Builder().name("user-whitelist").description("Player names that are never announced. Case does not matter.").build());
   private final Setting<Boolean> ignoreFriends = this.sgDetection
      .add(new BoolSetting.Builder().name("ignore-friends").description("Do not announce players on your friends list.").defaultValue(true).build());
   private final Setting<Integer> repeatDelay = this.sgDetection
      .add(
         new IntSetting.Builder()
            .name("repeat-delay")
            .description(
               "Seconds a player has to be out of sight before showing up again counts as new. Also how long the area must be clear before the next visit switches your modules again."
            )
            .defaultValue(30)
            .min(0)
            .sliderRange(0, 300)
            .build()
      );
   private final WebhookSettings webhook = new WebhookSettings(this.sgWebhook, "Player Detection");
   private static final float PLAYER_SCALE = 0.9375F;
   private static final float MODEL_DROP = 1.501F;
   private final Map<String, Long> lastSeen = new HashMap<>();
   private Set<String> present = new HashSet<>();
   private Set<String> presentNow = new HashSet<>();
   private final List<Player> newcomers = new ArrayList<>();
   private ClientLevel lastWorld;
   private boolean visit;
   private long clearSince;
   private int ticks;
   private final Color lineColor = new Color();
   private final Color sideColor = new Color();
   private final Color distanceColor = new Color();
   private final Color tracerLine = new Color();
   private final double[] tracerSideA = new double[3];
   private final double[] tracerSideB = new double[3];
   private final Matrix4f bodyMatrix = new Matrix4f();
   private final Matrix4f partMatrix = new Matrix4f();
   private final Quaternionf partRotation = new Quaternionf();
   private final Vector3f a = new Vector3f();
   private final Vector3f b = new Vector3f();

   public PlayerDetection() {
      super(
         SpecterClientAddon.ESP,
         "player-detection",
         "Renders players through walls, invisible players in dark blue, with an optional skeleton - and tells you when one shows up."
      );
   }

   @Override
   public void onActivate() {
      this.resetDetection();
   }

   @Override
   public void onDeactivate() {
      this.resetDetection();
   }

   private void resetDetection() {
      this.lastSeen.clear();
      this.present.clear();
      this.presentNow.clear();
      this.newcomers.clear();
      this.lastWorld = null;
      this.visit = false;
      this.clearSince = 0L;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         if (this.mc.level != this.lastWorld) {
            this.resetDetection();
            this.lastWorld = this.mc.level;
         }

         long now = System.currentTimeMillis();
         long gap = this.repeatDelay.get().intValue() * 1000L;
         this.newcomers.clear();
         this.presentNow.clear();
         boolean anyone = false;

         for (Player player : this.mc.level.players()) {
            if (player != this.mc.player && !player.isRemoved() && !(player instanceof FakePlayerEntity) && !this.ignored(player)) {
               anyone = true;
               String key = player.getGameProfile().name().toLowerCase(Locale.ROOT);
               Long before = this.lastSeen.put(key, now);
               this.presentNow.add(key);
               if (!this.present.contains(key) && (before == null || now - before > gap)) {
                  this.newcomers.add(player);
               }
            }
         }

         Set<String> swap = this.present;
         this.present = this.presentNow;
         this.presentNow = swap;
         if (anyone) {
            this.clearSince = 0L;
         } else if (this.visit) {
            if (this.clearSince == 0L) {
               this.clearSince = now;
            } else if (now - this.clearSince >= gap) {
               this.visit = false;
            }
         }

         if (++this.ticks % 200 == 0) {
            this.lastSeen.values().removeIf(at -> now - at > gap + 60000L);
         }

         if (!this.newcomers.isEmpty()) {
            this.announce(this.newcomers);
         }
      } else {
         this.resetDetection();
      }
   }

   private boolean ignored(Player player) {
      if (this.ignoreFriends.get() && Friends.get().isFriend(player)) {
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

   private void announce(List<Player> players) {
      StringBuilder names = new StringBuilder();

      for (Player player : players) {
         if (!names.isEmpty()) {
            names.append(", ");
         }

         names.append(player.getGameProfile().name()).append(" (").append(Math.round(this.mc.player.distanceTo(player))).append(" blocks)");
      }

      PlayerDetection.Notify mode = this.notify.get();
      boolean toast = mode == PlayerDetection.Notify.Toast || mode == PlayerDetection.Notify.Both;
      if (mode == PlayerDetection.Notify.Chat || mode == PlayerDetection.Notify.Both) {
         this.info("%s: %s", new Object[]{players.size() == 1 ? "Player detected" : "Players detected", names});
      }

      if (toast) {
         BypassToast.addOrUpdate(new ItemStack(Items.PLAYER_HEAD), "Player Detected", Component.literal(names.toString()), this.sound.get());
      } else if (this.sound.get()) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F, 1.0F));
      }

      if (!this.visit) {
         this.visit = true;

         for (Module module : this.modulesToToggle.get()) {
            if (module != this) {
               module.toggle();
            }
         }
      }

      if (this.webhook.enabled()) {
         Webhook.Embed embed = new Webhook.Embed("Player detected", names.toString(), 15158332);

         for (Player player : players) {
            embed.field(
               player.getGameProfile().name(),
               Webhook.coordinates(player.getBlockX(), player.getBlockY(), player.getBlockZ())
                  + "\n"
                  + Math.round(this.mc.player.distanceTo(player))
                  + " blocks away",
               true
            );
         }

         this.webhook.send(embed.context());
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.level != null && this.mc.player != null) {
         for (Player player : this.mc.level.players()) {
            if (player != this.mc.player && !player.isRemoved()) {
               boolean invis = player.isInvisible();
               if (!invis || this.invisible.get()) {
                  this.drawPlayer(event, player, invis);
               }

               if (this.skeleton.get()) {
                  this.drawSkeleton(event, player);
               }

               if (this.tracer.get() && this.present.contains(player.getGameProfile().name().toLowerCase(Locale.ROOT))) {
                  this.drawTracer(event, player);
               }
            }
         }
      }
   }

   /**
    * A big line from the crosshair to the player's chest: a thin line plus a flat ribbon turned to face the camera
    * (drawn from both sides), whose half-width grows with the distance from the camera so it keeps about the same
    * size on screen near or far.
    */
   private void drawTracer(Render3DEvent event, Player player) {
      Vec3 from = RenderUtils.center;
      if (from == null) {
         return;
      }

      double px = Mth.lerp((double)event.tickDelta, player.xOld, player.getX());
      double py = Mth.lerp((double)event.tickDelta, player.yOld, player.getY()) + player.getBbHeight() * 0.7;
      double pz = Mth.lerp((double)event.tickDelta, player.zOld, player.getZ());
      SettingColor set = this.tracerColor.get();
      this.tracerLine.set(set.r, set.g, set.b, set.a);
      event.renderer.line(from.x, from.y, from.z, px, py, pz, this.tracerLine);
      Vec3 cam = this.mc.gameRenderer.mainCamera().position();
      double dx = px - from.x;
      double dy = py - from.y;
      double dz = pz - from.z;
      double perUnit = this.tracerWidth.get() * 5.5E-4;
      double[] sideA = this.tracerSideA;
      double[] sideB = this.tracerSideB;
      if (this.ribbonSide(dx, dy, dz, from.x - cam.x, from.y - cam.y, from.z - cam.z, perUnit, sideA)
         && this.ribbonSide(dx, dy, dz, px - cam.x, py - cam.y, pz - cam.z, perUnit, sideB)) {
         double ax1 = from.x - sideA[0];
         double ay1 = from.y - sideA[1];
         double az1 = from.z - sideA[2];
         double ax2 = from.x + sideA[0];
         double ay2 = from.y + sideA[1];
         double az2 = from.z + sideA[2];
         double bx1 = px + sideB[0];
         double by1 = py + sideB[1];
         double bz1 = pz + sideB[2];
         double bx2 = px - sideB[0];
         double by2 = py - sideB[1];
         double bz2 = pz - sideB[2];
         event.renderer.quad(ax1, ay1, az1, ax2, ay2, az2, bx1, by1, bz1, bx2, by2, bz2, this.tracerLine);
         event.renderer.quad(bx2, by2, bz2, bx1, by1, bz1, ax2, ay2, az2, ax1, ay1, az1, this.tracerLine);
      }
   }

   /** The ribbon's sideways half-step at a point: across both the line and the view ray, {@code perUnit * distance} long. False if the two are parallel. */
   private boolean ribbonSide(double dx, double dy, double dz, double vx, double vy, double vz, double perUnit, double[] out) {
      double sx = dy * vz - dz * vy;
      double sy = dz * vx - dx * vz;
      double sz = dx * vy - dy * vx;
      double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
      if (len < 1.0E-9) {
         return false;
      }

      double width = perUnit * Math.max(0.05, Math.sqrt(vx * vx + vy * vy + vz * vz));
      out[0] = sx / len * width;
      out[1] = sy / len * width;
      out[2] = sz / len * width;
      return true;
   }


   private void drawPlayer(Render3DEvent event, Player player, boolean invis) {
      double alpha = this.fadeAlpha(player);
      if (alpha != 0.0) {
         Color base = invis ? this.invisibleColor.get() : PlayerUtils.getPlayerColor(player, this.playersColor.get());
         this.lineColor.set(base.r, base.g, base.b, (int)(base.a * alpha));
         this.sideColor.set(this.lineColor).a((int)(this.lineColor.a * this.fillOpacity.get()));
         if (this.mode.get() == PlayerDetection.Mode.Wireframe && !invis) {
            WireframeEntityRenderer.render(event, player, 1.0, this.sideColor, this.lineColor, this.shapeMode.get());
         } else {
            double x = Mth.lerp((double)event.tickDelta, player.xOld, player.getX()) - player.getX();
            double y = Mth.lerp((double)event.tickDelta, player.yOld, player.getY()) - player.getY();
            double z = Mth.lerp((double)event.tickDelta, player.zOld, player.getZ()) - player.getZ();
            AABB box = player.getBoundingBox();
            event.renderer
               .box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, this.sideColor, this.lineColor, this.shapeMode.get(), 0);
         }
      }
   }

   private double fadeAlpha(Entity entity) {
      double dist = PlayerUtils.squaredDistanceToCamera(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
      double fadeDist = Math.pow(this.fadeDistance.get(), 2.0);
      double alpha = 1.0;
      if (dist <= fadeDist * fadeDist) {
         alpha = (float)(Math.sqrt(dist) / fadeDist);
      }

      if (alpha <= 0.075) {
         alpha = 0.0;
      }

      return alpha;
   }

   private void drawSkeleton(Render3DEvent event, Player player) {
      EntityRenderer<Entity, EntityRenderState> renderer = (EntityRenderer<Entity, EntityRenderState>)this.mc.getEntityRenderDispatcher().getRenderer(player);
      EntityRenderState state = renderer.createRenderState(player, event.tickDelta);
      if (state instanceof AvatarRenderState playerState) {
         if (renderer instanceof LivingEntityRenderer<?, ?, ?> living) {
            if (living.getModel() instanceof PlayerModel model) {
               model.setupAnim(playerState);
               Vec3 var17 = renderer.getRenderOffset(state);
               double ox = Mth.lerp((double)event.tickDelta, player.xOld, player.getX()) + var17.x;
               double oy = Mth.lerp((double)event.tickDelta, player.yOld, player.getY()) + var17.y;
               double oz = Mth.lerp((double)event.tickDelta, player.zOld, player.getZ()) + var17.z;
               float scale = playerState.scale;
               this.bodyMatrix.identity().scale(scale).rotateY((float)Math.toRadians(180.0F - playerState.bodyRot));
               if (playerState.isFallFlying) {
                  if (!playerState.isAutoSpinAttack) {
                     this.bodyMatrix.rotateX((float)Math.toRadians(playerState.fallFlyingScale() * (-90.0F - playerState.xRot)));
                  }

                  if (playerState.shouldApplyFlyingYRot) {
                     this.bodyMatrix.rotateY(playerState.flyingYRot);
                  }
               } else if (playerState.swimAmount > 0.0F) {
                  float lean = playerState.isInWater ? -90.0F - playerState.xRot : -90.0F;
                  this.bodyMatrix.rotateX((float)Math.toRadians(Mth.lerp(playerState.swimAmount, 0.0F, lean)));
                  if (playerState.isVisuallySwimming) {
                     this.bodyMatrix.translate(0.0F, -1.0F, 0.3F);
                  }
               }

               this.bodyMatrix.scale(-1.0F, -1.0F, 1.0F).scale(0.9375F).translate(0.0F, -1.501F, 0.0F);
               Color color = this.distanceColors.get() ? this.colorForDistance(ox, oy, oz) : this.skeletonColor.get();
               this.bone(event, model.head, 0.0F, 0.0F, 0.0F, 0.0F, -8.0F, 0.0F, ox, oy, oz, color);
               this.bone(event, model.body, 0.0F, 0.0F, 0.0F, 0.0F, 12.0F, 0.0F, ox, oy, oz, color);
               this.joint(model.rightArm, 0.0F, 0.0F, 0.0F, this.a);
               this.joint(model.leftArm, 0.0F, 0.0F, 0.0F, this.b);
               this.line(event, ox, oy, oz, color);
               this.bone(event, model.rightArm, 0.0F, 0.0F, 0.0F, -1.0F, 10.0F, 0.0F, ox, oy, oz, color);
               this.bone(event, model.leftArm, 0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 0.0F, ox, oy, oz, color);
               if (this.legs.get()) {
                  this.joint(model.rightLeg, 0.0F, 0.0F, 0.0F, this.a);
                  this.joint(model.leftLeg, 0.0F, 0.0F, 0.0F, this.b);
                  this.line(event, ox, oy, oz, color);
                  this.bone(event, model.rightLeg, 0.0F, 0.0F, 0.0F, 0.0F, 12.0F, 0.0F, ox, oy, oz, color);
                  this.bone(event, model.leftLeg, 0.0F, 0.0F, 0.0F, 0.0F, 12.0F, 0.0F, ox, oy, oz, color);
               }
            }
         }
      }
   }

   private void bone(
      Render3DEvent event, ModelPart part, float x1, float y1, float z1, float x2, float y2, float z2, double ox, double oy, double oz, Color color
   ) {
      this.joint(part, x1, y1, z1, this.a);
      this.joint(part, x2, y2, z2, this.b);
      this.line(event, ox, oy, oz, color);
   }

   private void joint(ModelPart part, float x, float y, float z, Vector3f out) {
      this.partMatrix.set(this.bodyMatrix).translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
      if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
         this.partMatrix.rotate(this.partRotation.rotationZYX(part.zRot, part.yRot, part.xRot));
      }

      this.partMatrix.transformPosition(x / 16.0F, y / 16.0F, z / 16.0F, out);
   }

   private void line(Render3DEvent event, double ox, double oy, double oz, Color color) {
      event.renderer.line(ox + this.a.x, oy + this.a.y, oz + this.a.z, ox + this.b.x, oy + this.b.y, oz + this.b.z, color);
   }

   private Color colorForDistance(double x, double y, double z) {
      double percent = Math.min(1.0, this.mc.player.getEyePosition().distanceTo(new Vec3(x, y, z)) / 60.0);
      int r;
      int g;
      if (percent < 0.33) {
         r = (int)(percent / 0.33 * 255.0);
         g = 255;
      } else if (percent < 0.66) {
         r = 255;
         g = 255 - (int)((percent - 0.33) / 0.33 * 90.0);
      } else {
         r = 255;
         g = 165 - (int)((percent - 0.66) / 0.34 * 165.0);
      }

      return this.distanceColor.set(r, g, 0, 255);
   }

   public static enum Mode {
      Box,
      Wireframe;
   }

   public static enum Notify {
      Off,
      Chat,
      Toast,
      Both;
   }
}

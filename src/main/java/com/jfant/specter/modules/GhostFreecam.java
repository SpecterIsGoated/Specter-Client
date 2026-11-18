package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.camera.GhostView;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.meteor.MouseScrollEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import com.jfant.specter.render.Facing;
import com.jfant.specter.render.PurpleCard;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

public class GhostFreecam extends Module {
   private static final double MIN_SPEED = 0.05;
   private static final double MAX_SPEED = 20.0;
   private static final double MIN_ZOOM = 1.5;
   private static final double MAX_ZOOM = 10.0;
   private static final double BLOCKS_PER_SPEED = 10.0;
   private static final long POPUP_MS = 1500L;
   private static final float ZOOM_EASE = 14.0F;
   private static final int LOW = 6;
   private static final int LOW_COLOR = -38037;
   private static final String TITLE_TEXT = "Ghost Freecam";
   private static final int PAD = 7;
   private static final int DIVIDER_Y = 11;
   private static final int HEADER = 15;
   private static final int LINE = 11;
   private static final int MIN_WIDTH = 120;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgZoom = this.settings.createGroup("Zoom");
   private final SettingGroup sgCard = this.settings.createGroup("Card");
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description("How fast the camera flies. Sprint doubles it.")
            .defaultValue(0.8)
            .range(0.05, 20.0)
            .sliderRange(0.05, 20.0)
            .build()
      );
   private final Setting<Boolean> scrollSpeed = this.sgGeneral
      .add(new BoolSetting.Builder().name("scroll-speed").description("Scroll the mouse wheel to fly faster or slower.").defaultValue(true).build());
   private final Setting<Boolean> keepMining = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-mining")
            .description(
               "If you were holding attack when it came on, your body keeps mining - and keeps walking if you were walking too - after you let go. Walking without mining stops."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> keepSneaking = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-sneaking")
            .description("If you were sneaking when it came on, your body keeps sneaking.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> zoom = this.sgZoom
      .add(
         new BoolSetting.Builder()
            .name("zoom")
            .description("Hold the zoom key to zoom the camera in. Scroll while holding it to change how far.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Keybind> zoomKey = this.sgZoom
      .add(new KeybindSetting.Builder().name("zoom-key").description("Hold to zoom.").defaultValue(Keybind.none()).visible(this.zoom::get).build());
   private final Setting<Double> zoomAmount = this.sgZoom
      .add(
         new DoubleSetting.Builder()
            .name("zoom-amount")
            .description("How many times closer the zoom brings things.")
            .defaultValue(3.0)
            .range(1.5, 10.0)
            .sliderRange(1.5, 10.0)
            .visible(this.zoom::get)
            .build()
      );
   private final Setting<Boolean> card = this.sgCard
      .add(
         new BoolSetting.Builder()
            .name("card")
            .description("Show a card with the camera's position, how far it is from your body, speed, facing, and your body's health and hunger.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> speedPopup = this.sgCard
      .add(
         new BoolSetting.Builder()
            .name("speed-popup")
            .description("Show the new speed above the hotbar for a moment when you scroll.")
            .defaultValue(true)
            .build()
      );
   private final CardSpot spot = new CardSpot(this, this.sgCard, Corner.MiddleLeft, 4, 0);
   private final Setting<Double> scale = this.sgCard
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card and the speed pop-up. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .build()
      );
   public double x;
   public double y;
   public double z;
   public float yaw;
   public float pitch;
   private final GhostView.KeyLatch latch = new GhostView.KeyLatch();
   private final List<GhostFreecam.Row> rows = new ArrayList<>();
   private CameraType savedPerspective;
   private double savedFovEffect;
   private boolean savedBob;
   private long lastFrameNanos;
   private float zoomNow = 1.0F;
   private boolean live;
   private boolean handingOff;
   private String popup = "";
   private long popupAt;
   private int infoWidth;

   public GhostFreecam() {
      super(SpecterClientAddon.TOOLS, "ghost-freecam", "Fly the camera anywhere while your body stays still. The server never sees you move or turn.");
      GhostView.init();
   }

   public static GhostFreecam on() {
      Modules modules = Modules.get();
      GhostFreecam module = modules == null ? null : modules.get(GhostFreecam.class);
      return module != null && module.isActive() && module.live ? module : null;
   }

   public static float zoomFactor() {
      GhostFreecam module = on();
      return module == null ? 1.0F : module.zoomNow;
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null && this.mc.level != null && this.mc.options != null) {
         this.live = true;
         GhostView.enter();
         GhostFreelook look = GhostFreelook.on();
         if (look != null) {
            Vec3 pos = this.mc.gameRenderer.mainCamera().position();
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.yaw = look.yaw;
            this.pitch = look.pitch;
            this.savedPerspective = look.handOff(this.latch);
         } else {
            Vec3 eye = this.mc.player.getEyePosition();
            this.x = eye.x;
            this.y = eye.y;
            this.z = eye.z;
            this.yaw = this.mc.player.getYRot();
            this.pitch = this.mc.player.getXRot();
            this.savedPerspective = this.mc.options.getCameraType();
            if (this.savedPerspective == CameraType.THIRD_PERSON_FRONT) {
               this.yaw += 180.0F;
               this.pitch = -this.pitch;
            }

            this.latch.capture(this.keepMining.get(), this.keepSneaking.get());
         }

         Options options = this.mc.options;
         this.savedFovEffect = options.fovEffectScale().get();
         this.savedBob = options.bobView().get();
         options.fovEffectScale().set(0.0);
         options.bobView().set(false);
         options.setCameraType(CameraType.FIRST_PERSON);
         this.holdBodyStill();
         this.lastFrameNanos = System.nanoTime();
         this.zoomNow = 1.0F;
         this.infoWidth = 0;
         this.popupAt = 0L;
      } else {
         this.toggle();
      }
   }

   @Override
   public void onDeactivate() {
      if (this.live) {
         this.live = false;
         Options options = this.mc.options;
         options.fovEffectScale().set(this.savedFovEffect);
         options.bobView().set(this.savedBob);
         if (this.handingOff) {
            this.handingOff = false;
         } else {
            options.setCameraType(this.savedPerspective != null ? this.savedPerspective : CameraType.FIRST_PERSON);
            this.latch.release();

            for (KeyMapping key : this.movementKeys()) {
               key.setDown(false);
            }
         }

         this.zoomNow = 1.0F;
         GhostView.exit();
      }
   }

   CameraType handOff(GhostView.KeyLatch into) {
      into.copyFrom(this.latch);
      this.handingOff = true;
      CameraType original = this.savedPerspective;
      this.toggle();
      return original;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.toggle();
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundRespawnPacket) {
         this.mc.execute(() -> {
            if (this.isActive()) {
               this.toggle();
            }
         });
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null) {
         this.holdBodyStill();
         if (this.mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            this.mc.options.setCameraType(CameraType.FIRST_PERSON);
         }
      }
   }

   private void holdBodyStill() {
      for (KeyMapping key : this.movementKeys()) {
         if (!this.latch.holds(key)) {
            key.setDown(false);
         }
      }

      this.latch.apply();
   }

   private List<KeyMapping> movementKeys() {
      Options o = this.mc.options;
      return List.of(o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift);
   }

   public void stepFrame() {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (float)(now - this.lastFrameNanos) / 1.0E9F);
      this.lastFrameNanos = now;
      if (!(dt <= 0.0F)) {
         float zoomTarget = this.zooming() ? this.zoomAmount.get().floatValue() : 1.0F;
         this.zoomNow = this.zoomNow + (zoomTarget - this.zoomNow) * (1.0F - (float)Math.exp(-dt * 14.0F));
         if (Math.abs(this.zoomNow - zoomTarget) < 0.001F) {
            this.zoomNow = zoomTarget;
         }

         Options o = this.mc.options;
         boolean free = this.mc.gui.screen() == null && !this.keyDown(292);
         if (free) {
            double forward = (this.held(o.keyUp) ? 1 : 0) - (this.held(o.keyDown) ? 1 : 0);
            double strafe = (this.held(o.keyRight) ? 1 : 0) - (this.held(o.keyLeft) ? 1 : 0);
            double rise = (this.held(o.keyJump) ? 1 : 0) - (this.held(o.keyShift) ? 1 : 0);
            if (forward != 0.0 || strafe != 0.0 || rise != 0.0) {
               double step = this.speed.get() * (this.held(o.keySprint) ? 2 : 1) * 10.0 * dt;
               double rad = Math.toRadians(this.yaw);
               double sin = Math.sin(rad);
               double cos = Math.cos(rad);
               this.x += (-sin * forward - cos * strafe) * step;
               this.z += (cos * forward - sin * strafe) * step;
               this.y += rise * step;
            }
         }
      }
   }

   public void turn(double deltaYaw, double deltaPitch) {
      this.yaw = Mth.wrapDegrees(this.yaw + (float)(deltaYaw / this.zoomNow));
      this.pitch = Mth.clamp(this.pitch + (float)(deltaPitch / this.zoomNow), -90.0F, 90.0F);
   }

   private boolean zooming() {
      return this.zoom.get() && this.mc.gui.screen() == null && this.zoomKey.get().isPressed();
   }

   private boolean held(KeyMapping binding) {
      Key key = InputConstants.getKey(binding.saveString());
      if (key == InputConstants.UNKNOWN) {
         return false;
      } else {
         long window = this.mc.getWindow().handle();
         return key.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == 1 : GLFW.glfwGetKey(window, key.getValue()) == 1;
      }
   }

   private boolean keyDown(int glfwKey) {
      return GLFW.glfwGetKey(this.mc.getWindow().handle(), glfwKey) == 1;
   }

   @EventHandler(
      priority = 100
   )
   private void onScroll(MouseScrollEvent event) {
      if (event.value != 0.0 && this.mc.gui.screen() == null) {
         int dir = event.value > 0.0 ? 1 : -1;
         if (this.zooming()) {
            this.zoomAmount.set(Math.clamp(this.zoomAmount.get() + dir * 0.5, 1.5, 10.0));
            this.showPopup(String.format(Locale.ROOT, "Zoom %.1fx", this.zoomAmount.get()));
         } else {
            if (!this.scrollSpeed.get()) {
               return;
            }

            double step = Math.max(0.25, this.speed.get() * 0.15);
            this.speed.set(Math.clamp(this.speed.get() + dir * step, 0.05, 20.0));
            this.showPopup(String.format(Locale.ROOT, "Speed %.2f", this.speed.get()));
         }

         event.cancel();
      }
   }

   private void showPopup(String text) {
      this.popup = text;
      this.popupAt = System.currentTimeMillis();
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player != null && !this.mc.gui.hud.isHidden() && !this.mc.debugEntries.isOverlayVisible()) {
         GuiGraphicsExtractor ctx = event.graphics;
         Font tr = this.mc.font;
         float s = this.scale.get().floatValue();
         if (this.speedPopup.get() && this.popupAt != 0L && System.currentTimeMillis() - this.popupAt < 1500L) {
            this.drawPopup(ctx, tr, s);
         }

         if (this.card.get()) {
            this.drawCard(ctx, tr, s);
         }
      }
   }

   private void drawPopup(GuiGraphicsExtractor ctx, Font tr, float s) {
      int w = CardText.width(this.popup) + 16;
      int h = 18;
      float px = (this.mc.getWindow().getGuiScaledWidth() - w * s) / 2.0F;
      float py = this.mc.getWindow().getGuiScaledHeight() - 62 - h * s;
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(Math.round(px), Math.round(py));
      matrices.scale(s, s);
      PurpleCard.card(ctx, 0, 0, w, h);
      CardText.draw(ctx, this.popup, 8.0F, 5.0F, -1, true);
      matrices.popMatrix();
   }

   private void drawCard(GuiGraphicsExtractor ctx, Font tr, float s) {
      this.buildRows();
      int needed = CardText.width("Ghost Freecam");

      for (GhostFreecam.Row r : this.rows) {
         needed = Math.max(needed, r.width(tr));
      }

      this.infoWidth = Math.max(this.infoWidth, Math.max(needed, 120));
      int boxWidth = this.infoWidth + 14;
      int boxHeight = 15 + this.rows.size() * 11 - 3 + 14;
      this.spot.place(boxWidth * s, boxHeight * s);
      float cx = this.spot.x();
      float cy = this.spot.y();
      Matrix3x2fStack matrices = ctx.pose();
      matrices.pushMatrix();
      matrices.translate(Math.round(cx), Math.round(cy));
      matrices.scale(s, s);
      PurpleCard.card(ctx, 0, 0, boxWidth, boxHeight);
      matrices.translate(7.0F, 7.0F);
      CardText.draw(ctx, "Ghost Freecam", 0.0F, 0.0F, -1065473, true);
      ctx.fill(0, 11, this.infoWidth, 12, 1440926975);
      int rowY = 15;

      for (GhostFreecam.Row r : this.rows) {
         CardText.draw(ctx, r.label, 0.0F, (float)rowY, -1911561, true);
         if (r.paired()) {
            CardText.draw(ctx, r.value, (float)(CardText.width(r.label) + 4), (float)rowY, r.color, true);
            int vx2 = this.infoWidth - CardText.width(r.value2);
            CardText.draw(ctx, r.value2, (float)vx2, (float)rowY, r.color2, true);
            CardText.draw(ctx, r.label2, (float)(vx2 - 4 - CardText.width(r.label2)), (float)rowY, -1911561, true);
         } else {
            int vx = this.infoWidth - r.valueWidth(tr);
            CardText.draw(ctx, r.value, (float)vx, (float)rowY, r.color, true);
            if (!r.extra.isEmpty()) {
               CardText.draw(ctx, r.extra, (float)(vx + CardText.width(r.value) + 4), (float)rowY, -4149795, true);
            }
         }

         rowY += 11;
      }

      matrices.popMatrix();
   }

   private void buildRows() {
      this.rows.clear();
      this.rows
         .add(
            new GhostFreecam.Row(
               "XYZ", format((int)Math.floor(this.x)) + "  " + format((int)Math.floor(this.y)) + "  " + format((int)Math.floor(this.z)), "", -1
            )
         );
      double distance = this.mc.player.getEyePosition().distanceTo(new Vec3(this.x, this.y, this.z));
      this.rows.add(new GhostFreecam.Row("Distance", format((int)Math.round(distance)), -1, "Speed", String.format(Locale.ROOT, "%.2f", this.speed.get()), -1));
      this.rows.add(new GhostFreecam.Row("Facing", Facing.name(this.yaw), Facing.axes(this.yaw), -1));
      float health = this.mc.player.getHealth();
      String hp = health == Math.floor(health) ? Integer.toString((int)health) : String.format(Locale.ROOT, "%.1f", health);
      int food = this.mc.player.getFoodData().getFoodLevel();
      this.rows.add(new GhostFreecam.Row("Health", hp, health <= 6.0F ? -38037 : -1, "Hunger", Integer.toString(food), food <= 6 ? -38037 : -1));
   }

   private static String format(int value) {
      return String.format(Locale.ROOT, "%,d", value);
   }

   private record Row(String label, String value, String extra, int color, String label2, String value2, int color2) {
      private static final int PAIR_GAP = 10;

      Row(String label, String value, String extra, int color) {
         this(label, value, extra, color, "", "", 0);
      }

      Row(String label, String value, int color, String label2, String value2, int color2) {
         this(label, value, "", color, label2, value2, color2);
      }

      boolean paired() {
         return !this.label2.isEmpty();
      }

      int valueWidth(Font tr) {
         return CardText.width(this.value) + (this.extra.isEmpty() ? 0 : 4 + CardText.width(this.extra));
      }

      int width(Font tr) {
         return !this.paired()
            ? CardText.width(this.label) + 10 + this.valueWidth(tr)
            : CardText.width(this.label) + 4 + CardText.width(this.value) + 10 + CardText.width(this.label2) + 4 + CardText.width(this.value2);
      }
   }
}

package com.jfant.specter.phone;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.modules.DonutPhone;
import com.jfant.specter.phone.api.PhoneAppProvider;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.api.PhoneSound;
import com.jfant.specter.phone.apps.BuiltinApps;
import com.jfant.specter.phone.core.PhoneAnimationController;
import com.jfant.specter.phone.core.PhoneApplicationManager;
import com.jfant.specter.phone.core.PhoneApplicationRegistry;
import com.jfant.specter.phone.core.PhoneConfig;
import com.jfant.specter.phone.core.PhoneHomeScreen;
import com.jfant.specter.phone.core.PhoneInputManager;
import com.jfant.specter.phone.core.PhoneNavigationManager;
import com.jfant.specter.phone.core.PhoneNotificationManager;
import com.jfant.specter.phone.core.PhoneSystemUi;
import com.jfant.specter.phone.core.PhoneTheme;
import com.jfant.specter.phone.core.PhoneThemeManager;
import com.jfant.specter.phone.core.Wallpapers;
import com.jfant.specter.phone.gfx.PhoneTextures;
import com.jfant.specter.phone.render.PhoneRenderer;
import com.jfant.specter.phone.ui.PhoneCanvas;
import com.jfant.specter.phone.ui.PhoneUi;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Donut Phone: the operating system. Owns the registry, the running apps, navigation, notifications, theme,
 * animation and the interface drawn over the 3D phone's glass, and is the one place the game hooks call into
 * (chat key, first-person hands, frame start, GUI).
 */
public final class PhoneOS {
   private static PhoneOS instance;

   private final PhoneConfig config = PhoneConfig.load();
   private final PhoneApplicationRegistry registry = new PhoneApplicationRegistry();
   private final PhoneApplicationManager apps = new PhoneApplicationManager(this.registry);
   private final PhoneNavigationManager navigation = new PhoneNavigationManager(this.apps);
   private final PhoneNotificationManager notifications = new PhoneNotificationManager(this.config);
   private final PhoneThemeManager themes = new PhoneThemeManager(this.config);
   private final PhoneAnimationController animation = new PhoneAnimationController();
   private final PhoneSystemUi system = new PhoneSystemUi();
   private final PhoneHomeScreen home = new PhoneHomeScreen();
   private final PhoneInputManager input = new PhoneInputManager(this);
   private final PhoneCanvas canvas = new PhoneCanvas();
   private final PhoneRenderer.ScreenRect rect = new PhoneRenderer.ScreenRect();
   private double lastRender = -1;
   private boolean firstRunShown;
   /** Showing the lock screen (set each time the phone comes out, if the lock screen is on). */
   private boolean locked;
   @Nullable
   private java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> captureDone;
   private int captureFrames;
   /** The view to go back to after a selfie, or null. */
   private net.minecraft.client.CameraType cameraBefore;
   private boolean hudWasHidden;

   private PhoneOS() {
      this.notifications.setHooks(() -> this.sound(PhoneSound.NOTIFY), id -> {
         PhoneApplicationRegistry.Entry e = this.registry.get(id);
         return e == null ? id : e.info().name();
      }, () -> this.animation.visible());
      Runnable tap = () -> this.sound(PhoneSound.TAP);
      this.home.ui.tapSound = tap;
      this.system.bars.tapSound = tap;
      this.system.shadeUi.tapSound = tap;
      this.system.switcherUi.tapSound = tap;
      this.system.lockUi.tapSound = tap;
   }

   public static PhoneOS get() {
      if (instance == null) throw new IllegalStateException("Donut Phone is not started");
      return instance;
   }

   public static boolean started() {
      return instance != null;
   }

   /** Start-up: built-in apps, apps from other mods ({@code specter:phone} entrypoint), event hooks. */
   public static void init() {
      if (instance != null) return;
      instance = new PhoneOS();
      BuiltinApps.register(instance.registry);
      for (EntrypointContainer<PhoneAppProvider> c : FabricLoader.getInstance().getEntrypointContainers("specter:phone", PhoneAppProvider.class)) {
         String mod = c.getProvider().getMetadata().getId();
         try {
            c.getEntrypoint().registerApps((info, factory) -> instance.registry.register(info, factory, mod));
         } catch (Throwable t) {
            SpecterClientAddon.LOG.error("Donut Phone: apps from {} could not be loaded", mod, t);
         }
      }
      SpecterCore.EVENT_BUS.subscribe(instance);
      SpecterClientAddon.LOG.info("Donut Phone ready with {} apps", instance.registry.all().size());
   }

   // ============================================================================================== accessors

   public PhoneConfig config() {
      return this.config;
   }

   public PhoneApplicationRegistry registry() {
      return this.registry;
   }

   public PhoneApplicationManager apps() {
      return this.apps;
   }

   public PhoneNavigationManager navigation() {
      return this.navigation;
   }

   public PhoneNotificationManager notifications() {
      return this.notifications;
   }

   public PhoneThemeManager themes() {
      return this.themes;
   }

   public PhoneAnimationController animation() {
      return this.animation;
   }

   public PhoneSystemUi system() {
      return this.system;
   }

   public PhoneHomeScreen home() {
      return this.home;
   }

   public PhoneInputManager input() {
      return this.input;
   }

   public PhoneRenderer.ScreenRect rect() {
      return this.rect;
   }

   @Nullable
   public static DonutPhone module() {
      return Modules.get() == null ? null : Modules.get().get(DonutPhone.class);
   }

   /** Is the module on (the chat key opens the phone)? */
   public static boolean enabled() {
      DonutPhone m = module();
      return m != null && m.isActive();
   }

   private static double now() {
      return System.nanoTime() / 1.0E9;
   }

   // ============================================================================================== open / close

   /** When the phone screen was last replaced by another one (-1 = it wasn't). */
   private double leftAt = -1;

   public boolean isOut() {
      return this.animation.visible();
   }

   public void open() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.gui.screen() != null) return;
      PhoneRenderer.ensureResources();
      this.animation.open(now());
      this.locked = this.config.lockScreen;
      this.notifications.clearBanners();
      mc.gui.setScreen(new PhoneScreen());
      this.navigation.resume();
      this.sound(PhoneSound.OPEN);
   }

   /** Puts the phone away (closes the phone screen; the 3D phone animates down by itself). */
   public void close() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui.screen() instanceof PhoneScreen) {
         mc.gui.setScreen(null);
      } else {
         this.screenGone();
      }
   }

   /**
    * The phone screen was replaced. A server menu that shows up over it (Auto RTP's /rtp menu, which Specter+ puts
    * back under the phone straight away) gets a moment before the phone goes away, so the phone doesn't drop and come
    * back up on every RTP; anything else puts it away on the next frame.
    */
   void screenLeft() {
      this.leftAt = now();
      this.input.reset();
   }

   /** The phone screen is shown again (also after Specter+ put it back over a menu). */
   void screenBack() {
      this.leftAt = -1;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && this.animation.state() != PhoneAnimationController.State.OPEN
         && this.animation.state() != PhoneAnimationController.State.OPENING) {
         PhoneRenderer.ensureResources();
         this.animation.open(now());
         this.navigation.resume();
      }
   }

   private void checkScreenLeft() {
      if (this.leftAt < 0) return;
      net.minecraft.client.gui.screens.Screen s = Minecraft.getInstance().gui.screen();
      if (s instanceof PhoneScreen) {
         this.leftAt = -1;
      } else if (!(s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) || now() - this.leftAt > 1.5) {
         this.leftAt = -1;
         this.screenGone();
      }
   }

   /** The phone screen left (closed, or replaced by another screen such as chat or the death screen). */
   void screenGone() {
      if (this.animation.state() == PhoneAnimationController.State.OPEN || this.animation.state() == PhoneAnimationController.State.OPENING) {
         this.animation.close(now());
         this.sound(PhoneSound.CLOSE);
      }
      this.system.closeShade();
      this.input.reset();
      this.navigation.suspend();
      this.config.markDirty();
   }

   /** Puts the phone away and opens the real chat box (with text typed in, if any). */
   public void openChat(@Nullable String text) {
      Minecraft mc = Minecraft.getInstance();
      this.close();
      if (text == null || text.isEmpty()) mc.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
      else mc.gui.openChatAndAddText(ChatComponent.ChatMethod.MESSAGE, text);
   }

   // ============================================================================================== navigation

   public void openApp(String id, @Nullable String route) {
      this.openApp(id, route, PhoneCanvas.WIDTH / 2, PhoneCanvas.HEIGHT / 2);
   }

   public void openApp(String id, @Nullable String route, float fromX, float fromY) {
      PhoneApplicationManager.Running r = this.apps.launch(id);
      if (r == null) {
         this.toast("That app isn't installed.");
         return;
      }
      this.system.closeShade();
      this.navigation.open(r, route, fromX, fromY, now());
      this.notifications.markRead(id);
   }

   public void openFromNotification(PhoneNotificationManager.Notification n) {
      this.unlock();
      this.openApp(n.app, n.route);
   }

   public boolean locked() {
      return this.locked;
   }

   public void unlock() {
      this.locked = false;
      this.notifications.clearBanners();
   }

   /** A notification button: runs its command on the server once, then the buttons go away. */
   public void runAction(PhoneNotificationManager.Notification n, com.jfant.specter.phone.api.NotificationAction a) {
      Minecraft mc = Minecraft.getInstance();
      if (!n.actionsLive() || mc.getConnection() == null) return;
      mc.getConnection().sendCommand(a.command());
      n.actions = java.util.List.of();
      n.read = true;
      this.toast("Sent /" + a.command());
   }

   public void goHome() {
      if (this.locked) {
         this.locked = false;
         return;
      }
      this.system.closeShade();
      if (this.navigation.layer() == PhoneNavigationManager.Layer.HOME) this.home.firstPage();
      this.navigation.home(now());
   }

   public void back() {
      if (this.system.shadeOpen()) {
         this.system.closeShade();
         return;
      }
      switch (this.navigation.layer()) {
         case APP -> {
            PhoneApplicationManager.Running r = this.navigation.current();
            PhoneApplication app = r == null ? null : r.app();
            boolean handled = app != null && this.apps.guard(r, "back", app::back, false);
            if (!handled) this.navigation.home(now());
         }
         case SWITCHER -> this.navigation.home(now());
         case HOME -> this.close();
      }
   }

   public void switcher() {
      this.system.closeShade();
      this.navigation.switcher(now());
   }

   public void openShade() {
      this.system.openShade();
   }

   public void killApp(String id) {
      this.navigation.forget(id);
      this.apps.kill(id);
   }

   public void restartApp(String id) {
      this.killApp(id);
      this.openApp(id, null);
   }

   public void closeAllApps() {
      for (PhoneApplicationManager.Running r : this.apps.recents()) this.killApp(r.info().id());
      this.navigation.home(now());
   }

   public void toast(String text) {
      this.system.toast(text);
   }

   // ============================================================================================== sound

   public boolean soundsOn() {
      DonutPhone m = module();
      return m == null || m.sounds();
   }

   public void toggleSounds() {
      DonutPhone m = module();
      if (m != null) m.setSounds(!m.sounds());
   }

   public void sound(PhoneSound sound) {
      if (!this.soundsOn()) return;
      Minecraft mc = Minecraft.getInstance();
      SoundEvent event;
      float pitch, volume;
      switch (sound) {
         case TAP -> {
            event = SoundEvents.UI_BUTTON_CLICK.value();
            pitch = 1.9F;
            volume = 0.12F;
         }
         case OPEN -> {
            event = SoundEvents.BOOK_PAGE_TURN;
            pitch = 1.4F;
            volume = 0.5F;
         }
         case CLOSE -> {
            event = SoundEvents.BOOK_PAGE_TURN;
            pitch = 1.1F;
            volume = 0.4F;
         }
         case NOTIFY -> {
            event = SoundEvents.NOTE_BLOCK_CHIME.value();
            pitch = 1.6F;
            volume = 0.45F;
         }
         case SUCCESS -> {
            event = SoundEvents.NOTE_BLOCK_BELL.value();
            pitch = 1.8F;
            volume = 0.4F;
         }
         case ERROR -> {
            event = SoundEvents.NOTE_BLOCK_BASS.value();
            pitch = 0.8F;
            volume = 0.5F;
         }
         case LIKE -> {
            event = SoundEvents.AMETHYST_BLOCK_CHIME;
            pitch = 1.6F;
            volume = 0.6F;
         }
         default -> {
            event = SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT;
            pitch = 1.5F;
            volume = 0.5F;
         }
      }
      mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
   }

   // ============================================================================================== frame / tick hooks

   /** Start of every frame, before the GUI is extracted: advance the animation once for GUI and hand alike. */
   public void frameStart() {
      this.drawnUnderHud = false;
      this.checkScreenLeft();
      DonutPhone m = module();
      if (m != null) {
         boolean left = Minecraft.getInstance().options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
         this.animation.configure((float)m.scale(), (float)m.offsetX(), (float)m.offsetY(), (float)m.speed(), left);
      }
      this.animation.frame(now());
      PhoneTextures.frame();
      if (this.captureDone != null && ++this.captureFrames == (this.cameraBefore != null ? 5 : 3)) {
         // the previous frame was drawn without the phone and the HUD (and, for a selfie, from the front): grab it
         java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> done = this.captureDone;
         Minecraft mc = Minecraft.getInstance();
         net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> mc.execute(() -> {
            this.endCapture();
            done.accept(image);
         }));
      }
      if (this.animation.visible()) {
         float fov = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.hudFov;
         PhoneRenderer.project(this.animation.model, fov > 1 ? fov : 70.0F, this.rect);
      } else {
         this.rect.valid = false;
      }
   }

   /** First-person hands: true when the phone was drawn (the vanilla hands are then skipped). */
   public boolean submitHands(PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState) {
      if (this.captureDone != null) return true;
      if (!this.animation.visible() || !PhoneRenderer.ready()) return false;
      DonutPhone m = module();
      String wall = this.themes.wallpaper();
      Identifier tex = this.wallpaperTexture(wall);
      PhoneRenderer.submitFirstPerson(poseStack, collector, playerState, this.animation.model, this.themes.theme(), tex, wallSwatch(wall),
         this.animation.screenOn(), m == null || m.showArm());
      return true;
   }

   /** Is the 3D phone being drawn this frame (first person, HUD shown)? Otherwise the interface draws its own frame. */
   private boolean threeD() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null && mc.options.getCameraType().isFirstPerson() && !mc.gui.hud.isHidden() && !mc.player.isSpectator() && !mc.player.isSleeping();
   }

   @Nullable
   private Identifier wallpaperTexture(String id) {
      if (com.jfant.specter.phone.core.CustomWallpapers.isCustom(id)) return com.jfant.specter.phone.core.CustomWallpapers.texture(id);
      int w = Math.max(16, Math.round(this.rect.valid ? 180 * this.canvasPixels() : 360) / 16 * 16);
      int h = Math.round(w * PhoneCanvas.HEIGHT / PhoneCanvas.WIDTH);
      Identifier tex = PhoneTextures.paintLater("wall/" + id, w, h, Wallpapers.get(id).painter());
      return tex != null ? tex : PhoneTextures.anySize("wall/" + id);
   }

   private static int wallSwatch(String id) {
      return com.jfant.specter.phone.core.CustomWallpapers.isCustom(id) ? com.jfant.specter.phone.core.CustomWallpapers.swatch() : Wallpapers.swatch(id);
   }

   /** A picture dropped on the game window (or picked in Settings) becomes the wallpaper. */
   public void useWallpaperFile(java.nio.file.Path file) {
      try {
         String id = com.jfant.specter.phone.core.CustomWallpapers.add(file);
         this.themes.setWallpaper(id);
         this.toast("Wallpaper set");
         this.sound(PhoneSound.SUCCESS);
      } catch (java.io.IOException e) {
         this.toast(e.getMessage() != null && e.getMessage().length() < 60 ? e.getMessage() : "Couldn't use that picture");
      }
   }

   private float canvasPixels() {
      return this.rect.scale * Minecraft.getInstance().getWindow().getGuiScale();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      PhoneRenderer.ensureResources();
      this.config.tick();
      this.notifications.tick();
      this.apps.tick(this.animation.interactive() ? this.navigation.current() : null);
      Minecraft mc = Minecraft.getInstance();
      if (!this.firstRunShown && mc.player != null && mc.player.tickCount > 60) {
         this.firstRunShown = true;
         if (!this.config.firstRunDone) {
            this.config.firstRunDone = true;
            this.config.markDirty();
            DonutPhone m = module();
            if (m != null && !m.isActive()) m.toggle();
            this.notifications.post("settings", "Welcome to Donut Phone", "Press your chat key to take out your phone. Press it again for regular chat.", null);
         }
      }
      if (this.leftAt < 0 && this.animation.visible() && !(mc.gui.screen() instanceof PhoneScreen) && this.animation.interactive()) this.screenGone();
   }

   @EventHandler
   private void onLeave(GameLeftEvent event) {
      if (this.captureDone != null) this.endCapture();
      if (Minecraft.getInstance().gui.screen() instanceof PhoneScreen) Minecraft.getInstance().gui.setScreen(null);
      this.leftAt = -1;
      this.animation.snapClosed();
      this.navigation.suspend();
      this.navigation.home(now());
      this.apps.killAll();
      this.config.saveNow();
   }

   // ============================================================================================== interface

   /**
    * Draws the phone's interface over the glass. Called from the phone screen while it is open, and from the HUD
    * while the phone is being put away (so the interface fades out instead of vanishing).
    */
   public void renderInterface(GuiGraphicsExtractor ctx, double mouseGuiX, double mouseGuiY) {
      if (!this.rect.valid || this.captureDone != null) return;
      boolean threeD = this.threeD();
      float alpha = this.animation.uiAlpha();
      if (threeD && alpha <= 0.001F) return;
      double now = now();
      float dt = this.lastRender < 0 ? 0.016F : (float)Math.min(0.1, now - this.lastRender);
      this.lastRender = now;
      PhoneTheme theme = this.themes.theme();
      PhoneCanvas c = this.canvas;
      if (!threeD) this.drawFlatFrame(ctx, this.animation.progress());
      c.begin(ctx, theme, this.rect.x, this.rect.y, this.rect.scale);
      try {
         if (!threeD) {
            float p = this.animation.progress();
            c.push(0, (1 - p) * 60);
            c.alpha(Math.min(1, p * 1.5F));
            alpha = 1;
         } else {
            c.alpha(alpha);
         }
         // Nothing may leave the glass: an app sliding up, the shade sliding down or a page moving sideways would
         // otherwise poke its (dark) background out past the screen's edge onto the bezel and the world.
         c.clip(0, 0, c.width, c.height);
         float mx = this.input.toPhoneX(mouseGuiX), my = this.input.toPhoneY(mouseGuiY);
         if (!this.animation.interactive()) {
            mx = -1000;
            my = -1000;
         }
         this.drawScreen(c, theme, mx, my, dt, now);
         c.popAlpha();
      } catch (Throwable t) {
         SpecterClientAddon.LOG.error("Donut Phone: drawing failed", t);
      } finally {
         c.end();
      }
   }

   private void drawScreen(PhoneCanvas c, PhoneTheme theme, float mx, float my, float dt, double now) {
      String wall = this.themes.wallpaper();
      Identifier tex = this.wallpaperTexture(wall);
      if (tex != null) c.image(tex, 0, 0, c.width, c.height);
      else if (com.jfant.specter.phone.core.CustomWallpapers.isCustom(wall)) c.rect(0, 0, c.width, c.height, com.jfant.specter.phone.core.CustomWallpapers.swatch());
      else c.gradient(0, 0, c.width, c.height, Wallpapers.get(wall).swatchTop(), Wallpapers.get(wall).swatchBottom());

      if (this.locked) {
         this.system.renderLock(c, this, mx, my, dt);
         c.gui().nextStratum();
         this.system.renderBars(c, this, false, mx, my, dt);
         this.system.renderShade(c, this, mx, my, dt);
         c.gui().nextStratum();
         c.cornerMask(0, 0, c.width, c.height, PhoneCanvas.SCREEN_RADIUS, 0xFF030305);
         c.circle(c.width / 2, PhoneSystemUi.HOLE_Y, PhoneSystemUi.HOLE_R, 0xFF020203);
         return;
      }
      PhoneNavigationManager nav = this.navigation;
      float k = nav.transition(now);
      float ease = 1 - (1 - k) * (1 - k) * (1 - k);
      PhoneNavigationManager.Layer layer = nav.layer(), from = nav.from();
      boolean lightTop = false;

      // what was on screen fades out underneath
      if (k < 1 && from != layer) {
         c.alpha(1 - ease);
         this.drawLayer(c, from, nav.fromApp(), -1000, -1000, dt, from == PhoneNavigationManager.Layer.APP && layer == PhoneNavigationManager.Layer.HOME ? ease * 30 : 0);
         c.popAlpha();
      }
      if (layer == PhoneNavigationManager.Layer.HOME) {
         c.alpha(k < 1 && from != layer ? ease : 1);
         this.drawLayer(c, layer, null, mx, my, dt, 0);
         c.popAlpha();
      } else {
         float slide = layer == PhoneNavigationManager.Layer.APP ? (1 - ease) * 26 : 0;
         c.alpha(k < 1 ? ease : 1);
         PhoneApplicationManager.Running app = nav.current();
         this.drawLayer(c, layer, app, mx, my, dt, slide);
         c.popAlpha();
         if (layer == PhoneNavigationManager.Layer.APP && app != null && app.app() != null) {
            PhoneApplication a = app.app();
            lightTop = this.apps.guard(app, "lightTop", a::lightTop, false);
         }
      }
      c.gui().nextStratum();
      this.system.renderBars(c, this, lightTop, mx, my, dt);
      this.system.renderShade(c, this, mx, my, dt);
      // round the glass and draw the punch-hole camera over everything
      c.gui().nextStratum();
      c.cornerMask(0, 0, c.width, c.height, PhoneCanvas.SCREEN_RADIUS, 0xFF030305);
      c.circle(c.width / 2, PhoneSystemUi.HOLE_Y, PhoneSystemUi.HOLE_R, 0xFF020203);
      c.circle(c.width / 2 - 0.9F, PhoneSystemUi.HOLE_Y - 0.9F, PhoneSystemUi.HOLE_R * 0.35F, 0x3346508C);
   }

   private void drawLayer(PhoneCanvas c, PhoneNavigationManager.Layer layer, @Nullable PhoneApplicationManager.Running app, float mx, float my, float dt, float dy) {
      c.push(0, dy);
      try {
         switch (layer) {
            case HOME -> this.home.render(c, this, mx, my, dt);
            case SWITCHER -> this.system.renderSwitcher(c, this, mx, my, dt);
            case APP -> {
               if (app == null) break;
               PhoneUi ui = app.ui;
               ui.begin(c, mx, my, dt);
               if (app.crash != null || app.app() == null) {
                  this.system.renderCrash(c, ui, this, app);
               } else {
                  PhoneApplication a = app.app();
                  c.rect(0, 0, c.width, c.height, c.theme().background());
                  float fx = mx, fy = my;
                  this.apps.guard(app, "render", () -> a.render(c, ui, fx, fy, dt));
               }
               ui.end();
            }
         }
      } finally {
         c.pop();
      }
   }

   /** Third person / hidden HUD: there is no 3D phone, so draw a flat one (frame and bezel) for the interface to sit in. */
   private void drawFlatFrame(GuiGraphicsExtractor ctx, float progress) {
      PhoneCanvas c = this.canvas;
      c.begin(ctx, this.themes.theme(), this.rect.x, this.rect.y, this.rect.scale);
      c.push(0, (1 - progress) * 60);
      c.alpha(Math.min(1, progress * 1.5F));
      float bezel = 3.3F / 65.4F * 180;
      c.shadow(-bezel, -bezel, c.width + bezel * 2, c.height + bezel * 2, 30, 8, 0x90000000);
      c.round(-bezel - 1.6F, -bezel - 1.6F, c.width + bezel * 2 + 3.2F, c.height + bezel * 2 + 3.2F, 30, 0xFF5B5866, 0xFF3A3742);
      c.round(-bezel, -bezel, c.width + bezel * 2, c.height + bezel * 2, 28.5F, 0xFF050507);
      c.popAlpha();
      c.end();
   }

   /** Set when {@link #hudPass} drew the interface this frame, so the phone screen does not draw it a second time. */
   private boolean drawnUnderHud;

   public boolean drawnUnderHud() {
      return this.drawnUnderHud;
   }

   /**
    * Called by the HUD just before the hotbar, hearts and the rest: the phone's interface (and the dim behind it) is
    * drawn here, under the HUD, exactly like the 3D phone under it. Drawn from the phone screen instead (on top of
    * everything) the hearts went over the phone's frame but under its glass, and the glass's square black corners
    * cut notches out of them (2026-11-25 screenshot). Also covers the phone going down and the moment a server menu
    * sits over it.
    */
   public void hudPass(GuiGraphicsExtractor ctx) {
      Minecraft mc = Minecraft.getInstance();
      boolean phoneScreen = mc.gui.screen() instanceof PhoneScreen;
      boolean closing = this.animation.state() == PhoneAnimationController.State.CLOSING && this.animation.uiAlpha() > 0.001F;
      if (!phoneScreen && !closing && this.leftAt < 0) return;
      if (this.captureDone != null) return;
      double mx = -10000, my = -10000;
      if (phoneScreen) {
         this.drawDim(ctx);
         double scale = mc.getWindow().getGuiScale();
         mx = mc.mouseHandler.xpos() / scale;
         my = mc.mouseHandler.ypos() / scale;
      }
      this.renderInterface(ctx, mx, my);
      // the hotbar and everything after it go on top
      ctx.nextStratum();
      this.drawnUnderHud = true;
   }

   /** The soft darkening at the top and bottom of the screen while the phone is up (module setting). */
   public void drawDim(GuiGraphicsExtractor ctx) {
      DonutPhone m = module();
      if (m == null || !m.dimBackground() || this.capturing()) return;
      int a = Math.round(70 * this.animation.progress());
      if (a <= 0) return;
      int w = ctx.guiWidth(), h = ctx.guiHeight();
      ctx.fillGradient(0, 0, w, h / 3, a << 24, 0);
      ctx.fillGradient(0, h * 2 / 3, w, h, 0, a << 24);
   }

   /** HUD hook: while the phone goes down after its screen closed, let the interface fade instead of vanishing. */
   public void renderClosingFade(GuiGraphicsExtractor ctx) {
      if (this.drawnUnderHud) return;
      if (this.animation.state() == PhoneAnimationController.State.CLOSING && this.animation.uiAlpha() > 0.001F
         && !(Minecraft.getInstance().gui.screen() instanceof PhoneScreen)) {
         this.renderInterface(ctx, -10000, -10000);
      }
   }

   // ============================================================================================== photos

   /**
    * Takes a photo of the world without the phone or the HUD: both are hidden for a couple of frames, the frame is
    * read back, then everything comes back. {@code done} gets the image on the game thread and must close it.
    */
   public void capture(java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> done) {
      this.capture(false, done);
   }

   /**
    * Takes a photo: the next frames are drawn without the phone and the HUD, then grabbed. A selfie turns the camera
    * round for the shot (Minecraft's front-facing third person, looking at your face) and back again afterwards.
    */
   public void capture(boolean selfie, java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> done) {
      if (this.captureDone != null) return;
      Minecraft mc = Minecraft.getInstance();
      this.captureDone = done;
      this.captureFrames = 0;
      this.hudWasHidden = mc.gui.hud.isHidden();
      if (!this.hudWasHidden) mc.gui.hud.toggle();
      this.cameraBefore = null;
      if (selfie && mc.player != null && mc.options.getCameraType() != net.minecraft.client.CameraType.THIRD_PERSON_FRONT) {
         this.cameraBefore = mc.options.getCameraType();
         mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
      }
      this.sound(PhoneSound.SHUTTER);
   }

   public boolean capturing() {
      return this.captureDone != null;
   }

   private void endCapture() {
      Minecraft mc = Minecraft.getInstance();
      if (!this.hudWasHidden && mc.gui.hud.isHidden()) mc.gui.hud.toggle();
      if (this.cameraBefore != null) {
         mc.options.setCameraType(this.cameraBefore);
         this.cameraBefore = null;
      }
      this.captureDone = null;
   }

   /** Game closing. */
   public void shutdown() {
      this.config.saveNow();
      this.notifications.saveNow();
      this.apps.flushAll();
   }
}

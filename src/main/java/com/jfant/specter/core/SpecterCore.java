package com.jfant.specter.core;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.OpenScreenEvent;
import com.jfant.specter.core.events.meteor.KeyEvent;
import com.jfant.specter.core.events.meteor.MouseClickEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.systems.Systems;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.jfant.specter.core.systems.modules.Categories;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.PostInit;
import com.jfant.specter.core.utils.PreInit;
import com.jfant.specter.core.utils.ReflectInit;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.Version;
import com.jfant.specter.core.utils.misc.input.KeyAction;
import com.jfant.specter.core.utils.misc.input.KeyBinds;
import com.jfant.specter.gui.screen.HudAddPanel;
import com.jfant.specter.gui.screen.HudElementPanel;
import com.jfant.specter.gui.screen.SpecterScreen;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

public class SpecterCore implements ClientModInitializer {
   public static final String MOD_ID = "specter";
   public static final ModMetadata MOD_META = ((ModContainer)FabricLoader.getInstance().getModContainer("specter").orElseThrow()).getMetadata();
   public static final String NAME = MOD_META.getName();
   public static final Version VERSION;
   public static final String BUILD_NUMBER;
   public static SpecterCore INSTANCE;
   public static Minecraft mc;
   public static final IEventBus EVENT_BUS = new EventBus();
   public static final File FOLDER = FabricLoader.getInstance().getGameDir().resolve("specter").toFile();
   public static final Logger LOG = LoggerFactory.getLogger(NAME);
   private boolean wasWidgetScreen;
   private boolean wasHudHiddenRoot;

   public void onInitializeClient() {
      if (INSTANCE == null) {
         INSTANCE = this;
      } else {
         mc = Minecraft.getInstance();
         if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            LOG.info("Force loading mixins");
            MixinEnvironment.getCurrentEnvironment().audit();
         }

         LOG.info("Initializing {}", NAME);
         if (!FOLDER.exists()) {
            FOLDER.getParentFile().mkdirs();
            FOLDER.mkdir();
         }

         EVENT_BUS.registerLambdaFactory("com.jfant.specter", (lookupInMethod, klass) -> (Lookup)lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
         ReflectInit.registerPackages();
         ReflectInit.init(PreInit.class);
         Categories.init();
         Systems.init();
         EVENT_BUS.subscribe(this);
         SpecterClientAddon.init();
         Modules.get().sortModules();
         Systems.load();
         ReflectInit.init(PostInit.class);
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Systems.save();
            GuiThemes.save();
         }));
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (mc.screen == null && mc.getOverlay() == null && KeyBinds.OPEN_COMMANDS.consumeClick()) {
         mc.setScreen(new ChatScreen(Config.get().prefix.get(), true));
      }
   }

   @EventHandler
   private void onKey(KeyEvent event) {
      if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matches(event.input)) {
         this.toggleGui();
      }
   }

   @EventHandler
   private void onMouseClick(MouseClickEvent event) {
      if (event.action == KeyAction.Press && KeyBinds.OPEN_GUI.matchesMouse(event.click)) {
         this.toggleGui();
      }
   }

   private void toggleGui() {
      if (Utils.canCloseGui()) {
         mc.screen.onClose();
      } else if (Utils.canOpenGui()) {
         mc.setScreen(new SpecterScreen());
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onOpenScreen(OpenScreenEvent event) {
      boolean guiScreen = event.screen instanceof WidgetScreen
         || event.screen instanceof SpecterScreen
         || event.screen instanceof HudElementPanel
         || event.screen instanceof HudAddPanel;
      if (guiScreen) {
         if (!this.wasWidgetScreen) {
            this.wasHudHiddenRoot = mc.options.hideGui;
         }

         if (GuiThemes.get().hideHUD() || this.wasHudHiddenRoot) {
            mc.options.hideGui = !(event.screen instanceof HudEditorScreen)
               && !(event.screen instanceof HudElementPanel)
               && !(event.screen instanceof HudAddPanel);
         }
      } else {
         if (this.wasWidgetScreen) {
            mc.options.hideGui = this.wasHudHiddenRoot;
         }

         this.wasHudHiddenRoot = mc.options.hideGui;
      }

      this.wasWidgetScreen = guiScreen;
   }

   public static Identifier identifier(String path) {
      return Identifier.fromNamespaceAndPath("specter", path);
   }

   static {
      String versionString = MOD_META.getVersion().getFriendlyString();
      if (versionString.contains("-")) {
         versionString = versionString.split("-")[0];
      }

      if (versionString.equals("${version}")) {
         versionString = "0.0.0";
      }

      VERSION = new Version(versionString);
      BUILD_NUMBER = MOD_META.getCustomValue("specter:build_number").getAsString();
   }
}

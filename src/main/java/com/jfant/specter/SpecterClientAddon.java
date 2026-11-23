package com.jfant.specter;

import com.jfant.specter.auto.AutoModules;
import com.jfant.specter.commands.AntiVanishCommand;
import com.jfant.specter.commands.BaseEspCommand;
import com.jfant.specter.commands.SusChunksCommand;
import com.jfant.specter.commands.SusFindsCommand;
import com.jfant.specter.commands.TimeSetCommand;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.systems.modules.Category;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.fx.WorldFx;
import com.jfant.specter.gui.SpecterGuiTheme;
import com.jfant.specter.modules.AhSniper;
import com.jfant.specter.modules.AimAssist;
import com.jfant.specter.modules.Aimbot;
import com.jfant.specter.modules.AnchorPvp;
import com.jfant.specter.modules.AntiVanish;
import com.jfant.specter.modules.AnticheatAlerts;
import com.jfant.specter.modules.ArmorHud;
import com.jfant.specter.modules.Atmosphere;
import com.jfant.specter.modules.AutoReconnect;
import com.jfant.specter.modules.AutoRelog;
import com.jfant.specter.modules.AutoSell;
import com.jfant.specter.modules.BaseEsp;
import com.jfant.specter.modules.Bioluminescence;
import com.jfant.specter.modules.BlockUpdateFinder;
import com.jfant.specter.modules.CaveAnalyzer;
import com.jfant.specter.modules.ColorGrading;
import com.jfant.specter.modules.Crosshair;
import com.jfant.specter.modules.CrystalPvp;
import com.jfant.specter.modules.CustomFov;
import com.jfant.specter.modules.DebrisFinder;
import com.jfant.specter.modules.DrillTunnelFinderEsp;
import com.jfant.specter.modules.DrownedDripstone;
import com.jfant.specter.modules.FakeBanScreen;
import com.jfant.specter.modules.GhostFreecam;
import com.jfant.specter.modules.GhostFreelook;
import com.jfant.specter.modules.HitEffects;
import com.jfant.specter.modules.JetMech;
import com.jfant.specter.modules.Relics;
import com.jfant.specter.modules.Halo;
import com.jfant.specter.modules.AngelWings;
import com.jfant.specter.modules.DeathAnimations;
import com.jfant.specter.modules.ItemPhysics;
import com.jfant.specter.modules.ItemEsp;
import com.jfant.specter.modules.ItemNametags;
import com.jfant.specter.modules.ElytraSaver;
import com.jfant.specter.modules.LegitAutoTool;
import com.jfant.specter.modules.SpearLunge;
import com.jfant.specter.modules.AutoCliper;
import com.jfant.specter.modules.DonutPhone;
import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.modules.HoleEsp;
import com.jfant.specter.modules.HomeMethod;
import com.jfant.specter.modules.HotbarScale;
import com.jfant.specter.modules.HumanTotem;
import com.jfant.specter.modules.InventoryCounter;
import com.jfant.specter.modules.InventoryScale;
import com.jfant.specter.modules.JumpReset;
import com.jfant.specter.modules.KeyThrow;
import com.jfant.specter.modules.LightDebug;
import com.jfant.specter.modules.MaceSwap;
import com.jfant.specter.modules.MarineLayer;
import com.jfant.specter.modules.MediaBaseFinder;
import com.jfant.specter.modules.ModuleList;
import com.jfant.specter.modules.NameProtectPlus;
import com.jfant.specter.modules.NebulaNight;
import com.jfant.specter.modules.NoInvLeak;
import com.jfant.specter.modules.OpponentStats;
import com.jfant.specter.modules.OrderAhTrader;
import com.jfant.specter.modules.PearlMethod;
import com.jfant.specter.modules.PlayerDetection;
import com.jfant.specter.modules.PrimeChunkFinder;
import com.jfant.specter.modules.PingSystem;
import com.jfant.specter.modules.Reach;
import com.jfant.specter.modules.RealisticRain;
import com.jfant.specter.modules.RedstoneOptimizer;
import com.jfant.specter.modules.RenderReset;
import com.jfant.specter.modules.ShieldBreaker;
import com.jfant.specter.modules.SkyLyrics;
import com.jfant.specter.modules.Snowfall;
import com.jfant.specter.modules.SoundDetector;
import com.jfant.specter.modules.SpawnerNotifier;
import com.jfant.specter.modules.SpawnerProtect;
import com.jfant.specter.modules.SpotifyHud;
import com.jfant.specter.modules.StaffWatch;
import com.jfant.specter.modules.StasisFinder;
import com.jfant.specter.modules.SusAreaAnalyzer;
import com.jfant.specter.modules.SusChunkFinderV2;
import com.jfant.specter.modules.SusFinds;
import com.jfant.specter.modules.SusChunks;
import com.jfant.specter.modules.SwingSpeed;
import com.jfant.specter.modules.TimeSet;
import com.jfant.specter.modules.TotemCounter;
import com.jfant.specter.modules.TraderEsp;
import com.jfant.specter.modules.Triggerbot;
import com.jfant.specter.modules.VisualPresets;
import com.jfant.specter.modules.WTap;
import com.jfant.specter.modules.WhereAreYou;
import com.jfant.specter.move.MoveModules;
import com.jfant.specter.notify.NotificationOverlay;
import com.jfant.specter.util.Leaving;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public final class SpecterClientAddon {
   public static final Logger LOG = LogUtils.getLogger();
   public static final Category BASES = new Category("Base Finding", () -> Items.ECHO_SHARD.getDefaultInstance());
   public static final Category ESP = new Category("ESP", () -> Items.ENDER_EYE.getDefaultInstance());
   public static final Category DONUT = new Category("Donut", () -> Items.GOLD_INGOT.getDefaultInstance());
   public static final Category HUD = new Category("HUD", () -> Items.SPYGLASS.getDefaultInstance());
   public static final Category TOOLS = new Category("Tools", () -> Items.COMPASS.getDefaultInstance());
   public static final Category PVP = new Category("PVP", () -> Items.DIAMOND_SWORD.getDefaultInstance());
   public static final Category VISUALS = new Category("Visuals", () -> Items.AMETHYST_SHARD.getDefaultInstance());
   public static final Category BLATANT = new Category("Blatant", () -> Items.NETHERITE_SWORD.getDefaultInstance());

   private SpecterClientAddon() {
   }

   public static void init() {
      LOG.info("Initializing Specter Client");
      GuiThemes.add(new SpecterGuiTheme());
      Modules.get().add(new SusChunks());
      Modules.get().add(new BaseEsp());
      Modules.get().add(new HoleEsp());
      Modules.get().add(new SpawnerNotifier());
      Modules.get().add(new StasisFinder());
      Modules.get().add(new SoundDetector());
      Modules.get().add(new SpawnerProtect());
      Modules.get().add(new DrownedDripstone());
      Modules.get().add(new PlayerDetection());
      Modules.get().add(new TraderEsp());
      Modules.get().add(new RenderReset());
      Modules.get().add(new BlockUpdateFinder());
      Memory.init();
      Leaving.init();
      Modules.get().add(new MediaBaseFinder());
      Modules.get().add(new DebrisFinder());
      Modules.get().add(new WhereAreYou());
      Modules.get().add(new SpotifyHud());
      Modules.get().add(new StaffWatch());
      Modules.get().add(new AntiVanish());
      Modules.get().add(new AnticheatAlerts());
      Modules.get().add(new ModuleList());
      Modules.get().add(new NebulaNight());
      Modules.get().add(new Atmosphere());
      Modules.get().add(new Crosshair());
      Modules.get().add(new HitEffects());
      Modules.get().add(new SkyLyrics());
      Modules.get().add(new TimeSet());
      Modules.get().add(new GhostFreecam());
      Modules.get().add(new GhostFreelook());
      Modules.get().add(new InventoryScale());
      Modules.get().add(new HotbarScale());
      Modules.get().add(new LightDebug());
      Modules.get().add(new PrimeChunkFinder());
      Modules.get().add(new CaveAnalyzer());
      Modules.get().add(new DrillTunnelFinderEsp());
      Modules.get().add(new SusAreaAnalyzer());
      Modules.get().add(new SusChunkFinderV2());
      Modules.get().add(new SusFinds());
      Modules.get().add(new AhSniper());
      Modules.get().add(new AutoSell());
      Modules.get().add(new OrderAhTrader());
      Modules.get().add(new NameProtectPlus());
      Modules.get().add(new NoInvLeak());
      Modules.get().add(new AutoRelog());
      Modules.get().add(new AutoReconnect());
      Modules.get().add(new HomeMethod());
      Modules.get().add(new PearlMethod());
      Modules.get().add(new SwingSpeed());
      Modules.get().add(new FakeBanScreen());
      Modules.get().add(new HumanTotem());
      Modules.get().add(new CrystalPvp());
      Modules.get().add(new AnchorPvp());
      Modules.get().add(new OpponentStats());
      Modules.get().add(new TotemCounter());
      Modules.get().add(new ArmorHud());
      Modules.get().add(new InventoryCounter());
      Modules.get().add(new Triggerbot());
      Modules.get().add(new AimAssist());
      Modules.get().add(new WTap());
      Modules.get().add(new JumpReset());
      Modules.get().add(new ShieldBreaker());
      Modules.get().add(new MaceSwap());
      Modules.get().add(new KeyThrow());
      Modules.get().add(new Aimbot());
      Modules.get().add(new Reach());
      Modules.get().add(new Bioluminescence());
      Modules.get().add(new MarineLayer());
      Modules.get().add(new ColorGrading());
      Modules.get().add(new CustomFov());
      Modules.get().add(new Snowfall());
      Modules.get().add(new VisualPresets());
      Modules.get().add(new RedstoneOptimizer());
      Modules.get().add(new PingSystem());
      Modules.get().add(new RealisticRain());
      Modules.get().add(new JetMech());
      Modules.get().add(new Relics());
      Modules.get().add(new Halo());
      Modules.get().add(new AngelWings());
      Modules.get().add(new DeathAnimations());
      Modules.get().add(new ItemPhysics());
      Modules.get().add(new ItemEsp());
      Modules.get().add(new ItemNametags());
      Modules.get().add(new ElytraSaver());
      Modules.get().add(new LegitAutoTool());
      Modules.get().add(new SpearLunge());
      Modules.get().add(new AutoCliper());
      Modules.get().add(new DonutPhone());
      PhoneOS.init();
      NotificationOverlay.init();
      WorldFx.init();
      AutoModules.register();
      MoveModules.register();
      Commands.add(new SusChunksCommand());
      Commands.add(new SusFindsCommand());
      Commands.add(new BaseEspCommand());
      Commands.add(new AntiVanishCommand());
      Commands.add(new TimeSetCommand());
   }

   private static void renameSavedModule(String from, String to) {
      Path file = new File(SpecterCore.FOLDER, "modules.nbt").toPath();
      if (Files.isRegularFile(file)) {
         try {
            CompoundTag root = NbtIo.read(file);
            if (root == null) {
               return;
            }

            ListTag modules = root.getListOrEmpty("modules");
            int fromIndex = -1;
            int toIndex = -1;

            for (int i = 0; i < modules.size(); i++) {
               String name = modules.getCompoundOrEmpty(i).getStringOr("name", "");
               if (name.equals(from)) {
                  fromIndex = i;
               } else if (name.equals(to)) {
                  toIndex = i;
               }
            }

            if (fromIndex < 0) {
               return;
            }

            Files.copy(file, file.resolveSibling("modules.nbt.before-" + to + "-rename.bak"), StandardCopyOption.REPLACE_EXISTING);
            modules.getCompoundOrEmpty(fromIndex).putString("name", to);
            if (toIndex >= 0) {
               modules.remove(toIndex);
            }

            NbtIo.write(root, file);
            LOG.info("Saved settings of {} now belong to {}", from, to);
         } catch (RuntimeException | IOException var9) {
            LOG.warn("Could not hand the saved settings of {} to {}", new Object[]{from, to, var9});
         }
      }
   }

   public static void registerCategories() {
      Modules.registerCategory(BASES);
      Modules.registerCategory(ESP);
      Modules.registerCategory(DONUT);
      Modules.registerCategory(HUD);
      Modules.registerCategory(TOOLS);
      Modules.registerCategory(PVP);
      Modules.registerCategory(VISUALS);
      Modules.registerCategory(BLATANT);
   }
}

package com.jfant.specter.core.systems;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.systems.accounts.Accounts;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.systems.profiles.Profiles;
import com.jfant.specter.core.systems.proxies.Proxies;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;

public class Systems {
   private static final Map<Class<? extends System>, System<?>> systems = new Reference2ReferenceOpenHashMap();
   private static final List<Runnable> preLoadTasks = new ArrayList<>(1);

   public static void addPreLoadTask(Runnable task) {
      preLoadTasks.add(task);
   }

   public static void init() {
      add(new Modules());
      Config config = new Config();
      System<?> configSystem = add(config);
      configSystem.init();
      configSystem.load();
      config.settings.registerColorSettings(null);
      add(new Friends());
      add(new Accounts());
      add(new Profiles());
      add(new Proxies());
      add(new Hud());
      SpecterCore.EVENT_BUS.subscribe(Systems.class);
   }

   public static System<?> add(System<?> system) {
      systems.put((Class<? extends System>)system.getClass(), system);
      SpecterCore.EVENT_BUS.subscribe(system);
      system.init();
      return system;
   }

   @EventHandler
   private static void onGameLeft(GameLeftEvent event) {
      save();
   }

   public static void save(File folder) {
      long start = java.lang.System.currentTimeMillis();
      SpecterCore.LOG.info("Saving");

      for (System<?> system : systems.values()) {
         system.save(folder);
      }

      SpecterCore.LOG.info("Saved in {} milliseconds.", java.lang.System.currentTimeMillis() - start);
   }

   public static void save() {
      save(null);
   }

   public static void load(File folder) {
      long start = java.lang.System.currentTimeMillis();
      SpecterCore.LOG.info("Loading");

      for (Runnable task : preLoadTasks) {
         task.run();
      }

      for (System<?> system : systems.values()) {
         system.load(folder);
      }

      SpecterCore.LOG.info("Loaded in {} milliseconds", java.lang.System.currentTimeMillis() - start);
   }

   public static void load() {
      load(null);
   }

   public static <T extends System<?>> T get(Class<T> klass) {
      return (T)systems.get(klass);
   }
}

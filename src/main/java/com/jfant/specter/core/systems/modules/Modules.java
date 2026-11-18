package com.jfant.specter.core.systems.modules;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.game.OpenScreenEvent;
import com.jfant.specter.core.events.meteor.ActiveModulesChangedEvent;
import com.jfant.specter.core.events.meteor.KeyEvent;
import com.jfant.specter.core.events.meteor.ModuleBindChangedEvent;
import com.jfant.specter.core.events.meteor.MouseClickEvent;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.System;
import com.jfant.specter.core.systems.Systems;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.misc.ValueComparableMap;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.core.utils.misc.input.KeyAction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.Nullable;

public class Modules extends System<Modules> {
   private static final List<Category> CATEGORIES = new ArrayList<>();
   private final Map<Class<? extends Module>, Module> moduleInstances = new Reference2ReferenceOpenHashMap();
   private final Map<Category, List<Module>> groups = new Reference2ReferenceOpenHashMap();
   private final List<Module> active = new ArrayList<>();
   private Module moduleToBind;
   private boolean awaitingKeyRelease = false;

   public Modules() {
      super("modules");
   }

   public static Modules get() {
      return Systems.get(Modules.class);
   }

   @Override
   public void init() {
   }

   @Override
   public void load(File folder) {
      for (Module module : this.getAll()) {
         for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
               setting.reset();
            }
         }
      }

      super.load(folder);
   }

   public void sortModules() {
      for (List<Module> modules : this.groups.values()) {
         modules.sort(Comparator.comparing(o -> o.title));
      }
   }

   public static void registerCategory(Category category) {
      if (!Categories.REGISTERING) {
         throw new RuntimeException("Modules.registerCategory - Cannot register category outside of onRegisterCategories callback.");
      } else {
         CATEGORIES.add(category);
      }
   }

   public static Iterable<Category> loopCategories() {
      return CATEGORIES;
   }

   @Nullable
   public <T extends Module> T get(Class<T> klass) {
      return (T)this.moduleInstances.get(klass);
   }

   public <T extends Module> Optional<T> getOptional(Class<T> klass) {
      return Optional.ofNullable(this.get(klass));
   }

   @Nullable
   public Module get(String name) {
      for (Module module : this.moduleInstances.values()) {
         if (module.name.equalsIgnoreCase(name)) {
            return module;
         }
      }

      return null;
   }

   public boolean isActive(Class<? extends Module> klass) {
      Module module = this.get(klass);
      return module != null && module.isActive();
   }

   public List<Module> getGroup(Category category) {
      return this.groups.computeIfAbsent(category, category1 -> new ArrayList<>());
   }

   public Collection<Module> getAll() {
      return this.moduleInstances.values();
   }

   public int getCount() {
      return this.moduleInstances.size();
   }

   public List<Module> getActive() {
      return this.active;
   }

   public List<Tuple<Module, String>> searchTitles(String text) {
      Map<Tuple<Module, String>, Integer> modules = new HashMap<>();

      for (Module module : this.moduleInstances.values()) {
         String title = module.title;
         int score = Utils.searchLevenshteinDefault(title, text, false);
         if (Config.get().moduleAliases.get()) {
            for (String alias : module.aliases) {
               int aliasScore = Utils.searchLevenshteinDefault(alias, text, false);
               if (aliasScore < score) {
                  title = module.title + " (" + alias + ")";
                  score = aliasScore;
               }
            }
         }

         modules.put(new Tuple<>(module, title), score);
      }

      List<Tuple<Module, String>> l = new ArrayList<>(modules.keySet());
      l.sort(Comparator.comparingInt(modules::get));
      return l;
   }

   public Set<Module> searchSettingTitles(String text) {
      Map<Module, Integer> modules = new ValueComparableMap<>(Comparator.naturalOrder());

      for (Module module : this.moduleInstances.values()) {
         int lowest = 2147483647;

         for (SettingGroup sg : module.settings) {
            for (Setting<?> setting : sg) {
               int score = Utils.searchLevenshteinDefault(setting.title, text, false);
               if (score < lowest) {
                  lowest = score;
               }
            }
         }

         modules.put(module, modules.getOrDefault(module, 0) + lowest);
      }

      return modules.keySet();
   }

   void addActive(Module module) {
      synchronized (this.active) {
         if (!this.active.contains(module)) {
            this.active.add(module);
            SpecterCore.EVENT_BUS.post(ActiveModulesChangedEvent.get());
         }
      }
   }

   void removeActive(Module module) {
      synchronized (this.active) {
         if (this.active.remove(module)) {
            SpecterCore.EVENT_BUS.post(ActiveModulesChangedEvent.get());
         }
      }
   }

   public void setModuleToBind(Module moduleToBind) {
      this.moduleToBind = moduleToBind;
   }

   public void awaitKeyRelease() {
      this.awaitingKeyRelease = true;
   }

   public boolean isBinding() {
      return this.moduleToBind != null;
   }

   @EventHandler(
      priority = 200
   )
   private void onKeyBinding(KeyEvent event) {
      if (event.action == KeyAction.Release && this.onBinding(true, event.key(), event.modifiers())) {
         event.cancel();
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onButtonBinding(MouseClickEvent event) {
      if (event.action == KeyAction.Release && this.onBinding(false, event.button(), 0)) {
         event.cancel();
      }
   }

   private boolean onBinding(boolean isKey, int value, int modifiers) {
      if (!this.isBinding()) {
         return false;
      } else if (!this.awaitingKeyRelease) {
         if (this.moduleToBind.keybind.canBindTo(isKey, value, modifiers)) {
            this.moduleToBind.keybind.set(isKey, value, modifiers);
            this.moduleToBind.info("Bound to (highlight)%s(default).", this.moduleToBind.keybind);
         } else {
            if (value != 256) {
               return false;
            }

            this.moduleToBind.keybind.set(Keybind.none());
            this.moduleToBind.info("Removed bind.");
         }

         SpecterCore.EVENT_BUS.post(ModuleBindChangedEvent.get(this.moduleToBind));
         this.moduleToBind = null;
         return true;
      } else if (isKey && (value == 257 || value == 335)) {
         this.awaitingKeyRelease = false;
         return false;
      } else {
         return false;
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onKey(KeyEvent event) {
      if (event.action != KeyAction.Repeat) {
         this.onAction(true, event.key(), event.modifiers(), event.action == KeyAction.Press);
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseClick(MouseClickEvent event) {
      if (event.action != KeyAction.Repeat) {
         this.onAction(false, event.button(), 0, event.action == KeyAction.Press);
      }
   }

   private void onAction(boolean isKey, int value, int modifiers, boolean isPress) {
      if (SpecterCore.mc.screen == null && !Input.isKeyPressed(292)) {
         for (Module module : this.moduleInstances.values()) {
            if (module.keybind.matches(isKey, value, modifiers) && (isPress || module.toggleOnBindRelease && module.isActive())) {
               module.toggle();
               module.sendToggledMsg();
            }
         }
      }
   }

   @EventHandler(
      priority = 201
   )
   private void onOpenScreen(OpenScreenEvent event) {
      if (Utils.canUpdate()) {
         for (Module module : this.moduleInstances.values()) {
            if (module.toggleOnBindRelease && module.isActive()) {
               module.toggle();
               module.sendToggledMsg();
            }
         }
      }
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      synchronized (this.active) {
         for (Module module : this.getAll()) {
            if (module.isActive() && !module.runInMainMenu) {
               SpecterCore.EVENT_BUS.subscribe(module);
               module.onActivate();
            }
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      synchronized (this.active) {
         for (Module module : this.getAll()) {
            if (module.isActive() && !module.runInMainMenu) {
               SpecterCore.EVENT_BUS.unsubscribe(module);
               module.onDeactivate();
            }
         }
      }
   }

   public void disableAll() {
      synchronized (this.active) {
         for (Module module : this.getAll()) {
            module.disable();
         }
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      ListTag modulesTag = new ListTag();

      for (Module module : this.getAll()) {
         CompoundTag moduleTag = module.toTag();
         if (moduleTag != null) {
            modulesTag.add(moduleTag);
         }
      }

      tag.put("modules", modulesTag);
      return tag;
   }

   public Modules fromTag(CompoundTag tag) {
      this.disableAll();

      for (Tag moduleTagI : tag.getListOrEmpty("modules")) {
         CompoundTag moduleTag = (CompoundTag)moduleTagI;
         Module module = this.get(moduleTag.getStringOr("name", ""));
         if (module != null) {
            module.fromTag(moduleTag);
         }
      }

      return this;
   }

   public void add(Module module) {
      if (!CATEGORIES.contains(module.category)) {
         throw new RuntimeException("Modules.addModule - Module's category was not registered.");
      } else {
         AtomicReference<Module> removedModule = new AtomicReference<>();
         if (this.moduleInstances.values().removeIf(module1 -> {
            if (module1.name.equals(module.name)) {
               removedModule.set(module1);
               module1.settings.unregisterColorSettings();
               return true;
            } else {
               return false;
            }
         })) {
            this.getGroup(removedModule.get().category).remove(removedModule.get());
         }

         this.moduleInstances.put((Class<? extends Module>)module.getClass(), module);
         this.getGroup(module.category).add(module);
         module.settings.registerColorSettings(module);
      }
   }
}

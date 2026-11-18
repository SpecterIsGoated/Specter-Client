/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.systems.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.game.OpenScreenEvent;
import com.jfant.specter.core.events.meteor.ActiveModulesChangedEvent;
import com.jfant.specter.core.events.meteor.KeyInputEvent;
import com.jfant.specter.core.events.meteor.ModuleBindChangedEvent;
import com.jfant.specter.core.events.meteor.MouseClickEvent;
import com.jfant.specter.core.pathing.BaritoneUtils;
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
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.jfant.specter.core.SpecterCore.mc;

public class Modules extends System<Modules> {
    private static final List<Category> CATEGORIES = new ArrayList<>();

    private final Map<Class<? extends Module>, Module> moduleInstances = new Reference2ReferenceOpenHashMap<>();
    private final Map<Category, List<Module>> groups = new Reference2ReferenceOpenHashMap<>();

    private final Set<Module> active = new ReferenceOpenHashSet<>();
    private @Nullable Module moduleToBind;
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
        for (Module module : getAll()) {
            for (SettingGroup group : module.settings) {
                for (Setting<?> setting : group) setting.reset();
            }
        }

        super.load(folder);
    }

    public void sortModules() {
        for (List<Module> modules : groups.values()) {
            modules.sort(Comparator.comparing(o -> o.title));
        }
    }

    public static void registerCategory(Category category) {
        if (!Categories.REGISTERING)
            throw new RuntimeException("Modules.registerCategory - Cannot register category outside of onRegisterCategories callback.");

        CATEGORIES.add(category);
    }

    public static Iterable<Category> loopCategories() {
        return CATEGORIES;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends Module> T get(Class<T> klass) {
        return (T) moduleInstances.get(klass);
    }

    @SuppressWarnings("unused")
    public <T extends Module> Optional<T> getOptional(Class<T> klass) {
        return Optional.ofNullable(get(klass));
    }

    @Nullable
    public Module get(String name) {
        for (Module module : moduleInstances.values()) {
            if (module.name.equalsIgnoreCase(name)) return module;
        }

        return null;
    }

    public boolean isActive(Class<? extends Module> klass) {
        Module module = get(klass);
        return module != null && module.isActive();
    }

    public List<Module> getGroup(Category category) {
        return groups.computeIfAbsent(category, _ -> new ArrayList<>());
    }

    public Collection<Module> getAll() {
        return moduleInstances.values();
    }

    public int getCount() {
        return moduleInstances.size();
    }

    public Collection<Module> getActive() {
        return active;
    }

    public List<Pair<Module, String>> searchTitles(String text) {
        Object2IntMap<Pair<Module, String>> modules = new Object2IntOpenHashMap<>();

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

            modules.put(Pair.of(module, title), score);
        }

        List<Pair<Module, String>> l = new ArrayList<>(modules.keySet());
        l.sort(Comparator.comparingInt(modules::getInt));

        return l;
    }

    public Set<Module> searchSettingTitles(String text) {
        Map<Module, Integer> modules = new ValueComparableMap<>(Comparator.naturalOrder());

        for (Module module : this.moduleInstances.values()) {
            int lowest = Integer.MAX_VALUE;
            for (SettingGroup sg : module.settings) {
                for (Setting<?> setting : sg) {
                    int score = Utils.searchLevenshteinDefault(setting.title, text, false);
                    if (score < lowest) lowest = score;
                }
            }
            modules.put(module, modules.getOrDefault(module, 0) + lowest);
        }

        return modules.keySet();
    }

    void addActive(Module module) {
        synchronized (active) {
            if (active.add(module)) {
                SpecterCore.EVENT_BUS.post(ActiveModulesChangedEvent.get());
            }
        }
    }

    void removeActive(Module module) {
        synchronized (active) {
            if (active.remove(module)) {
                SpecterCore.EVENT_BUS.post(ActiveModulesChangedEvent.get());
            }
        }
    }

    // Binding

    public void setModuleToBind(@Nullable Module moduleToBind) {
        this.moduleToBind = moduleToBind;
    }

    /***
     * @see com.jfant.specter.core.commands.commands.BindCommand
     * For ensuring we don't instantly bind the module to the enter key.
     */
    public void awaitKeyRelease() {
        this.awaitingKeyRelease = true;
    }

    public boolean isBinding() {
        return moduleToBind != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onKeyBinding(KeyInputEvent event) {
        if (event.action == KeyAction.Release && onBinding(true, event.key(), event.modifiers())) event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onButtonBinding(MouseClickEvent event) {
        if (event.action == KeyAction.Release && onBinding(false, event.button(), 0)) event.cancel();
    }

    private boolean onBinding(boolean isKey, int value, int modifiers) {
        if (!isBinding()) return false;

        if (awaitingKeyRelease) {
            if (!isKey || (value != InputConstants.KEY_RETURN && value != InputConstants.KEY_NUMPADENTER)) return false;

            awaitingKeyRelease = false;
            return false;
        }

        if (moduleToBind.keybind.canBindTo(isKey, value, modifiers)) {
            moduleToBind.keybind.set(isKey, value, modifiers);
            moduleToBind.info("Bound to (highlight)%s(default).", moduleToBind.keybind);
        } else if (value == InputConstants.KEY_ESCAPE) {
            moduleToBind.keybind.set(Keybind.none());
            moduleToBind.info("Removed bind.");
        } else return false;

        SpecterCore.EVENT_BUS.post(ModuleBindChangedEvent.get(moduleToBind));
        moduleToBind = null;

        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKey(KeyInputEvent event) {
        if (event.action == KeyAction.Repeat) return;
        onAction(true, event.key(), event.modifiers(), event.action == KeyAction.Press);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseClick(MouseClickEvent event) {
        if (event.action == KeyAction.Repeat) return;
        onAction(false, event.button(), 0, event.action == KeyAction.Press);
    }

    private void onAction(boolean isKey, int value, int modifiers, boolean isPress) {
        if (mc.gui.screen() != null || Input.isKeyPressed(InputConstants.KEY_F3)) return;

        // A bind with modifiers takes precedence over one without, so pressing Ctrl + G does not
        // also toggle a module bound to plain G.
        boolean modifierBindMatched = false;

        for (Module module : moduleInstances.values()) {
            if (module.keybind.hasMods() && module.keybind.matches(isKey, value, modifiers)) {
                modifierBindMatched = true;
                break;
            }
        }

        for (Module module : moduleInstances.values()) {
            if (modifierBindMatched && !module.keybind.hasMods()) continue;

            if (module.keybind.matches(isKey, value, modifiers) && (isPress || (module.toggleOnBindRelease && module.isActive()))) {
                module.toggle();
                module.sendToggledMsg();
            }
        }
    }

    // End of binding

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onOpenScreen(OpenScreenEvent event) {
        if (!Utils.canUpdate()) return;

        for (Module module : moduleInstances.values()) {
            if (module.toggleOnBindRelease && module.isActive()) {
                module.toggle();
                module.sendToggledMsg();
            }
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (active) {
            for (Module module : getAll()) {
                if (module.isActive() && !module.runInMainMenu) {
                    SpecterCore.EVENT_BUS.subscribe(module);
                    module.onActivate();
                }
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        synchronized (active) {
            for (Module module : getAll()) {
                if (module.isActive() && !module.runInMainMenu) {
                    SpecterCore.EVENT_BUS.unsubscribe(module);
                    module.onDeactivate();
                }
            }
        }
    }

    public void disableAll() {
        synchronized (active) {
            for (Module module : getAll()) {
                module.disable();
            }
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        ListTag modulesTag = new ListTag();
        for (Module module : getAll()) {
            CompoundTag moduleTag = module.toTag();
            if (moduleTag != null) modulesTag.add(moduleTag);
        }
        tag.put("modules", modulesTag);

        return tag;
    }

    @Override
    public Modules fromTag(CompoundTag tag) {
        disableAll();

        ListTag modulesTag = tag.getListOrEmpty("modules");
        for (Tag moduleTagI : modulesTag) {
            CompoundTag moduleTag = (CompoundTag) moduleTagI;
            Module module = get(moduleTag.getStringOr("name", ""));
            if (module != null) module.fromTag(moduleTag);
        }

        return this;
    }

    // INIT MODULES

    public void add(Module module) {
        // Check if the module's category is registered
        if (!CATEGORIES.contains(module.category)) {
            throw new RuntimeException("Modules.addModule - Module's category was not registered.");
        }

        // Remove the previous module with the same name
        AtomicReference<Module> removedModule = new AtomicReference<>();
        if (moduleInstances.values().removeIf(module1 -> {
            if (module1.name.equals(module.name)) {
                removedModule.set(module1);
                module1.settings.unregisterColorSettings();

                return true;
            }

            return false;
        })) {
            getGroup(removedModule.get().category).remove(removedModule.get());
        }

        // Add the module
        moduleInstances.put(module.getClass(), module);
        getGroup(module.category).add(module);

        // Register color settings for the module
        module.settings.registerColorSettings(module);
    }

}

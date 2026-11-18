package com.jfant.specter.core.gui.tabs;

import com.jfant.specter.core.gui.tabs.builtin.ConfigTab;
import com.jfant.specter.core.gui.tabs.builtin.FriendsTab;
import com.jfant.specter.core.gui.tabs.builtin.GuiTab;
import com.jfant.specter.core.gui.tabs.builtin.HudTab;
import com.jfant.specter.core.gui.tabs.builtin.ModulesTab;
import com.jfant.specter.core.gui.tabs.builtin.ProfilesTab;
import com.jfant.specter.core.pathing.PathManagers;
import com.jfant.specter.core.utils.PreInit;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.List;

public class Tabs {
   private static final List<Tab> tabs = new ArrayList<>();
   private static final Reference2ReferenceOpenHashMap<Class<? extends Tab>, Tab> tabInstances = new Reference2ReferenceOpenHashMap();

   private Tabs() {
   }

   @PreInit(
      dependencies = {PathManagers.class}
   )
   public static void init() {
      add(new ModulesTab());
      add(new ConfigTab());
      add(new GuiTab());
      add(new HudTab());
      add(new FriendsTab());
      add(new ProfilesTab());
   }

   public static void add(Tab tab) {
      tabs.add(tab);
      tabInstances.put(tab.getClass(), tab);
   }

   public static List<Tab> get() {
      return tabs;
   }

   public static Tab get(Class<? extends Tab> klass) {
      return (Tab)tabInstances.get(klass);
   }
}

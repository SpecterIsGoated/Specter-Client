package com.jfant.specter.core.systems.profiles;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.systems.System;
import com.jfant.specter.core.systems.Systems;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.NbtUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

public class Profiles extends System<Profiles> implements Iterable<Profile> {
   public static final File FOLDER = new File(SpecterCore.FOLDER, "profiles");
   private List<Profile> profiles = new ArrayList<>();

   public Profiles() {
      super("profiles");
   }

   public static Profiles get() {
      return Systems.get(Profiles.class);
   }

   public void add(Profile profile) {
      if (!this.profiles.contains(profile)) {
         this.profiles.add(profile);
      }

      profile.save();
      this.save();
   }

   public void remove(Profile profile) {
      if (this.profiles.remove(profile)) {
         profile.delete();
      }

      this.save();
   }

   public Profile get(String name) {
      for (Profile profile : this) {
         if (profile.name.get().equalsIgnoreCase(name)) {
            return profile;
         }
      }

      return null;
   }

   public List<Profile> getAll() {
      return this.profiles;
   }

   @Override
   public File getFile() {
      return new File(FOLDER, "profiles.nbt");
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      for (Profile profile : this) {
         if (profile.loadOnJoin.get().contains(Utils.getWorldName())) {
            profile.load();
         }
      }
   }

   public boolean isEmpty() {
      return this.profiles.isEmpty();
   }

   @NotNull
   @Override
   public Iterator<Profile> iterator() {
      return this.profiles.iterator();
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.put("profiles", NbtUtils.listToTag(this.profiles));
      return tag;
   }

   public Profiles fromTag(CompoundTag tag) {
      this.profiles = NbtUtils.listFromTag(tag.getListOrEmpty("profiles"), Profile::new);

      for (File file : FOLDER.listFiles()) {
         if (file.isDirectory() && this.get(file.getName()) == null) {
            Profile p = new Profile();
            p.name.set(file.getName());
            boolean add = false;

            for (File f : file.listFiles()) {
               if (f.getName().equals("hud.nbt")) {
                  add = true;
                  p.hud.set(true);
               } else if (f.getName().equals("modules.nbt")) {
                  add = true;
                  p.modules.set(true);
               } else if (f.getName().endsWith(".nbt")) {
                  add = true;
               }
            }

            if (add) {
               this.add(p);
            }
         }
      }

      return this;
   }
}

package com.jfant.specter.core.systems.profiles;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.Settings;
import com.jfant.specter.core.settings.StringListSetting;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.ISerializable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.apache.commons.io.FileUtils;

public class Profile implements ISerializable<Profile> {
   public final Settings settings = new Settings();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSave = this.settings.createGroup("Save");
   public Setting<String> name = this.sgGeneral
      .add(new StringSetting.Builder().name("name").description("The name of the profile.").filter(Utils::nameFilter).build());
   public Setting<List<String>> loadOnJoin = this.sgGeneral
      .add(
         new StringListSetting.Builder()
            .name("load-on-join")
            .description("Which servers to set this profile as active when joining.")
            .filter(Utils::ipFilter)
            .build()
      );
   public Setting<Boolean> hud = this.sgSave
      .add(new BoolSetting.Builder().name("hud").description("Whether the profile should save hud.").defaultValue(false).build());
   public Setting<Boolean> modules = this.sgSave
      .add(new BoolSetting.Builder().name("modules").description("Whether the profile should save modules.").defaultValue(false).build());

   public Profile() {
   }

   public Profile(Tag tag) {
      this.fromTag((CompoundTag)tag);
   }

   public void load() {
      File folder = this.getSafeFile();
      if (folder != null) {
         if (this.hud.get()) {
            Hud.get().load(folder);
         }

         if (this.modules.get()) {
            Modules.get().load(folder);
         }
      }
   }

   public void save() {
      File folder = this.getSafeFile();
      if (folder != null) {
         if (this.hud.get()) {
            Hud.get().save(folder);
         }

         if (this.modules.get()) {
            Modules.get().save(folder);
         }
      }
   }

   public void delete() {
      try {
         File folder = this.getSafeFile();
         if (folder != null) {
            FileUtils.deleteDirectory(folder);
         }
      } catch (IOException var2) {
         SpecterCore.LOG.error("Error deleting profile {}", this.name.get(), var2);
      }
   }

   public File getFile() {
      return new File(Profiles.FOLDER, this.name.get());
   }

   public File getSafeFile() {
      try {
         File folder = this.getFile().getCanonicalFile();
         File profilesFolder = Profiles.FOLDER.getCanonicalFile();
         return !this.name.get().isEmpty() && profilesFolder.equals(folder.getParentFile()) ? folder : null;
      } catch (IOException var3) {
         SpecterCore.LOG.error("Error resolving profile {}", this.name.get(), var3);
         return null;
      }
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.put("settings", this.settings.toTag());
      return tag;
   }

   public Profile fromTag(CompoundTag tag) {
      if (tag.contains("settings")) {
         this.settings.fromTag(tag.getCompoundOrEmpty("settings"));
      }

      return this;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Profile profile = (Profile)o;
         return Objects.equals(profile.name.get(), this.name.get());
      } else {
         return false;
      }
   }
}

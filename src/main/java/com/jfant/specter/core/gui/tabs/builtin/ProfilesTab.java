package com.jfant.specter.core.gui.tabs.builtin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.tabs.Tab;
import com.jfant.specter.core.gui.tabs.TabScreen;
import com.jfant.specter.core.gui.tabs.WindowTabScreen;
import com.jfant.specter.core.gui.widgets.containers.WContainer;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.gui.widgets.pressable.WCheckbox;
import com.jfant.specter.core.gui.widgets.pressable.WConfirmedButton;
import com.jfant.specter.core.gui.widgets.pressable.WConfirmedMinus;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.systems.profiles.Profile;
import com.jfant.specter.core.systems.profiles.Profiles;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.NbtUtils;
import com.jfant.specter.core.utils.render.prompts.OkPrompt;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class ProfilesTab extends Tab {
   private static final PointerBuffer filters = BufferUtils.createPointerBuffer(1);

   public ProfilesTab() {
      super("Profiles");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new ProfilesTab.ProfilesScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof ProfilesTab.ProfilesScreen;
   }

   static {
      ByteBuffer pngFilter = MemoryUtil.memASCII("*.nbt");
      filters.put(pngFilter);
      filters.rewind();
   }

   private static class EditProfileScreen extends WindowScreen {
      private WContainer settingsContainer;
      private final Profile profile;
      private final boolean isNew;
      private final Runnable action;

      public EditProfileScreen(GuiTheme theme, Profile profile, Runnable action) {
         super(theme, profile == null ? "New Profile" : "Edit Profile");
         this.isNew = profile == null;
         this.profile = this.isNew ? new Profile() : profile;
         this.action = action;
      }

      @Override
      public void initWidgets() {
         this.settingsContainer = this.add(this.theme.verticalList()).expandX().minWidth(400.0).widget();
         this.settingsContainer.add(this.theme.settings(this.profile.settings)).expandX();
         this.add(this.theme.horizontalSeparator()).expandX();
         WButton save = this.add(this.theme.button(this.isNew ? "Create" : "Save")).expandX().widget();
         save.action = () -> {
            if (this.profile.getSafeFile() != null) {
               if (this.isNew) {
                  for (Profile p : Profiles.get()) {
                     if (this.profile.equals(p)) {
                        return;
                     }
                  }
               }

               List<String> valid = new ArrayList<>();

               for (String address : this.profile.loadOnJoin.get()) {
                  if (Utils.resolveAddress(address)) {
                     valid.add(address);
                  }
               }

               this.profile.loadOnJoin.set(valid);
               if (this.isNew) {
                  Profiles.get().add(this.profile);
               } else {
                  Profiles.get().save();
               }

               this.onClose();
            }
         };
         this.enterAction = save.action;
      }

      @Override
      public void tick() {
         super.tick();
         this.profile.settings.tick(this.settingsContainer, this.theme);
      }

      @Override
      protected void onClosed() {
         if (this.action != null) {
            this.action.run();
         }
      }
   }

   private static class ExportProfileScreen extends WindowScreen {
      private final Profile profile;

      public ExportProfileScreen(GuiTheme theme, Profile profile) {
         super(theme, "Export Profile");
         this.profile = profile;
      }

      @Override
      public void initWidgets() {
         this.add(this.theme.label("Select which profile settings to export."));
         WContainer settingsContainer = this.add(this.theme.verticalList()).expandX().minWidth(400.0).widget();
         settingsContainer.add(this.theme.horizontalSeparator()).expandX().widget();
         WCheckbox hud = this.addBool(settingsContainer, this.profile.settings.get("hud", Boolean.class));
         WCheckbox macros = this.addBool(settingsContainer, this.profile.settings.get("macros", Boolean.class));
         WCheckbox modules = this.addBool(settingsContainer, this.profile.settings.get("modules", Boolean.class));
         WCheckbox waypoints = this.addBool(settingsContainer, this.profile.settings.get("waypoints", Boolean.class));
         this.add(this.theme.horizontalSeparator()).expandX().widget();
         WButton export = this.add(this.theme.button("Export profile")).expandX().widget();
         export.action = () -> {
            this.exportProfile(this.profile, hud.checked, macros.checked, modules.checked, waypoints.checked);
            this.onClose();
         };
      }

      private WCheckbox addBool(WContainer container, Setting<Boolean> setting) {
         WHorizontalList boolList = container.add(this.theme.horizontalList()).expandX().widget();
         boolList.add(this.theme.label(setting.title)).widget().tooltip = setting.description;
         WCheckbox c = this.theme.checkbox(setting.get());
         boolList.add(c).expandCellX().right();
         return c;
      }

      private void exportProfile(Profile profile, boolean hud, boolean macros, boolean modules, boolean waypoints) {
         String path = TinyFileDialogs.tinyfd_saveFileDialog("Save profile", profile.name.get(), ProfilesTab.filters, null);
         if (path != null) {
            Path p = Path.of(path.endsWith(".nbt") ? path : path + ".nbt");
            CompoundTag nbt = new CompoundTag();
            nbt.putString("name", profile.name.get());

            try {
               for (File f : profile.getFile().listFiles()) {
                  if ((!f.getName().equals("hud.nbt") || !hud)
                     && (!f.getName().equals("macros.nbt") || !macros)
                     && (!f.getName().equals("modules.nbt") || !modules)) {
                     if (f.getName().endsWith(".nbt") && waypoints) {
                        nbt.put(f.getName(), NbtIo.read(f.toPath()));
                     }
                  } else {
                     nbt.put(f.getName(), NbtIo.read(f.toPath()));
                  }
               }

               NbtIo.write(nbt, p);
            } catch (IOException var13) {
               SpecterCore.LOG.error("Error serialising profile {} to a file", profile.name.get(), var13);
               OkPrompt.create()
                  .title("Failure exporting profile")
                  .message("There was an error serialising or exporting the profile %d.", new Object[]{profile.name.get()})
                  .message("Error: %d", new Object[]{var13.getMessage()})
                  .dontShowAgainCheckboxVisible(false)
                  .show();
            }
         }
      }
   }

   private static class ProfilesScreen extends WindowTabScreen {
      public ProfilesScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
      }

      @Override
      public void initWidgets() {
         WTable table = this.add(this.theme.table()).expandX().minWidth(400.0).widget();
         this.initTable(table);
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
         WButton create = l.add(this.theme.button("Create")).expandX().widget();
         create.tooltip = "Create new profile";
         create.action = () -> SpecterCore.mc.setScreen(new ProfilesTab.EditProfileScreen(this.theme, null, this::reload));
         WButton importBtn = l.add(this.theme.button("Import")).expandX().widget();
         importBtn.tooltip = "Import profile";
         importBtn.action = () -> {
            try {
               Profile imported = this.importProfile();
               if (imported != null) {
                  SpecterCore.LOG.info("Successfully imported profile '{}'.", imported.name.get());
               }

               this.reload();
            } catch (IOException var2x) {
               SpecterCore.LOG.error("Error importing profile", var2x);
               OkPrompt.create()
                  .title("Failure importing profile")
                  .message("There was an error importing the profile.")
                  .message("Error: %d", new Object[]{var2x.getMessage()})
                  .dontShowAgainCheckboxVisible(false)
                  .show();
            }
         };
      }

      private void initTable(WTable table) {
         table.clear();
         if (!Profiles.get().isEmpty()) {
            for (Profile profile : Profiles.get()) {
               table.add(this.theme.label(profile.name.get())).expandCellX();
               WConfirmedButton save = this.theme.confirmedButton("Save", "Confirm");
               save.action = profile::save;
               table.add(save).right();
               WButton load = table.add(this.theme.button("Load")).widget();
               load.action = profile::load;
               WButton export = table.add(this.theme.button("Export")).widget();
               export.action = () -> SpecterCore.mc.setScreen(new ProfilesTab.ExportProfileScreen(this.theme, profile));
               WButton edit = table.add(this.theme.button(GuiRenderer.EDIT)).widget();
               edit.action = () -> SpecterCore.mc.setScreen(new ProfilesTab.EditProfileScreen(this.theme, profile, this::reload));
               WConfirmedMinus remove = table.add(this.theme.confirmedMinus()).widget();
               remove.action = () -> {
                  Profiles.get().remove(profile);
                  this.reload();
               };
               table.row();
            }
         }
      }

      private Profile importProfile() throws IOException {
         String file = TinyFileDialogs.tinyfd_openFileDialog("Select profile to import", null, ProfilesTab.filters, null, false);
         if (file == null) {
            return null;
         } else {
            File profileFile = new File(file);
            CompoundTag nbt = NbtIo.read(profileFile.toPath());
            Profile p = new Profile();
            if (!p.name.set(nbt.getStringOr("name", profileFile.getName()))) {
               return null;
            } else {
               File profileFolder = p.getSafeFile();
               if (profileFolder == null) {
                  return null;
               } else {
                  profileFolder.mkdirs();
                  nbt.remove("name");

                  for (Entry<String, Tag> entry : nbt.entrySet()) {
                     String filename = entry.getKey();
                     if (filename.endsWith(".nbt") && !filename.contains("/") && !filename.contains("\\") && !new File(filename).isAbsolute()) {
                        switch (filename) {
                           case "hud.nbt":
                              p.hud.set(true);
                              break;
                           case "modules.nbt":
                              p.modules.set(true);
                        }

                        File f = new File(profileFolder, filename).getCanonicalFile();
                        if (f.toPath().startsWith(profileFolder.toPath())) {
                           NbtIo.writeUnnamedTagWithFallback(entry.getValue(), new DataOutputStream(new FileOutputStream(f)));
                        }
                     }
                  }

                  Profiles.get().getAll().add(p);
                  Profiles.get().save();
                  return p;
               }
            }
         }
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard(Profiles.get());
      }

      @Override
      public boolean fromClipboard() {
         return NbtUtils.fromClipboard(Profiles.get());
      }
   }
}

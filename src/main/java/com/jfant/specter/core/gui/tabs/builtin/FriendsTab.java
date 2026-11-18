package com.jfant.specter.core.gui.tabs.builtin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.tabs.Tab;
import com.jfant.specter.core.gui.tabs.TabScreen;
import com.jfant.specter.core.gui.tabs.WindowTabScreen;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.gui.widgets.pressable.WMinus;
import com.jfant.specter.core.gui.widgets.pressable.WPlus;
import com.jfant.specter.core.systems.friends.Friend;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.utils.misc.NbtUtils;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import net.minecraft.client.gui.screens.Screen;

public class FriendsTab extends Tab {
   public FriendsTab() {
      super("Friends");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new FriendsTab.FriendsScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof FriendsTab.FriendsScreen;
   }

   private static class FriendsScreen extends WindowTabScreen {
      public FriendsScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
      }

      @Override
      public void initWidgets() {
         WTable table = this.add(this.theme.table()).expandX().minWidth(400.0).widget();
         this.initTable(table);
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList list = this.add(this.theme.horizontalList()).expandX().widget();
         WTextBox nameW = list.add(this.theme.textBox("", (text, c) -> c != ' ')).expandX().widget();
         nameW.setFocused(true);
         WPlus add = list.add(this.theme.plus()).widget();
         add.action = () -> {
            String name = nameW.get().trim();
            Friend friend = new Friend(name);
            if (Friends.get().add(friend)) {
               nameW.set("");
               this.initTable(table);
               nameW.setFocused(true);
               MeteorExecutor.execute(() -> {
                  friend.updateInfo();
                  SpecterCore.mc.execute(() -> {
                     this.initTable(table);
                     nameW.setFocused(true);
                  });
               });
            }
         };
         this.enterAction = add.action;
      }

      private void initTable(WTable table) {
         table.clear();
         if (!Friends.get().isEmpty()) {
            Friends.get().forEach(friendx -> MeteorExecutor.execute(() -> {
               if (friendx.headTextureNeedsUpdate()) {
                  friendx.updateInfo();
               }
            }));

            for (Friend friend : Friends.get()) {
               table.add(this.theme.texture(32.0, 32.0, friend.getHead().needsRotate() ? 90.0 : 0.0, friend.getHead()));
               table.add(this.theme.label(friend.getName()));
               WMinus remove = table.add(this.theme.minus()).expandCellX().right().widget();
               remove.action = () -> {
                  Friends.get().remove(friend);
                  this.initTable(table);
               };
               table.row();
            }
         }
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard(Friends.get());
      }

      @Override
      public boolean fromClipboard() {
         return NbtUtils.fromClipboard(Friends.get());
      }
   }
}

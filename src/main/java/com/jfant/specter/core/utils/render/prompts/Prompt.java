package com.jfant.specter.core.utils.render.prompts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WCheckbox;
import com.jfant.specter.core.systems.config.Config;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

public abstract class Prompt<T> {
   protected final GuiTheme theme;
   protected final Screen parent;
   protected String title = "";
   protected final List<String> messages = new ArrayList<>();
   protected boolean dontShowAgainCheckboxVisible = true;
   protected String id = null;

   protected Prompt(GuiTheme theme, Screen parent) {
      this.theme = theme;
      this.parent = parent;
   }

   public T title(String title) {
      this.title = title;
      return (T)this;
   }

   public T message(String message) {
      this.messages.add(message);
      return (T)this;
   }

   public T message(String message, Object... args) {
      this.messages.add(String.format(message, args));
      return (T)this;
   }

   public T dontShowAgainCheckboxVisible(boolean visible) {
      this.dontShowAgainCheckboxVisible = visible;
      return (T)this;
   }

   public T id(String from) {
      this.id = from;
      return (T)this;
   }

   public boolean show() {
      if (this.id != null && Config.get().dontShowAgainPrompts.contains(this.id)) {
         return false;
      } else {
         if (!RenderSystem.isOnRenderThread()) {
            SpecterCore.mc.execute(() -> SpecterCore.mc.setScreen(new Prompt.PromptScreen(this.theme)));
         } else {
            SpecterCore.mc.setScreen(new Prompt.PromptScreen(this.theme));
         }

         return true;
      }
   }

   protected void dontShowAgain(Prompt<T>.PromptScreen screen) {
      if (screen.dontShowAgainCheckbox != null && screen.dontShowAgainCheckbox.checked && this.id != null) {
         Config.get().dontShowAgainPrompts.add(this.id);
      }
   }

   protected abstract void initialiseWidgets(Prompt<T>.PromptScreen var1);

   protected class PromptScreen extends WindowScreen {
      protected WCheckbox dontShowAgainCheckbox;
      protected WHorizontalList list;

      public PromptScreen(GuiTheme theme) {
         super(theme, Prompt.this.title);
         this.parent = Prompt.this.parent;
      }

      @Override
      public void initWidgets() {
         for (String line : Prompt.this.messages) {
            this.add(this.theme.label(line)).expandX();
         }

         this.add(this.theme.horizontalSeparator()).expandX();
         if (Prompt.this.dontShowAgainCheckboxVisible) {
            WHorizontalList checkboxContainer = this.<WHorizontalList>add(this.theme.horizontalList()).expandX().widget();
            this.dontShowAgainCheckbox = checkboxContainer.add(this.theme.checkbox(false)).widget();
            checkboxContainer.add(this.theme.label("Don't show this again.")).expandX();
         } else {
            this.dontShowAgainCheckbox = null;
         }

         this.list = this.<WHorizontalList>add(this.theme.horizontalList()).expandX().widget();
         Prompt.this.initialiseWidgets(this);
      }
   }
}

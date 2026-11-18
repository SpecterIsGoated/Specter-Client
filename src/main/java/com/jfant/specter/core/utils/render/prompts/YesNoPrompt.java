package com.jfant.specter.core.utils.render.prompts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import net.minecraft.client.gui.screens.Screen;

public class YesNoPrompt extends Prompt<YesNoPrompt> {
   private Runnable onYes = () -> {};
   private Runnable onNo = () -> {};

   private YesNoPrompt(GuiTheme theme, Screen parent) {
      super(theme, parent);
   }

   public static YesNoPrompt create() {
      return new YesNoPrompt(GuiThemes.get(), SpecterCore.mc.screen);
   }

   public static YesNoPrompt create(GuiTheme theme, Screen parent) {
      return new YesNoPrompt(theme, parent);
   }

   public YesNoPrompt onYes(Runnable action) {
      this.onYes = action;
      return this;
   }

   public YesNoPrompt onNo(Runnable action) {
      this.onNo = action;
      return this;
   }

   @Override
   protected void initialiseWidgets(Prompt<YesNoPrompt>.PromptScreen screen) {
      WButton yesButton = screen.list.add(this.theme.button("Yes")).expandX().widget();
      yesButton.action = () -> {
         this.dontShowAgain(screen);
         this.onYes.run();
         screen.onClose();
      };
      WButton noButton = screen.list.add(this.theme.button("No")).expandX().widget();
      noButton.action = () -> {
         this.dontShowAgain(screen);
         this.onNo.run();
         screen.onClose();
      };
   }
}

package com.jfant.specter.core.gui.themes.meteor;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.DefaultSettingsWidgetFactory;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.gui.renderer.packer.GuiTexture;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorAccount;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorHorizontalSeparator;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorLabel;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorModule;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorMultiLabel;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorQuad;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorSection;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorTooltip;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorTopBar;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorVerticalSeparator;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorView;
import com.jfant.specter.core.gui.themes.meteor.widgets.WMeteorWindow;
import com.jfant.specter.core.gui.themes.meteor.widgets.input.WMeteorDropdown;
import com.jfant.specter.core.gui.themes.meteor.widgets.input.WMeteorSlider;
import com.jfant.specter.core.gui.themes.meteor.widgets.input.WMeteorTextBox;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorButton;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorCheckbox;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorConfirmedButton;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorConfirmedMinus;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorFavorite;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorMinus;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorPlus;
import com.jfant.specter.core.gui.themes.meteor.widgets.pressable.WMeteorTriangle;
import com.jfant.specter.core.gui.utils.AlignmentX;
import com.jfant.specter.core.gui.utils.CharFilter;
import com.jfant.specter.core.gui.widgets.WAccount;
import com.jfant.specter.core.gui.widgets.WHorizontalSeparator;
import com.jfant.specter.core.gui.widgets.WLabel;
import com.jfant.specter.core.gui.widgets.WQuad;
import com.jfant.specter.core.gui.widgets.WTooltip;
import com.jfant.specter.core.gui.widgets.WTopBar;
import com.jfant.specter.core.gui.widgets.WVerticalSeparator;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.gui.widgets.containers.WSection;
import com.jfant.specter.core.gui.widgets.containers.WView;
import com.jfant.specter.core.gui.widgets.containers.WWindow;
import com.jfant.specter.core.gui.widgets.input.WDropdown;
import com.jfant.specter.core.gui.widgets.input.WSlider;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.gui.widgets.pressable.WCheckbox;
import com.jfant.specter.core.gui.widgets.pressable.WConfirmedButton;
import com.jfant.specter.core.gui.widgets.pressable.WConfirmedMinus;
import com.jfant.specter.core.gui.widgets.pressable.WFavorite;
import com.jfant.specter.core.gui.widgets.pressable.WMinus;
import com.jfant.specter.core.gui.widgets.pressable.WPlus;
import com.jfant.specter.core.gui.widgets.pressable.WTriangle;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.mojang.blaze3d.platform.MacosUtil;

public class MeteorGuiTheme extends GuiTheme {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgTextColors = this.settings.createGroup("Text");
   private final SettingGroup sgBackgroundColors = this.settings.createGroup("Background");
   private final SettingGroup sgOutline = this.settings.createGroup("Outline");
   private final SettingGroup sgSeparator = this.settings.createGroup("Separator");
   private final SettingGroup sgScrollbar = this.settings.createGroup("Scrollbar");
   private final SettingGroup sgSlider = this.settings.createGroup("Slider");
   private final SettingGroup sgStarscript = this.settings.createGroup("Starscript");
   public final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the GUI.")
            .defaultValue(1.0)
            .min(0.75)
            .sliderRange(0.75, 4.0)
            .onSliderRelease()
            .onChanged(aDouble -> {
               if (SpecterCore.mc.screen instanceof WidgetScreen) {
                  ((WidgetScreen)SpecterCore.mc.screen).invalidate();
               }
            })
            .build()
      );
   public final Setting<AlignmentX> moduleAlignment = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("module-alignment"))
                  .description("How module titles are aligned."))
               .defaultValue(AlignmentX.Center))
            .build()
      );
   public final Setting<Boolean> categoryIcons = this.sgGeneral
      .add(new BoolSetting.Builder().name("category-icons").description("Adds item icons to module categories.").defaultValue(false).build());
   public final Setting<Boolean> hideHUD = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-HUD").description("Hide HUD when in GUI.").defaultValue(false).onChanged(v -> {
         if (SpecterCore.mc.screen instanceof WidgetScreen) {
            SpecterCore.mc.options.hideGui = v;
         }
      }).build());
   public final Setting<SettingColor> accentColor = this.color("accent", "Main color of the GUI.", new SettingColor(145, 61, 226));
   public final Setting<SettingColor> checkboxColor = this.color("checkbox", "Color of checkbox.", new SettingColor(145, 61, 226));
   public final Setting<SettingColor> plusColor = this.color("plus", "Color of plus button.", new SettingColor(50, 255, 50));
   public final Setting<SettingColor> minusColor = this.color("minus", "Color of minus button.", new SettingColor(255, 50, 50));
   public final Setting<SettingColor> favoriteColor = this.color("favorite", "Color of checked favorite button.", new SettingColor(250, 215, 0));
   public final Setting<SettingColor> textColor = this.color(this.sgTextColors, "text", "Color of text.", new SettingColor(255, 255, 255));
   public final Setting<SettingColor> textSecondaryColor = this.color(
      this.sgTextColors, "text-secondary-text", "Color of secondary text.", new SettingColor(150, 150, 150)
   );
   public final Setting<SettingColor> textHighlightColor = this.color(
      this.sgTextColors, "text-highlight", "Color of text highlighting.", new SettingColor(45, 125, 245, 100)
   );
   public final Setting<SettingColor> titleTextColor = this.color(this.sgTextColors, "title-text", "Color of title text.", new SettingColor(255, 255, 255));
   public final Setting<SettingColor> loggedInColor = this.color(
      this.sgTextColors, "logged-in-text", "Color of logged in account name.", new SettingColor(45, 225, 45)
   );
   public final Setting<SettingColor> placeholderColor = this.color(
      this.sgTextColors, "placeholder", "Color of placeholder text.", new SettingColor(255, 255, 255, 20)
   );
   public final MeteorGuiTheme.ThreeStateColorSetting backgroundColor = new MeteorGuiTheme.ThreeStateColorSetting(
      this.sgBackgroundColors, "background", new SettingColor(20, 20, 20, 200), new SettingColor(30, 30, 30, 200), new SettingColor(40, 40, 40, 200)
   );
   public final Setting<SettingColor> moduleBackground = this.color(
      this.sgBackgroundColors, "module-background", "Color of module background when active.", new SettingColor(50, 50, 50)
   );
   public final MeteorGuiTheme.ThreeStateColorSetting outlineColor = new MeteorGuiTheme.ThreeStateColorSetting(
      this.sgOutline, "outline", new SettingColor(0, 0, 0), new SettingColor(10, 10, 10), new SettingColor(20, 20, 20)
   );
   public final Setting<SettingColor> separatorText = this.color(this.sgSeparator, "separator-text", "Color of separator text", new SettingColor(255, 255, 255));
   public final Setting<SettingColor> separatorCenter = this.color(
      this.sgSeparator, "separator-center", "Center color of separators.", new SettingColor(255, 255, 255)
   );
   public final Setting<SettingColor> separatorEdges = this.color(
      this.sgSeparator, "separator-edges", "Color of separator edges.", new SettingColor(225, 225, 225, 150)
   );
   public final MeteorGuiTheme.ThreeStateColorSetting scrollbarColor = new MeteorGuiTheme.ThreeStateColorSetting(
      this.sgScrollbar, "Scrollbar", new SettingColor(30, 30, 30, 200), new SettingColor(40, 40, 40, 200), new SettingColor(50, 50, 50, 200)
   );
   public final MeteorGuiTheme.ThreeStateColorSetting sliderHandle = new MeteorGuiTheme.ThreeStateColorSetting(
      this.sgSlider, "slider-handle", new SettingColor(130, 0, 255), new SettingColor(140, 30, 255), new SettingColor(150, 60, 255)
   );
   public final Setting<SettingColor> sliderLeft = this.color(this.sgSlider, "slider-left", "Color of slider left part.", new SettingColor(100, 35, 170));
   public final Setting<SettingColor> sliderRight = this.color(this.sgSlider, "slider-right", "Color of slider right part.", new SettingColor(50, 50, 50));
   private final Setting<SettingColor> starscriptText = this.color(
      this.sgStarscript, "starscript-text", "Color of text in Starscript code.", new SettingColor(169, 183, 198)
   );
   private final Setting<SettingColor> starscriptBraces = this.color(
      this.sgStarscript, "starscript-braces", "Color of braces in Starscript code.", new SettingColor(150, 150, 150)
   );
   private final Setting<SettingColor> starscriptParenthesis = this.color(
      this.sgStarscript, "starscript-parenthesis", "Color of parenthesis in Starscript code.", new SettingColor(169, 183, 198)
   );
   private final Setting<SettingColor> starscriptDots = this.color(
      this.sgStarscript, "starscript-dots", "Color of dots in starscript code.", new SettingColor(169, 183, 198)
   );
   private final Setting<SettingColor> starscriptCommas = this.color(
      this.sgStarscript, "starscript-commas", "Color of commas in starscript code.", new SettingColor(169, 183, 198)
   );
   private final Setting<SettingColor> starscriptOperators = this.color(
      this.sgStarscript, "starscript-operators", "Color of operators in Starscript code.", new SettingColor(169, 183, 198)
   );
   private final Setting<SettingColor> starscriptStrings = this.color(
      this.sgStarscript, "starscript-strings", "Color of strings in Starscript code.", new SettingColor(106, 135, 89)
   );
   private final Setting<SettingColor> starscriptNumbers = this.color(
      this.sgStarscript, "starscript-numbers", "Color of numbers in Starscript code.", new SettingColor(104, 141, 187)
   );
   private final Setting<SettingColor> starscriptKeywords = this.color(
      this.sgStarscript, "starscript-keywords", "Color of keywords in Starscript code.", new SettingColor(204, 120, 50)
   );
   private final Setting<SettingColor> starscriptAccessedObjects = this.color(
      this.sgStarscript, "starscript-accessed-objects", "Color of accessed objects (before a dot) in Starscript code.", new SettingColor(152, 118, 170)
   );

   public MeteorGuiTheme() {
      super("Meteor");
      this.settingsFactory = new DefaultSettingsWidgetFactory(this);
   }

   private Setting<SettingColor> color(SettingGroup group, String name, String description, SettingColor color) {
      return group.add(new ColorSetting.Builder().name(name + "-color").description(description).defaultValue(color).build());
   }

   private Setting<SettingColor> color(String name, String description, SettingColor color) {
      return this.color(this.sgColors, name, description, color);
   }

   @Override
   public WWindow window(WWidget icon, String title) {
      return this.w(new WMeteorWindow(icon, title));
   }

   @Override
   public WLabel label(String text, boolean title, double maxWidth) {
      return maxWidth == 0.0 && !text.contains("\n") ? this.w(new WMeteorLabel(text, title)) : this.w(new WMeteorMultiLabel(text, title, maxWidth));
   }

   @Override
   public WHorizontalSeparator horizontalSeparator(String text) {
      return this.w(new WMeteorHorizontalSeparator(text));
   }

   @Override
   public WVerticalSeparator verticalSeparator() {
      return this.w(new WMeteorVerticalSeparator());
   }

   @Override
   protected WButton button(String text, GuiTexture texture) {
      return this.w(new WMeteorButton(text, texture));
   }

   @Override
   protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) {
      return this.w(new WMeteorConfirmedButton(text, confirmText, texture));
   }

   @Override
   public WMinus minus() {
      return this.w(new WMeteorMinus());
   }

   @Override
   public WConfirmedMinus confirmedMinus() {
      return this.w(new WMeteorConfirmedMinus());
   }

   @Override
   public WPlus plus() {
      return this.w(new WMeteorPlus());
   }

   @Override
   public WCheckbox checkbox(boolean checked) {
      return this.w(new WMeteorCheckbox(checked));
   }

   @Override
   public WSlider slider(double value, double min, double max) {
      return this.w(new WMeteorSlider(value, min, max));
   }

   @Override
   public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
      return this.w(new WMeteorTextBox(text, placeholder, filter, renderer));
   }

   @Override
   public <T> WDropdown<T> dropdown(T[] values, T value) {
      return this.w(new WMeteorDropdown<>(values, value));
   }

   @Override
   public WTriangle triangle() {
      return this.w(new WMeteorTriangle());
   }

   @Override
   public WTooltip tooltip(String text) {
      return this.w(new WMeteorTooltip(text));
   }

   @Override
   public WView view() {
      return this.w(new WMeteorView());
   }

   @Override
   public WSection section(String title, boolean expanded, WWidget headerWidget) {
      return this.w(new WMeteorSection(title, expanded, headerWidget));
   }

   @Override
   public WAccount account(WidgetScreen screen, Account<?> account) {
      return this.w(new WMeteorAccount(screen, account));
   }

   @Override
   public WWidget module(Module module, String title) {
      return this.w(new WMeteorModule(module, title));
   }

   @Override
   public WQuad quad(Color color) {
      return this.w(new WMeteorQuad(color));
   }

   @Override
   public WTopBar topBar() {
      return this.w(new WMeteorTopBar());
   }

   @Override
   public WFavorite favorite(boolean checked) {
      return this.w(new WMeteorFavorite(checked));
   }

   @Override
   public Color textColor() {
      return this.textColor.get();
   }

   @Override
   public Color textSecondaryColor() {
      return this.textSecondaryColor.get();
   }

   @Override
   public Color starscriptTextColor() {
      return this.starscriptText.get();
   }

   @Override
   public Color starscriptBraceColor() {
      return this.starscriptBraces.get();
   }

   @Override
   public Color starscriptParenthesisColor() {
      return this.starscriptParenthesis.get();
   }

   @Override
   public Color starscriptDotColor() {
      return this.starscriptDots.get();
   }

   @Override
   public Color starscriptCommaColor() {
      return this.starscriptCommas.get();
   }

   @Override
   public Color starscriptOperatorColor() {
      return this.starscriptOperators.get();
   }

   @Override
   public Color starscriptStringColor() {
      return this.starscriptStrings.get();
   }

   @Override
   public Color starscriptNumberColor() {
      return this.starscriptNumbers.get();
   }

   @Override
   public Color starscriptKeywordColor() {
      return this.starscriptKeywords.get();
   }

   @Override
   public Color starscriptAccessedObjectColor() {
      return this.starscriptAccessedObjects.get();
   }

   @Override
   public TextRenderer textRenderer() {
      return TextRenderer.get();
   }

   @Override
   public double scale(double value) {
      double scaled = value * this.scale.get();
      if (MacosUtil.IS_MACOS) {
         scaled /= (double)SpecterCore.mc.getWindow().getScreenWidth() / SpecterCore.mc.getWindow().getWidth();
      }

      return scaled;
   }

   @Override
   public boolean categoryIcons() {
      return this.categoryIcons.get();
   }

   @Override
   public boolean hideHUD() {
      return this.hideHUD.get();
   }

   public class ThreeStateColorSetting {
      private final Setting<SettingColor> normal;
      private final Setting<SettingColor> hovered;
      private final Setting<SettingColor> pressed;

      public ThreeStateColorSetting(SettingGroup group, String name, SettingColor c1, SettingColor c2, SettingColor c3) {
         this.normal = MeteorGuiTheme.this.color(group, name, "Color of " + name + ".", c1);
         this.hovered = MeteorGuiTheme.this.color(group, "hovered-" + name, "Color of " + name + " when hovered.", c2);
         this.pressed = MeteorGuiTheme.this.color(group, "pressed-" + name, "Color of " + name + " when pressed.", c3);
      }

      public SettingColor get() {
         return this.normal.get();
      }

      public SettingColor get(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
         if (pressed) {
            return this.pressed.get();
         } else {
            return !hovered || !bypassDisableHoverColor && MeteorGuiTheme.this.disableHoverColor ? this.normal.get() : this.hovered.get();
         }
      }

      public SettingColor get(boolean pressed, boolean hovered) {
         return this.get(pressed, hovered, false);
      }
   }
}

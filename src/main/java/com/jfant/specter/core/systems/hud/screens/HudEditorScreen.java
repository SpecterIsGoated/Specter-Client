package com.jfant.specter.core.systems.hud.screens;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.gui.tabs.builtin.HudTab;
import com.jfant.specter.core.renderer.Renderer2D;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.HudElement;
import com.jfant.specter.core.systems.hud.HudRenderer;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.core.utils.other.Snapper;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.gui.screen.HudAddPanel;
import com.jfant.specter.gui.screen.HudElementPanel;
import com.jfant.specter.render.CardLayout;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.PurpleCard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

public class HudEditorScreen extends WidgetScreen implements Snapper.Container {
   private static final Color SPLIT_LINES_COLOR = new Color(190, 130, 255, 90);
   private static final Color INACTIVE_BG_COLOR = new Color(215, 60, 105, 45);
   private static final Color INACTIVE_OL_COLOR = new Color(225, 75, 120, 190);
   private static final Color HOVER_BG_COLOR = new Color(255, 110, 199, 36);
   private static final Color HOVER_OL_COLOR = new Color(255, 110, 199, 235);
   private static final Color SELECTION_BG_COLOR = new Color(176, 64, 224, 38);
   private static final Color SELECTION_OL_COLOR = new Color(196, 130, 255, 200);
   private static final int PINK = -37177;
   private static final String[] HINT = new String[]{
      "Drag to move  ·  Right-click an element for its settings", "Right-click empty space to add  ·  Delete removes"
   };
   private static final String SETTINGS_LABEL = "HUD Settings";
   private int cardX;
   private int cardY;
   private int cardW;
   private int cardH;
   private int buttonX;
   private int buttonY;
   private int buttonW;
   private int buttonH;
   private boolean cardPressed;
   private CardSpot heldCard;
   private final Hud hud;
   private final Snapper snapper;
   private Snapper.Element selectionSnapBox;
   private int lastMouseX;
   private int lastMouseY;
   private boolean pressed;
   private int clickX;
   private int clickY;
   private final List<HudElement> selection = new ArrayList<>();
   private boolean moved;
   private boolean dragging;
   private HudElement addedHoveredToSelectionWhenClickedElement;
   private double splitLinesAnimation;

   public HudEditorScreen(GuiTheme theme) {
      super(theme, "Hud Editor");
      this.hud = Hud.get();
      this.snapper = new Snapper(this);
   }

   @Override
   public void initWidgets() {
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double s = SpecterCore.mc.getWindow().getGuiScale();
      if (this.inCard(click.x(), click.y())) {
         this.cardPressed = true;
         return true;
      } else {
         CardSpot card = CardLayout.at(click.x(), click.y());
         if (card != null) {
            this.heldCard = card;
            return true;
         } else {
            double mouseX = click.x();
            double mouseY = click.y();
            mouseX *= s;
            mouseY *= s;
            if (click.button() == 0) {
               this.pressed = true;
               this.selectionSnapBox = null;
               HudElement hovered = this.getHovered((int)mouseX, (int)mouseY);
               this.dragging = hovered != null;
               if (this.dragging) {
                  if (!this.selection.contains(hovered)) {
                     this.selection.clear();
                     this.selection.add(hovered);
                     this.addedHoveredToSelectionWhenClickedElement = hovered;
                  }
               } else {
                  this.selection.clear();
               }

               this.clickX = (int)mouseX;
               this.clickY = (int)mouseY;
            }

            return false;
         }
      }
   }

   @Override
   public void mouseMoved(double mouseX, double mouseY) {
      double s = SpecterCore.mc.getWindow().getGuiScale();
      mouseX *= s;
      mouseY *= s;
      if (this.dragging && !this.selection.isEmpty()) {
         if (this.selectionSnapBox == null) {
            this.selectionSnapBox = new HudEditorScreen.SelectionBox();
         }

         this.snapper.move(this.selectionSnapBox, (int)mouseX - this.lastMouseX, (int)mouseY - this.lastMouseY);
      }

      if (this.pressed) {
         this.moved = true;
      }

      this.lastMouseX = (int)mouseX;
      this.lastMouseY = (int)mouseY;
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.cardPressed) {
         this.cardPressed = false;
         if (click.button() == 0 && this.inButton(click.x(), click.y())) {
            SpecterCore.mc.gui.setScreen(HudElementPanel.global(this));
         }

         return true;
      } else if (this.heldCard != null) {
         CardSpot card = this.heldCard;
         this.heldCard = null;
         if (click.button() == 1 && CardLayout.at(click.x(), click.y()) == card) {
            SpecterCore.mc.gui.setScreen(HudElementPanel.of(this, card));
         }

         return true;
      } else {
         double s = SpecterCore.mc.getWindow().getGuiScale();
         double mouseX = click.x();
         double mouseY = click.y();
         mouseX *= s;
         mouseY *= s;
         if (click.button() == 0) {
            this.pressed = false;
         }

         if (this.addedHoveredToSelectionWhenClickedElement != null) {
            this.selection.remove(this.addedHoveredToSelectionWhenClickedElement);
            this.addedHoveredToSelectionWhenClickedElement = null;
         }

         if (this.moved) {
            if (click.button() == 0 && !this.dragging) {
               this.fillSelection((int)mouseX, (int)mouseY);
            }
         } else if (click.button() == 0) {
            HudElement hovered = this.getHovered((int)mouseX, (int)mouseY);
            if (hovered != null) {
               hovered.toggle();
            }
         } else if (click.button() == 1) {
            HudElement hovered = this.getHovered((int)mouseX, (int)mouseY);
            if (hovered != null) {
               SpecterCore.mc.gui.setScreen(HudElementPanel.of(this, hovered));
            } else {
               SpecterCore.mc.gui.setScreen(new HudAddPanel(this, this.lastMouseX, this.lastMouseY));
            }
         }

         if (click.button() == 0) {
            this.snapper.unsnap();
            this.moved = this.dragging = false;
         }

         return false;
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (!this.pressed) {
         if (input.key() == 257 || input.key() == 335) {
            HudElement hovered = this.getHovered(this.lastMouseX, this.lastMouseY);
            if (hovered != null) {
               hovered.toggle();
            }
         } else if (input.key() == 261) {
            HudElement hovered = this.getHovered(this.lastMouseX, this.lastMouseY);
            double s = SpecterCore.mc.getWindow().getGuiScale();
            CardSpot card = CardLayout.at(this.lastMouseX / s, this.lastMouseY / s);
            if (card != null) {
               if (card.owner().isActive()) {
                  card.owner().toggle();
               }
            } else if (hovered != null) {
               hovered.remove();
            } else {
               for (HudElement element : this.selection) {
                  element.remove();
               }

               this.selection.clear();
            }
         } else if (!this.selection.isEmpty()) {
            int pixels = !Input.isKeyPressed(341) && !Input.isKeyPressed(345) ? 1 : 10;
            int dx = 0;
            int dy = 0;
            switch (input.key()) {
               case 262:
                  dx = pixels;
                  break;
               case 263:
                  dx = -pixels;
                  break;
               case 264:
                  dy = pixels;
                  break;
               case 265:
                  dy = -pixels;
            }

            for (HudElement element : this.selection) {
               element.move(dx, dy);
            }
         }
      }

      return super.keyPressed(input);
   }

   private void fillSelection(int mouseX, int mouseY) {
      int x1 = Math.min(this.clickX, mouseX);
      int x2 = Math.max(this.clickX, mouseX);
      int y1 = Math.min(this.clickY, mouseY);
      int y2 = Math.max(this.clickY, mouseY);

      for (HudElement e : this.hud) {
         if (e.getX() <= x2 && e.getX2() >= x1 && e.getY() <= y2 && e.getY2() >= y1) {
            this.selection.add(e);
         }
      }
   }

   @Override
   public Iterable<Snapper.Element> getElements() {
      return () -> new Iterator<Snapper.Element>() {
         private final Iterator<HudElement> it = HudEditorScreen.this.hud.iterator();

         @Override
         public boolean hasNext() {
            return this.it.hasNext();
         }

         public Snapper.Element next() {
            return this.it.next();
         }
      };
   }

   @Override
   public boolean shouldNotSnapTo(Snapper.Element element) {
      return this.selection.contains((HudElement)element);
   }

   @Override
   public int getSnappingRange() {
      return this.hud.snappingRange.get();
   }

   private void onRender(int mouseX, int mouseY) {
      for (HudElement element : this.hud) {
         if (!element.isActive()) {
            this.renderElement(element, INACTIVE_BG_COLOR, INACTIVE_OL_COLOR);
         }
      }

      if (this.pressed && !this.dragging) {
         this.fillSelection(mouseX, mouseY);
      }

      for (HudElement elementx : this.selection) {
         this.renderElement(elementx, HOVER_BG_COLOR, HOVER_OL_COLOR);
      }

      if (this.pressed && !this.dragging) {
         this.selection.clear();
      }

      if (this.pressed && !this.dragging) {
         int x1 = Math.min(this.clickX, mouseX);
         int x2 = Math.max(this.clickX, mouseX);
         int y1 = Math.min(this.clickY, mouseY);
         int y2 = Math.max(this.clickY, mouseY);
         this.renderQuad(x1, y1, x2 - x1, y2 - y1, SELECTION_BG_COLOR, SELECTION_OL_COLOR);
      }

      if (!this.pressed) {
         HudElement hovered = this.getHovered(mouseX, mouseY);
         if (hovered != null) {
            this.renderElement(hovered, HOVER_BG_COLOR, HOVER_OL_COLOR);
         }
      }
   }

   private HudElement getHovered(int mouseX, int mouseY) {
      for (HudElement element : this.hud) {
         if (mouseX >= element.x && mouseX <= element.x + element.getWidth() && mouseY >= element.y && mouseY <= element.y + element.getHeight()) {
            return element;
         }
      }

      return null;
   }

   private void renderQuad(double x, double y, double w, double h, Color bgColor, Color olColor) {
      Renderer2D.COLOR.quad(x + 1.0, y + 1.0, w - 2.0, h - 2.0, bgColor);
      Renderer2D.COLOR.quad(x, y, w, 1.0, olColor);
      Renderer2D.COLOR.quad(x, y + h - 1.0, w, 1.0, olColor);
      Renderer2D.COLOR.quad(x, y + 1.0, 1.0, h - 2.0, olColor);
      Renderer2D.COLOR.quad(x + w - 1.0, y + 1.0, 1.0, h - 2.0, olColor);
   }

   private void renderElement(HudElement element, Color bgColor, Color olColor) {
      this.renderQuad(element.x, element.y, element.getWidth(), element.getHeight(), bgColor, olColor);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int s = SpecterCore.mc.getWindow().getGuiScale();
      mouseX *= s;
      mouseY *= s;
      Utils.unscaledProjection();
      boolean renderSplitLines = this.pressed && !this.selection.isEmpty() && this.moved;
      if (renderSplitLines || this.splitLinesAnimation > 0.0) {
         this.renderSplitLines(renderSplitLines, delta / 20.0F);
      }

      renderElements(context);
      Renderer2D.COLOR.begin();
      this.onRender(mouseX, mouseY);
      Renderer2D.COLOR.render();
      Utils.scaledProjection();
      this.renderCards(context, mouseX / s, mouseY / s);
      this.renderHint(context, mouseX / s, mouseY / s);
      this.runAfterRenderTasks();
   }

   private void renderCards(GuiGraphicsExtractor ctx, int mx, int my) {
      CardSpot hovered = this.heldCard != null ? this.heldCard : (this.pressed ? null : CardLayout.at(mx, my));

      for (CardSpot card : CardSpot.all()) {
         float[] rect = CardLayout.liveRect(card);
         if (rect != null) {
            int x1 = (int)Math.floor(rect[0]) - 1;
            int y1 = (int)Math.floor(rect[1]) - 1;
            int x2 = (int)Math.ceil(rect[0] + rect[2]) + 1;
            int y2 = (int)Math.ceil(rect[1] + rect[3]) + 1;
            boolean hot = card == hovered;
            if (hot) {
               ctx.fill(x1, y1, x2, y2, 620719815);
            }

            int edge = hot ? -335581497 : 1623491327;
            ctx.fill(x1, y1, x2, y1 + 1, edge);
            ctx.fill(x1, y2 - 1, x2, y2, edge);
            ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, edge);
            ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, edge);
         }
      }
   }

   private boolean inCard(double x, double y) {
      return x >= this.cardX && x < this.cardX + this.cardW && y >= this.cardY && y < this.cardY + this.cardH;
   }

   private boolean inButton(double x, double y) {
      return x >= this.buttonX && x < this.buttonX + this.buttonW && y >= this.buttonY && y < this.buttonY + this.buttonH;
   }

   private void renderHint(GuiGraphicsExtractor ctx, int mx, int my) {
      int screenW = SpecterCore.mc.getWindow().getGuiScaledWidth();
      int screenH = SpecterCore.mc.getWindow().getGuiScaledHeight();
      int textW = Math.max(CardText.width(HINT[0]), CardText.width(HINT[1]));
      this.buttonW = CardText.width("HUD Settings") + 14;
      this.buttonH = 16;
      this.cardW = Math.min(screenW - 8, textW + this.buttonW + 14 + 12 + 8);
      this.cardH = 34;
      this.cardX = (screenW - this.cardW) / 2;
      this.cardY = screenH - this.cardH - 6;
      this.buttonX = this.cardX + this.cardW - this.buttonW - 8;
      this.buttonY = this.cardY + (this.cardH - this.buttonH) / 2;
      PurpleCard.card(ctx, this.cardX, this.cardY, this.cardX + this.cardW, this.cardY + this.cardH);
      CardText.draw(ctx, HINT[0], (float)(this.cardX + 10), (float)(this.cardY + 8), -1911561, true);
      CardText.draw(ctx, HINT[1], (float)(this.cardX + 10), (float)(this.cardY + 19), -4149795, true);
      boolean hover = this.inButton(mx, my);
      PurpleCard.fillRounded(
         ctx,
         this.buttonX,
         this.buttonY,
         this.buttonX + this.buttonW,
         this.buttonY + this.buttonH,
         3,
         15,
         hover ? -2135932704 : 1617964960,
         hover ? 1617964960 : 1077942384
      );
      int edge = hover ? -37177 : -1333837902;
      PurpleCard.strokeRounded(ctx, this.buttonX, this.buttonY, this.buttonX + this.buttonW, this.buttonY + this.buttonH, 3, edge, edge);
      CardText.draw(
         ctx,
         "HUD Settings",
         this.buttonX + (this.buttonW - CardText.width("HUD Settings")) / 2.0F,
         this.buttonY + (this.buttonH - 8) / 2.0F,
         hover ? -1 : -1911561,
         true
      );
   }

   public static void renderElements(GuiGraphicsExtractor drawContext) {
      Hud hud = Hud.get();
      boolean inactiveOnly = Utils.canUpdate() && hud.active;
      HudRenderer.INSTANCE.begin(drawContext);

      for (HudElement element : hud) {
         element.updatePos();
         if (inactiveOnly) {
            if (!element.isActive()) {
               element.render(HudRenderer.INSTANCE);
            }
         } else {
            element.render(HudRenderer.INSTANCE);
         }
      }

      HudRenderer.INSTANCE.end();
   }

   private void renderSplitLines(boolean increment, double delta) {
      if (increment) {
         this.splitLinesAnimation += delta * 6.0;
      } else {
         this.splitLinesAnimation -= delta * 6.0;
      }

      this.splitLinesAnimation = Mth.clamp(this.splitLinesAnimation, 0.0, 1.0);
      Renderer2D renderer = Renderer2D.COLOR;
      renderer.begin();
      double w = Utils.getWindowWidth();
      double h = Utils.getWindowHeight();
      double w3 = w / 3.0;
      double h3 = h / 3.0;
      int prevA = SPLIT_LINES_COLOR.a;
      SPLIT_LINES_COLOR.a = (int)(SPLIT_LINES_COLOR.a * this.splitLinesAnimation);
      this.renderSplitLine(renderer, w3, 0.0, w3, h);
      this.renderSplitLine(renderer, w3 * 2.0, 0.0, w3 * 2.0, h);
      this.renderSplitLine(renderer, 0.0, h3, w, h3);
      this.renderSplitLine(renderer, 0.0, h3 * 2.0, w, h3 * 2.0);
      SPLIT_LINES_COLOR.a = prevA;
      renderer.render();
   }

   private void renderSplitLine(Renderer2D renderer, double x, double y, double destX, double destY) {
      double incX = 0.0;
      double incY = 0.0;
      if (x == destX) {
         incY = Utils.getWindowWidth() / 25.0;
      } else {
         incX = Utils.getWindowWidth() / 25.0;
      }

      do {
         renderer.line(x, y, x + incX, y + incY, SPLIT_LINES_COLOR);
         x += incX * 2.0;
         y += incY * 2.0;
      } while (!(x >= destX) || !(y >= destY));
   }

   public static boolean isOpen() {
      Screen s = SpecterCore.mc.gui.screen();
      return s instanceof HudEditorScreen
         || s instanceof HudElementPanel
         || s instanceof HudAddPanel
         || s instanceof AddHudElementScreen
         || s instanceof HudElementPresetsScreen
         || s instanceof HudElementScreen
         || s instanceof HudTab.HudScreen;
   }

   private class SelectionBox implements Snapper.Element {
      private int x;
      private int y;
      private final int width;
      private final int height;

      public SelectionBox() {
         int x1 = 2147483647;
         int y1 = 2147483647;
         int x2 = 0;
         int y2 = 0;

         for (HudElement element : HudEditorScreen.this.selection) {
            if (element.getX() < x1) {
               x1 = element.getX();
            } else if (element.getX() > x2) {
               x2 = element.getX();
            }

            if (element.getX2() < x1) {
               x1 = element.getX2();
            } else if (element.getX2() > x2) {
               x2 = element.getX2();
            }

            if (element.getY() < y1) {
               y1 = element.getY();
            } else if (element.getY() > y2) {
               y2 = element.getY();
            }

            if (element.getY2() < y1) {
               y1 = element.getY2();
            } else if (element.getY2() > y2) {
               y2 = element.getY2();
            }
         }

         this.x = x1;
         this.y = y1;
         this.width = x2 - x1;
         this.height = y2 - y1;
      }

      @Override
      public int getX() {
         return this.x;
      }

      @Override
      public int getY() {
         return this.y;
      }

      @Override
      public int getWidth() {
         return this.width;
      }

      @Override
      public int getHeight() {
         return this.height;
      }

      @Override
      public void setPos(int x, int y) {
         for (HudElement element : HudEditorScreen.this.selection) {
            element.setPos(x + (element.x - this.x), y + (element.y - this.y));
         }

         this.x = x;
         this.y = y;
      }

      @Override
      public void move(int deltaX, int deltaY) {
         int lastX = this.x;
         int lastY = this.y;
         int border = Hud.get().border.get();
         this.x = Mth.clamp(this.x + deltaX, border, Utils.getWindowWidth() - this.width - border);
         this.y = Mth.clamp(this.y + deltaY, border, Utils.getWindowHeight() - this.height - border);

         for (HudElement element : HudEditorScreen.this.selection) {
            element.move(this.x - lastX, this.y - lastY);
         }
      }
   }
}

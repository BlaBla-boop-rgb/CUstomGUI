package dev.customgui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

public class CustomGuiScreen extends Screen {

    private static final int BTN_W = 140;
    private static final int BTN_H = 28;
    private static final int GAP = 10;

    /** Ein animierter Button. hover läuft weich zwischen 0 (nicht gehovert) und 1 (gehovert). */
    private static final class Btn {
        final String label;
        final String tooltip;
        final int accent; // 0xRRGGBB
        final Runnable action;
        int x, y;
        float hover = 0f;
        boolean wasHovered = false;

        Btn(String label, String tooltip, int accent, Runnable action) {
            this.label = label;
            this.tooltip = tooltip;
            this.accent = accent;
            this.action = action;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
        }
    }

    private final List<Btn> buttons = new ArrayList<>();
    private long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    public CustomGuiScreen() {
        super(Component.literal("Custom GUI"));
    }

    @Override
    protected void init() {
        buttons.clear();

        // >>> Hier kannst du die Aktionen der Buttons anpassen <<<
        buttons.add(new Btn("Spawn", "Führt /spawn aus", 0x55FFFF, () -> runCommand("spawn")));
        buttons.add(new Btn("Shop", "Führt /shop aus", 0x55FF55, () -> runCommand("shop")));
        buttons.add(new Btn("Schließen", "Schließt dieses Menü", 0xFF5555, this::onClose));

        int totalH = buttons.size() * BTN_H + (buttons.size() - 1) * GAP;
        int startY = this.height / 2 - totalH / 2 + 8;
        int x = this.width / 2 - BTN_W / 2;
        for (int i = 0; i < buttons.size(); i++) {
            Btn b = buttons.get(i);
            b.x = x;
            b.y = startY + i * (BTN_H + GAP);
        }
        openedAt = System.nanoTime();
        lastFrame = openedAt;
    }

    private void runCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta); // Hintergrund

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float time = (now - openedAt) / 1_000_000_000f;

        // Einblend-Animation (0 -> 1 in 0,35 s, easeOutCubic)
        float open = Math.min(1f, time / 0.35f);
        open = 1f - (float) Math.pow(1f - open, 3);
        int slide = Math.round((1f - open) * 18f);

        // Panel
        int panelW = BTN_W + 60;
        int totalH = buttons.size() * BTN_H + (buttons.size() - 1) * GAP;
        int panelH = totalH + 70;
        int px = this.width / 2 - panelW / 2;
        int py = this.height / 2 - panelH / 2 + slide;
        g.fill(px, py, px + panelW, py + panelH, withAlpha(0xFF14141E, open * 0.92f));
        g.fill(px, py, px + panelW, py + 2, withAlpha(0xFF55FFFF, open));

        // Titel
        Component title = Component.literal("Mein Custom GUI").withStyle(ChatFormatting.BOLD);
        g.text(this.font, title, this.width / 2 - this.font.width(title) / 2, py + 12,
                withAlpha(0xFFFFFFFF, open));

        // Buttons
        Btn hoveredBtn = null;
        for (Btn b : buttons) {
            boolean hovered = b.contains(mouseX, mouseY - slide) && open > 0.95f;
            if (hovered) hoveredBtn = b;

            // Hover-Sound einmal beim Betreten
            if (hovered && !b.wasHovered) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.8f));
            }
            b.wasHovered = hovered;

            // Weiches Annähern an Ziel (frameraten-unabhängig)
            float target = hovered ? 1f : 0f;
            b.hover += (target - b.hover) * (1f - (float) Math.exp(-dt * 14f));

            drawButton(g, b, slide, time, open);
        }

        // Tooltip-Zeile unten im Panel
        if (hoveredBtn != null) {
            Component tip = Component.literal(hoveredBtn.tooltip).withStyle(ChatFormatting.GRAY);
            g.text(this.font, tip, this.width / 2 - this.font.width(tip) / 2,
                    py + panelH - 16, withAlpha(0xFFFFFFFF, open));
        }
    }

    private void drawButton(GuiGraphicsExtractor g, Btn b, int slide, float time, float open) {
        float h = b.hover;
        int grow = Math.round(h * 3f);      // Button wird beim Hover größer
        int lift = Math.round(h * 2f);      // ...und hebt sich leicht an
        int x1 = b.x - grow;
        int y1 = b.y + slide - grow - lift;
        int x2 = b.x + BTN_W + grow;
        int y2 = b.y + slide + BTN_H + grow - lift;

        int accent = 0xFF000000 | b.accent;

        // Glow hinter dem Button
        if (h > 0.02f) {
            for (int i = 3; i >= 1; i--) {
                g.fill(x1 - i, y1 - i, x2 + i, y2 + i, withAlpha(accent, open * h * 0.12f));
            }
        }

        // Rahmen + Hintergrund (Farbe blendet von dunkel zu Akzent)
        int border = lerpColor(0xFF44445A, accent, h);
        int bg = lerpColor(0xFF2A2A3A, mixWithBlack(accent, 0.45f), h * 0.8f);
        g.fill(x1, y1, x2, y2, withAlpha(border, open));
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, withAlpha(bg, open));

        // Glanz-Streifen, der beim Hover über den Button wandert
        if (h > 0.02f) {
            float t = (time * 0.9f) % 1.4f - 0.2f;
            int sx = x1 + Math.round(t * (x2 - x1));
            int stripeL = Math.max(x1 + 1, sx);
            int stripeR = Math.min(x2 - 1, sx + 10);
            if (stripeR > stripeL) {
                g.fill(stripeL, y1 + 1, stripeR, y2 - 1, withAlpha(0xFFFFFFFF, open * h * 0.18f));
            }
        }

        // Text
        Component label = Component.literal(b.label).withStyle(ChatFormatting.BOLD);
        int textColor = lerpColor(0xFFDDDDDD, 0xFFFFFFFF, h);
        g.text(this.font, label, (x1 + x2) / 2 - this.font.width(label) / 2,
                (y1 + y2) / 2 - this.font.lineHeight / 2 + 1, withAlpha(textColor, open));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Btn b : buttons) {
                if (b.contains(event.x(), event.y())) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ---------- Farb-Helfer (ARGB) ----------

    private static int withAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = lerp((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, t);
        int r = lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
        int gr = lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
        int b = lerp(c1 & 0xFF, c2 & 0xFF, t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int mixWithBlack(int argb, float keep) {
        int r = Math.round(((argb >> 16) & 0xFF) * keep);
        int g = Math.round(((argb >> 8) & 0xFF) * keep);
        int b = Math.round((argb & 0xFF) * keep);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}

package dev.v1sion.vanillarefined.client;

import dev.v1sion.vanillarefined.Config;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Polished HUD: sliding hotbar glow, info panel, armor panel and animated toasts. */
final class Hud {
    static float glowX = -1;        // animated hotbar highlight position
    static boolean infoOpen = true;
    static float infoAnim = 1;

    record Toast(String icon, String title, String sub, int color, long start) {}
    static final Deque<Toast> toasts = new ArrayDeque<>();
    static final Set<String> warned = new HashSet<>();
    static boolean darkWarned, fullWarned;

    static void toast(String icon, String title, String sub, int color) {
        if (!Config.I.toasts) return;
        toasts.addLast(new Toast(icon, title, sub, color, System.currentTimeMillis()));
        while (toasts.size() > 3) toasts.removeFirst();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WorldFx.SPARKLE, 1.3f, .35f));
    }

    // ------------------------------------------------------------------ hotbar (drawn after the vanilla hotbar)

    static void hotbar(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!Config.I.hotbarPolish || p == null || mc.gui.hud.isHidden() || p.isSpectator()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int target = w / 2 - 91 - 1 + p.getInventory().getSelectedSlot() * 20;
        if (glowX < 0) glowX = target;
        glowX += (target - glowX) * Math.min(1f, dt.getRealtimeDeltaTicks() * .45f); // smooth slide between slots
        int x = Math.round(glowX), y = h - 23;
        float pulse = (float) (Math.sin(System.currentTimeMillis() / 380.0) * .5 + .5);
        int a = 60 + (int) (60 * pulse);
        g.fill(x - 1, y - 1, x + 25, y, argb(a, 0xFFE9A8));
        g.fill(x - 1, y + 24, x + 25, y + 25, argb(a, 0xFFE9A8));
        g.fill(x - 1, y, x, y + 24, argb(a, 0xFFE9A8));
        g.fill(x + 24, y, x + 25, y + 24, argb(a, 0xFFE9A8));
        g.fillGradient(x + 1, y + 1, x + 23, y + 23, argb(35 + (int) (25 * pulse), 0xFFF2C0), argb(0, 0xFFF2C0));
        // offhand slot gets the same golden frame
        if (!p.getOffhandItem().isEmpty()) {
            boolean left = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
            int ox = left ? w / 2 - 91 - 29 : w / 2 + 91;
            g.outline(ox, y, 29, 24, argb(70, 0xFFE9A8));
        }
    }

    // ------------------------------------------------------------------ info + armor + toasts

    static void overlay(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
        Font f = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();

        // info panel (top-left), slides in/out with the key
        infoAnim += ((infoOpen ? 1 : 0) - infoAnim) * .25f;
        if (Config.I.infoHud && infoAnim > .02f) {
            BlockPos bp = p.blockPosition();
            long time = mc.level.getOverworldClockTime() % 24000;
            int hh = (int) ((time / 1000 + 6) % 24), mm = (int) (time % 1000 * 60 / 1000);
            String biome = mc.level.getBiome(bp).unwrapKey().map(k -> pretty(k.identifier().getPath())).orElse("?");
            String[] lines = {
                    "§fXYZ §7" + bp.getX() + " " + bp.getY() + " " + bp.getZ(),
                    "§fFacing §7" + pretty(p.getDirection().getName()),
                    "§fBiome §7" + biome,
                    "§fTime §7" + String.format("%02d:%02d", hh, mm) + (time < 12300 || time > 23500 ? " §e☀" : " §9☾"),
                    "§fFPS §7" + mc.getFps()};
            int bw = 0;
            for (String s : lines) bw = Math.max(bw, f.width(s));
            int x = (int) (-bw - 16 + (bw + 20) * infoAnim), y = 4;
            g.fill(x - 4, y - 3, x + bw + 6, y + lines.length * 10 + 1, 0x90000000);
            g.fill(x - 4, y - 3, x - 2, y + lines.length * 10 + 1, 0xFFFFC857);
            for (int i = 0; i < lines.length; i++) g.text(f, lines[i], x + 2, y + i * 10, 0xFFFFFFFF, true);
        }

        // armor + held item durability panel (right of hotbar)
        if (Config.I.armorHud && !p.isSpectator()) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
            int x = w / 2 + 98 + (p.getOffhandItem().isEmpty() ? 0 : 30), y = h - 21;
            int shown = 0;
            for (EquipmentSlot s : slots) {
                ItemStack it = p.getItemBySlot(s);
                if (it.isEmpty() || !it.isDamageableItem()) continue;
                int ix = x + shown * 18;
                g.item(it, ix, y);
                float frac = 1f - it.getDamageValue() / (float) it.getMaxDamage();
                int col = frac > .5f ? 0xFF6BFF8A : frac > .2f ? 0xFFFFD84D : 0xFFFF5A5A;
                g.fill(ix + 1, y + 17, ix + 17, y + 19, 0xAA000000);
                g.fill(ix + 1, y + 17, ix + 1 + Math.round(16 * frac), y + 19, col);
                shown++;
                // low durability toast (once per item until repaired)
                String key = s.name() + it.getItem();
                if (frac < .1f && warned.add(key)) toast("⚠", it.getHoverName().getString() + " is almost broken", (it.getMaxDamage() - it.getDamageValue()) + " uses left", 0xFFFF5A5A);
                if (frac > .3f) warned.remove(key);
            }
        }

        // status toasts
        long time = mc.level.getOverworldClockTime() % 24000;
        if (time > 11800 && time < 12200 && !darkWarned && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD) { darkWarned = true; toast("☾", "It's getting dark", "Monsters will spawn soon", 0xFF8FA8FF); }
        if (time < 1000) darkWarned = false;
        boolean full = p.getInventory().getFreeSlot() == -1;
        if (full && !fullWarned) { fullWarned = true; toast("▣", "Inventory full", "New items will stay on the ground", 0xFFFFC857); }
        if (!full) fullWarned = false;

        // draw toasts (top-right), sliding in and fading out
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.start > 4200);
        int ty = 6;
        for (Toast t : toasts) {
            long age = now - t.start;
            float in = Math.min(1f, age / 260f), out = age > 3700 ? 1f - (age - 3700) / 500f : 1f;
            float e = 1 - (float) Math.pow(1 - in, 3);
            int tw = Math.max(f.width(t.title), f.width(t.sub)) + 34;
            int x = w - (int) ((tw + 6) * e);
            int alpha = (int) (230 * out);
            g.fill(x, ty, x + tw, ty + 26, argb((int) (alpha * .8f), 0x15101F));
            g.fill(x, ty, x + 2, ty + 26, argb(alpha, t.color & 0xFFFFFF));
            g.text(f, t.icon, x + 9, ty + 9, argb(alpha, t.color & 0xFFFFFF), true);
            g.text(f, t.title, x + 24, ty + 4, argb(alpha, 0xFFFFFF), true);
            g.text(f, t.sub, x + 24, ty + 15, argb(alpha, 0xAAAAAA), false);
            ty += 30;
        }
    }

    static int argb(int a, int rgb) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }
    static String pretty(String s) {
        StringBuilder b = new StringBuilder();
        for (String part : s.split("_")) if (!part.isEmpty()) b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        return b.toString().trim();
    }
}

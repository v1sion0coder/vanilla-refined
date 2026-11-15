package dev.v1sion.vanillarefined.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Adds a panel to the death screen with where you died, the dimension and a copy button. */
final class DeathPanel {
    static void attach(Minecraft mc, DeathScreen screen, int w, int h) {
        if (mc.player == null) return;
        Vec3 pos = mc.player.position();
        String dim = Hud.pretty(mc.player.level().dimension().identifier().getPath());
        String coords = (int) Math.floor(pos.x) + " " + (int) Math.floor(pos.y) + " " + (int) Math.floor(pos.z);
        long opened = System.currentTimeMillis();
        int lvl = mc.player.experienceLevel;
        Screens.getWidgets(screen).add(Button.builder(Component.literal("📍 Copy death coordinates"), b -> {
            mc.keyboardHandler.setClipboard(coords);
            b.setMessage(Component.literal("✔ Copied " + coords));
        }).bounds(w / 2 - 100, h / 4 + 120, 200, 20).build());
        ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
            float a = Math.min(1f, (System.currentTimeMillis() - opened) / 700f);
            int alpha = (int) (a * 255);
            int pw = 220, x = w / 2 - pw / 2, y = h / 4 + 64 + (int) ((1 - a) * 10);
            g.fill(x, y, x + pw, y + 48, Hud.argb((int) (a * 150), 0x000000));
            g.fill(x, y, x + pw, y + 1, Hud.argb(alpha, 0xFF5A5A));
            g.centeredText(mc.font, Component.literal("§7You died at §f" + coords), w / 2, y + 8, Hud.argb(alpha, 0xFFFFFF));
            g.centeredText(mc.font, Component.literal("§7in §f" + dim + "§7  •  level §a" + lvl), w / 2, y + 21, Hud.argb(alpha, 0xFFFFFF));
            g.centeredText(mc.font, Component.literal("§8Your items are waiting there"), w / 2, y + 34, Hud.argb(alpha, 0xFFFFFF));
        });
    }
}

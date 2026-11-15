package dev.v1sion.vanillarefined.client;

import dev.v1sion.vanillarefined.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Client entrypoint: particles, sounds, HUD and the better death screen. */
public final class VanillaRefinedClient implements ClientModInitializer {
    static KeyMapping infoKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vanillarefined", "main"));
        infoKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanillarefined.info", 74 /* J */, cat));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (infoKey.consumeClick()) Hud.infoOpen = !Hud.infoOpen;
            WorldFx.tick(mc);
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("vanillarefined", "hotbar"), Hud::hotbar);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("vanillarefined", "overlay"), Hud::overlay);
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof DeathScreen ds && Config.I.betterDeathScreen) DeathPanel.attach(mc, ds, w, h);
        });
    }
}

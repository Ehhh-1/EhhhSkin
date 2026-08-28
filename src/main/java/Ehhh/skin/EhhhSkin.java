package Ehhh.skin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EhhhSkin implements ClientModInitializer {
    public static final String MOD_ID = "ehhhskin";
    public static final Identifier CUSTOM_SKIN = new Identifier(MOD_ID, "custom_skin");
    private static boolean skinLoaded = false;

    @Override
    public void onInitializeClient() {
        Path skinFolder = getSkinFolder();

        try {
            Files.createDirectories(skinFolder);
            System.out.println("[EhhhSkin] Skin folder: " + skinFolder.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            loadSkinTexture(client, skinFolder);
        });
    }

    private Path getSkinFolder() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        String version = SharedConstants.getGameVersion().getName();

        Path versionDir = gameDir.resolve("versions").resolve(version);
        if (Files.isDirectory(versionDir)) {
            return versionDir.resolve("Ehhhskin");
        }

        return gameDir.resolve("Ehhhskin");
    }

    private void loadSkinTexture(MinecraftClient client, Path folder) {
        Path skinFile = folder.resolve("Skin.png");
        if (!Files.exists(skinFile)) {
            System.out.println("[EhhhSkin] Skin.png not found, using default skin.");
            return;
        }

        try {
            NativeImage image = NativeImage.read(Files.newInputStream(skinFile));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            client.getTextureManager().registerTexture(CUSTOM_SKIN, texture);
            skinLoaded = true;
            System.out.println("[EhhhSkin] Custom skin loaded successfully.");
        } catch (IOException e) {
            System.err.println("[EhhhSkin] Failed to load skin: " + e.getMessage());
        }
    }

    public static boolean isSkinLoaded() {
        return skinLoaded;
    }
}
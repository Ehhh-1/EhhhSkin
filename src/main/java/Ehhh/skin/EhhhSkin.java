package Ehhh.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class EhhhSkin implements ClientModInitializer {
    public static final String MOD_ID = "ehhhskin";
    public static final Identifier CUSTOM_SKIN = new Identifier(MOD_ID, "custom_skin");
    private static boolean skinLoaded = false;
    private static SkinTextures.Model currentModel = SkinTextures.Model.SLIM;
    private static Identifier currentSkinTexture = CUSTOM_SKIN;
    private static String currentSkinName = null; // 当前皮肤文件名（不含扩展名）

    @Override
    public void onInitializeClient() {
        Path skinFolder = getSkinFolder();
        try {
            Files.createDirectories(skinFolder);
            System.out.println("[EhhhSkin] Skin folder: " + skinFolder.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        loadConfig();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            loadDefaultSkin(client, skinFolder);
            if (currentSkinName != null && !currentSkinName.isEmpty()) {
                loadSkinFromFile(currentSkinName);
            }
        });

        // 命令注册
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("ehhhskin")
                            .then(ClientCommandManager.literal("folder")
                                    .executes(ctx -> {
                                        openSkinFolder();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("model")
                                    .then(ClientCommandManager.literal("steve")
                                            .executes(ctx -> {
                                                setCurrentModel(SkinTextures.Model.WIDE, "模型已切换为 Steve (粗壮)");
                                                return 1;
                                            }))
                                    .then(ClientCommandManager.literal("alex")
                                            .executes(ctx -> {
                                                setCurrentModel(SkinTextures.Model.SLIM, "模型已切换为 Alex (细手臂)");
                                                return 1;
                                            }))
                            )
                            .then(ClientCommandManager.literal("skin")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "name");
                                                loadSkinFromFile(name);
                                                return 1;
                                            }))
                            )
                            .then(ClientCommandManager.literal("online")
                                    .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String id = StringArgumentType.getString(ctx, "id");
                                                loadOnlineSkin(id);
                                                return 1;
                                            }))
                            )
            );
        });
    }

    // ====================== 本地皮肤加载 ======================
    private void loadSkinFromFile(String name) {
        Path skinFolder = getSkinFolder();
        Path skinFile = skinFolder.resolve(name + ".png");
        if (!Files.exists(skinFile)) {
            sendFeedback("皮肤文件不存在: " + name + ".png");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        try {
            NativeImage image = NativeImage.read(Files.newInputStream(skinFile));
            Identifier textureId = new Identifier(MOD_ID, "skin_" + name.toLowerCase());
            client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(image));
            currentSkinTexture = textureId;
            currentSkinName = name;
            skinLoaded = true;
            saveConfig();
            sendFeedback("已切换皮肤: " + name);
        } catch (IOException e) {
            sendFeedback("加载皮肤失败: " + e.getMessage());
        }
    }

    private void loadDefaultSkin(MinecraftClient client, Path folder) {
        Path skinFile = folder.resolve("Skin.png");
        if (!Files.exists(skinFile)) {
            System.out.println("[EhhhSkin] Skin.png not found, using default skin.");
            return;
        }
        try {
            NativeImage image = NativeImage.read(Files.newInputStream(skinFile));
            client.getTextureManager().registerTexture(CUSTOM_SKIN, new NativeImageBackedTexture(image));
            skinLoaded = true;
            currentSkinTexture = CUSTOM_SKIN;
            System.out.println("[EhhhSkin] Default skin loaded.");
        } catch (IOException e) {
            System.err.println("[EhhhSkin] Failed to load default skin: " + e.getMessage());
        }
    }

    // ====================== 在线皮肤获取与保存 ======================
    private void loadOnlineSkin(String playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 获取玩家 UUID
                String uuid = fetchPlayerUUID(playerId);
                if (uuid == null) {
                    client.execute(() -> sendFeedback("未搜索到该id"));
                    return;
                }

                // 2. 获取皮肤 URL（不再获取模型信息）
                String skinUrl = fetchSkinUrl(uuid);
                if (skinUrl == null) {
                    client.execute(() -> sendFeedback("该玩家没有皮肤或获取失败"));
                    return;
                }

                // 3. 下载皮肤图片字节
                byte[] skinBytes = downloadSkin(skinUrl);
                if (skinBytes == null) {
                    client.execute(() -> sendFeedback("皮肤下载失败"));
                    return;
                }

                // 4. 保存到本地文件（可选，失败不影响使用）
                Path skinFolder = getSkinFolder();
                Path saveFile = skinFolder.resolve(playerId + ".png");
                try {
                    Files.write(saveFile, skinBytes);
                    System.out.println("[EhhhSkin] Saved skin to " + saveFile);
                } catch (IOException e) {
                    System.err.println("[EhhhSkin] Failed to save skin file: " + e.getMessage());
                }

                // 5. 在主线程注册纹理并切换（保持模型不变）
                client.execute(() -> {
                    try (InputStream in = new ByteArrayInputStream(skinBytes)) {
                        NativeImage image = NativeImage.read(in);
                        Identifier textureId = new Identifier(MOD_ID, "online_" + playerId.toLowerCase());
                        client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(image));
                        currentSkinTexture = textureId;
                        currentSkinName = playerId; // 设置皮肤名称为玩家ID
                        skinLoaded = true;
                        saveConfig();
                        sendFeedback("皮肤已下载:" + playerId);
                    } catch (IOException e) {
                        sendFeedback("皮肤解析失败: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                client.execute(() -> sendFeedback("获取在线皮肤失败: " + e.getMessage()));
            }
        });
    }

    private String fetchPlayerUUID(String name) throws Exception {
        String url = "https://api.mojang.com/users/profiles/minecraft/" + name;
        String response = httpGet(url);
        if (response == null || response.isEmpty()) return null;
        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        return obj.get("id").getAsString();
    }

    private String fetchSkinUrl(String uuid) throws Exception {
        String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";
        String response = httpGet(url);
        if (response == null || response.isEmpty()) return null;
        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        JsonArray properties = obj.getAsJsonArray("properties");
        for (int i = 0; i < properties.size(); i++) {
            JsonObject prop = properties.get(i).getAsJsonObject();
            if ("textures".equals(prop.get("name").getAsString())) {
                String value = prop.get("value").getAsString();
                String decoded = new String(Base64.getDecoder().decode(value));
                JsonObject texObj = JsonParser.parseString(decoded).getAsJsonObject();
                JsonObject textures = texObj.getAsJsonObject("textures");
                JsonObject skin = textures.getAsJsonObject("SKIN");
                if (skin != null && skin.has("url")) {
                    return skin.get("url").getAsString();
                }
            }
        }
        return null;
    }

    private byte[] downloadSkin(String skinUrl) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(skinUrl))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    private String httpGet(String url) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    // ====================== 模型设置与配置 ======================
    private void setCurrentModel(SkinTextures.Model model, String feedback) {
        currentModel = model;
        saveConfig();
        sendFeedback(feedback);
    }

    private void loadConfig() {
        Path configFile = getSkinFolder().resolve("EhhhskinConfig.properties");
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                Properties props = new Properties();
                props.load(in);
                String model = props.getProperty("model", "slim");
                currentModel = "steve".equalsIgnoreCase(model) ? SkinTextures.Model.WIDE : SkinTextures.Model.SLIM;
                currentSkinName = props.getProperty("skin", null);
            } catch (IOException e) {
                System.err.println("[EhhhSkin] Failed to load config: " + e.getMessage());
            }
        }
    }

    private void saveConfig() {
        Path configFile = getSkinFolder().resolve("EhhhskinConfig.properties");
        try (OutputStream out = Files.newOutputStream(configFile)) {
            Properties props = new Properties();
            props.setProperty("model", currentModel == SkinTextures.Model.WIDE ? "steve" : "alex");
            if (currentSkinName != null) {
                props.setProperty("skin", currentSkinName);
            }
            props.store(out, "EhhhSkin configuration");
        } catch (IOException e) {
            System.err.println("[EhhhSkin] Failed to save config: " + e.getMessage());
        }
    }

    // ====================== 工具方法 ======================
    private void sendFeedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
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

    private void openSkinFolder() {
        Path folder = getSkinFolder();
        try {
            Files.createDirectories(folder);
            Util.getOperatingSystem().open(folder.toFile());
            sendFeedback("已打开皮肤文件夹: " + folder.toAbsolutePath());
        } catch (IOException e) {
            sendFeedback("打开文件夹失败: " + e.getMessage());
        }
    }

    // ====================== 供 Mixin 调用的静态方法 ======================
    public static boolean isSkinLoaded() {
        return skinLoaded;
    }

    public static SkinTextures.Model getCurrentModel() {
        return currentModel;
    }

    public static Identifier getCurrentSkinTexture() {
        return currentSkinTexture;
    }
}
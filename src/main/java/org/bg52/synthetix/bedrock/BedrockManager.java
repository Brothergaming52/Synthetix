package org.bg52.synthetix.bedrock;

import org.bg52.synthetix.SynthetixPlugin;

/**
 * Central orchestrator for Minecraft Bedrock Edition (Geyser/Floodgate) compatibility.
 * Coordinates Bedrock player protocol bridging, block visual proxies,
 * animated entity visual rigs, and Geyser resource pack & mappings compilation.
 */
public class BedrockManager {

    private final SynthetixPlugin plugin;
    private final BedrockProtocolBridge bridge;
    private final BedrockBlockVisualManager blockVisualManager;
    private final BedrockEntityVisualManager entityVisualManager;
    private final GeyserPackGenerator packGenerator;

    public BedrockManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.bridge = new BedrockProtocolBridge(plugin);
        this.blockVisualManager = new BedrockBlockVisualManager(plugin, bridge);
        this.entityVisualManager = new BedrockEntityVisualManager(plugin, bridge);
        this.packGenerator = new GeyserPackGenerator(plugin);
    }

    public void initialize() {
        this.bridge.initialize();
        this.blockVisualManager.initialize();
        this.entityVisualManager.initialize();

        // Run initial asynchronous Bedrock pack build & Geyser deployment
        this.packGenerator.generateAndDeployAsync().thenAccept(file -> {
            plugin.getLogger().info("[BedrockManager] Bedrock/Geyser compatibility pack ready (" + file.getName() + ", " + file.length() + " bytes).");
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[BedrockManager] Initial Geyser pack build encountered an issue: " + ex.getMessage());
            return null;
        });
    }

    public void shutdown() {
        if (blockVisualManager != null) blockVisualManager.shutdown();
        if (entityVisualManager != null) entityVisualManager.shutdown();
        if (bridge != null) bridge.shutdown();
    }

    public BedrockProtocolBridge getBridge() {
        return bridge;
    }

    public BedrockBlockVisualManager getBlockVisualManager() {
        return blockVisualManager;
    }

    public BedrockEntityVisualManager getEntityVisualManager() {
        return entityVisualManager;
    }

    public GeyserPackGenerator getPackGenerator() {
        return packGenerator;
    }
}


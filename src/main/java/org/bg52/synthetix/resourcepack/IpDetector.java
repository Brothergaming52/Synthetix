package org.bg52.synthetix.resourcepack;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Intelligent IP discovery utility for auto-detecting the server's public or reachable IP address.
 */
public class IpDetector {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final String[] IP_SERVICES = {
            "https://checkip.amazonaws.com",
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ipinfo.io/ip"
    };

    private final SynthetixPlugin plugin;
    private volatile String cachedIp = null;
    private volatile long lastCheckTime = 0L;
    private static final long CACHE_DURATION_MS = 60 * 60 * 1000L; // 1 hour

    public IpDetector(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves the effective external IP for resource pack hosting.
     * Respects manual entry if configured, or auto-detects via fast external services.
     */
    public String resolveExternalIp() {
        String configured = plugin.getConfig().getString("resource-pack.server.external-ip", "auto");
        if (configured != null && !configured.trim().isEmpty() && !configured.equalsIgnoreCase("auto")) {
            return configured.trim();
        }

        long now = System.currentTimeMillis();
        if (cachedIp != null && (now - lastCheckTime < CACHE_DURATION_MS)) {
            return cachedIp;
        }

        // 1. Try public IP lookup services
        for (String serviceUrl : IP_SERVICES) {
            String detected = fetchFromService(serviceUrl);
            if (detected != null && isValidIpv4(detected)) {
                this.cachedIp = detected;
                this.lastCheckTime = now;
                plugin.getLogger().info("[PackServer] Auto-detected public server IP: " + detected + " (via " + serviceUrl + ")");
                return detected;
            }
        }

        // 2. Try Bukkit server IP if non-wildcard
        String bukkitIp = Bukkit.getIp();
        if (bukkitIp != null && !bukkitIp.trim().isEmpty()
                && !bukkitIp.equals("0.0.0.0") && !bukkitIp.equals("127.0.0.1")
                && isValidIpv4(bukkitIp.trim())) {
            this.cachedIp = bukkitIp.trim();
            this.lastCheckTime = now;
            plugin.getLogger().info("[PackServer] Using Bukkit server IP: " + cachedIp);
            return cachedIp;
        }

        // 3. Fallback to local network host address
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            if (localIp != null && !localIp.trim().isEmpty() && !localIp.equals("127.0.0.1")) {
                this.cachedIp = localIp.trim();
                this.lastCheckTime = now;
                plugin.getLogger().info("[PackServer] External IP lookup unavailable; using local network IP: " + cachedIp);
                return cachedIp;
            }
        } catch (Throwable ignored) {
        }

        // 4. Default loopback fallback
        this.cachedIp = "127.0.0.1";
        this.lastCheckTime = now;
        plugin.getLogger().warning("[PackServer] Could not detect public IP; falling back to 127.0.0.1 (local clients only).");
        return "127.0.0.1";
    }

    private String fetchFromService(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Synthetix-PackHost/" + plugin.getDescription().getVersion());

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) {
                        return line.trim();
                    }
                }
            }
        } catch (Throwable ignored) {
            // Ignore and proceed to next provider
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static boolean isValidIpv4(String ip) {
        if (ip == null || !IPV4_PATTERN.matcher(ip).matches()) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String p : parts) {
                int val = Integer.parseInt(p);
                if (val < 0 || val > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}


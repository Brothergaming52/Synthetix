package org.bg52.synthetix.set;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Definition of an Equipment Set consisting of multiple items with tier bonuses.
 */
public class ItemSet {

    private final String id;
    private String displayName;
    private List<String> pieces;
    private Map<Integer, SetBonus> bonuses;

    public ItemSet(String id) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "unnamed_set";
        this.displayName = id;
        this.pieces = new ArrayList<>();
        this.bonuses = new TreeMap<>();
    }

    public ItemSet(String id, String displayName) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "unnamed_set";
        this.displayName = displayName != null ? displayName : id;
        this.pieces = new ArrayList<>();
        this.bonuses = new TreeMap<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : id;
    }

    public List<String> getPieces() {
        return pieces;
    }

    public void setPieces(List<String> pieces) {
        this.pieces = pieces != null ? new ArrayList<>(pieces) : new ArrayList<>();
    }

    public void addPiece(String itemId) {
        if (itemId != null && !pieces.contains(itemId.toLowerCase(Locale.ROOT))) {
            pieces.add(itemId.toLowerCase(Locale.ROOT));
        }
    }

    public void removePiece(String itemId) {
        if (itemId != null) {
            pieces.remove(itemId.toLowerCase(Locale.ROOT));
        }
    }

    public boolean containsPiece(String itemId) {
        return itemId != null && pieces.contains(itemId.toLowerCase(Locale.ROOT));
    }

    public Map<Integer, SetBonus> getBonuses() {
        return bonuses;
    }

    public void setBonuses(Map<Integer, SetBonus> bonuses) {
        this.bonuses = bonuses != null ? new TreeMap<>(bonuses) : new TreeMap<>();
    }

    public void addBonus(int pieceCount, SetBonus bonus) {
        if (bonus != null) {
            bonus.setPieceCount(pieceCount);
            this.bonuses.put(pieceCount, bonus);
        }
    }

    public void removeBonus(int pieceCount) {
        this.bonuses.remove(pieceCount);
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("id", id);
        sec.set("display-name", displayName);
        sec.set("pieces", pieces);

        if (bonuses != null && !bonuses.isEmpty()) {
            ConfigurationSection bonusSec = sec.createSection("bonuses");
            for (Map.Entry<Integer, SetBonus> entry : bonuses.entrySet()) {
                ConfigurationSection bEntry = bonusSec.createSection("tier_" + entry.getKey());
                entry.getValue().saveToConfig(bEntry);
            }
        }
    }

    public static ItemSet loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String id = sec.getString("id");
        if (id == null) id = sec.getName();

        ItemSet set = new ItemSet(id, sec.getString("display-name", id));
        set.setPieces(sec.getStringList("pieces"));

        ConfigurationSection bonusSec = sec.getConfigurationSection("bonuses");
        if (bonusSec != null) {
            Map<Integer, SetBonus> map = new TreeMap<>();
            for (String key : bonusSec.getKeys(false)) {
                ConfigurationSection bEntry = bonusSec.getConfigurationSection(key);
                if (bEntry != null) {
                    SetBonus bonus = SetBonus.loadFromConfig(bEntry);
                    if (bonus != null) {
                        map.put(bonus.getPieceCount(), bonus);
                    }
                }
            }
            set.setBonuses(map);
        }

        return set;
    }

    public ItemSet copy(String newId) {
        ItemSet copy = new ItemSet(newId, this.displayName);
        copy.setPieces(new ArrayList<>(this.pieces));
        Map<Integer, SetBonus> bCopies = new TreeMap<>();
        for (Map.Entry<Integer, SetBonus> entry : this.bonuses.entrySet()) {
            bCopies.put(entry.getKey(), entry.getValue().copy());
        }
        copy.setBonuses(bCopies);
        return copy;
    }
}


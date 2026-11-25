package org.bg52.synthetix.api.editor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing all built-in and external plugin ItemEditorCategory extensions.
 */
public class ItemEditorRegistry {

    private final Map<String, ItemEditorCategory> categories = new ConcurrentHashMap<>();

    /**
     * Registers a category into the item editor.
     */
    public void registerCategory(ItemEditorCategory category) {
        if (category != null && category.getId() != null) {
            categories.put(category.getId().toLowerCase(Locale.ROOT), category);
        }
    }

    /**
     * Unregisters a category by ID.
     */
    public void unregisterCategory(String id) {
        if (id != null) {
            categories.remove(id.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Returns all core Synthetix categories, sorted by priority.
     */
    public List<ItemEditorCategory> getCoreCategories() {
        return categories.values().stream()
                .filter(c -> !c.isPluginCategory())
                .sorted(Comparator.comparingInt(ItemEditorCategory::getPriority))
                .toList();
    }

    /**
     * Returns all external plugin categories, sorted by priority.
     */
    public List<ItemEditorCategory> getPluginCategories() {
        return categories.values().stream()
                .filter(ItemEditorCategory::isPluginCategory)
                .sorted(Comparator.comparingInt(ItemEditorCategory::getPriority))
                .toList();
    }

    public Collection<ItemEditorCategory> getAllCategories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    public ItemEditorCategory getCategory(String id) {
        return id != null ? categories.get(id.toLowerCase(Locale.ROOT)) : null;
    }
}


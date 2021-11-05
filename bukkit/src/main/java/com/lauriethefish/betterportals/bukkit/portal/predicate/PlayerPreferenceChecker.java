package com.lauriethefish.betterportals.bukkit.portal.predicate;

import com.lauriethefish.betterportals.api.BetterPortal;
import com.lauriethefish.betterportals.api.PortalPredicate;
import com.lauriethefish.betterportals.bukkit.player.IPlayerData;
import com.lauriethefish.betterportals.bukkit.player.IPlayerDataManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * At the moment, this is only meant to check if a player has seeThroughPortal enabled.
 * It could be expanded if other things are added.
 */
public class PlayerPreferenceChecker implements PortalPredicate {

    private final IPlayerDataManager playerDataManager;
    private final String preference;

    public PlayerPreferenceChecker(IPlayerDataManager playerDataManager, String preference) {
        this.preference = preference;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public boolean test(@NotNull BetterPortal portal, @NotNull Player player) {
        IPlayerData playerData = playerDataManager.getPlayerData(player);

        assert playerData != null;
        YamlConfiguration permanentData = playerData.getPermanentData();

        return permanentData.getBoolean(preference);
    }
}

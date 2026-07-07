package net.blueva.arcade.modules.minefield.game;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.visuals.VisualEffectsAPI;
import net.blueva.arcade.modules.minefield.state.MinefieldArenaState;
import net.blueva.arcade.modules.minefield.support.MinefieldGameTimerService;
import net.blueva.arcade.modules.minefield.support.MinefieldLoadoutService;
import net.blueva.arcade.modules.minefield.support.MinefieldMessageService;
import net.blueva.arcade.modules.minefield.support.MinefieldMineService;
import net.blueva.arcade.modules.minefield.support.MinefieldScoreboardService;
import net.blueva.arcade.modules.minefield.support.MinefieldStatsService;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinefieldGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final MinefieldStatsService statsService;
    private final MinefieldMessageService messageService;
    private final MinefieldLoadoutService loadoutService;
    private final MinefieldMineService mineService;
    private final MinefieldScoreboardService scoreboardService;
    private final MinefieldGameTimerService timerService;
    private final MinefieldArenaRegistry arenaRegistry;

    public MinefieldGameManager(ModuleInfo moduleInfo,
                                ModuleConfigAPI moduleConfig,
                                CoreConfigAPI coreConfig,
                                MinefieldStatsService statsService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.messageService = new MinefieldMessageService(moduleConfig, coreConfig);
        this.loadoutService = new MinefieldLoadoutService(moduleConfig);
        this.mineService = new MinefieldMineService(moduleConfig, messageService, statsService);
        this.scoreboardService = new MinefieldScoreboardService();
        this.timerService = new MinefieldGameTimerService(coreConfig, scoreboardService, mineService);
        this.arenaRegistry = new MinefieldArenaRegistry();
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        MinefieldArenaState state = arenaRegistry.createArenaState(context);
        mineService.clearMines(state);

        messageService.sendDescription(context);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage(player, "titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage(player, "titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            String title = coreConfig.getLanguage(player, "titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage(player, "titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        MinefieldArenaState state = arenaRegistry.getArenaState(context.getArenaId());
        if (state == null) {
            return;
        }

        timerService.startGameTimer(context, state, () -> endGameOnce(context, state));
        mineService.placeMines(context, state);

        for (Player player : context.getPlayers()) {
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            context.getScoreboardAPI().showModuleScoreboard(player);
        }
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             MinefieldArenaState state) {
        if (!state.markEnded()) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());
        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        MinefieldArenaState state = arenaRegistry.getArenaState(arenaId);
        if (state == null) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        mineService.clearMines(state);

        arenaRegistry.removeArenaState(arenaId);

        if (statsService.isEnabled()) {
            statsService.recordGamesPlayed(context.getPlayers());
        }

        for (Player player : context.getPlayers()) {
            mineService.clearImmunity(player.getUniqueId());
        }

        arenaRegistry.removePlayersForArena(arenaId);
    }

    public void handleDisable() {
        for (MinefieldArenaState state : arenaRegistry.getArenaStates()) {
            state.getContext().getSchedulerAPI().cancelModuleTasks(moduleInfo.getId());
        }

        for (MinefieldArenaState state : arenaRegistry.getArenaStates()) {
            mineService.clearMines(state);
        }

        arenaRegistry.clear();
        mineService.clearAll();
    }

    public void handlePlayerFinish(Player player) {
        Integer arenaId = arenaRegistry.getArenaId(player);
        if (arenaId == null) {
            return;
        }

        statsService.recordFinishLine(player);

        MinefieldArenaState state = arenaRegistry.getArenaState(arenaId);
        if (state == null) {
            return;
        }

        if (state.getWinner() == null) {
            state.setWinner(player.getUniqueId());
            statsService.recordWin(player);
        }
    }

    public void handlePlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  Player player,
                                  boolean deathBlock) {
        // Don't broadcast death messages for spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        messageService.broadcastDeathMessage(context, player, deathBlock);
    }

    public void handlePlayerRespawn(Player player) {
        loadoutService.applyRespawnEffects(player);
    }

    public void handleMineTrigger(Player player,
                                  GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  org.bukkit.Location plateLocation) {
        MinefieldArenaState state = arenaRegistry.getArenaState(context.getArenaId());
        if (state == null) {
            return;
        }

        mineService.handleMineTrigger(player, context, plateLocation);
    }

    public void removeMineLocation(int arenaId, org.bukkit.Location location) {
        MinefieldArenaState state = arenaRegistry.getArenaState(arenaId);
        if (state == null) {
            return;
        }

        mineService.removeMineLocation(state, location);
    }

    public boolean isMineMaterial(Material material) {
        return mineService.isMineMaterial(material);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = arenaRegistry.getArenaId(player);
        if (arenaId == null) {
            return null;
        }
        MinefieldArenaState state = arenaRegistry.getArenaState(arenaId);
        return state != null ? state.getContext() : null;
    }

    public void handleFinishLineCross(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      Player player) {
        if (context.getSpectators().contains(player)) {
            return;
        }

        context.finishPlayer(player);
        int position = context.getSpectators().indexOf(player) + 1;

        handlePlayerFinish(player);
        messageService.broadcastFinish(context, player, position);

        String title = moduleConfig.getTranslation(player, "titles.finished.title");
        String subtitle = moduleConfig.getTranslation(player, "titles.finished.subtitle")
                .replace("{position}", String.valueOf(position));

        context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 80, 20);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.classified"));
    }

    public void handlePlayerOutOfBounds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        Player player,
                                        boolean deathBlock) {
        // Don't process death for spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        Location deathLocation = player.getLocation();
        context.respawnPlayer(player);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
        playDeathEffect(player, deathLocation);
        handlePlayerDeath(context, player, deathBlock);
        handlePlayerRespawn(player);
    }

    public void playDeathEffect(Player player, Location location) {
        VisualEffectsAPI visualEffectsAPI = ModuleAPI.getVisualEffectsAPI();
        if (visualEffectsAPI == null || player == null) {
            return;
        }
        visualEffectsAPI.playDeathEffect(player, location != null ? location : player.getLocation());
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return new HashMap<>();
        }

        MinefieldArenaState state = arenaRegistry.getArenaState(context.getArenaId());
        Map<String, String> placeholders = new HashMap<>(scoreboardService.getCustomPlaceholders(context, player));
        if (state != null) {
            placeholders.put("mines_active", String.valueOf(mineService.getActiveMineCount(state)));
        }

        return placeholders;
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}

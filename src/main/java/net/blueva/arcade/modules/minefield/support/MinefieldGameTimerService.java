package net.blueva.arcade.modules.minefield.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.minefield.state.MinefieldArenaState;
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

public class MinefieldGameTimerService {

    private final CoreConfigAPI coreConfig;
    private final MinefieldScoreboardService scoreboardService;
    private final MinefieldMineService mineService;

    public MinefieldGameTimerService(CoreConfigAPI coreConfig,
                                     MinefieldScoreboardService scoreboardService,
                                     MinefieldMineService mineService) {
        this.coreConfig = coreConfig;
        this.scoreboardService = scoreboardService;
        this.mineService = mineService;
    }

    public void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               MinefieldArenaState state,
                               Runnable endGameCallback) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 60;
        }

        final int[] timeLeft = {gameTime};
        final int[] tickCount = {0};

        String taskId = "arena_" + arenaId + "_minefield_timer";
        state.setTimerTaskId(taskId);

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                state.setTimerTaskId(null);
                return;
            }

            tickCount[0]++;

            if (tickCount[0] % 2 == 0) {
                timeLeft[0]--;
            }

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();
            List<Player> spectators = context.getSpectators();

            if (allPlayers.size() < 2 || alivePlayers.isEmpty() || spectators.size() >= 3 || timeLeft[0] <= 0) {
                endGameCallback.run();
                return;
            }

            updateHud(context, state, allPlayers, timeLeft[0]);
        }, 0L, 10L);
    }

    private void updateHud(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           MinefieldArenaState state,
                           List<Player> allPlayers,
                           int timeLeft) {
        List<Player> topPlayers = scoreboardService.getTopPlayersByDistance(context);

        for (Player player : allPlayers) {
            if (!player.isOnline()) {
                continue;
            }

            String actionBarTemplate = coreConfig.getLanguage(player, "action_bar.in_game.global");
            String actionBarMessage = formatActionBar(actionBarTemplate, context, timeLeft);
            context.getMessagesAPI().sendActionBar(player, actionBarMessage);

            Map<String, String> customPlaceholders = new HashMap<>(scoreboardService.getCustomPlaceholders(context, player));
            customPlaceholders.put("time", formatCountdownTime(timeLeft));
            customPlaceholders.put("round", String.valueOf(context.getCurrentRound()));
            customPlaceholders.put("round_max", String.valueOf(context.getMaxRounds()));

            customPlaceholders.put("distance_1", topPlayers.size() >= 1 ? scoreboardService.formatDistance(context, topPlayers.get(0)) : "-");
            customPlaceholders.put("distance_2", topPlayers.size() >= 2 ? scoreboardService.formatDistance(context, topPlayers.get(1)) : "-");
            customPlaceholders.put("distance_3", topPlayers.size() >= 3 ? scoreboardService.formatDistance(context, topPlayers.get(2)) : "-");
            customPlaceholders.put("distance_4", topPlayers.size() >= 4 ? scoreboardService.formatDistance(context, topPlayers.get(3)) : "-");
            customPlaceholders.put("distance_5", topPlayers.size() >= 5 ? scoreboardService.formatDistance(context, topPlayers.get(4)) : "-");
            customPlaceholders.put("mines_active", String.valueOf(mineService.getActiveMineCount(state)));

            context.getScoreboardAPI().update(player, customPlaceholders);
        }
    }

    private String formatActionBar(String template,
                                   GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                   int timeLeft) {
        if (template == null) {
            return "";
        }

        return template
                .replace("{time}", formatCountdownTime(timeLeft))
                .replace("{round}", String.valueOf(context.getCurrentRound()))
                .replace("{round_max}", String.valueOf(context.getMaxRounds()));
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}

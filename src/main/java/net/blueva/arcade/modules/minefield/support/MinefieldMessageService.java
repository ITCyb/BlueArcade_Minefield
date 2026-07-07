package net.blueva.arcade.modules.minefield.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MinefieldMessageService {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;

    public MinefieldMessageService(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
    }

    public void sendDescription(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            List<String> description = moduleConfig.getTranslationList(player, "description");
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void broadcastDeathMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      Player player,
                                      boolean deathBlock) {
        String path = deathBlock ? "messages.deaths.death_block" : "messages.deaths.void";
        String message = getRandomMessage(path);
        if (message == null) {
            return;
        }

        message = message.replace("{player}", player.getName());
        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    public void broadcastFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                Player player,
                                int position) {
        String message = getRandomMessage("messages.finish.crossed");
        if (message == null) {
            return;
        }

        message = message
                .replace("{player}", player.getName())
                .replace("{position}", String.valueOf(position));

        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    public String getMineTriggeredMessage() {
        return getRandomMessage("messages.mines.triggered");
    }

    public String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getTranslationList(null, path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }

    public String getMustBePlayerMessage() {
        return coreConfig.getLanguage(null, "admin_commands.errors.must_be_player");
    }

    public String getUnknownSubcommandMessage() {
        return coreConfig.getLanguage(null, "admin_commands.errors.unknown_subcommand");
    }
}

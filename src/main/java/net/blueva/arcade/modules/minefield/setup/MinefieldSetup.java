package net.blueva.arcade.modules.minefield.setup;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.setup.GameSetupHandler;
import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.api.setup.SetupDataAPI;
import net.blueva.arcade.api.setup.TabCompleteContext;
import net.blueva.arcade.api.setup.TabCompleteResult;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class MinefieldSetup implements GameSetupHandler {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;

    public MinefieldSetup(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
    }

    @Override
    public boolean handle(SetupContext context) {
        return handleInternal(castSetupContext(context));
    }

    private boolean handleInternal(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.usage"));
            return true;
        }

        String subcommand = context.getArg(context.getStartIndex() - 1).toLowerCase();

        switch (subcommand) {
            case "finishline":
                return handleFinishLine(context);
            case "floor":
                return handleFloor(context);
            default:
                context.getMessagesAPI().sendRaw(context.getPlayer(), coreConfig.getLanguage(context.getPlayer(), "admin_commands.errors.unknown_subcommand"));
                return true;
        }
    }

    @Override
    public TabCompleteResult tabComplete(TabCompleteContext context) {
        return tabCompleteInternal(castTabContext(context));
    }

    private TabCompleteResult tabCompleteInternal(TabCompleteContext<Player, CommandSender> context) {
        int relIndex = context.getRelativeArgIndex();

        if (relIndex == 0) {
            String subcommand = context.getArg(context.getStartIndex() - 1);
            if ("finishline".equals(subcommand) || "floor".equals(subcommand)) {
                return TabCompleteResult.of("set");
            }
        }

        return TabCompleteResult.empty();
    }

    @Override
    public List<String> getSubcommands() {
        return Arrays.asList("finishline", "floor");
    }

    @Override
    public boolean validateConfig(SetupContext context) {
        return validateConfigInternal(castSetupContext(context));
    }

    private boolean validateConfigInternal(SetupContext<Player, CommandSender, Location> context) {
        SetupDataAPI data = context.getData();

        boolean hasFinishLine = data.has("game.finish_line.bounds.min.x") &&
                data.has("game.finish_line.bounds.max.x");
        boolean hasFloor = data.has("game.floor.bounds.min.x") &&
                data.has("game.floor.bounds.max.x");

        if (!hasFinishLine || !hasFloor) {
            if (context.getSender() != null) {
                context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.not_configured")
                        .replace("{arena_id}", String.valueOf(context.getArenaId())));
            }
        }

        return hasFinishLine && hasFloor;
    }

    private boolean handleFinishLine(SetupContext<Player, CommandSender, Location> context) {
        if (!context.isPlayer()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), coreConfig.getLanguage(context.getPlayer(), "admin_commands.errors.must_be_player"));
            return true;
        }

        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.usage_finish_line"));
            return true;
        }

        String action = context.getHandlerArg(0).toLowerCase();

        if (!action.equals("set")) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.usage_finish_line"));
            return true;
        }

        Player player = context.getPlayer();

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        context.getData().setRegionBounds("game.finish_line.bounds", pos1, pos2);
        context.getData().save();

        int x = (int) Math.abs(pos2.getX() - pos1.getX()) + 1;
        int y = (int) Math.abs(pos2.getY() - pos1.getY()) + 1;
        int z = (int) Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        int blocks = x * y * z;

        context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.set_success")
                .replace("{blocks}", String.valueOf(blocks))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z)));

        return true;
    }

    private boolean handleFloor(SetupContext<Player, CommandSender, Location> context) {
        if (!context.isPlayer()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), coreConfig.getLanguage(context.getPlayer(), "admin_commands.errors.must_be_player"));
            return true;
        }

        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.usage_floor"));
            return true;
        }

        String action = context.getHandlerArg(0).toLowerCase();
        if (!action.equals("set")) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.usage_floor"));
            return true;
        }

        Player player = context.getPlayer();

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        context.getData().setRegionBounds("game.floor.bounds", pos1, pos2);
        context.getData().save();

        int x = (int) Math.abs(pos2.getX() - pos1.getX()) + 1;
        int y = (int) Math.abs(pos2.getY() - pos1.getY()) + 1;
        int z = (int) Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        int blocks = x * y * z;

        context.getMessagesAPI().sendRaw(context.getPlayer(), moduleConfig.getTranslation(context.getPlayer(), "setup_messages.set_success_floor")
                .replace("{blocks}", String.valueOf(blocks))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z)));

        return true;
    }


    @SuppressWarnings("unchecked")
    private SetupContext<Player, CommandSender, Location> castSetupContext(SetupContext context) {
        return (SetupContext<Player, CommandSender, Location>) context;
    }

    @SuppressWarnings("unchecked")
    private TabCompleteContext<Player, CommandSender> castTabContext(TabCompleteContext context) {
        return (TabCompleteContext<Player, CommandSender>) context;
    }
}

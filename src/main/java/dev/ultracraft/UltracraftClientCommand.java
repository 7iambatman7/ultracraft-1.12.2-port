package dev.ultracraft;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/** Client-side /uc command adds /uc settings while forwarding existing commands to the server implementation. */
public final class UltracraftClientCommand extends CommandBase {
    @Override public String getName() { return "uc"; }
    @Override public String getUsage(ICommandSender sender) { return "/uc settings | help | status | cheats | cheat ..."; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 0 && "settings".equalsIgnoreCase(args[0])) {
            if (args.length > 1) throw new WrongUsageException("Usage: /uc settings");
            Minecraft.getMinecraft().displayGuiScreen(new UltracraftSettingsScreen());
            return;
        }
        if (Minecraft.getMinecraft().player != null) {
            String rest = args.length == 0 ? "help" : join(args);
            Minecraft.getMinecraft().player.sendChatMessage("/uc " + rest);
        }
    }

    private static String join(String[] args) { return String.join(" ", args); }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length <= 1) return getListOfStringsMatchingLastWord(args, "settings", "help", "status", "cheats", "cheat");
        if (args.length == 2 && "cheat".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "neverhungry", "superspeed");
        if (args.length == 3 && "cheat".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
        return java.util.Collections.emptyList();
    }
}

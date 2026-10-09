package dev.ultracraft;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** Minimal 1.12.2 command entry point for the supported Forge-side controls. */
public final class UltracraftCommand extends CommandBase {
	@Override
	public String getName() {
		return "uc";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/uc help | status | cheats | cheat <neverhungry|superspeed> [on|off|toggle]";
	}

	@Override
	public int getRequiredPermissionLevel() {
		// Matches the upstream rule: use cheat controls in your own world, or as an operator on someone else's.
		return 2;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
			message(sender, "Ultracraft (Forge 1.12.2) commands:");
			message(sender, "/uc status - show link and mode status");
			message(sender, "/uc cheats - show supported non-weapon cheats");
			message(sender, "/uc cheat neverhungry [on|off|toggle]");
			message(sender, "/uc cheat superspeed [on|off|toggle]");
			message(sender, "These two settings apply to the Minecraft side; V1 movement is controlled by ULTRAKILL.");
			return;
		}

		if ("status".equalsIgnoreCase(args[0])) {
			message(sender, "ULTRAKILL link: " + (UkLink.connected ? (UkLink.ready ? "ready" : "connecting/loading") : "not connected"));
			message(sender, "Client mode: V1 state is per player; server cheats are stored in config/ultracraft.properties.");
			return;
		}

		if ("cheats".equalsIgnoreCase(args[0])) {
			message(sender, "neverhungry=" + onOff(UltracraftConfig.cheatNeverHungry)
				+ " (world-wide; refills food and stops V1 movement exhaustion)");
			message(sender, "superspeed=" + onOff(UltracraftConfig.cheatSuperSpeed)
				+ " (Minecraft/Steve movement only; does not alter ULTRAKILL's V1 simulation)");
			message(sender, "ULTRAKILL-side settings and combat-related cheats are not implemented by this Forge increment.");
			return;
		}

		if ("cheat".equalsIgnoreCase(args[0])) {
			if (args.length < 2 || args.length > 3) throw new WrongUsageException(getUsage(sender));
			String key = args[1].toLowerCase(java.util.Locale.ROOT);
			boolean current;
			if ("neverhungry".equals(key)) current = UltracraftConfig.cheatNeverHungry;
			else if ("superspeed".equals(key)) current = UltracraftConfig.cheatSuperSpeed;
			else throw new WrongUsageException("Supported cheats: neverhungry, superspeed");

			if (args.length == 2) {
				message(sender, key + " is " + onOff(current));
				return;
			}
			String value = args[2].toLowerCase(java.util.Locale.ROOT);
			boolean next;
			if ("on".equals(value) || "true".equals(value)) next = true;
			else if ("off".equals(value) || "false".equals(value)) next = false;
			else if ("toggle".equals(value)) next = !current;
			else throw new WrongUsageException("Use on, off, or toggle");

			if ("neverhungry".equals(key)) UltracraftConfig.cheatNeverHungry = next;
			else UltracraftConfig.cheatSuperSpeed = next;
			UltracraftConfig.save();
			message(sender, key + " set to " + onOff(next) + ". Saved to config/ultracraft.properties.");
			if ("superspeed".equals(key)) message(sender, "This affects Minecraft/Steve movement only, not ULTRAKILL's V1 movement.");
			return;
		}

		throw new WrongUsageException(getUsage(sender));
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) return getListOfStringsMatchingLastWord(args, "help", "status", "cheats", "cheat");
		if (args.length == 2 && "cheat".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "neverhungry", "superspeed");
		if (args.length == 3 && "cheat".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "on", "off", "toggle");
		return java.util.Collections.emptyList();
	}

	private static void message(ICommandSender sender, String text) {
		sender.sendMessage(new TextComponentString("[Ultracraft] " + text));
	}

	private static String onOff(boolean value) {
		return value ? "ON" : "OFF";
	}
}

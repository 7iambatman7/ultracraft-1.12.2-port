package dev.ultracraft;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** One line of Ultracraft's protocol, client to server: what ULTRAKILL did to the shared world (hits, blasts, damage). */
public class UkMessage implements IMessage {
	public String text = "";

	public UkMessage() {}

	public UkMessage(String text) {
		this.text = text;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		text = ByteBufUtils.readUTF8String(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		ByteBufUtils.writeUTF8String(buf, text);
	}
}

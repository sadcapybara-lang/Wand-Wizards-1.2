package com.soulstealer.client;

import com.soulstealer.NamePackets;
import com.soulstealer.Network;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Small menu of the Name Changer. */
@OnlyIn(Dist.CLIENT)
public class NameChangerScreen extends Screen {
    private EditBox nameBox;

    public NameChangerScreen() {
        super(Component.literal("Name Changer"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 40;
        this.nameBox = new EditBox(this.font, cx - 100, top + 30, 200, 20, Component.literal("Name"));
        this.nameBox.setMaxLength(16);
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("Change"), b -> submit(this.nameBox.getValue()))
                .bounds(cx - 100, top + 58, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Real name"), b -> submit(""))
                .bounds(cx + 2, top + 58, 98, 20).build());
        this.setInitialFocus(this.nameBox);
    }

    private void submit(String name) {
        Network.CHANNEL.sendToServer(new NamePackets.Change(name.trim()));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit(this.nameBox.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int top = this.height / 2 - 40;
        graphics.drawCenteredString(this.font, this.title, cx, top + 6, 0xFFFFFF);
        graphics.drawString(this.font, "New name (letters, numbers, _ )", cx - 100, top + 19, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

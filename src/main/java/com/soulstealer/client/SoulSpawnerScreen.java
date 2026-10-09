package com.soulstealer.client;

import com.soulstealer.Network;
import com.soulstealer.SpawnFakePlayerPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** The menu of the Soul Spawner: name, skin and the fake join message. */
@OnlyIn(Dist.CLIENT)
public class SoulSpawnerScreen extends Screen {
    // remembered between uses, so spawning several dummies is quick
    private static String lastName = "";
    private static String lastSkin = "";
    private static boolean lastJoinMessage = true;

    private EditBox nameBox;
    private EditBox skinBox;
    private Button joinButton;
    private boolean joinMessage = lastJoinMessage;

    public SoulSpawnerScreen() {
        super(Component.literal("Soul Spawner"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 76;

        this.nameBox = new EditBox(this.font, cx - 100, top + 34, 200, 20, Component.literal("Name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(lastName);
        this.addRenderableWidget(this.nameBox);

        this.skinBox = new EditBox(this.font, cx - 100, top + 78, 200, 20, Component.literal("Skin"));
        this.skinBox.setMaxLength(16);
        this.skinBox.setValue(lastSkin);
        this.addRenderableWidget(this.skinBox);

        this.joinButton = Button.builder(joinText(), button -> {
            this.joinMessage = !this.joinMessage;
            button.setMessage(joinText());
        }).bounds(cx - 100, top + 106, 200, 20).build();
        this.addRenderableWidget(this.joinButton);

        this.addRenderableWidget(Button.builder(Component.literal("Spawn"), button -> submit())
                .bounds(cx - 100, top + 134, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(cx + 2, top + 134, 98, 20).build());

        this.setInitialFocus(this.nameBox);
    }

    private Component joinText() {
        return Component.literal("Fake join message: " + (this.joinMessage ? "ON" : "OFF"));
    }

    private void submit() {
        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) {
            return; // a name is needed
        }
        String skin = this.skinBox.getValue().trim();

        lastName = name;
        lastSkin = skin;
        lastJoinMessage = this.joinMessage;

        Network.CHANNEL.sendToServer(new SpawnFakePlayerPacket(name, skin, this.joinMessage));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
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
        int top = this.height / 2 - 76;

        graphics.drawCenteredString(this.font, this.title, cx, top + 6, 0xFFFFFF);
        graphics.drawString(this.font, "Name (shown above its head)", cx - 100, top + 23, 0xA0A0A0);
        graphics.drawString(this.font, "Skin of player (empty = same as name)", cx - 100, top + 67, 0xA0A0A0);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

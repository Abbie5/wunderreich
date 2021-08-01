/**
 * This class is adapted from "Easy Villagers"
 */
package de.ambertation.wunderreich.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.ambertation.wunderreich.Wunderreich;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;

public class CycleTradesButton extends Button {
	
	private static final ResourceLocation ARROW_BUTTON = new ResourceLocation(Wunderreich.MOD_ID, "textures/gui/reroll.png");
	
	public static final int WIDTH = 18;
	public static final int HEIGHT = 10;
	
	private MerchantScreen screen;
	
	public CycleTradesButton(int x, int y, OnPress pressable, MerchantScreen screen) {
		super(x, y, WIDTH, HEIGHT, TextComponent.EMPTY, pressable);
		this.screen = screen;
	}
	
	@Override
	public void render(PoseStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		visible = screen.getMenu().showProgressBar() && screen.getMenu().getTraderXp() <= 0;
		super.render(matrixStack, mouseX, mouseY, partialTicks);
	}
	
	@Override
	public void renderButton(PoseStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
		RenderSystem.setShaderTexture(0, BookViewScreen.BOOK_LOCATION);
		if (isHovered()) {
			blit(matrixStack, x, y, 26, 207, WIDTH, HEIGHT, 256, 256);
			screen.renderTooltip(matrixStack, Collections.singletonList(new TranslatableComponent("tooltip.wunderreich.cycle_trades").getVisualOrderText()), mouseX, mouseY);
		} else {
			blit(matrixStack, x, y, 3, 207, WIDTH, HEIGHT, 256, 256);
		}
	}
}

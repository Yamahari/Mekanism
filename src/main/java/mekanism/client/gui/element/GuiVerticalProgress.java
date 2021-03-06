package mekanism.client.gui.element;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiProgress.IProgressInfoHandler;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.util.ResourceLocation;

public class GuiVerticalProgress extends GuiTexturedElement {

    private final IProgressInfoHandler handler;

    public GuiVerticalProgress(IGuiWrapper gui, IProgressInfoHandler handler, ResourceLocation def, int x, int y) {
        super(MekanismUtils.getResource(ResourceType.GUI_ELEMENT, "vertical_progress.png"), gui, def, x, y, 8, 20);
        this.handler = handler;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        minecraft.textureManager.bindTexture(getResource());
        if (handler.isActive()) {
            guiObj.drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, 16, 20);
            int displayInt = (int) (handler.getProgress() * 20);
            guiObj.drawModalRectWithCustomSizedTexture(x, y, 8, 0, width, displayInt, 16, 20);
        }
        minecraft.textureManager.bindTexture(defaultLocation);
    }
}
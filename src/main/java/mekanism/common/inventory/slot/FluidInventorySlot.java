package mekanism.common.inventory.slot;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.annotations.FieldsAreNonnullByDefault;
import mekanism.api.annotations.NonNull;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.base.LazyOptionalHelper;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.util.FluidContainerUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;

@FieldsAreNonnullByDefault
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidInventorySlot extends BasicInventorySlot {

    private static final Predicate<@NonNull ItemStack> isFluidContainer = FluidContainerUtils::isFluidContainer;

    //TODO: Rename this maybe? It is basically used as an "input" slot where it accepts either an empty container to try and take stuff
    // OR accepts a fluid container tha that has contents that match the handler for purposes of filling the handler

    /**
     * Fills/Drains the tank depending on if this item has any contents in it
     */
    public static FluidInventorySlot input(IFluidHandler fluidHandler, Predicate<@NonNull FluidStack> validInput, @Nullable IMekanismInventory inventory, int x, int y) {
        Objects.requireNonNull(fluidHandler, "Fluid handler cannot be null");
        Objects.requireNonNull(validInput, "Input validity check cannot be null");
        return new FluidInventorySlot(fluidHandler, alwaysFalse, stack -> {
            FluidStack fluidContained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            if (fluidContained.isEmpty()) {
                //We want to try and drain the tank
                return true;
            }
            //True if the items contents are valid and we can fill the tank with any of our contents
            return validInput.test(fluidContained) && fluidHandler.fill(fluidContained, FluidAction.SIMULATE) > 0;
        }, isFluidContainer, inventory, x, y);
    }

    /**
     * Fills/Drains the tank depending on if this item has any contents in it AND if the supplied boolean's mode supports it
     */
    public static FluidInventorySlot rotary(IFluidHandler fluidHandler, Predicate<@NonNull FluidStack> validInput, BooleanSupplier modeSupplier,
          @Nullable IMekanismInventory inventory, int x, int y) {
        Objects.requireNonNull(fluidHandler, "Fluid handler cannot be null");
        Objects.requireNonNull(validInput, "Input validity check cannot be null");
        Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null");
        return new FluidInventorySlot(fluidHandler, alwaysFalse, stack -> {
            FluidStack fluidContained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            boolean mode = modeSupplier.getAsBoolean();
            //Mode == true if fluid to gas
            if (fluidContained.isEmpty()) {
                //We want to try and drain the tank AND we are not the input tank
                return !mode;
            }
            //True if we are the input tank and the items contents are valid and can fill the tank with any of our contents
            return mode && validInput.test(fluidContained) && fluidHandler.fill(fluidContained, FluidAction.SIMULATE) > 0;
        }, stack -> {
            LazyOptionalHelper<IFluidHandlerItem> capabilityHelper = new LazyOptionalHelper<>(stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY));
            if (capabilityHelper.isPresent()) {
                if (modeSupplier.getAsBoolean()) {
                    //Input tank, so we want to fill it
                    FluidStack fluidContained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
                    return !fluidContained.isEmpty() && validInput.test(fluidContained);
                }
                //Output tank, so we want to drain
                return isNonFullFluidContainer(capabilityHelper);
            }
            return false;
        }, inventory, x, y);
    }

    /**
     * Fills the tank from this item
     */
    public static FluidInventorySlot fill(IFluidHandler fluidHandler, Predicate<@NonNull FluidStack> validFluid, @Nullable IMekanismInventory inventory, int x, int y) {
        Objects.requireNonNull(fluidHandler, "Fluid handler cannot be null");
        Objects.requireNonNull(validFluid, "Fluid validity check cannot be null");
        return new FluidInventorySlot(fluidHandler, alwaysFalse, stack -> {
            FluidStack fluidContained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            //True if we can fill the tank with any of our contents, ignored if the item has no fluid, as it won't pass isValid
            return fluidHandler.fill(fluidContained, FluidAction.SIMULATE) > 0;
        }, stack -> {
            if (!FluidContainerUtils.isFluidContainer(stack)) {
                return false;
            }
            FluidStack fluidContained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            return !fluidContained.isEmpty() && validFluid.test(fluidContained);
        }, inventory, x, y);
    }

    /**
     * Accepts any items that can be filled with the current contents of the fluid tank, or if it is a fluid container and the tank is currently empty
     *
     * Drains the tank into this item.
     */
    public static FluidInventorySlot drain(FluidTank fluidTank, @Nullable IMekanismInventory inventory, int x, int y) {
        //TODO: Accept a fluid handler in general?
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return new FluidInventorySlot(fluidTank, alwaysFalse, stack -> new LazyOptionalHelper<>(FluidUtil.getFluidHandler(stack))
              .matches(itemFluidHandler -> fluidTank.isEmpty() || itemFluidHandler.fill(fluidTank.getFluid(), FluidAction.SIMULATE) > 0),
              stack -> isNonFullFluidContainer(new LazyOptionalHelper<>(stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY))), inventory, x, y);
    }

    //TODO: Should we make this also have the fluid type have to match a desired type???
    private static boolean isNonFullFluidContainer(LazyOptionalHelper<IFluidHandlerItem> capabilityHelper) {
        return capabilityHelper.getIfPresentElse(fluidHandler -> {
            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                if (fluidHandler.getFluidInTank(tank).getAmount() < fluidHandler.getTankCapacity(tank)) {
                    return true;
                }
            }
            return false;
        }, false);
    }

    private final IFluidHandler fluidHandler;

    private FluidInventorySlot(IFluidHandler fluidHandler, Predicate<@NonNull ItemStack> canExtract, Predicate<@NonNull ItemStack> canInsert,
          Predicate<@NonNull ItemStack> validator, @Nullable IMekanismInventory inventory, int x, int y) {
        super(canExtract, canInsert, validator, inventory, x, y);
        this.fluidHandler = fluidHandler;
    }

    @Override
    protected ContainerSlotType getSlotType() {
        return ContainerSlotType.EXTRA;
    }

    //TODO: Make it so that the fluid handler fills
}
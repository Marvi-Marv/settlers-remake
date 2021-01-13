package jsettlers.logic.objects;

import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.player.ECivilisation;
import jsettlers.logic.buildings.stack.IStackSizeSupplier;
import jsettlers.logic.constants.Constants;
import jsettlers.logic.map.grid.objects.AbstractHexMapObject;

/**
 * @author MarviMarv
 */
public class ManaMapObject extends AbstractHexMapObject {
    private static final long serialVersionUID = -174985264395107962L;

    private final ECivilisation civilisation;
    private final IStackSizeSupplier stack;

    public ManaMapObject(IStackSizeSupplier stack, ECivilisation civilisation) {
        this.civilisation = civilisation;
        this.stack = stack;
    }

    @Override
    public EMapObjectType getObjectType() {
        switch (this.civilisation) {
            case AMAZON:
                return EMapObjectType.MEAD;
            case ASIAN:
                return EMapObjectType.LIQUOR;
            case EGYPTIAN:
                return EMapObjectType.KEG;
            case ROMAN:
            default:
                return EMapObjectType.WINE_BOWL;
        }
    }

    @Override
    public float getStateProgress() {
        return ((float) stack.getStackSize()) / Constants.STACK_SIZE;
    }

    @Override
    public boolean cutOff() {
        return false;
    }

    @Override
    public boolean canBeCut() {
        return false;
    }
}

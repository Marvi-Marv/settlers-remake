package jsettlers.main.android.menus.mainmenu;

import jsettlers.common.menu.IStartingGame;
import jsettlers.main.android.providers.GameStarter;
import jsettlers.main.android.views.NewMultiPlayerSetupView;
import jsettlers.main.android.views.NewSinglePlayerSetupView;

/**
 * Created by tompr on 21/01/2017.
 */

public class NewSinglePlayerSetupMenu extends MapSetupMenu {
    public NewSinglePlayerSetupMenu(NewSinglePlayerSetupView view, GameStarter gameStarter) {
        super(view, gameStarter);
    }

    @Override
    public void startGame() {
        IStartingGame startingGame = getGameStarter().getStartScreen().startSingleplayerGame(getMapDefinition());
        getGameStarter().setStartingGame(startingGame);
    }
}

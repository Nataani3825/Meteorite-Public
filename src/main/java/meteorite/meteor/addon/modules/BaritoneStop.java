/**
 * BaritoneStop
 * =======
 *  - Written by Nataani
 *  - Part of the Meteorite Module
 *  Enables Hotkey assignment to stop baritone.
 */

package meteorite.meteor.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteorite.meteor.addon.Meteorite;

public class BaritoneStop extends Module {


    public BaritoneStop() {
        super(Meteorite.Main, "Baritone-Stop",
            "Chat utility to stop baritone.");
    }

    @Override
    public void onActivate() {
        ChatUtils.sendPlayerMsg("#stop");
        toggle();
    }

}

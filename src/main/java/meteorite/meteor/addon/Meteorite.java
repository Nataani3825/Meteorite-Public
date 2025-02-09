package meteorite.meteor.addon;

import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteorite.meteor.addon.modules.*;
import meteorite.meteor.addon.hud.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Meteorite extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger(Meteorite.class);
    public static final Category Main = new Category("Meteorite");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteorite");

        // Modules
        Modules.get().add(new AirPlace());
        Modules.get().add(new BaritoneStop());
        Modules.get().add(new EntityClusterESP());
        Modules.get().add(new ItemESP());
        Modules.get().add(new MiddleClickFlight());
        Modules.get().add(new MobGearESPExtra());
        Modules.get().add(new PacketSniffer());
        Modules.get().add(new SpawnerScout());
        Modules.get().add(new StashFinderExtra());
        Modules.get().add(new Survey());


        // Commands
        //Commands.add(new CommandExample());

        // HUD
        Hud.get().register(ElytraCount.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(Main);
    }

    @Override
    public String getPackage() {
        return "meteorite.meteor.addon";
    }

}

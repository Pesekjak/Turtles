package dev.pesek.turtles;

import io.github.pylonmc.rebar.content.guide.RebarGuide;
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage;

// TODO researches, recipes...
public interface TurtlesPages {

    SimpleStaticGuidePage COMPUTERS = new SimpleStaticGuidePage(TurtlesKeys.Pages.COMPUTERS);

    static void init() {
        RebarGuide.getRootPage().addPage(TurtlesItems.COMPUTER, COMPUTERS);
    }

}

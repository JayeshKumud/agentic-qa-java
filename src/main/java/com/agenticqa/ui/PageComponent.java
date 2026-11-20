package com.agenticqa.ui;

/**
 * A page object that {@link PageComponentFactory} can create. Implementations need a public constructor
 * {@code (Page page, Healer healer)}, which the factory calls.
 * <p>
 * Actions that are the same on every page, such as {@link #openHelp()}, are written once here as default
 * methods. They use {@link #elements()}, so each runs on the page object's own window, and their locators live
 * once in {@code locators/common.json}.
 */
public interface PageComponent {

    /** @return the title of the browser page */
    String getTitle();

    /**
     * The page object's elements, bound to its own window, so the shared actions below search that window.
     *
     * @return the page object's {@link PageElements}
     */
    PageElements elements();

    /** Clicks the help button, which every page has (helpButton in locators/common.json). */
    default void openHelp() {
        elements().get("helpButton").click();
    }
}

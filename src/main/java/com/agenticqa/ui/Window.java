package com.agenticqa.ui;

import java.util.Arrays;

/**
 * The app's windows. {@link WindowNavigator#switchToWindow(Window)} takes only these values, so a test can never
 * pass an unknown window:
 * <pre>
 * windows.switchToWindow(Window.PRICE_STACK);       // opens it from the current window's dropdown
 * windows.switchToWindow(Window.CUSTOMER_WINDOW);   // opens it from the Price Stack window's dropdown
 * windows.switchToWindow(Window.HOME);              // the scenario's first window
 * </pre>
 * Every window except {@link #HOME} is opened by the dropdown option with its {@link #text()}, and that text is also
 * the new window's title. Adding a window: add a value with its exact option text and its page object.
 */
public enum Window {

    /** The scenario's first window, the home page. Not a dropdown option; it is never opened, only switched to. */
    HOME("Home", HomePage.class),
    /** The Price Stack window: option "Price Stack" of the home page's dropdown. */
    PRICE_STACK("Price Stack", PricePage.class),
    /** The Customer window: option "Customer Window" of the Price Stack window's dropdown. */
    CUSTOMER_WINDOW("Customer Window", CustomersPage.class);

    private final String text;
    private final Class<? extends PageComponents> page;

    Window(String text, Class<? extends PageComponents> page) {
        this.text = text;
        this.page = page;
    }

    /** @return the dropdown option's text, exactly as shown, which is also the window's title ("Home" for HOME) */
    public String text() {
        return text;
    }

    /** @return the page object of this window, for example PricePage.class */
    public Class<? extends PageComponents> page() {
        return page;
    }

    /**
     * The window with this exact text, for steps that get it from a feature file: {@code Window.of("Price Stack")}.
     *
     * @param text the option text, or "Home"
     * @return the window
     * @throws IllegalArgumentException if no window has that text (the message lists the known ones)
     */
    public static Window of(String text) {
        return Arrays.stream(values()).filter(w -> w.text.equals(text)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No window '" + text + "'. Known: "
                        + Arrays.stream(values()).map(Window::text).toList() + ". Add it to the Window enum."));
    }

    @Override
    public String toString() {
        return text;
    }
}

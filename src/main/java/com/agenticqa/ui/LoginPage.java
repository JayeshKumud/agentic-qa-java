package com.agenticqa.ui;

import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.Page;

/**
 * Page object of the login page, shown after log out ({@link CommonActions#logout}) and before sign in.
 * Steps talk to this class and never use selectors themselves.
 * <p>
 * The selectors live in {@code src/main/resources/locators/loginpage.json}, not in this class.
 */
public class LoginPage implements PageComponents {

    /** Name of the locator file, locators/loginpage.json. */
    public static final String LOCATORS = "loginpage";

    /** The browser page title (the tab) of the login page. DUMMY: replace with the real title. */
    public static final String PAGE_TITLE = "Sign in to Open Platform";

    private final Page page;
    private final Healer healer;
    private final PageElements el;

    /**
     * Wraps the window that shows the login page.
     *
     * @param page   the Playwright page
     * @param healer this scenario's healer
     */
    public LoginPage(Page page, Healer healer) {
        this.page = page;
        this.healer = healer;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    /** @return the title of the browser page */
    @Override
    public String getTitle() {
        return page.title();
    }

    /** @return this page's elements, used by the shared actions of {@link PageComponents} */
    @Override
    public PageElements elements() {
        return el;
    }

    /**
     * Checks that the login page is shown: the browser page title is {@value #PAGE_TITLE} and the sign in button
     * is visible. Waits up to ui.waits.assertionMs for the page to
     * appear, for example right after log out.
     *
     * @return this page
     * @throws AssertionError if the title or the sign in button is not shown
     */
    @Override
    public LoginPage verifyDisplayed() {
        CommonActions.assertTitle(page, PAGE_TITLE);
        CommonActions.assertVisible(el.get("signInButton"), "the sign in button of the login page");
        return this;
    }

    /**
     * Signs in and checks that the home page is shown. It first waits for the login page ({@link #verifyDisplayed()}),
     * so the form is not looked up while a redirect to the login page is still running.
     *
     * @param username the user name or email
     * @param password the password
     * @return the home page, already checked with {@link HomePage#verifyDisplayed()}
     * @throws AssertionError if the login page is not shown, or the home page is not shown after sign in
     */
    public HomePage signIn(String username, String password) {
        verifyDisplayed(); // waits for the login page (title, sign in button), so no lookup runs mid-redirect
        el.get("usernameInput").fill(username);
        el.get("passwordInput").fill(password);
        el.get("signInButton").click();
        page.waitForLoadState();
        return new HomePage(page, healer).verifyDisplayed();
    }
}

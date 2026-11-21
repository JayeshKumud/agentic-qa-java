package com.agenticqa.steps;

import com.agenticqa.api.ApiFactory;
import com.agenticqa.apis.CustomerApi;
import com.agenticqa.apis.model.Customer;
import com.agenticqa.core.UniqueData;
import com.agenticqa.core.TestSession;
import com.agenticqa.ui.CustomersPage.CustomerRow;
import com.agenticqa.ui.Pages;
import com.agenticqa.ui.healing.HealEvent;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Route;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.agenticqa.steps.Keys.CUSTOMER;
import static com.agenticqa.steps.Keys.NAME_TAG;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Steps for the customers page. Data is usually prepared through the API ({@link CustomerApi}, fast and reliable)
 * and then checked in the browser; only the behaviour under test goes through the UI. GUI and API steps share
 * "the customer" through the scenario's {@link TestSession} (key {@code Keys.CUSTOMER}).
 */
public class CustomerUiSteps {

    private final TestSession session;
    private final ServiceCleanup cleanup;
    private final Pages pages;

    /**
     * Receives this scenario's objects from PicoContainer.
     *
     * @param session the per-scenario session
     * @param cleanup deletes what the scenario created, after it
     * @param pages   the scenario's page objects, each created on first use (API-only scenarios never start a
     *                browser)
     */
    public CustomerUiSteps(TestSession session, ServiceCleanup cleanup, Pages pages) {
        this.session = session;
        this.cleanup = cleanup;
        this.pages = pages;
    }

    /**
     * Creates several customers through the API whose names share one unique tag.
     *
     * @param count how many customers to create
     */
    @Given("{int} customers share a unique name tag")
    public void customersWithTag(int count) {
        String tag = "Batch " + UniqueData.token();
        for (int i = 1; i <= count; i++) {
            String name = tag + " #" + i;
            Customer created = ApiFactory.getApi(CustomerApi.class)
                    .postCustomer(new Customer(name, UniqueData.emailFor(name))).as(Customer.class);
            CustomerApiSteps.deleteAfterScenario(cleanup, created.getId());
        }
        session.data().put(NAME_TAG, tag);
    }

    /**
     * Adds a customer through the form, then finds its id via search and remembers it as "the customer".
     *
     * @param name the readable name; a unique token is appended and the email is derived from it
     */
    @When("I add a customer named {string} through the form")
    public void addThroughForm(String name) {
        String unique = UniqueData.uniqueName(name);
        String email = UniqueData.emailFor(unique);
        pages.customers().open().addCustomer(unique, email);

        List<CustomerRow> found = pages.customers().search(unique).visibleRows();
        assertEquals(found.size(), 1, "Expected exactly one row for " + unique);
        CustomerRow row = found.get(0);
        CustomerApiSteps.deleteAfterScenario(cleanup, row.id());
        session.data().put(CUSTOMER, new Customer(row.id(), row.name(), row.email(), row.status()));
    }

    /** Searches the page for the customer's name. */
    @When("I search the customers page for that customer")
    public void searchForCustomer() {
        pages.customers().open().search(session.data().get(CUSTOMER).getName());
    }

    /** Searches the page for the shared name tag. */
    @When("I search the customers page for that tag")
    public void searchForTag() {
        pages.customers().open().search(session.data().get(NAME_TAG));
    }

    /** Searches for a name no customer has. */
    @When("I search the customers page for a name that matches nobody")
    public void searchForNobody() {
        pages.customers().open().search("nobody-" + UniqueData.token());
    }

    /** Goes to the next page of results. */
    @When("I go to the next page")
    public void nextPage() {
        pages.customers().nextPage();
    }

    /** Goes to the previous page of results. */
    @When("I go to the previous page")
    public void previousPage() {
        pages.customers().previousPage();
    }

    /**
     * Checks that the results contain exactly the customer, with all columns correct.
     *
     * @param status the expected status column
     */
    @Then("the results show only that customer with status {string}")
    public void onlyThatCustomer(String status) {
        Customer expected = session.data().get(CUSTOMER);
        assertThat(pages.customers().rows()).hasCount(1);
        CustomerRow row = pages.customers().visibleRows().get(0);
        assertEquals(row.id(), expected.getId().longValue());
        assertEquals(row.name(), expected.getName());
        assertEquals(row.email(), expected.getEmail());
        assertEquals(row.status(), status);
    }

    /** Checks the empty-result message and that no rows are shown. */
    @Then("the page says no customers were found")
    public void noCustomersFound() {
        assertThat(pages.customers().noResultsMessage()).hasText("No customers found.");
        assertThat(pages.customers().rows()).hasCount(0);
    }

    /**
     * Checks the number of rows and the page indicator.
     *
     * @param count     expected rows on this page
     * @param indicator expected text, for example "Page 1 of 2"
     */
    @Then("the page shows {int} customer(s) and {string}")
    public void pageShows(int count, String indicator) {
        assertThat(pages.customers().rows()).hasCount(count);
        assertThat(pages.customers().pageIndicator()).hasText(indicator);
    }

    /**
     * Simulates a release that renamed an element id: from now on every HTML page the browser loads in this
     * scenario has the id replaced. The app itself is not changed.
     *
     * @param from the id the page object uses
     * @param to   the new id
     */
    @Given("the app's HTML renames the id {string} to {string}")
    public void renameId(String from, String to) {
        session.page().route("**/*", route -> {
            if (route.request().resourceType().equals("document") && route.request().method().equals("GET")) {
                APIResponse original = route.fetch();
                String html = original.text().replace("id=\"" + from + "\"", "id=\"" + to + "\"");
                route.fulfill(new Route.FulfillOptions().setResponse(original).setBody(html));
            } else {
                route.resume();
            }
        });
    }

    /**
     * Checks that a locator was healed in this scenario, and to which selector.
     *
     * @param description the element's readable name, for example "search input"
     * @param selector    the expected replacement selector
     */
    @Then("the {string} locator was healed to {string}")
    public void locatorHealed(String description, String selector) {
        List<HealEvent> heals = session.healEvents();
        assertTrue(heals.stream().anyMatch(h -> h.description().equals(description) && h.newSelector().equals(selector)),
                "Expected " + description + " to be healed to " + selector + ", heals were: " + heals);
    }

    /** Checks that nothing was healed in this scenario. */
    @Then("no locator was healed")
    public void nothingHealed() {
        assertTrue(session.healEvents().isEmpty(), "Unexpected heals: " + session.healEvents());
    }
}
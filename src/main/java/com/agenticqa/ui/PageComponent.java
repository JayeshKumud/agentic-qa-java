package com.agenticqa.ui;

/**
 * A page object that {@link PageComponentFactory} can create. Implementations need a public constructor
 * {@code (Page page, Healer healer)}, which the factory calls.
 */
public interface PageComponent {

    /** @return the title of the browser page */
    String getTitle();
}

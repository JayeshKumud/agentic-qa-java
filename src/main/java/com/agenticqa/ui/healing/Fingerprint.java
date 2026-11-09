package com.agenticqa.ui.healing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What an element looked like the last time its selector worked. Healing compares page elements against it.
 * Empty values are stored as null and are ignored when scoring.
 *
 * @param tag         lowercase tag name, for example "input"
 * @param id          the id attribute
 * @param name        the name attribute
 * @param type        the type attribute
 * @param placeholder the placeholder attribute
 * @param ariaLabel   the aria-label attribute
 * @param text        visible text, trimmed and cut to 80 characters (null for form fields)
 * @param classes     CSS classes
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Fingerprint(String tag, String id, String name, String type, String placeholder,
                          String ariaLabel, String text, List<String> classes) {

    /**
     * JavaScript that turns an element into the fields of this record. Used with evaluate.
     */
    static final String CAPTURE_JS = """
            el => {
              const attr = n => { const v = el.getAttribute(n); return v && v.trim() ? v.trim() : null; };
              const formField = ['input', 'textarea', 'select'].includes(el.tagName.toLowerCase());
              const text = formField ? '' : (el.innerText || '').trim().replace(/\\s+/g, ' ').slice(0, 80);
              return {
                tag: el.tagName.toLowerCase(),
                id: el.id || null,
                name: attr('name'),
                type: attr('type'),
                placeholder: attr('placeholder'),
                ariaLabel: attr('aria-label'),
                text: text || null,
                classes: [...el.classList]
              };
            }""";
}

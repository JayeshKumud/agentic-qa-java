package com.agenticqa.reporting;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Publishes the reports of a pipeline run somewhere people can open them (for example an artifact repository) and
 * says where they are. {@code TestRunner} calls it after the suite, only in the pipeline, and adds the returned
 * URLs to the test execution summary.
 * <p>
 * Implement it in one class with a public no-argument constructor and name that class in config/common.yaml:
 * <pre>
 * reporting:
 *   publisher: com.agenticqa.reporting.JfrogReportPublisher
 * </pre>
 */
public interface ReportPublisher {

    /**
     * Publishes the reports.
     *
     * @return report name to URL, for example "artifact_report_url" to the published Extent report; may be empty
     * @throws Exception if publishing fails; TestRunner logs it and still writes the summary
     */
    Map<String, String> publish() throws Exception;

    /**
     * Creates the configured publisher.
     *
     * @param className the class name from reporting.publisher
     * @return the publisher
     * @throws IllegalArgumentException if the class cannot be found, is not a ReportPublisher or cannot be created
     */
    static ReportPublisher create(String className) {
        try {
            Class<?> type = Class.forName(className);
            if (!ReportPublisher.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(className + " (reporting.publisher) does not implement "
                        + ReportPublisher.class.getName());
            }
            return (ReportPublisher) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                 | InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot create the report publisher " + className
                    + " (reporting.publisher); it needs a public no-argument constructor", e);
        }
    }
}

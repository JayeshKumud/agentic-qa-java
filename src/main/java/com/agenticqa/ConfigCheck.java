package com.agenticqa;

import com.agenticqa.config.EnvConfig;

/**
 * Prints the active environment and its effective settings (file, placeholders and -D overrides applied):
 * {@code mvn -q exec:java -Dexec.mainClass=com.agenticqa.ConfigCheck -Denv=uat}
 */
public class ConfigCheck {

    /**
     * Prints the environment name and the loaded settings.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("env      : " + EnvConfig.envName());
        var settings = EnvConfig.get();
        System.out.println("ui       : " + settings.ui());
        System.out.println("services : " + settings.services().keySet().stream()
                .map(name -> name + "=" + settings.service(name)).toList());
        System.out.println("retry    : " + settings.retry());
    }
}

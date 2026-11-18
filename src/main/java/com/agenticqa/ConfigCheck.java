package com.agenticqa;

import com.agenticqa.config.EnvConfig;

/**
 * Prints the active environment and its effective settings (common.yaml and the environment file merged,
 * placeholders and -D overrides applied):
 * {@code mvn -q exec:java -Dexec.mainClass=com.agenticqa.ConfigCheck -Denv=uat}
 */
public class ConfigCheck {

    /**
     * Prints the environment name and the loaded settings.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("env       : " + EnvConfig.envName());
        var settings = EnvConfig.get();
        System.out.println("ui        : " + settings.ui());
        System.out.println("execution : " + settings.execution());
        System.out.println("cleanup   : " + settings.cleanup());
        System.out.println("retry     : " + settings.retry());
        System.out.println("jks       : " + settings.jks());
        System.out.println("url       : " + settings.url());
        System.out.println("sct       : " + settings.sct());
        System.out.println("cloud     : " + settings.cloud());
    }
}

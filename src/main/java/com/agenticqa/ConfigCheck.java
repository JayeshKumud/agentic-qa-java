package com.agenticqa;

import com.agenticqa.config.EnvConfig;
import com.agenticqa.config.Settings;

import java.util.TreeSet;

/**
 * Prints the active environment and every setting (common.properties and the environment file merged,
 * placeholders and -D overrides applied):
 * {@code mvn -q exec:java -Dexec.mainClass=com.agenticqa.ConfigCheck -Denv=uat}
 */
public class ConfigCheck {

    public static void main(String[] args) {
        System.out.println("env = " + Settings.getEnvironment());
        for (String key : new TreeSet<>(EnvConfig.properties().stringPropertyNames())) {
            System.out.println(key + " = " + Settings.get(key));
        }
    }
}

package com.agenticqa;

import com.agenticqa.config.EnvConfig;

/** Throwaway check for step 1: prints the active environment and its settings. */
public class ConfigCheck {

    /**
     * Prints the environment name and the loaded settings.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("env      : " + EnvConfig.envName());
        System.out.println("settings : " + EnvConfig.get());
    }
}

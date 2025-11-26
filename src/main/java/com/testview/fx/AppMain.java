package com.testview.fx;

import javafx.application.Application;

/**
 * Point d'entrée dédié pour éviter l'erreur
 * "JavaFX runtime components are missing" dans certains contextes d'exécution IDE/JDK.
 *
 * Utilisez cette classe comme Main-Class. Elle délègue à l'application JavaFX réelle (Launcher).
 */
public final class AppMain {
    private AppMain() {}

    public static void main(String[] args) {
        Application.launch(Launcher.class, args);
    }
}

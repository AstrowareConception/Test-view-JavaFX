package com.testview.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lanceur minimal pour afficher un fichier FXML en ignorant contrôleurs et handlers.
 *
 * Utilisation:
 *  - mvn javafx:run -Dargs="chemin/vers/mon.fxml"
 *  - ou exécuter la classe en passant le chemin du FXML en premier argument.
 *  - si aucun argument n'est fourni, un exemple embarqué est utilisé.
 */
public class Launcher extends Application {

    @Override
    public void start(Stage stage) {
        // Wrapper principal avec barre d'outils et zone d'aperçu au centre
        BorderPane wrapper = new BorderPane();

        Label status = new Label();
        Button browseBtn = new Button("Parcourir…");
        ToolBar toolBar = new ToolBar(browseBtn, new Label("  "), status);
        wrapper.setTop(toolBar);

        // Gestion du bouton Parcourir
        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir un fichier FXML");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers FXML", "*.fxml"));

        browseBtn.setOnAction(e -> {
            try {
                File file = chooser.showOpenDialog(stage);
                if (file == null) return;
                Path path = file.toPath();
                openFileInNewWindow(status, stage, path);
                // Mémoriser le dossier pour les ouvertures suivantes
                chooser.setInitialDirectory(file.getParentFile());
            } catch (Exception ex) {
                ex.printStackTrace();
                wrapper.setCenter(new Label("Erreur de chargement FXML: \n" + ex.getMessage()));
                status.setText("Erreur");
                stage.setTitle("Erreur FXML");
            }
        });

        // Chargement initial: argument si fourni, sinon l'exemple embarqué
        String fxmlArg = null;
        if (!getParameters().getRaw().isEmpty()) {
            fxmlArg = getParameters().getRaw().get(0);
        }
        try {
            if (fxmlArg == null || fxmlArg.isBlank()) {
                try (InputStream in = getClass().getResourceAsStream("/test.fxml")) {
                    if (in == null) throw new IOException("Ressource test.fxml introuvable");
                    String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Parent content = loadSanitized(xml, getClass().getResource("/"));
                    wrapper.setCenter(content);
                    status.setText("Exemple embarqué chargé");
                    stage.setTitle("Aperçu FXML (exemple embarqué)");
                }
            } else {
                Path path = Path.of(fxmlArg);
                openFileInNewWindow(status, stage, path);
                chooser.setInitialDirectory(path.toFile().getParentFile());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            wrapper.setCenter(new Label("Erreur de chargement FXML: \n" + ex.getMessage()));
            status.setText("Erreur");
            stage.setTitle("Erreur FXML");
        }

        Scene scene = new Scene(wrapper);
        stage.setTitle(stage.getTitle() == null ? "Aperçu FXML" : stage.getTitle());
        stage.setScene(scene);
        stage.show();
    }

    private static URL toBaseUrl(File file) throws MalformedURLException {
        // Base URL pour résoudre les chemins relatifs (fx:include, images...).
        return file.getParentFile().toURI().toURL();
    }

    private Parent loadSanitized(String rawXml, URL baseUrl) throws IOException {
        String sanitized = FxmlSanitizer.sanitizeInlineIncludes(rawXml, baseUrl);

        // Récupérer les URLs de styles puis supprimer les blocs <stylesheets> du FXML
        java.util.List<String> styles = FxmlSanitizer.extractStylesheetUrls(sanitized);
        String withoutStylesheets = FxmlSanitizer.stripStylesheets(sanitized);

        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(baseUrl);
        // Aucun contrôleur: toute tentative est supprimée par sanitize
        // Fabrique d'emplacements de ressources pour éviter les ResourceBundle manquants
        loader.setResources(null);
        Parent root;
        try (var in = new java.io.ByteArrayInputStream(withoutStylesheets.getBytes(StandardCharsets.UTF_8))) {
            root = loader.load(in);
        }
        if (root != null && styles != null && !styles.isEmpty()) {
            root.getStylesheets().addAll(styles);
        }
        return root;
    }

    // Le point d'entrée se trouve dans AppMain pour éviter l'erreur
    // "JavaFX runtime components are missing" dans certains IDE/JDK.

    private void openFileInNewWindow(Label status, Stage owner, Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Fichier introuvable: " + path.toAbsolutePath());
        }
        String xml = Files.readString(path, StandardCharsets.UTF_8);
        URL baseUrl = toBaseUrl(path.toFile());
        Parent content = loadSanitized(xml, baseUrl);

        Stage preview = new Stage();
        preview.setTitle("Aperçu FXML - " + path.getFileName());
        preview.initOwner(owner);
        preview.setScene(new Scene(content));
        preview.show();

        status.setText("Ouvert dans nouvelle fenêtre: " + path.getFileName());
    }
}

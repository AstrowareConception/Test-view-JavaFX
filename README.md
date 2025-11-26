Test-view-JavaFX — Visualiseur rapide de fichiers FXML

Résumé
- Ce projet est un visualiseur JavaFX minimaliste conçu pour afficher n’importe quel fichier FXML rapidement, sans dépendre de contrôleurs, d’ids, de gestionnaires d’événements ou de scripts.
- Objectif: permettre aux designers/développeurs de « voir » une interface FXML telle qu’elle sera rendue par JavaFX, sans avoir à fournir le code qui l’accompagne.

Pourquoi c’est utile
- Prévisualisation ultra‑rapide d’une maquette FXML sans mettre en place un environnement applicatif complet.
- Vérifier l’agencement (layout), les styles, les ressources (images, CSS), et le rendu général.
- Ouvrir plusieurs variantes d’écrans dans des fenêtres distinctes pour comparer des versions.

Fonctionnalités clés
- Désactivation de la logique côté FXML pour un rendu « passif »:
  - Suppression de fx:controller.
  - Suppression des handlers (onAction, onMouseClicked, …).
  - Suppression des fx:id.
  - Suppression des blocs <fx:script>.
- Prise en charge de fx:include avec « inlining » récursif:
  - Les fichiers inclus sont résolus relativement au fichier parent.
  - Détection et garde contre les cycles d’inclusion.
- Fenêtre d’accueil persistante + aperçus dans de nouvelles fenêtres:
  - Un bouton « Parcourir… » permet de choisir un FXML sur le disque.
  - Chaque fichier chargé s’ouvre dans une nouvelle fenêtre, l’accueil reste visible.
- Exemple prêt à l’emploi (test.fxml) inclus pour valider l’installation en 1 commande.

Prérequis
- Java 17+ (JDK).
- Maven 3.8+ recommandé pour l’exécution pendant le développement.

Lancer rapidement
1) Avec Maven (recommandé)
- Exemple embarqué:
  - Windows PowerShell: mvn -q javafx:run
  - macOS/Linux: mvn -q javafx:run

- Charger un fichier FXML spécifique en argument:
  - Windows: mvn -q javafx:run -Dargs="C:\\chemin\\vers\\votre.fxml"
  - macOS/Linux: mvn -q javafx:run -Dargs="/chemin/vers/votre.fxml"

- Depuis l’écran d’accueil, vous pouvez aussi cliquer sur « Parcourir… » pour charger un FXML à la demande (ouvre une nouvelle fenêtre). Vous pouvez en ouvrir plusieurs.

2) Depuis votre IDE (IntelliJ IDEA, Eclipse, …)
- Configurer la classe de démarrage: com.testview.fx.AppMain.
- Optionnel: passer le chemin du FXML en « Program arguments ».
- Sinon, utilisez le bouton « Parcourir… » dans l’interface d’accueil après lancement.

3) Image autonome (sans installer JavaFX) via jlink
- Générer l’image:
  - mvn -q javafx:jlink
- Lancer l’application générée:
  - Windows: target\\test-view-javafx\\bin\\app "C:\\chemin\\vers\\votre.fxml"
  - macOS/Linux: target/test-view-javafx/bin/app "/chemin/vers/votre.fxml"

Modes d’utilisation
- Mode accueil par défaut:
  - Un exemple (src/main/resources/test.fxml) est affiché.
  - Utilisez le bouton « Parcourir… » pour choisir un autre FXML: un nouvel aperçu s’ouvre dans une nouvelle fenêtre.

- Mode argument en ligne de commande:
  - Passer un chemin FXML en argument ouvre directement ce fichier dans une nouvelle fenêtre, tout en conservant la fenêtre d’accueil.

- Mode « remplacez le fichier »:
  - Remplacez src/main/resources/test.fxml par votre FXML (même nom), puis relancez l’app.

Détails techniques importants
- Point d’entrée AppMain vs Launcher
  - AppMain (com.testview.fx.AppMain) est l’unique Main-Class. Il délègue à Launcher, qui est une sous‑classe de javafx.application.Application.
  - Ce schéma évite l’erreur « JavaFX runtime components are missing » rencontrée dans certains contextes IDE/JDK si on lance directement une classe Application.

- Neutralisation (sanitization) du FXML
  - Implémentée par com.testview.fx.FxmlSanitizer.
  - Retire fx:controller, fx:id, attributs onXxx, et toute balise <fx:script>.
  - But: empêcher JavaFX d’exiger du code ou des bundles inexistants, pour se concentrer sur le rendu visuel.

- Gestion de fx:include
  - Les <fx:include> sont résolus relativement à l’URL de base du fichier courant.
  - Les contenus inclus sont « inlinés » (intégrés) après sanitization.
  - Une protection anti-cycle est en place pour éviter les inclusions circulaires infinies.

Limitations (attendues par conception)
- Les contrôleurs, handlers, scripts et fx:id sont supprimés; aucune logique applicative n’est exécutée.
- Si la hiérarchie FXML dépend d’un code custom (ex. Node personnalisé via un contrôleur), le rendu visuel ne reflétera pas ce code.
- Les ressources (images, CSS) doivent être accessibles depuis les chemins relatifs au FXML source.
- Le sanitizer est basé sur des expressions régulières simples, adaptées au test/aperçu. Pour des cas FXML très complexes, certaines constructions atypiques pourraient nécessiter des ajustements.

Dépannage / FAQ
1) « Error: JavaFX runtime components are missing »
- Utilisez com.testview.fx.AppMain comme point d’entrée (déjà configuré dans pom.xml et le manifest du JAR). Évitez de lancer directement com.testview.fx.Launcher depuis la ligne de commande.
- Préférez mvn javafx:run en développement, ou l’image jlink pour un exécutable autonome.

2) Rien ne s’affiche ou erreur lors du chargement d’un FXML
- Vérifiez que le fichier existe et l’encodage (UTF‑8 recommandé).
- Si le FXML dépend de fx:include, assurez-vous que les chemins inclus sont valides relativement au fichier principal.
- Inspectez la console: le sanitizer ajoute des commentaires dans le XML inliné en cas d’échec d’un include.

3) Mes handlers/contrôleurs ne fonctionnent pas
- C’est normal: le but est de visualiser, pas d’exécuter la logique. Les handlers et contrôleurs sont retirés pour éviter toute dépendance applicative.

Structure du projet
- pom.xml — Dépendances JavaFX (controls, fxml) + plugin javafx‑maven‑plugin; configuration du Main‑Class com.testview.fx.AppMain.
- src/main/java/com/testview/fx/AppMain.java — Point d’entrée qui lance l’application JavaFX (Launcher).
- src/main/java/com/testview/fx/Launcher.java — UI d’accueil, bouton « Parcourir… », ouverture des FXML dans de nouvelles fenêtres.
- src/main/java/com/testview/fx/FxmlSanitizer.java — Neutralisation et inlining récursif des fx:include.
- src/main/resources/test.fxml — Exemple embarqué pour valider rapidement que tout fonctionne.

Exemples de commandes
- Lancer l’exemple: mvn -q javafx:run
- Ouvrir un FXML spécifique:
  - Windows: mvn -q javafx:run -Dargs="C:\\UI\\Screen.fxml"
  - macOS/Linux: mvn -q javafx:run -Dargs="/home/user/UI/Screen.fxml"
- Générer et utiliser l’image jlink:
  - mvn -q javafx:jlink
  - Windows: target\\test-view-javafx\\bin\\app "C:\\UI\\Screen.fxml"
  - macOS/Linux: target/test-view-javafx/bin/app "/home/user/UI/Screen.fxml"

Contribution
- Issues et suggestions bienvenues. Le code vise à rester minimal et focalisé sur l’aperçu visuel des FXML.

Licence
- Ce dépôt ne déclare pas de licence spécifique. Ajoutez-en une si vous prévoyez une distribution publique.

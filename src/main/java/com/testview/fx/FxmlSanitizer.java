package com.testview.fx;

import java.util.regex.Pattern;

/**
 * Nettoie un contenu FXML pour désactiver tout ce qui pourrait exiger du code
 * (contrôleurs, handlers, fx:id, scripts), afin de simplement visualiser l'UI.
 * Cette approche est basée sur des expressions régulières simples, suffisantes
 * pour un usage de test/aperçu. Elle n'altère pas la hiérarchie de nœuds.
 */
public final class FxmlSanitizer {
    private FxmlSanitizer() {}

    // Attribut fx:controller="..."
    private static final Pattern FX_CONTROLLER = Pattern.compile("\\sfx:controller\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);
    // Attributs d'event handler: onAction, onMouseClicked, etc.
    private static final Pattern ON_EVENT_ATTR = Pattern.compile("\\son[A-Z][A-Za-z0-9]*\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);
    // fx:id
    private static final Pattern FX_ID = Pattern.compile("\\sfx:id\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);
    // fx:script blocks
    private static final Pattern FX_SCRIPT = Pattern.compile("<fx:script[\\s\\S]*?</fx:script>", Pattern.CASE_INSENSITIVE);

    public static String sanitize(String xml) {
        if (xml == null) return "";
        String out = xml;
        out = FX_SCRIPT.matcher(out).replaceAll("");
        out = FX_CONTROLLER.matcher(out).replaceAll("");
        out = ON_EVENT_ATTR.matcher(out).replaceAll("");
        out = FX_ID.matcher(out).replaceAll("");
        // Supprimer attributs de type onX="#{...}" éventuels avec espaces
        out = out.replaceAll("\\s:on[A-Z][A-Za-z0-9]*\\s*=\\s*\"[^\"]*\"", "");
        return out;
    }

    /**
     * Sanitize + inline des fx:include pour éviter de charger d'autres fichiers non sanitizés.
     * @param xml contenu FXML
     * @param baseUrl base pour résoudre les includes relatifs
     * @return FXML prêt à charger
     */
    public static String sanitizeInlineIncludes(String xml, java.net.URL baseUrl) {
        return sanitizeInlineIncludes(xml, baseUrl, new java.util.HashSet<>());
    }

    private static String sanitizeInlineIncludes(String xml, java.net.URL baseUrl, java.util.Set<String> visited) {
        String sanitized = sanitize(xml);
        // Réécriture des références CSS relatives (fx:value="@...") en URLs absolues basées sur baseUrl
        sanitized = rewriteStylesheetUrls(sanitized, baseUrl);
        // Recherche des includes: <fx:include ... source="..." .../>
        java.util.regex.Pattern includeTag = java.util.regex.Pattern.compile(
                "<fx:include\\s+([^>]*?)source\\s*=\\s*\"([^\"]+)\"([^>]*)/?>(?:</fx:include>)?",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = includeTag.matcher(sanitized);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String source = m.group(2);
            String inlined = "";
            try {
                java.net.URL includeUrl = new java.net.URL(baseUrl, source);
                String key = includeUrl.toString();
                if (visited.contains(key)) {
                    inlined = "<!-- fx:include cycle détecté pour " + escapeXml(source) + " -->";
                } else {
                    visited.add(key);
                    String includeContent = new String(includeUrl.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String processed = sanitizeInlineIncludes(includeContent, includeUrl, visited);
                    inlined = processed;
                }
            } catch (Exception e) {
                inlined = "<!-- Impossible d'inclure: " + escapeXml(source) + " : " + escapeXml(e.getMessage()) + " -->";
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(inlined));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Extrait toutes les URLs de feuilles de style présentes dans les blocs
     * <stylesheets> et <X.stylesheets> du FXML fourni. Cette méthode suppose
     * que {@link #rewriteStylesheetUrls(String, java.net.URL)} a déjà été appelée,
     * donc les chemins relatifs commençant par @ ont été réécrits en URLs absolues.
     * Elle est tolérante et acceptera aussi bien <String>texte</String> que
     * <URL>texte</URL> ou encore des attributs value="..." s'il en reste.
     */
    public static java.util.List<String> extractStylesheetUrls(String xml) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        if (xml == null || xml.isEmpty()) return urls;

        java.util.regex.Pattern block = java.util.regex.Pattern.compile(
                "(<(?:[A-Za-z0-9_\\-.]+\\.)?stylesheets[^>]*>)([\\s\\S]*?)(</(?:[A-Za-z0-9_\\-.]+\\.)?stylesheets>)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher bm = block.matcher(xml);
        while (bm.find()) {
            String inner = bm.group(2);
            if (inner == null) continue;
            // 1) Balises <String>...</String>
            java.util.regex.Pattern pString = java.util.regex.Pattern.compile(
                    "<\\s*String[^>]*>([\\s\\S]*?)</\\s*String\\s*>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher ms = pString.matcher(inner);
            while (ms.find()) {
                String url = ms.group(1).trim();
                if (!url.isEmpty()) urls.add(url);
            }
            // 2) Balises <URL>...</URL>
            java.util.regex.Pattern pUrlText = java.util.regex.Pattern.compile(
                    "<\\s*URL[^>]*>([\\s\\S]*?)</\\s*URL\\s*>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher mu = pUrlText.matcher(inner);
            while (mu.find()) {
                String url = mu.group(1).trim();
                if (!url.isEmpty()) urls.add(url);
            }
            // 3) Formes auto-fermantes avec value="..."
            java.util.regex.Pattern pValue = java.util.regex.Pattern.compile(
                    "<\\s*(?:String|URL)[^>]*\\svalue=\"([^\"]+)\"[^>]*/\\s*>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher mv = pValue.matcher(inner);
            while (mv.find()) {
                String url = mv.group(1).trim();
                if (!url.isEmpty()) urls.add(url);
            }
        }
        return urls;
    }

    /**
     * Supprime tous les blocs <stylesheets> et <X.stylesheets> du document FXML,
     * afin d'éviter les divergences de prise en charge par certains FXMLLoader.
     * Les URLs peuvent être ajoutées ensuite par code avec Node.getStylesheets().addAll(...).
     */
    public static String stripStylesheets(String xml) {
        if (xml == null || xml.isEmpty()) return xml;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<(?:[A-Za-z0-9_\\-.]+\\.)?stylesheets[^>]*>[\\s\\S]*?</(?:[A-Za-z0-9_\\-.]+\\.)?stylesheets>",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        return p.matcher(xml).replaceAll("");
    }

    /**
     * Convertit les valeurs fx:value="@relpath.css" en URLs absolues basées sur baseUrl,
     * afin que les CSS des fichiers inclus restent correctement résolues après inlining.
     */
    private static String rewriteStylesheetUrls(String xml, java.net.URL baseUrl) {
        if (xml == null) return "";
        String out = xml;

        // 1) Attribut fx:value="@..."
        out = rewriteAttributeAtPath(out, baseUrl, "fx:value");

        // 2) Attribut value="@..."
        out = rewriteAttributeAtPath(out, baseUrl, "value");

        // 3) Contenu texte direct dans <String> ou <URL>: <String>@styles.css</String>
        out = rewriteElementTextAtPath(out, baseUrl, "String");
        out = rewriteElementTextAtPath(out, baseUrl, "URL");

        // 3bis) Attributs génériques de ressource 'url' (ex: <Image url="@image.png"/>)
        //      On réécrit aussi ces chemins relatifs en URLs absolues basées sur baseUrl.
        out = rewriteAttributeAtPath(out, baseUrl, "url");

        // 3ter) Autres attributs de ressource courants pouvant utiliser la notation @relative
        //       - source (Media, AudioClip, etc.)
        //       - href (Hyperlink, WebView content, etc.)
        //       - src (quelques contrôles personnalisés ou Web-related)
        out = rewriteAttributeAtPath(out, baseUrl, "source");
        out = rewriteAttributeAtPath(out, baseUrl, "href");
        out = rewriteAttributeAtPath(out, baseUrl, "src");

        // 4) Normaliser les éléments enfants de <stylesheets>: remplacer <String> par <URL>
        out = normalizeStylesheetElementTypes(out);

        return out;
    }

    private static String rewriteAttributeAtPath(String xml, java.net.URL baseUrl, String attrName) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                attrName + "\\s*=\\s*\"@([^\"]+)\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String rel = m.group(1);
            String absolute = rel;
            try {
                java.net.URL absUrl = new java.net.URL(baseUrl, rel);
                absolute = absUrl.toExternalForm();
            } catch (Exception ignored) {}
            String replacement = attrName + "=\"" + escapeXml(absolute) + "\"";
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Dans les blocs <stylesheets> (y compris la forme propriété <X.stylesheets>),
     * normalise tous les enfants en forme canonique JavaFX acceptée de manière stricte:
     *   <String>absolute-url</String>
     * - Supprime tout attribut fx:value/value et transforme le contenu en texte interne.
     * - Convertit toute variante <URL ...>…</URL> ou <URL value="…"/> en <String>…</String>.
     * - Convertit également <String value="…"/> en <String>…</String>.
     * - Laisse le reste du document inchangé.
     */
    private static String normalizeStylesheetElementTypes(String xml) {
        if (xml == null || xml.isEmpty()) return xml;

        // Couvrir deux formes équivalentes en FXML:
        //  - <stylesheets> ... </stylesheets>
        //  - <Node.stylesheets> ... </Node.stylesheets>
        // On limite strictement la normalisation à ces blocs.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(<(?:[A-Za-z0-9_\\-.]+\\.)?stylesheets[^>]*>)([\\s\\S]*?)(</(?:[A-Za-z0-9_\\-.]+\\.)?stylesheets>)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String open = m.group(1);
            String inner = m.group(2);
            String close = m.group(3);

            // Objectif: finir en <String>...</String> uniquement
            String normalized = inner
                    // 1) Harmoniser fx:value -> value pour faciliter les règles suivantes
                    .replaceAll("(?i)\\sfx:value(\\s*=)", " value$1")
                    // 2) Développer <String value="..."/> -> <String>...</String>
                    .replaceAll("(?i)<\\s*String([^>]*)\\svalue=\"([^\"]+)\"([^>]*)/\\s*>", "<String>$2</String>")
                    .replaceAll("(?i)<\\s*String([^>]*)\\svalue='([^']+)'([^>]*)/\\s*>", "<String>$2</String>")
                    // 3) Développer <String value="...">...</String> (prioriser l'attribut)
                    .replaceAll("(?i)<\\s*String([^>]*)\\svalue=\"([^\"]+)\"([^>]*)>\\s*</\\s*String\\s*>", "<String>$2</String>")
                    .replaceAll("(?i)<\\s*String([^>]*)\\svalue='([^']+)'([^>]*)>\\s*</\\s*String\\s*>", "<String>$2</String>")
                    // 4) Convertir toutes formes URL vers String
                    //    <URL value="..."/> -> <String>...</String>
                    .replaceAll("(?i)<\\s*URL([^>]*)\\svalue=\"([^\"]+)\"([^>]*)/\\s*>", "<String>$2</String>")
                    .replaceAll("(?i)<\\s*URL([^>]*)\\svalue='([^']+)'([^>]*)/\\s*>", "<String>$2</String>")
                    //    <URL>...</URL> -> <String>...</String>
                    .replaceAll("(?i)<\\s*URL[^>]*>([\\s\\S]*?)</\\s*URL\\s*>", "<String>$1</String>")
                    // 5) Supprimer tout attribut restant sur <String ...> -> <String>
                    .replaceAll("(?i)<\\s*String[^>]*>", "<String>");

            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(open + normalized + close));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String rewriteElementTextAtPath(String xml, java.net.URL baseUrl, String tag) {
        // Remplace >@rel< (en ignorant espaces autour) par >absolute< pour le tag donné
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(<" + tag + "[\\s>][^>]*>)(\\s*)@([^<\"]+?)(\\s*)(</" + tag + ">)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String rel = m.group(3).trim();
            String absolute = rel;
            try {
                java.net.URL absUrl = new java.net.URL(baseUrl, rel);
                absolute = absUrl.toExternalForm();
            } catch (Exception ignored) {}
            String replacement = m.group(1) + escapeXml(absolute) + m.group(5);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

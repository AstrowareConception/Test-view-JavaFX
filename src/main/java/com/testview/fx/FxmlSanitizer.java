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

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

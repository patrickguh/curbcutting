package ca.curbcutting.audit;

import java.util.List;

/**
 * Maps axe-core's rule "cat.*" tags to a user-facing category. See axe-core's
 * rule descriptions for the canonical set of categories a rule can belong to.
 */
public enum ViolationCategory {

    ARIA("cat.aria", "ARIA"),
    COLOR("cat.color", "Color & Contrast"),
    FORMS("cat.forms", "Forms"),
    KEYBOARD("cat.keyboard", "Keyboard"),
    LANGUAGE("cat.language", "Language"),
    NAME_ROLE_VALUE("cat.name-role-value", "Name, Role & Value"),
    PARSING("cat.parsing", "HTML Parsing"),
    SEMANTICS("cat.semantics", "Semantic Markup"),
    SENSORY_AND_VISUAL_CUES("cat.sensory-and-visual-cues", "Sensory & Visual Cues"),
    STRUCTURE("cat.structure", "Structure & Navigation"),
    TABLES("cat.tables", "Tables"),
    TEXT_ALTERNATIVES("cat.text-alternatives", "Text Alternatives"),
    TIME_AND_MEDIA("cat.time-and-media", "Time & Media"),
    OTHER(null, "Other");

    private final String axeTag;
    private final String label;

    ViolationCategory(String axeTag, String label) {
        this.axeTag = axeTag;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** CSS-safe slug, e.g. "name-role-value", for use as a class/data-attribute value. */
    public String getSlug() {
        return name().toLowerCase().replace('_', '-');
    }

    public static ViolationCategory fromTagsCsv(String tagsCsv) {
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return OTHER;
        }
        List<String> tags = List.of(tagsCsv.split(","));
        for (ViolationCategory category : values()) {
            if (category.axeTag != null && tags.contains(category.axeTag)) {
                return category;
            }
        }
        return OTHER;
    }
}

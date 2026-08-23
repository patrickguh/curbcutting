package ca.curbcutting.audit;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "violations")
public class Violation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id")
    private Page page;

    @Column(name = "rule_id", nullable = false, columnDefinition = "text")
    private String ruleId;

    @Column(columnDefinition = "text")
    private String impact;

    @Column(name = "help_text", columnDefinition = "text")
    private String helpText;

    @Column(name = "help_url", columnDefinition = "text")
    private String helpUrl;

    @Column(columnDefinition = "text")
    private String target;

    @Column(name = "html_snippet", columnDefinition = "text")
    private String htmlSnippet;

    @Column(columnDefinition = "text")
    private String tags;

    protected Violation() { }

    public Violation(Page page, String ruleId, String impact,
                     String helpText, String helpUrl,
                     String target, String htmlSnippet, String tags) {
        this.page = page;
        this.ruleId = ruleId;
        this.impact = impact;
        this.helpText = helpText;
        this.helpUrl = helpUrl;
        this.target = target;
        this.htmlSnippet = htmlSnippet;
        this.tags = tags;
    }

    public UUID getId() { return id; }
    public String getRuleId() { return ruleId; }
    public String getImpact() { return impact; }
    public String getHelpText() { return helpText; }
    public String getHelpUrl() { return helpUrl; }
    public String getTarget() { return target; }
    public String getHtmlSnippet() { return htmlSnippet; }
    public String getTags() { return tags; }

    public ViolationCategory getCategory() {
        return ViolationCategory.fromTagsCsv(tags);
    }
}
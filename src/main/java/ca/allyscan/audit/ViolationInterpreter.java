package ca.allyscan.audit;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ViolationInterpreter {

    private static final String SYSTEM_PROMPT = """
            You are an accessibility auditor summarizing axe-core scan results for a
            web developer. Given a list of WCAG/AODA violations grouped by page, write
            a concise, prioritized summary in plain language: lead with the most severe
            issues, explain the real-world impact for a user with a disability, and
            suggest a concrete fix for each. Do not just restate the raw data.
            """;

    private AnthropicClient client;

    public String interpret(String violationReport) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model("claude-haiku-4-5")
                .maxTokens(4096L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(violationReport)
                .build();

        Message response = client().messages().create(params);

        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
    }

    private AnthropicClient client() {
        if (client == null) {
            client = AnthropicOkHttpClient.fromEnv();
        }
        return client;
    }
}

package ca.allyscan.audit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SiteCrawler {

    private static final int MAX_PAGES = 20;
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    public List<String> discover(String rootUrl) {
        String host = URI.create(rootUrl).getHost();

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(stripFragment(rootUrl));

        while (!queue.isEmpty() && visited.size() < MAX_PAGES) {
            String url = queue.poll();
            if (visited.contains(url)) {
                continue;
            }

            Document doc;
            try {
                doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MILLIS)
                        .get();
            } catch (Exception e) {
                continue;
            }
            visited.add(url);

            for (var link : doc.select("a[href]")) {
                String absHref = stripFragment(link.absUrl("href"));
                if (absHref.isBlank() || visited.contains(absHref)) {
                    continue;
                }
                URI linkUri;
                try {
                    linkUri = URI.create(absHref);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (host.equals(linkUri.getHost())) {
                    queue.add(absHref);
                }
            }
        }

        return new ArrayList<>(visited);
    }

    private static String stripFragment(String url) {
        int hashIndex = url.indexOf('#');
        return hashIndex < 0 ? url : url.substring(0, hashIndex);
    }
}

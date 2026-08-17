package ca.curbcutting.audit;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public class ScratchScan {

    public static void main(String[] args) {
        String target = "https://dequeuniversity.com/demo/mars/";

        try (Playwright playwright = Playwright.create()) {

            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));

            Page page = browser.newPage();
            page.navigate(target);

            var results = new AxeBuilder(page).analyze();

            System.out.println("Scanned: " + target);
            System.out.println("Violations: " + results.getViolations().size());

            results.getViolations().forEach(v -> {
                System.out.println("----------");
                System.out.println("rule:    " + v.getId());
                System.out.println("impact:  " + v.getImpact());
                System.out.println("help:    " + v.getHelp());
                System.out.println("affects: " + v.getNodes().size() + " element(s)");
            });

            browser.close();
        }
    }
}
package ca.curbcutting.audit;

import com.deque.html.axecore.results.AxeResults;

public record PageScanResult(String title, AxeResults axeResults) { }

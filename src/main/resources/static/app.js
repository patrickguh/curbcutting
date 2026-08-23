(function () {
    "use strict";

    var POLL_MS = 2500;
    var ACTIVE_STATUSES = ["QUEUED", "RUNNING"];
    var timer = null;

    function isActive(status) {
        return ACTIVE_STATUSES.indexOf(status) !== -1;
    }

    function rows() {
        return document.querySelectorAll("tr[data-job-id]");
    }

    function hasActiveRows() {
        var statuses = document.querySelectorAll("tr[data-job-id] .status");
        for (var i = 0; i < statuses.length; i++) {
            if (isActive(statuses[i].textContent.trim())) {
                return true;
            }
        }
        return false;
    }

    function updateRow(row, job) {
        var statusEl = row.querySelector(".status");
        if (statusEl && statusEl.textContent.trim() !== job.status) {
            statusEl.className = "status status-" + job.status;
            statusEl.textContent = job.status;
        }

        var reportCell = row.querySelector(".report-cell");
        if (reportCell && !isActive(job.status) && !reportCell.querySelector("a")) {
            var link = document.createElement("a");
            link.href = "/scans/" + job.id + "/report";
            link.textContent = "View report";
            reportCell.replaceChildren(link);
        }
    }

    function poll() {
        fetch("/scans", { headers: { "Accept": "application/json" } })
            .then(function (res) { return res.json(); })
            .then(function (jobs) {
                var byId = {};
                jobs.forEach(function (job) { byId[job.id] = job; });

                rows().forEach(function (row) {
                    var job = byId[row.getAttribute("data-job-id")];
                    if (job) {
                        updateRow(row, job);
                    }
                });

                if (!hasActiveRows() && timer) {
                    clearInterval(timer);
                    timer = null;
                }
            })
            .catch(function () {
                // transient network hiccup — next tick will retry
            });
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (hasActiveRows()) {
            timer = setInterval(poll, POLL_MS);
        }
    });
})();

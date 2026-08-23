(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var summary = document.querySelector(".category-summary");
        if (!summary) {
            return;
        }

        var chips = summary.querySelectorAll(".category-chip");

        function applyFilter(activeCategory) {
            document.querySelectorAll(".page-report").forEach(function (article) {
                var rows = article.querySelectorAll("tr[data-category]");
                if (!rows.length) {
                    return;
                }

                var anyMatch = false;
                rows.forEach(function (row) {
                    var matches = !activeCategory || row.getAttribute("data-category") === activeCategory;
                    row.style.display = matches ? "" : "none";
                    if (matches) {
                        anyMatch = true;
                    }
                });

                article.style.display = (activeCategory && !anyMatch) ? "none" : "";
            });

            summary.classList.toggle("is-filtering", !!activeCategory);
        }

        chips.forEach(function (chip) {
            chip.addEventListener("click", function () {
                var wasActive = chip.getAttribute("aria-pressed") === "true";
                chips.forEach(function (c) { c.setAttribute("aria-pressed", "false"); });

                if (wasActive) {
                    applyFilter(null);
                } else {
                    chip.setAttribute("aria-pressed", "true");
                    applyFilter(chip.getAttribute("data-category"));
                }
            });
        });
    });
})();

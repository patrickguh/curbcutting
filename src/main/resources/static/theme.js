(function () {
    "use strict";

    function apply(value) {
        document.documentElement.setAttribute("data-theme", value);
    }

    document.addEventListener("DOMContentLoaded", function () {
        var buttons = document.querySelectorAll(".theme-option");
        if (!buttons.length) {
            return;
        }

        var stored = null;
        try {
            stored = localStorage.getItem("theme");
        } catch (e) {
            // storage unavailable (private mode, etc.) - fall back to the default
        }
        var current = (stored === "light" || stored === "dark" || stored === "system") ? stored : "dark";

        buttons.forEach(function (btn) {
            btn.setAttribute("aria-pressed", btn.getAttribute("data-theme-value") === current ? "true" : "false");

            btn.addEventListener("click", function () {
                var value = btn.getAttribute("data-theme-value");

                try {
                    localStorage.setItem("theme", value);
                } catch (e) {
                    // ignore - theme still applies for this page view
                }

                apply(value);

                buttons.forEach(function (b) {
                    b.setAttribute("aria-pressed", b === btn ? "true" : "false");
                });
            });
        });
    });
})();

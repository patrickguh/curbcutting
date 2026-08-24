(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var shell = document.querySelector(".app-shell");
        var toggle = document.querySelector(".sidebar-toggle");
        var backdrop = document.querySelector(".sidebar-backdrop");
        if (!shell || !toggle) {
            return;
        }

        function close() {
            shell.classList.remove("sidebar-open");
            toggle.setAttribute("aria-expanded", "false");
        }

        function open() {
            shell.classList.add("sidebar-open");
            toggle.setAttribute("aria-expanded", "true");
        }

        toggle.addEventListener("click", function () {
            if (shell.classList.contains("sidebar-open")) {
                close();
            } else {
                open();
            }
        });

        if (backdrop) {
            backdrop.addEventListener("click", close);
        }

        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") {
                close();
            }
        });
    });
})();
